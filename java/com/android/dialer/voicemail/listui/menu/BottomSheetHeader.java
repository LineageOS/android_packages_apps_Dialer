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

package com.android.dialer.voicemail.listui.menu;

import android.text.TextUtils;
import com.android.dialer.calllogutils.PhotoInfoBuilder;
import com.android.dialer.historyitemactions.HistoryItemBottomSheetHeaderInfo;
import com.android.dialer.voicemail.model.VoicemailEntry;

/** Configures the top row in the bottom sheet for the Voicemail Tab */
final class BottomSheetHeader {

  static HistoryItemBottomSheetHeaderInfo fromVoicemailEntry(VoicemailEntry voicemailEntry) {
    return HistoryItemBottomSheetHeaderInfo.newBuilder()
        .setNumber(voicemailEntry.getNumber())
        .setPhotoInfo(PhotoInfoBuilder.fromVoicemailEntry(voicemailEntry))
        .setPrimaryText(buildPrimaryVoicemailText(voicemailEntry))
        .setSecondaryText(buildSecondaryVoicemailText(voicemailEntry))
        .build();
  }

  private static String buildSecondaryVoicemailText(VoicemailEntry voicemailEntry) {
    return voicemailEntry.getGeocodedLocation();
  }

  private static String buildPrimaryVoicemailText(VoicemailEntry data) {
    StringBuilder primaryText = new StringBuilder();
    if (!TextUtils.isEmpty(data.getNumberAttributes().getName())) {
      primaryText.append(data.getNumberAttributes().getName());
    } else if (!TextUtils.isEmpty(data.getFormattedNumber())) {
      primaryText.append(data.getFormattedNumber());
    } else {
      // TODO(uabdullah): Handle CallLog.Calls.PRESENTATION_*, including Verizon restricted numbers.
      // primaryText.append(context.getText(R.string.voicemail_unknown));
      // TODO(uabdullah): Figure out why http://gpaste/5980163120562176 error when using string
      primaryText.append("Unknown");
    }
    return primaryText.toString();
  }
}
