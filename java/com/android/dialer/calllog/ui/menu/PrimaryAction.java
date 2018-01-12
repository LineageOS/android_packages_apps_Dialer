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

package com.android.dialer.calllog.ui.menu;

import android.content.Context;
import android.provider.CallLog.Calls;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.calllogutils.CallLogContactTypes;
import com.android.dialer.calllogutils.CallLogEntryText;
import com.android.dialer.calllogutils.CallLogIntents;
import com.android.dialer.contactactions.ContactPrimaryActionInfo;
import com.android.dialer.contactactions.ContactPrimaryActionInfo.PhotoInfo;

/** Configures the primary action row (top row) for the bottom sheet. */
final class PrimaryAction {

  static ContactPrimaryActionInfo fromRow(Context context, CoalescedRow row) {
    CharSequence primaryText = CallLogEntryText.buildPrimaryText(context, row);
    return ContactPrimaryActionInfo.builder()
        .setNumber(row.number())
        .setPhotoInfo(
            PhotoInfo.builder()
                .setPhotoId(row.numberAttributes().getPhotoId())
                .setPhotoUri(row.numberAttributes().getPhotoUri())
                .setLookupUri(row.numberAttributes().getLookupUri())
                .setIsVideo((row.features() & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO)
                .setContactType(CallLogContactTypes.getContactType(row))
                .setDisplayName(primaryText.toString())
                .build())
        .setPrimaryText(primaryText)
        .setSecondaryText(CallLogEntryText.buildSecondaryTextForBottomSheet(context, row))
        .setIntent(CallLogIntents.getCallBackIntent(context, row))
        .build();
  }
}
