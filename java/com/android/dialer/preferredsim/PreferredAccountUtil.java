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

package com.android.dialer.preferredsim;

import android.content.ComponentName;
import android.content.Context;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.dialer.common.LogUtil;
import com.google.common.collect.ImmutableSet;

import java.util.Optional;

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
  public static Optional<PhoneAccountHandle> getValidPhoneAccount(@NonNull Context context,
                                                                  @Nullable String
                                                                          componentNameString,
                                                                  @Nullable String idString) {
    if (TextUtils.isEmpty(componentNameString) || TextUtils.isEmpty(idString)) {
      LogUtil.i("PreferredAccountUtil.getValidPhoneAccount", "empty componentName or id");
      return Optional.empty();
    }
    ComponentName componentName = ComponentName.unflattenFromString(componentNameString);
    if (componentName == null) {
      LogUtil.e("PreferredAccountUtil.getValidPhoneAccount", "cannot parse component name");
      return Optional.empty();
    }
    PhoneAccountHandle phoneAccountHandle = new PhoneAccountHandle(componentName, idString);

    if (isPhoneAccountValid(context, phoneAccountHandle)) {
      return Optional.of(phoneAccountHandle);
    }
    return Optional.empty();
  }

  public static boolean isPhoneAccountValid(
      Context context, PhoneAccountHandle phoneAccountHandle) {
      return context
              .getSystemService(TelephonyManager.class)
              .createForPhoneAccountHandle(phoneAccountHandle)
          != null;
  }

  /**
   * Return a set of {@link android.accounts.Account#type} that is known to have writable contacts.
   * This is a light weight implementation of {@link
   * com.android.contacts.common.model.AccountTypeManager#getAccountTypes(boolean)}. External
   * accounts are not supported.
   */
  public static ImmutableSet<String> getValidAccountTypes() {
    return ImmutableSet.copyOf(
            (
                "com.google;"
                    + "com.osp.app.signin;"
                    + "com.android.exchange;"
                    + "com.google.android.exchange;"
                    + "com.google.android.gm.exchange")
            .split(";"));
  }
}
