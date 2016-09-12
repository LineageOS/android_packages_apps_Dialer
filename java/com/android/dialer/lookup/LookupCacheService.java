/*
 * Copyright (C) 2018 The LineageOS Project
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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;

import com.android.dialer.logging.ContactSource;
import com.android.dialer.phonenumbercache.CachedNumberLookupService;
import com.android.dialer.phonenumbercache.ContactInfo;

import java.io.InputStream;

public class LookupCacheService implements CachedNumberLookupService {
  @Override
  public CachedContactInfo buildCachedContactInfo(ContactInfo info) {
    return new LookupCachedContactInfo(info);
  }

  @Override
  public void addContact(Context context, CachedContactInfo cachedInfo) {
    LookupCache.cacheContact(context, cachedInfo.getContactInfo());
  }

  @Override
  public CachedContactInfo lookupCachedContactFromNumber(Context context, String number) {
    ContactInfo info = LookupCache.getCachedContact(context, number);
    return info != null ? new LookupCachedContactInfo(info) : null;
  }

  @Override
  public void clearAllCacheEntries(Context context) {
    LookupCache.deleteCachedContacts(context);
  }

  @Override
  public boolean isBusiness(ContactSource.Type sourceType) {
    // We don't store source type, so assume false
    return false;
  }

  @Override
  public boolean canReportAsInvalid(ContactSource.Type sourceType, String objectId) {
    return false;
  }

  @Override
  public boolean reportAsInvalid(Context context, CachedContactInfo cachedContactInfo) {
    return false;
  }

  @Override
  public @Nullable Uri addPhoto(Context context, String number, InputStream in) {
    TelephonyManager tm = context.getSystemService(TelephonyManager.class);
    String countryIso = tm.getSimCountryIso().toUpperCase();
    String normalized = number != null
        ? PhoneNumberUtils.formatNumberToE164(number, countryIso) : null;
    if (normalized != null) {
      Bitmap bitmap = BitmapFactory.decodeStream(in, null, null);
      if (bitmap != null) {
        return LookupCache.cacheImage(context, normalized, bitmap);
      }
    }
    return null;
  }

  private static class LookupCachedContactInfo implements CachedContactInfo {
    private final ContactInfo info;

    private LookupCachedContactInfo(ContactInfo info) {
      this.info = info;
    }

    @Override
    @NonNull public ContactInfo getContactInfo() {
      return info;
    }

    @Override
    public void setSource(ContactSource.Type sourceType, String name, long directoryId) {
    }

    @Override
    public void setDirectorySource(String name, long directoryId) {
    }

    @Override
    public void setLookupKey(String lookupKey) {
    }
  }
}
