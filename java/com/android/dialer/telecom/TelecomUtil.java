/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.dialer.telecom;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.content.ContextCompat;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Pair;
import com.android.dialer.common.LogUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Performs permission checks before calling into TelecomManager. Each method is self-explanatory -
 * perform the required check and return the fallback default if the permission is missing,
 * otherwise return the value from TelecomManager.
 */
public abstract class TelecomUtil {

  private static final String TAG = "TelecomUtil";
  private static boolean sWarningLogged = false;

  private static TelecomUtilImpl instance = new TelecomUtilImpl();

  /**
   * Cache for {@link #isVoicemailNumber(Context, PhoneAccountHandle, String)}. Both
   * PhoneAccountHandle and number are cached because multiple numbers might be mapped to true, and
   * comparing with {@link #getVoicemailNumber(Context, PhoneAccountHandle)} will not suffice.
   */
  private static final Map<Pair<PhoneAccountHandle, String>, Boolean> isVoicemailNumberCache =
      new ConcurrentHashMap<>();

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  public static void setInstanceForTesting(TelecomUtilImpl instanceForTesting) {
    instance = instanceForTesting;
  }

  public static void showInCallScreen(Context context, boolean showDialpad) {
    if (hasReadPhoneStatePermission(context)) {
      try {
        getTelecomManager(context).showInCallScreen(showDialpad);
      } catch (SecurityException e) {
        // Just in case
        LogUtil.w(TAG, "TelecomManager.showInCallScreen called without permission.");
      }
    }
  }

  public static void silenceRinger(Context context) {
    if (hasModifyPhoneStatePermission(context)) {
      try {
        getTelecomManager(context).silenceRinger();
      } catch (SecurityException e) {
        // Just in case
        LogUtil.w(TAG, "TelecomManager.silenceRinger called without permission.");
      }
    }
  }

  public static void cancelMissedCallsNotification(Context context) {
    if (hasModifyPhoneStatePermission(context)) {
      try {
        getTelecomManager(context).cancelMissedCallsNotification();
      } catch (SecurityException e) {
        LogUtil.w(TAG, "TelecomManager.cancelMissedCalls called without permission.");
      }
    }
  }

  public static Uri getAdnUriForPhoneAccount(Context context, PhoneAccountHandle handle) {
    if (hasModifyPhoneStatePermission(context)) {
      try {
        return getTelecomManager(context).getAdnUriForPhoneAccount(handle);
      } catch (SecurityException e) {
        LogUtil.w(TAG, "TelecomManager.getAdnUriForPhoneAccount called without permission.");
      }
    }
    return null;
  }

  public static boolean handleMmi(
      Context context, String dialString, @Nullable PhoneAccountHandle handle) {
    if (hasModifyPhoneStatePermission(context)) {
      try {
        if (handle == null) {
          return getTelecomManager(context).handleMmi(dialString);
        } else {
          return getTelecomManager(context).handleMmi(dialString, handle);
        }
      } catch (SecurityException e) {
        LogUtil.w(TAG, "TelecomManager.handleMmi called without permission.");
      }
    }
    return false;
  }

  @Nullable
  public static PhoneAccountHandle getDefaultOutgoingPhoneAccount(
      Context context, String uriScheme) {
    if (hasReadPhoneStatePermission(context)) {
      return getTelecomManager(context).getDefaultOutgoingPhoneAccount(uriScheme);
    }
    return null;
  }

  public static PhoneAccount getPhoneAccount(Context context, PhoneAccountHandle handle) {
    return getTelecomManager(context).getPhoneAccount(handle);
  }

  public static List<PhoneAccountHandle> getCallCapablePhoneAccounts(Context context) {
    if (hasReadPhoneStatePermission(context)) {
      return getTelecomManager(context).getCallCapablePhoneAccounts();
    }
    return new ArrayList<>();
  }

  public static boolean isInCall(Context context) {
    return instance.isInCall(context);
  }

  /**
   * {@link TelecomManager#isVoiceMailNumber(PhoneAccountHandle, String)} takes about 10ms, which is
   * way too slow for regular purposes. This method will cache the result for the life time of the
   * process. The cache will not be invalidated, for example, if the voicemail number is changed by
   * setting up apps like Google Voicemail, the result will be wrong. These events are rare.
   */
  public static boolean isVoicemailNumber(
      Context context, PhoneAccountHandle accountHandle, String number) {
    if (TextUtils.isEmpty(number)) {
      return false;
    }
    Pair<PhoneAccountHandle, String> cacheKey = new Pair<>(accountHandle, number);
    if (isVoicemailNumberCache.containsKey(cacheKey)) {
      return isVoicemailNumberCache.get(cacheKey);
    }
    boolean result = false;
    if (hasReadPhoneStatePermission(context)) {
      result = getTelecomManager(context).isVoiceMailNumber(accountHandle, number);
    }
    isVoicemailNumberCache.put(cacheKey, result);
    return result;
  }

  @Nullable
  public static String getVoicemailNumber(Context context, PhoneAccountHandle accountHandle) {
    if (hasReadPhoneStatePermission(context)) {
      return getTelecomManager(context).getVoiceMailNumber(accountHandle);
    }
    return null;
  }

  /**
   * Tries to place a call using the {@link TelecomManager}.
   *
   * @param context context.
   * @param intent the call intent.
   * @return {@code true} if we successfully attempted to place the call, {@code false} if it failed
   *     due to a permission check.
   */
  public static boolean placeCall(Context context, Intent intent) {
    if (hasCallPhonePermission(context)) {
      getTelecomManager(context).placeCall(intent.getData(), intent.getExtras());
      return true;
    }
    return false;
  }

  public static Uri getCallLogUri(Context context) {
    return hasReadWriteVoicemailPermissions(context)
        ? Calls.CONTENT_URI_WITH_VOICEMAIL
        : Calls.CONTENT_URI;
  }

  public static boolean hasReadWriteVoicemailPermissions(Context context) {
    return isDefaultDialer(context)
        || (hasPermission(context, Manifest.permission.READ_VOICEMAIL)
            && hasPermission(context, Manifest.permission.WRITE_VOICEMAIL));
  }

  public static boolean hasModifyPhoneStatePermission(Context context) {
    return isDefaultDialer(context)
        || hasPermission(context, Manifest.permission.MODIFY_PHONE_STATE);
  }

  public static boolean hasReadPhoneStatePermission(Context context) {
    return isDefaultDialer(context) || hasPermission(context, Manifest.permission.READ_PHONE_STATE);
  }

  public static boolean hasCallPhonePermission(Context context) {
    return isDefaultDialer(context) || hasPermission(context, Manifest.permission.CALL_PHONE);
  }

  private static boolean hasPermission(Context context, String permission) {
    return instance.hasPermission(context, permission);
  }

  private static TelecomManager getTelecomManager(Context context) {
    return (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
  }

  public static boolean isDefaultDialer(Context context) {
    return instance.isDefaultDialer(context);
  }

  /** Contains an implementation for {@link TelecomUtil} methods */
  @VisibleForTesting()
  public static class TelecomUtilImpl {

    public boolean isInCall(Context context) {
      if (hasReadPhoneStatePermission(context)) {
        return getTelecomManager(context).isInCall();
      }
      return false;
    }

    public boolean hasPermission(Context context, String permission) {
      return ContextCompat.checkSelfPermission(context, permission)
          == PackageManager.PERMISSION_GRANTED;
    }

    public boolean isDefaultDialer(Context context) {
      final boolean result =
          TextUtils.equals(
              context.getPackageName(), getTelecomManager(context).getDefaultDialerPackage());
      if (result) {
        sWarningLogged = false;
      } else {
        if (!sWarningLogged) {
          // Log only once to prevent spam.
          LogUtil.w(TAG, "Dialer is not currently set to be default dialer");
          sWarningLogged = true;
        }
      }
      return result;
    }
  }
}
