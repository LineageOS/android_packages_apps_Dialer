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

package com.android.dialer.voicemail.datasources;

import com.google.auto.value.AutoValue;

/** Dummy voicemail data class to allow us to work on the UI for the new voicemail tab. */
@AutoValue
public abstract class VoicemailData {
  public abstract String name();

  public abstract String location();

  public abstract String date();

  public abstract String duration();

  public abstract String transcription();

  public static Builder builder() {
    return new AutoValue_VoicemailData.Builder();
  }

  /** Creates instances of {@link VoicemailData}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String value);

    public abstract Builder setLocation(String value);

    public abstract Builder setDate(String value);

    public abstract Builder setDuration(String value);

    public abstract Builder setTranscription(String value);

    public abstract VoicemailData build();
  }
}
