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

package com.android.dialer.historyitemactions;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.widget.Toast;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.blockreportspam.BlockReportSpamDialogInfo;
import com.android.dialer.blockreportspam.ShowBlockReportSpamDialogNotifier;
import com.android.dialer.clipboard.ClipboardUtils;
import com.android.dialer.util.IntentUtil;
import com.android.dialer.util.UriUtils;
import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Modules for the bottom sheet that are shared between NewVoicemailFragment and NewCallLogFragment
 */
@SuppressWarnings("Guava")
public class SharedModules {

  public static Optional<HistoryItemActionModule> createModuleForAddingToContacts(
      Context context,
      DialerPhoneNumber dialerPhoneNumber,
      String name,
      String lookupUri,
      boolean isBlocked,
      boolean isSpam) {
    // Skip showing the menu item for a spam/blocked number.
    if (isBlocked || isSpam) {
      return Optional.absent();
    }

    // Skip showing the menu item for existing contacts.
    if (isExistingContact(lookupUri)) {
      return Optional.absent();
    }

    // Skip showing the menu item if there is no number.
    String normalizedNumber = dialerPhoneNumber.getNormalizedNumber();
    if (TextUtils.isEmpty(normalizedNumber)) {
      return Optional.absent();
    }

    Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
    intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
    intent.putExtra(ContactsContract.Intents.Insert.PHONE, normalizedNumber);

    if (!TextUtils.isEmpty(name)) {
      intent.putExtra(ContactsContract.Intents.Insert.NAME, name);
    }

    return Optional.of(
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
  private static boolean isExistingContact(@Nullable String lookupUri) {
    return !TextUtils.isEmpty(lookupUri) && !UriUtils.isEncodedContactUri(Uri.parse(lookupUri));
  }

  public static Optional<HistoryItemActionModule> createModuleForSendingTextMessage(
      Context context, String normalizedNumber, boolean isBlocked) {
    // Don't show the option to send a text message if the number is blocked.
    if (isBlocked) {
      return Optional.absent();
    }

    // TODO(zachh): There are some conditions where this module should not be shown; consider
    // voicemail, business numbers, etc.

    return !TextUtils.isEmpty(normalizedNumber)
        ? Optional.of(
            new IntentModule(
                context,
                IntentUtil.getSendSmsIntent(normalizedNumber),
                R.string.send_a_message,
                R.drawable.quantum_ic_message_vd_theme_24))
        : Optional.absent();
  }

  /**
   * Create modules related to blocking/unblocking a number and/or reporting it as spam/not spam.
   */
  public static List<HistoryItemActionModule> createModulesHandlingBlockedOrSpamNumber(
      Context context,
      BlockReportSpamDialogInfo blockReportSpamDialogInfo,
      boolean isBlocked,
      boolean isSpam) {
    List<HistoryItemActionModule> modules = new ArrayList<>();

    // For a spam number, add two options:
    // (1) "Not spam" and "Block", or
    // (2) "Not spam" and "Unblock".
    if (isSpam) {
      modules.add(createModuleForMarkingNumberAsNonSpam(context, blockReportSpamDialogInfo));
      modules.add(
          createModuleForBlockingOrUnblockingNumber(context, blockReportSpamDialogInfo, isBlocked));
      return modules;
    }

    // For a blocked non-spam number, add "Unblock" option.
    if (isBlocked) {
      modules.add(
          createModuleForBlockingOrUnblockingNumber(context, blockReportSpamDialogInfo, isBlocked));
      return modules;
    }

    // For a number that is neither a spam number nor blocked, add "Block/Report spam" option.
    modules.add(
        createModuleForBlockingNumberAndOptionallyReportingSpam(
            context, blockReportSpamDialogInfo));
    return modules;
  }

  /** Create "Not spam" module. */
  private static HistoryItemActionModule createModuleForMarkingNumberAsNonSpam(
      Context context, BlockReportSpamDialogInfo blockReportSpamDialogInfo) {
    return new HistoryItemActionModule() {
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
        ShowBlockReportSpamDialogNotifier.notifyShowDialogToReportNotSpam(
            context, blockReportSpamDialogInfo);
        return true; // Close the bottom sheet.
      }
    };
  }

  private static HistoryItemActionModule createModuleForBlockingOrUnblockingNumber(
      Context context, BlockReportSpamDialogInfo blockReportSpamDialogInfo, boolean isBlocked) {
    return new HistoryItemActionModule() {
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
                    blockReportSpamDialogInfo.getNormalizedNumber()),
                Toast.LENGTH_SHORT)
            .show();
        return true; // Close the bottom sheet.
      }
    };
  }

  /** Create "Block/Report spam" module */
  private static HistoryItemActionModule createModuleForBlockingNumberAndOptionallyReportingSpam(
      Context context, BlockReportSpamDialogInfo blockReportSpamDialogInfo) {
    return new HistoryItemActionModule() {
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
        ShowBlockReportSpamDialogNotifier.notifyShowDialogToBlockNumberAndOptionallyReportSpam(
            context, blockReportSpamDialogInfo);
        return true; // Close the bottom sheet.
      }
    };
  }

  public static Optional<HistoryItemActionModule> createModuleForCopyingNumber(
      Context context, String normalizedNumber) {
    if (TextUtils.isEmpty(normalizedNumber)) {
      return Optional.absent();
    }
    return Optional.of(
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
            ClipboardUtils.copyText(context, null, normalizedNumber, true);
            return false;
          }
        });
  }
}
