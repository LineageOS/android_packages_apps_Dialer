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

package com.android.dialer.multimedia;

import android.location.Location;
import android.net.Uri;
import android.support.annotation.Nullable;
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
 final class AutoValue_MultimediaData extends MultimediaData {

  private final String subject;
  private final Location location;
  private final Uri imageUri;
  private final String imageContentType;
  private final boolean important;

  private AutoValue_MultimediaData(
      @Nullable String subject,
      @Nullable Location location,
      @Nullable Uri imageUri,
      @Nullable String imageContentType,
      boolean important) {
    this.subject = subject;
    this.location = location;
    this.imageUri = imageUri;
    this.imageContentType = imageContentType;
    this.important = important;
  }

  @Nullable
  @Override
  public String getSubject() {
    return subject;
  }

  @Nullable
  @Override
  public Location getLocation() {
    return location;
  }

  @Nullable
  @Override
  public Uri getImageUri() {
    return imageUri;
  }

  @Nullable
  @Override
  public String getImageContentType() {
    return imageContentType;
  }

  @Override
  public boolean isImportant() {
    return important;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof MultimediaData) {
      MultimediaData that = (MultimediaData) o;
      return ((this.subject == null) ? (that.getSubject() == null) : this.subject.equals(that.getSubject()))
           && ((this.location == null) ? (that.getLocation() == null) : this.location.equals(that.getLocation()))
           && ((this.imageUri == null) ? (that.getImageUri() == null) : this.imageUri.equals(that.getImageUri()))
           && ((this.imageContentType == null) ? (that.getImageContentType() == null) : this.imageContentType.equals(that.getImageContentType()))
           && (this.important == that.isImportant());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (subject == null) ? 0 : this.subject.hashCode();
    h *= 1000003;
    h ^= (location == null) ? 0 : this.location.hashCode();
    h *= 1000003;
    h ^= (imageUri == null) ? 0 : this.imageUri.hashCode();
    h *= 1000003;
    h ^= (imageContentType == null) ? 0 : this.imageContentType.hashCode();
    h *= 1000003;
    h ^= this.important ? 1231 : 1237;
    return h;
  }

  static final class Builder extends MultimediaData.Builder {
    private String subject;
    private Location location;
    private Uri imageUri;
    private String imageContentType;
    private Boolean important;
    Builder() {
    }
    private Builder(MultimediaData source) {
      this.subject = source.getSubject();
      this.location = source.getLocation();
      this.imageUri = source.getImageUri();
      this.imageContentType = source.getImageContentType();
      this.important = source.isImportant();
    }
    @Override
    public MultimediaData.Builder setSubject(@Nullable String subject) {
      this.subject = subject;
      return this;
    }
    @Override
    public MultimediaData.Builder setLocation(@Nullable Location location) {
      this.location = location;
      return this;
    }
    @Override
    MultimediaData.Builder setImageUri(@Nullable Uri imageUri) {
      this.imageUri = imageUri;
      return this;
    }
    @Override
    MultimediaData.Builder setImageContentType(@Nullable String imageContentType) {
      this.imageContentType = imageContentType;
      return this;
    }
    @Override
    public MultimediaData.Builder setImportant(boolean important) {
      this.important = important;
      return this;
    }
    @Override
    public MultimediaData build() {
      String missing = "";
      if (this.important == null) {
        missing += " important";
      }
      if (!missing.isEmpty()) {
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      return new AutoValue_MultimediaData(
          this.subject,
          this.location,
          this.imageUri,
          this.imageContentType,
          this.important);
    }
  }

}
