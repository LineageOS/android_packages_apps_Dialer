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
import android.support.v4.os.BuildCompat;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import com.android.dialer.blockreportspam.BlockReportSpamDialogInfo;
import com.android.dialer.calldetails.CallDetailsActivity;
import com.android.dialer.calldetails.CallDetailsHeaderInfo;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.calllogutils.CallLogEntryText;
import com.android.dialer.calllogutils.NumberAttributesConverter;
import com.android.dialer.duo.Duo;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.duo.DuoConstants;
import com.android.dialer.glidephotomanager.PhotoInfo;
import com.android.dialer.historyitemactions.DividerModule;
import com.android.dialer.historyitemactions.DuoCallModule;
import com.android.dialer.historyitemactions.HistoryItemActionModule;
import com.android.dialer.historyitemactions.IntentModule;
import com.android.dialer.historyitemactions.SharedModules;
import com.android.dialer.logging.ReportingLocation;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.CallUtil;
import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configures the modules for the bottom sheet; these are the rows below the top row (primary
 * action) in the bottom sheet.
 */
@SuppressWarnings("Guava")
final class Modules {

  static List<HistoryItemActionModule> fromRow(Context context, CoalescedRow row) {
    // Conditionally add each module, which are items in the bottom sheet's menu.
    List<HistoryItemActionModule> modules = new ArrayList<>();

    String normalizedNumber = row.getNumber().getNormalizedNumber();
    boolean canPlaceCalls =
        PhoneNumberHelper.canPlaceCallsTo(normalizedNumber, row.getNumberPresentation());

    if (canPlaceCalls) {
      modules.addAll(createModulesForCalls(context, row, normalizedNumber));
      Optional<HistoryItemActionModule> moduleForSendingTextMessage =
          SharedModules.createModuleForSendingTextMessage(
              context, normalizedNumber, row.getNumberAttributes().getIsBlocked());
      if (moduleForSendingTextMessage.isPresent()) {
        modules.add(moduleForSendingTextMessage.get());
      }
    }

    if (!modules.isEmpty()) {
      modules.add(new DividerModule());
    }


    // TODO(zachh): Module for CallComposer.

    if (canPlaceCalls) {
      Optional<HistoryItemActionModule> moduleForAddingToContacts =
          SharedModules.createModuleForAddingToContacts(
              context,
              row.getNumber(),
              row.getNumberAttributes().getName(),
              row.getNumberAttributes().getLookupUri(),
              row.getNumberAttributes().getIsBlocked(),
              row.getNumberAttributes().getIsSpam());
      if (moduleForAddingToContacts.isPresent()) {
        modules.add(moduleForAddingToContacts.get());
      }

      BlockReportSpamDialogInfo blockReportSpamDialogInfo =
          BlockReportSpamDialogInfo.newBuilder()
              .setNormalizedNumber(row.getNumber().getNormalizedNumber())
              .setCountryIso(row.getNumber().getCountryIso())
              .setCallType(row.getCallType())
              .setReportingLocation(ReportingLocation.Type.CALL_LOG_HISTORY)
              .setContactSource(row.getNumberAttributes().getContactSource())
              .build();
      modules.addAll(
          SharedModules.createModulesHandlingBlockedOrSpamNumber(
              context,
              blockReportSpamDialogInfo,
              row.getNumberAttributes().getIsBlocked(),
              row.getNumberAttributes().getIsSpam()));

      Optional<HistoryItemActionModule> moduleForCopyingNumber =
          SharedModules.createModuleForCopyingNumber(context, normalizedNumber);
      if (moduleForCopyingNumber.isPresent()) {
        modules.add(moduleForCopyingNumber.get());
      }
    }

    modules.add(createModuleForAccessingCallDetails(context, row));

    modules.add(new DeleteCallLogItemModule(context, row.getCoalescedIds()));

    return modules;
  }

  private static List<HistoryItemActionModule> createModulesForCalls(
      Context context, CoalescedRow row, String normalizedNumber) {
    // Don't add call options if a number is blocked.
    if (row.getNumberAttributes().getIsBlocked()) {
      return Collections.emptyList();
    }

    boolean isDuoCall =
        DuoConstants.PHONE_ACCOUNT_COMPONENT_NAME
            .flattenToString()
            .equals(row.getPhoneAccountComponentName());

    // Obtain a PhoneAccountHandle that will be used to start carrier voice/video calls.
    // If the row is for a Duo call, we should use the default phone account as the one included in
    // the row is for Duo only.
    PhoneAccountHandle phoneAccountHandle =
        isDuoCall
            ? TelecomUtil.getDefaultOutgoingPhoneAccount(context, PhoneAccount.SCHEME_TEL)
            : TelecomUtil.composePhoneAccountHandle(
                row.getPhoneAccountComponentName(), row.getPhoneAccountId());

    List<HistoryItemActionModule> modules = new ArrayList<>();

    // Add an audio call item
    // TODO(zachh): Support post-dial digits; consider using DialerPhoneNumber.
    CallIntentBuilder callIntentBuilder =
        new CallIntentBuilder(normalizedNumber, CallInitiationType.Type.CALL_LOG)
            .setPhoneAccountHandle(phoneAccountHandle);
    modules.add(IntentModule.newCallModule(context, callIntentBuilder));

    // If the call log entry is for a spam call, nothing more to be done.
    if (row.getNumberAttributes().getIsSpam()) {
      return modules;
    }

    // If the call log entry is for a video call, add the corresponding video call options.
    // Note that if the entry is for a Duo video call but Duo is not available, we will fall back to
    // a carrier video call.
    if ((row.getFeatures() & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO) {
      modules.add(
          isDuoCall && canPlaceDuoCall(context, normalizedNumber)
              ? new DuoCallModule(context, normalizedNumber)
              : IntentModule.newCallModule(context, callIntentBuilder.setIsVideoCall(true)));
      return modules;
    }

    // At this point, the call log entry is for an audio call. We will also show a video call option
    // if the video capability is present.
    //
    // The carrier video call option takes precedence over Duo.
    if (canPlaceCarrierVideoCall(context, row)) {
      modules.add(IntentModule.newCallModule(context, callIntentBuilder.setIsVideoCall(true)));
    } else if (canPlaceDuoCall(context, normalizedNumber)) {
      modules.add(new DuoCallModule(context, normalizedNumber));
    }

    return modules;
  }

  private static HistoryItemActionModule createModuleForAccessingCallDetails(
      Context context, CoalescedRow row) {
    boolean canReportAsInvalidNumber = row.getNumberAttributes().getCanReportAsInvalidNumber();
    boolean canSupportAssistedDialing =
        !TextUtils.isEmpty(row.getNumberAttributes().getLookupUri());

    return new IntentModule(
        context,
        CallDetailsActivity.newInstance(
            context,
            row.getCoalescedIds(),
            createCallDetailsHeaderInfoFromRow(context, row),
            canReportAsInvalidNumber,
            canSupportAssistedDialing),
        R.string.call_details_menu_label,
        R.drawable.quantum_ic_info_outline_vd_theme_24);
  }

  private static CallDetailsHeaderInfo createCallDetailsHeaderInfoFromRow(
      Context context, CoalescedRow row) {
    return CallDetailsHeaderInfo.newBuilder()
        .setDialerPhoneNumber(row.getNumber())
        .setPhotoInfo(createPhotoInfoFromRow(row))
        .setPrimaryText(CallLogEntryText.buildPrimaryText(context, row).toString())
        .setSecondaryText(
            CallLogEntryText.buildSecondaryTextForBottomSheet(context, row).toString())
        .build();
  }

  private static PhotoInfo createPhotoInfoFromRow(CoalescedRow row) {
    return NumberAttributesConverter.toPhotoInfoBuilder(row.getNumberAttributes())
        .setFormattedNumber(row.getFormattedNumber())
        .setIsVideo((row.getFeatures() & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO)
        .setIsRtt(
            BuildCompat.isAtLeastP()
                && (row.getFeatures() & Calls.FEATURES_RTT) == Calls.FEATURES_RTT)
        .setIsVoicemail(row.getIsVoicemailCall())
        .build();
  }

  private static boolean canPlaceDuoCall(Context context, String phoneNumber) {
    Duo duo = DuoComponent.get(context).getDuo();

    return duo.isInstalled(context)
        && duo.isEnabled(context)
        && duo.isActivated(context)
        && duo.isReachable(context, phoneNumber);
  }

  private static boolean canPlaceCarrierVideoCall(Context context, CoalescedRow row) {
    int carrierVideoAvailability = CallUtil.getVideoCallingAvailability(context);
    boolean isCarrierVideoCallingEnabled =
        ((carrierVideoAvailability & CallUtil.VIDEO_CALLING_ENABLED)
            == CallUtil.VIDEO_CALLING_ENABLED);
    boolean canRelyOnCarrierVideoPresence =
        ((carrierVideoAvailability & CallUtil.VIDEO_CALLING_PRESENCE)
            == CallUtil.VIDEO_CALLING_PRESENCE);

    return isCarrierVideoCallingEnabled
        && canRelyOnCarrierVideoPresence
        && row.getNumberAttributes().getCanSupportCarrierVideoCall();
  }
}
