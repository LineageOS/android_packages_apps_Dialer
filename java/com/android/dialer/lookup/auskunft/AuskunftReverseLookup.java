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

import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.lookup.ContactBuilder;
import com.android.dialer.lookup.ReverseLookup;

import java.io.IOException;
import java.util.List;

public class AuskunftReverseLookup extends ReverseLookup {
    private static final String TAG = AuskunftReverseLookup.class.getSimpleName();

    public AuskunftReverseLookup(Context context) {
    }

    @Override
    public ContactInfo lookupNumber(Context context, String normalizedNumber,
            String formattedNumber) throws IOException {
        // only Austrian numbers are supported
        if (normalizedNumber.startsWith("+") && !normalizedNumber.startsWith("+43")) {
            return null;
        }

        // query the API and return null if nothing found or general error
        List<ContactInfo> infos = AuskunftApi.query(normalizedNumber, ContactBuilder.REVERSE_LOOKUP,
                normalizedNumber, formattedNumber);
        return (infos != null && !infos.isEmpty()) ? infos.get(0) : null;
    }
}
