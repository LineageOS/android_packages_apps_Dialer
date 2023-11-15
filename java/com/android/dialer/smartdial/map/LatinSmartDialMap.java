/*
 * Copyright (C) 2016 The Android Open Source Project
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

/** A {@link SmartDialMap} for the Latin alphabet, which is for T9 dialpad searching. */
final class LatinSmartDialMap extends SmartDialMap {

  private static LatinSmartDialMap instance;

  static LatinSmartDialMap getInstance() {
    if (instance == null) {
      instance = new LatinSmartDialMap();
    }

    return instance;
  }

  private LatinSmartDialMap() {}

  /*
   * The switch statement in this function was generated using the python code:
   * from unidecode import unidecode
   * for i in range(192, 564):
   *     char = unichr(i)
   *     decoded = unidecode(char)
   *     # Unicode characters that decompose into multiple characters i.e.
   *     #  into ss are not supported for now
   *     if (len(decoded) == 1 and decoded.isalpha()):
   *         print "case '" + char + "': return Optional.of('" + unidecode(char) +  "');"
   *
   * This gives us a way to map characters containing accents/diacritics to their
   * alphabetic equivalents. The unidecode library can be found at:
   * http://pypi.python.org/pypi/Unidecode/0.04.1
   *
   * Also remaps all upper case latin characters to their lower case equivalents.
   */
  @Override
  Optional<Character> normalizeCharacter(char ch) {
    if (isValidDialpadAlphabeticChar(ch)) {
      return Optional.of(ch);
    }

    switch (ch) {
      case 'À':
      case 'Á':
      case 'Â':
      case 'Ã':
      case 'Ä':
      case 'Å':
      case 'à':
      case 'á':
      case 'â':
      case 'ã':
      case 'ä':
      case 'å':
      case 'Ā':
      case 'ā':
      case 'Ă':
      case 'ă':
      case 'Ą':
      case 'ą':
      case 'Ǎ':
      case 'ǎ':
      case 'Ǟ':
      case 'ǟ':
      case 'Ǡ':
      case 'ǡ':
      case 'Ǻ':
      case 'ǻ':
      case 'Ȁ':
      case 'ȁ':
      case 'Ȃ':
      case 'ȃ':
      case 'Ȧ':
      case 'ȧ':
      case 'A':
        return Optional.of('a');
      case 'ƀ':
      case 'Ɓ':
      case 'Ƃ':
      case 'ƃ':
      case 'B':
        return Optional.of('b');
      case 'Ç':
      case 'ç':
      case 'Ć':
      case 'ć':
      case 'Ĉ':
      case 'ĉ':
      case 'Ċ':
      case 'ċ':
      case 'Č':
      case 'č':
      case 'Ƈ':
      case 'ƈ':
      case 'C':
        return Optional.of('c');
      case 'Ð':
      case 'ð':
      case 'Ď':
      case 'ď':
      case 'Đ':
      case 'đ':
      case 'Ɖ':
      case 'Ɗ':
      case 'Ƌ':
      case 'ƌ':
      case 'ƍ':
      case 'ȡ':
      case 'D':
        return Optional.of('d');
      case 'È':
      case 'É':
      case 'Ê':
      case 'Ë':
      case 'è':
      case 'é':
      case 'ê':
      case 'ë':
      case 'Ē':
      case 'ē':
      case 'Ĕ':
      case 'ĕ':
      case 'Ė':
      case 'ė':
      case 'Ę':
      case 'ę':
      case 'Ě':
      case 'ě':
      case 'Ɛ':
      case 'Ȅ':
      case 'ȅ':
      case 'Ȇ':
      case 'ȇ':
      case 'Ȩ':
      case 'ȩ':
      case 'E':
        return Optional.of('e');
      case 'Ƒ':
      case 'ƒ':
      case 'F':
        return Optional.of('f');
      case 'Ĝ':
      case 'ĝ':
      case 'Ğ':
      case 'ğ':
      case 'Ġ':
      case 'ġ':
      case 'Ģ':
      case 'ģ':
      case 'Ɠ':
      case 'Ɣ':
      case 'Ǥ':
      case 'ǥ':
      case 'Ǧ':
      case 'ǧ':
      case 'Ǵ':
      case 'ǵ':
      case 'G':
        return Optional.of('g');
      case 'Ĥ':
      case 'ĥ':
      case 'Ħ':
      case 'ħ':
      case 'Ȟ':
      case 'ȟ':
      case 'H':
        return Optional.of('h');
      case 'Ì':
      case 'Í':
      case 'Î':
      case 'Ï':
      case 'ì':
      case 'í':
      case 'î':
      case 'ï':
      case 'Ĩ':
      case 'ĩ':
      case 'Ī':
      case 'ī':
      case 'Ĭ':
      case 'ĭ':
      case 'Į':
      case 'į':
      case 'İ':
      case 'ı':
      case 'Ɩ':
      case 'Ɨ':
      case 'Ǐ':
      case 'ǐ':
      case 'Ȉ':
      case 'ȉ':
      case 'Ȋ':
      case 'ȋ':
      case 'I':
        return Optional.of('i');
      case 'Ĵ':
      case 'ĵ':
      case 'ǰ':
      case 'J':
        return Optional.of('j');
      case 'Ķ':
      case 'ķ':
      case 'ĸ':
      case 'Ƙ':
      case 'ƙ':
      case 'Ǩ':
      case 'ǩ':
      case 'K':
        return Optional.of('k');
      case 'Ĺ':
      case 'ĺ':
      case 'Ļ':
      case 'ļ':
      case 'Ľ':
      case 'ľ':
      case 'Ŀ':
      case 'ŀ':
      case 'Ł':
      case 'ł':
      case 'ƚ':
      case 'ƛ':
      case 'L':
        return Optional.of('l');
      case 'M':
        return Optional.of('m');
      case 'Ñ':
      case 'ñ':
      case 'Ń':
      case 'ń':
      case 'Ņ':
      case 'ņ':
      case 'Ň':
      case 'ň':
      case 'Ɲ':
      case 'ƞ':
      case 'Ǹ':
      case 'ǹ':
      case 'Ƞ':
      case 'N':
        return Optional.of('n');
      case 'Ò':
      case 'Ó':
      case 'Ô':
      case 'Õ':
      case 'Ö':
      case 'Ø':
      case 'ò':
      case 'ó':
      case 'ô':
      case 'õ':
      case 'ö':
      case 'ø':
      case 'Ō':
      case 'ō':
      case 'Ŏ':
      case 'ŏ':
      case 'Ő':
      case 'ő':
      case 'Ɔ':
      case 'Ɵ':
      case 'Ơ':
      case 'ơ':
      case 'Ǒ':
      case 'ǒ':
      case 'Ǫ':
      case 'ǫ':
      case 'Ǭ':
      case 'ǭ':
      case 'Ǿ':
      case 'ǿ':
      case 'Ȍ':
      case 'ȍ':
      case 'Ȏ':
      case 'ȏ':
      case 'Ȫ':
      case 'ȫ':
      case 'Ȭ':
      case 'ȭ':
      case 'Ȯ':
      case 'ȯ':
      case 'Ȱ':
      case 'ȱ':
      case 'O':
        return Optional.of('o');
      case 'Ƥ':
      case 'ƥ':
      case 'P':
        return Optional.of('p');
      case 'Q':
        return Optional.of('q');
      case 'Ŕ':
      case 'ŕ':
      case 'Ŗ':
      case 'ŗ':
      case 'Ř':
      case 'ř':
      case 'Ȑ':
      case 'ȑ':
      case 'Ȓ':
      case 'ȓ':
      case 'R':
        return Optional.of('r');
      case 'Ś':
      case 'ś':
      case 'Ŝ':
      case 'ŝ':
      case 'Ş':
      case 'ş':
      case 'Š':
      case 'š':
      case 'ſ':
      case 'Ș':
      case 'ș':
      case 'S':
        return Optional.of('s');
      case 'Ţ':
      case 'ţ':
      case 'Ť':
      case 'ť':
      case 'Ŧ':
      case 'ŧ':
      case 'ƫ':
      case 'Ƭ':
      case 'ƭ':
      case 'Ʈ':
      case 'Ț':
      case 'ț':
      case 'T':
        return Optional.of('t');
      case 'Ù':
      case 'Ú':
      case 'Û':
      case 'Ü':
      case 'ù':
      case 'ú':
      case 'û':
      case 'ü':
      case 'Ũ':
      case 'ũ':
      case 'Ū':
      case 'ū':
      case 'Ŭ':
      case 'ŭ':
      case 'Ů':
      case 'ů':
      case 'Ű':
      case 'ű':
      case 'Ų':
      case 'ų':
      case 'Ư':
      case 'ư':
      case 'Ǔ':
      case 'ǔ':
      case 'Ǖ':
      case 'ǖ':
      case 'Ǘ':
      case 'ǘ':
      case 'Ǚ':
      case 'ǚ':
      case 'Ǜ':
      case 'ǜ':
      case 'Ȕ':
      case 'ȕ':
      case 'Ȗ':
      case 'ȗ':
      case 'U':
        return Optional.of('u');
      case 'Ʋ':
      case 'V':
        return Optional.of('v');
      case 'Ŵ':
      case 'ŵ':
      case 'Ɯ':
      case 'ƿ':
      case 'Ƿ':
      case 'W':
        return Optional.of('w');
      case '×':
      case 'X':
        return Optional.of('x');
      case 'Ý':
      case 'ý':
      case 'ÿ':
      case 'Ŷ':
      case 'ŷ':
      case 'Ÿ':
      case 'Ʊ':
      case 'Ƴ':
      case 'ƴ':
      case 'Ȝ':
      case 'ȝ':
      case 'Ȳ':
      case 'ȳ':
      case 'Y':
        return Optional.of('y');
      case 'Ź':
      case 'ź':
      case 'Ż':
      case 'ż':
      case 'Ž':
      case 'ž':
      case 'Ƶ':
      case 'ƶ':
      case 'Ȥ':
      case 'ȥ':
      case 'Z':
        return Optional.of('z');
      default:
        return Optional.empty();
    }
  }

  @Override
  SimpleArrayMap<Character, Character> getCharToKeyMap() {
    return DialpadCharMappings.getDefaultCharToKeyMap();
  }
}
