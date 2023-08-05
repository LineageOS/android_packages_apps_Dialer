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

import android.provider.CallLog.Calls;
import android.text.TextUtils;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Helper class to determine the callback action associated with a call in the call log. */
public class CallbackActionHelper {

  /** Specifies the action a user can take to make a callback. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({CallbackAction.NONE, CallbackAction.IMS_VIDEO, CallbackAction.VOICE})
  public @interface CallbackAction {
    int NONE = 0;
    int IMS_VIDEO = 1;
    int VOICE = 3;
  }

  /**
   * Returns the {@link CallbackAction} that can be associated with a call.
   *
   * @param number The phone number in column {@link android.provider.CallLog.Calls#NUMBER}.
   * @param features Value of features in column {@link android.provider.CallLog.Calls#FEATURES}.
   * @return One of the values in {@link CallbackAction}
   */
  public static @CallbackAction int getCallbackAction(
      String number, int features) {
    if (TextUtils.isEmpty(number)) {
      return CallbackAction.NONE;
    }

    boolean isVideoCall = (features & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO;
    if (isVideoCall) {
      return CallbackAction.IMS_VIDEO;
    }

    return CallbackAction.VOICE;
  }
}
