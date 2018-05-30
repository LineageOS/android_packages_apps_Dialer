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
import android.text.TextUtils;
import com.android.dialer.calldetails.CallDetailsActivity;
import com.android.dialer.calldetails.CallDetailsHeaderInfo;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.calllogutils.CallLogEntryText;
import com.android.dialer.calllogutils.PhotoInfoBuilder;
import com.android.dialer.historyitemactions.HistoryItemActionModule;
import com.android.dialer.historyitemactions.HistoryItemActionModuleInfo;
import com.android.dialer.historyitemactions.HistoryItemActionModulesBuilder;
import com.android.dialer.historyitemactions.IntentModule;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import java.util.List;

/**
 * Configures the modules for the bottom sheet; these are the rows below the top row (contact info)
 * in the bottom sheet.
 */
final class Modules {

  /**
   * Returns a list of {@link HistoryItemActionModule HistoryItemActionModules}, which are items in
   * the bottom sheet.
   */
  static List<HistoryItemActionModule> fromRow(Context context, CoalescedRow row) {
    HistoryItemActionModulesBuilder modulesBuilder =
        new HistoryItemActionModulesBuilder(context, buildModuleInfo(row));


    // TODO(zachh): Module for CallComposer.

    if (PhoneNumberHelper.canPlaceCallsTo(
        row.getNumber().getNormalizedNumber(), row.getNumberPresentation())) {
      modulesBuilder
          .addModuleForVoiceCall()
          .addModuleForVideoCall()
          .addModuleForSendingTextMessage()
          .addModuleForDivider()
          .addModuleForAddingToContacts()
          .addModuleForBlockedOrSpamNumber()
          .addModuleForCopyingNumber();
    }

    List<HistoryItemActionModule> modules = modulesBuilder.build();

    // Add modules only available in the call log.
    modules.add(createModuleForAccessingCallDetails(context, row));
    modules.add(new DeleteCallLogItemModule(context, row.getCoalescedIds()));
    return modules;
  }

  private static HistoryItemActionModule createModuleForAccessingCallDetails(
      Context context, CoalescedRow row) {
    boolean canReportAsInvalidNumber =
        !row.getIsVoicemailCall() && row.getNumberAttributes().getCanReportAsInvalidNumber();

    return new IntentModule(
        context,
        CallDetailsActivity.newInstance(
            context,
            row.getCoalescedIds(),
            createCallDetailsHeaderInfoFromRow(context, row),
            canReportAsInvalidNumber,
            canSupportAssistedDialing(row)),
        R.string.call_details_menu_label,
        R.drawable.quantum_ic_info_outline_vd_theme_24);
  }

  private static CallDetailsHeaderInfo createCallDetailsHeaderInfoFromRow(
      Context context, CoalescedRow row) {
    return CallDetailsHeaderInfo.newBuilder()
        .setDialerPhoneNumber(row.getNumber())
        .setPhotoInfo(PhotoInfoBuilder.fromCoalescedRow(context, row))
        .setPrimaryText(CallLogEntryText.buildPrimaryText(context, row).toString())
        .setSecondaryText(
            CallLogEntryText.buildSecondaryTextForBottomSheet(context, row).toString())
        .build();
  }

  private static boolean canSupportAssistedDialing(CoalescedRow row) {
    return !TextUtils.isEmpty(row.getNumberAttributes().getLookupUri());
  }

  private static HistoryItemActionModuleInfo buildModuleInfo(CoalescedRow row) {
    return HistoryItemActionModuleInfo.newBuilder()
        .setNormalizedNumber(row.getNumber().getNormalizedNumber())
        .setCountryIso(row.getNumber().getCountryIso())
        .setName(row.getNumberAttributes().getName())
        .setCallType(row.getCallType())
        .setFeatures(row.getFeatures())
        .setLookupUri(row.getNumberAttributes().getLookupUri())
        .setPhoneAccountComponentName(row.getPhoneAccountComponentName())
        .setCanReportAsInvalidNumber(row.getNumberAttributes().getCanReportAsInvalidNumber())
        .setCanSupportAssistedDialing(canSupportAssistedDialing(row))
        .setCanSupportCarrierVideoCall(row.getNumberAttributes().getCanSupportCarrierVideoCall())
        .setIsBlocked(row.getNumberAttributes().getIsBlocked())
        .setIsEmergencyNumber(row.getNumberAttributes().getIsEmergencyNumber())
        .setIsSpam(row.getNumberAttributes().getIsSpam())
        .setIsVoicemailCall(row.getIsVoicemailCall())
        .setContactSource(row.getNumberAttributes().getContactSource())
        .setHost(HistoryItemActionModuleInfo.Host.CALL_LOG)
        .build();
  }
}
