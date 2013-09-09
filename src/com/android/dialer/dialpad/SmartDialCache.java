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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.common.util.StopWatch;

import com.android.dialer.dialpad.util.*;
import com.android.dialer.R;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cache object used to cache Smart Dial contacts that handles various states of the cache at the
 * point in time when getContacts() is called
 * 1) Cache is currently empty and there is no caching thread running - getContacts() starts a
 * caching thread and returns the cache when completed
 * 2) The cache is currently empty, but a caching thread has been started - getContacts() waits
 * till the existing caching thread is completed before immediately returning the cache
 * 3) The cache has already been populated, and there is no caching thread running - getContacts()
 * returns the existing cache immediately
 * 4) The cache has already been populated, but there is another caching thread running (due to
 * a forced cache refresh due to content updates - getContacts() returns the existing cache
 * immediately
 */
public class SmartDialCache {
    private static NameLatinizer mNormalizer;

    public static class ContactNumber {
        public final String displayName;
        public final String lookupKey;
        public final String latinizedName;
        public final long id;
        public final int affinity;
        public final String phoneNumber;

        public ContactNumber(long id, String displayName, String phoneNumber, String lookupKey,
                String latinizedName, int affinity) {
            this.displayName = displayName;
            this.lookupKey = lookupKey;
            this.latinizedName = latinizedName;
            this.id = id;
            this.affinity = affinity;
            this.phoneNumber = phoneNumber;
        }
    }

    public static interface PhoneQuery {

       Uri URI = Phone.CONTENT_URI.buildUpon().
               appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
               String.valueOf(Directory.DEFAULT)).
               appendQueryParameter(ContactsContract.REMOVE_DUPLICATE_ENTRIES, "true").
               build();

       final String[] PROJECTION_PRIMARY = new String[] {
            Phone._ID,                          // 0
            Phone.TYPE,                         // 1
            Phone.LABEL,                        // 2
            Phone.NUMBER,                       // 3
            Phone.CONTACT_ID,                   // 4
            Phone.LOOKUP_KEY,                   // 5
            Phone.DISPLAY_NAME_PRIMARY,         // 6
        };

        final String[] PROJECTION_ALTERNATIVE = new String[] {
            Phone._ID,                          // 0
            Phone.TYPE,                         // 1
            Phone.LABEL,                        // 2
            Phone.NUMBER,                       // 3
            Phone.CONTACT_ID,                   // 4
            Phone.LOOKUP_KEY,                   // 5
            Phone.DISPLAY_NAME_ALTERNATIVE,     // 6
        };

        public static final int PHONE_ID           = 0;
        public static final int PHONE_TYPE         = 1;
        public static final int PHONE_LABEL        = 2;
        public static final int PHONE_NUMBER       = 3;
        public static final int PHONE_CONTACT_ID   = 4;
        public static final int PHONE_LOOKUP_KEY   = 5;
        public static final int PHONE_DISPLAY_NAME = 6;

        // Current contacts - those contacted within the last 3 days (in milliseconds)
        final static long LAST_TIME_USED_CURRENT_MS = 3L * 24 * 60 * 60 * 1000;

        // Recent contacts - those contacted within the last 30 days (in milliseconds)
        final static long LAST_TIME_USED_RECENT_MS = 30L * 24 * 60 * 60 * 1000;

        final static String TIME_SINCE_LAST_USED_MS =
                "(? - " + Data.LAST_TIME_USED + ")";

        final static String SORT_BY_DATA_USAGE =
                "(CASE WHEN " + TIME_SINCE_LAST_USED_MS + " < " + LAST_TIME_USED_CURRENT_MS +
                " THEN 0 " +
                " WHEN " + TIME_SINCE_LAST_USED_MS + " < " + LAST_TIME_USED_RECENT_MS +
                " THEN 1 " +
                " ELSE 2 END), " +
                Data.TIMES_USED + " DESC";

        // This sort order is similar to that used by the ContactsProvider when returning a list
        // of frequently called contacts.
        public static final String SORT_ORDER =
                Contacts.STARRED + " DESC, "
                + Data.IS_SUPER_PRIMARY + " DESC, "
                + SORT_BY_DATA_USAGE + ", "
                + Contacts.IN_VISIBLE_GROUP + " DESC, "
                + Contacts.DISPLAY_NAME + ", "
                + Data.CONTACT_ID + ", "
                + Data.IS_PRIMARY + " DESC";
    }

    // Static set used to determine which countries use NANP numbers
    public static Set<String> sNanpCountries = null;

    private SmartDialTrie mContactsCache;
    private static AtomicInteger mCacheStatus;
    private final int mNameDisplayOrder;
    private final Context mContext;
    private final static Object mLock = new Object();

    /** The country code of the user's sim card obtained by calling getSimCountryIso*/
    private static final String PREF_USER_SIM_COUNTRY_CODE =
            "DialtactsActivity_user_sim_country_code";
    private static final String PREF_USER_SIM_COUNTRY_CODE_DEFAULT = null;

    private static String sUserSimCountryCode = PREF_USER_SIM_COUNTRY_CODE_DEFAULT;
    private static boolean sUserInNanpRegion = false;

    public static final int CACHE_NEEDS_RECACHE = 1;
    public static final int CACHE_IN_PROGRESS = 2;
    public static final int CACHE_COMPLETED = 3;

    private static final boolean DEBUG = false;

    private SmartDialCache(Context context, int nameDisplayOrder) {
        mNameDisplayOrder = nameDisplayOrder;
        Preconditions.checkNotNull(context, "Context must not be null");
        mContext = context.getApplicationContext();
        mCacheStatus = new AtomicInteger(CACHE_NEEDS_RECACHE);

        final TelephonyManager manager = (TelephonyManager) context.getSystemService(
                Context.TELEPHONY_SERVICE);
        if (manager != null) {
            sUserSimCountryCode = manager.getSimCountryIso();
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (sUserSimCountryCode != null) {
            // Update shared preferences with the latest country obtained from getSimCountryIso
            prefs.edit().putString(PREF_USER_SIM_COUNTRY_CODE, sUserSimCountryCode).apply();
        } else {
            // Couldn't get the country from getSimCountryIso. Maybe we are in airplane mode.
            // Try to load the settings, if any from SharedPreferences.
            sUserSimCountryCode = prefs.getString(PREF_USER_SIM_COUNTRY_CODE,
                    PREF_USER_SIM_COUNTRY_CODE_DEFAULT);
        }

        sUserInNanpRegion = isCountryNanp(sUserSimCountryCode);

    }

    private static SmartDialCache instance;

    /**
     * Returns an instance of SmartDialCache.
     *
     * @param context A context that provides a valid ContentResolver.
     * @param nameDisplayOrder One of the two name display order integer constants (1 or 2) as saved
     *        in settings under the key
     *        {@link android.provider.ContactsContract.Preferences#DISPLAY_ORDER}.
     * @return An instance of SmartDialCache
     */
    public static synchronized SmartDialCache getInstance(Context context, int nameDisplayOrder) {
        if (instance == null) {
            instance = new SmartDialCache(context, nameDisplayOrder);
            initNormalizer(context);
        }
        return instance;
    }

    private static void initNormalizer(Context context) {
        StringBuilder t9Chars = new StringBuilder();
        StringBuilder t9Digits = new StringBuilder();

        for (String item : context.getResources().getStringArray(R.array.t9_map)) {
            t9Chars.append(item);
            for (int i = 0; i < item.length(); i++) {
                t9Digits.append(item.charAt(0));
            }
        }

        mNormalizer = NameToNumberFactory.create(context,
                t9Chars.toString(), t9Digits.toString());
    }

    /**
     * Performs a database query, iterates through the returned cursor and saves the retrieved
     * contacts to a local cache.
     */
    private void cacheContacts(Context context) {
        mCacheStatus.set(CACHE_IN_PROGRESS);
        synchronized(mLock) {
            if (DEBUG) {
                Log.d(LOG_TAG, "Starting caching thread");
            }
            final StopWatch stopWatch = DEBUG ? StopWatch.start("SmartDial Cache") : null;
            final String millis = String.valueOf(System.currentTimeMillis());
            final Cursor c = context.getContentResolver().query(PhoneQuery.URI,
                    (mNameDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY)
                        ? PhoneQuery.PROJECTION_PRIMARY : PhoneQuery.PROJECTION_ALTERNATIVE,
                    null, new String[] {millis, millis},
                    PhoneQuery.SORT_ORDER);
            if (DEBUG) {
                stopWatch.lap("SmartDial query complete");
            }
            if (c == null) {
                Log.w(LOG_TAG, "SmartDial query received null for cursor");
                if (DEBUG) {
                    stopWatch.stopAndLog("SmartDial query received null for cursor", 0);
                }
                mCacheStatus.getAndSet(CACHE_NEEDS_RECACHE);
                return;
            }
            final SmartDialTrie cache = new SmartDialTrie(
                    SmartDialNameMatcher.LATIN_LETTERS_TO_DIGITS, sUserInNanpRegion);
            try {
                c.moveToPosition(-1);
                int affinityCount = 0;
                while (c.moveToNext()) {
                    final String displayName = c.getString(PhoneQuery.PHONE_DISPLAY_NAME);
                    final String phoneNumber = c.getString(PhoneQuery.PHONE_NUMBER);
                    final long id = c.getLong(PhoneQuery.PHONE_CONTACT_ID);
                    final String lookupKey = c.getString(PhoneQuery.PHONE_LOOKUP_KEY);
                    if (mNormalizer != null) {
                        String T9Names[] = mNormalizer.getNameLatinizations(displayName);
                        for (String T9Name : T9Names) {
                            cache.put(new ContactNumber(id, displayName, phoneNumber, lookupKey,
                                    T9Name, affinityCount));
                            affinityCount++;
                        }
                    }
                    cache.put(new ContactNumber(id, displayName, phoneNumber, lookupKey, null,
                            affinityCount));
                    affinityCount++;
                }
            } finally {
                c.close();
                mContactsCache = cache;
                if (DEBUG) {
                    stopWatch.stopAndLog("SmartDial caching completed", 0);
                }
            }
        }
        if (DEBUG) {
            Log.d(LOG_TAG, "Caching thread completed");
        }
        mCacheStatus.getAndSet(CACHE_COMPLETED);
    }

    /**
     * Returns the list of cached contacts. This is blocking so it should not be called from the UI
     * thread. There are 4 possible scenarios:
     *
     * 1) Cache is currently empty and there is no caching thread running - getContacts() starts a
     * caching thread and returns the cache when completed
     * 2) The cache is currently empty, but a caching thread has been started - getContacts() waits
     * till the existing caching thread is completed before immediately returning the cache
     * 3) The cache has already been populated, and there is no caching thread running -
     * getContacts() returns the existing cache immediately
     * 4) The cache has already been populated, but there is another caching thread running (due to
     * a forced cache refresh due to content updates - getContacts() returns the existing cache
     * immediately
     *
     * @return List of already cached contacts, or an empty list if the caching failed for any
     * reason.
     */
    public SmartDialTrie getContacts() {
        // Either scenario 3 or 4 - This means just go ahead and return the existing cache
        // immediately even if there is a caching thread currently running. We are guaranteed to
        // have the newest value of mContactsCache at this point because it is volatile.
        if (mContactsCache != null) {
            return mContactsCache;
        }
        // At this point we are forced to wait for cacheContacts to complete in another thread(if
        // one currently exists) because of mLock.
        synchronized(mLock) {
            // If mContactsCache is still null at this point, either there was never any caching
            // process running, or it failed (Scenario 1). If so, just go ahead and try to cache
            // the contacts again.
            if (mContactsCache == null) {
                cacheContacts(mContext);
                return (mContactsCache == null) ? new SmartDialTrie() : mContactsCache;
            } else {
                // After waiting for the lock on mLock to be released, mContactsCache is now
                // non-null due to the completion of the caching thread (Scenario 2). Go ahead
                // and return the existing cache.
                return mContactsCache;
            }
        }
    }

    /**
     * Cache contacts only if there is a need to (forced cache refresh or no attempt to cache yet).
     * This method is called in 2 places: whenever the DialpadFragment comes into view, and in
     * onResume.
     *
     * @param forceRecache If true, force a cache refresh.
     */

    public void cacheIfNeeded(boolean forceRecache) {
        if (DEBUG) {
            Log.d("SmartDial", "cacheIfNeeded called with " + String.valueOf(forceRecache));
        }
        if (mCacheStatus.get() == CACHE_IN_PROGRESS) {
            return;
        }
        if (forceRecache || mCacheStatus.get() == CACHE_NEEDS_RECACHE) {
            // Because this method can be possibly be called multiple times in rapid succession,
            // set the cache status even before starting a caching thread to avoid unnecessarily
            // spawning extra threads.
            mCacheStatus.set(CACHE_IN_PROGRESS);
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

    public static class ContactAffinityComparator implements Comparator<ContactNumber> {
        @Override
        public int compare(ContactNumber lhs, ContactNumber rhs) {
            // Smaller affinity is better because they are numbered in ascending order in
            // the order the contacts were returned from the ContactsProvider (sorted by
            // frequency of use and time last used
            return Integer.compare(lhs.affinity, rhs.affinity);
        }

    }

    public boolean getUserInNanpRegion() {
        return sUserInNanpRegion;
    }

    /**
     * Indicates whether the given country uses NANP numbers
     *
     * @param country ISO 3166 country code (case doesn't matter)
     * @return True if country uses NANP numbers (e.g. US, Canada), false otherwise
     */
    @VisibleForTesting
    static boolean isCountryNanp(String country) {
        if (TextUtils.isEmpty(country)) {
            return false;
        }
        if (sNanpCountries == null) {
            sNanpCountries = initNanpCountries();
        }
        return sNanpCountries.contains(country.toUpperCase());
    }

    private static Set<String> initNanpCountries() {
        final HashSet<String> result = new HashSet<String>();
        result.add("US"); // United States
        result.add("CA"); // Canada
        result.add("AS"); // American Samoa
        result.add("AI"); // Anguilla
        result.add("AG"); // Antigua and Barbuda
        result.add("BS"); // Bahamas
        result.add("BB"); // Barbados
        result.add("BM"); // Bermuda
        result.add("VG"); // British Virgin Islands
        result.add("KY"); // Cayman Islands
        result.add("DM"); // Dominica
        result.add("DO"); // Dominican Republic
        result.add("GD"); // Grenada
        result.add("GU"); // Guam
        result.add("JM"); // Jamaica
        result.add("PR"); // Puerto Rico
        result.add("MS"); // Montserrat
        result.add("MP"); // Northern Mariana Islands
        result.add("KN"); // Saint Kitts and Nevis
        result.add("LC"); // Saint Lucia
        result.add("VC"); // Saint Vincent and the Grenadines
        result.add("TT"); // Trinidad and Tobago
        result.add("TC"); // Turks and Caicos Islands
        result.add("VI"); // U.S. Virgin Islands
        return result;
    }
}
