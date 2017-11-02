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
import android.content.Intent;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import com.android.dialer.calldetails.CallDetailsActivity;
import com.android.dialer.calldetails.CallDetailsEntries;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.calllogutils.PhoneAccountUtils;
import com.android.dialer.clipboard.ClipboardUtils;
import com.android.dialer.contactactions.ContactActionModule;
import com.android.dialer.contactactions.DividerModule;
import com.android.dialer.contactactions.IntentModule;
import com.android.dialer.dialercontact.DialerContact;
import com.android.dialer.lettertile.LetterTileDrawable;
import com.android.dialer.util.IntentUtil;
import com.android.dialer.util.UriUtils;
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

    maybeAddModuleForVideoOrAudioCall(context, row, modules);
    maybeAddModuleForAddingToContacts(context, row, modules);

    String originalNumber = row.number().getRawInput().getNumber();
    maybeAddModuleForSendingTextMessage(context, originalNumber, modules);

    if (!modules.isEmpty()) {
      modules.add(new DividerModule());
    }

    // TODO(zachh): Module for blocking/unblocking spam.
    // TODO(zachh): Module for CallComposer.
    maybeAddModuleForCopyingNumber(context, originalNumber, modules);

    // TODO(zachh): Revisit if DialerContact is the best thing to pass to CallDetails; could
    // it use a ContactPrimaryActionInfo instead?
    addModuleForAccessingCallDetails(context, createDialerContactFromRow(row), modules);

    return modules;
  }

  private static void maybeAddModuleForVideoOrAudioCall(
      Context context, CoalescedRow row, List<ContactActionModule> modules) {
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

  private static void maybeAddModuleForAddingToContacts(
      Context context, CoalescedRow row, List<ContactActionModule> modules) {
    // TODO(zachh): Only show this for non-spam/blocked numbers.

    // Skip showing the menu item for existing contacts.
    if (isExistingContact(row)) {
      return;
    }

    // Skip showing the menu item if there is no number.
    String originalNumber = row.number().getRawInput().getNumber();
    if (TextUtils.isEmpty(originalNumber)) {
      return;
    }

    Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
    intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
    intent.putExtra(ContactsContract.Intents.Insert.PHONE, originalNumber);

    if (!TextUtils.isEmpty(row.name())) {
      intent.putExtra(ContactsContract.Intents.Insert.NAME, row.name());
    }
    modules.add(
        new IntentModule(
            context,
            intent,
            R.string.add_to_contacts,
            R.drawable.quantum_ic_person_add_vd_theme_24));
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
  private static boolean isExistingContact(CoalescedRow row) {
    return !TextUtils.isEmpty(row.lookupUri())
        && !UriUtils.isEncodedContactUri(Uri.parse(row.lookupUri()));
  }

  private static void maybeAddModuleForSendingTextMessage(
      Context context, String originalNumber, List<ContactActionModule> modules) {
    // TODO(zachh): There are some conditions where this module should not be shown; consider
    // voicemail, business numbers, blocked numbers, spam numbers, etc.
    if (!TextUtils.isEmpty(originalNumber)) {
      modules.add(
          new IntentModule(
              context,
              IntentUtil.getSendSmsIntent(originalNumber),
              R.string.send_a_message,
              R.drawable.quantum_ic_message_vd_theme_24));
    }
  }

  private static void maybeAddModuleForCopyingNumber(
      Context context, String originalNumber, List<ContactActionModule> modules) {
    if (TextUtils.isEmpty(originalNumber)) {
      return;
    }
    modules.add(
        new ContactActionModule() {
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
            ClipboardUtils.copyText(context, null, originalNumber, true);
            return false;
          }
        });
  }

  private static void addModuleForAccessingCallDetails(
      Context context, DialerContact dialerContact, List<ContactActionModule> modules) {
    // TODO(zachh): Load CallDetailsEntries and canReportInaccurateNumber in
    // CallDetailsActivity (see also isPeopleApiSource(sourceType)).
    CallDetailsEntries callDetailsEntries = CallDetailsEntries.getDefaultInstance();
    boolean canReportInaccurateNumber = false;
    boolean canSupportAssistedDialing = false; // TODO(zachh): Properly set value.

    modules.add(
        new IntentModule(
            context,
            CallDetailsActivity.newInstance(
                context,
                callDetailsEntries,
                dialerContact,
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
