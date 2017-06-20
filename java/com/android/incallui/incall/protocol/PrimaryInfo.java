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

import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import com.android.dialer.common.LogUtil;
import com.android.dialer.multimedia.MultimediaData;
import java.util.Locale;

/** Information about the primary call. */
public class PrimaryInfo {
  @Nullable public final String number;
  @Nullable public final String name;
  public final boolean nameIsNumber;
  // This is from contacts and shows the type of number. For example, "Mobile".
  @Nullable public final String label;
  @Nullable public final String location;
  @Nullable public final Drawable photo;
  @ContactPhotoType public final int photoType;
  public final boolean isSipCall;
  public final boolean isContactPhotoShown;
  public final boolean isWorkCall;
  public final boolean isSpam;
  public final boolean answeringDisconnectsOngoingCall;
  public final boolean shouldShowLocation;
  // Used for consistent LetterTile coloring.
  @Nullable public final String contactInfoLookupKey;
  @Nullable public final MultimediaData multimediaData;
  public final int numberPresentation;

  // TODO: Convert to autovalue. b/34502119
  public static PrimaryInfo createEmptyPrimaryInfo() {
    return new PrimaryInfo(
        null,
        null,
        false,
        null,
        null,
        null,
        ContactPhotoType.DEFAULT_PLACEHOLDER,
        false,
        false,
        false,
        false,
        false,
        false,
        null,
        null,
        -1);
  }

  public PrimaryInfo(
      @Nullable String number,
      @Nullable String name,
      boolean nameIsNumber,
      @Nullable String location,
      @Nullable String label,
      @Nullable Drawable photo,
      @ContactPhotoType int phototType,
      boolean isSipCall,
      boolean isContactPhotoShown,
      boolean isWorkCall,
      boolean isSpam,
      boolean answeringDisconnectsOngoingCall,
      boolean shouldShowLocation,
      @Nullable String contactInfoLookupKey,
      @Nullable MultimediaData multimediaData,
      int numberPresentation) {
    this.number = number;
    this.name = name;
    this.nameIsNumber = nameIsNumber;
    this.location = location;
    this.label = label;
    this.photo = photo;
    this.photoType = phototType;
    this.isSipCall = isSipCall;
    this.isContactPhotoShown = isContactPhotoShown;
    this.isWorkCall = isWorkCall;
    this.isSpam = isSpam;
    this.answeringDisconnectsOngoingCall = answeringDisconnectsOngoingCall;
    this.shouldShowLocation = shouldShowLocation;
    this.contactInfoLookupKey = contactInfoLookupKey;
    this.multimediaData = multimediaData;
    this.numberPresentation = numberPresentation;
  }

  @Override
  public String toString() {
    return String.format(
        Locale.US,
        "PrimaryInfo, number: %s, name: %s, location: %s, label: %s, "
            + "photo: %s, photoType: %d, isPhotoVisible: %b, MultimediaData: %s",
        LogUtil.sanitizePhoneNumber(number),
        LogUtil.sanitizePii(name),
        LogUtil.sanitizePii(location),
        label,
        photo,
        photoType,
        isContactPhotoShown,
        multimediaData);
  }
}
