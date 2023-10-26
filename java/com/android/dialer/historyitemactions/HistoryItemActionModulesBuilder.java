/*
 * Copyright (C) 2018 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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
import android.text.TextUtils;

import com.android.dialer.R;
import com.android.dialer.blockreportspam.BlockReportSpamDialogInfo;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.clipboard.ClipboardUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.spam.Spam;
import com.android.dialer.util.CallUtil;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.util.UriUtils;

import java.util.ArrayList;
import java.util.List;

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
      modules.add(IntentModule.newCallModule(context, callIntentBuilder));
      return this;
    }

    // At this point, the module info is for an audio call. We will also add a video call module if
    // the video capability is present.
    //
    // The carrier video call module takes precedence over the Duo module.
    if (canPlaceCarrierVideoCall()) {
      modules.add(IntentModule.newCallModule(
              context, callIntentBuilder));
    }
    return this;
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

    modules.add(IntentModule.newModuleForSendingTextMessage(context,
            moduleInfo.getNormalizedNumber()));
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
            R.drawable.quantum_ic_person_add_vd_theme_24));
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
            .setContactSource(moduleInfo.getContactSource())
            .build();

    // For a call that should be shown as spam, add two modules:
    // (1) "Not spam" and "Block", or
    // (2) "Not spam" and "Unblock".
    if (Spam.shouldShowAsSpam(moduleInfo.getIsSpam(), moduleInfo.getCallType())) {
      modules.add(
          BlockReportSpamModules.moduleForMarkingNumberAsNotSpam(
              context, blockReportSpamDialogInfo));
      modules.add(
          moduleInfo.getIsBlocked()
              ? BlockReportSpamModules.moduleForUnblockingNumber(
                  context, blockReportSpamDialogInfo)
              : BlockReportSpamModules.moduleForBlockingNumber(
                  context, blockReportSpamDialogInfo));
      return this;
    }

    // For a blocked number associated with a call that should not be shown as spam, add the
    // "Unblock" module.
    if (moduleInfo.getIsBlocked()) {
      modules.add(
          BlockReportSpamModules.moduleForUnblockingNumber(
              context, blockReportSpamDialogInfo));
      return this;
    }

    // For a number that is not blocked and is associated with a call that should not be shown as
    // spam, add the "Block/Report spam" module.
    modules.add(
        BlockReportSpamModules.moduleForBlockingNumberAndOptionallyReportingSpam(
            context, blockReportSpamDialogInfo));
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
}
