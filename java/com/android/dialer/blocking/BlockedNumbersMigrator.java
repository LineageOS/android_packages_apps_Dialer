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

package com.android.dialer.blocking;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build.VERSION_CODES;
import android.provider.BlockedNumberContract.BlockedNumbers;
import android.support.annotation.RequiresApi;
import com.android.dialer.common.LogUtil;
import com.android.dialer.database.FilteredNumberContract;
import com.android.dialer.database.FilteredNumberContract.FilteredNumber;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberColumns;
import java.util.Objects;

/**
 * Class which should be used to migrate numbers from {@link FilteredNumberContract} blocking to
 * {@link android.provider.BlockedNumberContract} blocking.
 */
@TargetApi(VERSION_CODES.M)
public class BlockedNumbersMigrator {

  private final Context context;

  /**
   * Creates a new BlockedNumbersMigrate, using the given {@link ContentResolver} to perform queries
   * against the blocked numbers tables.
   */
  public BlockedNumbersMigrator(Context context) {
    this.context = Objects.requireNonNull(context);
  }

  @RequiresApi(VERSION_CODES.N)
  @TargetApi(VERSION_CODES.N)
  private static boolean migrateToNewBlockingInBackground(ContentResolver resolver) {
    try (Cursor cursor =
        resolver.query(
            FilteredNumber.CONTENT_URI,
            new String[] {FilteredNumberColumns.NUMBER},
            null,
            null,
            null)) {
      if (cursor == null) {
        LogUtil.i(
            "BlockedNumbersMigrator.migrateToNewBlockingInBackground", "migrate - cursor was null");
        return false;
      }

      LogUtil.i(
          "BlockedNumbersMigrator.migrateToNewBlockingInBackground",
          "migrate - attempting to migrate " + cursor.getCount() + "numbers");

      int numMigrated = 0;
      while (cursor.moveToNext()) {
        String originalNumber =
            cursor.getString(cursor.getColumnIndex(FilteredNumberColumns.NUMBER));
        if (isNumberInNewBlocking(resolver, originalNumber)) {
          LogUtil.i(
              "BlockedNumbersMigrator.migrateToNewBlockingInBackground",
              "migrate - number was already blocked in new blocking");
          continue;
        }
        ContentValues values = new ContentValues();
        values.put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, originalNumber);
        resolver.insert(BlockedNumbers.CONTENT_URI, values);
        ++numMigrated;
      }
      LogUtil.i(
          "BlockedNumbersMigrator.migrateToNewBlockingInBackground",
          "migrate - migration complete. " + numMigrated + " numbers migrated.");
      return true;
    }
  }

  @RequiresApi(VERSION_CODES.N)
  @TargetApi(VERSION_CODES.N)
  private static boolean isNumberInNewBlocking(ContentResolver resolver, String originalNumber) {
    try (Cursor cursor =
        resolver.query(
            BlockedNumbers.CONTENT_URI,
            new String[] {BlockedNumbers.COLUMN_ID},
            BlockedNumbers.COLUMN_ORIGINAL_NUMBER + " = ?",
            new String[] {originalNumber},
            null)) {
      return cursor != null && cursor.getCount() != 0;
    }
  }

  /**
   * Copies all of the numbers in the {@link FilteredNumberContract} block list to the {@link
   * android.provider.BlockedNumberContract} block list.
   *
   * @param listener {@link Listener} called once the migration is complete.
   * @return {@code true} if the migrate can be attempted, {@code false} otherwise.
   * @throws NullPointerException if listener is null
   */
  public boolean migrate(final Listener listener) {
    LogUtil.i("BlockedNumbersMigrator.migrate", "migrate - start");
    if (!FilteredNumberCompat.canUseNewFiltering()) {
      LogUtil.i("BlockedNumbersMigrator.migrate", "migrate - can't use new filtering");
      return false;
    }
    Objects.requireNonNull(listener);
    new MigratorTask(listener).execute();
    return true;
  }

  /**
   * Listener for the operation to migrate from {@link FilteredNumberContract} blocking to {@link
   * android.provider.BlockedNumberContract} blocking.
   */
  public interface Listener {

    /** Called when the migration operation is finished. */
    void onComplete();
  }

  @TargetApi(VERSION_CODES.N)
  private class MigratorTask extends AsyncTask<Void, Void, Boolean> {

    private final Listener listener;

    public MigratorTask(Listener listener) {
      this.listener = listener;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
      LogUtil.i("BlockedNumbersMigrator.doInBackground", "migrate - start background migration");
      return migrateToNewBlockingInBackground(context.getContentResolver());
    }

    @Override
    protected void onPostExecute(Boolean isSuccessful) {
      LogUtil.i("BlockedNumbersMigrator.onPostExecute", "migrate - marking migration complete");
      FilteredNumberCompat.setHasMigratedToNewBlocking(context, isSuccessful);
      LogUtil.i("BlockedNumbersMigrator.onPostExecute", "migrate - calling listener");
      listener.onComplete();
    }
  }
}
