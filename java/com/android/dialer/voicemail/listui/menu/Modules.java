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
import com.android.dialer.blockreportspam.BlockReportSpamDialogInfo;
import com.android.dialer.historyitemactions.DividerModule;
import com.android.dialer.historyitemactions.HistoryItemActionModule;
import com.android.dialer.historyitemactions.SharedModules;
import com.android.dialer.logging.ReportingLocation;
import com.android.dialer.voicemail.model.VoicemailEntry;
import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.List;

/**
 * Configures the modules for the voicemail bottom sheet; these are the rows below the top row
 * (primary action) in the bottom sheet.
 */
@SuppressWarnings("Guava")
final class Modules {

  static List<HistoryItemActionModule> fromVoicemailEntry(
      Context context, VoicemailEntry voicemailEntry) {
    // Conditionally add each module, which are items in the bottom sheet's menu.
    List<HistoryItemActionModule> modules = new ArrayList<>();

    // TODO(uabdullah): Handle maybeAddModuleForVideoOrAudioCall(context, modules, row);
    Optional<HistoryItemActionModule> moduleForAddingContacts =
        SharedModules.createModuleForAddingToContacts(
            context,
            voicemailEntry.number(),
            voicemailEntry.numberAttributes().getName(),
            voicemailEntry.numberAttributes().getLookupUri(),
            voicemailEntry.numberAttributes().getIsBlocked(),
            voicemailEntry.numberAttributes().getIsSpam());
    if (moduleForAddingContacts.isPresent()) {
      modules.add(moduleForAddingContacts.get());
    }

    Optional<HistoryItemActionModule> moduleForSendingTextMessage =
        SharedModules.createModuleForSendingTextMessage(
            context,
            voicemailEntry.number().getNormalizedNumber(),
            voicemailEntry.numberAttributes().getIsBlocked());
    if (moduleForSendingTextMessage.isPresent()) {
      modules.add(moduleForSendingTextMessage.get());
    }

    if (!modules.isEmpty()) {
      modules.add(new DividerModule());
    }

    BlockReportSpamDialogInfo blockReportSpamDialogInfo =
        BlockReportSpamDialogInfo.newBuilder()
            .setNormalizedNumber(voicemailEntry.number().getNormalizedNumber())
            .setCountryIso(voicemailEntry.number().getCountryIso())
            .setCallType(voicemailEntry.callType())
            .setReportingLocation(ReportingLocation.Type.VOICEMAIL_HISTORY)
            .setContactSource(voicemailEntry.numberAttributes().getContactSource())
            .build();
    modules.addAll(
        SharedModules.createModulesHandlingBlockedOrSpamNumber(
            context,
            blockReportSpamDialogInfo,
            voicemailEntry.numberAttributes().getIsBlocked(),
            voicemailEntry.numberAttributes().getIsSpam()));

    // TODO(zachh): Module for CallComposer.
    Optional<HistoryItemActionModule> moduleForCopyingNumber =
        SharedModules.createModuleForCopyingNumber(
            context, voicemailEntry.number().getNormalizedNumber());
    if (moduleForCopyingNumber.isPresent()) {
      modules.add(moduleForCopyingNumber.get());
    }

    return modules;
  }
}
