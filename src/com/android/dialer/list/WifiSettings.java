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
 * limitations under the License.
 */
package com.android.dialer.list;

import android.content.Context;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;

public class WifiSettings implements WifiWizardAdapter.WifiWizardModel {
    private final Context mContext;

    private static final String SHOULD_DISPLAY_WIFI_SELECTION = "should_display_wifi_selection";
    private int mWhenToMakeWifiCalls;

    public WifiSettings(Context context) {
        this.mContext = context;
        this.mWhenToMakeWifiCalls = getTelephonyService().getWhenToMakeWifiCalls();
    }

    @Override
    public void setWhenToMakeWifiCalls(int preference) {
        mWhenToMakeWifiCalls = preference;
    }

    @Override
    public int getWhenToMakeWifiCalls() {
        return mWhenToMakeWifiCalls;
    }

    @Override
    public boolean getShouldDisplayWifiSelection() {
        return PreferenceManager.getDefaultSharedPreferences(mContext)
                .getBoolean(SHOULD_DISPLAY_WIFI_SELECTION, true);
    }

    @Override
    public void setShouldDisplayWifiSelection(boolean selection) {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit().putBoolean(SHOULD_DISPLAY_WIFI_SELECTION, selection)
                .commit();
    }

    @Override
    public void commitWhenToMakeWifiCalls() {
        getTelephonyService().setWhenToMakeWifiCalls(mWhenToMakeWifiCalls);

    }

    private TelephonyManager getTelephonyService() {
        return (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
    }
}
