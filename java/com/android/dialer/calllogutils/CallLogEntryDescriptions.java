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
 * limitations under the License.
 */

package com.android.dialer.calllogutils;

import android.content.Context;
import android.provider.CallLog.Calls;
import android.support.annotation.PluralsRes;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.time.Clock;
import com.google.common.collect.Collections2;
import java.util.List;

/** Builds descriptions of call log entries for accessibility users. */
public final class CallLogEntryDescriptions {

  private CallLogEntryDescriptions() {}

  /**
   * Builds the content description for a call log entry.
   *
   * <p>The description is of format<br>
   * {primary description}, {secondary description}, {phone account description}.
   *
   * <ul>
   *   <li>The primary description depends on the number of calls in the entry. For example:<br>
   *       "1 answered call from Jane Smith", or<br>
   *       "2 calls, the latest is an answered call from Jane Smith".
   *   <li>The secondary description is the same as the secondary text for the call log entry,
   *       except that date/time is not abbreviated. For example:<br>
   *       "mobile, 11 minutes ago".
   *   <li>The phone account description is of format "on {phone_account_label}, via {number}". For
   *       example:<br>
   *       "on SIM 1, via 6502531234".<br>
   *       Note that the phone account description will be empty if the device has only one SIM.
   * </ul>
   *
   * <p>An example of the full description can be:<br>
   * "2 calls, the latest is an answered call from Jane Smith, mobile, 11 minutes ago, on SIM 1, via
   * 6502531234".
   */
  public static CharSequence buildDescriptionForEntry(
      Context context, Clock clock, CoalescedRow row) {

    // Build the primary description.
    // Examples:
    //   (1) For an entry containing only 1 call:
    //         "1 missed call from James Smith".
    //   (2) For entries containing multiple calls:
    //         "2 calls, the latest is a missed call from Jame Smith".
    CharSequence primaryDescription =
        TextUtils.expandTemplate(
            context
                .getResources()
                .getQuantityString(
                    getPrimaryDescriptionResIdForCallType(row),
                    row.getCoalescedIds().getCoalescedIdCount()),
            String.valueOf(row.getCoalescedIds().getCoalescedIdCount()),
            CallLogEntryText.buildPrimaryText(context, row));

    // Build the secondary description.
    // An example: "mobile, 11 minutes ago".
    CharSequence secondaryDescription =
        joinSecondaryTextComponents(
            CallLogEntryText.buildSecondaryTextListForEntries(
                context, clock, row, /* abbreviateDateTime = */ false));

    // Build the phone account description.
    // Note that this description can be an empty string.
    CharSequence phoneAccountDescription = buildPhoneAccountDescription(context, row);

    return TextUtils.isEmpty(phoneAccountDescription)
        ? TextUtils.expandTemplate(
            context
                .getResources()
                .getText(
                    R.string.a11y_new_call_log_entry_full_description_without_phone_account_info),
            primaryDescription,
            secondaryDescription)
        : TextUtils.expandTemplate(
            context
                .getResources()
                .getText(R.string.a11y_new_call_log_entry_full_description_with_phone_account_info),
            primaryDescription,
            secondaryDescription,
            phoneAccountDescription);
  }

  private static @PluralsRes int getPrimaryDescriptionResIdForCallType(CoalescedRow row) {
    switch (row.getCallType()) {
      case Calls.INCOMING_TYPE:
      case Calls.ANSWERED_EXTERNALLY_TYPE:
        return R.plurals.a11y_new_call_log_entry_answered_call;
      case Calls.OUTGOING_TYPE:
        return R.plurals.a11y_new_call_log_entry_outgoing_call;
      case Calls.MISSED_TYPE:
        return R.plurals.a11y_new_call_log_entry_missed_call;
      case Calls.VOICEMAIL_TYPE:
        throw new IllegalStateException("Voicemails not expected in call log");
      case Calls.BLOCKED_TYPE:
        return R.plurals.a11y_new_call_log_entry_blocked_call;
      default:
        // It is possible for users to end up with calls with unknown call types in their
        // call history, possibly due to 3rd party call log implementations (e.g. to
        // distinguish between rejected and missed calls). Instead of crashing, just
        // assume that all unknown call types are missed calls.
        return R.plurals.a11y_new_call_log_entry_missed_call;
    }
  }

  private static CharSequence buildPhoneAccountDescription(Context context, CoalescedRow row) {
    PhoneAccountHandle phoneAccountHandle =
        TelecomUtil.composePhoneAccountHandle(
            row.getPhoneAccountComponentName(), row.getPhoneAccountId());
    if (phoneAccountHandle == null) {
      return "";
    }

    String phoneAccountLabel = PhoneAccountUtils.getAccountLabel(context, phoneAccountHandle);
    if (TextUtils.isEmpty(phoneAccountLabel)) {
      return "";
    }

    if (TextUtils.isEmpty(row.getNumber().getNormalizedNumber())) {
      return "";
    }

    return TextUtils.expandTemplate(
        context.getResources().getText(R.string.a11y_new_call_log_entry_phone_account),
        phoneAccountLabel,
        PhoneNumberUtils.createTtsSpannable(row.getNumber().getNormalizedNumber()));
  }

  private static CharSequence joinSecondaryTextComponents(List<CharSequence> components) {
    return TextUtils.join(
        ", ", Collections2.filter(components, (text) -> !TextUtils.isEmpty(text)));
  }
}
