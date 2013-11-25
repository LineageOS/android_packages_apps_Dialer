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

public class WifiSettings implements WifiWizardAdapter.WifiWizardModel {
    private final Context mContext;

    public WifiSettings(Context context) {
        this.mContext = context;
    }

    @Override
    public void setWhenToMakeWifiCalls(int preference) {
        // TODO(ihab): Implement based on real data
    }

    @Override
    public int getWhenToMakeWifiCalls() {
        // TODO(ihab): Implement based on real data
        return WIFI_ALWAYS_USE;
    }

    @Override
    public boolean shouldDisplayWifiSelection() {

        // PreferenceManager.getDefaultSharedPreferences(PhoneFavoriteFragment.this.getActivity());
        // .edit().putBoolean("", true).commit();
        // .getBoolean("boobaz", false);
        // .getSharedPreferences("boo.baz", 1);

        // TODO(ihab): Implement based on real data
        return true;

    }
}
