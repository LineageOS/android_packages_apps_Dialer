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

package com.android.dialer.app.calllog;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.v4.os.BuildCompat;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.android.dialer.app.R;
import com.android.dialer.calllogutils.PhoneAccountUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.notification.DialerNotificationManager;
import com.android.dialer.notification.NotificationChannelManager;

/** Shows a notification in the status bar for legacy vociemail. */
@TargetApi(VERSION_CODES.O)
public final class LegacyVoicemailNotifier {
  private static final String NOTIFICATION_TAG_PREFIX = "LegacyVoicemail_";
  private static final String NOTIFICATION_TAG = "LegacyVoicemail";
  private static final int NOTIFICATION_ID = 1;

  /**
   * Replicates how packages/services/Telephony/NotificationMgr.java handles legacy voicemail
   * notification. The notification will not be stackable because no information is available for
   * individual voicemails.
   */
  public static void showNotification(
      @NonNull Context context,
      @NonNull PhoneAccountHandle handle,
      int count,
      String voicemailNumber,
      PendingIntent callVoicemailIntent,
      PendingIntent voicemailSettingsIntent,
      boolean isRefresh) {
    LogUtil.enterBlock("LegacyVoicemailNotifier.showNotification");
    Assert.isNotNull(handle);
    Assert.checkArgument(BuildCompat.isAtLeastO());

    TelephonyManager pinnedTelephonyManager =
        context.getSystemService(TelephonyManager.class).createForPhoneAccountHandle(handle);
    if (pinnedTelephonyManager == null) {
      LogUtil.e("LegacyVoicemailNotifier.showNotification", "invalid PhoneAccountHandle");
      return;
    }

    Notification notification =
        createNotification(
            context,
            pinnedTelephonyManager,
            handle,
            count,
            voicemailNumber,
            callVoicemailIntent,
            voicemailSettingsIntent,
            isRefresh);
    DialerNotificationManager.notify(
        context, getNotificationTag(context, handle), NOTIFICATION_ID, notification);
  }

  @NonNull
  private static Notification createNotification(
      @NonNull Context context,
      @NonNull TelephonyManager pinnedTelephonyManager,
      @NonNull PhoneAccountHandle handle,
      int count,
      String voicemailNumber,
      PendingIntent callVoicemailIntent,
      PendingIntent voicemailSettingsIntent,
      boolean isRefresh) {
    String notificationTitle =
        context
            .getResources()
            .getQuantityString(R.plurals.notification_voicemail_title, count, count);
    boolean isOngoing =
        pinnedTelephonyManager
            .getCarrierConfig()
            .getBoolean(CarrierConfigManager.KEY_VOICEMAIL_NOTIFICATION_PERSISTENT_BOOL);

    String contentText;
    PendingIntent contentIntent;
    if (!TextUtils.isEmpty(voicemailNumber) && callVoicemailIntent != null) {
      contentText = getNotificationText(context, handle, voicemailNumber);
      contentIntent = callVoicemailIntent;
    } else {
      contentText = context.getString(R.string.notification_voicemail_no_vm_number);
      contentIntent = voicemailSettingsIntent;
    }

    Notification.Builder builder =
        new Notification.Builder(context)
            .setSmallIcon(android.R.drawable.stat_notify_voicemail)
            .setColor(context.getColor(R.color.dialer_theme_color))
            .setWhen(System.currentTimeMillis())
            .setContentTitle(notificationTitle)
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setSound(pinnedTelephonyManager.getVoicemailRingtoneUri(handle))
            .setOngoing(isOngoing)
            .setOnlyAlertOnce(isRefresh)
            .setChannelId(NotificationChannelManager.getVoicemailChannelId(context, handle))
            .setDeleteIntent(
                CallLogNotificationsService.createLegacyVoicemailDismissedPendingIntent(
                    context, handle));

    if (pinnedTelephonyManager.isVoicemailVibrationEnabled(handle)) {
      builder.setDefaults(Notification.DEFAULT_VIBRATE);
    }

    return builder.build();
  }

  @NonNull
  private static String getNotificationText(
      @NonNull Context context, PhoneAccountHandle handle, String voicemailNumber) {
    if (PhoneAccountUtils.getSubscriptionPhoneAccounts(context).size() > 1) {
      TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
      PhoneAccount phoneAccount = telecomManager.getPhoneAccount(handle);
      return phoneAccount.getShortDescription().toString();
    } else {
      return String.format(
          context.getString(R.string.notification_voicemail_text_format),
          PhoneNumberUtils.formatNumber(voicemailNumber));
    }
  }

  public static void cancelNotification(
      @NonNull Context context, @NonNull PhoneAccountHandle phoneAccountHandle) {
    LogUtil.enterBlock("LegacyVoicemailNotifier.cancelNotification");
    Assert.checkArgument(BuildCompat.isAtLeastO());
    Assert.isNotNull(phoneAccountHandle);
    DialerNotificationManager.cancel(
        context, getNotificationTag(context, phoneAccountHandle), NOTIFICATION_ID);
  }

  @NonNull
  private static String getNotificationTag(
      @NonNull Context context, @NonNull PhoneAccountHandle phoneAccountHandle) {
    if (context.getSystemService(TelephonyManager.class).getPhoneCount() <= 1) {
      return NOTIFICATION_TAG;
    }
    return NOTIFICATION_TAG_PREFIX + phoneAccountHandle.getId();
  }

  private LegacyVoicemailNotifier() {}
}
