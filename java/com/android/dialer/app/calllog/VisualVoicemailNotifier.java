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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.service.notification.StatusBarNotification;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.os.BuildCompat;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.dialer.app.DialtactsActivity;
import com.android.dialer.app.R;
import com.android.dialer.app.calllog.CallLogNotificationsQueryHelper.NewCall;
import com.android.dialer.app.contactinfo.ContactPhotoLoader;
import com.android.dialer.app.list.DialtactsPagerAdapter;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.notification.NotificationChannelManager;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.telecom.TelecomUtil;
import java.util.List;
import java.util.Map;

/** Shows a notification in the status bar for visual voicemail. */
final class VisualVoicemailNotifier {
  private static final String NOTIFICATION_TAG_PREFIX = "VisualVoicemail_";
  private static final String NOTIFICATION_GROUP = "VisualVoicemail";
  private static final int NOTIFICATION_ID = 1;

  public static void showNotifications(
      @NonNull Context context,
      @NonNull List<NewCall> newCalls,
      @NonNull Map<String, ContactInfo> contactInfos,
      @Nullable String callers) {
    LogUtil.enterBlock("VisualVoicemailNotifier.showNotifications");
    PendingIntent deleteIntent =
        CallLogNotificationsService.createMarkAllNewVoicemailsAsOldIntent(context);
    String contentTitle =
        context
            .getResources()
            .getQuantityString(
                R.plurals.notification_voicemail_title, newCalls.size(), newCalls.size());
    Notification.Builder groupSummary =
        createNotificationBuilder(context)
            .setContentTitle(contentTitle)
            .setContentText(callers)
            .setDeleteIntent(deleteIntent)
            .setGroupSummary(true)
            .setContentIntent(newVoicemailIntent(context, null));

    if (BuildCompat.isAtLeastO()) {
      groupSummary.setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN);
      PhoneAccountHandle handle = getAccountForCall(context, newCalls.get(0));
      groupSummary.setChannelId(NotificationChannelManager.getVoicemailChannelId(context, handle));
    }

    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
    notificationManager.notify(
        getNotificationTagForGroupSummary(), NOTIFICATION_ID, groupSummary.build());

    for (NewCall voicemail : newCalls) {
      notificationManager.notify(
          getNotificationTagForVoicemail(voicemail),
          NOTIFICATION_ID,
          createNotificationForVoicemail(context, voicemail, contactInfos));
    }
  }

  public static void cancelAllVoicemailNotifications(@NonNull Context context) {
    LogUtil.enterBlock("VisualVoicemailNotifier.cancelAllVoicemailNotifications");
    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
    for (StatusBarNotification notification : notificationManager.getActiveNotifications()) {
      String tag = notification.getTag();
      if (tag != null && tag.startsWith(NOTIFICATION_TAG_PREFIX)) {
        notificationManager.cancel(tag, notification.getId());
      }
    }
  }

  public static void cancelSingleVoicemailNotification(
      @NonNull Context context, @Nullable Uri voicemailUri) {
    LogUtil.enterBlock("VisualVoicemailNotifier.cancelSingleVoicemailNotification");
    if (voicemailUri == null) {
      LogUtil.e("VisualVoicemailNotifier.cancelSingleVoicemailNotification", "uri is null");
      return;
    }
    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
    String voicemailTag = getNotificationTagForUri(voicemailUri);
    String summaryTag = getNotificationTagForGroupSummary();
    int notificationCount = 0;

    for (StatusBarNotification notification : notificationManager.getActiveNotifications()) {
      String currentTag = notification.getTag();
      if (currentTag == null) {
        continue;
      }
      if (currentTag.equals(voicemailTag)) {
        notificationManager.cancel(notification.getTag(), notification.getId());
      } else if (currentTag.startsWith(NOTIFICATION_TAG_PREFIX) && !currentTag.equals(summaryTag)) {
        notificationCount++;
      }
    }

    if (notificationCount == 0) {
      // There are no more visual voicemail notifications. Remove the summary notification too.
      notificationManager.cancel(summaryTag, NOTIFICATION_ID);
    }
  }

  private static String getNotificationTagForVoicemail(@NonNull NewCall voicemail) {
    return getNotificationTagForUri(voicemail.voicemailUri);
  }

  private static String getNotificationTagForUri(@NonNull Uri voicemailUri) {
    return NOTIFICATION_TAG_PREFIX + voicemailUri;
  }

  private static String getNotificationTagForGroupSummary() {
    return NOTIFICATION_TAG_PREFIX + "GroupSummary";
  }

  private static Notification.Builder createNotificationBuilder(@NonNull Context context) {
    return new Notification.Builder(context)
        .setSmallIcon(android.R.drawable.stat_notify_voicemail)
        .setColor(context.getColor(R.color.dialer_theme_color))
        .setGroup(NOTIFICATION_GROUP)
        .setOnlyAlertOnce(true)
        .setAutoCancel(true);
  }

  private static Notification createNotificationForVoicemail(
      @NonNull Context context,
      @NonNull NewCall voicemail,
      @NonNull Map<String, ContactInfo> contactInfos) {
    PhoneAccountHandle handle = getAccountForCall(context, voicemail);
    ContactInfo contactInfo = contactInfos.get(voicemail.number);

    Notification.Builder builder =
        createNotificationBuilder(context)
            .setContentTitle(
                context
                    .getResources()
                    .getQuantityString(R.plurals.notification_voicemail_title, 1, 1))
            .setContentText(
                ContactDisplayUtils.getTtsSpannedPhoneNumber(
                    context.getResources(),
                    R.string.notification_new_voicemail_ticker,
                    contactInfo.name))
            .setWhen(voicemail.dateMs)
            .setSound(getVoicemailRingtoneUri(context, handle))
            .setDefaults(getNotificationDefaultFlags(context, handle));

    if (voicemail.voicemailUri != null) {
      builder.setDeleteIntent(
          CallLogNotificationsService.createMarkSingleNewVoicemailAsOldIntent(
              context, voicemail.voicemailUri));
    }

    if (BuildCompat.isAtLeastO()) {
      builder.setChannelId(NotificationChannelManager.getVoicemailChannelId(context, handle));
    }

    ContactPhotoLoader loader = new ContactPhotoLoader(context, contactInfo);
    Bitmap photoIcon = loader.loadPhotoIcon();
    if (photoIcon != null) {
      builder.setLargeIcon(photoIcon);
    }
    if (!TextUtils.isEmpty(voicemail.transcription)) {
      Logger.get(context)
          .logImpression(DialerImpression.Type.VVM_NOTIFICATION_CREATED_WITH_TRANSCRIPTION);
      builder.setStyle(new Notification.BigTextStyle().bigText(voicemail.transcription));
    }
    builder.setContentIntent(newVoicemailIntent(context, voicemail));
    Logger.get(context).logImpression(DialerImpression.Type.VVM_NOTIFICATION_CREATED);
    return builder.build();
  }

  @Nullable
  private static Uri getVoicemailRingtoneUri(
      @NonNull Context context, @Nullable PhoneAccountHandle handle) {
    if (VERSION.SDK_INT < VERSION_CODES.N) {
      return null;
    }
    if (handle == null) {
      LogUtil.i("VisualVoicemailNotifier.getVoicemailRingtoneUri", "null handle, getting fallback");
      handle = getFallbackAccount(context);
      if (handle == null) {
        LogUtil.i(
            "VisualVoicemailNotifier.getVoicemailRingtoneUri",
            "no fallback handle, using null (default) ringtone");
        return null;
      }
    }
    return context.getSystemService(TelephonyManager.class).getVoicemailRingtoneUri(handle);
  }

  private static int getNotificationDefaultFlags(
      @NonNull Context context, @Nullable PhoneAccountHandle handle) {
    if (VERSION.SDK_INT < VERSION_CODES.N) {
      return Notification.DEFAULT_ALL;
    }
    if (handle == null) {
      LogUtil.i(
          "VisualVoicemailNotifier.getNotificationDefaultFlags", "null handle, getting fallback");
      handle = getFallbackAccount(context);
      if (handle == null) {
        LogUtil.i(
            "VisualVoicemailNotifier.getNotificationDefaultFlags",
            "no fallback handle, using default vibration");
        return Notification.DEFAULT_ALL;
      }
    }
    if (context.getSystemService(TelephonyManager.class).isVoicemailVibrationEnabled(handle)) {
      return Notification.DEFAULT_VIBRATE;
    }
    return 0;
  }

  private static PendingIntent newVoicemailIntent(
      @NonNull Context context, @Nullable NewCall voicemail) {
    Intent intent =
        DialtactsActivity.getShowTabIntent(context, DialtactsPagerAdapter.TAB_INDEX_VOICEMAIL);
    // TODO (b/35486204): scroll to this voicemail
    if (voicemail != null) {
      intent.setData(voicemail.voicemailUri);
    }
    intent.putExtra(DialtactsActivity.EXTRA_CLEAR_NEW_VOICEMAILS, true);
    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  /**
   * Gets a phone account for the given call entry. This could be null if SIM associated with the
   * entry is no longer in the device or for other reasons (for example, modem reboot).
   */
  @Nullable
  public static PhoneAccountHandle getAccountForCall(
      @NonNull Context context, @Nullable NewCall call) {
    if (call == null || call.accountComponentName == null || call.accountId == null) {
      return null;
    }
    return new PhoneAccountHandle(
        ComponentName.unflattenFromString(call.accountComponentName), call.accountId);
  }

  /**
   * Gets any available phone account that can be used to get sound settings for voicemail. This is
   * only called if the phone account for the voicemail entry can't be found.
   */
  @Nullable
  public static PhoneAccountHandle getFallbackAccount(@NonNull Context context) {
    PhoneAccountHandle handle =
        TelecomUtil.getDefaultOutgoingPhoneAccount(context, PhoneAccount.SCHEME_TEL);
    if (handle == null) {
      List<PhoneAccountHandle> handles = TelecomUtil.getCallCapablePhoneAccounts(context);
      if (!handles.isEmpty()) {
        handle = handles.get(0);
      }
    }
    return handle;
  }

  private VisualVoicemailNotifier() {}
}
