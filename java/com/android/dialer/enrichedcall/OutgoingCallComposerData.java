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

package com.android.dialer.enrichedcall;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.common.Assert;
import com.google.auto.value.AutoValue;

/**
 * Value type holding references to all data that could be provided for the call composer.
 *
 * <p>Note: Either the subject, the image data, or both must be specified, e.g.
 *
 * <pre>
 *   OutgoingCallComposerData.builder.build(); // throws exception, no data set
 *   OutgoingCallComposerData.builder
 *       .setText(subject)
 *       .build(); // Success
 *   OutgoingCallComposerData.builder
 *       .setImageData(uri, contentType)
 *       .build(); // Success
 *   OutgoingCallComposerData.builder
 *      .setText(subject)
 *      .setImageData(uri, contentType)
 *      .build(); // Success
 * </pre>
 */
@AutoValue
public abstract class OutgoingCallComposerData {

  public static Builder builder() {
    return new AutoValue_OutgoingCallComposerData.Builder();
  }

  public boolean hasImageData() {
    return getImageUri() != null && getImageContentType() != null;
  }

  @Nullable
  public abstract String getSubject();

  @Nullable
  public abstract Uri getImageUri();

  @Nullable
  public abstract String getImageContentType();

  /** Builds instances of {@link OutgoingCallComposerData}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setSubject(String subject);

    public Builder setImageData(@NonNull Uri imageUri, @NonNull String imageContentType) {
      setImageUri(Assert.isNotNull(imageUri));
      setImageContentType(Assert.isNotNull(imageContentType));
      return this;
    }

    abstract Builder setImageUri(Uri imageUri);

    abstract Builder setImageContentType(String imageContentType);

    abstract OutgoingCallComposerData autoBuild();

    /**
     * Returns the OutgoingCallComposerData from this builder.
     *
     * @return the OutgoingCallComposerData.
     * @throws IllegalStateException if neither {@link #setSubject(String)} nor {@link
     *     #setImageData(Uri, String)} were called.
     */
    public OutgoingCallComposerData build() {
      OutgoingCallComposerData data = autoBuild();
      Assert.checkState(data.getSubject() != null || data.hasImageData());
      return data;
    }
  }
}
