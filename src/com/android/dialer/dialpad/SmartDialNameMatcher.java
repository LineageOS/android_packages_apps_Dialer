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

import com.android.contacts.test.NeededForTesting;

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
