package com.android.dialer.service;

import android.content.Context;

import com.android.dialer.calllog.ContactInfo;

public interface CachedNumberLookupService {
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
}
