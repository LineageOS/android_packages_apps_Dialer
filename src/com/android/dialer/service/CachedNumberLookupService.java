package com.android.dialer.service;

import android.content.ContentValues;
import android.content.Context;

import com.android.dialer.calllog.ContactInfo;

public interface CachedNumberLookupService {

    public class CachedContactInfo extends ContactInfo {
        public static final int SOURCE_TYPE_DIRECTORY = 1;
        public static final int SOURCE_TYPE_EXTENDED = 2;
        public static final int SOURCE_TYPE_PLACES = 3;
        public static final int SOURCE_TYPE_PROFILE = 4;

        public String sourceName;
        public int    sourceType;
        public int    sourceId;
        public String lookupKey;
    }

    /**
     * Perform a lookup using the cached number lookup service to return contact
     * information stored in the cache that corresponds to the given number.
     *
     * @param context Valid context
     * @param number Phone number to lookup the cache for
     * @return A {@link ContactInfo} containing the contact information if the phone
     * number is found in the cache, {@link ContactInfo#EMPTY} if the phone number was
     * not found in the cache, and null if there was an error when querying the cache.
     */
    public ContactInfo lookupCachedContactFromNumber(Context context, String number);

    public void addContact(Context context, CachedContactInfo info);
}
