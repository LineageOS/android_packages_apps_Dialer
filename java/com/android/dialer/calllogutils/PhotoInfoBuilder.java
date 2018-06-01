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

import android.content.Context;
import android.provider.CallLog.Calls;
import android.support.v4.os.BuildCompat;
import com.android.dialer.NumberAttributes;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.glidephotomanager.PhotoInfo;
import com.android.dialer.spam.Spam;
import com.android.dialer.voicemail.model.VoicemailEntry;

/** Builds {@link PhotoInfo} from other data types. */
public final class PhotoInfoBuilder {

  /** Returns a {@link PhotoInfo.Builder} with info from {@link CoalescedRow}. */
  public static PhotoInfo.Builder fromCoalescedRow(Context context, CoalescedRow coalescedRow) {
    return fromNumberAttributes(coalescedRow.getNumberAttributes())
        .setName(CallLogEntryText.buildPrimaryText(context, coalescedRow).toString())
        .setFormattedNumber(coalescedRow.getFormattedNumber())
        .setIsVoicemail(coalescedRow.getIsVoicemailCall())
        .setIsSpam(
            Spam.shouldShowAsSpam(
                coalescedRow.getNumberAttributes().getIsSpam(), coalescedRow.getCallType()))
        .setIsVideo((coalescedRow.getFeatures() & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO)
        .setIsRtt(
            BuildCompat.isAtLeastP()
                && (coalescedRow.getFeatures() & Calls.FEATURES_RTT) == Calls.FEATURES_RTT);
  }

  /** Returns a {@link PhotoInfo.Builder} with info from {@link VoicemailEntry}. */
  public static PhotoInfo.Builder fromVoicemailEntry(VoicemailEntry voicemailEntry) {
    return fromNumberAttributes(voicemailEntry.getNumberAttributes())
        .setFormattedNumber(voicemailEntry.getFormattedNumber())
        .setIsSpam(
            Spam.shouldShowAsSpam(
                voicemailEntry.getNumberAttributes().getIsSpam(), voicemailEntry.getCallType()));
  }

  /** Returns a {@link PhotoInfo.Builder} with info from {@link NumberAttributes}. */
  private static PhotoInfo.Builder fromNumberAttributes(NumberAttributes numberAttributes) {
    return PhotoInfo.newBuilder()
        .setName(numberAttributes.getName())
        .setPhotoUri(numberAttributes.getPhotoUri())
        .setPhotoId(numberAttributes.getPhotoId())
        .setLookupUri(numberAttributes.getLookupUri())
        .setIsBusiness(numberAttributes.getIsBusiness())
        .setIsBlocked(numberAttributes.getIsBlocked());
  }
}
