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

import static com.android.dialer.dialpad.SmartDialAdapter.LOG_TAG;

import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.common.util.StopWatch;
import com.android.dialer.dialpad.SmartDialCache.Contact;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

/**
 * This task searches through the provided cache to return the top 3 contacts(ranked by confidence)
 * that match the query, then passes it back to the {@link SmartDialLoaderCallback} through a
 * callback function.
 */
public class SmartDialLoaderTask extends AsyncTask<String, Integer, List<SmartDialEntry>> {

    public interface SmartDialLoaderCallback {
        void setSmartDialAdapterEntries(List<SmartDialEntry> list);
    }

    static private final boolean DEBUG = true; // STOPSHIP change to false.

    private static final int MAX_ENTRIES = 3;

    private final SmartDialCache mContactsCache;

    private final SmartDialLoaderCallback mCallback;

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
    }

    @Override
    protected List<SmartDialEntry> doInBackground(String... params) {
        return getContactMatches();
    }

    @Override
    protected void onPostExecute(List<SmartDialEntry> result) {
        if (mCallback != null) {
            mCallback.setSmartDialAdapterEntries(result);
        }
    }

    /**
     * Loads all visible contacts with phone numbers and check if their display names match the
     * query.  Return at most {@link #MAX_ENTRIES} {@link SmartDialEntry}'s for the matching
     * contacts.
     */
    private ArrayList<SmartDialEntry> getContactMatches() {
        final List<Contact> cachedContactList = mContactsCache.getContacts();
        // cachedContactList will never be null at this point

        if (DEBUG) {
            Log.d(LOG_TAG, "Size of cache: " + cachedContactList.size());
        }

        final StopWatch stopWatch = DEBUG ? StopWatch.start(LOG_TAG + " Start Match") : null;
        final ArrayList<SmartDialEntry> outList = Lists.newArrayList();

        int count = 0;
        for (int i = 0; i < cachedContactList.size(); i++) {
            final Contact contact = cachedContactList.get(i);
            final String displayName = contact.displayName;

            if (!mNameMatcher.matches(displayName)) {
                continue;
            }
            // Matched; create SmartDialEntry.
            @SuppressWarnings("unchecked")
            final SmartDialEntry entry = new SmartDialEntry(
                     contact.displayName,
                     Contacts.getLookupUri(contact.id, contact.lookupKey),
                     (ArrayList<SmartDialMatchPosition>) mNameMatcher.getMatchPositions().clone()
                     );
            outList.add(entry);
            count++;
            if (count >= MAX_ENTRIES) {
                break;
            }
        }
        if (DEBUG) {
            stopWatch.stopAndLog(LOG_TAG + " Match Complete", 0);
        }
        return outList;
    }
}
