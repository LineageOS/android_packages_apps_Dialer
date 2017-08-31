/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.contacts.common.compat;

import android.telephony.PhoneNumberUtils;
import android.text.style.TtsSpan;

/**
 * This class contains static utility methods extracted from PhoneNumberUtils, and the methods were
 * added in API level 23. In this way, we could enable the corresponding functionality for pre-M
 * devices. We need maintain this class and keep it synced with PhoneNumberUtils. Another thing to
 * keep in mind is that we use com.google.i18n rather than com.android.i18n in here, so we need make
 * sure the application behavior is preserved.
 */
public class PhoneNumberUtilsCompat {

  /** Not instantiable. */
  private PhoneNumberUtilsCompat() {}

  public static String formatNumber(
      String phoneNumber, String phoneNumberE164, String defaultCountryIso) {
      return PhoneNumberUtils.formatNumber(phoneNumber, phoneNumberE164, defaultCountryIso);
  }

  public static CharSequence createTtsSpannable(CharSequence phoneNumber) {
    return PhoneNumberUtils.createTtsSpannable(phoneNumber);
  }

  public static TtsSpan createTtsSpan(String phoneNumber) {
    return PhoneNumberUtils.createTtsSpan(phoneNumber);
  }
}
