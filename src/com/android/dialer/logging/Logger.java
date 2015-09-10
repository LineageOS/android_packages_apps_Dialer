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
 * limitations under the License.
 */

package com.android.dialer.logging;

import android.app.Activity;

import com.android.dialerbind.ObjectFactory;
import com.android.incallui.Call;

public abstract class Logger {

    public static Logger getInstance() {
        return ObjectFactory.getLoggerInstance();
    }

    /**
     * Logs a call event. PII like the call's number or caller details should never be logged.
     *
     * @param call to log.
     */
    public static void logCall(Call call) {
        final Logger logger = getInstance();
        if (logger != null) {
            logger.logCallImpl(call);
        }
    }

    /**
     * Logs an event indicating that a screen/fragment was displayed.
     *
     * @param fragmentName of the displayed fragment.
     * @param activity Parent activity of the fragment.
     * @param tag Optional string used to provide additional information about the fragment.
     */
    public static void logScreenView(String fragmentName, Activity activity, String tag) {
        final Logger logger = getInstance();
        if (logger != null) {
            logger.logScreenViewImpl(fragmentName, activity, tag);
        }
    }

    public abstract void logCallImpl(Call call);
    public abstract void logScreenViewImpl(String fragmentName, Activity activity, String tag);
}
