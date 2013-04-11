/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static com.android.dialer.dialpad.SmartDialCache.ContactNumber;

import com.android.dialer.dialpad.SmartDialTrie.Node;

import android.test.suitebuilder.annotation.SmallTest;

import junit.framework.TestCase;

/**
 * To run this test, use the command:
 * adb shell am instrument -w -e class com.android.dialer.dialpad.SmartDialTrieTest /
 * com.android.dialer.tests/android.test.InstrumentationTestRunner
 */
@SmallTest
public class SmartDialTrieTest extends TestCase{

    public void testSize() {
        final SmartDialTrie trie = new SmartDialTrie();
        trie.put(new ContactNumber(0, "Jason", "0", "0", 1));
        assertEquals(1, trie.size());
        trie.put(new ContactNumber(1, "Mary", "0", "1", 2));
        assertEquals(2, trie.size());
        trie.put(new ContactNumber(2, "John", "0", "2", 3));
        assertEquals(3, trie.size());
    }

    public void testPutForFullName() {
        final SmartDialTrie trie = new SmartDialTrie();
        final ContactNumber jasonsmith = new ContactNumber(0, "Jason Smith", "0", "0", 1);
        final ContactNumber jasonsmitt = new ContactNumber(1, "Jason Smitt", "0", "1", 2);
        trie.put(jasonsmith);
        trie.put(jasonsmitt);
        assertTrue(trie.getAllWithPrefix("5276676484").contains(jasonsmith));
        assertFalse(trie.getAllWithPrefix("5276676484").contains(jasonsmitt));

        assertFalse(trie.getAllWithPrefix("5276676488").contains(jasonsmith));
        assertTrue(trie.getAllWithPrefix("5276676488").contains(jasonsmitt));

    }

    public void testPutForPartialName() {
        final SmartDialTrie trie = new SmartDialTrie();
        final ContactNumber maryjane = new ContactNumber(0, "Mary Jane", "0", "0", 1);
        final ContactNumber sarahsmith = new ContactNumber(1, "Sarah Smith", "0", "1", 2);
        final ContactNumber jasonsmitt = new ContactNumber(2, "Jason Smitt", "0", "2", 3);
        trie.put(maryjane);
        trie.put(sarahsmith);
        trie.put(jasonsmitt);

        // 6279 corresponds to mary = "Mary Jane" but not "Jason Smitt"
        assertTrue(checkContains(trie, maryjane, "6279"));
        assertFalse(checkContains(trie, jasonsmitt, "6279"));

        // 72 corresponds to sa = "Sarah Smith" but not "Jason Smitt" or "Mary Jane"
        assertFalse(checkContains(trie, maryjane, "72"));
        assertTrue(checkContains(trie, sarahsmith, "72"));
        assertFalse(checkContains(trie, jasonsmitt, "72"));

        // 76 corresponds to sm = "Sarah Smith" and "Jason Smitt" but not "Mary Jane"
        assertFalse(checkContains(trie, maryjane, "76"));
        assertTrue(checkContains(trie, sarahsmith, "76"));
        assertTrue(checkContains(trie, jasonsmitt, "76"));
    }

    public void testPutForNameTokens() {
        final SmartDialTrie trie = new SmartDialTrie();
        final ContactNumber jasonfwilliams = new ContactNumber(0, "Jason F. Williams", "0", "0", 1);
        trie.put(jasonfwilliams);

        // 527 corresponds to jas = "Jason"
        assertTrue(checkContains(trie, jasonfwilliams, "527"));
        // 945 corresponds to wil = "Wil"
        assertTrue(checkContains(trie, jasonfwilliams, "945"));
        // 66 doesn't match
        assertFalse(checkContains(trie, jasonfwilliams, "66"));
    }

    public void testPutForInitialMatches() {
        final SmartDialTrie trie = new SmartDialTrie();
        final ContactNumber martinjuniorharry =
                new ContactNumber(0, "Martin Jr Harry", "0", "0", 1);
        trie.put(martinjuniorharry);
        // 654 corresponds to mjh = "(M)artin (J)r (H)arry"
        assertTrue(checkContains(trie, martinjuniorharry, "654"));
        // The reverse (456) does not match (for now)
        assertFalse(checkContains(trie, martinjuniorharry, "456"));
        // 6542 corresponds to mjha = "(M)artin (J)r (Ha)rry"
        assertTrue(checkContains(trie, martinjuniorharry, "6542"));
        // 542 corresponds to jha = "Martin (J)r (Ha)rry"
        assertTrue(checkContains(trie, martinjuniorharry, "542"));
        // 547 doesn't match
        assertFalse(checkContains(trie, martinjuniorharry, "547"));
        // 655 doesn't match
        assertFalse(checkContains(trie, martinjuniorharry, "655"));
        // 653 doesn't match
        assertFalse(checkContains(trie, martinjuniorharry, "653"));
        // 6543 doesn't match
        assertFalse(checkContains(trie, martinjuniorharry, "6543"));
    }

    public void testSeparators() {
        SmartDialTrie trie = new SmartDialTrie();
        String name = "Mcdonald Jamie-Cullum";
        byte[] bytes = trie.toByteArray(name);
        // Make sure the dash is correctly converted to a separator character
        for (int i = 0; i < name.length(); i++) {
            // separators at position 8 and 12
            if (i == 8 || i == 14) {
                assertTrue(bytes[i] == -1);
            } else {
                assertFalse(bytes[i] == -1);
            }
        }
    }

    public void testAccentedCharacters() {
        final SmartDialTrie trie = new SmartDialTrie();
        final ContactNumber reenee = new ContactNumber(0, "Reenée", "0", "0", 1);
        final ContactNumber bronte = new ContactNumber(2, "Brontë", "0", "1", 2);
        trie.put(reenee);
        trie.put(bronte);
        assertTrue(checkContains(trie, reenee, "733633"));
        assertTrue(checkContains(trie, bronte, "276683"));
    }

    public void testPutForNumbers() {
        final SmartDialTrie trie = new SmartDialTrie();
        final ContactNumber contactno1 = new ContactNumber(0, "James", "510-527-2357", "0", 1);
        trie.put(contactno1);
        final ContactNumber contactno2 = new ContactNumber(0, "James", "77212862357", "0", 1);
        trie.put(contactno2);
        final ContactNumber contactno3 = new ContactNumber(0, "James", "+13684976334", "0", 1);
        trie.put(contactno3);
        // all phone numbers belonging to the contact should correspond to it
        assertTrue(checkContains(trie, contactno1, "510"));
        assertFalse(checkContains(trie, contactno1, "511"));
        assertTrue(checkContains(trie, contactno2, "77212862357"));
        assertFalse(checkContains(trie, contactno2, "77212862356"));
        assertTrue(checkContains(trie, contactno3, "1368"));
        assertFalse(checkContains(trie, contactno3, "1367"));

    }

    public void testPutNumbersCountryCode() {
        final SmartDialTrie trie = new SmartDialTrie();
        final ContactNumber contactno1 = new ContactNumber(0, "James", "+13684976334", "0", 1);
        trie.put(contactno1);

        // all phone numbers belonging to the contact should correspond to it
        assertTrue(checkContains(trie, contactno1, "1368"));
        assertTrue(checkContains(trie, contactno1, "368497"));
        assertFalse(checkContains(trie, contactno1, "2368497"));

        final ContactNumber contactno2 = new ContactNumber(0, "Jason", "+65 9177-6930", "0", 1);
        trie.put(contactno2);

        assertTrue(checkContains(trie, contactno2, "6591776930"));
        assertTrue(checkContains(trie, contactno2, "91776930"));
        assertFalse(checkContains(trie, contactno2, "591776930"));

        final ContactNumber contactno3 = new ContactNumber(0, "Mike", "+85212345678", "0", 1);
        trie.put(contactno3);
        assertTrue(checkContains(trie, contactno3, "85212345678"));
        assertTrue(checkContains(trie, contactno3, "12345678"));
        assertFalse(checkContains(trie, contactno2, "5212345678"));

        // Invalid country code, don't try to parse it
        final ContactNumber contactno4 = new ContactNumber(0, "Invalid", "+85112345678", "0", 1);
        trie.put(contactno4);
        assertTrue(checkContains(trie, contactno4, "85112345678"));
        assertFalse(checkContains(trie, contactno4, "12345678"));

        final ContactNumber contactno5 = new ContactNumber(0, "Invalid", "+852", "0", 1);
        // Shouldn't crash
        trie.put(contactno5);
    }

    // Tests special case handling for NANP numbers
    public void testPutNumbersNANP() {

        final SmartDialTrie trie = new SmartDialTrie(true /* formatNanp */);
        // Unformatted number with 1 prefix
        final ContactNumber contactno1 = new ContactNumber(0, "James", "16503337596", "0", 1);
        trie.put(contactno1);

        assertTrue(checkContains(trie, contactno1, "16503337596"));
        assertTrue(checkContains(trie, contactno1, "6503337596"));
        assertTrue(checkContains(trie, contactno1, "3337596"));

        // Number with seperators
        final ContactNumber contactno2 = new ContactNumber(0, "Michael", "5109921234", "0", 1);
        trie.put(contactno2);
        assertTrue(checkContains(trie, contactno2, "5109921234"));
        assertTrue(checkContains(trie, contactno2, "9921234"));

        // Number with area code only + separators
        final ContactNumber contactno3 = new ContactNumber(0, "Jason", "(415)-123-4567", "0", 1);
        trie.put(contactno3);
        assertTrue(checkContains(trie, contactno3, "4151234567"));
        assertTrue(checkContains(trie, contactno3, "1234567"));

        // Number without +1 prefix but is a NANP number
        final ContactNumber contactno4 = new ContactNumber(0, "Mike", "1 510-284-9170", "0", 1);
        trie.put(contactno4);
        assertTrue(checkContains(trie, contactno4, "15102849170"));
        assertTrue(checkContains(trie, contactno4, "5102849170"));
        assertTrue(checkContains(trie, contactno4, "2849170"));

        // Invalid number(has 1 prefix, but is only 10 characters long)
        final ContactNumber contactno5 = new ContactNumber(0, "Invalid", "1-415-123-123", "0", 1);
        trie.put(contactno5);
        // It should still be inserted as is
        assertTrue(checkContains(trie, contactno5, "1415123123"));
        // But the NANP special case handling should not work
        assertFalse(checkContains(trie, contactno5, "415123123"));
        assertFalse(checkContains(trie, contactno5, "123123"));

        // Invalid number(only 9 characters long)
        final ContactNumber contactno6 = new ContactNumber(0, "Invalid2", "415-123-123", "0", 1);
        trie.put(contactno6);
        // It should still be inserted as is
        assertTrue(checkContains(trie, contactno6, "415123123"));
        // But the NANP special case handling should not work
        assertFalse(checkContains(trie, contactno6, "123123"));

        // If user's region is determined to be not in North America, then the NANP number
        // workarounds should not be applied
        final SmartDialTrie trieNonNANP = new SmartDialTrie();

        trieNonNANP.put(contactno3);
        assertTrue(checkContains(trieNonNANP, contactno3, "4151234567"));
        assertFalse(checkContains(trieNonNANP, contactno3, "1234567"));

        trieNonNANP.put(contactno4);
        assertTrue(checkContains(trieNonNANP, contactno4, "15102849170"));
        assertFalse(checkContains(trieNonNANP, contactno4, "5102849170"));
        assertFalse(checkContains(trieNonNANP, contactno4, "2849170"));
    }

    public void testNodeConstructor() {
        final Node n = new Node();
        // Node member variables should not be initialized by default at construction to reduce
        // unnecessary memory usage
        assertEquals(-1, n.getChildrenSize());
        assertNull(n.getChild(5, false));
        assertNull(n.getChild(0, false));
    }

    public void testNodeGetChild() {
        final Node n = new Node();
        // A node shouldn't contain children until getChild(index, true) is called
        assertEquals(-1, n.getChildrenSize());
        final Node child = n.getChild(1, true);
        // A node should always have 10 children once the child array is created
        assertEquals(10, n.getChildrenSize());
        // getChild(index, true) should never return null
        assertNotNull(child);
    }

    public void testNodeAddContact() {
        final Node n = new Node();
        assertNull(n.getContents());
        final ContactNumber contact = new ContactNumber(0, "James", "510-527-2357", "0", 1);
        final ContactNumber contactNotIn = new ContactNumber(2, "Jason Smitt", "0", "2", 3);
        n.add(contact);
        assertTrue(n.getContents().contains(contact));
        assertFalse(n.getContents().contains(contactNotIn));
    }

    private boolean checkContains(SmartDialTrie trie, ContactNumber contact, CharSequence prefix) {
        return trie.getAllWithPrefix(prefix).contains(contact);
    }
}
