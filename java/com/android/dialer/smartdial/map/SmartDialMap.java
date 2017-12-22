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

package com.android.dialer.smartdial.map;

import android.support.v4.util.SimpleArrayMap;
import com.google.common.base.Optional;

/** Definition for utilities that supports smart dial in different languages. */
@SuppressWarnings("Guava")
abstract class SmartDialMap {

  /**
   * Returns true if the provided character can be mapped to a key on the dialpad.
   *
   * <p>The provided character is expected to be a normalized character. See {@link
   * SmartDialMap#normalizeCharacter(char)} for details.
   */
  protected boolean isValidDialpadCharacter(char ch) {
    return isValidDialpadAlphabeticChar(ch) || isValidDialpadNumericChar(ch);
  }

  /**
   * Returns true if the provided character is a letter and can be mapped to a key on the dialpad.
   *
   * <p>The provided character is expected to be a normalized character. See {@link
   * SmartDialMap#normalizeCharacter(char)} for details.
   */
  protected boolean isValidDialpadAlphabeticChar(char ch) {
    return getCharToKeyMap().containsKey(ch);
  }

  /**
   * Returns true if the provided character is a digit, and can be mapped to a key on the dialpad.
   */
  protected boolean isValidDialpadNumericChar(char ch) {
    return '0' <= ch && ch <= '9';
  }

  /**
   * Get the index of the key on the dialpad which the character corresponds to.
   *
   * <p>The provided character is expected to be a normalized character. See {@link
   * SmartDialMap#normalizeCharacter(char)} for details.
   *
   * <p>An {@link Optional#absent()} is returned if the provided character can't be mapped to a key
   * on the dialpad.
   */
  protected Optional<Byte> getDialpadIndex(char ch) {
    if (isValidDialpadNumericChar(ch)) {
      return Optional.of((byte) (ch - '0'));
    }

    if (isValidDialpadAlphabeticChar(ch)) {
      return Optional.of((byte) (getCharToKeyMap().get(ch) - '0'));
    }

    return Optional.absent();
  }

  /**
   * Get the actual numeric character on the dialpad which the character corresponds to.
   *
   * <p>The provided character is expected to be a normalized character. See {@link
   * SmartDialMap#normalizeCharacter(char)} for details.
   *
   * <p>An {@link Optional#absent()} is returned if the provided character can't be mapped to a key
   * on the dialpad.
   */
  protected Optional<Character> getDialpadNumericCharacter(char ch) {
    return isValidDialpadAlphabeticChar(ch)
        ? Optional.of(getCharToKeyMap().get(ch))
        : Optional.absent();
  }

  /**
   * Converts uppercase characters to lower case ones, and on a best effort basis, strips accents
   * from accented characters.
   *
   * <p>An {@link Optional#absent()} is returned if the provided character can't be mapped to a key
   * on the dialpad.
   */
  abstract Optional<Character> normalizeCharacter(char ch);

  /**
   * Returns a map in which each key is a normalized character and the corresponding value is a
   * dialpad key.
   */
  abstract SimpleArrayMap<Character, Character> getCharToKeyMap();
}
