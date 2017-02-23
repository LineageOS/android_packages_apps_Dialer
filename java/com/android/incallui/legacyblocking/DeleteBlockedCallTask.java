/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.incallui.legacyblocking;

import android.Manifest.permission;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build.VERSION_CODES;
import android.provider.CallLog;
import android.support.v4.content.ContextCompat;
import com.android.dialer.common.LogUtil;
import com.android.dialer.telecom.TelecomUtil;
import java.util.Objects;

/**
 * Deletes a blocked call from the call log. This is only used on Android Marshmallow. On later
 * versions of the OS, call blocking is implemented in the system and there's no need to mess with
 * the call log.
 */
@TargetApi(VERSION_CODES.M)
public class DeleteBlockedCallTask extends AsyncTask<Void, Void, Long> {

  public static final String IDENTIFIER = "DeleteBlockedCallTask";

  // Try to identify if a call log entry corresponds to a number which was blocked. We match by
  // by comparing its creation time to the time it was added in the InCallUi and seeing if they
  // fall within a certain threshold.
  private static final int MATCH_BLOCKED_CALL_THRESHOLD_MS = 3000;

  private final Context context;
  private final Listener listener;
  private final String number;
  private final long timeAddedMillis;

  /**
   * Creates the task to delete the new {@link CallLog} entry from the given blocked number.
   *
   * @param number The blocked number.
   * @param timeAddedMillis The time at which the call from the blocked number was placed.
   */
  public DeleteBlockedCallTask(
      Context context, Listener listener, String number, long timeAddedMillis) {
    this.context = Objects.requireNonNull(context);
    this.listener = Objects.requireNonNull(listener);
    this.number = number;
    this.timeAddedMillis = timeAddedMillis;
  }

  @Override
  public Long doInBackground(Void... params) {
    if (ContextCompat.checkSelfPermission(context, permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        || ContextCompat.checkSelfPermission(context, permission.WRITE_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) {
      LogUtil.i("DeleteBlockedCallTask.doInBackground", "missing call log permissions");
      return -1L;
    }

    // First, lookup the call log entry of the most recent call with this number.
    try (Cursor cursor =
        context
            .getContentResolver()
            .query(
                TelecomUtil.getCallLogUri(context),
                CallLogDeleteBlockedCallQuery.PROJECTION,
                CallLog.Calls.NUMBER + "= ?",
                new String[] {number},
                CallLog.Calls.DATE + " DESC LIMIT 1")) {

      // If match is found, delete this call log entry and return the call log entry id.
      if (cursor != null && cursor.moveToFirst()) {
        long creationTime = cursor.getLong(CallLogDeleteBlockedCallQuery.DATE_COLUMN_INDEX);
        if (timeAddedMillis > creationTime
            && timeAddedMillis - creationTime < MATCH_BLOCKED_CALL_THRESHOLD_MS) {
          long callLogEntryId = cursor.getLong(CallLogDeleteBlockedCallQuery.ID_COLUMN_INDEX);
          context
              .getContentResolver()
              .delete(
                  TelecomUtil.getCallLogUri(context),
                  CallLog.Calls._ID + " IN (" + callLogEntryId + ")",
                  null);
          return callLogEntryId;
        }
      }
    }
    return -1L;
  }

  @Override
  public void onPostExecute(Long callLogEntryId) {
    listener.onDeleteBlockedCallTaskComplete(callLogEntryId >= 0);
  }

  /** Callback invoked when delete is complete. */
  public interface Listener {

    void onDeleteBlockedCallTaskComplete(boolean didFindEntry);
  }

  private static class CallLogDeleteBlockedCallQuery {

    static final String[] PROJECTION = new String[] {CallLog.Calls._ID, CallLog.Calls.DATE};

    static final int ID_COLUMN_INDEX = 0;
    static final int DATE_COLUMN_INDEX = 1;
  }
}
