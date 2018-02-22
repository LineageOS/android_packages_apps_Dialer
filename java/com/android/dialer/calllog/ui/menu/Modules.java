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
import com.android.dialer.calllogutils.CallLogContactTypes;
import com.android.dialer.calllogutils.PhoneNumberDisplayUtil;
import com.android.dialer.contactactions.ContactActionModule;
import com.android.dialer.contactactions.DividerModule;
import com.android.dialer.contactactions.IntentModule;
import com.android.dialer.contactactions.SharedModules;
import com.android.dialer.dialercontact.DialerContact;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.telecom.TelecomUtil;
import com.google.common.base.Optional;
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

    String normalizedNumber = row.number().getNormalizedNumber();
    boolean canPlaceCalls =
        PhoneNumberHelper.canPlaceCallsTo(normalizedNumber, row.numberPresentation());

    if (canPlaceCalls) {
      addModuleForCalls(context, modules, row, normalizedNumber);
      SharedModules.maybeAddModuleForSendingTextMessage(
          context, modules, normalizedNumber, row.numberAttributes().getIsBlocked());
    }

    if (!modules.isEmpty()) {
      modules.add(new DividerModule());
    }


    // TODO(zachh): Module for CallComposer.

    if (canPlaceCalls) {
      SharedModules.maybeAddModuleForAddingToContacts(
          context,
          modules,
          row.number(),
          row.numberAttributes().getName(),
          row.numberAttributes().getLookupUri(),
          row.numberAttributes().getIsBlocked(),
          row.numberAttributes().getIsSpam());
      SharedModules.addModulesHandlingBlockedOrSpamNumber(
          context,
          modules,
          normalizedNumber,
          row.numberAttributes().getIsBlocked(),
          row.numberAttributes().getIsSpam());
      SharedModules.maybeAddModuleForCopyingNumber(context, modules, normalizedNumber);
    }

    // TODO(zachh): Revisit if DialerContact is the best thing to pass to CallDetails; could
    // it use a ContactPrimaryActionInfo instead?
    addModuleForAccessingCallDetails(context, modules, row);

    modules.add(new DeleteCallLogItemModule(context, row.coalescedIds()));

    return modules;
  }

  private static void addModuleForCalls(
      Context context,
      List<ContactActionModule> modules,
      CoalescedRow row,
      String normalizedNumber) {
    // Don't add call options if a number is blocked.
    if (row.numberAttributes().getIsBlocked()) {
      return;
    }

    PhoneAccountHandle phoneAccountHandle =
        TelecomUtil.composePhoneAccountHandle(
            row.phoneAccountComponentName(), row.phoneAccountId());

    // Add an audio call item
    modules.add(
        IntentModule.newCallModule(
            context, normalizedNumber, phoneAccountHandle, CallInitiationType.Type.CALL_LOG));

    // Add a video item if (1) the call log entry is for a video call, and (2) the call is not spam.
    if ((row.features() & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO
        && !row.numberAttributes().getIsSpam()) {
      modules.add(
          IntentModule.newVideoCallModule(
              context, normalizedNumber, phoneAccountHandle, CallInitiationType.Type.CALL_LOG));
    }

    // TODO(zachh): Also show video option if the call log entry is for an audio call but video
    // capabilities are present?
  }

  private static void addModuleForAccessingCallDetails(
      Context context, List<ContactActionModule> modules, CoalescedRow row) {
    boolean canReportAsInvalidNumber = row.numberAttributes().getCanReportAsInvalidNumber();
    boolean canSupportAssistedDialing = !TextUtils.isEmpty(row.numberAttributes().getLookupUri());

    modules.add(
        new IntentModule(
            context,
            CallDetailsActivity.newInstance(
                context,
                row.coalescedIds(),
                createDialerContactFromRow(context, row),
                canReportAsInvalidNumber,
                canSupportAssistedDialing),
            R.string.call_details_menu_label,
            R.drawable.quantum_ic_info_outline_vd_theme_24));
  }

  private static DialerContact createDialerContactFromRow(Context context, CoalescedRow row) {
    Optional<String> presentationName =
        PhoneNumberDisplayUtil.getNameForPresentation(context, row.numberPresentation());
    if (presentationName.isPresent()) {
      return DialerContact.newBuilder()
          .setNameOrNumber(presentationName.get())
          .setContactType(CallLogContactTypes.getContactType(row))
          .build();
    }

    String normalizedNumber = row.number().getNormalizedNumber();
    DialerContact.Builder dialerContactBuilder =
        DialerContact.newBuilder()
            .setNumber(normalizedNumber)
            .setContactType(CallLogContactTypes.getContactType(row))
            .setPhotoId(row.numberAttributes().getPhotoId());

    if (!row.numberAttributes().getName().isEmpty()) {
      dialerContactBuilder.setNameOrNumber(row.numberAttributes().getName());
      if (row.formattedNumber() != null) {
        dialerContactBuilder.setDisplayNumber(row.formattedNumber());
      }
    } else if (!TextUtils.isEmpty(row.formattedNumber())) {
      dialerContactBuilder.setNameOrNumber(row.formattedNumber());
    }

    dialerContactBuilder.setNumberLabel(row.numberAttributes().getNumberTypeLabel());
    dialerContactBuilder.setPhotoUri(row.numberAttributes().getPhotoUri());
    dialerContactBuilder.setContactUri(row.numberAttributes().getLookupUri());

    return dialerContactBuilder.build();
  }
}
