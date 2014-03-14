/*
 * Copyright (C) 2014 Xiao-Long Chen <chillermillerlong@hotmail.com>
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

package com.android.dialer.lookup.opencnam;

import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.lookup.ContactBuilder;
import com.android.dialer.lookup.ReverseLookup;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import android.provider.ContactsContract.CommonDataKinds.Phone;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class OpenCnamReverseLookup extends ReverseLookup {
    private static final String TAG =
            OpenCnamReverseLookup.class.getSimpleName();

    private static final boolean DEBUG = false;

    private static final String LOOKUP_URL =
            "https://api.opencnam.com/v2/phone/";

    public OpenCnamReverseLookup(Context context) {
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
        String displayName;
        try {
            displayName = httpGetRequest(normalizedNumber);
            if (DEBUG) Log.d(TAG, "Reverse lookup returned name: " + displayName);
        } catch (IOException e) {
            return null;
        }

        // Check displayName. The free tier of the service will return the
        // following for some numbers:
        // "CNAM for phone "NORMALIZED" is currently unavailable for Hobbyist Tier users."

        if (displayName.contains("Hobbyist Tier")) {
            return null;
        }

        String number = formattedNumber != null
                ? formattedNumber : normalizedNumber;

        ContactBuilder builder = new ContactBuilder(
                ContactBuilder.REVERSE_LOOKUP,
                normalizedNumber, formattedNumber);

        ContactBuilder.Name n = new ContactBuilder.Name();
        n.displayName = displayName;
        builder.setName(n);

        ContactBuilder.PhoneNumber pn = new ContactBuilder.PhoneNumber();
        pn.number = number;
        pn.type = Phone.TYPE_MAIN;
        builder.addPhoneNumber(pn);

        builder.setPhotoUri(ContactBuilder.PHOTO_URI_BUSINESS);

        return Pair.create(builder.build(), null);
    }

    private String httpGetRequest(String number) throws IOException {
        HttpClient client = new DefaultHttpClient();
        HttpGet request = new HttpGet(LOOKUP_URL + number);

        HttpResponse response = client.execute(request);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getEntity().writeTo(out);

        return new String(out.toByteArray());
    }
}
