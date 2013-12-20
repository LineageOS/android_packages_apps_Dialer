/*
 * Copyright (C) 2013, The Linux Foundation. All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:
 * Redistributions of source code must retain the above copyright
          notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above
          copyright notice, this list of conditions and the following
          disclaimer in the documentation and/or other materials provided
          with the distribution.
 * Neither the name of The Linux Foundation, Inc. nor the names of its
          contributors may be used to endorse or promote products derived
          from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.dialer.calllog;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.util.Log;

import java.util.ArrayList;

public class CallLogSearchProvider extends ContentProvider {

    private static final String TAG = "CallLogSearchProvider";
    public static String AUTHORITY = "com.android.dialer.calllog.calllogsearchprovider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/dictionary");
    private String[] mSuggestColumns = new String[] {
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
            SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2
    };
    private final int SUGGEST_DATA_COLUMN_INDEX = 0;
    private final int SUGGEST_ACTION_COLUMN_INDEX = 1;
    private final int SUGGEST_EXTRA_DATA_COLUMN_INDEX = 2;
    private final int SUGGEST_TEXT_COLUMN_INDEX = 3;
    private final int SUGGEST_TEXT2_COLUMN_INDEX = 4;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Log.d(TAG, "query selectionArgs[0]: " + selectionArgs[0]);
        String select = "(" + Calls.NUMBER + " like '%" + selectionArgs[0] + "%'  or  "
                + Calls.CACHED_NAME
                + " like '%" + selectionArgs[0] + "%' )";
        Cursor cursor = getContext().getContentResolver().query(
                Calls.CONTENT_URI,
                new String[] {
                        "_id", Calls.NUMBER, Calls.CACHED_NAME
                },
                select,
                null,
                null);
        if (null == cursor) {
            return null;
        }

        return new SuggestionsCursor(cursor);
    }

    private class SuggestionsCursor extends CursorWrapper {
        int mColumnCount;
        ArrayList<Row> mRows = new ArrayList<Row>();

        public SuggestionsCursor(Cursor cursor) {
            super(cursor);
            mColumnCount = cursor.getColumnCount();
            fillRows();
        }

        public int getCount() {
            return mRows.size();
        }

        public int getType(int columnIndex) {
            return -1;
        }

        private class Row {
            String mName;
            String mNumber;

            public Row(String name, String number) {
                mName = name;
                mNumber = number;
            }

            public String getLine1() {
                if (mName != null && !mName.equals("")) {
                    return mName;
                } else {
                    return mNumber;
                }
            }

            public String getLine2() {
                if (mName != null && !mName.equals("")) {
                    return mNumber;
                } else {
                    return null;
                }
            }
        }

        private void fillRows() {
            int nameColumn = mCursor.getColumnIndex(Calls.CACHED_NAME);
            int numberColumn = mCursor.getColumnIndex(Calls.NUMBER);
            int count = mCursor.getCount();
            for (int i = 0; i < count; i++) {
                mCursor.moveToPosition(i);
                String name = mCursor.getString(nameColumn);
                String number = mCursor.getString(numberColumn);
                mRows.add(new Row(name, number));
            }
        }

        public int getColumnCount() {
            return mColumnCount + mSuggestColumns.length;
        }

        public int getColumnIndex(String columnName) {
            for (int i = 0; i < mSuggestColumns.length; i++) {
                if (mSuggestColumns[i].equals(columnName)) {
                    return mColumnCount + i;
                }
            }
            return mCursor.getColumnIndex(columnName);
        }

        public String[] getColumnNames() {
            String[] x = mCursor.getColumnNames();
            String[] y = new String[x.length + mSuggestColumns.length];

            for (int i = 0; i < x.length; i++) {
                y[i] = x[i];
            }
            for (int i = 0; i < mSuggestColumns.length; i++) {
                y[x.length + i] = mSuggestColumns[i];
            }

            return y;
        }

        public String getString(int column) {
            Log.d(TAG, "getString column" + column);
            if (column < mColumnCount) {
                return mCursor.getString(column);
            }

            Row row = mRows.get(mCursor.getPosition());
            switch (column - mColumnCount) {
                case SUGGEST_DATA_COLUMN_INDEX:
                    Uri uri = Calls.CONTENT_URI.buildUpon()
                            .appendQueryParameter("id", mCursor.getString(0)).build();
                    return uri.toString();
                case SUGGEST_ACTION_COLUMN_INDEX:
                    return Intent.ACTION_SEARCH;
                case SUGGEST_EXTRA_DATA_COLUMN_INDEX:
                    return getString(getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_1));
                case SUGGEST_TEXT_COLUMN_INDEX:
                    return row.getLine1();
                case SUGGEST_TEXT2_COLUMN_INDEX:
                    return row.getLine2();
                default:
                    return null;
            }
        }
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
