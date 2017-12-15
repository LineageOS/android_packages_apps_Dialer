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
import com.android.dialer.contactactions.ContactActionModule;
import com.android.dialer.contactactions.DividerModule;
import com.android.dialer.contactactions.SharedModules;
import com.android.dialer.voicemail.model.VoicemailEntry;
import java.util.ArrayList;
import java.util.List;

/**
 * Configures the modules for the voicemail bottom sheet; these are the rows below the top row
 * (primary action) in the bottom sheet.
 */
final class Modules {

  static List<ContactActionModule> fromVoicemailEntry(
      Context context, VoicemailEntry voicemailEntry) {
    // Conditionally add each module, which are items in the bottom sheet's menu.
    List<ContactActionModule> modules = new ArrayList<>();

    // TODO(uabdullah): Handle maybeAddModuleForVideoOrAudioCall(context, modules, row);
    SharedModules.maybeAddModuleForAddingToContacts(
        context,
        modules,
        voicemailEntry.number(),
        voicemailEntry.name(),
        voicemailEntry.lookupUri());

    String originalNumber = voicemailEntry.number().getRawInput().getNumber();
    SharedModules.maybeAddModuleForSendingTextMessage(context, modules, originalNumber);

    if (!modules.isEmpty()) {
      modules.add(new DividerModule());
    }

    // TODO(zachh): Module for blocking/unblocking spam.
    // TODO(zachh): Module for CallComposer.
    SharedModules.maybeAddModuleForCopyingNumber(context, modules, originalNumber);

    return modules;
  }
}
