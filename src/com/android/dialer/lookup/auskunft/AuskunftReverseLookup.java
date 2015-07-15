/**
 * Copyright (c) 2015, The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dialer.lookup.auskunft;

import android.content.Context;

import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.lookup.ReverseLookup;

public class AuskunftReverseLookup extends ReverseLookup {
    private static final String TAG = AuskunftReverseLookup.class.getSimpleName();

    public AuskunftReverseLookup(Context context) {
    }

    private static final String[] NUMBER_PREFIXES_MOBILE = {
        "+43650", "+43660", "+43664", "+43665",
        "+43670", "+43676", "+43677", "+43678",
        "+43680", "+43681", "+43688", "+43699"
    };

    // a typical international formatted Austrian number has a minimal length of 10 characters
    // e.g. +432236401, this is used for the heuristic reverse lookup of call-through numbers
    private static final int MIN_NUMBER_LENGTH = 10;

    @Override
    public ContactInfo lookupNumber(Context context,
            String normalizedNumber, String formattedNumber) {
        // only Austrian numbers are supported
        if (normalizedNumber.startsWith("+") && !normalizedNumber.startsWith("+43")) {
            return null;
        }

        // query the API
        ContactInfo[] infos = AuskunftApi.query(normalizedNumber);

        // return result immediately for mobile numbers because we won't have any call-through
        // numbers here and may only get other invalid entries if looking any further
        for (String number_prefix_mobile : NUMBER_PREFIXES_MOBILE) {
            if (normalizedNumber.startsWith(number_prefix_mobile)) {
                return (infos.length != 0) ? infos[0] : null;
            }
        }

        // query the lookup provider heuristically in a loop, removing the last character of the
        // number gradually to find the base number of unlisted call-through numbers, ideally we
        // only need 1 or 2 queries because most of the numbers are either listed or not that long
        while (infos.length == 0 && normalizedNumber.length() >= MIN_NUMBER_LENGTH) {
            // remove the last character of the normalized number string and do a lookup
            normalizedNumber = normalizedNumber.substring(0, normalizedNumber.length() - 1);
            infos = AuskunftApi.query(normalizedNumber);
        }

        return (infos.length != 0) ? infos[0] : null;
    }
}
