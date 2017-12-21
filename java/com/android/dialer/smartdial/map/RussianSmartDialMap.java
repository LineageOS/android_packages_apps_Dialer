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

package com.android.dialer.smartdial.map;

import android.support.v4.util.SimpleArrayMap;
import com.google.common.base.Optional;

/** A {@link SmartDialMap} for the Russian alphabet. */
@SuppressWarnings("Guava")
final class RussianSmartDialMap extends SmartDialMap {
  private static final SimpleArrayMap<Character, Character> CHAR_TO_KEY_MAP =
      new SimpleArrayMap<>();

  // Reference: https://en.wikipedia.org/wiki/Russian_alphabet
  static {
    CHAR_TO_KEY_MAP.put('а', '2');
    CHAR_TO_KEY_MAP.put('б', '2');
    CHAR_TO_KEY_MAP.put('в', '2');
    CHAR_TO_KEY_MAP.put('г', '2');

    CHAR_TO_KEY_MAP.put('д', '3');
    CHAR_TO_KEY_MAP.put('е', '3');
    CHAR_TO_KEY_MAP.put('ё', '3');
    CHAR_TO_KEY_MAP.put('ж', '3');
    CHAR_TO_KEY_MAP.put('з', '3');

    CHAR_TO_KEY_MAP.put('и', '4');
    CHAR_TO_KEY_MAP.put('й', '4');
    CHAR_TO_KEY_MAP.put('к', '4');
    CHAR_TO_KEY_MAP.put('л', '4');

    CHAR_TO_KEY_MAP.put('м', '5');
    CHAR_TO_KEY_MAP.put('н', '5');
    CHAR_TO_KEY_MAP.put('о', '5');
    CHAR_TO_KEY_MAP.put('п', '5');

    CHAR_TO_KEY_MAP.put('р', '6');
    CHAR_TO_KEY_MAP.put('с', '6');
    CHAR_TO_KEY_MAP.put('т', '6');
    CHAR_TO_KEY_MAP.put('у', '6');

    CHAR_TO_KEY_MAP.put('ф', '7');
    CHAR_TO_KEY_MAP.put('х', '7');
    CHAR_TO_KEY_MAP.put('ц', '7');
    CHAR_TO_KEY_MAP.put('ч', '7');

    CHAR_TO_KEY_MAP.put('ш', '8');
    CHAR_TO_KEY_MAP.put('щ', '8');
    CHAR_TO_KEY_MAP.put('ъ', '8');
    CHAR_TO_KEY_MAP.put('ы', '8');

    CHAR_TO_KEY_MAP.put('ь', '9');
    CHAR_TO_KEY_MAP.put('э', '9');
    CHAR_TO_KEY_MAP.put('ю', '9');
    CHAR_TO_KEY_MAP.put('я', '9');
  }

  private static RussianSmartDialMap instance;

  static RussianSmartDialMap getInstance() {
    if (instance == null) {
      instance = new RussianSmartDialMap();
    }

    return instance;
  }

  private RussianSmartDialMap() {}

  @Override
  Optional<Character> normalizeCharacter(char ch) {
    ch = Character.toLowerCase(ch);
    return isValidDialpadAlphabeticChar(ch) ? Optional.of(ch) : Optional.absent();
  }

  @Override
  SimpleArrayMap<Character, Character> getCharToKeyMap() {
    return CHAR_TO_KEY_MAP;
  }
}
