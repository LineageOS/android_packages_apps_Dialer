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
import android.content.res.Resources;
import android.icu.lang.UCharacter;
import android.icu.text.BreakIterator;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import com.android.dialer.util.DialerUtils;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Utility class for formatting data and data usage in call log entries. */
public class CallEntryFormatter {

  /**
   * Formats the provided date into a value suitable for display in the current locale.
   *
   * <p>For example, returns a string like "Wednesday, May 25, 2016, 8:02PM" or "Chorshanba, 2016
   * may 25,20:02".
   *
   * <p>For pre-N devices, the returned value may not start with a capital if the local convention
   * is to not capitalize day names. On N+ devices, the returned value is always capitalized.
   */
  public static CharSequence formatDate(Context context, long callDateMillis) {
    CharSequence dateValue =
        DateUtils.formatDateRange(
            context,
            callDateMillis /* startDate */,
            callDateMillis /* endDate */,
            DateUtils.FORMAT_SHOW_TIME
                | DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_SHOW_WEEKDAY
                | DateUtils.FORMAT_SHOW_YEAR);

    // We want the beginning of the date string to be capitalized, even if the word at the beginning
    // of the string is not usually capitalized. For example, "Wednesdsay" in Uzbek is "chorshanba”
    // (not capitalized). To handle this issue we apply title casing to the start of the sentence so
    // that "chorshanba, 2016 may 25,20:02" becomes "Chorshanba, 2016 may 25,20:02".
    //
    // The ICU library was not available in Android until N, so we can only do this in N+ devices.
    // Pre-N devices will still see incorrect capitalization in some languages.
    if (VERSION.SDK_INT < VERSION_CODES.N) {
      return dateValue;
    }

    // Using the ICU library is safer than just applying toUpperCase() on the first letter of the
    // word because in some languages, there can be multiple starting characters which should be
    // upper-cased together. For example in Dutch "ij" is a digraph in which both letters should be
    // capitalized together.

    // TITLECASE_NO_LOWERCASE is necessary so that things that are already capitalized like the
    // month ("May") are not lower-cased as part of the conversion.
    return UCharacter.toTitleCase(
        Locale.getDefault(),
        dateValue.toString(),
        BreakIterator.getSentenceInstance(),
        UCharacter.TITLECASE_NO_LOWERCASE);
  }

  private static CharSequence formatDuration(Context context, long elapsedSeconds) {
    long minutes = 0;
    long seconds = 0;

    if (elapsedSeconds >= 60) {
      minutes = elapsedSeconds / 60;
      elapsedSeconds -= minutes * 60;
      seconds = elapsedSeconds;
      return context.getString(R.string.callDetailsDurationFormat, minutes, seconds);
    } else {
      seconds = elapsedSeconds;
      return context.getString(R.string.callDetailsShortDurationFormat, seconds);
    }
  }

  private static CharSequence formatDurationA11y(Context context, long elapsedSeconds) {
    Resources res = context.getResources();
    if (elapsedSeconds >= 60) {
      int minutes = (int) (elapsedSeconds / 60);
      int seconds = (int) elapsedSeconds - minutes * 60;
      String minutesString = res.getQuantityString(R.plurals.a11y_minutes, minutes);
      String secondsString = res.getQuantityString(R.plurals.a11y_seconds, seconds);
      // example output: "1 minute 1 second", "2 minutes 2 seconds", ect.
      return context.getString(
          R.string.a11y_call_duration_format, minutes, minutesString, seconds, secondsString);
    } else {
      String secondsString = res.getQuantityString(R.plurals.a11y_seconds, (int) elapsedSeconds);
      // example output: "1 second", "2 seconds"
      return context.getString(
          R.string.a11y_call_duration_short_format, elapsedSeconds, secondsString);
    }
  }

  /**
   * Formats a string containing the call duration and the data usage (if specified).
   *
   * @param elapsedSeconds Total elapsed seconds.
   * @param dataUsage Data usage in bytes, or null if not specified.
   * @return String containing call duration and data usage.
   */
  public static CharSequence formatDurationAndDataUsage(
      Context context, long elapsedSeconds, long dataUsage) {
    return formatDurationAndDataUsageInternal(
        context, formatDuration(context, elapsedSeconds), dataUsage);
  }

  /**
   * Formats a string containing the call duration and the data usage (if specified) for TalkBack.
   *
   * @param elapsedSeconds Total elapsed seconds.
   * @param dataUsage Data usage in bytes, or null if not specified.
   * @return String containing call duration and data usage.
   */
  public static CharSequence formatDurationAndDataUsageA11y(
      Context context, long elapsedSeconds, long dataUsage) {
    return formatDurationAndDataUsageInternal(
        context, formatDurationA11y(context, elapsedSeconds), dataUsage);
  }

  private static CharSequence formatDurationAndDataUsageInternal(
      Context context, CharSequence duration, long dataUsage) {
    List<CharSequence> durationItems = new ArrayList<>();
    if (dataUsage > 0) {
      durationItems.add(duration);
      durationItems.add(Formatter.formatShortFileSize(context, dataUsage));
      return DialerUtils.join(durationItems);
    } else {
      return duration;
    }
  }
}
