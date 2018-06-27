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

package com.android.dialer.historyitemactions;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.support.annotation.IntDef;
import android.text.TextUtils;
import com.android.dialer.blockreportspam.BlockReportSpamDialogInfo;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.clipboard.ClipboardUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.duo.Duo;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.ReportingLocation;
import com.android.dialer.spam.Spam;
import com.android.dialer.util.CallUtil;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.util.UriUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds a list of {@link HistoryItemActionModule HistoryItemActionModules}.
 *
 * <p>Example usage:
 *
 * <pre><code>
 *    // Create a HistoryItemActionModuleInfo proto with the information you have.
 *    // You can simply skip a field if there is no information for it.
 *    HistoryItemActionModuleInfo moduleInfo =
 *        HistoryItemActionModuleInfo.newBuilder()
 *            .setNormalizedNumber("+16502530000")
 *            .setCountryIso("US")
 *            .setName("Google")
 *            .build();
 *
 *    // Initialize the builder using the module info above.
 *    // Note that some modules require an activity context to work so it is preferred to pass one
 *    // instead of an application context to the builder.
 *    HistoryItemActionModulesBuilder modulesBuilder =
 *        new HistoryItemActionModulesBuilder(activityContext, moduleInfo);
 *
 *    // Add all modules you want in the order you like.
 *    // If a module shouldn't be added according to the module info, it won't be.
 *    // For example, if the module info is not for a video call and doesn't indicate the presence
 *    // of video calling capabilities, calling addModuleForVideoCall() is a no-op.
 *    modulesBuilder
 *        .addModuleForVoiceCall()
 *        .addModuleForVideoCall()
 *        .addModuleForSendingTextMessage()
 *        .addModuleForDivider()
 *        .addModuleForAddingToContacts()
 *        .addModuleForBlockedOrSpamNumber()
 *        .addModuleForCopyingNumber();
 *
 *    List<HistoryItemActionModule> modules = modulesBuilder.build();
 * </code></pre>
 */
public final class HistoryItemActionModulesBuilder {

  /** Represents events when a module is tapped by the user. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    Event.ADD_TO_CONTACT,
    Event.BLOCK_NUMBER,
    Event.BLOCK_NUMBER_AND_REPORT_SPAM,
    Event.REPORT_NOT_SPAM,
    Event.REQUEST_CARRIER_VIDEO_CALL,
    Event.REQUEST_DUO_VIDEO_CALL,
    Event.REQUEST_DUO_VIDEO_CALL_FOR_NON_CONTACT,
    Event.SEND_TEXT_MESSAGE,
    Event.UNBLOCK_NUMBER
  })
  @interface Event {
    int ADD_TO_CONTACT = 1;
    int BLOCK_NUMBER = 2;
    int BLOCK_NUMBER_AND_REPORT_SPAM = 3;
    int REPORT_NOT_SPAM = 4;
    int REQUEST_CARRIER_VIDEO_CALL = 5;
    int REQUEST_DUO_VIDEO_CALL = 6;
    int REQUEST_DUO_VIDEO_CALL_FOR_NON_CONTACT = 7;
    int SEND_TEXT_MESSAGE = 8;
    int UNBLOCK_NUMBER = 9;
  }

  /**
   * Maps each {@link Event} to a {@link DialerImpression.Type} to be logged when the modules are
   * hosted by the call log.
   */
  private static final ImmutableMap<Integer, DialerImpression.Type> CALL_LOG_IMPRESSIONS =
      new ImmutableMap.Builder<Integer, DialerImpression.Type>()
          .put(Event.ADD_TO_CONTACT, DialerImpression.Type.ADD_TO_A_CONTACT_FROM_CALL_LOG)
          .put(Event.BLOCK_NUMBER, DialerImpression.Type.CALL_LOG_BLOCK_NUMBER)
          .put(Event.BLOCK_NUMBER_AND_REPORT_SPAM, DialerImpression.Type.CALL_LOG_BLOCK_REPORT_SPAM)
          .put(Event.REPORT_NOT_SPAM, DialerImpression.Type.CALL_LOG_REPORT_AS_NOT_SPAM)
          .put(
              Event.REQUEST_CARRIER_VIDEO_CALL,
              DialerImpression.Type.IMS_VIDEO_REQUESTED_FROM_CALL_LOG)
          .put(
              Event.REQUEST_DUO_VIDEO_CALL,
              DialerImpression.Type.LIGHTBRINGER_VIDEO_REQUESTED_FROM_CALL_LOG)
          .put(
              Event.REQUEST_DUO_VIDEO_CALL_FOR_NON_CONTACT,
              DialerImpression.Type.LIGHTBRINGER_NON_CONTACT_VIDEO_REQUESTED_FROM_CALL_LOG)
          .put(Event.SEND_TEXT_MESSAGE, DialerImpression.Type.CALL_LOG_SEND_MESSAGE)
          .put(Event.UNBLOCK_NUMBER, DialerImpression.Type.CALL_LOG_UNBLOCK_NUMBER)
          .build();

  private final Context context;
  private final HistoryItemActionModuleInfo moduleInfo;
  private final List<HistoryItemActionModule> modules;

  public HistoryItemActionModulesBuilder(Context context, HistoryItemActionModuleInfo moduleInfo) {
    Assert.checkArgument(
        moduleInfo.getHost() != HistoryItemActionModuleInfo.Host.UNKNOWN,
        "A host must be specified.");

    this.context = context;
    this.moduleInfo = moduleInfo;
    this.modules = new ArrayList<>();
  }

  public List<HistoryItemActionModule> build() {
    return new ArrayList<>(modules);
  }

  /**
   * Adds a module for placing a voice call.
   *
   * <p>The method is a no-op if the number is blocked.
   */
  public HistoryItemActionModulesBuilder addModuleForVoiceCall() {
    if (moduleInfo.getIsBlocked()) {
      return this;
    }

    // TODO(zachh): Support post-dial digits; consider using DialerPhoneNumber.
    // Do not set PhoneAccountHandle so that regular PreCall logic will be used. The account used to
    // place or receive the call should be ignored for voice calls.
    CallIntentBuilder callIntentBuilder =
        new CallIntentBuilder(moduleInfo.getNormalizedNumber(), getCallInitiationType())
            .setAllowAssistedDial(moduleInfo.getCanSupportAssistedDialing());
    modules.add(IntentModule.newCallModule(context, callIntentBuilder));
    return this;
  }

  /**
   * Adds a module for a carrier video call *or* a Duo video call.
   *
   * <p>This method is a no-op if
   *
   * <ul>
   *   <li>the call is one made to/received from an emergency number,
   *   <li>the call is one made to a voicemail box,
   *   <li>the call should be shown as spam, or
   *   <li>the number is blocked.
   * </ul>
   *
   * <p>If the provided module info is for a Duo video call and Duo is available, add a Duo video
   * call module.
   *
   * <p>If the provided module info is for a Duo video call but Duo is unavailable, add a carrier
   * video call module.
   *
   * <p>If the provided module info is for a carrier video call, add a carrier video call module.
   *
   * <p>If the provided module info is for a voice call and the device has carrier video call
   * capability, add a carrier video call module.
   *
   * <p>If the provided module info is for a voice call, the device doesn't have carrier video call
   * capability, and Duo is available, add a Duo video call module.
   */
  public HistoryItemActionModulesBuilder addModuleForVideoCall() {
    if (moduleInfo.getIsEmergencyNumber()
        || moduleInfo.getIsVoicemailCall()
        || Spam.shouldShowAsSpam(moduleInfo.getIsSpam(), moduleInfo.getCallType())
        || moduleInfo.getIsBlocked()) {
      return this;
    }

    // Do not set PhoneAccountHandle so that regular PreCall logic will be used. The account used to
    // place or receive the call should be ignored for carrier video calls.
    // TODO(a bug): figure out the correct video call behavior
    CallIntentBuilder callIntentBuilder =
        new CallIntentBuilder(moduleInfo.getNormalizedNumber(), getCallInitiationType())
            .setAllowAssistedDial(moduleInfo.getCanSupportAssistedDialing())
            .setIsVideoCall(true);

    // If the module info is for a video call, add an appropriate video call module.
    if ((moduleInfo.getFeatures() & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO) {
      boolean isDuoCall = isDuoCall();
      modules.add(
          IntentModule.newCallModule(
              context,
              callIntentBuilder.setIsDuoCall(isDuoCall),
              isDuoCall
                  ? getImpressionsForDuoVideoCall()
                  : getImpressions(Event.REQUEST_CARRIER_VIDEO_CALL)));
      return this;
    }

    // At this point, the module info is for an audio call. We will also add a video call module if
    // the video capability is present.
    //
    // The carrier video call module takes precedence over the Duo module.
    if (canPlaceCarrierVideoCall()) {
      modules.add(
          IntentModule.newCallModule(
              context, callIntentBuilder, getImpressions(Event.REQUEST_CARRIER_VIDEO_CALL)));
    } else if (canPlaceDuoCall()) {
      modules.add(
          IntentModule.newCallModule(
              context, callIntentBuilder.setIsDuoCall(true), getImpressionsForDuoVideoCall()));
    }
    return this;
  }

  /**
   * Returns a list of impressions to be logged when the user taps the module that attempts to
   * initiate a Duo video call.
   */
  private ImmutableList<DialerImpression.Type> getImpressionsForDuoVideoCall() {
    return isExistingContact()
        ? getImpressions(Event.REQUEST_DUO_VIDEO_CALL)
        : getImpressions(
            Event.REQUEST_DUO_VIDEO_CALL, Event.REQUEST_DUO_VIDEO_CALL_FOR_NON_CONTACT);
  }

  /**
   * Adds a module for sending text messages.
   *
   * <p>The method is a no-op if
   *
   * <ul>
   *   <li>the permission to send SMS is not granted,
   *   <li>the call is one made to/received from an emergency number,
   *   <li>the call is one made to a voicemail box,
   *   <li>the number is blocked, or
   *   <li>the number is empty.
   * </ul>
   */
  public HistoryItemActionModulesBuilder addModuleForSendingTextMessage() {
    // TODO(zachh): There are other conditions where this module should not be shown
    // (e.g., business numbers).
    if (!PermissionsUtil.hasSendSmsPermissions(context)
        || moduleInfo.getIsEmergencyNumber()
        || moduleInfo.getIsVoicemailCall()
        || moduleInfo.getIsBlocked()
        || TextUtils.isEmpty(moduleInfo.getNormalizedNumber())) {
      return this;
    }

    modules.add(
        IntentModule.newModuleForSendingTextMessage(
            context, moduleInfo.getNormalizedNumber(), getImpressions(Event.SEND_TEXT_MESSAGE)));
    return this;
  }

  /**
   * Adds a module for a divider.
   *
   * <p>The method is a no-op if the divider module will be the first module.
   */
  public HistoryItemActionModulesBuilder addModuleForDivider() {
    if (modules.isEmpty()) {
      return this;
    }

    modules.add(new DividerModule());
    return this;
  }

  /**
   * Adds a module for adding a number to Contacts.
   *
   * <p>The method is a no-op if
   *
   * <ul>
   *   <li>the permission to write contacts is not granted,
   *   <li>the call is one made to/received from an emergency number,
   *   <li>the call is one made to a voicemail box,
   *   <li>the call should be shown as spam,
   *   <li>the number is blocked,
   *   <li>the number is empty, or
   *   <li>the number belongs to an existing contact.
   * </ul>
   */
  public HistoryItemActionModulesBuilder addModuleForAddingToContacts() {
    if (!PermissionsUtil.hasContactsWritePermissions(context)
        || moduleInfo.getIsEmergencyNumber()
        || moduleInfo.getIsVoicemailCall()
        || Spam.shouldShowAsSpam(moduleInfo.getIsSpam(), moduleInfo.getCallType())
        || moduleInfo.getIsBlocked()
        || isExistingContact()
        || TextUtils.isEmpty(moduleInfo.getNormalizedNumber())) {
      return this;
    }

    Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
    intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
    intent.putExtra(ContactsContract.Intents.Insert.PHONE, moduleInfo.getNormalizedNumber());

    if (!TextUtils.isEmpty(moduleInfo.getName())) {
      intent.putExtra(ContactsContract.Intents.Insert.NAME, moduleInfo.getName());
    }

    modules.add(
        new IntentModule(
            context,
            intent,
            R.string.add_to_contacts,
            R.drawable.quantum_ic_person_add_vd_theme_24,
            getImpressions(Event.ADD_TO_CONTACT)));
    return this;
  }

  /**
   * Add modules for blocking/unblocking a number and/or marking it as spam/not spam.
   *
   * <p>The method is a no-op if
   *
   * <ul>
   *   <li>the call is one made to/received from an emergency number, or
   *   <li>the call is one made to a voicemail box.
   * </ul>
   *
   * <p>If the call should be shown as spam, add two modules:
   *
   * <ul>
   *   <li>"Not spam" and "Block", or
   *   <li>"Not spam" and "Unblock".
   * </ul>
   *
   * <p>If the number is blocked but the call should not be shown as spam, add the "Unblock" module.
   *
   * <p>If the number is not blocked and the call should not be shown as spam, add the "Block/Report
   * spam" module.
   */
  public HistoryItemActionModulesBuilder addModuleForBlockedOrSpamNumber() {
    if (moduleInfo.getIsEmergencyNumber() || moduleInfo.getIsVoicemailCall()) {
      return this;
    }

    BlockReportSpamDialogInfo blockReportSpamDialogInfo =
        BlockReportSpamDialogInfo.newBuilder()
            .setNormalizedNumber(moduleInfo.getNormalizedNumber())
            .setCountryIso(moduleInfo.getCountryIso())
            .setCallType(moduleInfo.getCallType())
            .setReportingLocation(getReportingLocation())
            .setContactSource(moduleInfo.getContactSource())
            .build();

    // For a call that should be shown as spam, add two modules:
    // (1) "Not spam" and "Block", or
    // (2) "Not spam" and "Unblock".
    if (Spam.shouldShowAsSpam(moduleInfo.getIsSpam(), moduleInfo.getCallType())) {
      modules.add(
          BlockReportSpamModules.moduleForMarkingNumberAsNotSpam(
              context, blockReportSpamDialogInfo, getImpression(Event.REPORT_NOT_SPAM)));
      modules.add(
          moduleInfo.getIsBlocked()
              ? BlockReportSpamModules.moduleForUnblockingNumber(
                  context, blockReportSpamDialogInfo, getImpression(Event.UNBLOCK_NUMBER))
              : BlockReportSpamModules.moduleForBlockingNumber(
                  context, blockReportSpamDialogInfo, getImpression(Event.BLOCK_NUMBER)));
      return this;
    }

    // For a blocked number associated with a call that should not be shown as spam, add the
    // "Unblock" module.
    if (moduleInfo.getIsBlocked()) {
      modules.add(
          BlockReportSpamModules.moduleForUnblockingNumber(
              context, blockReportSpamDialogInfo, getImpression(Event.UNBLOCK_NUMBER)));
      return this;
    }

    // For a number that is not blocked and is associated with a call that should not be shown as
    // spam, add the "Block/Report spam" module.
    modules.add(
        BlockReportSpamModules.moduleForBlockingNumberAndOptionallyReportingSpam(
            context, blockReportSpamDialogInfo, getImpression(Event.BLOCK_NUMBER_AND_REPORT_SPAM)));
    return this;
  }

  /**
   * Adds a module for copying a number.
   *
   * <p>The method is a no-op if the number is empty.
   */
  public HistoryItemActionModulesBuilder addModuleForCopyingNumber() {
    if (TextUtils.isEmpty(moduleInfo.getNormalizedNumber())) {
      return this;
    }

    modules.add(
        new HistoryItemActionModule() {
          @Override
          public int getStringId() {
            return R.string.copy_number;
          }

          @Override
          public int getDrawableId() {
            return R.drawable.quantum_ic_content_copy_vd_theme_24;
          }

          @Override
          public boolean onClick() {
            ClipboardUtils.copyText(
                context,
                /* label = */ null,
                moduleInfo.getNormalizedNumber(),
                /* showToast = */ true);
            return false;
          }
        });
    return this;
  }

  private boolean canPlaceCarrierVideoCall() {
    int carrierVideoAvailability = CallUtil.getVideoCallingAvailability(context);
    boolean isCarrierVideoCallingEnabled =
        ((carrierVideoAvailability & CallUtil.VIDEO_CALLING_ENABLED)
            == CallUtil.VIDEO_CALLING_ENABLED);
    boolean canRelyOnCarrierVideoPresence =
        ((carrierVideoAvailability & CallUtil.VIDEO_CALLING_PRESENCE)
            == CallUtil.VIDEO_CALLING_PRESENCE);

    return isCarrierVideoCallingEnabled
        && canRelyOnCarrierVideoPresence
        && moduleInfo.getCanSupportCarrierVideoCall();
  }

  private boolean isDuoCall() {
    return DuoComponent.get(context)
        .getDuo()
        .isDuoAccount(moduleInfo.getPhoneAccountComponentName());
  }

  private boolean canPlaceDuoCall() {
    Duo duo = DuoComponent.get(context).getDuo();

    return duo.isInstalled(context)
        && duo.isEnabled(context)
        && duo.isActivated(context)
        && duo.isReachable(context, moduleInfo.getNormalizedNumber());
  }

  /**
   * Lookup URIs are currently fetched from the cached column of the system call log. This URI
   * contains encoded information for non-contacts for the purposes of populating contact cards.
   *
   * <p>We infer whether a contact is existing or not by checking if the lookup URI is "encoded" or
   * not.
   *
   * <p>TODO(zachh): We should revisit this once the contact URI is no longer being read from the
   * cached column in the system database, in case we decide not to overload the column.
   */
  private boolean isExistingContact() {
    return !TextUtils.isEmpty(moduleInfo.getLookupUri())
        && !UriUtils.isEncodedContactUri(Uri.parse(moduleInfo.getLookupUri()));
  }

  /**
   * Maps the value of {@link HistoryItemActionModuleInfo#getHost()} to {@link
   * CallInitiationType.Type}, which is required by {@link CallIntentBuilder} to build a call
   * intent.
   */
  private CallInitiationType.Type getCallInitiationType() {
    switch (moduleInfo.getHost()) {
      case CALL_LOG:
        return CallInitiationType.Type.CALL_LOG;
      case VOICEMAIL:
        return CallInitiationType.Type.VOICEMAIL_LOG;
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported host: %s", moduleInfo.getHost()));
    }
  }

  /**
   * Maps the value of {@link HistoryItemActionModuleInfo#getHost()} to {@link
   * ReportingLocation.Type}, which is for logging where a spam number is reported.
   */
  private ReportingLocation.Type getReportingLocation() {
    switch (moduleInfo.getHost()) {
      case CALL_LOG:
        return ReportingLocation.Type.CALL_LOG_HISTORY;
      case VOICEMAIL:
        return ReportingLocation.Type.VOICEMAIL_HISTORY;
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported host: %s", moduleInfo.getHost()));
    }
  }

  /** Returns a list of impressions to be logged for the given {@link Event events}. */
  private ImmutableList<DialerImpression.Type> getImpressions(@Event int... events) {
    Assert.isNotNull(events);

    ImmutableList.Builder<DialerImpression.Type> impressionListBuilder =
        new ImmutableList.Builder<>();
    for (@Event int event : events) {
      getImpression(event).ifPresent(impressionListBuilder::add);
    }

    return impressionListBuilder.build();
  }

  /**
   * Returns an impression to be logged for the given {@link Event}, or {@link Optional#empty()} if
   * no impression is available for the event.
   */
  private Optional<DialerImpression.Type> getImpression(@Event int event) {
    switch (moduleInfo.getHost()) {
      case CALL_LOG:
        return Optional.of(CALL_LOG_IMPRESSIONS.get(event));
      case VOICEMAIL:
        // TODO(a bug): Return proper impressions for voicemail.
        return Optional.empty();
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported host: %s", moduleInfo.getHost()));
    }
  }
}
