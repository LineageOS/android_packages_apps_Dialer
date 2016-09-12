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

package com.android.dialer.lookup.yellowpages;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.util.Log;

import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.lookup.ContactBuilder;
import com.android.dialer.lookup.LookupSettings;
import com.android.dialer.lookup.LookupUtils;
import com.android.dialer.lookup.ReverseLookup;

import java.io.FileNotFoundException;
import java.io.IOException;

public class YellowPagesReverseLookup extends ReverseLookup {
  private static final String TAG = YellowPagesReverseLookup.class.getSimpleName();

  private final String type;

  public YellowPagesReverseLookup(Context context, String type) {
    this.type = type;
  }

  /**
   * Lookup image
   *
   * @param context The application context
   * @param uri The image URI
   */
  @Override
  public Bitmap lookupImage(Context context, Uri uri) {
    if (uri == null) {
      throw new NullPointerException("URI is null");
    }

    Log.e(TAG, "Fetching " + uri);

    String scheme = uri.getScheme();

    if (scheme.startsWith("http")) {
      try {
        byte[] response = LookupUtils.httpGetBytes(uri.toString(), null);
        return BitmapFactory.decodeByteArray(response, 0, response.length);
      } catch (IOException e) {
        Log.e(TAG, "Failed to retrieve image", e);
      }
    } else if (scheme.equals(ContentResolver.SCHEME_CONTENT)) {
      try {
        ContentResolver cr = context.getContentResolver();
        return BitmapFactory.decodeStream(cr.openInputStream(uri));
      } catch (FileNotFoundException e) {
        Log.e(TAG, "Failed to retrieve image", e);
      }
    }

    return null;
  }

  /**
   * Perform phone number lookup.
   *
   * @param context The application context
   * @param normalizedNumber The normalized phone number
   * @param formattedNumber The formatted phone number
   * @return The phone number info object
   */
  @Override
  public ContactInfo lookupNumber(Context context,
      String normalizedNumber, String formattedNumber) throws IOException {
    String lookupUrl = type.equals(LookupSettings.RLP_YELLOWPAGES_CA)
        ? YellowPagesApi.LOOKUP_URL_CANADA : YellowPagesApi.LOOKUP_URL_UNITED_STATES;
    YellowPagesApi ypa = new YellowPagesApi(normalizedNumber, lookupUrl);
    YellowPagesApi.ContactInfo info = ypa.getContactInfo();
    if (info.name == null) {
        return null;
    }

    ContactBuilder builder = ContactBuilder.forReverseLookup(normalizedNumber, formattedNumber)
        .setName(ContactBuilder.Name.createDisplayName(info.name))
        .addPhoneNumber(ContactBuilder.PhoneNumber.createMainNumber(info.formattedNumber))
        .addWebsite(ContactBuilder.WebsiteUrl.createProfile(info.website));

    if (info.address != null) {
      ContactBuilder.Address a = new ContactBuilder.Address();
      a.formattedAddress = info.address;
      a.type = StructuredPostal.TYPE_WORK;
      builder.addAddress(a);
    }

    if (info.photoUrl != null) {
      builder.setPhotoUri(info.photoUrl);
    } else {
      builder.setPhotoUri(ContactBuilder.PHOTO_URI_BUSINESS);
    }

    return builder.build();
  }
}
