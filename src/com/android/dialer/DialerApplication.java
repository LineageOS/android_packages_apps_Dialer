/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.dialer;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Trace;

import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.contacts.common.extensions.ExtensionsFactory;
import com.android.contacts.commonbind.analytics.AnalyticsUtil;
import com.android.dialer.discovery.WifiCallStatusNudgeListener;
import com.android.dialer.incall.InCallMetricsHelper;
import com.android.phone.common.incall.DialerDataSubscription;
import com.android.dialer.util.MetricsHelper;
import com.android.dialer.deeplink.DeepLinkIntegrationManager;

import java.util.Locale;

public class DialerApplication extends Application {

    private static final String TAG = "DialerApplication";
    private static final boolean DEBUG = false;

    private static final String PREF_LAST_GLOBAL_LOCALE = "last_global_locale";

    @Override
    public void onCreate() {
        Trace.beginSection(TAG + " onCreate");
        super.onCreate();

        Trace.beginSection(TAG + " ExtensionsFactory initialization");
        ExtensionsFactory.init(getApplicationContext());
        Trace.endSection();

        Trace.beginSection(TAG + " Analytics initialization");
        AnalyticsUtil.initialize(this);
        Trace.endSection();

        DialerDataSubscription.init(this);
        MetricsHelper.init(this);
        WifiCallStatusNudgeListener.init(this);
        InCallMetricsHelper.init(this);
        DeepLinkIntegrationManager.getInstance().setUp(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Locale locale = getResources().getConfiguration().locale;
        String currentLocale = locale != null ? locale.toString() : "";
        prefs.edit().putString(PREF_LAST_GLOBAL_LOCALE, currentLocale).apply();
        Trace.endSection();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String previousLocale = prefs.getString(PREF_LAST_GLOBAL_LOCALE, "");
        String newLocale = (newConfig != null && newConfig.locale != null) ?
                newConfig.locale.toString() : "";
        if (DEBUG) {
            Log.d(TAG, "onConfigurationChanged: previous locale=" + previousLocale +
                    ", new locale=" + newLocale);
        }

        // If locale changed, update incall api plugins
        if (!TextUtils.equals(previousLocale, newLocale)) {
            prefs.edit().putString(PREF_LAST_GLOBAL_LOCALE, newLocale).apply();
            DialerDataSubscription.get(this).refresh();
        }

    }
}
