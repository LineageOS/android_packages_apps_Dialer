/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.dialer.calllogutils;

import android.content.Context;
import android.content.Intent;
import android.provider.CallLog.Calls;
import android.support.annotation.Nullable;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.precall.PreCall;
import com.android.dialer.telecom.TelecomUtil;

/** Provides intents related to call log entries. */
public final class CallLogIntents {

  /**
   * Returns an intent which can be used to call back for the provided row.
   *
   * <p>If the call was a video call, a video call will be placed, and if the call was an audio
   * call, an audio call will be placed.
   *
   * @return null if the provided {@code row} doesn't have a number
   */
  @Nullable
  public static Intent getCallBackIntent(Context context, CoalescedRow row) {
    String normalizedNumber = row.number().getNormalizedNumber();
    if (!PhoneNumberHelper.canPlaceCallsTo(normalizedNumber, row.numberPresentation())) {
      return null;
    }

    // TODO(zachh): More granular logging?
    return PreCall.getIntent(
        context,
        new CallIntentBuilder(normalizedNumber, CallInitiationType.Type.CALL_LOG)
            .setPhoneAccountHandle(
                TelecomUtil.composePhoneAccountHandle(
                    row.phoneAccountComponentName(), row.phoneAccountId()))
            .setIsVideoCall((row.features() & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO));
  }
}
