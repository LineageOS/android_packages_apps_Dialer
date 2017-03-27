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

package com.android.incallui.spam;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.os.Build.VERSION_CODES;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.support.annotation.NonNull;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.AsyncTaskExecutor;
import com.android.dialer.common.concurrent.AsyncTaskExecutors;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.PermissionsUtil;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCall.CallHistoryStatus;
import java.util.Objects;

/** Checks if the number is in the call history. */
@TargetApi(VERSION_CODES.M)
public class NumberInCallHistoryTask extends AsyncTask<Void, Void, Integer> {

  public static final String TASK_ID = "number_in_call_history_status";

  private final Context context;
  private final Listener listener;
  private final String number;
  private final String countryIso;

  public NumberInCallHistoryTask(
      @NonNull Context context, @NonNull Listener listener, String number, String countryIso) {
    this.context = Objects.requireNonNull(context);
    this.listener = Objects.requireNonNull(listener);
    this.number = number;
    this.countryIso = countryIso;
  }

  public void submitTask() {
    if (!PermissionsUtil.hasPhonePermissions(context)) {
      return;
    }
    AsyncTaskExecutor asyncTaskExecutor = AsyncTaskExecutors.createThreadPoolExecutor();
    asyncTaskExecutor.submit(TASK_ID, this);
  }

  @Override
  @CallHistoryStatus
  public Integer doInBackground(Void... params) {
    String numberToQuery = number;
    String fieldToQuery = Calls.NUMBER;
    String normalizedNumber = PhoneNumberUtils.formatNumberToE164(number, countryIso);

    // If we can normalize the number successfully, look in "normalized_number"
    // field instead. Otherwise, look for number in "number" field.
    if (!TextUtils.isEmpty(normalizedNumber)) {
      numberToQuery = normalizedNumber;
      fieldToQuery = Calls.CACHED_NORMALIZED_NUMBER;
    }
    try (Cursor cursor =
        context
            .getContentResolver()
            .query(
                TelecomUtil.getCallLogUri(context),
                new String[] {CallLog.Calls._ID},
                fieldToQuery + " = ?",
                new String[] {numberToQuery},
                null)) {
      return cursor != null && cursor.getCount() > 0
          ? DialerCall.CALL_HISTORY_STATUS_PRESENT
          : DialerCall.CALL_HISTORY_STATUS_NOT_PRESENT;
    } catch (SQLiteException e) {
      LogUtil.e("NumberInCallHistoryTask.doInBackground", "query call log error", e);
      return DialerCall.CALL_HISTORY_STATUS_UNKNOWN;
    }
  }

  @Override
  public void onPostExecute(@CallHistoryStatus Integer callHistoryStatus) {
    listener.onComplete(callHistoryStatus);
  }

  /** Callback for the async task. */
  public interface Listener {

    void onComplete(@CallHistoryStatus int callHistoryStatus);
  }
}
