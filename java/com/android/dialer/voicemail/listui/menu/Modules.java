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
import com.android.dialer.historyitemactions.HistoryItemActionModule;
import com.android.dialer.historyitemactions.HistoryItemActionModuleInfo;
import com.android.dialer.historyitemactions.HistoryItemActionModulesBuilder;
import com.android.dialer.voicemail.model.VoicemailEntry;
import java.util.List;

/**
 * Configures the modules for the voicemail bottom sheet; these are the rows below the top row
 * (contact info) in the bottom sheet.
 */
final class Modules {

  static List<HistoryItemActionModule> fromVoicemailEntry(
      Context context, VoicemailEntry voicemailEntry) {
    return new HistoryItemActionModulesBuilder(context, buildModuleInfo(voicemailEntry))
        // TODO(uabdullah): add module for calls.
        .addModuleForAddingToContacts()
        .addModuleForSendingTextMessage()
        .addModuleForDivider()
        .addModuleForBlockedOrSpamNumber()
        .addModuleForCopyingNumber()
        // TODO(zachh): Module for CallComposer.
        .build();
  }

  private static HistoryItemActionModuleInfo buildModuleInfo(VoicemailEntry voicemailEntry) {
    return HistoryItemActionModuleInfo.newBuilder()
        .setNormalizedNumber(voicemailEntry.getNumber().getNormalizedNumber())
        .setCountryIso(voicemailEntry.getNumber().getCountryIso())
        .setName(voicemailEntry.getNumberAttributes().getName())
        .setCallType(voicemailEntry.getCallType())
        .setLookupUri(voicemailEntry.getNumberAttributes().getLookupUri())
        .setPhoneAccountComponentName(voicemailEntry.getPhoneAccountComponentName())
        .setCanReportAsInvalidNumber(
            voicemailEntry.getNumberAttributes().getCanReportAsInvalidNumber())
        .setCanSupportAssistedDialing(
            !TextUtils.isEmpty(voicemailEntry.getNumberAttributes().getLookupUri()))
        .setCanSupportCarrierVideoCall(
            voicemailEntry.getNumberAttributes().getCanSupportCarrierVideoCall())
        .setIsBlocked(voicemailEntry.getNumberAttributes().getIsBlocked())
        .setIsEmergencyNumber(voicemailEntry.getNumberAttributes().getIsEmergencyNumber())
        .setIsSpam(voicemailEntry.getNumberAttributes().getIsSpam())
        // A voicemail call is an outgoing call to the voicemail box.
        // Voicemail entries are not voicemail calls.
        .setIsVoicemailCall(false)
        .setContactSource(voicemailEntry.getNumberAttributes().getContactSource())
        .setHost(HistoryItemActionModuleInfo.Host.VOICEMAIL)
        .build();
  }
}
