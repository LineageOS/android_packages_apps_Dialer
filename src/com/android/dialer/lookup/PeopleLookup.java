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
import com.android.dialer.lookup.auskunft.AuskunftPeopleLookup;

import android.content.Context;
import android.util.Log;

public abstract class PeopleLookup {
    private static final String TAG = PeopleLookup.class.getSimpleName();

    private static PeopleLookup INSTANCE = null;

    public static PeopleLookup getInstance(Context context) {
        String provider = LookupSettings.getPeopleLookupProvider(context);

        if (INSTANCE == null || !isInstance(provider)) {
            Log.d(TAG, "Chosen people lookup provider: " + provider);

            if (provider.equals(LookupSettings.PLP_AUSKUNFT)) {
                INSTANCE = new AuskunftPeopleLookup(context);
            }
        }

        return INSTANCE;
    }

    private static boolean isInstance(String provider) {
        if (provider.equals(LookupSettings.PLP_AUSKUNFT)
                && INSTANCE instanceof AuskunftPeopleLookup) {
            return true;
        } else {
            return false;
        }
    }

    public abstract ContactInfo[] lookup(Context context,
            String filter);
}
