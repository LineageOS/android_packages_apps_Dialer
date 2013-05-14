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

import static com.android.dialer.dialpad.SmartDialController.LOG_TAG;

import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.common.util.StopWatch;
import com.android.dialer.dialpad.SmartDialCache.ContactNumber;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This task searches through the provided cache to return the top 3 contacts(ranked by confidence)
 * that match the query, then passes it back to the {@link SmartDialLoaderCallback} through a
 * callback function.
 */
public class SmartDialLoaderTask extends AsyncTask<String, Integer, List<SmartDialEntry>> {

    public interface SmartDialLoaderCallback {
        void setSmartDialAdapterEntries(List<SmartDialEntry> list, String query);
    }

    static private final boolean DEBUG = false;

    private static final int MAX_ENTRIES = 3;

    private final SmartDialCache mContactsCache;

    private final SmartDialLoaderCallback mCallback;

    private final String mQuery;

    /**
     * See {@link ContactsPreferences#getDisplayOrder()}.
     * {@link ContactsContract.Preferences#DISPLAY_ORDER_PRIMARY} (first name first)
     * {@link ContactsContract.Preferences#DISPLAY_ORDER_ALTERNATIVE} (last name first)
     */
    private final SmartDialNameMatcher mNameMatcher;

    public SmartDialLoaderTask(SmartDialLoaderCallback callback, String query,
            SmartDialCache cache) {
        this.mCallback = callback;
        this.mNameMatcher = new SmartDialNameMatcher(PhoneNumberUtils.normalizeNumber(query));
        this.mContactsCache = cache;
        this.mQuery = query;
    }

    @Override
    protected List<SmartDialEntry> doInBackground(String... params) {
        return getContactMatches();
    }

    @Override
    protected void onPostExecute(List<SmartDialEntry> result) {
        if (mCallback != null) {
            mCallback.setSmartDialAdapterEntries(result, mQuery);
        }
    }

    /**
     * Loads all visible contacts with phone numbers and check if their display names match the
     * query.  Return at most {@link #MAX_ENTRIES} {@link SmartDialEntry}'s for the matching
     * contacts.
     */
    private ArrayList<SmartDialEntry> getContactMatches() {

        final SmartDialTrie trie = mContactsCache.getContacts();
        final boolean matchNanp = mContactsCache.getUserInNanpRegion();

        if (DEBUG) {
            Log.d(LOG_TAG, "Size of cache: " + trie.size());
        }

        final StopWatch stopWatch = DEBUG ? StopWatch.start("Start Match") : null;
        final ArrayList<ContactNumber> allMatches = trie.getAllWithPrefix(mNameMatcher.getQuery());
        if (DEBUG) {
            stopWatch.lap("Find matches");
        }
        // Sort matches in order of ascending contact affinity (lower is better)
        Collections.sort(allMatches, new SmartDialCache.ContactAffinityComparator());
        if (DEBUG) {
            stopWatch.lap("Sort");
        }
        final Set<ContactMatch> duplicates = new HashSet<ContactMatch>();
        final ArrayList<SmartDialEntry> candidates = Lists.newArrayList();
        for (ContactNumber contact : allMatches) {
            final ContactMatch contactMatch = new ContactMatch(contact.lookupKey, contact.id);
            // Don't add multiple contact numbers from the same contact into suggestions if
            // there are multiple matches. Instead, just keep the highest priority number
            // instead.
            if (duplicates.contains(contactMatch)) {
                continue;
            }
            duplicates.add(contactMatch);
            final boolean matches = mNameMatcher.matches(contact.displayName);

            candidates.add(new SmartDialEntry(
                    contact.displayName,
                    Contacts.getLookupUri(contact.id, contact.lookupKey),
                    contact.phoneNumber,
                    mNameMatcher.getMatchPositions(),
                    SmartDialNameMatcher.matchesNumber(contact.phoneNumber,
                            mNameMatcher.getQuery(), matchNanp)
                    ));
            if (candidates.size() >= MAX_ENTRIES) {
                break;
            }
        }
        if (DEBUG) {
            stopWatch.stopAndLog(LOG_TAG + " Match Complete", 0);
        }
        return candidates;
    }

    private class ContactMatch {
        public final String lookupKey;
        public final long id;

        public ContactMatch(String lookupKey, long id) {
            this.lookupKey = lookupKey;
            this.id = id;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(lookupKey, id);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (object instanceof ContactMatch) {
                ContactMatch that = (ContactMatch) object;
                return Objects.equal(this.lookupKey, that.lookupKey)
                        && Objects.equal(this.id, that.id);
            }
            return false;
        }
    }
}
