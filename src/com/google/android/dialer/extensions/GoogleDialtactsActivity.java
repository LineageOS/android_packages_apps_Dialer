/*
  * Copyright (C) 2013 Xiao-Long Chen <chenxiaolong@cxl.epac.to>
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

/*
 * This is a reverse-engineered implementation of com.google.android.Dialer.
 * There is no guarantee that this implementation will work correctly or even
 * work at all. Use at your own risk.
 */

package com.google.android.dialer.extensions;

import com.android.dialer.DialtactsActivity;
import com.google.android.gsf.Gservices;
import com.google.android.dialer.reverselookup.ReverseLookupSettingUtil;
import com.google.android.dialer.settings.GoogleDialerSettingsActivity;

import android.content.ContextWrapper;
import android.content.Intent;

public class GoogleDialtactsActivity extends DialtactsActivity {
    @Override
    protected void handleMenuSettings() {
        boolean enableNearby = Gservices.getBoolean(
                ((ContextWrapper)this).getContentResolver(),
                "dialer_enable_nearby_places_directory", true);
        boolean gServiceEnabled =
                ReverseLookupSettingUtil.isGServiceEnabled(this);

        if (enableNearby || gServiceEnabled) {
            startActivity(new Intent(this, GoogleDialerSettingsActivity.class));
        } else {
            super.handleMenuSettings();
        }
    }
}
