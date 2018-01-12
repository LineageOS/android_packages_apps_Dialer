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

import android.content.Context;
import android.text.TextUtils;
import com.android.dialer.contactactions.ContactPrimaryActionInfo;
import com.android.dialer.contactactions.ContactPrimaryActionInfo.PhotoInfo;
import com.android.dialer.lettertile.LetterTileDrawable;
import com.android.dialer.voicemail.model.VoicemailEntry;

/** Configures the primary action row (top row) for theottom sheet for the Voicemail Tab */
final class PrimaryAction {

  // TODO(uabdullah): Need to do the following:
  // setIsVideo - check if is passing in voicemailEntry.features() is required
  // setLookupUri - check if passing in voicemailEntry.lookupUri() is required
  // setIntent - allow video calling
  // setPrimaryText - check in with UX
  // setSecondaryText - check in with UX
  static ContactPrimaryActionInfo fromVoicemailEntry(
      Context context, VoicemailEntry voicemailEntry) {
    return ContactPrimaryActionInfo.builder()
        .setNumber(voicemailEntry.number())
        .setPhotoInfo(
            PhotoInfo.builder()
                .setPhotoId(voicemailEntry.numberAttributes().getPhotoId())
                .setPhotoUri(voicemailEntry.numberAttributes().getPhotoUri())
                .setIsVideo(false)
                .setContactType(
                    LetterTileDrawable.TYPE_DEFAULT) // TODO(uabdullah): Use proper type.
                .setDisplayName(voicemailEntry.numberAttributes().getName())
                .build())
        .setPrimaryText(buildPrimaryVoicemailText(context, voicemailEntry))
        .setSecondaryText(buildSecondaryVoicemailText(voicemailEntry))
        .build();
  }

  private static CharSequence buildSecondaryVoicemailText(VoicemailEntry voicemailEntry) {
    return voicemailEntry.geocodedLocation();
  }

  public static String buildPrimaryVoicemailText(Context context, VoicemailEntry data) {
    StringBuilder primaryText = new StringBuilder();
    if (!TextUtils.isEmpty(data.numberAttributes().getName())) {
      primaryText.append(data.numberAttributes().getName());
    } else if (!TextUtils.isEmpty(data.formattedNumber())) {
      primaryText.append(data.formattedNumber());
    } else {
      // TODO(uabdullah): Handle CallLog.Calls.PRESENTATION_*, including Verizon restricted numbers.
      // primaryText.append(context.getText(R.string.voicemail_unknown));
      // TODO(uabdullah): Figure out why http://gpaste/5980163120562176 error when using string
      primaryText.append("Unknown");
    }
    return primaryText.toString();
  }
}
