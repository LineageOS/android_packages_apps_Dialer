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

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.StringDef;
import android.support.v4.os.BuildCompat;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import com.android.contacts.common.compat.TelephonyManagerCompat;
import com.android.dialer.buildtype.BuildType;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutors;
import com.android.dialer.telecom.TelecomUtil;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Objects;

/** Contains info on how to create {@link NotificationChannel NotificationChannels} */
public class NotificationChannelManager {

  private static final String PREFS_FILENAME = "NotificationChannelManager";
  private static final String PREF_NEED_FIRST_INIT = "needFirstInit";
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
   * <p>phoneAccount should only be null if channelName is {@link Channel#DEFAULT} or {@link
   * Channel#MISSED_CALL} since these do not have account-specific settings.
   */
  @TargetApi(26)
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
      case Channel.DEFAULT:
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
    Channel.ONGOING_CALL_OLD,
    Channel.MISSED_CALL,
    Channel.VOICEMAIL,
    Channel.EXTERNAL_CALL,
    Channel.DEFAULT
  })
  public @interface Channel {
    @Deprecated String ONGOING_CALL_OLD = "ongoingCall";
    String INCOMING_CALL = "incomingCall";
    String ONGOING_CALL = "ongoingCall2";
    String MISSED_CALL = "missedCall";
    String VOICEMAIL = "voicemail";
    String EXTERNAL_CALL = "externalCall";
    String DEFAULT = "default";
  }

  @Channel
  private static final String[] prepopulatedAccountChannels =
      new String[] {Channel.INCOMING_CALL, Channel.ONGOING_CALL, Channel.VOICEMAIL};

  @Channel
  private static final String[] prepopulatedGlobalChannels =
      new String[] {Channel.MISSED_CALL, Channel.DEFAULT};

  private NotificationChannelManager() {}

  public void firstInitIfNeeded(@NonNull Context context) {
    if (BuildCompat.isAtLeastO()) {
      DialerExecutors.createNonUiTaskBuilder(this::firstInitIfNeededSync)
          .build()
          .executeSerial(context);
    }
  }

  private boolean firstInitIfNeededSync(@NonNull Context context) {
    if (needsFirstInit(context)) {
      initChannels(context);
      return true;
    }
    return false;
  }

  public boolean needsFirstInit(@NonNull Context context) {
    return (BuildCompat.isAtLeastO()
        && getSharedPreferences(context).getBoolean(PREF_NEED_FIRST_INIT, true));
  }

  @RequiresApi(VERSION_CODES.N)
  private SharedPreferences getSharedPreferences(@NonNull Context context) {
    // Use device protected storage since in some cases this will need to be accessed while device
    // is locked
    context = context.createDeviceProtectedStorageContext();
    return context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE);
  }

  @RequiresApi(26)
  public Intent getSettingsIntentForChannel(
      @NonNull Context context, @Channel String channelName, PhoneAccountHandle accountHandle) {
    checkNullity(channelName, accountHandle);
    Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
    intent.putExtra(
        Settings.EXTRA_CHANNEL_ID, getChannel(context, channelName, accountHandle).getId());
    intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
    return intent;
  }

  @TargetApi(26)
  @SuppressWarnings("AndroidApiChecker")
  public void initChannels(@NonNull Context context) {
    if (!BuildCompat.isAtLeastO()) {
      return;
    }
    LogUtil.enterBlock("NotificationChannelManager.initChannels");
    List<PhoneAccountHandle> phoneAccounts = TelecomUtil.getCallCapablePhoneAccounts(context);

    // Remove notification channels for PhoneAccounts that don't exist anymore
    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
    List<NotificationChannelGroup> notificationChannelGroups =
        notificationManager.getNotificationChannelGroups();
    notificationChannelGroups
        .stream()
        .filter(group -> !idExists(group.getId(), phoneAccounts))
        .forEach(group -> deleteGroup(notificationManager, group));

    for (PhoneAccountHandle phoneAccountHandle : phoneAccounts) {
      for (@Channel String channel : prepopulatedAccountChannels) {
        getChannel(context, channel, phoneAccountHandle);
      }
    }

    for (@Channel String channel : prepopulatedGlobalChannels) {
      getChannel(context, channel, null);
    }
    getSharedPreferences(context).edit().putBoolean(PREF_NEED_FIRST_INIT, false).apply();
  }

  @TargetApi(26)
  private void deleteGroup(
      @NonNull NotificationManager notificationManager, @NonNull NotificationChannelGroup group) {
    for (NotificationChannel channel : group.getChannels()) {
      notificationManager.deleteNotificationChannel(channel.getId());
    }
    notificationManager.deleteNotificationChannelGroup(group.getId());
  }

  private boolean idExists(String id, List<PhoneAccountHandle> phoneAccountHandles) {
    for (PhoneAccountHandle handle : phoneAccountHandles) {
      if (Objects.equals(handle.getId(), id)) {
        return true;
      }
    }
    return false;
  }

  @NonNull
  @RequiresApi(26)
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

  @RequiresApi(26)
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

    Uri silentRingtone = Uri.EMPTY;

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
        deleteOldOngoingCallChannelIfNeeded(context, phoneAccountHandle);
        break;
      case Channel.VOICEMAIL:
        name = context.getText(R.string.notification_channel_voicemail);
        importance = NotificationManager.IMPORTANCE_DEFAULT;
        canShowBadge = true;
        lights = true;
        vibration =
            TelephonyManagerCompat.isVoicemailVibrationEnabled(
                getTelephonyManager(context), phoneAccountHandle);
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
      case Channel.DEFAULT:
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
      // silentRingtone acts as a sentinel value to indicate that setSound should still be called,
      // but with a null value to indicate no sound.
      channel.setSound(
          sound.equals(silentRingtone) ? null : sound,
          new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
    }
    channel.enableLights(lights);
    channel.enableVibration(vibration);
    getNotificationManager(context).createNotificationChannel(channel);
    return channel;
  }

  @RequiresApi(26)
  private void deleteOldOngoingCallChannelIfNeeded(
      @NonNull Context context, PhoneAccountHandle phoneAccountHandle) {
    String channelId = channelNameToId(Channel.ONGOING_CALL_OLD, phoneAccountHandle);
    NotificationManager notificationManager = getNotificationManager(context);
    NotificationChannel channel = notificationManager.getNotificationChannel(channelId);
    if (channel != null) {
      LogUtil.i(
          "NotificationManager.deleteOldOngoingCallChannelIfNeeded",
          "Old ongoing channel found. Deleting to create new channel");
      notificationManager.deleteNotificationChannel(channel.getId());
    }
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
