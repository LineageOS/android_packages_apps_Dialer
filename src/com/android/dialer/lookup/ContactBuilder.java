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

package com.android.dialer.lookup;

import com.android.contacts.common.util.Constants;
import com.android.dialer.R;
import com.android.dialer.calllog.ContactInfo;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.DisplayNameSources;
import android.util.Log;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ContactBuilder {
    private static final String TAG =
            ContactBuilder.class.getSimpleName();

    private static final boolean DEBUG = false;

    /** Used to choose the proper directory ID */
    public static final int FORWARD_LOOKUP = 0;
    public static final int PEOPLE_LOOKUP = 1;
    public static final int REVERSE_LOOKUP = 2;

    /** Default photo for businesses if no other image is found */
    public static final String PHOTO_URI_BUSINESS =
            new Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority("com.android.dialer")
            .appendPath(String.valueOf(
                    R.drawable.ic_places_picture_180_holo_light))
            .build()
            .toString();

    private ArrayList<Address> mAddresses = new ArrayList<Address>();
    private ArrayList<PhoneNumber> mPhoneNumbers
            = new ArrayList<PhoneNumber>();
    private ArrayList<WebsiteUrl> mWebsites
            = new ArrayList<WebsiteUrl>();

    private int mDirectoryType;

    private Name mName;

    private String mNormalizedNumber;
    private String mFormattedNumber;
    private int mDisplayNameSource = DisplayNameSources.ORGANIZATION;
    private Uri mPhotoUri;

    private boolean mIsBusiness;

    public ContactBuilder(int directoryType, String normalizedNumber,
            String formattedNumber) {
        mDirectoryType = directoryType;
        mNormalizedNumber = normalizedNumber;
        mFormattedNumber = formattedNumber;
    }

    public void addAddress(Address address) {
        if (DEBUG) Log.d(TAG, "Adding address");
        if (address != null) {
            mAddresses.add(address);
        }
    }

    public Address[] getAddresses() {
        return mAddresses.toArray(new Address[mAddresses.size()]);
    }

    public void addPhoneNumber(PhoneNumber phoneNumber) {
        if (DEBUG) Log.d(TAG, "Adding phone number");
        if (phoneNumber != null) {
            mPhoneNumbers.add(phoneNumber);
        }
    }

    public PhoneNumber[] getPhoneNumbers() {
        return mPhoneNumbers.toArray(
                new PhoneNumber[mPhoneNumbers.size()]);
    }

    public void addWebsite(WebsiteUrl website) {
        if (DEBUG) Log.d(TAG, "Adding website");
        if (website != null) {
            mWebsites.add(website);
        }
    }

    public Website[] getWebsites() {
        return mWebsites.toArray(new Website[mWebsites.size()]);
    }

    public void setName(Name name) {
        if (DEBUG) Log.d(TAG, "Setting name");
        if (name != null) {
            mName = name;
        }
    }

    public Name getName() {
        return mName;
    }

    public void setPhotoUri(String photoUri) {
        setPhotoUri(Uri.parse(photoUri));
    }

    public void setPhotoUri(Uri photoUri) {
        if (DEBUG) Log.d(TAG, "Setting photo URI");
        mPhotoUri = photoUri;
    }

    public Uri getPhotoUri() {
        return mPhotoUri;
    }

    public void setIsBusiness(boolean isBusiness) {
        if (DEBUG) Log.d(TAG, "Setting isBusiness to " + isBusiness);
        mIsBusiness = isBusiness;
    }

    public boolean isBusiness() {
        return mIsBusiness;
    }

    public ContactInfo build() {
        if (mName == null) {
            throw new IllegalStateException("Name has not been set");
        }

        if (mDirectoryType != FORWARD_LOOKUP
                && mDirectoryType != PEOPLE_LOOKUP
                && mDirectoryType != REVERSE_LOOKUP) {
            throw new IllegalStateException("Invalid directory type");
        }

        // Use the incoming call's phone number if no other phone number
        // is specified. The reverse lookup source could present the phone
        // number differently (eg. without the area code).
        if (mPhoneNumbers.size() == 0) {
            PhoneNumber pn = new PhoneNumber();
            // Use the formatted number where possible
            pn.number = mFormattedNumber != null
                    ? mFormattedNumber : mNormalizedNumber;
            pn.type = Phone.TYPE_MAIN;
            addPhoneNumber(pn);
        }

        try {
            JSONObject contact = new JSONObject();

            // Insert the name
            contact.put(StructuredName.CONTENT_ITEM_TYPE,
                    mName.getJsonObject());

            // Insert phone numbers
            JSONArray phoneNumbers = new JSONArray();
            for (int i = 0; i < mPhoneNumbers.size(); i++) {
                phoneNumbers.put(mPhoneNumbers.get(i).getJsonObject());
            }
            contact.put(Phone.CONTENT_ITEM_TYPE, phoneNumbers);

            // Insert addresses if there are any
            if (mAddresses.size() > 0) {
                JSONArray addresses = new JSONArray();
                for (int i = 0; i < mAddresses.size(); i++) {
                    addresses.put(mAddresses.get(i).getJsonObject());
                }
                contact.put(StructuredPostal.CONTENT_ITEM_TYPE, addresses);
            }

            // Insert websites if there are any
            if (mWebsites.size() > 0) {
                JSONArray websites = new JSONArray();
                for (int i = 0; i < mWebsites.size(); i++) {
                    websites.put(mWebsites.get(i).getJsonObject());
                }
                contact.put(Website.CONTENT_ITEM_TYPE, websites);
            }

            ContactInfo info = new ContactInfo();
            info.name = mName.displayName;
            info.normalizedNumber = mNormalizedNumber;
            info.number = mPhoneNumbers.get(0).number;
            info.type = mPhoneNumbers.get(0).type;
            info.label = mPhoneNumbers.get(0).label;
            info.photoUri = mPhotoUri != null ? mPhotoUri : null;

            String json = new JSONObject()
                    .put(Contacts.DISPLAY_NAME, mName.displayName)
                    .put(Contacts.DISPLAY_NAME_SOURCE, mDisplayNameSource)
                    .put(Directory.EXPORT_SUPPORT,
                            Directory.EXPORT_SUPPORT_ANY_ACCOUNT)
                    .put(Contacts.CONTENT_ITEM_TYPE, contact)
                    .toString();

            if (json != null) {
                long directoryId = -1;
                if (mDirectoryType == FORWARD_LOOKUP
                        || mDirectoryType == PEOPLE_LOOKUP) {
                    directoryId = ContactsContract.Directory.DEFAULT;
                } else if (mDirectoryType == REVERSE_LOOKUP) {
                    directoryId = Long.MAX_VALUE;
                }

                info.lookupUri = Contacts.CONTENT_LOOKUP_URI
                        .buildUpon()
                        .appendPath(Constants.LOOKUP_URI_ENCODED)
                        .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                                String.valueOf(directoryId))
                        .encodedFragment(json)
                        .build();
            }

            return info;
        } catch (JSONException e) {
            Log.e(TAG, "Failed to build contact", e);
            return null;
        }
    }

    // android.provider.ContactsContract.CommonDataKinds.StructuredPostal
    public static class Address {
        public String formattedAddress;
        public int type;
        public String label;
        public String street;
        public String poBox;
        public String neighborhood;
        public String city;
        public String region;
        public String postCode;
        public String country;

        public static Address createFormattedHome(String address) {
            Address a = new Address();
            a.formattedAddress = address;
            a.type = StructuredPostal.TYPE_HOME;
            return a;
        }

        public JSONObject getJsonObject() throws JSONException {
            JSONObject json = new JSONObject();
            json.putOpt(StructuredPostal.FORMATTED_ADDRESS,
                    formattedAddress);
            json.put(StructuredPostal.TYPE, type);
            json.putOpt(StructuredPostal.LABEL, label);
            json.putOpt(StructuredPostal.STREET, street);
            json.putOpt(StructuredPostal.POBOX, poBox);
            json.putOpt(StructuredPostal.NEIGHBORHOOD, neighborhood);
            json.putOpt(StructuredPostal.CITY, city);
            json.putOpt(StructuredPostal.REGION, region);
            json.putOpt(StructuredPostal.POSTCODE, postCode);
            json.putOpt(StructuredPostal.COUNTRY, country);
            return json;
        }

        public String toString() {
            return "formattedAddress: " + formattedAddress + "; " +
                    "type: " + type + "; " +
                    "label: " + label + "; " +
                    "street: " + street + "; " +
                    "poBox: " + poBox + "; " +
                    "neighborhood: " + neighborhood + "; " +
                    "city: " + city + "; " +
                    "region: " + region + "; " +
                    "postCode: " + postCode + "; " +
                    "country: " + country;
        }
    }

    // android.provider.ContactsContract.CommonDataKinds.StructuredName
    public static class Name {
        public String displayName;
        public String givenName;
        public String familyName;
        public String prefix;
        public String middleName;
        public String suffix;
        public String phoneticGivenName;
        public String phoneticMiddleName;
        public String phoneticFamilyName;

        public static Name createDisplayName(String displayName) {
            Name name = new Name();
            name.displayName = displayName;
            return name;
        }

        public JSONObject getJsonObject() throws JSONException {
            JSONObject json = new JSONObject();
            json.putOpt(StructuredName.DISPLAY_NAME, displayName);
            json.putOpt(StructuredName.GIVEN_NAME, givenName);
            json.putOpt(StructuredName.FAMILY_NAME, familyName);
            json.putOpt(StructuredName.PREFIX, prefix);
            json.putOpt(StructuredName.MIDDLE_NAME, middleName);
            json.putOpt(StructuredName.SUFFIX, suffix);
            json.putOpt(StructuredName.PHONETIC_GIVEN_NAME,
                    phoneticGivenName);
            json.putOpt(StructuredName.PHONETIC_MIDDLE_NAME,
                    phoneticMiddleName);
            json.putOpt(StructuredName.PHONETIC_FAMILY_NAME,
                    phoneticFamilyName);
            return json;
        }

        public String toString() {
            return "displayName: " + displayName + "; " +
                    "givenName: " + givenName + "; " +
                    "familyName: " + familyName + "; " +
                    "prefix: " + prefix + "; " +
                    "middleName: " + middleName + "; " +
                    "suffix: " + suffix + "; " +
                    "phoneticGivenName: " + phoneticGivenName + "; " +
                    "phoneticMiddleName: " + phoneticMiddleName + "; " +
                    "phoneticFamilyName: " + phoneticFamilyName;
        }
    }

    // android.provider.ContactsContract.CommonDataKinds.Phone
    public static class PhoneNumber {
        public String number;
        public int type;
        public String label;

        public static PhoneNumber createMainNumber(String number) {
            PhoneNumber n = new PhoneNumber();
            n.number = number;
            n.type = Phone.TYPE_MAIN;
            return n;
        }

        public JSONObject getJsonObject() throws JSONException {
            JSONObject json = new JSONObject();
            json.put(Phone.NUMBER, number);
            json.put(Phone.TYPE, type);
            json.putOpt(Phone.LABEL, label);
            return json;
        }

        public String toString() {
            return "number: " + number + "; " +
                    "type: " + type + "; " +
                    "label: " + label;
        }
    }

    // android.provider.ContactsContract.CommonDataKinds.Website
    public static class WebsiteUrl {
        public String url;
        public int type;
        public String label;

        public static WebsiteUrl createProfile(String url) {
            WebsiteUrl u = new WebsiteUrl();
            u.url = url;
            u.type = Website.TYPE_PROFILE;
            return u;
        }

        public JSONObject getJsonObject() throws JSONException {
            JSONObject json = new JSONObject();
            json.put(Website.URL, url);
            json.put(Website.TYPE, type);
            json.putOpt(Website.LABEL, label);
            return json;
        }

        public String toString() {
            return "url: " + url + "; " +
                    "type: " + type + "; " +
                    "label: " + label;
        }
    }
}
