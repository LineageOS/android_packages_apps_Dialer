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

package com.android.dialer.calllog.model;

import android.support.annotation.ColorInt;
import android.support.annotation.Nullable;
import com.android.dialer.CoalescedIds;
import com.android.dialer.DialerPhoneNumber;
import com.google.auto.value.AutoValue;

/** Data class containing the contents of a row from the CoalescedAnnotatedCallLog. */
@AutoValue
public abstract class CoalescedRow {

  public static Builder builder() {
    return new AutoValue_CoalescedRow.Builder()
        .setId(0)
        .setTimestamp(0)
        .setNumber(DialerPhoneNumber.getDefaultInstance())
        .setPhotoId(0)
        .setIsRead(false)
        .setIsNew(false)
        .setPhoneAccountColor(0)
        .setFeatures(0)
        .setIsBusiness(false)
        .setIsVoicemail(false)
        .setCallType(0)
        .setCoalescedIds(CoalescedIds.getDefaultInstance());
  }

  public abstract int id();

  public abstract long timestamp();

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
  public abstract String numberTypeLabel();

  public abstract boolean isRead();

  public abstract boolean isNew();

  @Nullable
  public abstract String geocodedLocation();

  @Nullable
  public abstract String phoneAccountComponentName();

  @Nullable
  public abstract String phoneAccountId();

  @Nullable
  public abstract String phoneAccountLabel();

  @ColorInt
  public abstract int phoneAccountColor();

  public abstract int features();

  public abstract boolean isBusiness();

  public abstract boolean isVoicemail();

  public abstract int callType();

  public abstract CoalescedIds coalescedIds();

  /** Builder for {@link CoalescedRow}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setId(int id);

    public abstract Builder setTimestamp(long timestamp);

    public abstract Builder setNumber(DialerPhoneNumber number);

    public abstract Builder setName(@Nullable String name);

    public abstract Builder setFormattedNumber(@Nullable String formattedNumber);

    public abstract Builder setPhotoUri(@Nullable String photoUri);

    public abstract Builder setPhotoId(long photoId);

    public abstract Builder setLookupUri(@Nullable String lookupUri);

    public abstract Builder setNumberTypeLabel(@Nullable String numberTypeLabel);

    public abstract Builder setIsRead(boolean isRead);

    public abstract Builder setIsNew(boolean isNew);

    public abstract Builder setGeocodedLocation(@Nullable String geocodedLocation);

    public abstract Builder setPhoneAccountComponentName(
        @Nullable String phoneAccountComponentName);

    public abstract Builder setPhoneAccountId(@Nullable String phoneAccountId);

    public abstract Builder setPhoneAccountLabel(@Nullable String phoneAccountLabel);

    public abstract Builder setPhoneAccountColor(@ColorInt int phoneAccountColor);

    public abstract Builder setFeatures(int features);

    public abstract Builder setIsBusiness(boolean isBusiness);

    public abstract Builder setIsVoicemail(boolean isVoicemail);

    public abstract Builder setCallType(int callType);

    public abstract Builder setCoalescedIds(CoalescedIds coalescedIds);

    public abstract CoalescedRow build();
  }
}
