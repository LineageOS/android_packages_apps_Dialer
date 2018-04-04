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

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import com.android.dialer.common.LogUtil;

/** Notifies that a Duo video call should be started. */
public final class PlaceDuoCallNotifier {

  private PlaceDuoCallNotifier() {}

  /**
   * Broadcasts an intent notifying that a Duo call should be started.
   *
   * <p>See {@link PlaceDuoCallReceiver} for how the intent is handled.
   *
   * @param phoneNumber The number to start a Duo call. It can be of any format.
   */
  public static void notify(Context context, String phoneNumber) {
    LogUtil.enterBlock("PlaceDuoCallNotifier.notify");

    Intent intent = new Intent();
    intent.setAction(PlaceDuoCallReceiver.ACTION_START_DUO_CALL);
    intent.putExtra(PlaceDuoCallReceiver.EXTRA_PHONE_NUMBER, phoneNumber);

    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
  }
}
