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

import android.annotation.TargetApi;
import android.content.AsyncQueryHandler;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.os.UserManagerCompat;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberColumns;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberTypes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FilteredNumberAsyncQueryHandler extends AsyncQueryHandler {

  public static final int INVALID_ID = -1;
  // Id used to replace null for blocked id since ConcurrentHashMap doesn't allow null key/value.
  @VisibleForTesting static final int BLOCKED_NUMBER_CACHE_NULL_ID = -1;

  @VisibleForTesting
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

  void hasBlockedNumbers(final OnHasBlockedNumbersListener listener) {
    if (!FilteredNumberCompat.canAttemptBlockOperations(context)) {
      listener.onHasBlockedNumbers(false);
      return;
    }
    startQuery(
        NO_TOKEN,
        new Listener() {
          @Override
          protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            listener.onHasBlockedNumbers(cursor != null && cursor.getCount() > 0);
          }
        },
        FilteredNumberCompat.getContentUri(context, null),
        new String[] {FilteredNumberCompat.getIdColumnName(context)},
        FilteredNumberCompat.useNewFiltering(context)
            ? null
            : FilteredNumberColumns.TYPE + "=" + FilteredNumberTypes.BLOCKED_NUMBER,
        null,
        null);
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
    if (!FilteredNumberCompat.canAttemptBlockOperations(context)) {
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
    String formattedNumber = FilteredNumbersUtil.getBlockableNumber(context, e164Number, number);
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
            // New filtering doesn't have a concept of type
            if (!FilteredNumberCompat.useNewFiltering(context)
                && cursor.getInt(cursor.getColumnIndex(FilteredNumberColumns.TYPE))
                    != FilteredNumberTypes.BLOCKED_NUMBER) {
              blockedNumberCache.put(number, BLOCKED_NUMBER_CACHE_NULL_ID);
              listener.onCheckComplete(null);
              return;
            }
            Integer blockedId = cursor.getInt(cursor.getColumnIndex(FilteredNumberColumns._ID));
            blockedNumberCache.put(number, blockedId);
            listener.onCheckComplete(blockedId);
          }
        },
        FilteredNumberCompat.getContentUri(context, null),
        FilteredNumberCompat.filter(
            new String[] {
              FilteredNumberCompat.getIdColumnName(context),
              FilteredNumberCompat.getTypeColumnName(context)
            }),
        getIsBlockedNumberSelection(e164Number != null) + " = ?",
        new String[] {formattedNumber},
        null);
  }

  /**
   * Synchronously check if this number has been blocked.
   *
   * @return blocked id.
   */
  @TargetApi(VERSION_CODES.M)
  @Nullable
  public Integer getBlockedIdSynchronous(@Nullable String number, String countryIso) {
    Assert.isWorkerThread();
    if (number == null) {
      return null;
    }
    if (!FilteredNumberCompat.canAttemptBlockOperations(context)) {
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
    String formattedNumber = FilteredNumbersUtil.getBlockableNumber(context, e164Number, number);
    if (TextUtils.isEmpty(formattedNumber)) {
      return null;
    }

    try (Cursor cursor =
        context
            .getContentResolver()
            .query(
                FilteredNumberCompat.getContentUri(context, null),
                FilteredNumberCompat.filter(
                    new String[] {
                      FilteredNumberCompat.getIdColumnName(context),
                      FilteredNumberCompat.getTypeColumnName(context)
                    }),
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
      int blockedId = cursor.getInt(cursor.getColumnIndex(FilteredNumberColumns._ID));
      blockedNumberCache.put(number, blockedId);
      return blockedId;
    } catch (SecurityException e) {
      LogUtil.e("FilteredNumberAsyncQueryHandler.getBlockedIdSynchronous", null, e);
      return null;
    }
  }

  @VisibleForTesting
  public void clearCache() {
    blockedNumberCache.clear();
  }

  /*
   * TODO: b/27779827, non-e164 numbers can be blocked in the new form of blocking. As a
   * temporary workaround, determine which column of the database to query based on whether the
   * number is e164 or not.
   */
  private String getIsBlockedNumberSelection(boolean isE164Number) {
    if (FilteredNumberCompat.useNewFiltering(context) && !isE164Number) {
      return FilteredNumberCompat.getOriginalNumberColumnName(context);
    }
    return FilteredNumberCompat.getE164NumberColumnName(context);
  }

  public void blockNumber(
      final OnBlockNumberListener listener, String number, @Nullable String countryIso) {
    blockNumber(listener, null, number, countryIso);
  }

  /** Add a number manually blocked by the user. */
  public void blockNumber(
      final OnBlockNumberListener listener,
      @Nullable String normalizedNumber,
      String number,
      @Nullable String countryIso) {
    blockNumber(
        listener,
        FilteredNumberCompat.newBlockNumberContentValues(
            context, number, normalizedNumber, countryIso));
  }

  /**
   * Block a number with specified ContentValues. Can be manually added or a restored row from
   * performing the 'undo' action after unblocking.
   */
  public void blockNumber(final OnBlockNumberListener listener, ContentValues values) {
    blockedNumberCache.clear();
    if (!FilteredNumberCompat.canAttemptBlockOperations(context)) {
      listener.onBlockComplete(null);
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
        FilteredNumberCompat.getContentUri(context, null),
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
    unblock(listener, FilteredNumberCompat.getContentUri(context, id));
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
    if (!FilteredNumberCompat.canAttemptBlockOperations(context)) {
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
            values.remove(FilteredNumberCompat.getIdColumnName(context));

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

  public interface OnCheckBlockedListener {

    /**
     * Invoked after querying if a number is blocked.
     *
     * @param id The ID of the row if blocked, null otherwise.
     */
    void onCheckComplete(Integer id);
  }

  public interface OnBlockNumberListener {

    /**
     * Invoked after inserting a blocked number.
     *
     * @param uri The uri of the newly created row.
     */
    void onBlockComplete(Uri uri);
  }

  public interface OnUnblockNumberListener {

    /**
     * Invoked after removing a blocked number
     *
     * @param rows The number of rows affected (expected value 1).
     * @param values The deleted data (used for restoration).
     */
    void onUnblockComplete(int rows, ContentValues values);
  }

  interface OnHasBlockedNumbersListener {

    /**
     * @param hasBlockedNumbers {@code true} if any blocked numbers are stored. {@code false}
     *     otherwise.
     */
    void onHasBlockedNumbers(boolean hasBlockedNumbers);
  }

  /** Methods for FilteredNumberAsyncQueryHandler result returns. */
  private abstract static class Listener {

    protected void onQueryComplete(int token, Object cookie, Cursor cursor) {}

    protected void onInsertComplete(int token, Object cookie, Uri uri) {}

    protected void onUpdateComplete(int token, Object cookie, int result) {}

    protected void onDeleteComplete(int token, Object cookie, int result) {}
  }
}
