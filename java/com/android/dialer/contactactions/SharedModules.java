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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.clipboard.ClipboardUtils;
import com.android.dialer.util.IntentUtil;
import com.android.dialer.util.UriUtils;
import java.util.List;

/**
 * Modules for the bottom sheet that are shared between NewVoicemailFragment and NewCallLogFragment
 */
public class SharedModules {

  public static void maybeAddModuleForAddingToContacts(
      Context context,
      List<ContactActionModule> modules,
      @NonNull DialerPhoneNumber number,
      @Nullable String name,
      @Nullable String lookupUri) {
    // TODO(zachh): Only show this for non-spam/blocked numbers.

    // Skip showing the menu item for existing contacts.
    if (isExistingContact(lookupUri)) {
      return;
    }

    // Skip showing the menu item if there is no number.
    String originalNumber = number.getRawInput().getNumber();
    if (TextUtils.isEmpty(originalNumber)) {
      return;
    }

    Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
    intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
    intent.putExtra(ContactsContract.Intents.Insert.PHONE, originalNumber);

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
      Context context, List<ContactActionModule> modules, String originalNumber) {
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

  public static void maybeAddModuleForCopyingNumber(
      Context context, List<ContactActionModule> modules, String originalNumber) {
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
}
