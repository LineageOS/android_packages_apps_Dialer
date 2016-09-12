/**
 * Copyright (c) 2015, The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dialer.lookup.auskunft;

import android.content.Context;
import android.util.Log;

import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.lookup.ContactBuilder;
import com.android.dialer.lookup.PeopleLookup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AuskunftPeopleLookup extends PeopleLookup {
  private static final String TAG = AuskunftPeopleLookup.class.getSimpleName();

  public AuskunftPeopleLookup(Context context) {
  }

  @Override
  public List<ContactInfo> lookup(Context context, String filter) {
    try {
      List<AuskunftApi.ContactInfo> infos = AuskunftApi.query(filter);
      if (infos != null) {
          List<ContactInfo> result = new ArrayList<>();
          for (AuskunftApi.ContactInfo info : infos) {
            result.add(ContactBuilder.forPeopleLookup(info.number)
                .setName(ContactBuilder.Name.createDisplayName(info.name))
                .addPhoneNumber(ContactBuilder.PhoneNumber.createMainNumber(info.number))
                .addWebsite(ContactBuilder.WebsiteUrl.createProfile(info.url))
                .addAddress(ContactBuilder.Address.createFormattedHome(info.address))
                .build());
          }
          return result;
      }
    } catch (IOException e) {
      Log.e(TAG, "People lookup failed", e);
    }
    return null;
  }
}
