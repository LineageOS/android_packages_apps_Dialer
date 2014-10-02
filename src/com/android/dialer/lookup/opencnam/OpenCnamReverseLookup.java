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

import android.content.Context;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.lookup.ContactBuilder;
import com.android.dialer.lookup.LookupUtils;
import com.android.dialer.lookup.ReverseLookup;

import org.apache.http.client.methods.HttpGet;

import java.io.IOException;

public class OpenCnamReverseLookup extends ReverseLookup {
    private static final String TAG =
            OpenCnamReverseLookup.class.getSimpleName();

    private static final boolean DEBUG = false;

    private static final String LOOKUP_URL =
            "https://api.opencnam.com/v2/phone/";

    /** Query parameters for paid accounts */
    private static final String ACCOUNT_SID = "account_sid";
    private static final String AUTH_TOKEN = "auth_token";

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
    public ContactInfo lookupNumber(Context context,
            String normalizedNumber, String formattedNumber) throws IOException {
        if (normalizedNumber.startsWith("+") &&!normalizedNumber.startsWith("+1")) {
            // Any non-US number will return "We currently accept only US numbers"
            return null;
        }

        String displayName = httpGetRequest(context, normalizedNumber);
        if (DEBUG) Log.d(TAG, "Reverse lookup returned name: " + displayName);

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
        builder.setName(ContactBuilder.Name.createDisplayName(displayName));
        builder.addPhoneNumber(ContactBuilder.PhoneNumber.createMainNumber(number));
        builder.setPhotoUri(ContactBuilder.PHOTO_URI_BUSINESS);

        return builder.build();
    }

    private String httpGetRequest(Context context, String number) throws IOException {
        Uri.Builder builder = Uri.parse(LOOKUP_URL + number).buildUpon();

        // Paid account
        String accountSid = Settings.System.getString(
                context.getContentResolver(),
                Settings.System.DIALER_OPENCNAM_ACCOUNT_SID);
        String authToken = Settings.System.getString(
                context.getContentResolver(),
                Settings.System.DIALER_OPENCNAM_AUTH_TOKEN);

        if (!TextUtils.isEmpty(accountSid) && !TextUtils.isEmpty(authToken)) {
            Log.d(TAG, "Using paid account");

            builder.appendQueryParameter(ACCOUNT_SID, accountSid);
            builder.appendQueryParameter(AUTH_TOKEN, authToken);
        }

        return LookupUtils.httpGet(new HttpGet(builder.build().toString()));
    }
}
