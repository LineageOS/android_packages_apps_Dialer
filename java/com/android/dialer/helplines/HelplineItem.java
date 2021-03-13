/*
 * Copyright (C) 2019-2021 The LineageOS Project
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
package com.android.dialer.helplines;

import android.content.res.Resources;

import com.android.dialer.helplines.utils.HelplineUtils;

import org.lineageos.lib.phone.SensitivePhoneNumberInfo;

public class HelplineItem {
    private final SensitivePhoneNumberInfo mInfo;
    private final String mName;

    public HelplineItem(Resources res, SensitivePhoneNumberInfo info, String countryIso) {
        mInfo = info;
        mName = HelplineUtils.getName(res, info, countryIso);
    }

    public String getName() {
        return mName;
    }

    public String get(String key) {
        return mInfo.get(key);
    }
}
