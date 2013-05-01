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

import android.text.TextUtils;

import com.android.dialer.dialpad.SmartDialTrie.CountryCodeWithOffset;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

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

    public static final char[] LATIN_LETTERS_TO_DIGITS = {
        '2', '2', '2', // A,B,C -> 2
        '3', '3', '3', // D,E,F -> 3
        '4', '4', '4', // G,H,I -> 4
        '5', '5', '5', // J,K,L -> 5
        '6', '6', '6', // M,N,O -> 6
        '7', '7', '7', '7', // P,Q,R,S -> 7
        '8', '8', '8', // T,U,V -> 8
        '9', '9', '9', '9' // W,X,Y,Z -> 9
    };

    // Whether or not we allow matches like 57 - (J)ohn (S)mith
    private static final boolean ALLOW_INITIAL_MATCH = true;

    // The maximum length of the initial we will match - typically set to 1 to minimize false
    // positives
    private static final int INITIAL_LENGTH_LIMIT = 1;

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
     *
     * Also remaps all upper case latin characters to their lower case equivalents.
     */
    public static char remapAccentedChars(char c) {
        switch (c) {
            case 'À': return 'a';
            case 'Á': return 'a';
            case 'Â': return 'a';
            case 'Ã': return 'a';
            case 'Ä': return 'a';
            case 'Å': return 'a';
            case 'Ç': return 'c';
            case 'È': return 'e';
            case 'É': return 'e';
            case 'Ê': return 'e';
            case 'Ë': return 'e';
            case 'Ì': return 'i';
            case 'Í': return 'i';
            case 'Î': return 'i';
            case 'Ï': return 'i';
            case 'Ð': return 'd';
            case 'Ñ': return 'n';
            case 'Ò': return 'o';
            case 'Ó': return 'o';
            case 'Ô': return 'o';
            case 'Õ': return 'o';
            case 'Ö': return 'o';
            case '×': return 'x';
            case 'Ø': return 'o';
            case 'Ù': return 'u';
            case 'Ú': return 'u';
            case 'Û': return 'u';
            case 'Ü': return 'u';
            case 'Ý': return 'u';
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
            case 'Ā': return 'a';
            case 'ā': return 'a';
            case 'Ă': return 'a';
            case 'ă': return 'a';
            case 'Ą': return 'a';
            case 'ą': return 'a';
            case 'Ć': return 'c';
            case 'ć': return 'c';
            case 'Ĉ': return 'c';
            case 'ĉ': return 'c';
            case 'Ċ': return 'c';
            case 'ċ': return 'c';
            case 'Č': return 'c';
            case 'č': return 'c';
            case 'Ď': return 'd';
            case 'ď': return 'd';
            case 'Đ': return 'd';
            case 'đ': return 'd';
            case 'Ē': return 'e';
            case 'ē': return 'e';
            case 'Ĕ': return 'e';
            case 'ĕ': return 'e';
            case 'Ė': return 'e';
            case 'ė': return 'e';
            case 'Ę': return 'e';
            case 'ę': return 'e';
            case 'Ě': return 'e';
            case 'ě': return 'e';
            case 'Ĝ': return 'g';
            case 'ĝ': return 'g';
            case 'Ğ': return 'g';
            case 'ğ': return 'g';
            case 'Ġ': return 'g';
            case 'ġ': return 'g';
            case 'Ģ': return 'g';
            case 'ģ': return 'g';
            case 'Ĥ': return 'h';
            case 'ĥ': return 'h';
            case 'Ħ': return 'h';
            case 'ħ': return 'h';
            case 'Ĩ': return 'i';
            case 'ĩ': return 'i';
            case 'Ī': return 'i';
            case 'ī': return 'i';
            case 'Ĭ': return 'i';
            case 'ĭ': return 'i';
            case 'Į': return 'i';
            case 'į': return 'i';
            case 'İ': return 'i';
            case 'ı': return 'i';
            case 'Ĵ': return 'j';
            case 'ĵ': return 'j';
            case 'Ķ': return 'k';
            case 'ķ': return 'k';
            case 'ĸ': return 'k';
            case 'Ĺ': return 'l';
            case 'ĺ': return 'l';
            case 'Ļ': return 'l';
            case 'ļ': return 'l';
            case 'Ľ': return 'l';
            case 'ľ': return 'l';
            case 'Ŀ': return 'l';
            case 'ŀ': return 'l';
            case 'Ł': return 'l';
            case 'ł': return 'l';
            case 'Ń': return 'n';
            case 'ń': return 'n';
            case 'Ņ': return 'n';
            case 'ņ': return 'n';
            case 'Ň': return 'n';
            case 'ň': return 'n';
            case 'Ō': return 'o';
            case 'ō': return 'o';
            case 'Ŏ': return 'o';
            case 'ŏ': return 'o';
            case 'Ő': return 'o';
            case 'ő': return 'o';
            case 'Ŕ': return 'r';
            case 'ŕ': return 'r';
            case 'Ŗ': return 'r';
            case 'ŗ': return 'r';
            case 'Ř': return 'r';
            case 'ř': return 'r';
            case 'Ś': return 's';
            case 'ś': return 's';
            case 'Ŝ': return 's';
            case 'ŝ': return 's';
            case 'Ş': return 's';
            case 'ş': return 's';
            case 'Š': return 's';
            case 'š': return 's';
            case 'Ţ': return 't';
            case 'ţ': return 't';
            case 'Ť': return 't';
            case 'ť': return 't';
            case 'Ŧ': return 't';
            case 'ŧ': return 't';
            case 'Ũ': return 'u';
            case 'ũ': return 'u';
            case 'Ū': return 'u';
            case 'ū': return 'u';
            case 'Ŭ': return 'u';
            case 'ŭ': return 'u';
            case 'Ů': return 'u';
            case 'ů': return 'u';
            case 'Ű': return 'u';
            case 'ű': return 'u';
            case 'Ų': return 'u';
            case 'ų': return 'u';
            case 'Ŵ': return 'w';
            case 'ŵ': return 'w';
            case 'Ŷ': return 'y';
            case 'ŷ': return 'y';
            case 'Ÿ': return 'y';
            case 'Ź': return 'z';
            case 'ź': return 'z';
            case 'Ż': return 'z';
            case 'ż': return 'z';
            case 'Ž': return 'z';
            case 'ž': return 'z';
            case 'ſ': return 's';
            case 'ƀ': return 'b';
            case 'Ɓ': return 'b';
            case 'Ƃ': return 'b';
            case 'ƃ': return 'b';
            case 'Ɔ': return 'o';
            case 'Ƈ': return 'c';
            case 'ƈ': return 'c';
            case 'Ɖ': return 'd';
            case 'Ɗ': return 'd';
            case 'Ƌ': return 'd';
            case 'ƌ': return 'd';
            case 'ƍ': return 'd';
            case 'Ɛ': return 'e';
            case 'Ƒ': return 'f';
            case 'ƒ': return 'f';
            case 'Ɠ': return 'g';
            case 'Ɣ': return 'g';
            case 'Ɩ': return 'i';
            case 'Ɨ': return 'i';
            case 'Ƙ': return 'k';
            case 'ƙ': return 'k';
            case 'ƚ': return 'l';
            case 'ƛ': return 'l';
            case 'Ɯ': return 'w';
            case 'Ɲ': return 'n';
            case 'ƞ': return 'n';
            case 'Ɵ': return 'o';
            case 'Ơ': return 'o';
            case 'ơ': return 'o';
            case 'Ƥ': return 'p';
            case 'ƥ': return 'p';
            case 'ƫ': return 't';
            case 'Ƭ': return 't';
            case 'ƭ': return 't';
            case 'Ʈ': return 't';
            case 'Ư': return 'u';
            case 'ư': return 'u';
            case 'Ʊ': return 'y';
            case 'Ʋ': return 'v';
            case 'Ƴ': return 'y';
            case 'ƴ': return 'y';
            case 'Ƶ': return 'z';
            case 'ƶ': return 'z';
            case 'ƿ': return 'w';
            case 'Ǎ': return 'a';
            case 'ǎ': return 'a';
            case 'Ǐ': return 'i';
            case 'ǐ': return 'i';
            case 'Ǒ': return 'o';
            case 'ǒ': return 'o';
            case 'Ǔ': return 'u';
            case 'ǔ': return 'u';
            case 'Ǖ': return 'u';
            case 'ǖ': return 'u';
            case 'Ǘ': return 'u';
            case 'ǘ': return 'u';
            case 'Ǚ': return 'u';
            case 'ǚ': return 'u';
            case 'Ǜ': return 'u';
            case 'ǜ': return 'u';
            case 'Ǟ': return 'a';
            case 'ǟ': return 'a';
            case 'Ǡ': return 'a';
            case 'ǡ': return 'a';
            case 'Ǥ': return 'g';
            case 'ǥ': return 'g';
            case 'Ǧ': return 'g';
            case 'ǧ': return 'g';
            case 'Ǩ': return 'k';
            case 'ǩ': return 'k';
            case 'Ǫ': return 'o';
            case 'ǫ': return 'o';
            case 'Ǭ': return 'o';
            case 'ǭ': return 'o';
            case 'ǰ': return 'j';
            case 'ǲ': return 'd';
            case 'Ǵ': return 'g';
            case 'ǵ': return 'g';
            case 'Ƿ': return 'w';
            case 'Ǹ': return 'n';
            case 'ǹ': return 'n';
            case 'Ǻ': return 'a';
            case 'ǻ': return 'a';
            case 'Ǿ': return 'o';
            case 'ǿ': return 'o';
            case 'Ȁ': return 'a';
            case 'ȁ': return 'a';
            case 'Ȃ': return 'a';
            case 'ȃ': return 'a';
            case 'Ȅ': return 'e';
            case 'ȅ': return 'e';
            case 'Ȇ': return 'e';
            case 'ȇ': return 'e';
            case 'Ȉ': return 'i';
            case 'ȉ': return 'i';
            case 'Ȋ': return 'i';
            case 'ȋ': return 'i';
            case 'Ȍ': return 'o';
            case 'ȍ': return 'o';
            case 'Ȏ': return 'o';
            case 'ȏ': return 'o';
            case 'Ȑ': return 'r';
            case 'ȑ': return 'r';
            case 'Ȓ': return 'r';
            case 'ȓ': return 'r';
            case 'Ȕ': return 'u';
            case 'ȕ': return 'u';
            case 'Ȗ': return 'u';
            case 'ȗ': return 'u';
            case 'Ș': return 's';
            case 'ș': return 's';
            case 'Ț': return 't';
            case 'ț': return 't';
            case 'Ȝ': return 'y';
            case 'ȝ': return 'y';
            case 'Ȟ': return 'h';
            case 'ȟ': return 'h';
            case 'Ȥ': return 'z';
            case 'ȥ': return 'z';
            case 'Ȧ': return 'a';
            case 'ȧ': return 'a';
            case 'Ȩ': return 'e';
            case 'ȩ': return 'e';
            case 'Ȫ': return 'o';
            case 'ȫ': return 'o';
            case 'Ȭ': return 'o';
            case 'ȭ': return 'o';
            case 'Ȯ': return 'o';
            case 'ȯ': return 'o';
            case 'Ȱ': return 'o';
            case 'ȱ': return 'o';
            case 'Ȳ': return 'y';
            case 'ȳ': return 'y';
            case 'A': return 'a';
            case 'B': return 'b';
            case 'C': return 'c';
            case 'D': return 'd';
            case 'E': return 'e';
            case 'F': return 'f';
            case 'G': return 'g';
            case 'H': return 'h';
            case 'I': return 'i';
            case 'J': return 'j';
            case 'K': return 'k';
            case 'L': return 'l';
            case 'M': return 'm';
            case 'N': return 'n';
            case 'O': return 'o';
            case 'P': return 'p';
            case 'Q': return 'q';
            case 'R': return 'r';
            case 'S': return 's';
            case 'T': return 't';
            case 'U': return 'u';
            case 'V': return 'v';
            case 'W': return 'w';
            case 'X': return 'x';
            case 'Y': return 'y';
            case 'Z': return 'z';
            default: return c;
        }
    }

    private final ArrayList<SmartDialMatchPosition> mMatchPositions = Lists.newArrayList();

    public SmartDialNameMatcher(String query) {
        mQuery = query;
    }

    /**
     * Strips a phone number of unnecessary characters (spaces, dashes, etc.)
     *
     * @param number Phone number we want to normalize
     * @return Phone number consisting of digits from 0-9
     */
    public static String normalizeNumber(String number) {
        return normalizeNumber(number, 0);
    }

    /**
     * Strips a phone number of unnecessary characters (spaces, dashes, etc.)
     *
     * @param number Phone number we want to normalize
     * @param offset Offset to start from
     * @return Phone number consisting of digits from 0-9
     */
    public static String normalizeNumber(String number, int offset) {
        final StringBuilder s = new StringBuilder();
        for (int i = offset; i < number.length(); i++) {
            char ch = number.charAt(i);
            if (ch >= '0' && ch <= '9') {
                s.append(ch);
            }
        }
        return s.toString();
    }

    /**
     * Matches a phone number against a query, taking care of formatting characters and also
     * taking into account country code prefixes and special NANP number treatment.
     *
     * @param phoneNumber - Raw phone number
     * @param query - Normalized query (only contains numbers from 0-9)
     * @param matchNanp - Whether or not to do special matching for NANP numbers
     * @return {@literal null} if the number and the query don't match, a valid
     *         SmartDialMatchPosition with the matching positions otherwise
     */
    public static SmartDialMatchPosition matchesNumber(String phoneNumber, String query,
            boolean matchNanp) {
        // Try matching the number as is
        SmartDialMatchPosition matchPos = matchesNumberWithOffset(phoneNumber, query, 0);
        if (matchPos == null) {
            // Try matching the number without the '+' prefix, if any
            final CountryCodeWithOffset code = SmartDialTrie.getOffsetWithoutCountryCode(
                    phoneNumber);
            if (code != null) {
                matchPos = matchesNumberWithOffset(phoneNumber, query, code.offset);
            }
            if (matchPos == null && matchNanp) {
                // Try matching NANP numbers
                final int[] offsets = SmartDialTrie.getOffsetForNANPNumbers(phoneNumber);
                for (int i = 0; i < offsets.length; i++) {
                    matchPos = matchesNumberWithOffset(phoneNumber, query, offsets[i]);
                    if (matchPos != null) break;
                }
            }
        }
        return matchPos;
    }

    /**
     * Matches a phone number against a query, taking care of formatting characters
     *
     * @param phoneNumber - Raw phone number
     * @param query - Normalized query (only contains numbers from 0-9)
     * @param offset - The position in the number to start the match against (used to ignore
     * leading prefixes/country codes)
     * @return {@literal null} if the number and the query don't match, a valid
     *         SmartDialMatchPosition with the matching positions otherwise
     */
    private static SmartDialMatchPosition matchesNumberWithOffset(String phoneNumber, String query,
            int offset) {
        if (TextUtils.isEmpty(phoneNumber) || TextUtils.isEmpty(query)) {
            return null;
        }
        int queryAt = 0;
        int numberAt = offset;
        for (int i = offset; i < phoneNumber.length(); i++) {
            if (queryAt == query.length()) {
                break;
            }
            char ch = phoneNumber.charAt(i);
            if (ch >= '0' && ch <= '9') {
                if (ch != query.charAt(queryAt)) {
                    return null;
                }
                queryAt++;
            } else {
                if (queryAt == 0) {
                    // Found a separator before any part of the query was matched, so advance the
                    // offset to avoid prematurely highlighting separators before the rest of the
                    // query.
                    // E.g. don't highlight the first '-' if we're matching 1-510-111-1111 with
                    // '510'.
                    // However, if the current offset is 0, just include the beginning separators
                    // anyway, otherwise the highlighting ends up looking weird.
                    // E.g. if we're matching (510)-111-1111 with '510', we should include the
                    // first '('.
                    if (offset != 0) {
                        offset++;
                    }
                }
            }
            numberAt++;
        }
        return new SmartDialMatchPosition(0 + offset, numberAt);
    }

    /**
     * This function iterates through each token in the display name, trying to match the query
     * to the numeric equivalent of the token.
     *
     * A token is defined as a range in the display name delimited by characters that have no
     * latin alphabet equivalents (e.g. spaces - ' ', periods - ',', underscores - '_' or chinese
     * characters - '王'). Transliteration from non-latin characters to latin character will be
     * done on a best effort basis - e.g. 'Ü' - 'u'.
     *
     * For example,
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
     * ArrayList of match positions (multiple matches correspond to initial matches).
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
            if (isLowercaseLatinLetterOrDigit(ch)) {
                if (ch >= 'a' && ch <= 'z') {
                    // a starts at index 0. If ch >= '0' && ch <= '9', we don't have to do anything
                    ch = LATIN_LETTERS_TO_DIGITS[ch - 'a'];
                }
                if (ch != query.charAt(queryStart)) {
                    // Failed to match the current character in the query.

                    // Case 1: Failed to match the first character in the query. Skip to the next
                    // token since there is no chance of this token matching the query.

                    // Case 2: Previous characters in the query matched, but the current character
                    // failed to match. This happened in the middle of a token. Skip to the next
                    // token since there is no chance of this token matching the query.

                    // Case 3: Previous characters in the query matched, but the current character
                    // failed to match. This happened right at the start of the current token. In
                    // this case, we should restart the query and try again with the current token.
                    // Otherwise, we would fail to match a query like "964"(yog) against a name
                    // Yo-Yoghurt because the query match would fail on the 3rd character, and
                    // then skip to the end of the "Yoghurt" token.

                    if (queryStart == 0 || isLowercaseLatinLetterOrDigit(remapAccentedChars(
                            displayName.charAt(nameStart - 1)))) {
                        // skip to the next token, in the case of 1 or 2.
                        while (nameStart < nameLength &&
                                isLowercaseLatinLetterOrDigit(remapAccentedChars(
                                        displayName.charAt(nameStart)))) {
                            nameStart++;
                        }
                        nameStart++;
                    }

                    // Restart the query and set the correct token position
                    queryStart = 0;
                    seperatorCount = 0;
                    tokenStart = nameStart;
                } else {
                    if (queryStart == queryLength - 1) {

                        // As much as possible, we prioritize a full token match over a sub token
                        // one so if we find a full token match, we can return right away
                        matchList.add(new SmartDialMatchPosition(
                                tokenStart, queryLength + tokenStart + seperatorCount));
                        return true;
                    } else if (ALLOW_INITIAL_MATCH && queryStart < INITIAL_LENGTH_LIMIT) {
                        // we matched the first character.
                        // branch off and see if we can find another match with the remaining
                        // characters in the query string and the remaining tokens
                        // find the next separator in the query string
                        int j;
                        for (j = nameStart; j < nameLength; j++) {
                            if (!isLowercaseLatinLetterOrDigit(remapAccentedChars(
                                    displayName.charAt(j)))) {
                                break;
                            }
                        }
                        // this means there is at least one character left after the separator
                        if (j < nameLength - 1) {
                            final String remainder = displayName.substring(j + 1);
                            final ArrayList<SmartDialMatchPosition> partialTemp =
                                    Lists.newArrayList();
                            if (matchesCombination(
                                    remainder, query.substring(queryStart + 1), partialTemp)) {

                                // store the list of possible match positions
                                SmartDialMatchPosition.advanceMatchPositions(partialTemp, j + 1);
                                partialTemp.add(0,
                                        new SmartDialMatchPosition(nameStart, nameStart + 1));
                                // we found a partial token match, store the data in a
                                // temp buffer and return it if we end up not finding a full
                                // token match
                                partial = partialTemp;
                            }
                        }
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
        // if we have no complete match at this point, then we attempt to fall back to the partial
        // token match(if any). If we don't allow initial matching (ALLOW_INITIAL_MATCH = false)
        // then partial will always be empty.
        if (!partial.isEmpty()) {
            matchList.addAll(partial);
            return true;
        }
        return false;
    }

    /*
     * Returns true if the character is a lowercase latin character or digit(i.e. non-separator).
     */
    private boolean isLowercaseLatinLetterOrDigit(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9');
    }

    public boolean matches(String displayName) {
        mMatchPositions.clear();
        return matchesCombination(displayName, mQuery, mMatchPositions);
    }

    public ArrayList<SmartDialMatchPosition> getMatchPositions() {
        // Return a clone of mMatchPositions so that the caller can use it without
        // worrying about it changing
        return new ArrayList<SmartDialMatchPosition>(mMatchPositions);
    }

    public String getQuery() {
        return mQuery;
    }
}
