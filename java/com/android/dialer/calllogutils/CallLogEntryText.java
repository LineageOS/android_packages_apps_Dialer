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
import android.text.TextUtils;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.time.Clock;

/**
 * Computes the primary text and secondary text for call log entries.
 *
 * <p>These text values are shown in the main call log list or in the top item of the bottom sheet
 * menu.
 */
public final class CallLogEntryText {

  /**
   * The primary text for bottom sheets is the same as shown in the entry list.
   *
   * <p>(In the entry list, the number of calls and additional icons are displayed as images
   * following the primary text.)
   */
  public static CharSequence buildPrimaryText(Context context, CoalescedRow row) {
    StringBuilder primaryText = new StringBuilder();
    if (!TextUtils.isEmpty(row.numberAttributes().getName())) {
      primaryText.append(row.numberAttributes().getName());
    } else if (!TextUtils.isEmpty(row.formattedNumber())) {
      primaryText.append(row.formattedNumber());
    } else {
      // TODO(zachh): Handle CallLog.Calls.PRESENTATION_*, including Verizon restricted numbers.
      primaryText.append(context.getText(R.string.new_call_log_unknown));
    }
    return primaryText.toString();
  }

  /** The secondary text to show in the main call log entry list. */
  public static CharSequence buildSecondaryTextForEntries(
      Context context, Clock clock, CoalescedRow row) {
    /*
     * Rules: (Duo video, )?$Label|$Location • Date
     *
     * Examples:
     *   Duo Video, Mobile • Now
     *   Duo Video • 11:45pm
     *   Mobile • 11:45pm
     *   Mobile • Sunday
     *   Brooklyn, NJ • Jan 15
     *
     * Date rules:
     *   if < 1 minute ago: "Now"; else if today: HH:MM(am|pm); else if < 3 days: day; else: MON D
     */
    StringBuilder secondaryText = secondaryTextPrefix(context, row);

    if (secondaryText.length() > 0) {
      secondaryText.append(" • ");
    }
    secondaryText.append(
        CallLogDates.newCallLogTimestampLabel(context, clock.currentTimeMillis(), row.timestamp()));
    return secondaryText.toString();
  }

  /**
   * The secondary text to show in the top item of the bottom sheet.
   *
   * <p>This is basically the same as {@link #buildSecondaryTextForEntries(Context, Clock,
   * CoalescedRow)} except that instead of suffixing with the time of the call, we suffix with the
   * formatted number.
   */
  public static String buildSecondaryTextForBottomSheet(Context context, CoalescedRow row) {
    /*
     * Rules: (Duo video, )?$Label|$Location [• NumberIfNoName]?
     *
     * The number is shown at the end if there is no name for the entry. (It is shown in primary
     * text otherwise.)
     *
     * Examples:
     *   Duo Video, Mobile • 555-1234
     *   Duo Video • 555-1234
     *   Mobile • 555-1234
     *   Mobile • 555-1234
     *   Brooklyn, NJ
     */
    StringBuilder secondaryText = secondaryTextPrefix(context, row);

    if (TextUtils.isEmpty(row.numberAttributes().getName())) {
      // If the name is empty the number is shown as the primary text and there's nothing to add.
      return secondaryText.toString();
    }
    if (TextUtils.isEmpty(row.formattedNumber())) {
      // If there's no number, don't append anything.
      return secondaryText.toString();
    }
    // Otherwise append the number.
    if (secondaryText.length() > 0) {
      secondaryText.append(" • ");
    }
    secondaryText.append(row.formattedNumber());
    return secondaryText.toString();
  }

  /**
   * Returns a value such as "Duo Video, Mobile" without the time of the call or formatted number
   * appended.
   *
   * <p>When the secondary text is shown in call log entry list, this prefix is suffixed with the
   * time of the call, and when it is shown in a bottom sheet, it is suffixed with the formatted
   * number.
   */
  private static StringBuilder secondaryTextPrefix(Context context, CoalescedRow row) {
    StringBuilder secondaryText = new StringBuilder();
    if ((row.features() & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO) {
      // TODO(zachh): Add "Duo" prefix?
      secondaryText.append(context.getText(R.string.new_call_log_video));
    }
    String numberTypeLabel = row.numberAttributes().getNumberTypeLabel();
    if (!TextUtils.isEmpty(numberTypeLabel)) {
      if (secondaryText.length() > 0) {
        secondaryText.append(", ");
      }
      secondaryText.append(numberTypeLabel);
    } else { // If there's a number type label, don't show the location.
      String location = row.geocodedLocation();
      if (!TextUtils.isEmpty(location)) {
        if (secondaryText.length() > 0) {
          secondaryText.append(", ");
        }
        secondaryText.append(location);
      }
    }
    return secondaryText;
  }
}
