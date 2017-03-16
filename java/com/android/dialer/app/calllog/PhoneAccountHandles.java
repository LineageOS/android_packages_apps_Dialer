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
