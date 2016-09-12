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
import com.android.dialer.lookup.google.GoogleForwardLookup;
import com.android.dialer.lookup.openstreetmap.OpenStreetMapForwardLookup;

import android.content.Context;
import android.location.Location;
import android.util.Log;

public abstract class ForwardLookup {
    private static final String TAG = ForwardLookup.class.getSimpleName();

    private static ForwardLookup INSTANCE = null;

    public static ForwardLookup getInstance(Context context) {
        String provider = LookupSettings.getForwardLookupProvider(context);

        if (INSTANCE == null || !isInstance(provider)) {
            Log.d(TAG, "Chosen forward lookup provider: " + provider);

            if (provider.equals(LookupSettings.FLP_GOOGLE)) {
                INSTANCE = new GoogleForwardLookup(context);
            } else if (provider.equals(LookupSettings.FLP_OPENSTREETMAP)) {
                INSTANCE = new OpenStreetMapForwardLookup(context);
            }
        }

        return INSTANCE;
    }

    private static boolean isInstance(String provider) {
        if (provider.equals(LookupSettings.FLP_GOOGLE)
                && INSTANCE instanceof GoogleForwardLookup) {
            return true;
        } else if (provider.equals(LookupSettings.FLP_OPENSTREETMAP)
                && INSTANCE instanceof OpenStreetMapForwardLookup) {
            return true;
        } else {
            return false;
        }
    }

    public abstract ContactInfo[] lookup(Context context,
            String filter, Location lastLocation);
}
