/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.dialer.util;

import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;

/** Utilities for creation of intents in Dialer. */
public class IntentUtil {

  private static final String SMS_URI_PREFIX = "sms:";
  private static final int NO_PHONE_TYPE = -1;

  public static Intent getSendSmsIntent(CharSequence phoneNumber) {
    return new Intent(Intent.ACTION_SENDTO, Uri.parse(SMS_URI_PREFIX + phoneNumber));
  }

  public static Intent getNewContactIntent() {
    return new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
  }

  public static Intent getNewContactIntent(CharSequence phoneNumber) {
    return getNewContactIntent(null /* name */, phoneNumber /* phoneNumber */, NO_PHONE_TYPE);
  }

  public static Intent getNewContactIntent(
      CharSequence name, CharSequence phoneNumber, int phoneNumberType) {
    Intent intent = getNewContactIntent();
    populateContactIntent(intent, name, phoneNumber, phoneNumberType);
    return intent;
  }

  public static Intent getAddToExistingContactIntent() {
    Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
    intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
    return intent;
  }

  public static Intent getAddToExistingContactIntent(CharSequence phoneNumber) {
    return getAddToExistingContactIntent(
        null /* name */, phoneNumber /* phoneNumber */, NO_PHONE_TYPE);
  }

  public static Intent getAddToExistingContactIntent(
      CharSequence name, CharSequence phoneNumber, int phoneNumberType) {
    Intent intent = getAddToExistingContactIntent();
    populateContactIntent(intent, name, phoneNumber, phoneNumberType);
    return intent;
  }

  private static void populateContactIntent(
      Intent intent, CharSequence name, CharSequence phoneNumber, int phoneNumberType) {
    if (phoneNumber != null) {
      intent.putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber);
    }
    if (name != null) {
      intent.putExtra(ContactsContract.Intents.Insert.NAME, name);
    }
    if (phoneNumberType != NO_PHONE_TYPE) {
      intent.putExtra(ContactsContract.Intents.Insert.PHONE_TYPE, phoneNumberType);
    }
  }
}
