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

package com.android.dialer.duo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.constants.ActivityRequestCodes;

/** A {@link BroadcastReceiver} that starts a Duo video call. */
public final class PlaceDuoCallReceiver extends BroadcastReceiver {

  static final String ACTION_START_DUO_CALL = "start_duo_call";
  static final String EXTRA_PHONE_NUMBER = "phone_number";

  /**
   * {@link Activity} needed to launch Duo.
   *
   * <p>A Duo call can only be placed via {@link Activity#startActivityForResult(Intent, int)}.
   */
  private final Activity activity;

  /** Returns an {@link IntentFilter} containing all actions accepted by this broadcast receiver. */
  public static IntentFilter getIntentFilter() {
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(ACTION_START_DUO_CALL);
    return intentFilter;
  }

  public PlaceDuoCallReceiver(Activity activity) {
    this.activity = activity;
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    LogUtil.enterBlock("PlaceDuoCallReceiver.onReceive");

    String action = intent.getAction();

    switch (Assert.isNotNull(action)) {
      case ACTION_START_DUO_CALL:
        startDuoCall(context, intent);
        break;
      default:
        throw new IllegalStateException("Unsupported action: " + action);
    }
  }

  private void startDuoCall(Context context, Intent intent) {
    LogUtil.enterBlock("PlaceDuoCallReceiver.startDuoCall");

    Assert.checkArgument(intent.hasExtra(EXTRA_PHONE_NUMBER));
    String phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER);

    Duo duo = DuoComponent.get(context).getDuo();
    activity.startActivityForResult(
        duo.getCallIntent(phoneNumber).orNull(), ActivityRequestCodes.DIALTACTS_DUO);
  }
}
