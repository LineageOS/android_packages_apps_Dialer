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

package com.android.dialer.blocking;

import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.net.Uri;
import android.provider.BlockedNumberContract.BlockedNumbers;
import android.support.annotation.Nullable;
import android.support.v4.os.UserManagerCompat;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** TODO(calderwoodra): documentation */
@Deprecated
public class FilteredNumberAsyncQueryHandler extends AsyncQueryHandler {

  public static final int INVALID_ID = -1;
  // Id used to replace null for blocked id since ConcurrentHashMap doesn't allow null key/value.
  static final int BLOCKED_NUMBER_CACHE_NULL_ID = -1;

  static final Map<String, Integer> blockedNumberCache = new ConcurrentHashMap<>();

  private static final int NO_TOKEN = 0;
  private final Context context;

  public FilteredNumberAsyncQueryHandler(Context context) {
    super(context.getContentResolver());
    this.context = context;
  }

  @Override
  protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
    try {
      if (cookie != null) {
        ((Listener) cookie).onQueryComplete(token, cookie, cursor);
      }
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
  }

  @Override
  protected void onInsertComplete(int token, Object cookie, Uri uri) {
    if (cookie != null) {
      ((Listener) cookie).onInsertComplete(token, cookie, uri);
    }
  }

  @Override
  protected void onUpdateComplete(int token, Object cookie, int result) {
    if (cookie != null) {
      ((Listener) cookie).onUpdateComplete(token, cookie, result);
    }
  }

  @Override
  protected void onDeleteComplete(int token, Object cookie, int result) {
    if (cookie != null) {
      ((Listener) cookie).onDeleteComplete(token, cookie, result);
    }
  }

  /**
   * Checks if the given number is blocked, calling the given {@link OnCheckBlockedListener} with
   * the id for the blocked number, {@link #INVALID_ID}, or {@code null} based on the result of the
   * check.
   */
  public void isBlockedNumber(
      final OnCheckBlockedListener listener, @Nullable final String number, String countryIso) {
    if (number == null) {
      listener.onCheckComplete(INVALID_ID);
      return;
    }
    if (!FilteredNumbersUtil.canAttemptBlockOperations(context)) {
      listener.onCheckComplete(null);
      return;
    }
    Integer cachedId = blockedNumberCache.get(number);
    if (cachedId != null) {
      if (listener == null) {
        return;
      }
      if (cachedId == BLOCKED_NUMBER_CACHE_NULL_ID) {
        cachedId = null;
      }
      listener.onCheckComplete(cachedId);
      return;
    }

    if (!UserManagerCompat.isUserUnlocked(context)) {
      LogUtil.i(
          "FilteredNumberAsyncQueryHandler.isBlockedNumber",
          "Device locked in FBE mode, cannot access blocked number database");
      listener.onCheckComplete(INVALID_ID);
      return;
    }

    String e164Number = PhoneNumberUtils.formatNumberToE164(number, countryIso);
    String formattedNumber = FilteredNumbersUtil.getBlockableNumber(e164Number, number);
    if (TextUtils.isEmpty(formattedNumber)) {
      listener.onCheckComplete(INVALID_ID);
      blockedNumberCache.put(number, INVALID_ID);
      return;
    }

    startQuery(
        NO_TOKEN,
        new Listener() {
          @Override
          protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            /*
             * In the frameworking blocking, numbers can be blocked in both e164 format
             * and not, resulting in multiple rows being returned for this query. For
             * example, both '16502530000' and '6502530000' can exist at the same time
             * and will be returned by this query.
             */
            if (cursor == null || cursor.getCount() == 0) {
              blockedNumberCache.put(number, BLOCKED_NUMBER_CACHE_NULL_ID);
              listener.onCheckComplete(null);
              return;
            }
            cursor.moveToFirst();
            Integer blockedId = cursor.getInt(cursor.getColumnIndex(BlockedNumbers.COLUMN_ID));
            blockedNumberCache.put(number, blockedId);
            listener.onCheckComplete(blockedId);
          }
        },
        BlockedNumbers.CONTENT_URI,
        new String[] {BlockedNumbers.COLUMN_ID},
        getIsBlockedNumberSelection(e164Number != null) + " = ?",
        new String[] {formattedNumber},
        null);
  }

  /**
   * Synchronously check if this number has been blocked.
   *
   * @return blocked id.
   */
  @Nullable
  public Integer getBlockedIdSynchronous(@Nullable String number, String countryIso) {
    Assert.isWorkerThread();
    if (number == null) {
      return null;
    }
    if (!FilteredNumbersUtil.canAttemptBlockOperations(context)) {
      return null;
    }
    Integer cachedId = blockedNumberCache.get(number);
    if (cachedId != null) {
      if (cachedId == BLOCKED_NUMBER_CACHE_NULL_ID) {
        cachedId = null;
      }
      return cachedId;
    }

    String e164Number = PhoneNumberUtils.formatNumberToE164(number, countryIso);
    String formattedNumber = FilteredNumbersUtil.getBlockableNumber(e164Number, number);
    if (TextUtils.isEmpty(formattedNumber)) {
      return null;
    }

    try (Cursor cursor =
        context
            .getContentResolver()
            .query(
                BlockedNumbers.CONTENT_URI,
                new String[] {BlockedNumbers.COLUMN_ID},
                getIsBlockedNumberSelection(e164Number != null) + " = ?",
                new String[] {formattedNumber},
                null)) {
      /*
       * In the frameworking blocking, numbers can be blocked in both e164 format
       * and not, resulting in multiple rows being returned for this query. For
       * example, both '16502530000' and '6502530000' can exist at the same time
       * and will be returned by this query.
       */
      if (cursor == null || cursor.getCount() == 0) {
        blockedNumberCache.put(number, BLOCKED_NUMBER_CACHE_NULL_ID);
        return null;
      }
      cursor.moveToFirst();
      int blockedId = cursor.getInt(cursor.getColumnIndex(BlockedNumbers.COLUMN_ID));
      blockedNumberCache.put(number, blockedId);
      return blockedId;
    } catch (SecurityException e) {
      LogUtil.e("FilteredNumberAsyncQueryHandler.getBlockedIdSynchronous", null, e);
      return null;
    }
  }

  public void clearCache() {
    blockedNumberCache.clear();
  }

  /*
   * TODO(maxwelb): a bug, non-e164 numbers can be blocked in the new form of blocking. As a
   * temporary workaround, determine which column of the database to query based on whether the
   * number is e164 or not.
   */
  private String getIsBlockedNumberSelection(boolean isE164Number) {
    if (!isE164Number) {
      return BlockedNumbers.COLUMN_ORIGINAL_NUMBER;
    }
    return BlockedNumbers.COLUMN_E164_NUMBER;
  }

  /** Add a number manually blocked by the user. */
  public void blockNumber(final OnBlockNumberListener listener, String number) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, Objects.requireNonNull(number));
    blockNumber(listener, contentValues);
  }

  /**
   * Block a number with specified ContentValues. Can be manually added or a restored row from
   * performing the 'undo' action after unblocking.
   */
  public void blockNumber(final OnBlockNumberListener listener, ContentValues values) {
    blockedNumberCache.clear();
    if (!FilteredNumbersUtil.canAttemptBlockOperations(context)) {
      if (listener != null) {
        listener.onBlockComplete(null);
      }
      return;
    }
    startInsert(
        NO_TOKEN,
        new Listener() {
          @Override
          public void onInsertComplete(int token, Object cookie, Uri uri) {
            if (listener != null) {
              listener.onBlockComplete(uri);
            }
          }
        },
        BlockedNumbers.CONTENT_URI,
        values);
  }

  /**
   * Unblocks the number with the given id.
   *
   * @param listener (optional) The {@link OnUnblockNumberListener} called after the number is
   *     unblocked.
   * @param id The id of the number to unblock.
   */
  public void unblock(@Nullable final OnUnblockNumberListener listener, Integer id) {
    if (id == null) {
      throw new IllegalArgumentException("Null id passed into unblock");
    }
    unblock(listener, ContentUris.withAppendedId(BlockedNumbers.CONTENT_URI, id));
  }

  /**
   * Removes row from database.
   *
   * @param listener (optional) The {@link OnUnblockNumberListener} called after the number is
   *     unblocked.
   * @param uri The uri of row to remove, from {@link FilteredNumberAsyncQueryHandler#blockNumber}.
   */
  public void unblock(@Nullable final OnUnblockNumberListener listener, final Uri uri) {
    blockedNumberCache.clear();
    if (!FilteredNumbersUtil.canAttemptBlockOperations(context)) {
      if (listener != null) {
        listener.onUnblockComplete(0, null);
      }
      return;
    }
    startQuery(
        NO_TOKEN,
        new Listener() {
          @Override
          public void onQueryComplete(int token, Object cookie, Cursor cursor) {
            int rowsReturned = cursor == null ? 0 : cursor.getCount();
            if (rowsReturned != 1) {
              throw new SQLiteDatabaseCorruptException(
                  "Returned " + rowsReturned + " rows for uri " + uri + "where 1 expected.");
            }
            cursor.moveToFirst();
            final ContentValues values = new ContentValues();
            DatabaseUtils.cursorRowToContentValues(cursor, values);
            values.remove(BlockedNumbers.COLUMN_ID);

            startDelete(
                NO_TOKEN,
                new Listener() {
                  @Override
                  public void onDeleteComplete(int token, Object cookie, int result) {
                    if (listener != null) {
                      listener.onUnblockComplete(result, values);
                    }
                  }
                },
                uri,
                null,
                null);
          }
        },
        uri,
        null,
        null,
        null,
        null);
  }

  /** TODO(calderwoodra): documentation */
  public interface OnCheckBlockedListener {

    /**
     * Invoked after querying if a number is blocked.
     *
     * @param id The ID of the row if blocked, null otherwise.
     */
    void onCheckComplete(Integer id);
  }

  /** TODO(calderwoodra): documentation */
  public interface OnBlockNumberListener {

    /**
     * Invoked after inserting a blocked number.
     *
     * @param uri The uri of the newly created row.
     */
    void onBlockComplete(Uri uri);
  }

  /** TODO(calderwoodra): documentation */
  public interface OnUnblockNumberListener {

    /**
     * Invoked after removing a blocked number
     *
     * @param rows The number of rows affected (expected value 1).
     * @param values The deleted data (used for restoration).
     */
    void onUnblockComplete(int rows, ContentValues values);
  }

  /** Methods for FilteredNumberAsyncQueryHandler result returns. */
  private abstract static class Listener {

    protected void onQueryComplete(int token, Object cookie, Cursor cursor) {}

    protected void onInsertComplete(int token, Object cookie, Uri uri) {}

    protected void onUpdateComplete(int token, Object cookie, int result) {}

    protected void onDeleteComplete(int token, Object cookie, int result) {}
  }
}
