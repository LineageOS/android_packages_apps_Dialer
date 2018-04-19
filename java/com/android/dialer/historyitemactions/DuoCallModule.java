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
import com.android.dialer.duo.PlaceDuoCallNotifier;

/** {@link HistoryItemActionModule} for making a Duo call. */
public class DuoCallModule implements HistoryItemActionModule {

  private final Context context;
  private final String phoneNumber;

  /**
   * Creates a module for making a Duo call.
   *
   * @param phoneNumber The number to start a Duo call. It can be of any format.
   */
  public DuoCallModule(Context context, String phoneNumber) {
    this.context = context;
    this.phoneNumber = phoneNumber;
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
    PlaceDuoCallNotifier.notify(context, phoneNumber);
    return true; // Close the bottom sheet.
  }
}
