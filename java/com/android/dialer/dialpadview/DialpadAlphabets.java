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

import android.support.v4.util.SimpleArrayMap;

/** A class containing key-letter mappings for the dialpad. */
public class DialpadAlphabets {

  // The default mapping (the Latin alphabet)
  private static final String[] def = {
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

  // Russian
  private static final String[] rus = {
    "" /* 0 */,
    "" /* 1 */,
    "АБВГ" /* 2 */,
    "ДЕЖЗ" /* 3 */,
    "ИЙКЛ" /* 4 */,
    "МНОП" /* 5 */,
    "РСТУ" /* 6 */,
    "ФХЦЧ" /* 7 */,
    "ШЩЪЫ" /* 8 */,
    "ЬЭЮЯ" /* 9 */,
    "" /* * */,
    "" /* # */,
  };

  // A map in which each key is an ISO 639-2 language code and the corresponding key is an array
  // defining key-letter mappings
  private static final SimpleArrayMap<String, String[]> alphabets = new SimpleArrayMap<>();

  static {
    alphabets.put("rus", rus);
  }

  /**
   * Returns the alphabet (a key-letter mapping) of the given ISO 639-2 language code or null if
   *
   * <ul>
   *   <li>no alphabet for the language code is defined, or
   *   <li>the language code is invalid.
   * </ul>
   */
  public static String[] getAlphabetForLanguage(String languageCode) {
    return alphabets.get(languageCode);
  }

  /** Returns the default key-letter mapping (the one that uses the Latin alphabet). */
  public static String[] getDefaultAlphabet() {
    return def;
  }
}
