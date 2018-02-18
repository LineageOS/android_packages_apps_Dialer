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

package com.android.dialer.contactactions;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.widget.Toast;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.clipboard.ClipboardUtils;
import com.android.dialer.util.IntentUtil;
import com.android.dialer.util.UriUtils;
import java.util.List;
import java.util.Locale;

/**
 * Modules for the bottom sheet that are shared between NewVoicemailFragment and NewCallLogFragment
 */
public class SharedModules {

  public static void maybeAddModuleForAddingToContacts(
      Context context,
      List<ContactActionModule> modules,
      DialerPhoneNumber number,
      String name,
      String lookupUri,
      boolean isBlocked,
      boolean isSpam) {
    // Skip showing the menu item for a spam/blocked number.
    if (isBlocked || isSpam) {
      return;
    }

    // Skip showing the menu item for existing contacts.
    if (isExistingContact(lookupUri)) {
      return;
    }

    // Skip showing the menu item if there is no number.
    String normalizedNumber = number.getNormalizedNumber();
    if (TextUtils.isEmpty(normalizedNumber)) {
      return;
    }

    Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
    intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
    intent.putExtra(ContactsContract.Intents.Insert.PHONE, normalizedNumber);

    if (!TextUtils.isEmpty(name)) {
      intent.putExtra(ContactsContract.Intents.Insert.NAME, name);
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
  public static boolean isExistingContact(@Nullable String lookupUri) {
    return !TextUtils.isEmpty(lookupUri) && !UriUtils.isEncodedContactUri(Uri.parse(lookupUri));
  }

  public static void maybeAddModuleForSendingTextMessage(
      Context context,
      List<ContactActionModule> modules,
      String normalizedNumber,
      boolean isBlocked) {
    // Don't show the option to send a text message if the number is blocked.
    if (isBlocked) {
      return;
    }

    // TODO(zachh): There are some conditions where this module should not be shown; consider
    // voicemail, business numbers, etc.

    if (!TextUtils.isEmpty(normalizedNumber)) {
      modules.add(
          new IntentModule(
              context,
              IntentUtil.getSendSmsIntent(normalizedNumber),
              R.string.send_a_message,
              R.drawable.quantum_ic_message_vd_theme_24));
    }
  }

  public static void addModulesHandlingBlockedOrSpamNumber(
      Context context,
      List<ContactActionModule> modules,
      String normalizedNumber,
      boolean isBlocked,
      boolean isSpam) {
    // For a spam number, add two options:
    // (1) "Not spam" and "Block", or
    // (2) "Not spam" and "Unblock".
    if (isSpam) {
      addModuleForMarkingNumberAsNonSpam(context, modules, normalizedNumber);
      addModuleForBlockingOrUnblockingNumber(context, modules, normalizedNumber, isBlocked);
      return;
    }

    // For a blocked non-spam number, add "Unblock" option.
    if (isBlocked) {
      addModuleForBlockingOrUnblockingNumber(context, modules, normalizedNumber, isBlocked);
      return;
    }

    // For a number that is neither a spam number nor blocked, add "Block/Report spam" option.
    addModuleForBlockingNumberAndOptionallyReportingSpam(context, modules, normalizedNumber);
  }

  private static void addModuleForMarkingNumberAsNonSpam(
      Context context, List<ContactActionModule> modules, String normalizedNumber) {
    modules.add(
        new ContactActionModule() {
          @Override
          public int getStringId() {
            return R.string.not_spam;
          }

          @Override
          public int getDrawableId() {
            return R.drawable.quantum_ic_report_off_vd_theme_24;
          }

          @Override
          public boolean onClick() {
            // TODO(a bug): implement this method.
            Toast.makeText(
                    context,
                    String.format(Locale.ENGLISH, "TODO: Report %s as non-spam", normalizedNumber),
                    Toast.LENGTH_SHORT)
                .show();
            return true; // Close the bottom sheet.
          }
        });
  }

  private static void addModuleForBlockingOrUnblockingNumber(
      Context context,
      List<ContactActionModule> modules,
      String normalizedNumber,
      boolean isBlocked) {
    modules.add(
        new ContactActionModule() {
          @Override
          public int getStringId() {
            return isBlocked ? R.string.unblock_number : R.string.block_number;
          }

          @Override
          public int getDrawableId() {
            return isBlocked
                ? R.drawable.ic_unblock // TODO(a bug): use a vector icon
                : R.drawable.quantum_ic_block_vd_theme_24;
          }

          @Override
          public boolean onClick() {
            // TODO(a bug): implement this method.
            Toast.makeText(
                    context,
                    String.format(
                        Locale.ENGLISH,
                        "TODO: " + (isBlocked ? "Unblock " : "Block ") + " number %s.",
                        normalizedNumber),
                    Toast.LENGTH_SHORT)
                .show();
            return true; // Close the bottom sheet.
          }
        });
  }

  private static void addModuleForBlockingNumberAndOptionallyReportingSpam(
      Context context, List<ContactActionModule> modules, String normalizedNumber) {
    modules.add(
        new ContactActionModule() {
          @Override
          public int getStringId() {
            return R.string.block_and_optionally_report_spam;
          }

          @Override
          public int getDrawableId() {
            return R.drawable.quantum_ic_block_vd_theme_24;
          }

          @Override
          public boolean onClick() {
            // TODO(a bug): implement this method.
            Toast.makeText(
                    context,
                    String.format(
                        Locale.ENGLISH,
                        "TODO: Block and optionally report as spam %s.",
                        normalizedNumber),
                    Toast.LENGTH_SHORT)
                .show();
            return true; // Close the bottom sheet.
          }
        });
  }

  public static void maybeAddModuleForCopyingNumber(
      Context context, List<ContactActionModule> modules, String normalizedNumber) {
    if (TextUtils.isEmpty(normalizedNumber)) {
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
            ClipboardUtils.copyText(context, null, normalizedNumber, true);
            return false;
          }
        });
  }
}
