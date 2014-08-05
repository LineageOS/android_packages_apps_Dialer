/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.services.callrecorder;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.provider.BaseColumns;
import android.util.Log;

import com.android.services.callrecorder.common.CallRecording;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent data store for call recordings.  Usage:
 * open()
 * read/write operations
 * close()
 */
public class CallRecordingDataStore {
    private static final String TAG = "CallRecordingStore";
    private SQLiteOpenHelper mOpenHelper = null;
    private SQLiteDatabase mDatabase = null;

    /**
     * Open before reading/writing.  Will not open handle if one is already open.
     */
    public void open(Context context) {
        if (mDatabase == null) {
            mOpenHelper = new CallRecordingSQLiteOpenHelper(context);
            mDatabase = mOpenHelper.getWritableDatabase();
        }
    }

    /**
     * close when finished reading/writing
     */
    public void close() {
        if (mDatabase != null) {
            mDatabase.close();
        }
        if (mOpenHelper != null) {
            mOpenHelper.close();
        }
        mDatabase = null;
        mOpenHelper = null;
    }

    /**
     * Save a recording in the data store
     *
     * @param recording the recording to store
     */
    public void putRecording(CallRecording recording) {
        final String insertSql = "INSERT INTO " +
                CallRecordingsContract.CallRecording.TABLE_NAME + " (" +
                CallRecordingsContract.CallRecording.COLUMN_NAME_PHONE_NUMBER + ", " +
                CallRecordingsContract.CallRecording.COLUMN_NAME_CALL_DATE + ", " +
                CallRecordingsContract.CallRecording.COLUMN_NAME_RECORDING_FILENAME + ", " +
                CallRecordingsContract.CallRecording.COLUMN_NAME_CREATION_DATE + ") " +
                " VALUES (?, ?, ?, ?)";

        try {
            SQLiteStatement stmt = mDatabase.compileStatement(insertSql);
            int idx = 1;
            stmt.bindString(idx++, recording.phoneNumber);
            stmt.bindLong(idx++, recording.creationTime);
            stmt.bindString(idx++, recording.fileName);
            stmt.bindLong(idx++, System.currentTimeMillis());
            long id = stmt.executeInsert();
            Log.i(TAG, "Saved recording " + recording + " with id " + id);
        } catch (SQLiteException e) {
            Log.w(TAG, "Failed to save recording " + recording, e);
        }
    }

    /**
     * Get all recordings associated with a phone call
     *
     * @param phoneNumber phone number no spaces
     * @param callCreationDate time that the call was created
     * @return list of recordings
     */
    public List<CallRecording> getRecordings(String phoneNumber, long callCreationDate) {
        List<CallRecording> resultList = new ArrayList<CallRecording>();

        final String query = "SELECT " +
                CallRecordingsContract.CallRecording.COLUMN_NAME_RECORDING_FILENAME +
                " FROM " + CallRecordingsContract.CallRecording.TABLE_NAME +
                " WHERE " + CallRecordingsContract.CallRecording.COLUMN_NAME_PHONE_NUMBER + " = ?" +
                " AND " + CallRecordingsContract.CallRecording.COLUMN_NAME_CALL_DATE + " = ?" +
                " ORDER BY " + CallRecordingsContract.CallRecording.COLUMN_NAME_CREATION_DATE;

        String args[] = {
            phoneNumber, String.valueOf(callCreationDate)
        };

        try {
            Cursor cursor = mDatabase.rawQuery(query, args);
            while (cursor.moveToNext()) {
                String fileName = cursor.getString(0);
                CallRecording recording =
                        new CallRecording(phoneNumber, callCreationDate, fileName, 0);
                if (recording.getFile().exists()) {
                    resultList.add(recording);
                }
            }
            cursor.close();
        } catch (SQLiteException e) {
            Log.w(TAG, "Failed to fetch recordings for number " + phoneNumber +
                ", date " + callCreationDate, e);
        }

        return resultList;
    }

    static class CallRecordingsContract {
        static interface CallRecording extends BaseColumns {
            static final String TABLE_NAME = "call_recordings";
            static final String COLUMN_NAME_PHONE_NUMBER = "phone_number";
            static final String COLUMN_NAME_CALL_DATE = "call_date";
            static final String COLUMN_NAME_RECORDING_FILENAME = "recording_filename";
            static final String COLUMN_NAME_CREATION_DATE = "creation_date";
        }
    }

    static class CallRecordingSQLiteOpenHelper extends SQLiteOpenHelper {
        private static final int VERSION = 1;
        private static final String DB_NAME = "callrecordings.db";

        public CallRecordingSQLiteOpenHelper(Context context) {
            super(context, DB_NAME, null, VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + CallRecordingsContract.CallRecording.TABLE_NAME + " (" +
                CallRecordingsContract.CallRecording._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                CallRecordingsContract.CallRecording.COLUMN_NAME_PHONE_NUMBER + " TEXT," +
                CallRecordingsContract.CallRecording.COLUMN_NAME_CALL_DATE + " LONG," +
                CallRecordingsContract.CallRecording.COLUMN_NAME_RECORDING_FILENAME + " TEXT, " +
                CallRecordingsContract.CallRecording.COLUMN_NAME_CREATION_DATE + " LONG" +
                ");"
            );

            db.execSQL("CREATE INDEX IF NOT EXISTS phone_number_call_date_index ON " +
                CallRecordingsContract.CallRecording.TABLE_NAME + " (" +
                CallRecordingsContract.CallRecording.COLUMN_NAME_PHONE_NUMBER + ", " +
                CallRecordingsContract.CallRecording.COLUMN_NAME_CALL_DATE + ");"
            );
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // implement if we change the schema
        }
    }
}
