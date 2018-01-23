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
   *   if < 1 minute ago: "Just now";
   *   else if < 1 hour ago: time relative to now (e.g., "8 min ago");
   *   else if today: time (e.g., "12:15 PM");
   *   else if < 7 days: abbreviated day of week (e.g., "Wed");
   *   else if < 1 year: date with abbreviated month, day, but no year (e.g., "Jan 15");
   *   else: date with abbreviated month, day, and year (e.g., "Jan 15, 2018").
   * </pre>
   */
  public static CharSequence newCallLogTimestampLabel(
      Context context, long nowMillis, long timestampMillis) {
    // For calls logged less than 1 minute ago, display "Just now".
    if (nowMillis - timestampMillis < TimeUnit.MINUTES.toMillis(1)) {
      return context.getString(R.string.just_now);
    }

    // For calls logged less than 1 hour ago, display time relative to now (e.g., "8 min ago").
    if (nowMillis - timestampMillis < TimeUnit.HOURS.toMillis(1)) {
      return DateUtils.getRelativeTimeSpanString(
              timestampMillis,
              nowMillis,
              DateUtils.MINUTE_IN_MILLIS,
              DateUtils.FORMAT_ABBREV_RELATIVE)
          .toString()
          // The platform method DateUtils#getRelativeTimeSpanString adds a dot ('.') after the
          // abbreviated time unit for some languages (e.g., "8 min. ago") but we prefer not to have
          // the dot.
          .replace(".", "");
    }

    int dayDifference = getDayDifference(nowMillis, timestampMillis);

    // For calls logged today, display time (e.g., "12:15 PM").
    if (dayDifference == 0) {
      return DateUtils.formatDateTime(context, timestampMillis, DateUtils.FORMAT_SHOW_TIME);
    }

    // For calls logged within a week, display the abbreviated day of week (e.g., "Wed").
    if (dayDifference < 7) {
      return formatDayOfWeek(context, timestampMillis);
    }

    // For calls logged within a year, display abbreviated month, day, but no year (e.g., "Jan 15").
    if (isWithinOneYear(nowMillis, timestampMillis)) {
      return formatAbbreviatedDate(context, timestampMillis, /* showYear = */ false);
    }

    // For calls logged no less than one year ago, display abbreviated month, day, and year
    // (e.g., "Jan 15, 2018").
    return formatAbbreviatedDate(context, timestampMillis, /* showYear = */ true);
  }

  /**
   * Formats the provided timestamp (in milliseconds) into date and time suitable for display in the
   * current locale.
   *
   * <p>For example, returns a string like "Wednesday, May 25, 2016, 8:02PM" or "Chorshanba, 2016
   * may 25,20:02".
   *
   * <p>For pre-N devices, the returned value may not start with a capital if the local convention
   * is to not capitalize day names. On N+ devices, the returned value is always capitalized.
   */
  public static CharSequence formatDate(Context context, long timestamp) {
    return toTitleCase(
        DateUtils.formatDateTime(
            context,
            timestamp,
            DateUtils.FORMAT_SHOW_TIME
                | DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_SHOW_WEEKDAY
                | DateUtils.FORMAT_SHOW_YEAR));
  }

  /**
   * Formats the provided timestamp (in milliseconds) into abbreviated day of week.
   *
   * <p>For example, returns a string like "Wed" or "Chor".
   *
   * <p>For pre-N devices, the returned value may not start with a capital if the local convention
   * is to not capitalize day names. On N+ devices, the returned value is always capitalized.
   */
  private static CharSequence formatDayOfWeek(Context context, long timestamp) {
    return toTitleCase(
        DateUtils.formatDateTime(
            context, timestamp, DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY));
  }

  /**
   * Formats the provided timestamp (in milliseconds) into the month abbreviation, day, and
   * optionally, year.
   *
   * <p>For example, returns a string like "Jan 15" or "Jan 15, 2018".
   *
   * <p>For pre-N devices, the returned value may not start with a capital if the local convention
   * is to not capitalize day names. On N+ devices, the returned value is always capitalized.
   */
  private static CharSequence formatAbbreviatedDate(
      Context context, long timestamp, boolean showYear) {
    int flags = DateUtils.FORMAT_ABBREV_MONTH;
    if (!showYear) {
      flags |= DateUtils.FORMAT_NO_YEAR;
    }

    return toTitleCase(DateUtils.formatDateTime(context, timestamp, flags));
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

  /**
   * Returns the absolute difference in days between two timestamps. It is the caller's
   * responsibility to ensure both timestamps are in milliseconds. Failure to do so will result in
   * undefined behavior.
   *
   * <p>Note that the difference is based on day boundaries, not 24-hour periods.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>The difference between 01/19/2018 00:00 and 01/19/2018 23:59 is 0.
   *   <li>The difference between 01/18/2018 23:59 and 01/19/2018 23:59 is 1.
   *   <li>The difference between 01/18/2018 00:00 and 01/19/2018 23:59 is 1.
   *   <li>The difference between 01/17/2018 23:59 and 01/19/2018 00:00 is 2.
   * </ul>
   */
  public static int getDayDifference(long firstTimestamp, long secondTimestamp) {
    // Ensure secondTimestamp is no less than firstTimestamp
    if (secondTimestamp < firstTimestamp) {
      long t = firstTimestamp;
      firstTimestamp = secondTimestamp;
      secondTimestamp = t;
    }

    // Use secondTimestamp as reference
    Calendar startOfReferenceDay = Calendar.getInstance();
    startOfReferenceDay.setTimeInMillis(secondTimestamp);

    // This is attempting to find the start of the reference day, but it's not quite right due to
    // daylight savings. Unfortunately there doesn't seem to be a way to get the correct start of
    // the day without using Joda or Java8, both of which are disallowed. This means that the wrong
    // formatting may be applied on days with time changes (though the displayed values will be
    // correct).
    startOfReferenceDay.add(Calendar.HOUR_OF_DAY, -startOfReferenceDay.get(Calendar.HOUR_OF_DAY));
    startOfReferenceDay.add(Calendar.MINUTE, -startOfReferenceDay.get(Calendar.MINUTE));
    startOfReferenceDay.add(Calendar.SECOND, -startOfReferenceDay.get(Calendar.SECOND));

    Calendar other = Calendar.getInstance();
    other.setTimeInMillis(firstTimestamp);

    int dayDifference = 0;
    while (other.before(startOfReferenceDay)) {
      startOfReferenceDay.add(Calendar.DATE, -1);
      dayDifference++;
    }

    return dayDifference;
  }

  /**
   * Returns true if the two timestamps are within one year. It is the caller's responsibility to
   * ensure both timestamps are in milliseconds. Failure to do so will result in undefined behavior.
   *
   * <p>Note that the difference is based on 365/366-day periods.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>01/01/2018 00:00 and 12/31/2018 23:59 is within one year.
   *   <li>12/31/2017 23:59 and 12/31/2018 23:59 is not within one year.
   *   <li>12/31/2017 23:59 and 01/01/2018 00:00 is within one year.
   * </ul>
   */
  private static boolean isWithinOneYear(long firstTimestamp, long secondTimestamp) {
    // Ensure secondTimestamp is no less than firstTimestamp
    if (secondTimestamp < firstTimestamp) {
      long t = firstTimestamp;
      firstTimestamp = secondTimestamp;
      secondTimestamp = t;
    }

    // Use secondTimestamp as reference
    Calendar reference = Calendar.getInstance();
    reference.setTimeInMillis(secondTimestamp);
    reference.add(Calendar.YEAR, -1);

    Calendar other = Calendar.getInstance();
    other.setTimeInMillis(firstTimestamp);

    return reference.before(other);
  }
}
