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

package com.android.dialer.simulator.impl;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.provider.VoicemailContract.Status;
import android.provider.VoicemailContract.Voicemails;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import com.android.dialer.common.Assert;
import com.google.auto.value.AutoValue;
import java.util.concurrent.TimeUnit;

/** Populates the device database with voicemail entries. */
final class SimulatorVoicemail {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";

  private static final Voicemail.Builder[] SIMPLE_VOICEMAILS = {
    // Long transcription with an embedded phone number.
    Voicemail.builder()
        .setPhoneNumber("+1-302-6365454")
        .setTranscription(
            "Hi, this is a very long voicemail. Please call me back at 650 253 0000. "
                + "I hope you listen to all of it. This is very important. "
                + "Hi, this is a very long voicemail. "
                + "I hope you listen to all of it. It's very important.")
        .setDurationSeconds(10)
        .setIsRead(false),
    // RTL transcription.
    Voicemail.builder()
        .setPhoneNumber("+1-302-6365454")
        .setTranscription("هزاران دوست کم اند و یک دشمن زیاد")
        .setDurationSeconds(60)
        .setIsRead(true),
    // Empty number.
    Voicemail.builder()
        .setPhoneNumber("")
        .setTranscription("")
        .setDurationSeconds(60)
        .setIsRead(true),
    // No duration.
    Voicemail.builder()
        .setPhoneNumber("+1-302-6365454")
        .setTranscription("")
        .setDurationSeconds(0)
        .setIsRead(true),
    // Short number.
    Voicemail.builder()
        .setPhoneNumber("711")
        .setTranscription("This is a short voicemail.")
        .setDurationSeconds(12)
        .setIsRead(true),
  };

  @WorkerThread
  public static void populateVoicemail(@NonNull Context context) {
    Assert.isWorkerThread();
    enableVoicemail(context);

    // Do this 4 times to make the voicemail database 4 times bigger.
    long timeMillis = System.currentTimeMillis();
    for (int i = 0; i < 4; i++) {
      for (Voicemail.Builder builder : SIMPLE_VOICEMAILS) {
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

  private static void enableVoicemail(@NonNull Context context) {
    PhoneAccountHandle handle =
        new PhoneAccountHandle(new ComponentName(context, SimulatorVoicemail.class), ACCOUNT_ID);

    ContentValues values = new ContentValues();
    values.put(Status.SOURCE_PACKAGE, handle.getComponentName().getPackageName());
    values.put(Status.SOURCE_TYPE, TelephonyManager.VVM_TYPE_OMTP);
    values.put(Status.PHONE_ACCOUNT_COMPONENT_NAME, handle.getComponentName().flattenToString());
    values.put(Status.PHONE_ACCOUNT_ID, handle.getId());
    values.put(Status.CONFIGURATION_STATE, Status.CONFIGURATION_STATE_OK);
    values.put(Status.DATA_CHANNEL_STATE, Status.DATA_CHANNEL_STATE_OK);
    values.put(Status.NOTIFICATION_CHANNEL_STATE, Status.NOTIFICATION_CHANNEL_STATE_OK);
    context.getContentResolver().insert(Status.buildSourceUri(context.getPackageName()), values);
  }

  @AutoValue
  abstract static class Voicemail {
    @NonNull
    abstract String getPhoneNumber();

    @NonNull
    abstract String getTranscription();

    abstract long getDurationSeconds();

    abstract long getTimeMillis();

    abstract boolean getIsRead();

    static Builder builder() {
      return new AutoValue_SimulatorVoicemail_Voicemail.Builder();
    }

    ContentValues getAsContentValues(Context context) {
      ContentValues values = new ContentValues();
      values.put(Voicemails.DATE, getTimeMillis());
      values.put(Voicemails.NUMBER, getPhoneNumber());
      values.put(Voicemails.DURATION, getDurationSeconds());
      values.put(Voicemails.SOURCE_PACKAGE, context.getPackageName());
      values.put(Voicemails.IS_READ, getIsRead() ? 1 : 0);
      values.put(Voicemails.TRANSCRIPTION, getTranscription());
      return values;
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setPhoneNumber(@NonNull String phoneNumber);

      abstract Builder setTranscription(@NonNull String transcription);

      abstract Builder setDurationSeconds(long durationSeconds);

      abstract Builder setTimeMillis(long timeMillis);

      abstract Builder setIsRead(boolean isRead);

      abstract Voicemail build();
    }
  }

  private SimulatorVoicemail() {}
}
