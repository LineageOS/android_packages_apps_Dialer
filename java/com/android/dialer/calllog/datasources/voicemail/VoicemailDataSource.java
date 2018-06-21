/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.calllog.datasources.voicemail;

import android.content.ContentValues;
import android.content.Context;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.calllog.datasources.CallLogMutations;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.PermissionsUtil;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map.Entry;
import javax.inject.Inject;

/** Provide information for whether the call is a call to the voicemail inbox. */
public class VoicemailDataSource implements CallLogDataSource {

  private final Context appContext;
  private final ListeningExecutorService backgroundExecutor;

  @Inject
  VoicemailDataSource(
      @ApplicationContext Context appContext,
      @BackgroundExecutor ListeningExecutorService backgroundExecutor) {
    this.appContext = appContext;
    this.backgroundExecutor = backgroundExecutor;
  }

  @Override
  public ListenableFuture<Boolean> isDirty() {
    // The isVoicemail status is immutable and permanent. The call will always show as "Voicemail"
    // even if the SIM is swapped. Dialing the row will result in some unexpected number after a SIM
    // swap but this is deemed acceptable.
    return Futures.immediateFuture(false);
  }

  @Override
  @SuppressWarnings("missingPermission")
  public ListenableFuture<Void> fill(CallLogMutations mutations) {
    if (!PermissionsUtil.hasReadPhoneStatePermissions(appContext)) {
      for (Entry<Long, ContentValues> insert : mutations.getInserts().entrySet()) {
        insert.getValue().put(AnnotatedCallLog.IS_VOICEMAIL_CALL, 0);
      }
      return Futures.immediateFuture(null);
    }

    return backgroundExecutor.submit(
        () -> {
          TelecomManager telecomManager = appContext.getSystemService(TelecomManager.class);
          for (Entry<Long, ContentValues> insert : mutations.getInserts().entrySet()) {
            ContentValues values = insert.getValue();
            PhoneAccountHandle phoneAccountHandle =
                TelecomUtil.composePhoneAccountHandle(
                    values.getAsString(AnnotatedCallLog.PHONE_ACCOUNT_COMPONENT_NAME),
                    values.getAsString(AnnotatedCallLog.PHONE_ACCOUNT_ID));
            DialerPhoneNumber dialerPhoneNumber;
            try {
              dialerPhoneNumber =
                  DialerPhoneNumber.parseFrom(values.getAsByteArray(AnnotatedCallLog.NUMBER));
            } catch (InvalidProtocolBufferException e) {
              throw new IllegalStateException(e);
            }

            if (telecomManager.isVoiceMailNumber(
                phoneAccountHandle, dialerPhoneNumber.getNormalizedNumber())) {
              values.put(AnnotatedCallLog.IS_VOICEMAIL_CALL, 1);
              TelephonyManager telephonyManager =
                  TelephonyManagerCompat.getTelephonyManagerForPhoneAccountHandle(
                      appContext, phoneAccountHandle);
              values.put(
                  AnnotatedCallLog.VOICEMAIL_CALL_TAG, telephonyManager.getVoiceMailAlphaTag());
            } else {
              values.put(AnnotatedCallLog.IS_VOICEMAIL_CALL, 0);
            }
          }
          return null;
        });
  }

  @Override
  public ListenableFuture<Void> onSuccessfulFill() {
    return Futures.immediateFuture(null);
  }

  @Override
  public void registerContentObservers() {}

  @Override
  public void unregisterContentObservers() {}

  @Override
  public ListenableFuture<Void> clearData() {
    return Futures.immediateFuture(null);
  }

  @Override
  public String getLoggingName() {
    return "VoicemailDataSource";
  }
}
