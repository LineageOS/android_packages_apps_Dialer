/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.dialer.database;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.PhoneNumberUtils;

import com.android.dialer.database.FilteredNumberContract.FilteredNumber;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberColumns;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberSources;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberTypes;

public class FilteredNumberAsyncQueryHandler extends AsyncQueryHandler {
    private static final int NO_TOKEN = 0;

    public FilteredNumberAsyncQueryHandler(ContentResolver cr) {
        super(cr);
    }

    /**
     * Methods for FilteredNumberAsyncQueryHandler result returns.
     */
    private static abstract class Listener {
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
        }
        protected void onInsertComplete(int token, Object cookie, Uri uri) {
        }
        protected void onUpdateComplete(int token, Object cookie, int result) {
        }
        protected void onDeleteComplete(int token, Object cookie, int result) {
        }
    }

    public interface OnCheckBlockedListener {
        public void onQueryComplete(Integer id);
    }

    public interface OnBlockNumberListener {
        public void onInsertComplete(Uri uri);
    }

    public interface OnUnblockNumberListener {
        public void onDeleteComplete(int rows);
    }

    @Override
    protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
        ((Listener) cookie).onQueryComplete(token, cookie, cursor);
    }

    @Override
    protected void onInsertComplete(int token, Object cookie, Uri uri) {
        ((Listener) cookie).onInsertComplete(token, cookie, uri);
    }

    @Override
    protected void onUpdateComplete(int token, Object cookie, int result) {
        ((Listener) cookie).onUpdateComplete(token, cookie, result);
    }

    @Override
    protected void onDeleteComplete(int token, Object cookie, int result) {
        ((Listener) cookie).onDeleteComplete(token, cookie, result);
    }

    private static Uri getContentUri(Integer id) {
        Uri uri = FilteredNumber.CONTENT_URI;
        if (id != null) {
            uri = ContentUris.withAppendedId(uri, id);
        }
        return uri;
    }

    /**
     * Check if the number + country iso given has been blocked.
     * This method normalizes the number for the lookup if normalizedNumber is null.
     * Returns to the listener the the ID of the row if blocked, null otherwise.
     */
    public final void isBlocked(final OnCheckBlockedListener listener,
                                String normalizedNumber, String number, String countryIso) {
        if (normalizedNumber == null) {
            normalizedNumber = PhoneNumberUtils.formatNumberToE164(number, countryIso);
            if (normalizedNumber == null) {
                throw new IllegalArgumentException("Invalid phone number");
            }
        }
        isBlocked(listener, normalizedNumber);
    }

    /**
     * Check if the normalized number given has been blocked.
     * Returns to the listener  the ID of the row if blocked, null otherwise.
     */
    public final void isBlocked(final OnCheckBlockedListener listener,
                                       String normalizedNumber) {
        startQuery(NO_TOKEN,
                new Listener() {
                    @Override
                    protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                        if (cursor.getCount() != 1) {
                            listener.onQueryComplete(null);
                            return;
                        }
                        cursor.moveToFirst();
                        if (cursor.getInt(cursor.getColumnIndex(FilteredNumberColumns.TYPE))
                                != FilteredNumberTypes.BLOCKED_NUMBER) {
                            listener.onQueryComplete(null);
                            return;
                        }
                        listener.onQueryComplete(
                                cursor.getInt(cursor.getColumnIndex(FilteredNumberColumns._ID)));
                    }
                },
                getContentUri(null),
                new String[]{FilteredNumberColumns._ID, FilteredNumberColumns.TYPE},
                FilteredNumberColumns.NORMALIZED_NUMBER + " = ?",
                new String[]{normalizedNumber},
                null);
    }

    /**
     * Add a number manually blocked by the user.
     * Returns to the listener the URL of the newly created row.
     */
    public final void blockNumber(final OnBlockNumberListener listener,
                                  String number, String countryIso) {
        blockNumber(listener,
                PhoneNumberUtils.formatNumberToE164(number, countryIso), number, countryIso);
    }

    /**
     * Add a number manually blocked by the user.
     * Returns to the listener the URL of the newly created row.
     */
    public final void blockNumber(final OnBlockNumberListener listener,
                                        String normalizedNumber, String number, String countryIso) {
        if (normalizedNumber == null) {
            blockNumber(listener, number, countryIso);
        }
        ContentValues v = new ContentValues();
        v.put(FilteredNumberColumns.NORMALIZED_NUMBER, normalizedNumber);
        v.put(FilteredNumberColumns.NUMBER, number);
        v.put(FilteredNumberColumns.COUNTRY_ISO, countryIso);
        v.put(FilteredNumberColumns.TYPE, FilteredNumberTypes.BLOCKED_NUMBER);
        v.put(FilteredNumberColumns.SOURCE, FilteredNumberSources.USER);
        startInsert(NO_TOKEN,
                new Listener() {
                    @Override
                    public void onInsertComplete(int token, Object cookie, Uri uri) {
                        listener.onInsertComplete(uri);
                    }
                }, getContentUri(null), v);
    }

    /**
     * Removes row from database.
     * Caller should call {@link FilteredNumberAsyncQueryHandler#isBlocked} first.
     * @param id the ID of the row to remove, from {@link FilteredNumberAsyncQueryHandler#isBlocked}.
     * Returns to the listener the number of rows affected. Expected value is 1.
     */
    public final void unblock(final OnUnblockNumberListener listener, Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("Null id passed into unblock");
        }
        startDelete(NO_TOKEN, new Listener() {
            @Override
            public void onDeleteComplete(int token, Object cookie, int result) {
                listener.onDeleteComplete(result);
            }
        }, getContentUri(id), null, null);
    }
}
