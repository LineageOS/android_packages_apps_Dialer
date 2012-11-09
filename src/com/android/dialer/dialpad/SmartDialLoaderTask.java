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

import com.google.common.collect.Lists;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.common.util.StopWatch;

import java.util.ArrayList;
import java.util.List;

/**
 * AsyncTask that performs one of two functions depending on which constructor is used.
 * If {@link #SmartDialLoaderTask(Context context, int nameDisplayOrder)} is used, the task
 * caches all contacts with a phone number into the static variable {@link #sContactsCache}.
 * If {@link #SmartDialLoaderTask(SmartDialLoaderCallback callback, String query)} is used, the
 * task searches through the cache to return the top 3 contacts(ranked by confidence) that match
 * the query, then passes it back to the {@link SmartDialLoaderCallback} through a callback
 * function.
 */
// TODO: Make the cache a singleton class and refactor to fix possible concurrency issues in the
// future
public class SmartDialLoaderTask extends AsyncTask<String, Integer, List<SmartDialEntry>> {

    private class Contact {
        final String mDisplayName;
        final String mLookupKey;
        final long mId;

        public Contact(long id, String displayName, String lookupKey) {
            mDisplayName = displayName;
            mLookupKey = lookupKey;
            mId = id;
        }
    }

    public interface SmartDialLoaderCallback {
        void setSmartDialAdapterEntries(List<SmartDialEntry> list);
    }

    static private final boolean DEBUG = true; // STOPSHIP change to false.

    private static final int MAX_ENTRIES = 3;

    private static List<Contact> sContactsCache;

    private final boolean mCacheOnly;

    private final SmartDialLoaderCallback mCallback;

    private final Context mContext;
    /**
     * See {@link ContactsPreferences#getDisplayOrder()}.
     * {@link ContactsContract.Preferences#DISPLAY_ORDER_PRIMARY} (first name first)
     * {@link ContactsContract.Preferences#DISPLAY_ORDER_ALTERNATIVE} (last name first)
     */
    private final int mNameDisplayOrder;

    private final SmartDialNameMatcher mNameMatcher;

    // cache only constructor
    private SmartDialLoaderTask(Context context, int nameDisplayOrder) {
        this.mNameDisplayOrder = nameDisplayOrder;
        this.mContext = context;
        // we're just caching contacts so no need to initialize a SmartDialNameMatcher or callback
        this.mNameMatcher = null;
        this.mCallback = null;
        this.mCacheOnly = true;
    }

    public SmartDialLoaderTask(SmartDialLoaderCallback callback, String query) {
        this.mCallback = callback;
        this.mContext = null;
        this.mCacheOnly = false;
        this.mNameDisplayOrder = 0;
        this.mNameMatcher = new SmartDialNameMatcher(PhoneNumberUtils.normalizeNumber(query));
    }

    @Override
    protected List<SmartDialEntry> doInBackground(String... params) {
        if (mCacheOnly) {
            cacheContacts();
            return Lists.newArrayList();
        }

        return getContactMatches();
    }

    @Override
    protected void onPostExecute(List<SmartDialEntry> result) {
        if (mCallback != null) {
            mCallback.setSmartDialAdapterEntries(result);
        }
    }

    /** Query used for loadByContactName */
    private interface ContactQuery {
        Uri URI = Contacts.CONTENT_URI.buildUpon()
                // Visible contact only
                //.appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY, "0")
                .build();
        String[] PROJECTION = new String[] {
                Contacts._ID,
                Contacts.DISPLAY_NAME,
                Contacts.LOOKUP_KEY
            };
        String[] PROJECTION_ALTERNATIVE = new String[] {
                Contacts._ID,
                Contacts.DISPLAY_NAME_ALTERNATIVE,
                Contacts.LOOKUP_KEY
            };

        int COLUMN_ID = 0;
        int COLUMN_DISPLAY_NAME = 1;
        int COLUMN_LOOKUP_KEY = 2;

        String SELECTION =
                //Contacts.IN_VISIBLE_GROUP + "=1 and " +
                Contacts.HAS_PHONE_NUMBER + "=1";

        String ORDER_BY = Contacts.LAST_TIME_CONTACTED + " DESC";
    }

    public static void startCacheContactsTaskIfNeeded(Context context, int displayOrder) {
        if (sContactsCache != null) {
            // contacts have already been cached, just return
            return;
        }
        final SmartDialLoaderTask task =
                new SmartDialLoaderTask(context, displayOrder);
        task.execute();
    }

    /**
     * Caches the contacts into an in memory array list. This is called once at startup and should
     * not be cancelled.
     */
    private void cacheContacts() {
        final StopWatch stopWatch = DEBUG ? StopWatch.start("SmartDial Cache") : null;
        if (sContactsCache != null) {
            // contacts have already been cached, just return
            stopWatch.stopAndLog("SmartDial Already Cached", 0);
            return;
        }

        final Cursor c = mContext.getContentResolver().query(ContactQuery.URI,
                (mNameDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY)
                    ? ContactQuery.PROJECTION : ContactQuery.PROJECTION_ALTERNATIVE,
                ContactQuery.SELECTION, null,
                ContactQuery.ORDER_BY);
        if (c == null) {
            stopWatch.stopAndLog("Query Failuregi", 0);
            return;
        }
        sContactsCache = Lists.newArrayListWithCapacity(c.getCount());
        try {
            c.moveToPosition(-1);
            while (c.moveToNext()) {
                final String displayName = c.getString(ContactQuery.COLUMN_DISPLAY_NAME);
                final long id = c.getLong(ContactQuery.COLUMN_ID);
                final String lookupKey = c.getString(ContactQuery.COLUMN_LOOKUP_KEY);
                sContactsCache.add(new Contact(id, displayName, lookupKey));
            }
        } finally {
            c.close();
            if (DEBUG) {
                stopWatch.stopAndLog("SmartDial Cache", 0);
            }
        }
    }

    /**
     * Loads all visible contacts with phone numbers and check if their display names match the
     * query.  Return at most {@link #MAX_ENTRIES} {@link SmartDialEntry}'s for the matching
     * contacts.
     */
    private ArrayList<SmartDialEntry> getContactMatches() {
        final StopWatch stopWatch = DEBUG ? StopWatch.start(LOG_TAG + " Start Match") : null;
        if (sContactsCache == null) {
            // contacts should have been cached by this point in time, but in case they
            // are not, we go ahead and cache them into memory.
            if (DEBUG) {
                Log.d(LOG_TAG, "empty cache");
            }
            cacheContacts();
            // TODO: if sContactsCache is still null at this point we should try to recache
        }
        if (DEBUG) {
            Log.d(LOG_TAG, "Size of cache: " + sContactsCache.size());
        }
        final ArrayList<SmartDialEntry> outList = Lists.newArrayList();
        if (sContactsCache == null) {
            return outList;
        }
        int count = 0;
        for (int i = 0; i < sContactsCache.size(); i++) {
            final Contact contact = sContactsCache.get(i);
            final String displayName = contact.mDisplayName;

            if (!mNameMatcher.matches(displayName)) {
                continue;
            }
            // Matched; create SmartDialEntry.
            @SuppressWarnings("unchecked")
            final SmartDialEntry entry = new SmartDialEntry(
                     contact.mDisplayName,
                     Contacts.getLookupUri(contact.mId, contact.mLookupKey),
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
