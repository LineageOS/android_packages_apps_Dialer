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
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.dialer.app.MainComponent;
import com.android.dialer.app.R;
import com.android.dialer.app.calllog.CallLogNotificationsQueryHelper.NewCall;
import com.android.dialer.app.contactinfo.ContactPhotoLoader;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.android.provider.VoicemailCompat;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.notification.DialerNotificationManager;
import com.android.dialer.notification.NotificationChannelManager;
import com.android.dialer.notification.NotificationManagerUtils;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.theme.base.ThemeComponent;
import java.util.List;
import java.util.Map;

/** Shows a notification in the status bar for visual voicemail. */
final class VisualVoicemailNotifier {
  /** Prefix used to generate a unique tag for each voicemail notification. */
  static final String NOTIFICATION_TAG_PREFIX = "VisualVoicemail_";
  /** Common ID for all voicemail notifications. */
  static final int NOTIFICATION_ID = 1;
  /** Tag for the group summary notification. */
  static final String GROUP_SUMMARY_NOTIFICATION_TAG = "GroupSummary_VisualVoicemail";
  /**
   * Key used to associate all voicemail notifications and the summary as belonging to a single
   * group.
   */
  private static final String GROUP_KEY = "VisualVoicemailGroup";

  /**
   * @param shouldAlert whether ringtone or vibration should be made when the notification is posted
   *     or updated. Should only be true when there is a real new voicemail.
   */
  public static void showNotifications(
      @NonNull Context context,
      @NonNull List<NewCall> newCalls,
      @NonNull Map<String, ContactInfo> contactInfos,
      @Nullable String callers,
      boolean shouldAlert) {
    LogUtil.enterBlock("VisualVoicemailNotifier.showNotifications");
    PendingIntent deleteIntent =
        CallLogNotificationsService.createMarkAllNewVoicemailsAsOldIntent(context);
    String contentTitle =
        context
            .getResources()
            .getQuantityString(
                R.plurals.notification_voicemail_title, newCalls.size(), newCalls.size());
    NotificationCompat.Builder groupSummary =
        createNotificationBuilder(context)
            .setContentTitle(contentTitle)
            .setContentText(callers)
            .setDeleteIntent(deleteIntent)
            .setGroupSummary(true)
            .setContentIntent(newVoicemailIntent(context, null));

    if (VERSION.SDK_INT >= VERSION_CODES.O) {
      if (shouldAlert) {
        groupSummary.setOnlyAlertOnce(false);
        // Group summary will alert when posted/updated
        groupSummary.setGroupAlertBehavior(Notification.GROUP_ALERT_ALL);
      } else {
        // Only children will alert. but since all children are set to "only alert summary" it is
        // effectively silenced.
        groupSummary.setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN);
      }
      PhoneAccountHandle handle = getAccountForCall(context, newCalls.get(0));
      groupSummary.setChannelId(NotificationChannelManager.getVoicemailChannelId(context, handle));
    }

    DialerNotificationManager.notify(
        context, GROUP_SUMMARY_NOTIFICATION_TAG, NOTIFICATION_ID, groupSummary.build());

    for (NewCall voicemail : newCalls) {
      DialerNotificationManager.notify(
          context,
          getNotificationTagForVoicemail(voicemail),
          NOTIFICATION_ID,
          createNotificationForVoicemail(context, voicemail, contactInfos));
    }
  }

  public static void cancelAllVoicemailNotifications(@NonNull Context context) {
    LogUtil.enterBlock("VisualVoicemailNotifier.cancelAllVoicemailNotifications");
    NotificationManagerUtils.cancelAllInGroup(context, GROUP_KEY);
  }

  public static void cancelSingleVoicemailNotification(
      @NonNull Context context, @Nullable Uri voicemailUri) {
    LogUtil.enterBlock("VisualVoicemailNotifier.cancelSingleVoicemailNotification");
    if (voicemailUri == null) {
      LogUtil.e("VisualVoicemailNotifier.cancelSingleVoicemailNotification", "uri is null");
      return;
    }
    // This will also dismiss the group summary if there are no more voicemail notifications.
    DialerNotificationManager.cancel(
        context, getNotificationTagForUri(voicemailUri), NOTIFICATION_ID);
  }

  private static String getNotificationTagForVoicemail(@NonNull NewCall voicemail) {
    return getNotificationTagForUri(voicemail.voicemailUri);
  }

  private static String getNotificationTagForUri(@NonNull Uri voicemailUri) {
    return NOTIFICATION_TAG_PREFIX + voicemailUri;
  }

  private static NotificationCompat.Builder createNotificationBuilder(@NonNull Context context) {
    return new NotificationCompat.Builder(context)
        .setSmallIcon(android.R.drawable.stat_notify_voicemail)
        .setColor(ThemeComponent.get(context).theme().getColorPrimary())
        .setGroup(GROUP_KEY)
        .setOnlyAlertOnce(true)
        .setAutoCancel(true);
  }

  static Notification createNotificationForVoicemail(
      @NonNull Context context,
      @NonNull NewCall voicemail,
      @NonNull Map<String, ContactInfo> contactInfos) {
    PhoneAccountHandle handle = getAccountForCall(context, voicemail);
    ContactInfo contactInfo = contactInfos.get(voicemail.number);

    NotificationCompat.Builder builder =
        createNotificationBuilder(context)
            .setContentTitle(
                ContactDisplayUtils.getTtsSpannedPhoneNumber(
                    context.getResources(),
                    R.string.notification_new_voicemail_ticker,
                    contactInfo.name))
            .setWhen(voicemail.dateMs)
            .setSound(getVoicemailRingtoneUri(context, handle))
            .setDefaults(getNotificationDefaultFlags(context, handle));

    if (!TextUtils.isEmpty(voicemail.transcription)) {
      Logger.get(context)
          .logImpression(DialerImpression.Type.VVM_NOTIFICATION_CREATED_WITH_TRANSCRIPTION);
      builder
          .setContentText(voicemail.transcription)
          .setStyle(new NotificationCompat.BigTextStyle().bigText(voicemail.transcription));
    } else {
      switch (voicemail.transcriptionState) {
        case VoicemailCompat.TRANSCRIPTION_IN_PROGRESS:
          Logger.get(context)
              .logImpression(DialerImpression.Type.VVM_NOTIFICATION_CREATED_WITH_IN_PROGRESS);
          builder.setContentText(context.getString(R.string.voicemail_transcription_in_progress));
          break;
        case VoicemailCompat.TRANSCRIPTION_FAILED_NO_SPEECH_DETECTED:
          Logger.get(context)
              .logImpression(
                  DialerImpression.Type.VVM_NOTIFICATION_CREATED_WITH_TRANSCRIPTION_FAILURE);
          builder.setContentText(
              context.getString(R.string.voicemail_transcription_failed_no_speech));
          break;
        case VoicemailCompat.TRANSCRIPTION_FAILED_LANGUAGE_NOT_SUPPORTED:
          Logger.get(context)
              .logImpression(
                  DialerImpression.Type.VVM_NOTIFICATION_CREATED_WITH_TRANSCRIPTION_FAILURE);
          builder.setContentText(
              context.getString(R.string.voicemail_transcription_failed_language_not_supported));
          break;
        case VoicemailCompat.TRANSCRIPTION_FAILED:
          Logger.get(context)
              .logImpression(
                  DialerImpression.Type.VVM_NOTIFICATION_CREATED_WITH_TRANSCRIPTION_FAILURE);
          builder.setContentText(context.getString(R.string.voicemail_transcription_failed));
          break;
        default:
          Logger.get(context)
              .logImpression(DialerImpression.Type.VVM_NOTIFICATION_CREATED_WITH_NO_TRANSCRIPTION);
          break;
      }
    }

    if (voicemail.voicemailUri != null) {
      builder.setDeleteIntent(
          CallLogNotificationsService.createMarkSingleNewVoicemailAsOldIntent(
              context, voicemail.voicemailUri));
    }

    if (VERSION.SDK_INT >= VERSION_CODES.O) {
      builder.setChannelId(NotificationChannelManager.getVoicemailChannelId(context, handle));
      builder.setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY);
    }

    ContactPhotoLoader loader = new ContactPhotoLoader(context, contactInfo);
    Bitmap photoIcon = loader.loadPhotoIcon();
    if (photoIcon != null) {
      builder.setLargeIcon(photoIcon);
    }
    builder.setContentIntent(newVoicemailIntent(context, voicemail));
    Logger.get(context).logImpression(DialerImpression.Type.VVM_NOTIFICATION_CREATED);
    return builder.build();
  }

  @Nullable
  private static Uri getVoicemailRingtoneUri(
      @NonNull Context context, @Nullable PhoneAccountHandle handle) {
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
    Intent intent = MainComponent.getShowVoicemailIntent(context);

    // TODO (a bug): scroll to this voicemail
    if (voicemail != null) {
      intent.setData(voicemail.voicemailUri);
    }
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
