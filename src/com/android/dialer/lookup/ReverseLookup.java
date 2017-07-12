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
import com.android.dialer.lookup.auskunft.AuskunftReverseLookup;
import com.android.dialer.lookup.cyngn.CyngnChineseReverseLookup;
import com.android.dialer.lookup.dastelefonbuch.TelefonbuchReverseLookup;
import com.android.dialer.lookup.opencnam.OpenCnamReverseLookup;
import com.android.dialer.lookup.yellowpages.YellowPagesReverseLookup;
import com.android.dialer.lookup.zabasearch.ZabaSearchReverseLookup;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;

public abstract class ReverseLookup {
    private static final String TAG = ReverseLookup.class.getSimpleName();

    private static ReverseLookup INSTANCE = null;

    public static ReverseLookup getInstance(Context context) {
        String provider = LookupSettings.getReverseLookupProvider(context);

        if (INSTANCE == null || !isInstance(provider)) {
            Log.d(TAG, "Chosen reverse lookup provider: " + provider);

            if (provider.equals(LookupSettings.RLP_OPENCNAM)) {
                INSTANCE = new OpenCnamReverseLookup(context);
            } else if (provider.equals(LookupSettings.RLP_YELLOWPAGES)
                    || provider.equals(LookupSettings.RLP_YELLOWPAGES_CA)) {
                INSTANCE = new YellowPagesReverseLookup(context);
            } else if (provider.equals(LookupSettings.RLP_ZABASEARCH)) {
                INSTANCE = new ZabaSearchReverseLookup(context);
            } else if (provider.equals(LookupSettings.RLP_CYNGN_CHINESE)) {
                INSTANCE = new CyngnChineseReverseLookup(context);
            } else if (provider.equals(LookupSettings.RLP_DASTELEFONBUCH)) {
                INSTANCE = new TelefonbuchReverseLookup(context);
            } else if (provider.equals(LookupSettings.RLP_AUSKUNFT)) {
                INSTANCE = new AuskunftReverseLookup(context);
            }
        }

        return INSTANCE;
    }

    private static boolean isInstance(String provider) {
        if (provider.equals(LookupSettings.RLP_OPENCNAM)
                && INSTANCE instanceof OpenCnamReverseLookup) {
            return true;
        } else if ((provider.equals(LookupSettings.RLP_YELLOWPAGES)
                || provider.equals(LookupSettings.RLP_YELLOWPAGES_CA))
                && INSTANCE instanceof YellowPagesReverseLookup) {
            return true;
        } else if (provider.equals(LookupSettings.RLP_ZABASEARCH)
                && INSTANCE instanceof ZabaSearchReverseLookup) {
            return true;
        } else if (provider.equals(LookupSettings.RLP_CYNGN_CHINESE)
                && INSTANCE instanceof CyngnChineseReverseLookup) {
            return true;
        } else if (provider.equals(LookupSettings.RLP_DASTELEFONBUCH)
                && INSTANCE instanceof TelefonbuchReverseLookup) {
            return true;
        } else if (provider.equals(LookupSettings.RLP_AUSKUNFT)
                && INSTANCE instanceof AuskunftReverseLookup) {
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
     */
    public Bitmap lookupImage(Context context, Uri uri) {
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
    public abstract ContactInfo lookupNumber(Context context,
            String normalizedNumber, String formattedNumber) throws IOException;
}
