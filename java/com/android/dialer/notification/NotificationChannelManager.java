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

package com.android.dialer.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringDef;
import android.support.v4.os.BuildCompat;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import com.android.contacts.common.compat.TelephonyManagerCompat;
import com.android.dialer.buildtype.BuildType;
import com.android.dialer.common.LogUtil;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Contains info on how to create {@link NotificationChannel NotificationChannels} */
public class NotificationChannelManager {

  private static NotificationChannelManager instance;

  public static NotificationChannelManager getInstance() {
    if (instance == null) {
      instance = new NotificationChannelManager();
    }
    return instance;
  }

  /**
   * Set the channel of notification appropriately. Will create the channel if it does not already
   * exist. Safe to call pre-O (will no-op).
   *
   * <p>phoneAccount should only be null if channelName is {@link Channel#MISC} or {@link
   * Channel#MISSED_CALL} since these do not have account-specific settings.
   */
  public static void applyChannel(
      @NonNull Notification.Builder notification,
      @NonNull Context context,
      @Channel String channelName,
      @Nullable PhoneAccountHandle phoneAccount) {
    checkNullity(channelName, phoneAccount);

    if (BuildCompat.isAtLeastO()) {
      NotificationChannel channel =
          NotificationChannelManager.getInstance().getChannel(context, channelName, phoneAccount);
      notification.setChannelId(channel.getId());
    }
  }

  private static void checkNullity(
      @Channel String channelName, @Nullable PhoneAccountHandle phoneAccount) {
    if (phoneAccount != null || channelAllowsNullPhoneAccountHandle(channelName)) {
      return;
    }

    // TODO (b/36568553): don't throw an exception once most cases have been identified
    IllegalArgumentException exception =
        new IllegalArgumentException(
            "Phone account handle must not be null on channel " + channelName);
    if (BuildType.get() == BuildType.RELEASE) {
      LogUtil.e("NotificationChannelManager.applyChannel", null, exception);
    } else {
      throw exception;
    }
  }

  private static boolean channelAllowsNullPhoneAccountHandle(@Channel String channelName) {
    switch (channelName) {
      case Channel.MISC:
      case Channel.MISSED_CALL:
        return true;
      default:
        return false;
    }
  }

  /** The base Channel IDs for {@link NotificationChannel} */
  @Retention(RetentionPolicy.SOURCE)
  @StringDef({
    Channel.INCOMING_CALL,
    Channel.ONGOING_CALL,
    Channel.MISSED_CALL,
    Channel.VOICEMAIL,
    Channel.EXTERNAL_CALL,
    Channel.MISC
  })
  public @interface Channel {
    String INCOMING_CALL = "incomingCall";
    String ONGOING_CALL = "ongoingCall";
    String MISSED_CALL = "missedCall";
    String VOICEMAIL = "voicemail";
    String EXTERNAL_CALL = "externalCall";
    String MISC = "miscellaneous";
  }

  private NotificationChannelManager() {}

  private NotificationChannel getChannel(
      @NonNull Context context,
      @Channel String channelName,
      @Nullable PhoneAccountHandle phoneAccount) {
    String channelId = channelNameToId(channelName, phoneAccount);
    NotificationChannel channel = getNotificationManager(context).getNotificationChannel(channelId);
    if (channel == null) {
      channel = createChannel(context, channelName, phoneAccount);
    }
    return channel;
  }

  private static String channelNameToId(
      @Channel String name, @Nullable PhoneAccountHandle phoneAccountHandle) {
    if (phoneAccountHandle == null) {
      return name;
    } else {
      return name + ":" + phoneAccountHandle.getId();
    }
  }

  private NotificationChannel createChannel(
      Context context,
      @Channel String channelName,
      @Nullable PhoneAccountHandle phoneAccountHandle) {
    String channelId = channelNameToId(channelName, phoneAccountHandle);

    if (phoneAccountHandle != null) {
      PhoneAccount account = getTelecomManager(context).getPhoneAccount(phoneAccountHandle);
      NotificationChannelGroup group =
          new NotificationChannelGroup(
              phoneAccountHandle.getId(),
              (account == null) ? phoneAccountHandle.getId() : account.getLabel().toString());
      getNotificationManager(context)
          .createNotificationChannelGroup(group); // No-op if already exists
    } else if (!channelAllowsNullPhoneAccountHandle(channelName)) {
      LogUtil.w(
          "NotificationChannelManager.createChannel",
          "Null PhoneAccountHandle with channel " + channelName);
    }

    Uri silentRingtone = Uri.parse("");

    CharSequence name;
    int importance;
    boolean canShowBadge;
    boolean lights;
    boolean vibration;
    Uri sound;
    switch (channelName) {
      case Channel.INCOMING_CALL:
        name = context.getText(R.string.notification_channel_incoming_call);
        importance = NotificationManager.IMPORTANCE_MAX;
        canShowBadge = false;
        lights = true;
        vibration = false;
        sound = silentRingtone;
        break;
      case Channel.MISSED_CALL:
        name = context.getText(R.string.notification_channel_missed_call);
        importance = NotificationManager.IMPORTANCE_DEFAULT;
        canShowBadge = true;
        lights = true;
        vibration = true;
        sound = silentRingtone;
        break;
      case Channel.ONGOING_CALL:
        name = context.getText(R.string.notification_channel_ongoing_call);
        importance = NotificationManager.IMPORTANCE_DEFAULT;
        canShowBadge = false;
        lights = false;
        vibration = false;
        sound = silentRingtone;
        break;
      case Channel.VOICEMAIL:
        name = context.getText(R.string.notification_channel_voicemail);
        importance = NotificationManager.IMPORTANCE_DEFAULT;
        canShowBadge = true;
        lights = true;
        vibration = true;
        sound =
            TelephonyManagerCompat.getVoicemailRingtoneUri(
                getTelephonyManager(context), phoneAccountHandle);
        break;
      case Channel.EXTERNAL_CALL:
        name = context.getText(R.string.notification_channel_external_call);
        importance = NotificationManager.IMPORTANCE_HIGH;
        canShowBadge = false;
        lights = true;
        vibration = true;
        sound = null;
        break;
      case Channel.MISC:
        name = context.getText(R.string.notification_channel_misc);
        importance = NotificationManager.IMPORTANCE_DEFAULT;
        canShowBadge = false;
        lights = true;
        vibration = true;
        sound = null;
        break;
      default:
        throw new IllegalArgumentException("Unknown channel: " + channelName);
    }

    NotificationChannel channel = new NotificationChannel(channelId, name, importance);
    channel.setShowBadge(canShowBadge);
    if (sound != null) {
      channel.setSound(
          sound,
          new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
    }
    channel.enableLights(lights);
    channel.enableVibration(vibration);
    getNotificationManager(context).createNotificationChannel(channel);
    return channel;
  }

  private static NotificationManager getNotificationManager(@NonNull Context context) {
    return context.getSystemService(NotificationManager.class);
  }

  private static TelephonyManager getTelephonyManager(@NonNull Context context) {
    return context.getSystemService(TelephonyManager.class);
  }

  private static TelecomManager getTelecomManager(@NonNull Context context) {
    return context.getSystemService(TelecomManager.class);
  }
}
