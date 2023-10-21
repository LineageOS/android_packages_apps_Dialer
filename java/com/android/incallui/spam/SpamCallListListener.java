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
 * limitations under the License
 */

package com.android.incallui.spam;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorFactory;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.PermissionsUtil;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCall.CallHistoryStatus;

/**
 * Creates notifications after a call ends if the call matched the criteria (incoming, accepted,
 * etc).
 */
public class SpamCallListListener implements CallList.Listener {
  private final Context context;
  private final DialerExecutorFactory dialerExecutorFactory;

  public SpamCallListListener(Context context, @NonNull DialerExecutorFactory factory) {
    this.context = context;
    this.dialerExecutorFactory = Assert.isNotNull(factory);
  }

  /** Checks if the number is in the call history. */
  private static final class NumberInCallHistoryWorker implements Worker<Void, Integer> {

    private final Context appContext;
    private final String number;
    private final String countryIso;

    public NumberInCallHistoryWorker(
        @NonNull Context appContext, String number, String countryIso) {
      this.appContext = Assert.isNotNull(appContext);
      this.number = number;
      this.countryIso = countryIso;
    }

    @Override
    @NonNull
    @CallHistoryStatus
    public Integer doInBackground(@Nullable Void input) throws Throwable {
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
          appContext
              .getContentResolver()
              .query(
                  TelecomUtil.getCallLogUri(appContext),
                  new String[] {CallLog.Calls._ID},
                  fieldToQuery + " = ?",
                  new String[] {numberToQuery},
                  null)) {
        return cursor != null && cursor.getCount() > 0
            ? DialerCall.CALL_HISTORY_STATUS_PRESENT
            : DialerCall.CALL_HISTORY_STATUS_NOT_PRESENT;
      } catch (SQLiteException e) {
        LogUtil.e("NumberInCallHistoryWorker.doInBackground", "query call log error", e);
        return DialerCall.CALL_HISTORY_STATUS_UNKNOWN;
      }
    }
  }

  @Override
  public void onIncomingCall(final DialerCall call) {
    String number = call.getNumber();
    if (TextUtils.isEmpty(number)) {
      return;
    }

    if (!PermissionsUtil.hasCallLogReadPermissions(context)) {
      LogUtil.i(
          "SpamCallListListener.onIncomingCall",
          "call log permission missing, not checking if number is in call history");
      return;
    }

    NumberInCallHistoryWorker historyTask =
        new NumberInCallHistoryWorker(context, number, call.getCountryIso());
    dialerExecutorFactory
        .createNonUiTaskBuilder(historyTask)
        .onSuccess(call::setCallHistoryStatus)
        .build()
        .executeParallel(null);
  }

  @Override
  public void onUpgradeToVideo(DialerCall call) {}

  @Override
  public void onSessionModificationStateChange(DialerCall call) {}

  @Override
  public void onCallListChange(CallList callList) {}

  @Override
  public void onWiFiToLteHandover(DialerCall call) {}

  @Override
  public void onHandoverToWifiFailed(DialerCall call) {}

  @Override
  public void onInternationalCallOnWifi(@NonNull DialerCall call) {}

  @Override
  public void onDisconnect(DialerCall call) {}
}
