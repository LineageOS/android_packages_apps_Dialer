/*
 * Copyright (C) 2014 Xiao-Long Chen <chillermillerlong@hotmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dialer.lookup;

import com.android.contacts.common.list.PhoneNumberListAdapter.PhoneQuery;
import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.R;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Contacts;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.LinkedList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class LookupProvider extends ContentProvider {
    private static final String TAG = LookupProvider.class.getSimpleName();

    private static final boolean DEBUG = false;

    public static final String AUTHORITY = "com.android.dialer.provider";
    public static final Uri AUTHORITY_URI =
            Uri.parse("content://" + AUTHORITY);
    public static final Uri NEARBY_LOOKUP_URI =
            Uri.withAppendedPath(AUTHORITY_URI, "nearby");
    public static final Uri PEOPLE_LOOKUP_URI =
            Uri.withAppendedPath(AUTHORITY_URI, "people");
    public static final Uri IMAGE_CACHE_URI =
            Uri.withAppendedPath(AUTHORITY_URI, "images");

    private static final UriMatcher sURIMatcher = new UriMatcher(-1);
    private final LinkedList<FutureTask> mActiveTasks =
            new LinkedList<FutureTask>();

    private static final int NEARBY = 0;
    private static final int PEOPLE = 1;
    private static final int IMAGE = 2;

    static {
        sURIMatcher.addURI(AUTHORITY, "nearby/*", NEARBY);
        sURIMatcher.addURI(AUTHORITY, "people/*", PEOPLE);
        sURIMatcher.addURI(AUTHORITY, "images/*", IMAGE);
    }

    private class FutureCallable<T> implements Callable<T> {
        private final Callable<T> mCallable;
        private volatile FutureTask<T> mFuture;

        public FutureCallable(Callable<T> callable) {
            mFuture = null;
            mCallable = callable;
        }

        public T call() throws Exception {
            Log.v(TAG, "Future called for " + Thread.currentThread().getName());

            T result = mCallable.call();
            if (mFuture == null) {
                return result;
            }

            synchronized (mActiveTasks) {
                mActiveTasks.remove(mFuture);
            }

            mFuture = null;
            return result;
        }

        public void setFuture(FutureTask<T> future) {
            mFuture = future;
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, final String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        if (DEBUG) Log.v(TAG, "query: " + uri);

        final int match = sURIMatcher.match(uri);

        switch (match) {
        case NEARBY:
        case PEOPLE:
            Context context = getContext();
            if (!isLocationEnabled()) {
                Log.v(TAG, "Location settings is disabled, ignoring query.");
                return null;
            }

            final Location lastLocation = getLastLocation();
            if (lastLocation == null) {
                Log.v(TAG, "No location available, ignoring query.");
                return null;
            }

            final String filter = Uri.encode(uri.getLastPathSegment());
            String limit = uri.getQueryParameter(ContactsContract.LIMIT_PARAM_KEY);

            int maxResults = -1;

            try {
                if (limit != null) {
                    maxResults = Integer.parseInt(limit);
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "query: invalid limit parameter: '" + limit + "'");
            }

            final int finalMaxResults = maxResults;

            return execute(new Callable<Cursor>() {
                @Override
                public Cursor call() {
                    return handleFilter(match, projection, filter,
                            finalMaxResults, lastLocation);
                }
            }, "FilterThread");
        }

        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("insert() not supported");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        throw new UnsupportedOperationException("update() not supported");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("delete() not supported");
    }

    @Override
    public String getType(Uri uri) {
        int match = sURIMatcher.match(uri);

        switch (match) {
        case NEARBY:
        case PEOPLE:
            return Contacts.CONTENT_ITEM_TYPE;

        default:
            return null;
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        switch (sURIMatcher.match(uri)) {
        case IMAGE:
            String number = uri.getLastPathSegment();

            File image = LookupCache.getImagePath(getContext(), number);

            if (mode.equals("r")) {
                if (image == null || !image.exists() || !image.isFile()) {
                    throw new FileNotFoundException("Cached image does not exist");
                }

                return ParcelFileDescriptor.open(image,
                        ParcelFileDescriptor.MODE_READ_ONLY);
            } else {
                throw new FileNotFoundException("The URI is read only");
            }

        default:
            throw new FileNotFoundException("Invalid URI: " + uri);
        }
    }

    /**
     * Check if the location services is on.
     *
     * @return Whether location services are enabled
     */
    private boolean isLocationEnabled() {
        try {
            int mode = Settings.Secure.getInt(
                getContext().getContentResolver(),
                Settings.Secure.LOCATION_MODE);

            return mode != Settings.Secure.LOCATION_MODE_OFF;
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Failed to get location mode", e);
            return false;
        }
    }

    /**
     * Get location from last location query.
     *
     * @return The last location
     */
    private Location getLastLocation() {
        LocationManager locationManager = (LocationManager)
                getContext().getSystemService(Context.LOCATION_SERVICE);

        locationManager.requestSingleUpdate(new Criteria(),
                new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }
        }, Looper.getMainLooper());

        return locationManager.getLastLocation();
    }

    /**
     * Process filter/query and perform the lookup.
     *
     * @param projection Columns to include in query
     * @param filter String to lookup
     * @param maxResults Maximum number of results
     * @param lastLocation Coordinates of last location query
     * @return Cursor for the results
     */
    private Cursor handleFilter(int type, String[] projection, String filter,
            int maxResults, Location lastLocation) {
        if (DEBUG) Log.v(TAG, "handleFilter(" + filter + ")");

        if (filter != null) {
            try {
                filter = URLDecoder.decode(filter, "UTF-8");
            } catch (UnsupportedEncodingException e) {
            }

            ContactInfo[] results = null;
            if (type == NEARBY) {
                ForwardLookup fl = ForwardLookup.getInstance(getContext());
                results = fl.lookup(getContext(), filter, lastLocation);
            } else if (type == PEOPLE) {
                PeopleLookup pl = PeopleLookup.getInstance(getContext());
                results = pl.lookup(getContext(), filter);
            }

            if (results == null || results.length == 0) {
                if (DEBUG) Log.v(TAG, "handleFilter(" + filter + "): No results");
                return null;
            }

            Cursor cur = null;
            try {
                cur = buildResultCursor(projection, results, maxResults);

                if (DEBUG) Log.v(TAG, "handleFilter(" + filter + "): "
                        + cur.getCount() + " matches");
            } catch (JSONException e) {
                Log.e(TAG, "JSON failure", e);
            }

            return cur;
        }

        return null;
    }

    /**
     * Query results.
     *
     * @param projection Columns to include in query
     * @param results Results for the forward lookup
     * @param maxResults Maximum number of rows/results to add to cursor
     * @return Cursor for forward lookup query results
     */
    private Cursor buildResultCursor(String[] projection,
            ContactInfo[] results, int maxResults)
            throws JSONException {
        // Extended directories always use this projection
        MatrixCursor cursor = new MatrixCursor(PhoneQuery.PROJECTION_PRIMARY);

        int id = 1;

        for (int i = 0; i < results.length; i++) {
            Object[] row = new Object[PhoneQuery.PROJECTION_PRIMARY.length];

            row[PhoneQuery.PHONE_ID] = id;
            row[PhoneQuery.PHONE_TYPE] = results[i].type;
            row[PhoneQuery.PHONE_LABEL] = getAddress(results[i]);
            row[PhoneQuery.PHONE_NUMBER] = results[i].number;
            row[PhoneQuery.CONTACT_ID] = id;
            row[PhoneQuery.LOOKUP_KEY] = results[i].lookupUri.getEncodedFragment();
            row[PhoneQuery.PHOTO_ID] = 0;
            row[PhoneQuery.DISPLAY_NAME] = results[i].name;
            row[PhoneQuery.PHOTO_URI] = results[i].photoUri;

            cursor.addRow(row);

            if (maxResults != -1 && cursor.getCount() >= maxResults) {
                break;
            }

            id++;
        }

        return cursor;
    }

    private String getAddress(ContactInfo info) {
        // Hack: Show city or address for phone label, so they appear in
        // the results list

        String city = null;
        String address = null;

        try {
            String jsonString = info.lookupUri.getEncodedFragment();
            JSONObject json = new JSONObject(jsonString);
            JSONObject contact = json.getJSONObject(Contacts.CONTENT_ITEM_TYPE);

            if (!contact.has(StructuredPostal.CONTENT_ITEM_TYPE)) {
                return null;
            }

            JSONArray addresses = contact.getJSONArray(
                    StructuredPostal.CONTENT_ITEM_TYPE);

            if (addresses.length() == 0) {
                return null;
            }

            JSONObject addressEntry = addresses.getJSONObject(0);

            if (addressEntry.has(StructuredPostal.CITY)) {
                city = addressEntry.getString(StructuredPostal.CITY);
            }
            if (addressEntry.has(StructuredPostal.FORMATTED_ADDRESS)) {
                address = addressEntry.getString(
                        StructuredPostal.FORMATTED_ADDRESS);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to get address", e);
        }

        if (city != null) {
            return city;
        } else if (address != null) {
            return address;
        } else {
            return null;
        }
    }

    /**
     * Execute thread that is killed after a specified amount of time.
     *
     * @param callable The thread
     * @param name Name of the thread
     * @return Instance of the thread
     */
    private <T> T execute(Callable<T> callable, String name) {
        FutureCallable<T> futureCallable = new FutureCallable<T>(callable);
        FutureTask<T> future = new FutureTask<T>(futureCallable);
        futureCallable.setFuture(future);

        synchronized (mActiveTasks) {
            mActiveTasks.addLast(future);
            Log.v(TAG, "Currently running tasks: " + mActiveTasks.size());

            while (mActiveTasks.size() > 8) {
                Log.w(TAG, "Too many tasks, canceling one");
                mActiveTasks.removeFirst().cancel(true);
            }
        }

        Log.v(TAG, "Starting task " + name);

        new Thread(future, name).start();

        try {
            Log.v(TAG, "Getting future " + name);
            return future.get(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.w(TAG, "Task was interrupted: " + name);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Log.w(TAG, "Task threw an exception: " + name, e);
        } catch (TimeoutException e) {
            Log.w(TAG, "Task timed out: " + name);
            future.cancel(true);
        } catch (CancellationException e) {
            Log.w(TAG, "Task was cancelled: " + name);
        }

        return null;
    }
}
