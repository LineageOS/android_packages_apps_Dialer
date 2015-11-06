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
import android.app.Fragment;
import android.text.TextUtils;

import com.android.contacts.commonbind.analytics.AnalyticsUtil;
import com.android.dialerbind.ObjectFactory;
import com.android.incallui.Call;

/**
 * Single entry point for all logging/analytics-related work for all user interactions.
 */
public abstract class Logger {
    public static final String FRAGMENT_TAG_SEPARATOR = "#";

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
     * Logs an event indicating that a fragment was displayed.
     *
     * @param fragment to log an event for.
     */
    public static void logFragmentView(Fragment fragment) {
        if (fragment == null) {
            return;
        }

        logScreenView(fragment.getClass().getSimpleName(), fragment.getActivity(), null);
    }

    /**
     * Logs an event indicating that a screen was displayed.
     *
     * @param screenName of the displayed screen.
     * @param activity Parent activity of the displayed screen.
     * @param tag Optional string used to provide additional information about the screen.
     */
    public static void logScreenView(String screenName, Activity activity, String tag) {
        final Logger logger = getInstance();
        if (logger != null) {
            logger.logScreenViewImpl(getScreenNameWithTag(screenName, tag));
        }

        AnalyticsUtil.sendScreenView(screenName, activity, tag);
    }

    /**
     * Build a tagged version of the provided screenName if the tag is non-empty.
     *
     * @param screenName Name of the screen.
     * @param tag Optional tag describing the screen.
     * @return the unchanged screenName if the tag is {@code null} or empty, the tagged version of
     *         the screenName otherwise.
     */
    public static String getScreenNameWithTag(String screenName, String tag) {
        if (TextUtils.isEmpty(tag)) {
            return screenName;
        }
        return screenName + FRAGMENT_TAG_SEPARATOR + tag;
    }

    public abstract void logCallImpl(Call call);
    public abstract void logScreenViewImpl(String screenName);
}
