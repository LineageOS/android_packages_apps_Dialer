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

import android.app.Activity;
import android.provider.CallLog.Calls;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.precall.PreCall;

/** Actions which can be performed on a call log row. */
public final class CallLogRowActions {

  /**
   * Places a call to the number in the provided {@link CoalescedRow}.
   *
   * <p>If the call was a video call, a video call will be placed, and if the call was an audio
   * call, an audio call will be placed. The phone account corresponding to the row is used.
   */
  public static void startCallForRow(Activity activity, CoalescedRow row) {
    // TODO(zachh): More granular logging?
    PreCall.start(
        activity,
        new CallIntentBuilder(
                row.getNumber().getNormalizedNumber(), CallInitiationType.Type.CALL_LOG)
            .setIsVideoCall((row.getFeatures() & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO)
            .setIsDuoCall(
                DuoComponent.get(activity)
                    .getDuo()
                    .isDuoAccount(row.getPhoneAccountComponentName())));
  }
}
