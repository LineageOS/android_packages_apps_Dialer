/*
  * Copyright (C) 2013 Xiao-Long Chen <chenxiaolong@cxl.epac.to>
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

/*
 * This is a reverse-engineered implementation of com.google.android.Dialer.
 * There is no guarantee that this implementation will work correctly or even
 * work at all. Use at your own risk.
 */

package com.google.android.dialer.provider;

import com.android.dialer.R;

import com.google.android.common.http.UrlRules;
import com.google.android.dialer.util.GoogleLocationSettingHelper;
import com.google.android.dialer.util.JsonUtil;
import com.google.android.gsf.Gservices;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
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

public class DialerProvider extends ContentProvider {
    public static final Uri AUTHORITY_URI =
            Uri.parse("content://com.google.android.dialer.provider");
    public static final Uri NEARBY_PLACES_URI =
            Uri.withAppendedPath(AUTHORITY_URI, "nearbyPlaces");

    public static final int CHARSET_PREFIX_LEN = "charset=".length();

    private static final Looper mLooper = new Handler().getLooper();
    private static final UriMatcher sURIMatcher = new UriMatcher(-1);
    private final LinkedList<FutureTask> mActiveTasks;
    private String mUserAgent;

    static {
        sURIMatcher.addURI("com.google.android.dialer.provider", "nearbyPlaces/*", 0);
    }

    public DialerProvider() {
        mActiveTasks = new LinkedList<FutureTask>();
    }

    private Cursor buildResultCursor(
            String[] array, JSONArray jsonArray, int n) throws JSONException {
        int indexDisplayName = -1;
        int indexData3 = -1;
        int indexHasPhoneNumber = -1;
        int indexId = -1;
        int indexContactId = -1;
        int indexData1 = -1;
        int indexData2 = -1;
        int indexPhotoUri = -1;
        int indexPhotoThumbUri = -1;
        int indexLookup = -1;

        for (int i = 0; i < array.length; ++i) {
            String s = array[i];

            if ("display_name".equals(s)) {
                indexDisplayName = i;
            } else if ("data3".equals(s)) {
                indexData3 = i;
            } else if ("has_phone_number".equals(s)) {
                indexHasPhoneNumber = i;
            } else if ("_id".equals(s)) {
                indexId = i;
            } else if ("contact_id".equals(s)) {
                indexContactId = i;
            } else if ("data1".equals(s)) {
                indexData1 = i;
            } else if ("data2".equals(s)) {
                indexData2 = i;
            } else if ("photo_uri".equals(s)) {
                indexPhotoUri = i;
            } else if ("photo_thumb_uri".equals(s)) {
                indexPhotoThumbUri = i;
            } else if ("lookup".equals(s)) {
                indexLookup = i;
            }
        }

        ContentResolver resolver = getContext().getContentResolver();
        boolean showNearbyDistance = Gservices.getBoolean(resolver,
                "dialer_debug_display_nearby_place_distance", false);

        int n12;
        if (Gservices.getBoolean(resolver, "dialer_enable_nearby_places_export", true)) {
            n12 = 2;
        } else {
            n12 = 0;
        }

        MatrixCursor matrixCursor = new MatrixCursor(array);
        JSONArray jsonArray2 = jsonArray.getJSONArray(1);

        int n13 = 1;
        int position = 0;

        while (position < jsonArray2.length()) {
            try {
                JSONArray jsonArray3 = jsonArray2.getJSONArray(position);
                String displayName = decodeHtml(jsonArray3.getString(0));
                JSONObject jsonObject = jsonArray3.getJSONObject(3);
                String data1 = decodeHtml(jsonObject.getString("b"));
                String data3 = decodeHtml(jsonObject.getString("g"));
                String optString = jsonObject.optString("f", null);
                String photoUri = jsonObject.optString("d", null);

                if (showNearbyDistance) {
                    String miles = jsonObject.optString("c", null);
                    if (miles != null) {
                        displayName = displayName + " [" + miles + " miles]";
                    }
                }

                if (!data1.isEmpty()) {
                    Object[] array2 = new Object[array.length];

                    if (indexDisplayName >= 0) {
                        array2[indexDisplayName] = displayName;
                    }
                    if (indexData3 >= 0) {
                        array2[indexData3] = data3;
                    }
                    if (indexHasPhoneNumber >= 0) {
                        array2[indexHasPhoneNumber] = true;
                    }
                    if (indexContactId != -1) {
                        array2[indexContactId] = n13;
                    }
                    if (indexData1 >= 0) {
                        array2[indexData1] = data1;
                    }
                    if (indexData2 >= 0) {
                        array2[indexData2] = 12;
                    }

                    String photoThumbUri;
                    if (photoUri == null) {
                        photoUri = new Uri.Builder()
                                .scheme("android.resource")
                                .authority("com.google.android.dialer")
                                .appendPath(String.valueOf(
                                        R.drawable.ic_places_picture_180_holo_light))
                                .toString();
                        photoThumbUri = new Uri.Builder()
                                .scheme("android.resource")
                                .authority("com.google.android.dialer")
                                .appendPath(String.valueOf(
                                        R.drawable.ic_places_picture_holo_light))
                                .toString();
                    } else {
                        photoThumbUri = photoUri;
                    }

                    if (indexPhotoUri >= 0) {
                        array2[indexPhotoUri] = photoUri;
                    }
                    if (indexPhotoThumbUri >= 0) {
                        array2[indexPhotoThumbUri] = photoThumbUri;
                    }
                    if (indexLookup >= 0) {
                        JSONObject put = new JSONObject()
                                .put("vnd.android.cursor.item/name",
                                        new JSONObject().put("data1", displayName))
                                .put("vnd.android.cursor.item/phone_v2",
                                        JsonUtil.newJsonArray(new JSONObject()
                                                .put("data1", data1)
                                                .put("data2", 12)))
                                .put("vnd.android.cursor.item/postal-address_v2",
                                        JsonUtil.newJsonArray(new JSONObject()
                                                .put("data1", displayName + ", " + data3)
                                                .put("data2", 2)));

                        if (optString != null) {
                            put.put("vnd.android.cursor.item/website",
                                    JsonUtil.newJsonArray(new JSONObject()
                                            .put("data1", optString)
                                            .put("data2", 3)));
                        }

                        array2[indexLookup] = new JSONObject()
                                .put("display_name", displayName)
                                .put("display_name_source", 30)
                                .put("exportSupport", n12)
                                .put("photo_uri", photoUri)
                                .put("vnd.android.cursor.item/contact", put)
                                .toString();
                    }
                    if (indexId != -1) {
                        array2[indexId] = n13;
                    }
                    matrixCursor.addRow(array2);
                    if (n != -1 && matrixCursor.getCount() >= n) {
                        break;
                    }

                    n13++;
                }
            }
            catch (JSONException e) {
                Log.e("DialerProvider", "Skipped the suggestions at position " + position, e);
            }

            position++;
        }
        return matrixCursor;
    }

    private String decodeHtml(String s) {
        return Html.fromHtml(s).toString();
    }

    private <T> T execute(Callable<T> callable, String name, long time, TimeUnit timeUnit) {
        FutureCallable<T> futureCallable = new FutureCallable<T>(callable);
        FutureTask<T> future = new FutureTask<T>(futureCallable);
        futureCallable.setFuture(future);

        synchronized (mActiveTasks) {
            mActiveTasks.addLast(future);
            if (Log.isLoggable("DialerProvider", Log.VERBOSE)) {
                Log.v("DialerProvider", "Currently running tasks: " + mActiveTasks.size());
            }
            while (mActiveTasks.size() > 8) {
                Log.w("DialerProvider", "Too many tasks, canceling one");
                mActiveTasks.removeFirst().cancel(true);
            }
        }

        if (Log.isLoggable("DialerProvider", Log.VERBOSE)) {
            Log.v("DialerProvider", "Starting task " + name);
        }

        new Thread(future, name).start();
        try {
            if (Log.isLoggable("DialerProvider", Log.VERBOSE)) {
                Log.v("DialerProvider", "Getting future " + name);
            }
            return future.get(time, timeUnit);
        } catch (InterruptedException e) {
            Log.w("DialerProvider", "Task was interrupted: " + name);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Log.w("DialerProvider", "Task threw an exception: " + name, e);
        } catch (TimeoutException e) {
            Log.w("DialerProvider", "Task timed out: " + name);
            future.cancel(true);
        } catch (CancellationException e) {
            Log.w("DialerProvider", "Task was cancelled: " + name);
        }

        // TODO: Is this appropriate?
        return null;
    }

    private String executeHttpRequest(Uri uri) throws IOException {
        String charset = null;

        if (Log.isLoggable("DialerProvider", Log.VERBOSE)) {
            Log.v("DialerProvider", "executeHttpRequest(" + uri + ")");
        }

        try {
            URLConnection conn = new URL(uri.toString()).openConnection();
            conn.setRequestProperty("User-Agent", mUserAgent);

            InputStream inputStream = conn.getInputStream();
            charset = getCharsetFromContentType(conn.getContentType());

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            byte[] buf = new byte[1000];
            while (true) {
                int len = inputStream.read(buf);
                if (len <= 0) {
                    break;
                }
                outputStream.write(buf, 0, len);
            }

            inputStream.close();
            outputStream.flush();

            return new String(outputStream.toByteArray(), charset);
        } catch (UnsupportedEncodingException e) {
            Log.w("DialerProvider", "Invalid charset: " + charset, e);
        } catch (IOException e) {
            // TODO: Didn't find anything that goes here in byte-code
        }

        // TODO: Is this appropriate?
        return null;
    }

    private static String getCharsetFromContentType(String s) {
        String[] split = s.split(";");
        for (int i = 0; i < split.length; i++) {
            String trimmed = split[i].trim();
            if (trimmed.startsWith("charset=")) {
                return trimmed.substring(CHARSET_PREFIX_LEN);
            }
        }
        return "UTF-8";
    }

    private Location getLastLocation() {
        LocationManager locationManager =
                (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestSingleUpdate(new Criteria(), new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (Log.isLoggable("DialerProvider", Log.VERBOSE)) {
                    Log.v("DialerProvider", "onLocationChanged: " + location);
                }
            }

            @Override
            public void onProviderDisabled(String provider) {
                if (Log.isLoggable("DialerProvider", Log.VERBOSE)) {
                    Log.v("DialerProvider", "onProviderDisabled: " + provider);
                }
            }

            @Override
            public void onProviderEnabled(String provider) {
                if (Log.isLoggable("DialerProvider", Log.VERBOSE)) {
                    Log.v("DialerProvider", "onProviderEnabled: " + provider);
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                if (Log.isLoggable("DialerProvider", Log.VERBOSE)) {
                    Log.v("DialerProvider", "onStatusChanged: "
                            + provider + ", " + status + ", " + extras);
                }
            }
        }, DialerProvider.mLooper);
        return locationManager.getLastLocation();
    }

    private int getRandomInteger(int n) {
        return (int) Math.floor(Math.random() * n);
    }

    private String getRandomNoiseString() {
        StringBuilder sb = new StringBuilder();
        for (int n = 4 + getRandomInteger(32), i = 0; i < n; i++) {
            if (Math.random() >= 0.3) {
                int temp;
                if (Math.random() <= 0.5) {
                    temp = 97;
                } else {
                    temp = 65;
                }
                i = temp + getRandomInteger(26);
            } else {
                i = 48 + getRandomInteger(10);
            }
            sb.append(Character.toString((char)i));
        }
        return sb.toString();
    }

    private JSONArray getSuggestResponseInJsonArrayFormat(Uri uri) throws IOException {
        try {
            return new JSONArray(executeHttpRequest(uri));
        } catch (JSONException e) {
            Log.e("DialerProvider", "Failed to retrieve/parse the response from " + uri, e);
            return null;
        }
    }

    private Cursor handleFilter(
            String[] projection, String filter, int limitInt, Location lastLocation) {
        if (Log.isLoggable("DialerProvider", Log.VERBOSE)) {
            Log.v("DialerProvider", "handleFilter(" + filter + ")");
        }

        if (filter != null) {
            JSONArray response = null;

            try {
                filter = URLDecoder.decode(filter, "UTF-8");
                ContentResolver resolver = getContext().getContentResolver();

                int minQueryLen = com.google.android.gsf.Gservices.getInt(resolver,
                        "dialer_nearby_places_min_query_len", 2);
                int maxQueryLen = com.google.android.gsf.Gservices.getInt(resolver,
                        "dialer_nearby_places_max_query_len", 50);
                int radius = com.google.android.gsf.Gservices.getInt(resolver,
                        "dialer_nearby_places_directory_radius_meters", 1000);

                int length = filter.length();
                if (length >= minQueryLen) {
                    if (length > maxQueryLen) {
                        filter = filter.substring(0, maxQueryLen);
                    }

                    Uri.Builder builder = Uri.parse(
                            rewriteUrl("https://www.google.com/complete/search?gs_ri=dialer"))
                            .buildUpon()
                            .appendQueryParameter("q", filter)
                            .appendQueryParameter("hl",
                                    getContext().getResources()
                                    .getConfiguration().locale.getLanguage());

                    builder = builder
                            .appendQueryParameter("sll",
                                    String.format("%f,%f",
                                            lastLocation.getLatitude(),
                                            lastLocation.getLongitude()))
                            .appendQueryParameter("radius", Integer.toString(radius))
                            .appendQueryParameter("gs_gbg", getRandomNoiseString());

                    response = getSuggestResponseInJsonArrayFormat(builder.build());

                    if (Log.isLoggable("DialerProvider", Log.VERBOSE)) {
                        Log.v("DialerProvider", "Results: " + response);
                    }

                    Cursor cur = buildResultCursor(projection, response, limitInt);
                    if (Log.isLoggable("DialerProvider", Log.VERBOSE)) {
                        Log.v("DialerProvider", "handleFilter(" + filter + "): "
                                + cur.getCount() + " matches");
                    }

                    return cur;
                }
            } catch (UnsupportedEncodingException e) {
                // TODO: Something should probably go here
            } catch (IOException e) {
                Log.e("DialerProvider", "Failed to execute query", e);
            } catch (JSONException e) {
                Log.e("DialerProvider", "Invalid response to query: " + response, e);
            }
        }

        return null;
    }

    private String rewriteUrl(String url) throws IOException {
        UrlRules.Rule rule = UrlRules.getRules(getContext().getContentResolver()).matchRule(url);
        String newUrl = rule.apply(url);

        if (newUrl == null) {
            Log.w("DialerProvider", "Blocked by " + rule.mName + ": " + url);
            throw new IOException("Blocked by rule: " + rule.mName);
        }

        if (Log.isLoggable("DialerProvider", Log.VERBOSE)) {
            Log.v("DialerProvider", "Rule " + rule.mName + ": " + url + " -> " + newUrl);
        }
        return newUrl;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri uri) {
        switch (sURIMatcher.match(uri)) {
        default:
            return null;

        case 0:
            return "vnd.android.cursor.item/contact";
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        StringBuilder sb = new StringBuilder("GoogleDialer ");
        try {
            sb.append(context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0).versionName);
            sb.append(" ");
            sb.append(Build.FINGERPRINT);
            mUserAgent = sb.toString();
            return true;
        }
        catch (PackageManager.NameNotFoundException e) {
            // TODO: Assuming a return false should be here
            return false;
        }
    }

    @Override
    public Cursor query(Uri uri, final String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        if (Log.isLoggable("DialerProvider", 2)) {
            Log.v("DialerProvider", "query: " + uri);
        }

        switch (sURIMatcher.match(uri)) {
        case 0:
            Context context = getContext();
            if (!GoogleLocationSettingHelper.isGoogleLocationServicesEnabled(context)
                    || !GoogleLocationSettingHelper.isSystemLocationSettingEnabled(context)) {
                if (Log.isLoggable("DialerProvider", Log.VERBOSE)) {
                    Log.v("DialerProvider", "Location settings is disabled, ignoring query.");
                }
                return null;
            }

            final Location lastLocation = getLastLocation();
            if (lastLocation == null) {
                if (Log.isLoggable("DialerProvider", Log.VERBOSE)) {
                    Log.v("DialerProvider", "No location available, ignoring query.");
                }
                return null;
            }

            final String filter = Uri.encode(uri.getLastPathSegment());
            String limit = uri.getQueryParameter("limit");

            try {
                final int limitInt;
                if (limit == null) {
                    limitInt = -1;
                } else {
                    limitInt = Integer.parseInt(limit);
                }

                return execute(new Callable<Cursor>() {
                    @Override
                    public Cursor call() {
                        return handleFilter(projection, filter, limitInt, lastLocation);
                    }
                }, "FilterThread", 10000L, TimeUnit.MILLISECONDS);
            } catch (NumberFormatException e) {
                Log.e("DialerProvider", "query: invalid limit parameter: '" + limit + "'");
            }

            break;
        }

        // TODO: Is this acceptable?
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    private class FutureCallable<T> implements Callable<T> {
        private final Callable<T> mCallable;
        private volatile FutureTask<T> mFuture;

        public FutureCallable(Callable<T> callable) {
            mFuture = null;
            mCallable = callable;
        }

        public T call() throws Exception {
            if (Log.isLoggable("DialerProvider", Log.VERBOSE)) {
                Log.v("DialerProvider", "Future called for "
                        + Thread.currentThread().getName());
            }

            T call = mCallable.call();
            if (mFuture == null) {
                return call;
            }

            synchronized (mActiveTasks) {
                mActiveTasks.remove(mFuture);
            }

            mFuture = null;
            return call;
        }

        public void setFuture(FutureTask<T> future) {
            mFuture = future;
        }
    }
}
