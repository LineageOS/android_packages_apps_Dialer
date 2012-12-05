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

import android.test.suitebuilder.annotation.SmallTest;
import android.test.suitebuilder.annotation.Suppress;
import android.util.Log;

import com.android.dialer.dialpad.SmartDialNameMatcher;

import java.text.Normalizer;
import java.util.ArrayList;

import junit.framework.TestCase;

@SmallTest
public class SmartDialNameMatcherTest extends TestCase {
    private static final String TAG = "SmartDialNameMatcherTest";

    public void testMatches() {
        // Test to ensure that all alphabetic characters are covered
        checkMatches("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz",
                "22233344455566677778889999" + "22233344455566677778889999", true, 0, 26 * 2);
        // Should fail because of a mistyped 2 instead of 9 in the second last character
        checkMatches("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz",
                "22233344455566677778889999" + "22233344455566677778889929", false, 0, 0);

        // Basic name test
        checkMatches("joe", "5", true, 0, 1);
        checkMatches("joe", "56", true, 0, 2);
        checkMatches("joe", "563", true, 0, 3);

        // Matches only word boundary.
        checkMatches("joe", "63", false, 0, 0);
        checkMatches("joe oe", "63", true, 4, 6);

        // Test for a match across word boundaries
        checkMatches("joe oe", "56363", true, 0, 6);
    }

    public void testMatches_repeatedLetters() {
        checkMatches("aaaaaaaaaa", "2222222222", true, 0, 10);
        // Fails because of one extra 2
        checkMatches("aaaaaaaaaa", "22222222222", false, 0, 0);
        checkMatches("zzzzzzzzzz zzzzzzzzzz", "99999999999999999999", true, 0, 21);
    }

    public void testMatches_repeatedSpaces() {
        checkMatches("William     J  Smith", "9455426576", true, 0, 17);
        checkMatches("William     J  Smith", "576", true, 12, 17);
        // Fails because we start at non-word boundary
        checkMatches("William     J  Smith", "6576", false, 0, 0);
    }


    public void testMatches_Initial() {
        // wjs matches (W)illiam (J)ohn (S)mith
        checkMatches("William John Smith", "957", true, 0, 1, 8, 9, 13, 14);
        // wjsmit matches (W)illiam (J)ohn (Smit)h
        checkMatches("William John Smith", "957648", true, 0, 1, 8, 9, 13, 17);
        // wjohn matches (W)illiam (John) Smith
        checkMatches("William John Smith", "95646", true, 0, 1, 8, 12);
        // jsmi matches William (J)ohn (Smi)th
        checkMatches("William John Smith", "5764", true, 8, 9, 13, 16);
        // make sure multiple spaces don't mess things up
        checkMatches("William        John   Smith", "5764", true, 15, 16, 22, 25);
    }

    // TODO: Do we want to make these pass anymore?
    @Suppress
    public void testMatches_repeatedSeparators() {
        // Simple match for single token
        checkMatches("John,,,,,Doe", "5646", true, 0, 4);
        // Match across tokens
        checkMatches("John,,,,,Doe", "56463", true, 0, 10);
        // Match token after chain of separators
        checkMatches("John,,,,,Doe", "363", true, 9, 12);
    }

    public void testMatches_umlaut() {
        checkMatches("ÄÖÜäöü", "268268", true, 0, 6);
    }
    // TODO: Great if it was treated as "s" or "ss. Figure out if possible without prefix trie?
    @Suppress
    public void testMatches_germanSharpS() {
        checkMatches("ß", "s", true, 0, 1);
        checkMatches("ß", "ss", true, 0, 1);
    }

    // TODO: Add this and make it work
    @Suppress
    public void testMatches_greek() {
        // http://en.wikipedia.org/wiki/Greek_alphabet
        fail("Greek letters aren't supported yet.");
    }

    // TODO: Add this and make it work
    @Suppress
    public void testMatches_cyrillic() {
        // http://en.wikipedia.org/wiki/Cyrillic_script
        fail("Cyrillic letters aren't supported yet.");
    }

    private void checkMatches(String displayName, String query, boolean expectedMatches,
            int... expectedMatchPositions) {
        final SmartDialNameMatcher matcher = new SmartDialNameMatcher(query);
        final ArrayList<SmartDialMatchPosition> matchPositions =
                new ArrayList<SmartDialMatchPosition>();
        final boolean matches = matcher.matchesCombination(
                displayName, query, matchPositions);
        Log.d(TAG, "query=" + query + "  text=" + displayName
                + "  nfd=" + Normalizer.normalize(displayName, Normalizer.Form.NFD)
                + "  nfc=" + Normalizer.normalize(displayName, Normalizer.Form.NFC)
                + "  nfkd=" + Normalizer.normalize(displayName, Normalizer.Form.NFKD)
                + "  nfkc=" + Normalizer.normalize(displayName, Normalizer.Form.NFKC)
                + "  matches=" + matches);
        assertEquals("matches", expectedMatches, matches);
        final int length = expectedMatchPositions.length;
        assertEquals(length % 2, 0);
        if (matches) {
            for (int i = 0; i < length/2; i++) {
                assertEquals("start", expectedMatchPositions[i * 2], matchPositions.get(i).start);
                assertEquals("end", expectedMatchPositions[i * 2 + 1], matchPositions.get(i).end);
            }
        }
    }

}
