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
import android.text.format.Formatter;
import com.android.dialer.util.DialerUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Utility class for formatting duration and data usage in call log entries. */
public class CallLogDurations {

  private static CharSequence formatDuration(Context context, long elapsedSeconds) {
    // Getting this method into a good state took a bunch of work between eng, i18n team and
    // translators. If at all possible, the strings should not be changed or updated.
    long minutes = TimeUnit.SECONDS.toMinutes(elapsedSeconds);
    long seconds = elapsedSeconds - TimeUnit.MINUTES.toSeconds(minutes);
    Resources res = context.getResources();
    String formatPattern;
    if (elapsedSeconds >= 60) {
      String minutesString = res.getString(R.string.call_details_minutes_abbreviation);
      String secondsString = res.getString(R.string.call_details_seconds_abbreviation);
      // example output: "1m 1s"
      formatPattern =
          context.getString(
              R.string.call_duration_format_pattern,
              Long.toString(minutes),
              minutesString,
              Long.toString(seconds),
              secondsString);
    } else {
      String secondsString = res.getString(R.string.call_details_seconds_abbreviation);
      // example output: "1s"
      formatPattern =
          context.getString(
              R.string.call_duration_short_format_pattern, Long.toString(seconds), secondsString);
    }

    // Since we don't want to update the strings.xml, we need to remove the quotations from the
    // previous implementation.
    return formatPattern.replace("\'", "");
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
