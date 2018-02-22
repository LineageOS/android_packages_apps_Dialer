/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.glidephotomanager;

import android.support.annotation.Nullable;
import com.google.auto.value.AutoValue;

/** The number information used to create the photo.. */
@AutoValue
public abstract class PhotoInfo {

  /** The display name of the number */
  @Nullable
  public abstract String name();

  /** The number displayed to the user. */
  @Nullable
  public abstract String formattedNumber();

  /** The URI to the photo */
  @Nullable
  public abstract String photoUri();

  /** Value of {@link android.provider.ContactsContract.CommonDataKinds.Photo#_ID} */
  public abstract long photoId();

  /** The contacts provider lookup URI for the contact associated with the number */
  @Nullable
  public abstract String lookupUri();

  /** Should a business icon be displayed */
  public abstract boolean isBusiness();

  /** Should a voicemail icon be displayed */
  public abstract boolean isVoicemail();

  /** Should a blocked icon be displayed */
  public abstract boolean isBlocked();

  /** Should a spam icon be displayed */
  public abstract boolean isSpam();

  /**
   * Should the photo be badged as video call.
   *
   * <p>Defaults to false.
   */
  public abstract boolean isVideo();

  /** Builder for {@link PhotoInfo} */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setName(@Nullable String name);

    public abstract Builder setFormattedNumber(@Nullable String formattedNumber);

    public abstract Builder setPhotoUri(@Nullable String uri);

    public abstract Builder setPhotoId(long id);

    public abstract Builder setLookupUri(@Nullable String uri);

    public abstract Builder setIsBusiness(boolean isBusiness);

    public abstract Builder setIsVoicemail(boolean isVoicemail);

    public abstract Builder setIsBlocked(boolean isBlocked);

    public abstract Builder setIsSpam(boolean isSpam);

    public abstract Builder setIsVideo(boolean isVideo);

    public abstract PhotoInfo build();
  }

  public static PhotoInfo.Builder builder() {
    return new AutoValue_PhotoInfo.Builder()
        .setPhotoId(0)
        .setIsBusiness(false)
        .setIsVoicemail(false)
        .setIsBlocked(false)
        .setIsSpam(false)
        .setIsVideo(false);
  }
}
