/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.dialer.oem;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProviderBindings;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cequint Caller ID manager to provide caller information.
 *
 * <p>This is only enabled on Motorola devices for Sprint.
 *
 * <p>If it's enabled, this class will be called by call log and incall to get caller info from
 * Cequint Caller ID. It also caches any information fetched in static map, which lives through
 * whole application lifecycle.
 */
@TargetApi(VERSION_CODES.M)
public class CequintCallerIdManager {

  private static final String CONFIG_CALLER_ID_ENABLED = "config_caller_id_enabled";

  private static final String PROVIDER_NAME = "com.cequint.ecid";

  private static final Uri CONTENT_URI = Uri.parse("content://" + PROVIDER_NAME + "/lookup");

  private static final int CALLER_ID_LOOKUP_USER_PROVIDED_CID = 0x0001;
  private static final int CALLER_ID_LOOKUP_SYSTEM_PROVIDED_CID = 0x0002;
  private static final int CALLER_ID_LOOKUP_INCOMING_CALL = 0x0020;

  private static final Uri CONTENT_URI_FOR_INCALL =
      Uri.parse("content://" + PROVIDER_NAME + "/incalllookup");

  private static final String[] EMPTY_PROJECTION = new String[] {};

  // Column names in Cequint provider.
  private static final String CITY_NAME = "cid_pCityName";
  private static final String STATE_NAME = "cid_pStateName";
  private static final String STATE_ABBR = "cid_pStateAbbr";
  private static final String COUNTRY_NAME = "cid_pCountryName";
  private static final String COMPANY = "cid_pCompany";
  private static final String NAME = "cid_pName";
  private static final String FIRST_NAME = "cid_pFirstName";
  private static final String LAST_NAME = "cid_pLastName";
  private static final String IMAGE = "cid_pLogo";
  private static final String DISPLAY_NAME = "cid_pDisplayName";

  private static boolean hasAlreadyCheckedCequintCallerIdPackage;
  private static boolean isCequintCallerIdEnabled;

  // TODO: Revisit it and maybe remove it if it's not necessary.
  private final ConcurrentHashMap<String, CequintCallerIdContact> callLogCache;

  /** Cequint caller id contact information. */
  public static class CequintCallerIdContact {
    public final String name;
    public final String geoDescription;
    public final String imageUrl;

    private CequintCallerIdContact(String name, String geoDescription, String imageUrl) {
      this.name = name;
      this.geoDescription = geoDescription;
      this.imageUrl = imageUrl;
    }
  }

  /** Check whether Cequint Caller Id provider package is available and enabled. */
  @AnyThread
  public static synchronized boolean isCequintCallerIdEnabled(@NonNull Context context) {
    if (!ConfigProviderBindings.get(context).getBoolean(CONFIG_CALLER_ID_ENABLED, true)) {
      return false;
    }
    if (!hasAlreadyCheckedCequintCallerIdPackage) {
      hasAlreadyCheckedCequintCallerIdPackage = true;
      isCequintCallerIdEnabled = false;

      try {
        context.getPackageManager().getPackageInfo(PROVIDER_NAME, 0);
        isCequintCallerIdEnabled = true;
      } catch (PackageManager.NameNotFoundException e) {
        isCequintCallerIdEnabled = false;
      }
    }
    return isCequintCallerIdEnabled;
  }

  public static CequintCallerIdManager createInstanceForCallLog() {
    return new CequintCallerIdManager();
  }

  @WorkerThread
  @Nullable
  public static CequintCallerIdContact getCequintCallerIdContactForInCall(
      Context context, String number, String cnapName, boolean isIncoming) {
    Assert.isWorkerThread();
    LogUtil.d(
        "CequintCallerIdManager.getCequintCallerIdContactForInCall",
        "number: %s, cnapName: %s, isIncoming: %b",
        LogUtil.sanitizePhoneNumber(number),
        LogUtil.sanitizePii(cnapName),
        isIncoming);
    int flag = 0;
    if (isIncoming) {
      flag |= CALLER_ID_LOOKUP_INCOMING_CALL;
      flag |= CALLER_ID_LOOKUP_SYSTEM_PROVIDED_CID;
    } else {
      flag |= CALLER_ID_LOOKUP_USER_PROVIDED_CID;
    }
    String[] flags = {cnapName, String.valueOf(flag)};
    return lookup(context, CONTENT_URI_FOR_INCALL, number, flags);
  }

  @WorkerThread
  @Nullable
  public CequintCallerIdContact getCequintCallerIdContact(Context context, String number) {
    Assert.isWorkerThread();
    LogUtil.d(
        "CequintCallerIdManager.getCequintCallerIdContact",
        "number: %s",
        LogUtil.sanitizePhoneNumber(number));
    if (callLogCache.containsKey(number)) {
      return callLogCache.get(number);
    }
    CequintCallerIdContact cequintCallerIdContact =
        lookup(
            context,
            CONTENT_URI,
            PhoneNumberUtils.stripSeparators(number),
            new String[] {"system"});
    if (cequintCallerIdContact != null) {
      callLogCache.put(number, cequintCallerIdContact);
    }
    return cequintCallerIdContact;
  }

  @WorkerThread
  @Nullable
  private static CequintCallerIdContact lookup(
      Context context, Uri uri, @NonNull String number, String[] flags) {
    Assert.isWorkerThread();
    Assert.isNotNull(number);

    // Cequint is using custom arguments for content provider. See more details in b/35766080.
    try (Cursor cursor =
        context.getContentResolver().query(uri, EMPTY_PROJECTION, number, flags, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        String city = getString(cursor, cursor.getColumnIndex(CITY_NAME));
        String state = getString(cursor, cursor.getColumnIndex(STATE_NAME));
        String stateAbbr = getString(cursor, cursor.getColumnIndex(STATE_ABBR));
        String country = getString(cursor, cursor.getColumnIndex(COUNTRY_NAME));
        String company = getString(cursor, cursor.getColumnIndex(COMPANY));
        String name = getString(cursor, cursor.getColumnIndex(NAME));
        String firstName = getString(cursor, cursor.getColumnIndex(FIRST_NAME));
        String lastName = getString(cursor, cursor.getColumnIndex(LAST_NAME));
        String imageUrl = getString(cursor, cursor.getColumnIndex(IMAGE));
        String displayName = getString(cursor, cursor.getColumnIndex(DISPLAY_NAME));

        String contactName =
            TextUtils.isEmpty(displayName)
                ? generateDisplayName(firstName, lastName, company, name)
                : displayName;
        String geoDescription = getGeoDescription(city, state, stateAbbr, country);
        LogUtil.d(
            "CequintCallerIdManager.lookup",
            "number: %s, contact name: %s, geo: %s, photo url: %s",
            LogUtil.sanitizePhoneNumber(number),
            LogUtil.sanitizePii(contactName),
            LogUtil.sanitizePii(geoDescription),
            imageUrl);
        return new CequintCallerIdContact(contactName, geoDescription, imageUrl);
      } else {
        LogUtil.d("CequintCallerIdManager.lookup", "No CequintCallerIdContact found");
        return null;
      }
    } catch (Exception e) {
      LogUtil.e("CequintCallerIdManager.lookup", "exception on query", e);
      return null;
    }
  }

  private static String getString(Cursor cursor, int columnIndex) {
    if (!cursor.isNull(columnIndex)) {
      String string = cursor.getString(columnIndex);
      if (!TextUtils.isEmpty(string)) {
        return string;
      }
    }
    return null;
  }

  /**
   * Returns generated name from other names, e.g. first name, last name etc. Returns null if there
   * is no other names.
   */
  @Nullable
  private static String generateDisplayName(
      String firstName, String lastName, String company, String name) {
    boolean hasFirstName = !TextUtils.isEmpty(firstName);
    boolean hasLastName = !TextUtils.isEmpty(lastName);
    boolean hasCompanyName = !TextUtils.isEmpty(company);
    boolean hasName = !TextUtils.isEmpty(name);

    StringBuilder stringBuilder = new StringBuilder();

    if (hasFirstName || hasLastName) {
      if (hasFirstName) {
        stringBuilder.append(firstName);
        if (hasLastName) {
          stringBuilder.append(" ");
        }
      }
      if (hasLastName) {
        stringBuilder.append(lastName);
      }
    } else if (hasCompanyName) {
      stringBuilder.append(company);
    } else if (hasName) {
      stringBuilder.append(name);
    } else {
      return null;
    }

    if (stringBuilder.length() > 0) {
      return stringBuilder.toString();
    }
    return null;
  }

  /** Returns geo location information. e.g. Mountain View, CA. */
  private static String getGeoDescription(
      String city, String state, String stateAbbr, String country) {
    String geoDescription = null;

    if (TextUtils.isEmpty(city) && !TextUtils.isEmpty(state)) {
      geoDescription = state;
    } else if (!TextUtils.isEmpty(city) && !TextUtils.isEmpty(stateAbbr)) {
      geoDescription = city + ", " + stateAbbr;
    } else if (!TextUtils.isEmpty(country)) {
      geoDescription = country;
    }
    return geoDescription;
  }

  private CequintCallerIdManager() {
    callLogCache = new ConcurrentHashMap<>();
  }
}
