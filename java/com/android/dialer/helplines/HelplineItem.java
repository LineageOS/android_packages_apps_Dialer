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

import org.lineageos.lib.phone.spn.Item;

/* When loading all the items we modify the name based on the subscription.
 * Using the setter would modify it permanently, resulting in modifications on each load.
 * Therefore we don't use Item directly but use this little helper class so the modified
 * name can be stored
 */
public class HelplineItem {
    private final Item mItem;
    private final String mName;

    public HelplineItem(Resources res, Item item, String countryIso) {
        mItem = item;
        mName = HelplineUtils.getName(res, item, countryIso);
    }

    public Item getItem() {
        return mItem;
    }

    public String getName() {
        return mName;
    }
}
