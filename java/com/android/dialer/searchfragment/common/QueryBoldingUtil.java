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

import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;

/** Utility class for handling bolding queries contained in string. */
public class QueryBoldingUtil {

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
  public static CharSequence getNameWithQueryBolded(@Nullable String query, @NonNull String name) {
    if (TextUtils.isEmpty(query)) {
      return name;
    }

    int index = -1;
    int numberOfBoldedCharacters = 0;

    if (QueryFilteringUtil.nameMatchesT9Query(query, name)) {
      // Bold the characters that match the t9 query
      String t9 = QueryFilteringUtil.getT9Representation(name);
      index = QueryFilteringUtil.indexOfQueryNonDigitsIgnored(query, t9);
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
          && QueryFilteringUtil.getDigit(name.charAt(nameIndex)) == query.charAt(initialsBolded)) {
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
  public static CharSequence getNumberWithQueryBolded(
      @Nullable String query, @NonNull String number) {
    if (TextUtils.isEmpty(query) || !QueryFilteringUtil.numberMatchesNumberQuery(query, number)) {
      return number;
    }

    int index = QueryFilteringUtil.indexOfQueryNonDigitsIgnored(query, number);
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
}
