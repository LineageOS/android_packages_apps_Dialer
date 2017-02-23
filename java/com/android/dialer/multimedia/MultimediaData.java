/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.multimedia;

import android.location.Location;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.common.LogUtil;


/** Holds the data associated with an enriched call session. */

public abstract class MultimediaData {

  public static final MultimediaData EMPTY = builder().build();

  @NonNull
  public static Builder builder() {
    return new AutoValue_MultimediaData.Builder().setImportant(false);
  }

  /** Returns the call composer subject if set, or null if this isn't a call composer session. */
  @Nullable
  public abstract String getSubject();

  /** Returns the call composer location if set, or null if this isn't a call composer session. */
  @Nullable
  public abstract Location getLocation();

  /** Returns {@code true} if this session contains image data. */
  public boolean hasImageData() {
    // imageUri and content are always either both null or nonnull
    return getImageUri() != null && getImageContentType() != null;
  }

  /** Returns the call composer photo if set, or null if this isn't a call composer session. */
  @Nullable
  public abstract Uri getImageUri();

  /**
   * Returns the content type of the image, either image/png or image/jpeg, if set, or null if this
   * isn't a call composer session.
   */
  @Nullable
  public abstract String getImageContentType();

  /** Returns {@code true} if this is a call composer session that's marked as important. */
  public abstract boolean isImportant();

  /** Returns the string form of this MultimediaData with no PII. */
  @Override
  public String toString() {
    return String.format(
        "MultimediaData{subject: %s, location: %s, imageUrl: %s, imageContentType: %s, "
            + "important: %b}",
        LogUtil.sanitizePii(getSubject()),
        LogUtil.sanitizePii(getLocation()),
        LogUtil.sanitizePii(getImageUri()),
        getImageContentType(),
        isImportant());
  }

  /** Creates instances of {@link MultimediaData}. */

  public abstract static class Builder {

    public abstract Builder setSubject(@NonNull String subject);

    public abstract Builder setLocation(@NonNull Location location);

    public Builder setImage(@NonNull Uri image, @NonNull String imageContentType) {
      setImageUri(image);
      setImageContentType(imageContentType);
      return this;
    }

    abstract Builder setImageUri(@NonNull Uri image);

    abstract Builder setImageContentType(@NonNull String imageContentType);

    public abstract Builder setImportant(boolean isImportant);

    public abstract MultimediaData build();
  }
}
