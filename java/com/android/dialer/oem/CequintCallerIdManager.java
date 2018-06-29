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

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProviderComponent;
import com.google.auto.value.AutoValue;
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
public class CequintCallerIdManager {

  @VisibleForTesting
  public static final String CONFIG_CALLER_ID_ENABLED = "config_caller_id_enabled";

  private static final int CALLER_ID_LOOKUP_USER_PROVIDED_CID = 0x0001;
  private static final int CALLER_ID_LOOKUP_SYSTEM_PROVIDED_CID = 0x0002;
  private static final int CALLER_ID_LOOKUP_INCOMING_CALL = 0x0020;

  private static final String[] EMPTY_PROJECTION = new String[] {};

  /** Column names in Cequint content provider. */
  @VisibleForTesting
  public static final class CequintColumnNames {
    public static final String CITY_NAME = "cid_pCityName";
    public static final String STATE_NAME = "cid_pStateName";
    public static final String STATE_ABBR = "cid_pStateAbbr";
    public static final String COUNTRY_NAME = "cid_pCountryName";
    public static final String COMPANY = "cid_pCompany";
    public static final String NAME = "cid_pName";
    public static final String FIRST_NAME = "cid_pFirstName";
    public static final String LAST_NAME = "cid_pLastName";
    public static final String PHOTO_URI = "cid_pLogo";
    public static final String DISPLAY_NAME = "cid_pDisplayName";
  }

  private static boolean hasAlreadyCheckedCequintCallerIdPackage;
  private static String cequintProviderAuthority;

  // TODO(a bug): Revisit it and maybe remove it if it's not necessary.
  private final ConcurrentHashMap<String, CequintCallerIdContact> callLogCache =
      new ConcurrentHashMap<>();

  /** Cequint caller ID contact information. */
  @AutoValue
  public abstract static class CequintCallerIdContact {

    @Nullable
    public abstract String name();

    /**
     * Description of the geolocation (e.g., "Mountain View, CA"), which is for display purpose
     * only.
     */
    @Nullable
    public abstract String geolocation();

    @Nullable
    public abstract String photoUri();

    static Builder builder() {
      return new AutoValue_CequintCallerIdManager_CequintCallerIdContact.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setName(@Nullable String name);

      abstract Builder setGeolocation(@Nullable String geolocation);

      abstract Builder setPhotoUri(@Nullable String photoUri);

      abstract CequintCallerIdContact build();
    }
  }

  /** Check whether Cequint Caller ID provider package is available and enabled. */
  @AnyThread
  public static synchronized boolean isCequintCallerIdEnabled(@NonNull Context context) {
    if (!ConfigProviderComponent.get(context)
        .getConfigProvider()
        .getBoolean(CONFIG_CALLER_ID_ENABLED, true)) {
      return false;
    }
    if (!hasAlreadyCheckedCequintCallerIdPackage) {
      hasAlreadyCheckedCequintCallerIdPackage = true;

      String[] providerNames = context.getResources().getStringArray(R.array.cequint_providers);
      PackageManager packageManager = context.getPackageManager();
      for (String provider : providerNames) {
        if (CequintPackageUtils.isCallerIdInstalled(packageManager, provider)) {
          cequintProviderAuthority = provider;
          LogUtil.i(
              "CequintCallerIdManager.isCequintCallerIdEnabled", "found provider: %s", provider);
          return true;
        }
      }
      LogUtil.d("CequintCallerIdManager.isCequintCallerIdEnabled", "no provider found");
    }
    return cequintProviderAuthority != null;
  }

  /** Returns a {@link CequintCallerIdContact} for a call. */
  @WorkerThread
  @Nullable
  public static CequintCallerIdContact getCequintCallerIdContactForCall(
      Context context, String number, String cnapName, boolean isIncoming) {
    Assert.isWorkerThread();
    LogUtil.d(
        "CequintCallerIdManager.getCequintCallerIdContactForCall",
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
    return lookup(context, getIncallLookupUri(), number, flags);
  }

  /**
   * Returns a cached {@link CequintCallerIdContact} associated with the provided number. If no
   * contact can be found in the cache, look up the number using the Cequint content provider.
   *
   * @deprecated This method is for the old call log only. New code should use {@link
   *     #getCequintCallerIdContactForNumber(Context, String)}.
   */
  @Deprecated
  @WorkerThread
  @Nullable
  public CequintCallerIdContact getCachedCequintCallerIdContact(Context context, String number) {
    Assert.isWorkerThread();
    LogUtil.d(
        "CequintCallerIdManager.getCachedCequintCallerIdContact",
        "number: %s",
        LogUtil.sanitizePhoneNumber(number));
    if (callLogCache.containsKey(number)) {
      return callLogCache.get(number);
    }
    CequintCallerIdContact cequintCallerIdContact =
        getCequintCallerIdContactForNumber(context, number);
    if (cequintCallerIdContact != null) {
      callLogCache.put(number, cequintCallerIdContact);
    }
    return cequintCallerIdContact;
  }

  /**
   * Returns a {@link CequintCallerIdContact} associated with the provided number by looking it up
   * using the Cequint content provider.
   */
  @WorkerThread
  @Nullable
  public static CequintCallerIdContact getCequintCallerIdContactForNumber(
      Context context, String number) {
    Assert.isWorkerThread();
    LogUtil.d(
        "CequintCallerIdManager.getCequintCallerIdContactForNumber",
        "number: %s",
        LogUtil.sanitizePhoneNumber(number));

    return lookup(
        context, getLookupUri(), PhoneNumberUtils.stripSeparators(number), new String[] {"system"});
  }

  @WorkerThread
  @Nullable
  private static CequintCallerIdContact lookup(
      Context context, Uri uri, @NonNull String number, String[] flags) {
    Assert.isWorkerThread();
    Assert.isNotNull(number);

    // Cequint is using custom arguments for content provider. See more details in a bug.
    try (Cursor cursor =
        context.getContentResolver().query(uri, EMPTY_PROJECTION, number, flags, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        String city = getString(cursor, cursor.getColumnIndex(CequintColumnNames.CITY_NAME));
        String state = getString(cursor, cursor.getColumnIndex(CequintColumnNames.STATE_NAME));
        String stateAbbr = getString(cursor, cursor.getColumnIndex(CequintColumnNames.STATE_ABBR));
        String country = getString(cursor, cursor.getColumnIndex(CequintColumnNames.COUNTRY_NAME));
        String company = getString(cursor, cursor.getColumnIndex(CequintColumnNames.COMPANY));
        String name = getString(cursor, cursor.getColumnIndex(CequintColumnNames.NAME));
        String firstName = getString(cursor, cursor.getColumnIndex(CequintColumnNames.FIRST_NAME));
        String lastName = getString(cursor, cursor.getColumnIndex(CequintColumnNames.LAST_NAME));
        String photoUri = getString(cursor, cursor.getColumnIndex(CequintColumnNames.PHOTO_URI));
        String displayName =
            getString(cursor, cursor.getColumnIndex(CequintColumnNames.DISPLAY_NAME));

        String contactName =
            TextUtils.isEmpty(displayName)
                ? generateDisplayName(firstName, lastName, company, name)
                : displayName;
        String geolocation = getGeolocation(city, state, stateAbbr, country);
        LogUtil.d(
            "CequintCallerIdManager.lookup",
            "number: %s, contact name: %s, geo: %s, photo url: %s",
            LogUtil.sanitizePhoneNumber(number),
            LogUtil.sanitizePii(contactName),
            LogUtil.sanitizePii(geolocation),
            photoUri);
        return CequintCallerIdContact.builder()
            .setName(contactName)
            .setGeolocation(geolocation)
            .setPhotoUri(photoUri)
            .build();
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

  /** Returns geolocation information (e.g., "Mountain View, CA"). */
  private static String getGeolocation(
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

  private static Uri getLookupUri() {
    return Uri.parse("content://" + cequintProviderAuthority + "/lookup");
  }

  private static Uri getIncallLookupUri() {
    return Uri.parse("content://" + cequintProviderAuthority + "/incalllookup");
  }
}
