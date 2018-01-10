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
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import com.android.dialer.common.LogUtil;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility class for handling bolding queries contained in string. */
public class QueryBoldingUtil {

  /**
   * Compares a name and query and returns a {@link CharSequence} with bolded characters.
   *
   * <p>Some example of matches:
   *
   * <ul>
   *   <li>"query" would bold "John [query] Smith"
   *   <li>"222" would bold "[AAA] Mom"
   *   <li>"222" would bold "[A]llen [A]lex [A]aron"
   *   <li>"2226" would bold "[AAA M]om"
   * </ul>
   *
   * <p>Some examples of non-matches:
   *
   * <ul>
   *   <li>"ss" would not match "Jessica Jones"
   *   <li>"77" would not match "Jessica Jones"
   * </ul>
   *
   * @param query containing any characters
   * @param name of a contact/string that query will compare to
   * @param context of the app
   * @return name with query bolded if query can be found in the name.
   */
  public static CharSequence getNameWithQueryBolded(
      @Nullable String query, @NonNull String name, @NonNull Context context) {
    if (TextUtils.isEmpty(query)) {
      return name;
    }

    if (!QueryFilteringUtil.nameMatchesT9Query(query, name, context)) {
      Pattern pattern = Pattern.compile("(^|\\s)" + Pattern.quote(query.toLowerCase()));
      Matcher matcher = pattern.matcher(name.toLowerCase());
      if (matcher.find()) {
        // query matches the start of a name (i.e. "jo" -> "Jessica [Jo]nes")
        return getBoldedString(name, matcher.start(), query.length());
      } else {
        // query not found in name
        return name;
      }
    }

    int indexOfT9Match = QueryFilteringUtil.getIndexOfT9Substring(query, name, context);
    if (indexOfT9Match != -1) {
      // query matches the start of a T9 name (i.e. 75 -> "Jessica [Jo]nes")
      int numBolded = query.length();

      // Bold an extra character for each non-letter
      for (int i = indexOfT9Match; i <= indexOfT9Match + numBolded && i < name.length(); i++) {
        if (!Character.isLetter(name.charAt(i))) {
          numBolded++;
        }
      }
      return getBoldedString(name, indexOfT9Match, numBolded);
    } else {
      // query match the T9 initials (i.e. 222 -> "[A]l [B]ob [C]harlie")
      return getNameWithInitialsBolded(query, name, context);
    }
  }

  private static CharSequence getNameWithInitialsBolded(
      String query, String name, Context context) {
    SpannableString boldedInitials = new SpannableString(name);
    name = name.toLowerCase();
    int initialsBolded = 0;
    int nameIndex = -1;

    while (++nameIndex < name.length() && initialsBolded < query.length()) {
      if ((nameIndex == 0 || name.charAt(nameIndex - 1) == ' ')
          && QueryFilteringUtil.getDigit(name.charAt(nameIndex), context)
              == query.charAt(initialsBolded)) {
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
    if (numBolded + index > s.length()) {
      LogUtil.e(
          "QueryBoldingUtil#getBoldedString",
          "number of bolded characters exceeded length of string.");
      numBolded = s.length() - index;
    }
    SpannableString span = new SpannableString(s);
    span.setSpan(
        new StyleSpan(Typeface.BOLD), index, index + numBolded, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
    return span;
  }
}
