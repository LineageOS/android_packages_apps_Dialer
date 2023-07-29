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
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BlockedNumberContract;
import android.provider.BlockedNumberContract.BlockedNumbers;

import java.util.Objects;

/** TODO(calderwoodra): documentation */
@Deprecated
public class FilteredNumberAsyncQueryHandler extends AsyncQueryHandler {

  public static final int INVALID_ID = -1;

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
    if (!BlockedNumberContract.canCurrentUserBlockNumbers(context)) {
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

  /** TODO(calderwoodra): documentation */
  public interface OnBlockNumberListener {

    /**
     * Invoked after inserting a blocked number.
     *
     * @param uri The uri of the newly created row.
     */
    void onBlockComplete(Uri uri);
  }

  /** Methods for FilteredNumberAsyncQueryHandler result returns. */
  private abstract static class Listener {

    protected void onQueryComplete(int token, Object cookie, Cursor cursor) {}

    protected void onInsertComplete(int token, Object cookie, Uri uri) {}

    protected void onUpdateComplete(int token, Object cookie, int result) {}

    protected void onDeleteComplete(int token, Object cookie, int result) {}
  }
}
