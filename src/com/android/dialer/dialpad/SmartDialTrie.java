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

import android.text.TextUtils;

import com.android.dialer.dialpad.SmartDialCache.ContactNumber;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import java.util.ArrayList;

/**
 * Prefix trie where the only allowed characters are the characters '0' to '9'. Multiple contacts
 * can occupy the same nodes. Provides functions to get all contacts that lie on or below a node.
 * This is useful for retrieving all contacts that start with that prefix.
 */
public class SmartDialTrie {
    final Node mRoot = new Node();
    private int mSize = 0;
    private final char[] mCharacterMap;

    public SmartDialTrie() {
        // Use the latin letter to digit map by default if none provided
        this(SmartDialNameMatcher.LATIN_LETTERS_TO_DIGITS);
    }

    public SmartDialTrie(char[] charMap) {
        mCharacterMap = charMap;
    }

    /**
     * Returns all contacts in the prefix tree that correspond to this prefix.
     */
    public ArrayList<ContactNumber> getAllWithPrefix(CharSequence prefix) {
        final ArrayList<ContactNumber> result = Lists.newArrayList();
        if (TextUtils.isEmpty(prefix)) {
            return result;
        }
        Node current = mRoot;
        for (int i = 0; i < prefix.length(); i++) {
            char ch = prefix.charAt(i);
            current = current.getChild(ch, false);
            if (current == null) {
                return result;
            }
        }
        // return all contacts that correspond to this prefix
        getAll(current, result);
        return result;
    }

    /**
     * Returns all the contacts located at and under the provided node(including its children)
     */
    private void getAll(Node root, ArrayList<ContactNumber> output) {
        if (root == null) {
            return;
        }
        if (root.getContents() != null) {
            output.addAll(root.getContents());
        }
        for (int i = 0; i < root.getChildrenSize(); i++) {
            getAll(root.getChild(i, false), output);
        }
    }

    /**
     * Adds the display name and phone number of a contact into the prefix trie.
     *
     * @param contact Desired contact to add
     */
    public void put(ContactNumber contact) {
        // Preconvert the display name into a byte array containing indexes to avoid having to
        // remap each character over multiple passes
        putForPrefix(contact, mRoot, toByteArray(contact.displayName), 0,
                contact.displayName.length(), true, true);
        // We don't need to do the same for phone numbers since we only make one pass over them.
        putNumber(contact, contact.phoneNumber);
        mSize++;
    }

    @VisibleForTesting
    /* package */ byte[] toByteArray(CharSequence chars) {
        final int length = chars.length();
        final byte[] result = new byte[length];
        char c;
        for (int i = 0; i < length; i++) {
            c = SmartDialNameMatcher.remapAccentedChars(chars.charAt(i));
            if (c >= 'a' && c <= 'z') {
                result[i] = (byte) (mCharacterMap[c - 'a'] - '0');
            } else {
                result[i] = -1;
            }
        }
        return result;
    }

    /**
     * Puts a phone number and its associated contact into the prefix trie.
     *
     * @param contact - Contact to add to the trie
     * @param phoneNumber - Phone number of the contact
     */
    public void putNumber(ContactNumber contact, String phoneNumber) {
        Node current = mRoot;
        final int length = phoneNumber.length();
        char ch;
        for (int i = 0; i < length; i++) {
            ch = phoneNumber.charAt(i);
            if (ch >= '0' && ch <= '9') {
                current = current.getChild(ch, true);
            }
        }
        current.add(contact);
    }

    /**
     * Place an contact into the trie using at the provided node using the provided prefix. Uses as
     * the input prefix a byte array instead of a CharSequence, as we will be traversing the array
     * multiple times and it is more efficient to pre-convert the prefix into indexes before hand.
     *
     * @param contact Contact to put
     * @param root Root node to use as the starting point
     * @param prefix Sequence of bytes which represent the index at
     * @param start - Starting index of the byte array
     * @param end - Last index(not inclusive) of the byte array
     * @param isFullName If true, prefix will be treated as a full name and recursive calls to add
     *        initial matches as well as name token matches into the trie will be made.
     * @param addInitials If true, recursive calls to add initial matches into the trie will be
     *        made.
     */
    private void putForPrefix(ContactNumber contact, Node root, byte[] prefix, int start, int end,
            boolean isFullName, boolean addInitials) {
        Node current = root;
        Node initialNode = root;
        final int length = end;
        boolean atSeparator = true;
        byte index;
        for (int i = start; i < length; i++) {
            index = prefix[i];
            if (index > -1) {
                if (atSeparator) {
                    atSeparator = false;
                    // encountered a new name token, so add this token into the tree starting from
                    // the root node
                    if (addInitials || isFullName) {
                        if (initialNode != this.mRoot) {
                            if (isFullName) {
                                putForPrefix(contact, this.mRoot, prefix, i, prefix.length, false,
                                        true);
                            }
                            putForPrefix(contact, initialNode,
                                    prefix, i, prefix.length, false, false);
                        }
                    }

                    // Finding a new name token means we find a new initial character as well.
                    // Use initialNode to track the current node at which initial characters match.
                    // E.g. If we are at character m of John W S(m)ith, then the current initial
                    // node is indexed by the characters JWS.
                    initialNode = initialNode.getChild(index, true);
                }
                current = current.getChild(index, true);
            } else {
                atSeparator = true;
            }
        }
        current.add(contact);
    }

    public int size() {
        return mSize;
    }

    @VisibleForTesting
    /* package */ static class Node {
        Node[] mChildren;
        private ArrayList<ContactNumber> mContents;

        public Node() {
            // don't allocate array or contents unless needed
        }

        public int getChildrenSize() {
            if (mChildren == null) {
                return -1;
            }
            return mChildren.length;
        }

        /**
         * Returns a specific child of the current node.
         *
         * @param index Index of the child to return.
         * @param createIfDoesNotExist Whether or not to create a node in that child slot if one
         *        does not already currently exist.
         * @return The existing or newly created child, or {@literal null} if the child does not
         *         exist and createIfDoesNotExist is false.
         */
        public Node getChild(int index, boolean createIfDoesNotExist) {
            if (createIfDoesNotExist) {
                if (mChildren == null) {
                    mChildren = new Node[10];
                }
                if (mChildren[index] == null) {
                    mChildren[index] = new Node();
                }
            } else {
                if (mChildren == null) {
                    return null;
                }
            }
            return mChildren[index];
        }

        /**
         * Same as getChild(int index, boolean createIfDoesNotExist), but takes a character from '0'
         * to '9' as an index.
         */
        public Node getChild(char index, boolean createIfDoesNotExist) {
            return getChild(index - '0', createIfDoesNotExist);
        }

        public void add(ContactNumber contact) {
            if (mContents == null) {
                mContents = Lists.newArrayList();
            }
            mContents.add(contact);
        }

        public ArrayList<ContactNumber> getContents() {
            return mContents;
        }
    }
}
