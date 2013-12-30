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

package com.google.android.dialer;

import com.android.dialer.database.DialerDatabaseHelper;
import com.android.internal.annotations.VisibleForTesting;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class GoogleDialerDatabaseHelper extends DialerDatabaseHelper {
    private static final String TAG = GoogleDialerDatabaseHelper.class.getSimpleName();
    private static GoogleDialerDatabaseHelper sSingleton = null;
    private final String[] mArgs1;

    protected GoogleDialerDatabaseHelper(Context context, String databaseName) {
        super(context, databaseName, 60004);
        mArgs1 = new String[1];
    }

    public static synchronized GoogleDialerDatabaseHelper getInstance(Context context) {
        if (sSingleton == null) {
            sSingleton = new GoogleDialerDatabaseHelper(
                    context.getApplicationContext(), "dialer.db");
        }
        return sSingleton;
    }

    @VisibleForTesting
    static GoogleDialerDatabaseHelper getNewInstanceForTest(Context context) {
        return new GoogleDialerDatabaseHelper(context, null);
    }

    private void setupTables(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS cached_number_contacts");
        db.execSQL("CREATE TABLE " + "cached_number_contacts" + " (" +
                "normalized_number" + " TEXT PRIMARY KEY NOT NULL, " +
                "number" + " TEXT NOT NULL, " +
                "phone_type" + " INTEGER DEFAULT 0, " +
                "phone_label" + " TEXT,display_name TEXT, " +
                "has_photo" + " INTEGER DEFAULT 0, " +
                "has_thumbnail" + " INTEGER DEFAULT 0, " +
                "photo_uri" + " TEXT, " +
                "time_last_updated" + " LONG NOT NULL, " +
                "source_name" + " TEXT, " +
                "source_type" + " INTEGER DEFAULT 0, " +
                "source_id" + " TEXT, " +
                "lookup_key" + " TEXT" +
        ");");
        db.execSQL("CREATE INDEX " + "cached_number_index"
                + " ON " + "cached_number_contacts (normalized_number);");
        setProperty(db, "proprietary_database_version", String.valueOf(6));
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        super.onCreate(db);
        setupTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldNumber, int newNumber) {
        super.onUpgrade(db, oldNumber, newNumber);
        int version = getPropertyAsInt(db, "proprietary_database_version", 0);
        if (version == 0) {
            Log.e(TAG, "Malformed database version..recreating database");
        }
        if (version < 6) {
            setupTables(db);
        } else {
            setProperty(db, "proprietary_database_version", String.valueOf(6));
        }
    }

    public void prune() {
        prune(2592000000L);
    }

    public void prune(long timestamp) {
        mArgs1[0] = Long.toString(System.currentTimeMillis() - timestamp);
        getWritableDatabase().execSQL(
                "DELETE FROM cached_number_contacts WHERE time_last_updated<?", mArgs1);
    }

    public void purgeAll() {
        getWritableDatabase().execSQL("DELETE FROM cached_number_contacts");
    }

    public void purgeSource(int type) {
        mArgs1[0] = Integer.toString(type);
        getWritableDatabase().execSQL(
                "DELETE FROM cached_number_contacts WHERE source_type=?", mArgs1);
    }
}
