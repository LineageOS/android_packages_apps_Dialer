/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2013 Android Open Kang Project
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

package com.android.dialer.callstats;

import android.provider.CallLog.Calls;

public class CallStatsQuery {

    public static final String[] _PROJECTION = new String[] {
            Calls._ID, // 0
            Calls.NUMBER, // 1
            Calls.DATE, // 2
            Calls.DURATION, // 3
            Calls.TYPE, // 4
            Calls.COUNTRY_ISO, // 5
            Calls.GEOCODED_LOCATION, // 6
            Calls.CACHED_NAME, // 7
            Calls.CACHED_NUMBER_TYPE, // 8
            Calls.CACHED_NUMBER_LABEL, // 9
            Calls.CACHED_LOOKUP_URI, // 10
            Calls.CACHED_MATCHED_NUMBER, // 11
            Calls.CACHED_NORMALIZED_NUMBER, // 12
            Calls.CACHED_PHOTO_ID, // 13
            Calls.CACHED_FORMATTED_NUMBER, // 14
            Calls.NUMBER_PRESENTATION, // 15
    };

    public static final int ID = 0;
    public static final int NUMBER = 1;
    public static final int DATE = 2;
    public static final int DURATION = 3;
    public static final int CALL_TYPE = 4;
    public static final int COUNTRY_ISO = 5;
    public static final int GEOCODED_LOCATION = 6;
    public static final int CACHED_NAME = 7;
    public static final int CACHED_NUMBER_TYPE = 8;
    public static final int CACHED_NUMBER_LABEL = 9;
    public static final int CACHED_LOOKUP_URI = 10;
    public static final int CACHED_MATCHED_NUMBER = 11;
    public static final int CACHED_NORMALIZED_NUMBER = 12;
    public static final int CACHED_PHOTO_ID = 13;
    public static final int CACHED_FORMATTED_NUMBER = 14;
    public static final int NUMBER_PRESENTATION = 15;
}
