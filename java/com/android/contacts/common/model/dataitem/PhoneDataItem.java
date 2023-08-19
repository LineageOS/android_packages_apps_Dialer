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
 * limitations under the License.
 */

package com.android.contacts.common.model.dataitem;

import android.content.ContentValues;
import android.content.Context;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;

/**
 * Represents a phone data item, wrapping the columns in {@link
 * android.provider.ContactsContract.CommonDataKinds.Phone}.
 */
public class PhoneDataItem extends DataItem {

  private static final String KEY_FORMATTED_PHONE_NUMBER = "formattedPhoneNumber";

  /* package */ PhoneDataItem(ContentValues values) {
    super(values);
  }

  public String getNumber() {
    return getContentValues().getAsString(Phone.NUMBER);
  }

  /** Returns the normalized phone number in E164 format. */
  public String getNormalizedNumber() {
    return getContentValues().getAsString(Phone.NORMALIZED_NUMBER);
  }

  public String getLabel() {
    return getContentValues().getAsString(Phone.LABEL);
  }

  public void computeFormattedPhoneNumber(Context context, String defaultCountryIso) {
    final String phoneNumber = getNumber();
    if (phoneNumber != null) {
      final String formattedPhoneNumber =
          PhoneNumberHelper.formatNumber(
              context, phoneNumber, getNormalizedNumber(), defaultCountryIso);
      getContentValues().put(KEY_FORMATTED_PHONE_NUMBER, formattedPhoneNumber);
    }
  }
}
