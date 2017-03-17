/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.util.Pair;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import com.android.contacts.common.compat.TelephonyManagerCompat;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.dialer.app.DialtactsActivity;
import com.android.dialer.app.R;
import com.android.dialer.app.calllog.CallLogNotificationsQueryHelper.NewCall;
import com.android.dialer.app.contactinfo.ContactPhotoLoader;
import com.android.dialer.app.list.ListsFragment;
import com.android.dialer.blocking.FilteredNumbersUtil;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.nano.DialerImpression;
import com.android.dialer.notification.NotificationChannelManager;
import com.android.dialer.notification.NotificationChannelManager.Channel;
import com.android.dialer.phonenumbercache.ContactInfo;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Shows a voicemail notification in the status bar. */
public class DefaultVoicemailNotifier {

  public static final String TAG = "VoicemailNotifier";

  /** The tag used to identify notifications from this class. */
  static final String NOTIFICATION_TAG = "DefaultVoicemailNotifier";
  /** The identifier of the notification of new voicemails. */
  private static final int NOTIFICATION_ID = R.id.notification_voicemail;

  private final Context context;
  private final CallLogNotificationsQueryHelper queryHelper;

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  DefaultVoicemailNotifier(Context context, CallLogNotificationsQueryHelper queryHelper) {
    this.context = context;
    this.queryHelper = queryHelper;
  }

  /** Returns an instance of {@link DefaultVoicemailNotifier}. */
  public static DefaultVoicemailNotifier getInstance(Context context) {
    return new DefaultVoicemailNotifier(
        context, CallLogNotificationsQueryHelper.getInstance(context));
  }

  /**
   * Updates the notification and notifies of the call with the given URI.
   *
   * <p>Clears the notification if there are no new voicemails, and notifies if the given URI
   * corresponds to a new voicemail.
   *
   * <p>It is not safe to call this method from the main thread.
   */
  public void updateNotification() {
    // Lookup the list of new voicemails to include in the notification.
    final List<NewCall> newCalls = queryHelper.getNewVoicemails();

    if (newCalls == null) {
      // Query failed, just return.
      return;
    }

    Resources resources = context.getResources();

    // This represents a list of names to include in the notification.
    String callers = null;

    // Maps each number into a name: if a number is in the map, it has already left a more
    // recent voicemail.
    final Map<String, ContactInfo> contactInfos = new ArrayMap<>();

    // Iterate over the new voicemails to determine all the information above.
    Iterator<NewCall> itr = newCalls.iterator();
    while (itr.hasNext()) {
      NewCall newCall = itr.next();

      // Skip notifying for numbers which are blocked.
      if (FilteredNumbersUtil.shouldBlockVoicemail(
          context, newCall.number, newCall.countryIso, newCall.dateMs)) {
        itr.remove();

        // Delete the voicemail.
        context.getContentResolver().delete(newCall.voicemailUri, null, null);
        continue;
      }

      // Check if we already know the name associated with this number.
      ContactInfo contactInfo = contactInfos.get(newCall.number);
      if (contactInfo == null) {
        contactInfo =
            queryHelper.getContactInfo(
                newCall.number, newCall.numberPresentation, newCall.countryIso);
        contactInfos.put(newCall.number, contactInfo);
        // This is a new caller. Add it to the back of the list of callers.
        if (TextUtils.isEmpty(callers)) {
          callers = contactInfo.name;
        } else {
          callers =
              resources.getString(
                  R.string.notification_voicemail_callers_list, callers, contactInfo.name);
        }
      }
    }

    if (newCalls.isEmpty()) {
      // No voicemails to notify about: clear the notification.
      CallLogNotificationsService.markNewVoicemailsAsOld(context, null);
      return;
    }

    Notification.Builder groupSummary =
        createNotificationBuilder()
            .setContentTitle(
                resources.getQuantityString(
                    R.plurals.notification_voicemail_title, newCalls.size(), newCalls.size()))
            .setContentText(callers)
            .setDeleteIntent(createMarkNewVoicemailsAsOldIntent(null))
            .setGroupSummary(true)
            .setContentIntent(newVoicemailIntent(null));

    NotificationChannelManager.applyChannel(
        groupSummary,
        context,
        Channel.VOICEMAIL,
        PhoneAccountHandles.getAccount(context, newCalls.get(0)));

    LogUtil.i(TAG, "Creating voicemail notification");
    getNotificationManager().notify(NOTIFICATION_TAG, NOTIFICATION_ID, groupSummary.build());

    for (NewCall voicemail : newCalls) {
      getNotificationManager()
          .notify(
              voicemail.voicemailUri.toString(),
              NOTIFICATION_ID,
              createNotificationForVoicemail(voicemail, contactInfos));
    }
  }

  /**
   * Determines which ringtone Uri and Notification defaults to use when updating the notification
   * for the given call.
   */
  private Pair<Uri, Integer> getNotificationInfo(@Nullable NewCall callToNotify) {
    LogUtil.v(TAG, "getNotificationInfo");
    if (callToNotify == null) {
      LogUtil.i(TAG, "callToNotify == null");
      return new Pair<>(null, 0);
    }
    PhoneAccountHandle accountHandle = PhoneAccountHandles.getAccount(context, callToNotify);
    if (accountHandle == null) {
      LogUtil.i(TAG, "No default phone account found, using default notification ringtone");
      return new Pair<>(null, Notification.DEFAULT_ALL);
    }
    return new Pair<>(
        TelephonyManagerCompat.getVoicemailRingtoneUri(getTelephonyManager(), accountHandle),
        getNotificationDefaults(accountHandle));
  }

  private int getNotificationDefaults(PhoneAccountHandle accountHandle) {
    if (VERSION.SDK_INT >= VERSION_CODES.N) {
      return TelephonyManagerCompat.isVoicemailVibrationEnabled(
              getTelephonyManager(), accountHandle)
          ? Notification.DEFAULT_VIBRATE
          : 0;
    }
    return Notification.DEFAULT_ALL;
  }

  /** Creates a pending intent that marks all new voicemails as old. */
  private PendingIntent createMarkNewVoicemailsAsOldIntent(@Nullable Uri voicemailUri) {
    Intent intent = new Intent(context, CallLogNotificationsService.class);
    intent.setAction(CallLogNotificationsService.ACTION_MARK_NEW_VOICEMAILS_AS_OLD);
    intent.setData(voicemailUri);
    return PendingIntent.getService(context, 0, intent, 0);
  }

  private NotificationManager getNotificationManager() {
    return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
  }

  private TelephonyManager getTelephonyManager() {
    return (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
  }

  private Notification createNotificationForVoicemail(
      @NonNull NewCall voicemail, @NonNull Map<String, ContactInfo> contactInfos) {
    Pair<Uri, Integer> notificationInfo = getNotificationInfo(voicemail);
    ContactInfo contactInfo = contactInfos.get(voicemail.number);

    Notification.Builder notificationBuilder =
        createNotificationBuilder()
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
            .setSound(notificationInfo.first)
            .setDefaults(notificationInfo.second)
            .setDeleteIntent(createMarkNewVoicemailsAsOldIntent(voicemail.voicemailUri));

    NotificationChannelManager.applyChannel(
        notificationBuilder,
        context,
        Channel.VOICEMAIL,
        PhoneAccountHandles.getAccount(context, voicemail));

    ContactPhotoLoader loader = new ContactPhotoLoader(context, contactInfo);
    Bitmap photoIcon = loader.loadPhotoIcon();
    if (photoIcon != null) {
      notificationBuilder.setLargeIcon(photoIcon);
    }
    if (!TextUtils.isEmpty(voicemail.transcription)) {
      Logger.get(context)
          .logImpression(DialerImpression.Type.VVM_NOTIFICATION_CREATED_WITH_TRANSCRIPTION);
      notificationBuilder.setStyle(
          new Notification.BigTextStyle().bigText(voicemail.transcription));
    }
    notificationBuilder.setContentIntent(newVoicemailIntent(voicemail));
    Logger.get(context).logImpression(DialerImpression.Type.VVM_NOTIFICATION_CREATED);
    return notificationBuilder.build();
  }

  private Notification.Builder createNotificationBuilder() {
    return new Notification.Builder(context)
        .setSmallIcon(android.R.drawable.stat_notify_voicemail)
        .setColor(context.getColor(R.color.dialer_theme_color))
        .setGroup(NOTIFICATION_TAG)
        .setOnlyAlertOnce(true)
        .setAutoCancel(true);
  }

  private PendingIntent newVoicemailIntent(@Nullable NewCall voicemail) {
    Intent intent = DialtactsActivity.getShowTabIntent(context, ListsFragment.TAB_INDEX_VOICEMAIL);
    // TODO (b/35486204): scroll to this voicemail
    if (voicemail != null) {
      intent.setData(voicemail.voicemailUri);
    }
    intent.putExtra(DialtactsActivity.EXTRA_CLEAR_NEW_VOICEMAILS, true);
    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }
}
