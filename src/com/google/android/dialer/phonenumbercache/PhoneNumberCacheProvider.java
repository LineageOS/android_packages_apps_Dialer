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

package com.google.android.dialer.phonenumbercache;

import com.android.contacts.common.GeoUtil;
import com.google.android.dialer.GoogleDialerDatabaseHelper;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PathPermission;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class PhoneNumberCacheProvider extends ContentProvider {
    private static final String TAG = PhoneNumberCacheProvider.class.getSimpleName();

    private static final Set<String> SUPPORTED_UPDATE_COLUMNS = new HashSet<String>();
    private static final UriMatcher sUriMatcher = new UriMatcher(-1);
    private final String[] mArgs1;
    private GoogleDialerDatabaseHelper mDbHelper;
    private File mPhotoPath;
    private File mThumbnailPath;

    static {
        sUriMatcher.addURI("com.google.android.dialer.cacheprovider", "contact", 1000);
        sUriMatcher.addURI("com.google.android.dialer.cacheprovider", "contact/*", 1001);
        sUriMatcher.addURI("com.google.android.dialer.cacheprovider", "photo/*", 2000);
        sUriMatcher.addURI("com.google.android.dialer.cacheprovider", "thumbnail/*", 3000);
        SUPPORTED_UPDATE_COLUMNS.add("number");
        SUPPORTED_UPDATE_COLUMNS.add("phone_type");
        SUPPORTED_UPDATE_COLUMNS.add("phone_label");
        SUPPORTED_UPDATE_COLUMNS.add("display_name");
        SUPPORTED_UPDATE_COLUMNS.add("photo_uri");
        SUPPORTED_UPDATE_COLUMNS.add("source_name");
        SUPPORTED_UPDATE_COLUMNS.add("source_type");
        SUPPORTED_UPDATE_COLUMNS.add("source_id");
        SUPPORTED_UPDATE_COLUMNS.add("lookup_key");
    }

    public PhoneNumberCacheProvider() {
        mArgs1 = new String[1];
    }

    public PhoneNumberCacheProvider(Context context, String readPermission,
            String writePermission, PathPermission[] pathPermissions) {
        super(context, readPermission, writePermission, pathPermissions);
        mArgs1 = new String[1];
    }

    private void createDirectoryIfDoesNotExist(File file) {
        if (!file.exists() && !file.mkdirs()) {
            throw new RuntimeException(
                    "Unable to create photo storage directory " + file.getPath());
        }
    }

    private void createPhotoDirectoriesIfDoNotExist() {
        mPhotoPath = new File(getContext().getFilesDir(), "photos/raw");
        mThumbnailPath = new File(getContext().getFilesDir(), "thumbnails/raw");
        createDirectoryIfDoesNotExist(mPhotoPath);
        createDirectoryIfDoesNotExist(mThumbnailPath);
    }

    private boolean deleteFile(String number, boolean fullPhoto) {
        File file;
        if (fullPhoto) {
            file = getPhotoForNumber(number);
        } else {
            file = getThumbnailForNumber(number);
        }
        return file.delete();
    }

    private boolean deleteFiles(String number) {
        boolean deletedPhoto = deleteFile(number, true);
        boolean deletedThumbnail = deleteFile(number, false);
        if (!deletedPhoto && !deletedThumbnail) {
            return false;
        } else {
            return true;
        }
    }

    private String getE164Number(String number) {
        return PhoneNumberUtils.formatNumberToE164(number,
                GeoUtil.getCurrentCountryIso(getContext()));
    }

    private String getNumberFromUri(Uri uri) {
        if (uri.getPathSegments().size() != 2) {
            throw new IllegalArgumentException("Invalid URI or phone number not provided");
        }
        return getE164Number(uri.getLastPathSegment());
    }

    private String getNumberFromValues(ContentValues values) {
        String number = values.getAsString("number");
        if (number == null || number.length() == 0) {
            throw new IllegalArgumentException("Phone number not provided");
        }
        return getE164Number(number);
    }

    private File getPhotoForNumber(String number) {
        return new File(mPhotoPath, number);
    }

    private File getThumbnailForNumber(String number) {
        return new File(mThumbnailPath, number);
    }

    private boolean isNumberInCache(String number) {
        SQLiteDatabase database = mDbHelper.getReadableDatabase();
        mArgs1[0] = number;
        long entries = DatabaseUtils.queryNumEntries(database,
                "cached_number_contacts", "normalized_number=?", mArgs1);
        boolean inCache = false;
        if (entries > 0) {
            inCache = true;
        }
        return inCache;
    }

    private ParcelFileDescriptor openFileForRead(
            String number, boolean fullPhoto) throws FileNotFoundException {
        File file;
        if (fullPhoto) {
            file = getPhotoForNumber(number);
        } else {
            file = getThumbnailForNumber(number);
        }
        if (file.exists()) {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        }
        setHavePhoto(number, fullPhoto, false);
        throw new FileNotFoundException("No photo file found for number: " + number);
    }

    private ParcelFileDescriptor openFileForWrite(String number, boolean fullPhoto) {
        // TODO: Check if it's right. Decompiler completely screwed it up

        File file;
        if (fullPhoto) {
            file = getPhotoForNumber(number);
        } else {
            file = getThumbnailForNumber(number);
        }

        try {
            if (!file.exists()) {
                file.createNewFile();
                setHavePhoto(number, fullPhoto, true);
            }

            return ParcelFileDescriptor.open(file,
                    ParcelFileDescriptor.MODE_READ_WRITE);
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            Log.d(TAG, "IOException when attempting to create new file for cached photo.");
            return null;
        }
    }

    private void setHavePhoto(String number, boolean fullPhoto, boolean havePhoto) {
        String which = fullPhoto ? "has_photo" : "has_thumbnail";
        String have = havePhoto ? "1" : "0";

        SQLiteDatabase database = mDbHelper.getWritableDatabase();
        mArgs1[0] = number;
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE cached_number_contacts SET ");
        sb.append(which);
        sb.append("=");
        sb.append(have);
        sb.append(" WHERE ");
        sb.append("normalized_number=?");
        sb.append(";");

        database.execSQL(sb.toString(), mArgs1);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO: Where does 1001 come from?
        if (sUriMatcher.match(uri) == 1001) {
            mDbHelper.prune();

            String number = getNumberFromUri(uri);
            mArgs1[0] = number;
            SQLiteDatabase database = mDbHelper.getWritableDatabase();
            deleteFiles(number);
            return database.delete("cached_number_contacts", "normalized_number=?", mArgs1);
        }
        throw new IllegalArgumentException("Unknown URI or phone number not provided");
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO: What are the constants?
        int match = sUriMatcher.match(uri);
        switch (match) {
        default:
            throw new IllegalArgumentException("Unknown URI");
        case 1000:
        case 1001:
            String number;
            if (match == 1000) {
                number = getNumberFromValues(values);
            } else {
                number = getNumberFromUri(uri);
            }

            for (String key : values.keySet()) {
                if (!SUPPORTED_UPDATE_COLUMNS.contains(key)) {
                    values.remove(key);
                    Log.e(TAG, "Ignoring unsupported column for update: " + key);
                }
            }

            mDbHelper.prune();
            values.put("normalized_number", number);
            values.put("time_last_updated", System.currentTimeMillis());

            SQLiteDatabase database = mDbHelper.getWritableDatabase();
            mArgs1[0] = number;
            Integer type = values.getAsInteger("source_type");

            // TODO: What is n and n2?
            int n = 0;
            if (type != null && CachedNumberLookupServiceImpl.CachedContactInfoImpl
                    .isPeopleApiSource(type)) {
                n = 1;
            } else {
                int n2 = -1;
                try {
                    n2 = (int) DatabaseUtils.longForQuery(database,
                            "SELECT source_type FROM cached_number_contacts WHERE normalized_number=?",
                            mArgs1);
                    boolean peopleApiSource = CachedNumberLookupServiceImpl
                            .CachedContactInfoImpl.isPeopleApiSource(n2);

                    if (!peopleApiSource) {
                        n = 1;
                    }
                }
                catch (SQLiteDoneException ex) {
                    // TODO: What to do here? Decompiler broke code
                }
            }
            if (n != 0) {
                database.insertWithOnConflict("cached_number_contacts", null, values, 5);
            }
            return uri;
        }
    }

    @Override
    public boolean onCreate() {
        mDbHelper = GoogleDialerDatabaseHelper.getInstance(getContext());
        createPhotoDirectoriesIfDoNotExist();
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        int match = sUriMatcher.match(uri);
        switch (match) {
        default:
            throw new FileNotFoundException("Unknown or unsupported URI");
        case 2000:
        case 3000:
            String number = getNumberFromUri(uri);
            if (!isNumberInCache(number)) {
                throw new FileNotFoundException("Phone number does not exist in cache");
            }
            if (mode.equals("r")) {
                return openFileForRead(number, match == 2000);
            } else {
                return openFileForWrite(number, match == 2000);
            }
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        if (sUriMatcher.match(uri) == 1001) {
            String number = getNumberFromUri(uri);
            if (number != null) {
                mDbHelper.prune();
                mArgs1[0] = number;
                return mDbHelper.getWritableDatabase().query(
                        "cached_number_contacts", projection,
                        "normalized_number=?", mArgs1,
                        null, null, null);
            }
        }
        return null;
    }

    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException(
                "The cache does not support update operations."
                + " Use insert to replace an existing phone number, if needed.");
    }
}
