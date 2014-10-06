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
import com.android.dialer.lookup.ContactBuilder;
import com.android.dialer.lookup.PeopleLookup;

import android.content.Context;
import android.location.Location;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;

public class WhitePagesPeopleLookup extends PeopleLookup {
    private static final String TAG =
            WhitePagesPeopleLookup.class.getSimpleName();

    public WhitePagesPeopleLookup(Context context) {
    }

    @Override
    public ContactInfo[] lookup(Context context, String filter) {
        WhitePagesApi.ContactInfo[] infos = null;

        try {
            infos = WhitePagesApi.peopleLookup(context, filter, 3);
        } catch (IOException e) {
            Log.e(TAG, "People lookup failed", e);
        }

        if (infos == null || infos.length == 0) {
            return null;
        }

        ContactInfo[] details = new ContactInfo[infos.length];
        for (int i = 0; i < infos.length; i++) {
            WhitePagesApi.ContactInfo info = infos[i];
            ContactBuilder builder = new ContactBuilder(
                    ContactBuilder.PEOPLE_LOOKUP, null, info.formattedNumber);

            builder.setName(ContactBuilder.Name.createDisplayName(info.name));
            builder.addPhoneNumber(
                    ContactBuilder.PhoneNumber.createMainNumber(info.formattedNumber));
            builder.addWebsite(ContactBuilder.WebsiteUrl.createProfile(info.website));

            if (info.address != null || info.city != null) {
                ContactBuilder.Address a = new ContactBuilder.Address();
                a.city = info.city;
                a.formattedAddress = info.address;
                a.type = StructuredPostal.TYPE_HOME;
                builder.addAddress(a);
            }

            details[i] = builder.build();
        }

        return details;
    }
}
