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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.util.Log;

import com.android.contacts.common.util.StopWatch;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

/**
 * Cache object used to cache Smart Dial contacts that handles various states of the cache:
 * 1) Cache has been populated
 * 2) Cache task is currently running
 * 3) Cache task failed
 */
public class SmartDialCache {

    public static class Contact {
        public final String displayName;
        public final String lookupKey;
        public final long id;

        public Contact(long id, String displayName, String lookupKey) {
            this.displayName = displayName;
            this.lookupKey = lookupKey;
            this.id = id;
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
                Contacts.HAS_PHONE_NUMBER + "=1";

        String ORDER_BY = Contacts.LAST_TIME_CONTACTED + " DESC";
    }

    // mContactsCache and mCachingStarted need to be volatile because we check for their status
    // in cacheIfNeeded from the UI thread, to decided whether or not to fire up a caching thread.
    private List<Contact> mContactsCache;
    private volatile boolean mNeedsRecache = true;
    private final int mNameDisplayOrder;
    private final Context mContext;
    private final Object mLock = new Object();

    private static final boolean DEBUG = true; // STOPSHIP change to false.

    public SmartDialCache(Context context, int nameDisplayOrder) {
        mNameDisplayOrder = nameDisplayOrder;
        Preconditions.checkNotNull(context, "Context must not be null");
        mContext = context.getApplicationContext();
    }

    /**
     * Performs a database query, iterates through the returned cursor and saves the retrieved
     * contacts to a local cache.
     */
    private void cacheContacts(Context context) {
        synchronized(mLock) {
            // In extremely rare edge cases, getContacts() might be called and start caching
            // between the time mCachingThread is added to the thread pool and it starts
            // running. If so, at this point in time mContactsCache will no longer be null
            // since it is populated by getContacts. We thus no longer have to perform any
            // caching.
            if (mContactsCache != null) {
                if (DEBUG) {
                    Log.d(LOG_TAG, "Contacts already cached");
                }
                return;
            }
            final StopWatch stopWatch = DEBUG ? StopWatch.start("SmartDial Cache") : null;
            final Cursor c = context.getContentResolver().query(ContactQuery.URI,
                    (mNameDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY)
                        ? ContactQuery.PROJECTION : ContactQuery.PROJECTION_ALTERNATIVE,
                    ContactQuery.SELECTION, null,
                    ContactQuery.ORDER_BY);
            if (c == null) {
                Log.w(LOG_TAG, "SmartDial query received null for cursor");
                if (DEBUG) {
                    stopWatch.stopAndLog("Query Failure", 0);
                }
                return;
            }
            try {
                mContactsCache = Lists.newArrayListWithCapacity(c.getCount());
                c.moveToPosition(-1);
                while (c.moveToNext()) {
                    final String displayName = c.getString(ContactQuery.COLUMN_DISPLAY_NAME);
                    final long id = c.getLong(ContactQuery.COLUMN_ID);
                    final String lookupKey = c.getString(ContactQuery.COLUMN_LOOKUP_KEY);
                    mContactsCache.add(new Contact(id, displayName, lookupKey));
                }
            } finally {
                c.close();
                if (DEBUG) {
                    stopWatch.stopAndLog("SmartDial Cache", 0);
                }
            }
        }
    }

    /**
     * Returns the list of cached contacts. If the caching task has not started or been completed,
     * the method blocks till the caching process is complete before returning the full list of
     * cached contacts. This means that this method should not be called from the UI thread.
     *
     * @return List of already cached contacts, or an empty list if the caching failed for any
     * reason.
     */
    public List<Contact> getContacts() {
        synchronized(mLock) {
            if (mContactsCache == null) {
                cacheContacts(mContext);
                mNeedsRecache = false;
                return (mContactsCache == null) ? new ArrayList<Contact>() : mContactsCache;
            } else {
                return mContactsCache;
            }
        }
    }

    /**
     * Only start a new caching task if {@link #mContactsCache} is null and there is no caching
     * task that is currently running
     */
    public void cacheIfNeeded() {
        if (mNeedsRecache) {
            mNeedsRecache = false;
            startCachingThread();
        }
    }

    private void startCachingThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                cacheContacts(mContext);
            }
        }).start();
    }

}
