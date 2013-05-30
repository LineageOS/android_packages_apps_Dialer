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

import android.content.Context;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.telephony.PhoneNumberUtils;

import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.common.util.StopWatch;
import com.android.dialer.database.DialerDatabaseHelper;
import com.android.dialer.database.DialerDatabaseHelper.ContactNumber;

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
        void setSmartDialAdapterEntries(List<SmartDialEntry> list, String query);
    }

    static private final boolean DEBUG = false;

    private final SmartDialLoaderCallback mCallback;

    private final DialerDatabaseHelper mDialerDatabaseHelper;

    private final String mQuery;

    private final SmartDialNameMatcher mNameMatcher;

    public SmartDialLoaderTask(SmartDialLoaderCallback callback, String query, Context context) {
        this.mCallback = callback;
        mDialerDatabaseHelper = DialerDatabaseHelper.getInstance(context);
        this.mQuery = query;
        this.mNameMatcher = new SmartDialNameMatcher(PhoneNumberUtils.normalizeNumber(query),
                SmartDialPrefix.getMap());
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
     * Loads top visible contacts with phone numbers and check if their display names match the
     * query.
     */
    private ArrayList<SmartDialEntry> getContactMatches() {

        final StopWatch stopWatch = DEBUG ? StopWatch.start("Start Match") : null;

        final ArrayList<ContactNumber> allMatches = mDialerDatabaseHelper.getLooseMatches(mQuery,
                mNameMatcher);
        if (DEBUG) {
            stopWatch.lap("Find matches");
        }

        final ArrayList<SmartDialEntry> candidates = Lists.newArrayList();
        for (ContactNumber contact : allMatches) {
            final boolean matches = mNameMatcher.matches(contact.displayName);
            candidates.add(new SmartDialEntry(
                    contact.displayName,
                    Contacts.getLookupUri(contact.id, contact.lookupKey),
                    contact.phoneNumber,
                    mNameMatcher.getMatchPositions(),
                    mNameMatcher.matchesNumber(contact.phoneNumber, mNameMatcher.getQuery())
                    ));
        }
        if (DEBUG) {
            stopWatch.stopAndLog(LOG_TAG + " Match Complete", 0);
        }
        return candidates;
    }
}
