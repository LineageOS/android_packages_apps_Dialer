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
import android.icu.lang.UCharacter;
import android.icu.text.BreakIterator;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.text.format.DateUtils;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Static methods for formatting dates in the call log. */
public final class CallLogDates {

  /**
   * Uses the new date formatting rules to format dates in the new call log.
   *
   * <p>Rules:
   *
   * <pre>
   *   if < 1 minute ago: "Now";
   *   else if today: "12:15 PM"
   *   else if < 3 days ago: "Wednesday";
   *   else: "Jan 15"
   * </pre>
   */
  public static CharSequence newCallLogTimestampLabel(
      Context context, long nowMillis, long timestampMillis) {
    if (nowMillis - timestampMillis < TimeUnit.MINUTES.toMillis(1)) {
      return context.getString(R.string.now);
    }
    if (isSameDay(nowMillis, timestampMillis)) {
      return DateUtils.formatDateTime(
          context, timestampMillis, DateUtils.FORMAT_SHOW_TIME); // e.g. 12:15 PM
    }
    if (isWithin3Days(nowMillis, timestampMillis)) {
      return formatDayOfWeek(context, timestampMillis); // e.g. "Wednesday"
    }
    return formatAbbreviatedMonthAndDay(context, timestampMillis); // e.g. "Jan 15"
  }

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
    return toTitleCase(
        DateUtils.formatDateTime(
            context,
            callDateMillis,
            DateUtils.FORMAT_SHOW_TIME
                | DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_SHOW_WEEKDAY
                | DateUtils.FORMAT_SHOW_YEAR));
  }

  /**
   * Formats the provided date into the day of week.
   *
   * <p>For example, returns a string like "Wednesday" or "Chorshanba".
   *
   * <p>For pre-N devices, the returned value may not start with a capital if the local convention
   * is to not capitalize day names. On N+ devices, the returned value is always capitalized.
   */
  private static CharSequence formatDayOfWeek(Context context, long callDateMillis) {
    return toTitleCase(
        DateUtils.formatDateTime(context, callDateMillis, DateUtils.FORMAT_SHOW_WEEKDAY));
  }

  /**
   * Formats the provided date into the month abbreviation and day.
   *
   * <p>For example, returns a string like "Jan 15".
   *
   * <p>For pre-N devices, the returned value may not start with a capital if the local convention
   * is to not capitalize day names. On N+ devices, the returned value is always capitalized.
   */
  private static CharSequence formatAbbreviatedMonthAndDay(Context context, long callDateMillis) {
    return toTitleCase(
        DateUtils.formatDateTime(
            context, callDateMillis, DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_NO_YEAR));
  }

  private static CharSequence toTitleCase(CharSequence value) {
    // We want the beginning of the date string to be capitalized, even if the word at the beginning
    // of the string is not usually capitalized. For example, "Wednesdsay" in Uzbek is "chorshanba”
    // (not capitalized). To handle this issue we apply title casing to the start of the sentence so
    // that "chorshanba, 2016 may 25,20:02" becomes "Chorshanba, 2016 may 25,20:02".
    //
    // The ICU library was not available in Android until N, so we can only do this in N+ devices.
    // Pre-N devices will still see incorrect capitalization in some languages.
    if (VERSION.SDK_INT < VERSION_CODES.N) {
      return value;
    }

    // Using the ICU library is safer than just applying toUpperCase() on the first letter of the
    // word because in some languages, there can be multiple starting characters which should be
    // upper-cased together. For example in Dutch "ij" is a digraph in which both letters should be
    // capitalized together.

    // TITLECASE_NO_LOWERCASE is necessary so that things that are already capitalized are not
    // lower-cased as part of the conversion.
    return UCharacter.toTitleCase(
        Locale.getDefault(),
        value.toString(),
        BreakIterator.getSentenceInstance(),
        UCharacter.TITLECASE_NO_LOWERCASE);
  }

  private static boolean isWithin3Days(long nowMillis, long timestampMillis) {
    Calendar threeDaysAgoStartOfDay = Calendar.getInstance();
    threeDaysAgoStartOfDay.setTimeInMillis(nowMillis);

    // This is attempting to find the start of the current day, but it's not quite right due to
    // daylight savings. Unfortunately there doesn't seem to be a way to get the correct start of
    // the day without using Joda or Java8, both of which are disallowed. This means that the wrong
    // formatting may be applied on days with time changes (though the displayed values will be
    // correct).
    threeDaysAgoStartOfDay.add(
        Calendar.HOUR_OF_DAY, -threeDaysAgoStartOfDay.get(Calendar.HOUR_OF_DAY));
    threeDaysAgoStartOfDay.add(Calendar.MINUTE, -threeDaysAgoStartOfDay.get(Calendar.MINUTE));
    threeDaysAgoStartOfDay.add(Calendar.SECOND, -threeDaysAgoStartOfDay.get(Calendar.SECOND));

    threeDaysAgoStartOfDay.add(Calendar.DATE, -2);

    Calendar then = Calendar.getInstance();
    then.setTimeInMillis(timestampMillis);

    return then.equals(threeDaysAgoStartOfDay) || then.after(threeDaysAgoStartOfDay);
  }

  /** Returns true if the provided timestamps are from the same day in the default time zone. */
  public static boolean isSameDay(long firstMillis, long secondMillis) {
    Calendar first = Calendar.getInstance();
    first.setTimeInMillis(firstMillis);

    Calendar second = Calendar.getInstance();
    second.setTimeInMillis(secondMillis);

    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
        && first.get(Calendar.MONTH) == second.get(Calendar.MONTH)
        && first.get(Calendar.DAY_OF_MONTH) == second.get(Calendar.DAY_OF_MONTH);
  }
}
