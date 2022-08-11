/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;


/** Utility methods for processing dates. */
public class DateUtils {

  /**
   * Determine the difference, in days between two dates. Uses similar logic as the {@link
   * android.text.format.DateUtils.getRelativeTimeSpanString} method.
   *
   * @param time Instance of time object to use for calculations.
   * @param date1 First date to check.
   * @param date2 Second date to check.
   * @return The absolute difference in days between the two dates.
   */
  public static int getDayDifference(ZoneId timeZone, long date1, long date2) {
    LocalDate localDate1 = Instant.ofEpochMilli(date1).atZone(timeZone).toLocalDate();
    LocalDate localDate2 = Instant.ofEpochMilli(date2).atZone(timeZone).toLocalDate();
    return Math.abs((int) ChronoUnit.DAYS.between(localDate2, localDate1));
  }
}
