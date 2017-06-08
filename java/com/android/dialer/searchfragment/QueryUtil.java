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

package com.android.dialer.searchfragment;

import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.PhoneNumberUtils;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import java.util.regex.Pattern;

/** Contains utility methods for comparing and filtering strings with search queries. */
final class QueryUtil {

  /** Matches strings with "-", "(", ")", 2-9 of at least length one. */
  static final Pattern T9_PATTERN = Pattern.compile("[\\-()2-9]+");

  /**
   * Compares a name and query and returns a {@link CharSequence} with bolded characters.
   *
   * <p>Some example:
   *
   * <ul>
   *   <li>"query" would bold "John [query] Smith"
   *   <li>"222" would bold "[AAA] Mom"
   *   <li>"222" would bold "[A]llen [A]lex [A]aron"
   * </ul>
   *
   * @param query containing any characters
   * @param name of a contact/string that query will compare to
   * @return name with query bolded if query can be found in the name.
   */
  static CharSequence getNameWithQueryBolded(@Nullable String query, @NonNull String name) {
    if (TextUtils.isEmpty(query)) {
      return name;
    }

    int index = -1;
    int numberOfBoldedCharacters = 0;

    if (nameMatchesT9Query(query, name)) {
      // Bold the characters that match the t9 query
      index = indexOfQueryNonDigitsIgnored(query, getT9Representation(name));
      if (index == -1) {
        return getNameWithInitialsBolded(query, name);
      }
      numberOfBoldedCharacters = query.length();

      for (int i = 0; i < query.length(); i++) {
        char c = query.charAt(i);
        if (!Character.isDigit(c)) {
          numberOfBoldedCharacters--;
        }
      }

      for (int i = 0; i < index + numberOfBoldedCharacters; i++) {
        if (!Character.isLetterOrDigit(name.charAt(i))) {
          if (i < index) {
            index++;
          } else {
            numberOfBoldedCharacters++;
          }
        }
      }
    }

    if (index == -1) {
      // Bold the query as an exact match in the name
      index = name.toLowerCase().indexOf(query);
      numberOfBoldedCharacters = query.length();
    }

    return index == -1 ? name : getBoldedString(name, index, numberOfBoldedCharacters);
  }

  private static CharSequence getNameWithInitialsBolded(String query, String name) {
    SpannableString boldedInitials = new SpannableString(name);
    name = name.toLowerCase();
    int initialsBolded = 0;
    int nameIndex = -1;

    while (++nameIndex < name.length() && initialsBolded < query.length()) {
      if ((nameIndex == 0 || name.charAt(nameIndex - 1) == ' ')
          && getDigit(name.charAt(nameIndex)) == query.charAt(initialsBolded)) {
        boldedInitials.setSpan(
            new StyleSpan(Typeface.BOLD),
            nameIndex,
            nameIndex + 1,
            Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        initialsBolded++;
      }
    }
    return boldedInitials;
  }

  /**
   * Compares a number and a query and returns a {@link CharSequence} with bolded characters.
   *
   * <ul>
   *   <li>"123" would bold "(650)34[1-23]24"
   *   <li>"123" would bold "+1([123])111-2222
   * </ul>
   *
   * @param query containing only numbers and phone number related characters "(", ")", "-", "+"
   * @param number phone number of a contact that the query will compare to.
   * @return number with query bolded if query can be found in the number.
   */
  static CharSequence getNumberWithQueryBolded(@Nullable String query, @NonNull String number) {
    if (TextUtils.isEmpty(query) || !numberMatchesNumberQuery(query, number)) {
      return number;
    }

    int index = indexOfQueryNonDigitsIgnored(query, number);
    int boldedCharacters = query.length();

    for (char c : query.toCharArray()) {
      if (!Character.isDigit(c)) {
        boldedCharacters--;
      }
    }

    for (int i = 0; i < index + boldedCharacters; i++) {
      if (!Character.isDigit(number.charAt(i))) {
        if (i <= index) {
          index++;
        } else {
          boldedCharacters++;
        }
      }
    }
    return getBoldedString(number, index, boldedCharacters);
  }

  private static SpannableString getBoldedString(String s, int index, int numBolded) {
    SpannableString span = new SpannableString(s);
    span.setSpan(
        new StyleSpan(Typeface.BOLD), index, index + numBolded, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
    return span;
  }

  /**
   * @return true if the query is of T9 format and the name's T9 representation belongs to the
   *     query; false otherwise.
   */
  static boolean nameMatchesT9Query(String query, String name) {
    if (!T9_PATTERN.matcher(query).matches()) {
      return false;
    }

    // Substring
    if (indexOfQueryNonDigitsIgnored(query, getT9Representation(name)) != -1) {
      return true;
    }

    // Check matches initials
    // TODO investigate faster implementation
    query = digitsOnly(query);
    int queryIndex = 0;

    String[] names = name.toLowerCase().split("\\s");
    for (int i = 0; i < names.length && queryIndex < query.length(); i++) {
      if (TextUtils.isEmpty(names[i])) {
        continue;
      }

      if (getDigit(names[i].charAt(0)) == query.charAt(queryIndex)) {
        queryIndex++;
      }
    }

    return queryIndex == query.length();
  }

  /** @return true if the number belongs to the query. */
  static boolean numberMatchesNumberQuery(String query, String number) {
    return PhoneNumberUtils.isGlobalPhoneNumber(query)
        && indexOfQueryNonDigitsIgnored(query, number) != -1;
  }

  /**
   * Checks if query is contained in number while ignoring all characters in both that are not
   * digits (i.e. {@link Character#isDigit(char)} returns false).
   *
   * @return index where query is found with all non-digits removed, -1 if it's not found.
   */
  private static int indexOfQueryNonDigitsIgnored(@NonNull String query, @NonNull String number) {
    return digitsOnly(number).indexOf(digitsOnly(query));
  }

  // Returns string with letters replaced with their T9 representation.
  private static String getT9Representation(String s) {
    StringBuilder builder = new StringBuilder(s.length());
    for (char c : s.toLowerCase().toCharArray()) {
      builder.append(getDigit(c));
    }
    return builder.toString();
  }

  /** @return String s with only digits recognized by Character#isDigit() remaining */
  static String digitsOnly(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isDigit(c)) {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  // Returns the T9 representation of a lower case character, otherwise returns the character.
  private static char getDigit(char c) {
    switch (c) {
      case 'a':
      case 'b':
      case 'c':
        return '2';
      case 'd':
      case 'e':
      case 'f':
        return '3';
      case 'g':
      case 'h':
      case 'i':
        return '4';
      case 'j':
      case 'k':
      case 'l':
        return '5';
      case 'm':
      case 'n':
      case 'o':
        return '6';
      case 'p':
      case 'q':
      case 'r':
      case 's':
        return '7';
      case 't':
      case 'u':
      case 'v':
        return '8';
      case 'w':
      case 'x':
      case 'y':
      case 'z':
        return '9';
      default:
        return c;
    }
  }
}
