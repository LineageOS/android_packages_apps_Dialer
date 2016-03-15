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
 * limitations under the License.
 */

package com.android.dialer.filterednumber;

import com.google.common.base.Preconditions;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.AsyncTask;
import android.support.annotation.Nullable;

import com.android.dialer.compat.BlockedNumbersSdkCompat;
import com.android.dialer.compat.FilteredNumberCompat;
import com.android.dialer.database.FilteredNumberContract;
import com.android.dialer.database.FilteredNumberContract.FilteredNumber;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberColumns;

/**
 * Class which should be used to migrate numbers from {@link FilteredNumberContract} blocking to
 * {@link android.provider.BlockedNumberContract} blocking.
 */
public class BlockedNumbersMigrator {

    /**
     * Listener for the operation to migrate from {@link FilteredNumberContract} blocking to
     * {@link android.provider.BlockedNumberContract} blocking.
     */
    public interface Listener {

        /**
         * Called when the migration operation is finished.
         */
        void onComplete();
    }

    private final ContentResolver mContentResolver;

    /**
     * Creates a new BlockedNumbersMigrate, using the given {@link ContentResolver} to perform
     * queries against the blocked numbers tables.
     *
     * @param contentResolver The ContentResolver
     * @throws NullPointerException if contentResolver is null
     */
    public BlockedNumbersMigrator(ContentResolver contentResolver) {
        mContentResolver = Preconditions.checkNotNull(contentResolver);
    }

    /**
     * Copies all of the numbers in the {@link FilteredNumberContract} block list to the
     * {@link android.provider.BlockedNumberContract} block list.
     *
     * @param listener {@link Listener} called once the migration is complete.
     * @return {@code true} if the migrate can be attempted, {@code false} otherwise.
     * @throws NullPointerException if listener is null
     */
    public boolean migrate(final Listener listener) {
        if (!FilteredNumberCompat.canUseNewFiltering()) {
            return false;
        }
        Preconditions.checkNotNull(listener);
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                return migrateToNewBlockingInBackground(mContentResolver);
            }

            @Override
            protected void onPostExecute(Boolean isSuccessful) {
                FilteredNumberCompat.setHasMigratedToNewBlocking(isSuccessful);
                listener.onComplete();
            }
        }.execute();
        return true;
    }

    private static boolean migrateToNewBlockingInBackground(ContentResolver resolver) {
        try (Cursor cursor = resolver.query(FilteredNumber.CONTENT_URI,
                new String[]{FilteredNumberColumns.NUMBER}, null, null, null)) {
            if (cursor == null) {
                return false;
            }

            while (cursor.moveToNext()) {
                String originalNumber = cursor
                        .getString(cursor.getColumnIndex(FilteredNumberColumns.NUMBER));
                if (isNumberInNewBlocking(resolver, originalNumber)) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put(BlockedNumbersSdkCompat.COLUMN_ORIGINAL_NUMBER, originalNumber);
                resolver.insert(BlockedNumbersSdkCompat.CONTENT_URI, values);
            }
            return true;
        }
    }

    private static boolean isNumberInNewBlocking(ContentResolver resolver, String originalNumber) {
        try (Cursor cursor = resolver.query(BlockedNumbersSdkCompat.CONTENT_URI,
                new String[]{BlockedNumbersSdkCompat._ID},
                BlockedNumbersSdkCompat.COLUMN_ORIGINAL_NUMBER + " = ?",
                new String[] {originalNumber}, null)) {
            return cursor != null && cursor.getCount() != 0;
        }
    }
}
