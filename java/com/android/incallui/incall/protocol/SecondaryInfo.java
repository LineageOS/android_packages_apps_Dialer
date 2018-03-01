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
 * limitations under the License.
 */

package com.android.incallui.incall.protocol;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import com.android.dialer.common.LogUtil;
import com.google.auto.value.AutoValue;
import java.util.Locale;

/** Information about the secondary call. */
@AutoValue
public abstract class SecondaryInfo implements Parcelable {
  public abstract boolean shouldShow();

  @Nullable
  public abstract String name();

  public abstract boolean nameIsNumber();

  @Nullable
  public abstract String label();

  @Nullable
  public abstract String providerLabel();

  public abstract boolean isConference();

  public abstract boolean isVideoCall();

  public abstract boolean isFullscreen();

  public static Builder builder() {
    return new AutoValue_SecondaryInfo.Builder()
        .setShouldShow(false)
        .setNameIsNumber(false)
        .setIsConference(false)
        .setIsVideoCall(false)
        .setIsFullscreen(false);
  }

  /** Builder class for secondary info. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setShouldShow(boolean shouldShow);

    public abstract Builder setName(String name);

    public abstract Builder setNameIsNumber(boolean nameIsNumber);

    public abstract Builder setLabel(String label);

    public abstract Builder setProviderLabel(String providerLabel);

    public abstract Builder setIsConference(boolean isConference);

    public abstract Builder setIsVideoCall(boolean isVideoCall);

    public abstract Builder setIsFullscreen(boolean isFullscreen);

    public abstract SecondaryInfo build();
  }

  @Override
  public String toString() {
    return String.format(
        Locale.US,
        "SecondaryInfo, show: %b, name: %s, label: %s, " + "providerLabel: %s",
        shouldShow(),
        LogUtil.sanitizePii(name()),
        label(),
        providerLabel());
  }

  public static final Creator<SecondaryInfo> CREATOR =
      new Creator<SecondaryInfo>() {
        @Override
        public SecondaryInfo createFromParcel(Parcel in) {
          return builder()
              .setShouldShow(in.readByte() != 0)
              .setName(in.readString())
              .setNameIsNumber(in.readByte() != 0)
              .setLabel(in.readString())
              .setProviderLabel(in.readString())
              .setIsConference(in.readByte() != 0)
              .setIsVideoCall(in.readByte() != 0)
              .setIsFullscreen(in.readByte() != 0)
              .build();
        }

        @Override
        public SecondaryInfo[] newArray(int size) {
          return new SecondaryInfo[size];
        }
      };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeByte((byte) (shouldShow() ? 1 : 0));
    dest.writeString(name());
    dest.writeByte((byte) (nameIsNumber() ? 1 : 0));
    dest.writeString(label());
    dest.writeString(providerLabel());
    dest.writeByte((byte) (isConference() ? 1 : 0));
    dest.writeByte((byte) (isVideoCall() ? 1 : 0));
    dest.writeByte((byte) (isFullscreen() ? 1 : 0));
  }
}
