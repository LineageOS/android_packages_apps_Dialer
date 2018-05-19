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
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.consolidator.PhoneLookupInfoConsolidator;

/** Builds {@link NumberAttributes} from other data types. */
public final class NumberAttributesBuilder {

  /** Returns a {@link NumberAttributes.Builder} with info from {@link PhoneLookupInfo}. */
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
        .setIsBlocked(phoneLookupInfoConsolidator.isBlocked())
        .setIsSpam(phoneLookupInfoConsolidator.isSpam())
        .setCanReportAsInvalidNumber(phoneLookupInfoConsolidator.canReportAsInvalidNumber())
        .setIsCp2InfoIncomplete(phoneLookupInfoConsolidator.isDefaultCp2InfoIncomplete())
        .setContactSource(phoneLookupInfoConsolidator.getContactSource())
        .setCanSupportCarrierVideoCall(phoneLookupInfoConsolidator.canSupportCarrierVideoCall())
        .setGeolocation(phoneLookupInfoConsolidator.getGeolocation())
        .setIsEmergencyNumber(phoneLookupInfoConsolidator.isEmergencyNumber());
  }
}
