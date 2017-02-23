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
import com.android.dialer.common.LogUtil;
import java.util.Locale;

/** Information about the secondary call. */
public class SecondaryInfo implements Parcelable {
  public final boolean shouldShow;
  public final String name;
  public final boolean nameIsNumber;
  public final String label;
  public final String providerLabel;
  public final boolean isConference;
  public final boolean isVideoCall;
  public final boolean isFullscreen;

  public static SecondaryInfo createEmptySecondaryInfo(boolean isFullScreen) {
    return new SecondaryInfo(false, null, false, null, null, false, false, isFullScreen);
  }

  public SecondaryInfo(
      boolean shouldShow,
      String name,
      boolean nameIsNumber,
      String label,
      String providerLabel,
      boolean isConference,
      boolean isVideoCall,
      boolean isFullscreen) {
    this.shouldShow = shouldShow;
    this.name = name;
    this.nameIsNumber = nameIsNumber;
    this.label = label;
    this.providerLabel = providerLabel;
    this.isConference = isConference;
    this.isVideoCall = isVideoCall;
    this.isFullscreen = isFullscreen;
  }

  @Override
  public String toString() {
    return String.format(
        Locale.US,
        "SecondaryInfo, show: %b, name: %s, label: %s, " + "providerLabel: %s",
        shouldShow,
        LogUtil.sanitizePii(name),
        label,
        providerLabel);
  }

  protected SecondaryInfo(Parcel in) {
    shouldShow = in.readByte() != 0;
    name = in.readString();
    nameIsNumber = in.readByte() != 0;
    label = in.readString();
    providerLabel = in.readString();
    isConference = in.readByte() != 0;
    isVideoCall = in.readByte() != 0;
    isFullscreen = in.readByte() != 0;
  }

  public static final Creator<SecondaryInfo> CREATOR =
      new Creator<SecondaryInfo>() {
        @Override
        public SecondaryInfo createFromParcel(Parcel in) {
          return new SecondaryInfo(in);
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
    dest.writeByte((byte) (shouldShow ? 1 : 0));
    dest.writeString(name);
    dest.writeByte((byte) (nameIsNumber ? 1 : 0));
    dest.writeString(label);
    dest.writeString(providerLabel);
    dest.writeByte((byte) (isConference ? 1 : 0));
    dest.writeByte((byte) (isVideoCall ? 1 : 0));
    dest.writeByte((byte) (isFullscreen ? 1 : 0));
  }
}
