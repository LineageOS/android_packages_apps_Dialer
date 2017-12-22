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

package com.android.dialer.dialpadview;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.v4.util.SimpleArrayMap;
import com.android.dialer.common.Assert;
import com.android.dialer.compat.CompatUtils;
import com.android.dialer.configprovider.ConfigProviderBindings;

/** A class containing character mappings for the dialpad. */
public class DialpadCharMappings {
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  public static final String FLAG_ENABLE_DUAL_ALPHABETS = "enable_dual_alphabets_on_t9";

  /** The character mapping for the Latin alphabet (the default mapping) */
  private static class Latin {
    private static final String[] KEY_TO_CHARS = {
      "+" /* 0 */,
      "" /* 1 */,
      "ABC" /* 2 */,
      "DEF" /* 3 */,
      "GHI" /* 4 */,
      "JKL" /* 5 */,
      "MNO" /* 6 */,
      "PQRS" /* 7 */,
      "TUV" /* 8 */,
      "WXYZ" /* 9 */,
      "" /* * */,
      "" /* # */,
    };

    private static final SimpleArrayMap<Character, Character> CHAR_TO_KEY =
        getCharToKeyMap(KEY_TO_CHARS);
  }

  /** The character mapping for the Bulgarian alphabet */
  private static class Bul {
    private static final String[] KEY_TO_CHARS = {
      "" /* 0 */,
      "" /* 1 */,
      "АБВГ" /* 2 */,
      "ДЕЖЗ" /* 3 */,
      "ИЙКЛ" /* 4 */,
      "МНО" /* 5 */,
      "ПРС" /* 6 */,
      "ТУФХ" /* 7 */,
      "ЦЧШЩ" /* 8 */,
      "ЪЬЮЯ" /* 9 */,
      "" /* * */,
      "" /* # */,
    };

    private static final SimpleArrayMap<Character, Character> CHAR_TO_KEY =
        getCharToKeyMap(KEY_TO_CHARS);
  }

  /** The character mapping for the Russian alphabet */
  private static class Rus {
    private static final String[] KEY_TO_CHARS = {
      "" /* 0 */,
      "" /* 1 */,
      "АБВГ" /* 2 */,
      "ДЕЁЖЗ" /* 3 */,
      "ИЙКЛ" /* 4 */,
      "МНОП" /* 5 */,
      "РСТУ" /* 6 */,
      "ФХЦЧ" /* 7 */,
      "ШЩЪЫ" /* 8 */,
      "ЬЭЮЯ" /* 9 */,
      "" /* * */,
      "" /* # */,
    };

    private static final SimpleArrayMap<Character, Character> CHAR_TO_KEY =
        getCharToKeyMap(KEY_TO_CHARS);
  }

  /** The character mapping for the Ukrainian alphabet */
  private static class Ukr {
    private static final String[] KEY_TO_CHARS = {
      "" /* 0 */,
      "" /* 1 */,
      "АБВГҐ" /* 2 */,
      "ДЕЄЖЗ" /* 3 */,
      "ИІЇЙКЛ" /* 4 */,
      "МНОП" /* 5 */,
      "РСТУ" /* 6 */,
      "ФХЦЧ" /* 7 */,
      "ШЩ" /* 8 */,
      "ЬЮЯ" /* 9 */,
      "" /* * */,
      "" /* # */,
    };

    private static final SimpleArrayMap<Character, Character> CHAR_TO_KEY =
        getCharToKeyMap(KEY_TO_CHARS);
  }

  // A map in which each key is an ISO 639-2 language code and the corresponding value is a
  // character-key map.
  private static final SimpleArrayMap<String, SimpleArrayMap<Character, Character>>
      CHAR_TO_KEY_MAPS = new SimpleArrayMap<>();

  // A map in which each key is an ISO 639-2 language code and the corresponding value is an array
  // defining a key-characters map.
  private static final SimpleArrayMap<String, String[]> KEY_TO_CHAR_MAPS = new SimpleArrayMap<>();

  static {
    CHAR_TO_KEY_MAPS.put("bul", Bul.CHAR_TO_KEY);
    CHAR_TO_KEY_MAPS.put("rus", Rus.CHAR_TO_KEY);
    CHAR_TO_KEY_MAPS.put("ukr", Ukr.CHAR_TO_KEY);

    KEY_TO_CHAR_MAPS.put("bul", Bul.KEY_TO_CHARS);
    KEY_TO_CHAR_MAPS.put("rus", Rus.KEY_TO_CHARS);
    KEY_TO_CHAR_MAPS.put("ukr", Ukr.KEY_TO_CHARS);
  }

  /**
   * Returns the character-key map of the ISO 639-2 language code of the 1st language preference or
   * null if
   *
   * <ul>
   *   <li>no character-key map for the language code is defined, or
   *   <li>the support for dual alphabets is disabled.
   * </ul>
   */
  public static SimpleArrayMap<Character, Character> getCharToKeyMap(@NonNull Context context) {
    return isDualAlphabetsEnabled(context)
        ? CHAR_TO_KEY_MAPS.get(CompatUtils.getLocale(context).getISO3Language())
        : null;
  }

  /**
   * Returns the character-key map of the provided ISO 639-2 language code.
   *
   * <p>Note: this method is for implementations of {@link
   * com.android.dialer.smartdial.map.SmartDialMap} only. {@link #getCharToKeyMap(Context)} should
   * be used for all other purposes.
   *
   * <p>It is the caller's responsibility to ensure the language code is valid and a character
   * mapping is defined for that language. Otherwise, an exception will be thrown.
   */
  public static SimpleArrayMap<Character, Character> getCharToKeyMap(String languageCode) {
    SimpleArrayMap<Character, Character> charToKeyMap = CHAR_TO_KEY_MAPS.get(languageCode);

    return Assert.isNotNull(
        charToKeyMap, "No character mappings can be found for language code '%s'", languageCode);
  }

  /** Returns the default character-key map (the one that uses the Latin alphabet). */
  public static SimpleArrayMap<Character, Character> getDefaultCharToKeyMap() {
    return Latin.CHAR_TO_KEY;
  }

  /**
   * Returns the key-characters map of the given ISO 639-2 language code of the 1st language
   * preference or null if
   *
   * <ul>
   *   <li>no key-characters map for the language code is defined, or
   *   <li>the support for dual alphabets is disabled.
   * </ul>
   */
  public static String[] getKeyToCharsMap(@NonNull Context context) {
    return isDualAlphabetsEnabled(context)
        ? KEY_TO_CHAR_MAPS.get(CompatUtils.getLocale(context).getISO3Language())
        : null;
  }

  /** Returns the default key-characters map (the one that uses the Latin alphabet). */
  public static String[] getDefaultKeyToCharsMap() {
    return Latin.KEY_TO_CHARS;
  }

  private static boolean isDualAlphabetsEnabled(Context context) {
    return ConfigProviderBindings.get(context).getBoolean(FLAG_ENABLE_DUAL_ALPHABETS, false);
  }

  /**
   * Given a array representing a key-characters map, return its reverse map.
   *
   * <p>It is the caller's responsibility to ensure that
   *
   * <ul>
   *   <li>the array contains only 12 elements,
   *   <li>the 0th element ~ the 9th element are the mappings for keys "0" ~ "9",
   *   <li>the 10th element is for key "*", and
   *   <li>the 11th element is for key "#".
   * </ul>
   *
   * @param keyToChars An array representing a key-characters map. It must satisfy the conditions
   *     above.
   * @return A character-key map.
   */
  private static SimpleArrayMap<Character, Character> getCharToKeyMap(
      @NonNull String[] keyToChars) {
    Assert.checkArgument(keyToChars.length == 12);

    SimpleArrayMap<Character, Character> charToKeyMap = new SimpleArrayMap<>();

    for (int keyIndex = 0; keyIndex < keyToChars.length; keyIndex++) {
      String chars = keyToChars[keyIndex];

      for (int j = 0; j < chars.length(); j++) {
        char c = chars.charAt(j);
        if (Character.isAlphabetic(c)) {
          charToKeyMap.put(Character.toLowerCase(c), getKeyChar(keyIndex));
        }
      }
    }

    return charToKeyMap;
  }

  /** Given a key index of the dialpad, returns the corresponding character. */
  private static char getKeyChar(int keyIndex) {
    Assert.checkArgument(0 <= keyIndex && keyIndex <= 11);

    switch (keyIndex) {
      case 10:
        return '*';
      case 11:
        return '#';
      default:
        return (char) ('0' + keyIndex);
    }
  }
}
