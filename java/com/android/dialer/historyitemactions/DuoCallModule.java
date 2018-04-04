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
 * limitations under the License.
 */

package com.android.dialer.historyitemactions;

import android.Manifest.permission;
import android.content.Context;
import android.support.annotation.RequiresPermission;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.duo.Duo;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.duo.PlaceDuoCallNotifier;
import com.android.dialer.precall.PreCall;

/** {@link HistoryItemActionModule} for making a Duo call. */
public class DuoCallModule implements HistoryItemActionModule {

  private final Context context;
  private final String phoneNumber;
  private final CallInitiationType.Type callInitiationType;

  /**
   * Creates a module for making a Duo call.
   *
   * @param phoneNumber The number to start a Duo call. It can be of any format.
   */
  public DuoCallModule(
      Context context, String phoneNumber, CallInitiationType.Type callInitiationType) {
    this.context = context;
    this.phoneNumber = phoneNumber;
    this.callInitiationType = callInitiationType;
  }

  @Override
  public int getStringId() {
    return R.string.video_call;
  }

  @Override
  public int getDrawableId() {
    return R.drawable.quantum_ic_videocam_vd_white_24;
  }

  @Override
  @RequiresPermission(permission.READ_PHONE_STATE)
  public boolean onClick() {
    if (canPlaceDuoCall(context, phoneNumber)) {
      PlaceDuoCallNotifier.notify(context, phoneNumber);
    } else {
      // If a Duo call can't be placed, fall back to an IMS video call.
      PreCall.start(
          context, new CallIntentBuilder(phoneNumber, callInitiationType).setIsVideoCall(true));
    }

    return true; // Close the bottom sheet.
  }

  private boolean canPlaceDuoCall(Context context, String phoneNumber) {
    Duo duo = DuoComponent.get(context).getDuo();

    return duo.isInstalled(context)
        && duo.isEnabled(context)
        && duo.isActivated(context)
        && duo.isReachable(context, phoneNumber);
  }
}
