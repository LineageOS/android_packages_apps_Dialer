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

package com.android.dialer.calllogutils;

import android.text.TextUtils;
import com.android.dialer.NumberAttributes;
import com.android.dialer.glidephotomanager.PhotoInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.consolidator.PhoneLookupInfoConsolidator;

/** Converts {@link NumberAttributes} to {@link PhotoInfo} */
public final class NumberAttributesConverter {

  /** Converts {@link NumberAttributes} to {@link PhotoInfo.Builder} */
  public static PhotoInfo.Builder toPhotoInfoBuilder(NumberAttributes numberAttributes) {
    return PhotoInfo.builder()
        .setName(numberAttributes.getName())
        .setPhotoUri(numberAttributes.getPhotoUri())
        .setPhotoId(numberAttributes.getPhotoId())
        .setLookupUri(numberAttributes.getLookupUri())
        .setIsBusiness(numberAttributes.getIsBusiness())
        .setIsSpam(numberAttributes.getIsSpam())
        .setIsVoicemail(numberAttributes.getIsVoicemail())
        .setIsBlocked(numberAttributes.getIsBlocked());
  }

  /** Converts {@link PhoneLookupInfo} to {@link NumberAttributes.Builder} */
  public static NumberAttributes.Builder fromPhoneLookupInfo(PhoneLookupInfo phoneLookupInfo) {
    PhoneLookupInfoConsolidator phoneLookupInfoConsolidator =
        new PhoneLookupInfoConsolidator(phoneLookupInfo);
    return NumberAttributes.newBuilder()
        .setName(phoneLookupInfoConsolidator.getName())
        .setPhotoUri(
            !TextUtils.isEmpty(phoneLookupInfoConsolidator.getPhotoThumbnailUri())
                ? phoneLookupInfoConsolidator.getPhotoThumbnailUri()
                : phoneLookupInfoConsolidator.getPhotoUri())
        .setPhotoId(phoneLookupInfoConsolidator.getPhotoId())
        .setLookupUri(phoneLookupInfoConsolidator.getLookupUri())
        .setNumberTypeLabel(phoneLookupInfoConsolidator.getNumberLabel())
        .setIsBusiness(phoneLookupInfoConsolidator.isBusiness())
        .setIsVoicemail(phoneLookupInfoConsolidator.isVoicemail())
        .setIsBlocked(phoneLookupInfoConsolidator.isBlocked())
        .setIsSpam(phoneLookupInfoConsolidator.isSpam())
        .setCanReportAsInvalidNumber(phoneLookupInfoConsolidator.canReportAsInvalidNumber())
        .setIsCp2InfoIncomplete(phoneLookupInfoConsolidator.isDefaultCp2InfoIncomplete())
        .setContactSource(phoneLookupInfoConsolidator.getContactSource());
  }
}
