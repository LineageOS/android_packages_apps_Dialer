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

import android.support.annotation.NonNull;
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
 final class AutoValue_SimulatorVoicemail_Voicemail extends SimulatorVoicemail.Voicemail {

  private final String phoneNumber;
  private final String transcription;
  private final long durationSeconds;
  private final long timeMillis;
  private final boolean isRead;

  private AutoValue_SimulatorVoicemail_Voicemail(
      String phoneNumber,
      String transcription,
      long durationSeconds,
      long timeMillis,
      boolean isRead) {
    this.phoneNumber = phoneNumber;
    this.transcription = transcription;
    this.durationSeconds = durationSeconds;
    this.timeMillis = timeMillis;
    this.isRead = isRead;
  }

  @NonNull
  @Override
  String getPhoneNumber() {
    return phoneNumber;
  }

  @NonNull
  @Override
  String getTranscription() {
    return transcription;
  }

  @Override
  long getDurationSeconds() {
    return durationSeconds;
  }

  @Override
  long getTimeMillis() {
    return timeMillis;
  }

  @Override
  boolean getIsRead() {
    return isRead;
  }

  @Override
  public String toString() {
    return "Voicemail{"
        + "phoneNumber=" + phoneNumber + ", "
        + "transcription=" + transcription + ", "
        + "durationSeconds=" + durationSeconds + ", "
        + "timeMillis=" + timeMillis + ", "
        + "isRead=" + isRead
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof SimulatorVoicemail.Voicemail) {
      SimulatorVoicemail.Voicemail that = (SimulatorVoicemail.Voicemail) o;
      return (this.phoneNumber.equals(that.getPhoneNumber()))
           && (this.transcription.equals(that.getTranscription()))
           && (this.durationSeconds == that.getDurationSeconds())
           && (this.timeMillis == that.getTimeMillis())
           && (this.isRead == that.getIsRead());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= this.phoneNumber.hashCode();
    h *= 1000003;
    h ^= this.transcription.hashCode();
    h *= 1000003;
    h ^= (this.durationSeconds >>> 32) ^ this.durationSeconds;
    h *= 1000003;
    h ^= (this.timeMillis >>> 32) ^ this.timeMillis;
    h *= 1000003;
    h ^= this.isRead ? 1231 : 1237;
    return h;
  }

  static final class Builder extends SimulatorVoicemail.Voicemail.Builder {
    private String phoneNumber;
    private String transcription;
    private Long durationSeconds;
    private Long timeMillis;
    private Boolean isRead;
    Builder() {
    }
    private Builder(SimulatorVoicemail.Voicemail source) {
      this.phoneNumber = source.getPhoneNumber();
      this.transcription = source.getTranscription();
      this.durationSeconds = source.getDurationSeconds();
      this.timeMillis = source.getTimeMillis();
      this.isRead = source.getIsRead();
    }
    @Override
    SimulatorVoicemail.Voicemail.Builder setPhoneNumber(String phoneNumber) {
      this.phoneNumber = phoneNumber;
      return this;
    }
    @Override
    SimulatorVoicemail.Voicemail.Builder setTranscription(String transcription) {
      this.transcription = transcription;
      return this;
    }
    @Override
    SimulatorVoicemail.Voicemail.Builder setDurationSeconds(long durationSeconds) {
      this.durationSeconds = durationSeconds;
      return this;
    }
    @Override
    SimulatorVoicemail.Voicemail.Builder setTimeMillis(long timeMillis) {
      this.timeMillis = timeMillis;
      return this;
    }
    @Override
    SimulatorVoicemail.Voicemail.Builder setIsRead(boolean isRead) {
      this.isRead = isRead;
      return this;
    }
    @Override
    SimulatorVoicemail.Voicemail build() {
      String missing = "";
      if (this.phoneNumber == null) {
        missing += " phoneNumber";
      }
      if (this.transcription == null) {
        missing += " transcription";
      }
      if (this.durationSeconds == null) {
        missing += " durationSeconds";
      }
      if (this.timeMillis == null) {
        missing += " timeMillis";
      }
      if (this.isRead == null) {
        missing += " isRead";
      }
      if (!missing.isEmpty()) {
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      return new AutoValue_SimulatorVoicemail_Voicemail(
          this.phoneNumber,
          this.transcription,
          this.durationSeconds,
          this.timeMillis,
          this.isRead);
    }
  }

}
