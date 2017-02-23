/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.smartdial;

/**
 * Note: These methods currently take characters as arguments. For future planned language support,
 * they will need to be changed to use codepoints instead of characters.
 *
 * <p>http://docs.oracle.com/javase/6/docs/api/java/lang/String.html#codePointAt(int)
 *
 * <p>If/when this change is made, LatinSmartDialMap(which operates on chars) will continue to work
 * by simply casting from a codepoint to a character.
 */
public interface SmartDialMap {

  /*
   * Returns true if the provided character can be mapped to a key on the dialpad
   */
  boolean isValidDialpadCharacter(char ch);

  /*
   * Returns true if the provided character is a letter, and can be mapped to a key on the dialpad
   */
  boolean isValidDialpadAlphabeticChar(char ch);

  /*
   * Returns true if the provided character is a digit, and can be mapped to a key on the dialpad
   */
  boolean isValidDialpadNumericChar(char ch);

  /*
   * Get the index of the key on the dialpad which the character corresponds to
   */
  byte getDialpadIndex(char ch);

  /*
   * Get the actual numeric character on the dialpad which the character corresponds to
   */
  char getDialpadNumericCharacter(char ch);

  /*
   * Converts uppercase characters to lower case ones, and on a best effort basis, strips accents
   * from accented characters.
   */
  char normalizeCharacter(char ch);
}
