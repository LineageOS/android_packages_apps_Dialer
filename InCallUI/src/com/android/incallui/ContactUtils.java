/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.incallui;

import android.content.Context;
import android.location.Address;
import android.util.Pair;

import com.android.incalluibind.ObjectFactory;

import java.util.Calendar;
import java.util.List;

/**
 * Utility functions to help manipulate contact data.
 */
public abstract class ContactUtils {
    protected Context mContext;

    public static ContactUtils getInstance(Context context) {
        return ObjectFactory.getContactUtilsInstance(context);
    }

    protected ContactUtils(Context context) {
        mContext = context;
    }

    public interface Listener {
        public void onContactInteractionsFound(Address address,
                List<Pair<Calendar, Calendar>> openingHours);
    }

    public abstract boolean retrieveContactInteractionsFromLookupKey(String lookupKey,
            Listener listener);
}
