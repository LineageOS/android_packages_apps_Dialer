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
package com.android.dialer.app.calllog;

import android.content.ComponentName;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.app.calllog.CallLogNotificationsQueryHelper.NewCall;
import com.android.dialer.common.LogUtil;
import com.android.dialer.telecom.TelecomUtil;
import java.util.List;

/** Methods to help extract {@link PhoneAccount} information from database and Telecomm sources. */
class PhoneAccountHandles {

  @Nullable
  public static PhoneAccountHandle getAccount(@NonNull Context context, @Nullable NewCall call) {
    PhoneAccountHandle handle;
    if (call == null || call.accountComponentName == null || call.accountId == null) {
      LogUtil.v(
          "PhoneAccountUtils.getAccount",
          "accountComponentName == null || callToNotify.accountId == null");
      handle = TelecomUtil.getDefaultOutgoingPhoneAccount(context, PhoneAccount.SCHEME_TEL);
      if (handle == null) {
        List<PhoneAccountHandle> callCapablePhoneAccounts =
            TelecomUtil.getCallCapablePhoneAccounts(context);
        if (!callCapablePhoneAccounts.isEmpty()) {
          return callCapablePhoneAccounts.get(0);
        }
        return null;
      }
    } else {
      handle =
          new PhoneAccountHandle(
              ComponentName.unflattenFromString(call.accountComponentName), call.accountId);
    }
    if (handle.getComponentName() != null) {
      LogUtil.v(
          "PhoneAccountUtils.getAccount",
          "PhoneAccountHandle.ComponentInfo:" + handle.getComponentName());
    } else {
      LogUtil.i("PhoneAccountUtils.getAccount", "PhoneAccountHandle.ComponentInfo: null");
    }
    return handle;
  }
}
