/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.calllogutils;

import android.content.Context;
import android.provider.CallLog.Calls;
import android.telephony.PhoneNumberUtils;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.google.common.base.Optional;

/** Helper for formatting and managing the display of phone numbers. */
public class PhoneNumberDisplayUtil {

  /** Returns the string to display for the given phone number if there is no matching contact. */
  public static CharSequence getDisplayName(
      Context context, CharSequence number, int presentation, boolean isVoicemail) {
    Optional<String> presentationString = getNameForPresentation(context, presentation);
    if (presentationString.isPresent()) {
      return presentationString.get();
    }
    if (isVoicemail) {
      return context.getResources().getString(R.string.voicemail_string);
    }
    if (PhoneNumberHelper.isLegacyUnknownNumbers(number)) {
      return context.getResources().getString(R.string.unknown);
    }
    return "";
  }

  /** Returns the string associated with the given presentation. */
  public static Optional<String> getNameForPresentation(Context appContext, int presentation) {
    if (presentation == Calls.PRESENTATION_UNKNOWN) {
      return Optional.of(appContext.getResources().getString(R.string.unknown));
    }
    if (presentation == Calls.PRESENTATION_RESTRICTED) {
      return Optional.of(PhoneNumberHelper.getDisplayNameForRestrictedNumber(appContext));
    }
    if (presentation == Calls.PRESENTATION_PAYPHONE) {
      return Optional.of(appContext.getResources().getString(R.string.payphone));
    }
    return Optional.absent();
  }

  /**
   * Returns the string to display for the given phone number.
   *
   * @param number the number to display
   * @param formattedNumber the formatted number if available, may be null
   */
  public static CharSequence getDisplayNumber(
      Context context,
      CharSequence number,
      int presentation,
      CharSequence formattedNumber,
      CharSequence postDialDigits,
      boolean isVoicemail) {
    final CharSequence displayName = getDisplayName(context, number, presentation, isVoicemail);
    if (!TextUtils.isEmpty(displayName)) {
      return getTtsSpannableLtrNumber(displayName);
    }

    if (!TextUtils.isEmpty(formattedNumber)) {
      return getTtsSpannableLtrNumber(formattedNumber);
    } else if (!TextUtils.isEmpty(number)) {
      return getTtsSpannableLtrNumber(number.toString() + postDialDigits);
    } else {
      return context.getResources().getString(R.string.unknown);
    }
  }

  /** Returns number annotated as phone number in LTR direction. */
  private static CharSequence getTtsSpannableLtrNumber(CharSequence number) {
    return PhoneNumberUtils.createTtsSpannable(
        BidiFormatter.getInstance().unicodeWrap(number.toString(), TextDirectionHeuristics.LTR));
  }
}
