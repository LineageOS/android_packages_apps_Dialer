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
 * limitations under the License
 */

package com.android.dialer.searchfragment.common;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.util.SimpleArrayMap;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import com.android.dialer.dialpadview.DialpadCharMappings;
import java.util.regex.Pattern;

/** Utility class for filtering, comparing and handling strings and queries. */
public class QueryFilteringUtil {

  /**
   * The default character-digit map that will be used to find the digit associated with a given
   * character on a T9 keyboard.
   */
  private static final SimpleArrayMap<Character, Character> DEFAULT_CHAR_TO_DIGIT_MAP =
      DialpadCharMappings.getDefaultCharToKeyMap();

  /** Matches strings with "-", "(", ")", 2-9 of at least length one. */
  private static final Pattern T9_PATTERN = Pattern.compile("[\\-()2-9]+");

  /**
   * Returns true if the query is of T9 format and the name's T9 representation belongs to the query
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>#nameMatchesT9Query("7", "John Smith") returns true, 7 -> 'S'
   *   <li>#nameMatchesT9Query("55", "Jessica Jones") returns true, 55 -> 'JJ'
   *   <li>#nameMatchesT9Query("56", "Jessica Jones") returns true, 56 -> 'Jo'
   *   <li>#nameMatchesT9Query("7", "Jessica Jones") returns false, no names start with P,Q,R or S
   * </ul>
   *
   * <p>When the 1st language preference uses a non-Latin alphabet (e.g., Russian) and the character
   * mappings for the alphabet is defined in {@link DialpadCharMappings}, the Latin alphabet will be
   * used first to check if the name matches the query. If they don't match, the non-Latin alphabet
   * will be used.
   *
   * <p>Examples (when the 1st language preference is Russian):
   *
   * <ul>
   *   <li>#nameMatchesT9Query("7", "John Smith") returns true, 7 -> 'S'
   *   <li>#nameMatchesT9Query("7", "Павел Чехов") returns true, 7 -> 'Ч'
   *   <li>#nameMatchesT9Query("77", "Pavel Чехов") returns true, 7 -> 'P' (in the Latin alphabet),
   *       7 -> 'Ч' (in the Russian alphabet)
   * </ul>
   */
  public static boolean nameMatchesT9Query(String query, String name, Context context) {
    if (!T9_PATTERN.matcher(query).matches()) {
      return false;
    }

    query = digitsOnly(query);
    if (getIndexOfT9Substring(query, name, context) != -1) {
      return true;
    }

    // Check matches initials
    // TODO(calderwoodra) investigate faster implementation
    int queryIndex = 0;

    String[] names = name.toLowerCase().split("\\s");
    for (int i = 0; i < names.length && queryIndex < query.length(); i++) {
      if (TextUtils.isEmpty(names[i])) {
        continue;
      }

      if (getDigit(names[i].charAt(0), context) == query.charAt(queryIndex)) {
        queryIndex++;
      }
    }

    return queryIndex == query.length();
  }

  /**
   * Returns the index where query is contained in the T9 representation of the name.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>#getIndexOfT9Substring("76", "John Smith") returns 5, 76 -> 'Sm'
   *   <li>#nameMatchesT9Query("2226", "AAA Mom") returns 0, 2226 -> 'AAAM'
   *   <li>#nameMatchesT9Query("2", "Jessica Jones") returns -1, Neither 'Jessica' nor 'Jones' start
   *       with A, B or C
   * </ul>
   */
  public static int getIndexOfT9Substring(String query, String name, Context context) {
    query = digitsOnly(query);
    String t9Name = getT9Representation(name, context);
    String t9NameDigitsOnly = digitsOnly(t9Name);
    if (t9NameDigitsOnly.startsWith(query)) {
      return 0;
    }

    int nonLetterCount = 0;
    for (int i = 1; i < t9NameDigitsOnly.length(); i++) {
      char cur = t9Name.charAt(i);
      if (!Character.isDigit(cur)) {
        nonLetterCount++;
        continue;
      }

      // If the previous character isn't a digit and the current is, check for a match
      char prev = t9Name.charAt(i - 1);
      int offset = i - nonLetterCount;
      if (!Character.isDigit(prev) && t9NameDigitsOnly.startsWith(query, offset)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns true if the subparts of the name (split by white space) begin with the query.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>#nameContainsQuery("b", "Brandon") returns true
   *   <li>#nameContainsQuery("o", "Bob") returns false
   *   <li>#nameContainsQuery("o", "Bob Olive") returns true
   * </ul>
   */
  public static boolean nameContainsQuery(String query, String name) {
    if (TextUtils.isEmpty(name)) {
      return false;
    }

    return Pattern.compile("(^|\\s)" + Pattern.quote(query.toLowerCase()))
        .matcher(name.toLowerCase())
        .find();
  }

  /** @return true if the number belongs to the query. */
  public static boolean numberMatchesNumberQuery(String query, String number) {
    return PhoneNumberUtils.isGlobalPhoneNumber(query)
        && indexOfQueryNonDigitsIgnored(query, number) != -1;
  }

  /**
   * Checks if query is contained in number while ignoring all characters in both that are not
   * digits (i.e. {@link Character#isDigit(char)} returns false).
   *
   * @return index where query is found with all non-digits removed, -1 if it's not found.
   */
  static int indexOfQueryNonDigitsIgnored(@NonNull String query, @NonNull String number) {
    return digitsOnly(number).indexOf(digitsOnly(query));
  }

  /**
   * Replaces characters in the given string with their T9 representations.
   *
   * @param s The original string
   * @param context The context
   * @return The original string with characters replaced with T9 representations.
   */
  public static String getT9Representation(String s, Context context) {
    StringBuilder builder = new StringBuilder(s.length());
    for (char c : s.toLowerCase().toCharArray()) {
      builder.append(getDigit(c, context));
    }
    return builder.toString();
  }

  /** @return String s with only digits recognized by Character#isDigit() remaining */
  public static String digitsOnly(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isDigit(c)) {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /**
   * Returns the digit on a T9 keyboard which is associated with the given lower case character.
   *
   * <p>The default character-key mapping will be used first to find a digit. If no digit is found,
   * try the mapping of the current default locale if it is defined in {@link DialpadCharMappings}.
   * If the second attempt fails, return the original character.
   */
  static char getDigit(char c, Context context) {
    Character digit = DEFAULT_CHAR_TO_DIGIT_MAP.get(c);
    if (digit != null) {
      return digit;
    }

    SimpleArrayMap<Character, Character> charToKeyMap =
        DialpadCharMappings.getCharToKeyMap(context);
    if (charToKeyMap != null) {
      digit = charToKeyMap.get(c);
      return digit != null ? digit : c;
    }

    return c;
  }
}
