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
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import com.android.dialer.calldetails.CallDetailsActivity;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.calllogutils.PhoneAccountUtils;
import com.android.dialer.contactactions.ContactActionModule;
import com.android.dialer.contactactions.DividerModule;
import com.android.dialer.contactactions.IntentModule;
import com.android.dialer.contactactions.SharedModules;
import com.android.dialer.dialercontact.DialerContact;
import com.android.dialer.lettertile.LetterTileDrawable;
import java.util.ArrayList;
import java.util.List;

/**
 * Configures the modules for the bottom sheet; these are the rows below the top row (primary
 * action) in the bottom sheet.
 */
final class Modules {

  static List<ContactActionModule> fromRow(Context context, CoalescedRow row) {
    // Conditionally add each module, which are items in the bottom sheet's menu.
    List<ContactActionModule> modules = new ArrayList<>();

    maybeAddModuleForVideoOrAudioCall(context, modules, row);
    SharedModules.maybeAddModuleForAddingToContacts(
        context, modules, row.number(), row.name(), row.lookupUri());

    String originalNumber = row.number().getRawInput().getNumber();
    SharedModules.maybeAddModuleForSendingTextMessage(context, modules, originalNumber);

    if (!modules.isEmpty()) {
      modules.add(new DividerModule());
    }


    // TODO(zachh): Module for blocking/unblocking spam.
    // TODO(zachh): Module for CallComposer.

    SharedModules.maybeAddModuleForCopyingNumber(context, modules, originalNumber);

    // TODO(zachh): Revisit if DialerContact is the best thing to pass to CallDetails; could
    // it use a ContactPrimaryActionInfo instead?
    addModuleForAccessingCallDetails(context, modules, row);

    return modules;
  }

  private static void maybeAddModuleForVideoOrAudioCall(
      Context context, List<ContactActionModule> modules, CoalescedRow row) {
    String originalNumber = row.number().getRawInput().getNumber();
    if (TextUtils.isEmpty(originalNumber)) {
      // Skip adding the menu item if the phone number is unknown.
      return;
    }

    PhoneAccountHandle phoneAccountHandle =
        PhoneAccountUtils.getAccount(row.phoneAccountComponentName(), row.phoneAccountId());

    if ((row.features() & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO) {
      // Add an audio call item for video calls. Clicking the top entry on the bottom sheet will
      // trigger a video call.
      modules.add(
          IntentModule.newCallModule(
              context, originalNumber, phoneAccountHandle, CallInitiationType.Type.CALL_LOG));
    } else {
      // Add a video call item for audio calls. Click the top entry on the bottom sheet will
      // trigger an audio call.
      // TODO(zachh): Only show video option if video capabilities present?
      modules.add(
          IntentModule.newVideoCallModule(
              context, originalNumber, phoneAccountHandle, CallInitiationType.Type.CALL_LOG));
    }
  }

  private static void addModuleForAccessingCallDetails(
      Context context, List<ContactActionModule> modules, CoalescedRow row) {
    // TODO(zachh): Load canReportInaccurateNumber in CallDetailsActivity
    // (see also isPeopleApiSource(sourceType)).
    boolean canReportInaccurateNumber = false;
    boolean canSupportAssistedDialing = false; // TODO(zachh): Properly set value.

    modules.add(
        new IntentModule(
            context,
            CallDetailsActivity.newInstance(
                context,
                row.coalescedIds(),
                createDialerContactFromRow(row),
                canReportInaccurateNumber,
                canSupportAssistedDialing),
            R.string.call_details_menu_label,
            R.drawable.quantum_ic_info_outline_vd_theme_24));
  }

  private static DialerContact createDialerContactFromRow(CoalescedRow row) {
    // TODO(zachh): Do something with parsed values to make more dialable?
    String originalNumber = row.number().getRawInput().getNumber();

    DialerContact.Builder dialerContactBuilder =
        DialerContact.newBuilder()
            .setNumber(originalNumber)
            .setContactType(LetterTileDrawable.TYPE_DEFAULT) // TODO(zachh): Use proper type.
            .setPhotoId(row.photoId());

    if (!TextUtils.isEmpty(row.name())) {
      dialerContactBuilder.setNameOrNumber(row.name());
    } else if (!TextUtils.isEmpty(originalNumber)) {
      dialerContactBuilder.setNameOrNumber(originalNumber);
    }
    if (row.numberTypeLabel() != null) {
      dialerContactBuilder.setNumberLabel(row.numberTypeLabel());
    }
    if (row.photoUri() != null) {
      dialerContactBuilder.setPhotoUri(row.photoUri());
    }
    if (row.lookupUri() != null) {
      dialerContactBuilder.setContactUri(row.lookupUri());
    }
    if (row.formattedNumber() != null) {
      dialerContactBuilder.setDisplayNumber(row.formattedNumber());
    }
    return dialerContactBuilder.build();
  }
}
