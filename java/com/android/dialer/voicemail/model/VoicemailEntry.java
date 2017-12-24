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
 * limitations under the License.
 */

package com.android.dialer.voicemail.model;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.DialerPhoneNumber;
import com.google.auto.value.AutoValue;

/** Data class containing the contents of a voicemail entry from the AnnotatedCallLog. */
@AutoValue
public abstract class VoicemailEntry {

  public static Builder builder() {
    return new AutoValue_VoicemailEntry.Builder()
        .setId(0)
        .setTimestamp(0)
        .setNumber(DialerPhoneNumber.getDefaultInstance())
        .setPhotoId(0)
        .setDuration(0)
        .setCallType(0)
        .setIsRead(0);
  }

  public abstract int id();

  public abstract long timestamp();

  @NonNull
  public abstract DialerPhoneNumber number();

  @Nullable
  public abstract String name();

  @Nullable
  public abstract String formattedNumber();

  @Nullable
  public abstract String photoUri();

  public abstract long photoId();

  @Nullable
  public abstract String lookupUri();

  @Nullable
  public abstract String geocodedLocation();

  public abstract long duration();

  @Nullable
  public abstract String transcription();

  @Nullable
  public abstract String voicemailUri();

  public abstract int callType();

  public abstract int isRead();

  /** Builder for {@link VoicemailEntry}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setId(int id);

    public abstract Builder setTimestamp(long timestamp);

    public abstract Builder setNumber(@NonNull DialerPhoneNumber number);

    public abstract Builder setName(@Nullable String name);

    public abstract Builder setFormattedNumber(@Nullable String formattedNumber);

    public abstract Builder setPhotoUri(@Nullable String photoUri);

    public abstract Builder setPhotoId(long photoId);

    public abstract Builder setLookupUri(@Nullable String lookupUri);

    public abstract Builder setDuration(long duration);

    public abstract Builder setTranscription(@Nullable String transcription);

    public abstract Builder setVoicemailUri(@Nullable String voicemailUri);

    public abstract Builder setGeocodedLocation(@Nullable String geocodedLocation);

    public abstract Builder setCallType(int callType);

    public abstract Builder setIsRead(int isRead);

    public abstract VoicemailEntry build();
  }
}
