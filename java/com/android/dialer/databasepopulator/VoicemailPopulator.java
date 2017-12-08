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

package com.android.dialer.databasepopulator;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.VoicemailContract.Status;
import android.provider.VoicemailContract.Voicemails;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import com.android.dialer.common.Assert;
import com.google.auto.value.AutoValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Populates the device database with voicemail entries. */
public final class VoicemailPopulator {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static String componentName = "";
  private static final Voicemail.Builder[] SIMPLE_VOICEMAILS = {
    // Long transcription with an embedded phone number.
    Voicemail.builder()
        .setPhoneNumber("+1-302-6365454")
        .setTranscription(
            "Hi, this is a very long voicemail. Please call me back at 650 253 0000. "
                + "I hope you listen to all of it. This is very important. "
                + "Hi, this is a very long voicemail. "
                + "I hope you listen to all of it. It's very important.")
        .setPhoneAccountComponentName(componentName)
        .setDurationSeconds(10)
        .setIsRead(false),
    // RTL transcription.
    Voicemail.builder()
        .setPhoneNumber("+1-302-6365454")
        .setTranscription("هزاران دوست کم اند و یک دشمن زیاد")
        .setDurationSeconds(60)
        .setPhoneAccountComponentName(componentName)
        .setIsRead(true),
    // Empty number.
    Voicemail.builder()
        .setPhoneNumber("")
        .setTranscription("")
        .setDurationSeconds(60)
        .setPhoneAccountComponentName(componentName)
        .setIsRead(true),
    // No duration.
    Voicemail.builder()
        .setPhoneNumber("+1-302-6365454")
        .setTranscription("")
        .setDurationSeconds(0)
        .setPhoneAccountComponentName(componentName)
        .setIsRead(true),
    // Short number.
    Voicemail.builder()
        .setPhoneNumber("711")
        .setTranscription("This is a short voicemail.")
        .setDurationSeconds(12)
        .setPhoneAccountComponentName(componentName)
        .setIsRead(true),
  };

  @WorkerThread
  public static void populateVoicemail(@NonNull Context context, boolean fastMode) {
    Assert.isWorkerThread();
    enableVoicemail(context);
    List<Voicemail.Builder> voicemails = new ArrayList<>();
    if (fastMode) {
      voicemails.add(SIMPLE_VOICEMAILS[0]);
    } else {
      voicemails = Arrays.asList(SIMPLE_VOICEMAILS);
    }
    // Do this 4 times to make the voicemail database 4 times bigger.
    long timeMillis = System.currentTimeMillis();
    for (int i = 0; i < 4; i++) {
      for (Voicemail.Builder builder : voicemails) {
        Voicemail voicemail = builder.setTimeMillis(timeMillis).build();
        context
            .getContentResolver()
            .insert(
                Voicemails.buildSourceUri(context.getPackageName()),
                voicemail.getAsContentValues(context));
        timeMillis -= TimeUnit.HOURS.toMillis(2);
      }
    }
  }

  @WorkerThread
  public static void populateVoicemail(@NonNull Context context) {
    populateVoicemail(context, false);
  }

  @WorkerThread
  public static void deleteAllVoicemail(@NonNull Context context) {
    Assert.isWorkerThread();
    context
        .getContentResolver()
        .delete(Voicemails.buildSourceUri(context.getPackageName()), null, null);
  }

  @VisibleForTesting
  public static void enableVoicemail(@NonNull Context context) {
    PhoneAccountHandle handle =
        new PhoneAccountHandle(new ComponentName(context, VoicemailPopulator.class), ACCOUNT_ID);
    componentName = handle.getComponentName().toString();
    ContentValues values = new ContentValues();
    values.put(Status.SOURCE_PACKAGE, handle.getComponentName().getPackageName());
    if (VERSION.SDK_INT >= VERSION_CODES.N_MR1) {
      values.put(Status.SOURCE_TYPE, TelephonyManager.VVM_TYPE_OMTP);
    }
    values.put(Status.PHONE_ACCOUNT_COMPONENT_NAME, handle.getComponentName().flattenToString());
    values.put(Status.PHONE_ACCOUNT_ID, handle.getId());
    values.put(Status.CONFIGURATION_STATE, Status.CONFIGURATION_STATE_OK);
    values.put(Status.DATA_CHANNEL_STATE, Status.DATA_CHANNEL_STATE_OK);
    values.put(Status.NOTIFICATION_CHANNEL_STATE, Status.NOTIFICATION_CHANNEL_STATE_OK);
    context.getContentResolver().insert(Status.buildSourceUri(context.getPackageName()), values);
  }

  /** Data for a single voicemail entry. */
  @AutoValue
  public abstract static class Voicemail {
    @NonNull
    public abstract String getPhoneNumber();

    @NonNull
    public abstract String getTranscription();

    public abstract long getDurationSeconds();

    public abstract long getTimeMillis();

    public abstract boolean getIsRead();

    public abstract String getPhoneAccountComponentName();

    public static Builder builder() {
      return new AutoValue_VoicemailPopulator_Voicemail.Builder();
    }

    public ContentValues getAsContentValues(Context context) {
      ContentValues values = new ContentValues();
      values.put(Voicemails.DATE, getTimeMillis());
      values.put(Voicemails.NUMBER, getPhoneNumber());
      values.put(Voicemails.DURATION, getDurationSeconds());
      values.put(Voicemails.SOURCE_PACKAGE, context.getPackageName());
      values.put(Voicemails.IS_READ, getIsRead() ? 1 : 0);
      values.put(Voicemails.TRANSCRIPTION, getTranscription());
      values.put(Voicemails.PHONE_ACCOUNT_COMPONENT_NAME, getPhoneAccountComponentName());
      return values;
    }

    /** Builder for a single voicemail entry. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setPhoneNumber(@NonNull String phoneNumber);

      public abstract Builder setTranscription(@NonNull String transcription);

      public abstract Builder setDurationSeconds(long durationSeconds);

      public abstract Builder setTimeMillis(long timeMillis);

      public abstract Builder setIsRead(boolean isRead);

      public abstract Builder setPhoneAccountComponentName(String phoneAccountComponentName);

      public abstract Voicemail build();
    }
  }

  private VoicemailPopulator() {}
}
