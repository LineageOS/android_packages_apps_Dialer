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
 * limitations under the License
 */

package com.android.dialer.precall.impl;

import android.content.ComponentName;
import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.android.dialer.common.LogUtil;
import com.google.common.base.Optional;

/**
 * Utilities for looking up and validating preferred {@link PhoneAccountHandle}. Contacts should
 * follow the same logic.
 */
public class PreferredAccountUtil {

  /**
   * Validates {@code componentNameString} and {@code idString} maps to SIM that is present on the
   * device.
   */
  @NonNull
  public static Optional<PhoneAccountHandle> getValidPhoneAccount(
      @NonNull Context context, @Nullable String componentNameString, @Nullable String idString) {
    if (TextUtils.isEmpty(componentNameString) || TextUtils.isEmpty(idString)) {
      LogUtil.i("PreferredAccountUtil.getValidPhoneAccount", "empty componentName or id");
      return Optional.absent();
    }
    ComponentName componentName = ComponentName.unflattenFromString(componentNameString);
    if (componentName == null) {
      LogUtil.e("PreferredAccountUtil.getValidPhoneAccount", "cannot parse component name");
      return Optional.absent();
    }
    PhoneAccountHandle phoneAccountHandle = new PhoneAccountHandle(componentName, idString);

    if (isPhoneAccountValid(context, phoneAccountHandle)) {
      return Optional.of(phoneAccountHandle);
    }
    return Optional.absent();
  }

  private static boolean isPhoneAccountValid(
      Context context, PhoneAccountHandle phoneAccountHandle) {
    if (VERSION.SDK_INT >= VERSION_CODES.O) {
      return context
              .getSystemService(TelephonyManager.class)
              .createForPhoneAccountHandle(phoneAccountHandle)
          != null;
    }

    PhoneAccount phoneAccount =
        context.getSystemService(TelecomManager.class).getPhoneAccount(phoneAccountHandle);
    if (phoneAccount == null) {
      LogUtil.e("PreferredAccountUtil.isPhoneAccountValid", "invalid phone account");
      return false;
    }

    if (!phoneAccount.isEnabled()) {
      LogUtil.e("PreferredAccountUtil.isPhoneAccountValid", "disabled phone account");
      return false;
    }
    for (SubscriptionInfo info :
        SubscriptionManager.from(context).getActiveSubscriptionInfoList()) {
      if (phoneAccountHandle.getId().startsWith(info.getIccId())) {
        LogUtil.i("PreferredAccountUtil.isPhoneAccountValid", "sim found");
        return true;
      }
    }
    return false;
  }
}
