/*
 * Copyright (C) 2014 Xiao-Long Chen <chenxiaolong@cxl.epac.to>
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

package com.android.dialer.lookup.whitepages;

import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.lookup.PeopleLookup;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.util.List;

public class WhitePagesPeopleLookup extends PeopleLookup {
    private static final String TAG = WhitePagesPeopleLookup.class.getSimpleName();

    public WhitePagesPeopleLookup(Context context) {
    }

    @Override
    public ContactInfo[] lookup(Context context, String filter) {
        List<ContactInfo> infos = null;
        try {
            infos = WhitePagesApi.peopleLookup(context, filter);
        } catch (IOException e) {
            Log.e(TAG, "People lookup failed", e);
        }
        return (infos != null && !infos.isEmpty())
                ? infos.toArray(new ContactInfo[infos.size()]) : null;
    }
}
