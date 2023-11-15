/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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

import androidx.collection.SimpleArrayMap;

import com.android.dialer.dialpadview.DialpadCharMappings;

import java.util.Optional;

/** A {@link SmartDialMap} for the Ukrainian alphabet. */
final class UkrainianSmartDialMap extends SmartDialMap {

  private static UkrainianSmartDialMap instance;

  static UkrainianSmartDialMap getInstance() {
    if (instance == null) {
      instance = new UkrainianSmartDialMap();
    }

    return instance;
  }

  private UkrainianSmartDialMap() {}

  @Override
  Optional<Character> normalizeCharacter(char ch) {
    ch = Character.toLowerCase(ch);
    return isValidDialpadAlphabeticChar(ch) ? Optional.of(ch) : Optional.empty();
  }

  @Override
  SimpleArrayMap<Character, Character> getCharToKeyMap() {
    return DialpadCharMappings.getCharToKeyMap("ukr");
  }
}
