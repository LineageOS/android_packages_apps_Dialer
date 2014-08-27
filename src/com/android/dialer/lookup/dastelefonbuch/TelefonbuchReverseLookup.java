/*
 * Copyright (C) 2014 Danny Baumann <dannybaumann@web.de>
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

package com.android.dialer.lookup.dastelefonbuch;

import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.util.Pair;

import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.lookup.ContactBuilder;
import com.android.dialer.lookup.ReverseLookup;

import java.io.IOException;

public class TelefonbuchReverseLookup extends ReverseLookup {
    private static final String TAG = TelefonbuchReverseLookup.class.getSimpleName();

    public TelefonbuchReverseLookup(Context context) {
    }

    /**
     * Perform phone number lookup.
     *
     * @param context The application context
     * @param normalizedNumber The normalized phone number
     * @param formattedNumber The formatted phone number
     * @return The phone number info object
     */
    public Pair<ContactInfo, Object> lookupNumber(Context context,
            String normalizedNumber, String formattedNumber) {
        TelefonbuchApi.ContactInfo info = null;

        if (normalizedNumber.startsWith("+") && !normalizedNumber.startsWith("+49")) {
            // Das Telefonbuch only supports German numbers
            return null;
        }

        try {
            info = TelefonbuchApi.reverseLookup(context, normalizedNumber);
        } catch (IOException e) {
            return null;
        }

        if (info == null) {
            return null;
        }

        ContactBuilder builder = new ContactBuilder(
                ContactBuilder.REVERSE_LOOKUP,
                normalizedNumber, formattedNumber);

        ContactBuilder.Name n = new ContactBuilder.Name();
        n.displayName = info.name;
        builder.setName(n);

        ContactBuilder.PhoneNumber pn = new ContactBuilder.PhoneNumber();
        pn.number = info.formattedNumber;
        pn.type = Phone.TYPE_MAIN;
        builder.addPhoneNumber(pn);

        if (info.address != null) {
            ContactBuilder.Address a = new ContactBuilder.Address();
            a.formattedAddress = info.address;
            a.type = StructuredPostal.TYPE_HOME;
            builder.addAddress(a);
        }

        ContactBuilder.WebsiteUrl w = new ContactBuilder.WebsiteUrl();
        w.url = info.website;
        w.type = Website.TYPE_PROFILE;
        builder.addWebsite(w);

        return Pair.create(builder.build(), null);
    }
}
