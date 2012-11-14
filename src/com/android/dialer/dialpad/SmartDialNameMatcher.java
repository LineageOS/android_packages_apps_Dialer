/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.dialer.dialpad;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import java.text.Normalizer;
import java.util.ArrayList;

/**
 * {@link #SmartDialNameMatcher} contains utility functions to remove accents from accented
 * characters and normalize a phone number. It also contains the matching logic that determines if
 * a contact's display name matches a numeric query. The boolean variable
 * {@link #ALLOW_INITIAL_MATCH} controls the behavior of the matching logic and determines
 * whether we allow matches like 57 - (J)ohn (S)mith.
 */
public class SmartDialNameMatcher {

    private final String mQuery;

    private static final char[] LETTERS_TO_DIGITS = {
        '2', '2', '2', // A,B,C -> 2
        '3', '3', '3', // D,E,F -> 3
        '4', '4', '4', // G,H,I -> 4
        '5', '5', '5', // J,K,L -> 5
        '6', '6', '6', // M,N,O -> 6
        '7', '7', '7', '7', // P,Q,R,S -> 7
        '8', '8', '8', // T,U,V -> 8
        '9', '9', '9', '9' // W,X,Y,Z -> 9
    };

    /*
     * The switch statement in this function was generated using the python code:
     * from unidecode import unidecode
     * for i in range(192, 564):
     *     char = unichr(i)
     *     decoded = unidecode(char)
     *     # Unicode characters that decompose into multiple characters i.e.
     *     #  into ss are not supported for now
     *     if (len(decoded) == 1 and decoded.isalpha()):
     *         print "case '" + char + "': return '" + unidecode(char) +  "';"
     *
     * This gives us a way to map characters containing accents/diacritics to their
     * alphabetic equivalents. The unidecode library can be found at:
     * http://pypi.python.org/pypi/Unidecode/0.04.1
     */
    private static char remapAccentedChars(char c) {
        switch (c) {
            case 'À': return 'A';
            case 'Á': return 'A';
            case 'Â': return 'A';
            case 'Ã': return 'A';
            case 'Ä': return 'A';
            case 'Å': return 'A';
            case 'Ç': return 'C';
            case 'È': return 'E';
            case 'É': return 'E';
            case 'Ê': return 'E';
            case 'Ë': return 'E';
            case 'Ì': return 'I';
            case 'Í': return 'I';
            case 'Î': return 'I';
            case 'Ï': return 'I';
            case 'Ð': return 'D';
            case 'Ñ': return 'N';
            case 'Ò': return 'O';
            case 'Ó': return 'O';
            case 'Ô': return 'O';
            case 'Õ': return 'O';
            case 'Ö': return 'O';
            case '×': return 'x';
            case 'Ø': return 'O';
            case 'Ù': return 'U';
            case 'Ú': return 'U';
            case 'Û': return 'U';
            case 'Ü': return 'U';
            case 'Ý': return 'U';
            case 'à': return 'a';
            case 'á': return 'a';
            case 'â': return 'a';
            case 'ã': return 'a';
            case 'ä': return 'a';
            case 'å': return 'a';
            case 'ç': return 'c';
            case 'è': return 'e';
            case 'é': return 'e';
            case 'ê': return 'e';
            case 'ë': return 'e';
            case 'ì': return 'i';
            case 'í': return 'i';
            case 'î': return 'i';
            case 'ï': return 'i';
            case 'ð': return 'd';
            case 'ñ': return 'n';
            case 'ò': return 'o';
            case 'ó': return 'o';
            case 'ô': return 'o';
            case 'õ': return 'o';
            case 'ö': return 'o';
            case 'ø': return 'o';
            case 'ù': return 'u';
            case 'ú': return 'u';
            case 'û': return 'u';
            case 'ü': return 'u';
            case 'ý': return 'y';
            case 'ÿ': return 'y';
            case 'Ā': return 'A';
            case 'ā': return 'a';
            case 'Ă': return 'A';
            case 'ă': return 'a';
            case 'Ą': return 'A';
            case 'ą': return 'a';
            case 'Ć': return 'C';
            case 'ć': return 'c';
            case 'Ĉ': return 'C';
            case 'ĉ': return 'c';
            case 'Ċ': return 'C';
            case 'ċ': return 'c';
            case 'Č': return 'C';
            case 'č': return 'c';
            case 'Ď': return 'D';
            case 'ď': return 'd';
            case 'Đ': return 'D';
            case 'đ': return 'd';
            case 'Ē': return 'E';
            case 'ē': return 'e';
            case 'Ĕ': return 'E';
            case 'ĕ': return 'e';
            case 'Ė': return 'E';
            case 'ė': return 'e';
            case 'Ę': return 'E';
            case 'ę': return 'e';
            case 'Ě': return 'E';
            case 'ě': return 'e';
            case 'Ĝ': return 'G';
            case 'ĝ': return 'g';
            case 'Ğ': return 'G';
            case 'ğ': return 'g';
            case 'Ġ': return 'G';
            case 'ġ': return 'g';
            case 'Ģ': return 'G';
            case 'ģ': return 'g';
            case 'Ĥ': return 'H';
            case 'ĥ': return 'h';
            case 'Ħ': return 'H';
            case 'ħ': return 'h';
            case 'Ĩ': return 'I';
            case 'ĩ': return 'i';
            case 'Ī': return 'I';
            case 'ī': return 'i';
            case 'Ĭ': return 'I';
            case 'ĭ': return 'i';
            case 'Į': return 'I';
            case 'į': return 'i';
            case 'İ': return 'I';
            case 'ı': return 'i';
            case 'Ĵ': return 'J';
            case 'ĵ': return 'j';
            case 'Ķ': return 'K';
            case 'ķ': return 'k';
            case 'ĸ': return 'k';
            case 'Ĺ': return 'L';
            case 'ĺ': return 'l';
            case 'Ļ': return 'L';
            case 'ļ': return 'l';
            case 'Ľ': return 'L';
            case 'ľ': return 'l';
            case 'Ŀ': return 'L';
            case 'ŀ': return 'l';
            case 'Ł': return 'L';
            case 'ł': return 'l';
            case 'Ń': return 'N';
            case 'ń': return 'n';
            case 'Ņ': return 'N';
            case 'ņ': return 'n';
            case 'Ň': return 'N';
            case 'ň': return 'n';
            case 'Ō': return 'O';
            case 'ō': return 'o';
            case 'Ŏ': return 'O';
            case 'ŏ': return 'o';
            case 'Ő': return 'O';
            case 'ő': return 'o';
            case 'Ŕ': return 'R';
            case 'ŕ': return 'r';
            case 'Ŗ': return 'R';
            case 'ŗ': return 'r';
            case 'Ř': return 'R';
            case 'ř': return 'r';
            case 'Ś': return 'S';
            case 'ś': return 's';
            case 'Ŝ': return 'S';
            case 'ŝ': return 's';
            case 'Ş': return 'S';
            case 'ş': return 's';
            case 'Š': return 'S';
            case 'š': return 's';
            case 'Ţ': return 'T';
            case 'ţ': return 't';
            case 'Ť': return 'T';
            case 'ť': return 't';
            case 'Ŧ': return 'T';
            case 'ŧ': return 't';
            case 'Ũ': return 'U';
            case 'ũ': return 'u';
            case 'Ū': return 'U';
            case 'ū': return 'u';
            case 'Ŭ': return 'U';
            case 'ŭ': return 'u';
            case 'Ů': return 'U';
            case 'ů': return 'u';
            case 'Ű': return 'U';
            case 'ű': return 'u';
            case 'Ų': return 'U';
            case 'ų': return 'u';
            case 'Ŵ': return 'W';
            case 'ŵ': return 'w';
            case 'Ŷ': return 'Y';
            case 'ŷ': return 'y';
            case 'Ÿ': return 'Y';
            case 'Ź': return 'Z';
            case 'ź': return 'z';
            case 'Ż': return 'Z';
            case 'ż': return 'z';
            case 'Ž': return 'Z';
            case 'ž': return 'z';
            case 'ſ': return 's';
            case 'ƀ': return 'b';
            case 'Ɓ': return 'B';
            case 'Ƃ': return 'B';
            case 'ƃ': return 'b';
            case 'Ɔ': return 'O';
            case 'Ƈ': return 'C';
            case 'ƈ': return 'c';
            case 'Ɖ': return 'D';
            case 'Ɗ': return 'D';
            case 'Ƌ': return 'D';
            case 'ƌ': return 'd';
            case 'ƍ': return 'd';
            case 'Ɛ': return 'E';
            case 'Ƒ': return 'F';
            case 'ƒ': return 'f';
            case 'Ɠ': return 'G';
            case 'Ɣ': return 'G';
            case 'Ɩ': return 'I';
            case 'Ɨ': return 'I';
            case 'Ƙ': return 'K';
            case 'ƙ': return 'k';
            case 'ƚ': return 'l';
            case 'ƛ': return 'l';
            case 'Ɯ': return 'W';
            case 'Ɲ': return 'N';
            case 'ƞ': return 'n';
            case 'Ɵ': return 'O';
            case 'Ơ': return 'O';
            case 'ơ': return 'o';
            case 'Ƥ': return 'P';
            case 'ƥ': return 'p';
            case 'ƫ': return 't';
            case 'Ƭ': return 'T';
            case 'ƭ': return 't';
            case 'Ʈ': return 'T';
            case 'Ư': return 'U';
            case 'ư': return 'u';
            case 'Ʊ': return 'Y';
            case 'Ʋ': return 'V';
            case 'Ƴ': return 'Y';
            case 'ƴ': return 'y';
            case 'Ƶ': return 'Z';
            case 'ƶ': return 'z';
            case 'ƿ': return 'w';
            case 'Ǎ': return 'A';
            case 'ǎ': return 'a';
            case 'Ǐ': return 'I';
            case 'ǐ': return 'i';
            case 'Ǒ': return 'O';
            case 'ǒ': return 'o';
            case 'Ǔ': return 'U';
            case 'ǔ': return 'u';
            case 'Ǖ': return 'U';
            case 'ǖ': return 'u';
            case 'Ǘ': return 'U';
            case 'ǘ': return 'u';
            case 'Ǚ': return 'U';
            case 'ǚ': return 'u';
            case 'Ǜ': return 'U';
            case 'ǜ': return 'u';
            case 'Ǟ': return 'A';
            case 'ǟ': return 'a';
            case 'Ǡ': return 'A';
            case 'ǡ': return 'a';
            case 'Ǥ': return 'G';
            case 'ǥ': return 'g';
            case 'Ǧ': return 'G';
            case 'ǧ': return 'g';
            case 'Ǩ': return 'K';
            case 'ǩ': return 'k';
            case 'Ǫ': return 'O';
            case 'ǫ': return 'o';
            case 'Ǭ': return 'O';
            case 'ǭ': return 'o';
            case 'ǰ': return 'j';
            case 'ǲ': return 'D';
            case 'Ǵ': return 'G';
            case 'ǵ': return 'g';
            case 'Ƿ': return 'W';
            case 'Ǹ': return 'N';
            case 'ǹ': return 'n';
            case 'Ǻ': return 'A';
            case 'ǻ': return 'a';
            case 'Ǿ': return 'O';
            case 'ǿ': return 'o';
            case 'Ȁ': return 'A';
            case 'ȁ': return 'a';
            case 'Ȃ': return 'A';
            case 'ȃ': return 'a';
            case 'Ȅ': return 'E';
            case 'ȅ': return 'e';
            case 'Ȇ': return 'E';
            case 'ȇ': return 'e';
            case 'Ȉ': return 'I';
            case 'ȉ': return 'i';
            case 'Ȋ': return 'I';
            case 'ȋ': return 'i';
            case 'Ȍ': return 'O';
            case 'ȍ': return 'o';
            case 'Ȏ': return 'O';
            case 'ȏ': return 'o';
            case 'Ȑ': return 'R';
            case 'ȑ': return 'r';
            case 'Ȓ': return 'R';
            case 'ȓ': return 'r';
            case 'Ȕ': return 'U';
            case 'ȕ': return 'u';
            case 'Ȗ': return 'U';
            case 'ȗ': return 'u';
            case 'Ș': return 'S';
            case 'ș': return 's';
            case 'Ț': return 'T';
            case 'ț': return 't';
            case 'Ȝ': return 'Y';
            case 'ȝ': return 'y';
            case 'Ȟ': return 'H';
            case 'ȟ': return 'h';
            case 'Ȥ': return 'Z';
            case 'ȥ': return 'z';
            case 'Ȧ': return 'A';
            case 'ȧ': return 'a';
            case 'Ȩ': return 'E';
            case 'ȩ': return 'e';
            case 'Ȫ': return 'O';
            case 'ȫ': return 'o';
            case 'Ȭ': return 'O';
            case 'ȭ': return 'o';
            case 'Ȯ': return 'O';
            case 'ȯ': return 'o';
            case 'Ȱ': return 'O';
            case 'ȱ': return 'o';
            case 'Ȳ': return 'Y';
            case 'ȳ': return 'y';
            default: return c;
        }
    }

    private final ArrayList<SmartDialMatchPosition> mMatchPositions = Lists.newArrayList();

    public SmartDialNameMatcher(String query) {
        mQuery = query;
    }

    /**
     * Strips all accented characters in a name and converts them to their alphabetic equivalents.
     *
     * @param name Name we want to remove accented characters from.
     * @return Name without accents in characters
     */
    public static String stripDiacritics(String name) {
        // NFD stands for normalization form D - Canonical Decomposition
        // This means that for all characters with diacritics, e.g. ä, we decompose them into
        // two characters, the first being the alphabetic equivalent, and the second being a
        // a character that represents the diacritic.

        final String normalized = Normalizer.normalize(name, Normalizer.Form.NFD);
        final StringBuilder stripped = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            // This pass through the string strips out all the diacritics by checking to see
            // if they are in this list here:
            // http://www.fileformat.info/info/unicode/category/Mn/list.htm
            if (Character.getType(normalized.charAt(i)) != Character.NON_SPACING_MARK) {
                stripped.append(normalized.charAt(i));
            }
        }
        return stripped.toString();
    }

    /**
     * Strips a phone number of unnecessary characters (zeros, ones, spaces, dashes, etc.)
     *
     * @param number Phone number we want to normalize
     * @return Phone number consisting of digits from 2-9
     */
    public static String normalizeNumber(String number) {
        final StringBuilder s = new StringBuilder();
        for (int i = 0; i < number.length(); i++) {
            char ch = number.charAt(i);
            if (ch >= '2' && ch <= '9') {
                s.append(ch);
            }
        }
        return s.toString();
    }

    /**
     * This function iterates through each token in the display name, trying to match the query
     * to the numeric equivalent of the token.
     *
     * A token is defined as a range in the display name delimited by whitespace. For example,
     * the display name "Phillips Thomas Jr" contains three tokens: "phillips", "thomas", and "jr".
     *
     * A match must begin at the start of a token.
     * For example, typing 846(Tho) would match "Phillips Thomas", but 466(hom) would not.
     *
     * Also, a match can extend across tokens.
     * For example, typing 37337(FredS) would match (Fred S)mith.
     *
     * @param displayName The normalized(no accented characters) display name we intend to match
     * against.
     * @param query The string of digits that we want to match the display name to.
     * @param matchList An array list of {@link SmartDialMatchPosition}s that we add matched
     * positions to.
     * @return Returns true if a combination of the tokens in displayName match the query
     * string contained in query. If the function returns true, matchList will contain an
     * ArrayList of match positions. For now, matchList will contain a maximum of one match
     * position. If we intend to support initial matching in the future, matchList could possibly
     * contain more than one match position.
     */
    @VisibleForTesting
    boolean matchesCombination(String displayName, String query,
            ArrayList<SmartDialMatchPosition> matchList) {
        final int nameLength = displayName.length();
        final int queryLength = query.length();

        if (nameLength < queryLength) {
            return false;
        }

        if (queryLength == 0) {
            return false;
        }

        // The current character index in displayName
        // E.g. 3 corresponds to 'd' in "Fred Smith"
        int nameStart = 0;

        // The current character in the query we are trying to match the displayName against
        int queryStart = 0;

        // The start position of the current token we are inspecting
        int tokenStart = 0;

        // The number of non-alphabetic characters we've encountered so far in the current match.
        // E.g. if we've currently matched 3733764849 to (Fred Smith W)illiam, then the
        // seperatorCount should be 2. This allows us to correctly calculate offsets for the match
        // positions
        int seperatorCount = 0;

        ArrayList<SmartDialMatchPosition> partial = new ArrayList<SmartDialMatchPosition>();

        // Keep going until we reach the end of displayName
        while (nameStart < nameLength && queryStart < queryLength) {
            char ch = displayName.charAt(nameStart);
            // Strip diacritics from accented characters if any
            ch = remapAccentedChars(ch);
            if ((ch >= 'A') && (ch <= 'Z')) {
                // Simply change the ascii code to the lower case version instead of using
                // toLowerCase for efficiency
                ch += 32;
            }
            if ((ch >= 'a') && (ch <= 'z')) {
                // a starts at index 0
                if (LETTERS_TO_DIGITS[ch - 'a'] != query.charAt(queryStart)) {
                    // we did not find a match
                    queryStart = 0;
                    seperatorCount = 0;
                    while (nameStart < nameLength &&
                            !Character.isWhitespace(displayName.charAt(nameStart))) {
                        nameStart++;
                    }
                    nameStart++;
                    tokenStart = nameStart;
                } else {
                    if (queryStart == queryLength - 1) {

                        // As much as possible, we prioritize a full token match over a sub token
                        // one so if we find a full token match, we can return right away
                        matchList.add(new SmartDialMatchPosition(
                                tokenStart, queryLength + tokenStart + seperatorCount));
                        return true;
                    }
                    nameStart++;
                    queryStart++;
                    // we matched the current character in the name against one in the query,
                    // continue and see if the rest of the characters match
                }
            } else {
                // found a separator, we skip this character and continue to the next one
                nameStart++;
                if (queryStart == 0) {
                    // This means we found a separator before the start of a token,
                    // so we should increment the token's start position to reflect its true
                    // start position
                    tokenStart = nameStart;
                } else {
                    // Otherwise this separator was found in the middle of a token being matched,
                    // so increase the separator count
                    seperatorCount++;
                }
            }
        }
        return false;
    }

    public boolean matches(String displayName) {
        mMatchPositions.clear();
        return matchesCombination(displayName, mQuery, mMatchPositions);
    }

    public ArrayList<SmartDialMatchPosition> getMatchPositions() {
        return mMatchPositions;
    }

    public String getQuery() {
        return mQuery;
    }
}
