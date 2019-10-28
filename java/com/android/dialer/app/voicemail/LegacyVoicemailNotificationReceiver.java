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

package com.android.dialer.app.voicemail;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.support.annotation.VisibleForTesting;
import android.support.v4.os.BuildCompat;
import android.support.v4.os.UserManagerCompat;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import com.android.dialer.app.calllog.LegacyVoicemailNotifier;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.PerAccountSharedPreferences;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.storage.StorageComponent;
import com.android.voicemail.VoicemailClient;
import com.android.voicemail.VoicemailComponent;

/**
 * Receives {@link TelephonyManager#ACTION_SHOW_VOICEMAIL_NOTIFICATION}, and forwards to {@link
 * LegacyVoicemailNotifier}. Will ignore the notification if the account has visual voicemail.
 * Legacy voicemail is the traditional, non-visual, dial-in voicemail.
 */
@TargetApi(VERSION_CODES.O)
public class LegacyVoicemailNotificationReceiver extends BroadcastReceiver {

  @VisibleForTesting static final String LEGACY_VOICEMAIL_DISMISSED = "legacy_voicemail_dismissed";

  @Override
  public void onReceive(Context context, Intent intent) {

    if (!TelephonyManager.ACTION_SHOW_VOICEMAIL_NOTIFICATION.equals(intent.getAction())
        && !VoicemailClient.ACTION_SHOW_LEGACY_VOICEMAIL.equals(intent.getAction())) {
      return;
    }

    LogUtil.i(
        "LegacyVoicemailNotificationReceiver.onReceive", "received legacy voicemail notification");
    if (!BuildCompat.isAtLeastO()) {
      LogUtil.e(
          "LegacyVoicemailNotificationReceiver.onReceive",
          "SDK not finalized: SDK_INT="
              + Build.VERSION.SDK_INT
              + ", PREVIEW_SDK_INT="
              + Build.VERSION.PREVIEW_SDK_INT
              + ", RELEASE="
              + Build.VERSION.RELEASE_OR_CODENAME);
      return;
    }

    PhoneAccountHandle phoneAccountHandle =
        Assert.isNotNull(intent.getParcelableExtra(TelephonyManager.EXTRA_PHONE_ACCOUNT_HANDLE));
    int count = intent.getIntExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, -1);

    boolean isRefresh = intent.getBooleanExtra(TelephonyManagerCompat.EXTRA_IS_REFRESH, false);
    LogUtil.i("LegacyVoicemailNotificationReceiver.onReceive", "isRefresh: " + isRefresh);
    PerAccountSharedPreferences preferences = getSharedPreferences(context, phoneAccountHandle);
    if (isRefresh) {
      if (preferences.getBoolean(LEGACY_VOICEMAIL_DISMISSED, false)) {
        LogUtil.i(
            "LegacyVoicemailNotificationReceiver.onReceive",
            "notification dismissed, ignoring refresh");
        return;
      }
    } else {
      setDismissed(context, phoneAccountHandle, false);
    }

    if (count == -1) {
      // Carrier might not send voicemail count. Missing extra means there are unknown numbers of
      // voicemails (One or more). Treat it as 1 so the generic version will be shown. ("Voicemail"
      // instead of "X voicemails")
      count = 1;
    }

    if (count == 0) {
      LogUtil.i("LegacyVoicemailNotificationReceiver.onReceive", "clearing notification");
      LegacyVoicemailNotifier.cancelNotification(context, phoneAccountHandle);
      return;
    }

    if (!intent.getBooleanExtra(VoicemailClient.EXTRA_IS_LEGACY_MODE, false)
        && UserManagerCompat.isUserUnlocked(context)
        && VoicemailComponent.get(context)
            .getVoicemailClient()
            .isActivated(context, phoneAccountHandle)) {
      LogUtil.i(
          "LegacyVoicemailNotificationReceiver.onReceive",
          "visual voicemail is activated, ignoring notification");
      return;
    }

    String voicemailNumber = intent.getStringExtra(TelephonyManager.EXTRA_VOICEMAIL_NUMBER);
    PendingIntent callVoicemailIntent =
        intent.getParcelableExtra(TelephonyManager.EXTRA_CALL_VOICEMAIL_INTENT);
    PendingIntent voicemailSettingIntent =
        intent.getParcelableExtra(TelephonyManager.EXTRA_LAUNCH_VOICEMAIL_SETTINGS_INTENT);

    LogUtil.i("LegacyVoicemailNotificationReceiver.onReceive", "sending notification");
    LegacyVoicemailNotifier.showNotification(
        context,
        phoneAccountHandle,
        count,
        voicemailNumber,
        callVoicemailIntent,
        voicemailSettingIntent,
        isRefresh);
  }

  public static void setDismissed(
      Context context, PhoneAccountHandle phoneAccountHandle, boolean dismissed) {
    getSharedPreferences(context, phoneAccountHandle)
        .edit()
        .putBoolean(LEGACY_VOICEMAIL_DISMISSED, dismissed)
        .apply();
  }

  @VisibleForTesting
  static PerAccountSharedPreferences getSharedPreferences(
      Context context, PhoneAccountHandle phoneAccountHandle) {
    return new PerAccountSharedPreferences(
        context, phoneAccountHandle, StorageComponent.get(context).unencryptedSharedPrefs());
  }
}
