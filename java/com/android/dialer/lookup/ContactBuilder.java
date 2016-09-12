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
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.common.util.Constants;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Text;

import java.sql.Struct;
import java.util.ArrayList;

public class ContactBuilder {
  private static final String TAG = ContactBuilder.class.getSimpleName();

  private static final boolean DEBUG = false;

  /** Default photo for businesses if no other image is found */
  public static final String PHOTO_URI_BUSINESS = new Uri.Builder()
      .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
      .authority("com.android.dialer")
      .appendPath(String.valueOf(R.drawable.ic_places_picture_180_holo_light))
      .build()
      .toString();

  private final ArrayList<Address> addresses = new ArrayList<>();
  private final ArrayList<PhoneNumber> phoneNumbers = new ArrayList<>();
  private final ArrayList<WebsiteUrl> websites = new ArrayList<>();

  private final long directoryId;
  private Name name;
  private final String normalizedNumber;
  private final String formattedNumber;
  private Uri photoUri;

  public static ContactBuilder forForwardLookup(String number) {
      return new ContactBuilder(DirectoryId.NEARBY, null, number);
  }

  public static ContactBuilder forPeopleLookup(String number) {
      return new ContactBuilder(DirectoryId.PEOPLE, null, number);
  }

  public static ContactBuilder forReverseLookup(String normalizedNumber, String formattedNumber) {
      return new ContactBuilder(DirectoryId.NULL, normalizedNumber, formattedNumber);
  }

  private ContactBuilder(long directoryId, String normalizedNumber, String formattedNumber) {
    this.directoryId = directoryId;
    this.normalizedNumber = normalizedNumber;
    this.formattedNumber = formattedNumber;
  }

  public ContactBuilder(Uri encodedContactUri) throws JSONException {
    String jsonData = encodedContactUri.getEncodedFragment();
    String directoryIdStr = encodedContactUri.getQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY);
    long directoryId = DirectoryId.DEFAULT;

    if (!TextUtils.isEmpty(directoryIdStr)) {
      try {
        directoryId = Long.parseLong(directoryIdStr);
      } catch (NumberFormatException e) {
        Log.e(TAG, "Error parsing directory id of uri " + encodedContactUri, e);
      }
    }

    this.directoryId = directoryId;
    this.formattedNumber = null;
    this.normalizedNumber = null;

    try {
      // name
      JSONObject json = new JSONObject(jsonData);
      JSONObject contact = json.optJSONObject(Contacts.CONTENT_ITEM_TYPE);
      JSONObject nameObj = contact.optJSONObject(StructuredName.CONTENT_ITEM_TYPE);
      name = new Name(nameObj);

      if (contact != null) {
        // numbers
        if (contact.has(Phone.CONTENT_ITEM_TYPE)) {
          String phoneData = contact.getString(Phone.CONTENT_ITEM_TYPE);
          Object phoneObject = new JSONTokener(phoneData).nextValue();
          JSONArray phoneNumbersJson;
          if (phoneObject instanceof JSONObject) {
            phoneNumbersJson = new JSONArray();
            phoneNumbersJson.put(phoneObject);
          } else {
            phoneNumbersJson = contact.getJSONArray(Phone.CONTENT_ITEM_TYPE);
          }
          for (int i = 0; i < phoneNumbersJson.length(); ++i) {
            JSONObject phoneObj = phoneNumbersJson.getJSONObject(i);
            phoneNumbers.add(new PhoneNumber(phoneObj));
          }
        }

        // address
        if (contact.has(StructuredPostal.CONTENT_ITEM_TYPE)) {
          JSONArray addressesJson = contact.getJSONArray(StructuredPostal.CONTENT_ITEM_TYPE);
          for (int i = 0; i < addressesJson.length(); ++i) {
            JSONObject addrObj = addressesJson.getJSONObject(i);
            addresses.add(new Address(addrObj));
          }
        }

        // websites
        if (contact.has(Website.CONTENT_ITEM_TYPE)) {
          JSONArray websitesJson = contact.getJSONArray(Website.CONTENT_ITEM_TYPE);
          for (int i = 0; i < websitesJson.length(); ++i) {
            JSONObject websiteObj = websitesJson.getJSONObject(i);
            final WebsiteUrl websiteUrl = new WebsiteUrl(websiteObj);
            if (!TextUtils.isEmpty(websiteUrl.url)) {
              websites.add(new WebsiteUrl(websiteObj));
            }
          }
        }
      }
    } catch(JSONException e) {
      Log.e(TAG, "Error parsing encoded fragment of uri " + encodedContactUri, e);
      throw e;
    }
  }

  public ContactBuilder addAddress(Address address) {
    if (DEBUG) Log.d(TAG, "Adding address");
    if (address != null) {
      addresses.add(address);
    }
    return this;
  }

  public ContactBuilder addPhoneNumber(PhoneNumber phoneNumber) {
    if (DEBUG) Log.d(TAG, "Adding phone number");
    if (phoneNumber != null) {
      phoneNumbers.add(phoneNumber);
    }
    return this;
  }

  public ContactBuilder addWebsite(WebsiteUrl website) {
    if (DEBUG) Log.d(TAG, "Adding website");
    if (website != null) {
      websites.add(website);
    }
    return this;
  }

  public ContactBuilder setName(Name name) {
    if (DEBUG) Log.d(TAG, "Setting name");
    if (name != null) {
      this.name = name;
    }
    return this;
  }

  public ContactBuilder setPhotoUri(String photoUri) {
    if (photoUri != null) {
      setPhotoUri(Uri.parse(photoUri));
    }
    return this;
  }

  public ContactBuilder setPhotoUri(Uri photoUri) {
    if (DEBUG) Log.d(TAG, "Setting photo URI");
    this.photoUri = photoUri;
    return this;
  }

  public ContactInfo build() {
    if (name == null) {
      throw new IllegalStateException("Name has not been set");
    }

    // Use the incoming call's phone number if no other phone number
    // is specified. The reverse lookup source could present the phone
    // number differently (eg. without the area code).
    if (phoneNumbers.isEmpty()) {
      PhoneNumber pn = new PhoneNumber();
      // Use the formatted number where possible
      pn.number = formattedNumber != null
              ? formattedNumber : normalizedNumber;
      pn.type = Phone.TYPE_MAIN;
      addPhoneNumber(pn);
    }

    try {
      JSONObject contact = new JSONObject();

      // Insert the name
      contact.put(StructuredName.CONTENT_ITEM_TYPE, name.getJsonObject());

      // Insert phone numbers
      JSONArray phoneNumbersJson = new JSONArray();
      for (PhoneNumber number : phoneNumbers) {
        phoneNumbersJson.put(number.getJsonObject());
      }
      contact.put(Phone.CONTENT_ITEM_TYPE, phoneNumbersJson);

      // Insert addresses if there are any
      if (!addresses.isEmpty()) {
        JSONArray addressesJson = new JSONArray();
        for (Address address : addresses) {
          addressesJson.put(address.getJsonObject());
        }
        contact.put(StructuredPostal.CONTENT_ITEM_TYPE, addressesJson);
      }

      // Insert websites if there are any
      if (!websites.isEmpty()) {
        JSONArray websitesJson = new JSONArray();
        for (WebsiteUrl site : websites) {
          websitesJson.put(site.getJsonObject());
        }
        contact.put(Website.CONTENT_ITEM_TYPE, websitesJson);
      }

      ContactInfo info = new ContactInfo();
      info.name = name.displayName;
      info.normalizedNumber = normalizedNumber;
      info.number = phoneNumbers.get(0).number;
      info.type = phoneNumbers.get(0).type;
      info.label = phoneNumbers.get(0).label;
      info.photoUri = photoUri;

      String json = new JSONObject()
          .put(Contacts.DISPLAY_NAME, name.displayName)
          .put(Contacts.DISPLAY_NAME_SOURCE, DisplayNameSources.ORGANIZATION)
          .put(Directory.EXPORT_SUPPORT, Directory.EXPORT_SUPPORT_ANY_ACCOUNT)
          .put(Contacts.CONTENT_ITEM_TYPE, contact)
          .toString();

      if (json != null) {
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
      if (address == null) {
        return null;
      }
      Address a = new Address();
      a.formattedAddress = address;
      a.type = StructuredPostal.TYPE_HOME;
      return a;
    }

    public JSONObject getJsonObject() throws JSONException {
      JSONObject json = new JSONObject();
      json.putOpt(StructuredPostal.FORMATTED_ADDRESS, formattedAddress);
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

    public Address() {}

    public Address(JSONObject json) throws JSONException {
      if (json.has(StructuredPostal.FORMATTED_ADDRESS)) {
        formattedAddress = json.getString(StructuredPostal.FORMATTED_ADDRESS);
      }
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
      json.putOpt(StructuredName.PHONETIC_GIVEN_NAME, phoneticGivenName);
      json.putOpt(StructuredName.PHONETIC_MIDDLE_NAME, phoneticMiddleName);
      json.putOpt(StructuredName.PHONETIC_FAMILY_NAME, phoneticFamilyName);
      return json;
    }

    public Name(JSONObject json) throws JSONException {
      if (json != null) {
        displayName = json.optString(StructuredName.DISPLAY_NAME, null);
      }
    }

    public Name() {}

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

    public PhoneNumber(JSONObject json) throws JSONException {
      number = json.getString(Phone.NUMBER);
      type = json.getInt(Phone.TYPE);
      if (json.has(Phone.LABEL)) {
        label = json.getString(Phone.LABEL);
      }
    }

    public PhoneNumber() {}

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
      if (url == null) {
        return null;
      }
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

    public WebsiteUrl() {}

    public WebsiteUrl(JSONObject json) throws JSONException {
      if (json.has(Website.URL)) {
        url = json.getString(Website.URL);
      }
      if (json.has(Website.TYPE)) {
        type = json.getInt(Website.TYPE);
      }
      if (json.has(Website.LABEL)) {
        label = json.getString(Website.LABEL);
      }
    }

    public String toString() {
      return "url: " + url + "; " +
          "type: " + type + "; " +
          "label: " + label;
    }
  }
}
