/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.incallui;

import android.util.Log;

/**
 * Manages logging for the entire class.
 */
/*package*/ class Logger {

    // Generic tag for all In Call logging
    private static final String TAG = "InCall";

    public static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    public static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    public static void d(Object obj, String msg) {
        if (DEBUG) {
            Log.d(TAG, getPrefix(obj) + msg);
        }
    }

    public static void v(Object obj, String msg) {
        if (VERBOSE) {
            Log.v(TAG, getPrefix(obj) + msg);
        }
    }

    public static void e(Object obj, String msg, Exception e) {
        Log.e(TAG, getPrefix(obj) + msg, e);
    }

    public static void e(Object obj, String msg) {
        Log.e(TAG, getPrefix(obj) + msg);
    }

    public static void i(Object obj, String msg) {
        Log.i(TAG, getPrefix(obj) + msg);
    }

    private static String getPrefix(Object obj) {
        return (obj == null ? "" : (obj.getClass().getSimpleName() + " - "));
    }
}
