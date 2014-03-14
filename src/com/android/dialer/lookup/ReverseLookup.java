/*
 * Copyright (C) 2014 Xiao-Long Chen <chillermillerlong@hotmail.com>
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

package com.android.dialer.lookup;

import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.lookup.opencnam.OpenCnamReverseLookup;
import com.android.dialer.lookup.whitepages.WhitePagesReverseLookup;
import com.android.dialer.lookup.yellowpages.YellowPagesReverseLookup;
import com.android.dialer.lookup.zabasearch.ZabaSearchReverseLookup;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

public abstract class ReverseLookup {
    private static final String TAG = ReverseLookup.class.getSimpleName();

    private static ReverseLookup INSTANCE = null;

    public static ReverseLookup getInstance(Context context) {
        String provider = LookupSettings.getReverseLookupProvider(context);

        if (INSTANCE == null || !isInstance(provider)) {
            Log.d(TAG, "Chosen reverse lookup provider: " + provider);

            if (provider.equals(LookupSettings.RLP_OPENCNAM)) {
                INSTANCE = new OpenCnamReverseLookup(context);
            } else if (provider.equals(LookupSettings.RLP_WHITEPAGES)
                    || provider.equals(LookupSettings.RLP_WHITEPAGES_CA)) {
                INSTANCE = new WhitePagesReverseLookup(context);
            } else if (provider.equals(LookupSettings.RLP_YELLOWPAGES)
                    || provider.equals(LookupSettings.RLP_YELLOWPAGES_CA)) {
                INSTANCE = new YellowPagesReverseLookup(context);
            } else if (provider.equals(LookupSettings.RLP_ZABASEARCH)) {
                INSTANCE = new ZabaSearchReverseLookup(context);
            }
        }

        return INSTANCE;
    }

    private static boolean isInstance(String provider) {
        if (provider.equals(LookupSettings.RLP_OPENCNAM)
                && INSTANCE instanceof OpenCnamReverseLookup) {
            return true;
        } else if ((provider.equals(LookupSettings.RLP_WHITEPAGES)
                || provider.equals(LookupSettings.RLP_WHITEPAGES_CA))
                && INSTANCE instanceof WhitePagesReverseLookup) {
            return true;
        } else if ((provider.equals(LookupSettings.RLP_YELLOWPAGES)
                || provider.equals(LookupSettings.RLP_YELLOWPAGES_CA))
                && INSTANCE instanceof YellowPagesReverseLookup) {
            return true;
        } else if (provider.equals(LookupSettings.RLP_ZABASEARCH)
                && INSTANCE instanceof ZabaSearchReverseLookup) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Lookup image
     *
     * @param context The application context
     * @param uri The image URI
     * @param data Extra data (a authentication token, perhaps)
     */
    public Bitmap lookupImage(Context context, Uri uri, Object data) {
        return null;
    }

    /**
     * Perform phone number lookup.
     *
     * @param context The application context
     * @param normalizedNumber The normalized phone number
     * @param formattedNumber The formatted phone number
     * @return The phone number info object
     */
    public abstract Pair<ContactInfo, Object> lookupNumber(Context context,
            String normalizedNumber, String formattedNumber);
}
