/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.location;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.os.UserManagerCompat;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.util.PermissionsUtil;
import java.util.List;
import java.util.Locale;

/**
 * This class is used to detect the country where the user is. It is a simplified version of the
 * country detector service in the framework. The sources of country location are queried in the
 * following order of reliability:
 *
 * <ul>
 *   <li>Mobile network
 *   <li>Location manager
 *   <li>SIM's country
 *   <li>User's default locale
 * </ul>
 *
 * As far as possible this class tries to replicate the behavior of the system's country detector
 * service: 1) Order in priority of sources of country location 2) Mobile network information
 * provided by CDMA phones is ignored 3) Location information is updated every 12 hours (instead of
 * 24 hours in the system) 4) Location updates only uses the {@link
 * LocationManager#PASSIVE_PROVIDER} to avoid active use of the GPS 5) If a location is successfully
 * obtained and geocoded, we never fall back to use of the SIM's country (for the system, the
 * fallback never happens without a reboot) 6) Location is not used if the device does not implement
 * a {@link android.location.Geocoder}
 */
public class CountryDetector {
  private static final String KEY_PREFERENCE_TIME_UPDATED = "preference_time_updated";
  static final String KEY_PREFERENCE_CURRENT_COUNTRY = "preference_current_country";
  // Wait 12 hours between updates
  private static final long TIME_BETWEEN_UPDATES_MS = 1000L * 60 * 60 * 12;
  // Minimum distance before an update is triggered, in meters. We don't need this to be too
  // exact because all we care about is what country the user is in.
  private static final long DISTANCE_BETWEEN_UPDATES_METERS = 5000;
  // Used as a default country code when all the sources of country data have failed in the
  // exceedingly rare event that the device does not have a default locale set for some reason.
  private static final String DEFAULT_COUNTRY_ISO = "US";

  @VisibleForTesting static CountryDetector sInstance;

  private final TelephonyManager telephonyManager;
  private final LocaleProvider localeProvider;
  private final Geocoder geocoder;
  private final Context appContext;

  @VisibleForTesting
  CountryDetector(
      Context appContext,
      TelephonyManager telephonyManager,
      LocationManager locationManager,
      LocaleProvider localeProvider,
      Geocoder geocoder) {
    this.telephonyManager = telephonyManager;
    this.localeProvider = localeProvider;
    this.appContext = appContext;
    this.geocoder = geocoder;

    // If the device does not implement Geocoder there is no point trying to get location updates
    // because we cannot retrieve the country based on the location anyway.
    if (Geocoder.isPresent()) {
      registerForLocationUpdates(appContext, locationManager);
    }
  }

  private static void registerForLocationUpdates(Context context, LocationManager locationManager) {
    if (!PermissionsUtil.hasLocationPermissions(context)) {
      LogUtil.w(
          "CountryDetector.registerForLocationUpdates",
          "no location permissions, not registering for location updates");
      return;
    }

    LogUtil.i("CountryDetector.registerForLocationUpdates", "registering for location updates");

    final Intent activeIntent = new Intent(context, LocationChangedReceiver.class);
    final PendingIntent pendingIntent =
        PendingIntent.getBroadcast(context, 0, activeIntent, PendingIntent.FLAG_UPDATE_CURRENT);

    locationManager.requestLocationUpdates(
        LocationManager.PASSIVE_PROVIDER,
        TIME_BETWEEN_UPDATES_MS,
        DISTANCE_BETWEEN_UPDATES_METERS,
        pendingIntent);
  }

  /** @return the single instance of the {@link CountryDetector} */
  public static synchronized CountryDetector getInstance(Context context) {
    if (sInstance == null) {
      Context appContext = context.getApplicationContext();
      sInstance =
          new CountryDetector(
              appContext,
              (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE),
              (LocationManager) context.getSystemService(Context.LOCATION_SERVICE),
              Locale::getDefault,
              new Geocoder(appContext));
    }
    return sInstance;
  }

  public String getCurrentCountryIso() {
    String result = null;
    if (isNetworkCountryCodeAvailable()) {
      result = getNetworkBasedCountryIso();
    }
    if (TextUtils.isEmpty(result)) {
      result = getLocationBasedCountryIso();
    }
    if (TextUtils.isEmpty(result)) {
      result = getSimBasedCountryIso();
    }
    if (TextUtils.isEmpty(result)) {
      result = getLocaleBasedCountryIso();
    }
    if (TextUtils.isEmpty(result)) {
      result = DEFAULT_COUNTRY_ISO;
    }
    return result.toUpperCase(Locale.US);
  }

  /** @return the country code of the current telephony network the user is connected to. */
  private String getNetworkBasedCountryIso() {
    return telephonyManager.getNetworkCountryIso();
  }

  /** @return the geocoded country code detected by the {@link LocationManager}. */
  @Nullable
  private String getLocationBasedCountryIso() {
    if (!Geocoder.isPresent()
        || !PermissionsUtil.hasLocationPermissions(appContext)
        || !UserManagerCompat.isUserUnlocked(appContext)) {
      return null;
    }
    return PreferenceManager.getDefaultSharedPreferences(appContext)
        .getString(KEY_PREFERENCE_CURRENT_COUNTRY, null);
  }

  /** @return the country code of the SIM card currently inserted in the device. */
  private String getSimBasedCountryIso() {
    return telephonyManager.getSimCountryIso();
  }

  /** @return the country code of the user's currently selected locale. */
  private String getLocaleBasedCountryIso() {
    Locale defaultLocale = localeProvider.getLocale();
    if (defaultLocale != null) {
      return defaultLocale.getCountry();
    }
    return null;
  }

  private boolean isNetworkCountryCodeAvailable() {
    // On CDMA TelephonyManager.getNetworkCountryIso() just returns the SIM's country code.
    // In this case, we want to ignore the value returned and fallback to location instead.
    return telephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM;
  }

  /** Interface for accessing the current locale. */
  interface LocaleProvider {
    Locale getLocale();
  }

  public static class LocationChangedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
      if (!intent.hasExtra(LocationManager.KEY_LOCATION_CHANGED)) {
        return;
      }

      final Location location =
          (Location) intent.getExtras().get(LocationManager.KEY_LOCATION_CHANGED);

      // TODO: rething how we access the gecoder here, right now we have to set the static instance
      // of CountryDetector to make this work for tests which is weird
      // (see CountryDetectorTest.locationChangedBroadcast_GeocodesLocation)
      processLocationUpdate(context, CountryDetector.getInstance(context).geocoder, location);
    }
  }

  private static void processLocationUpdate(
      Context appContext, Geocoder geocoder, Location location) {
    DialerExecutorComponent.get(appContext)
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(new GeocodeCountryWorker(geocoder))
        .onSuccess(
            country -> {
              if (country == null) {
                return;
              }

              PreferenceManager.getDefaultSharedPreferences(appContext)
                  .edit()
                  .putLong(CountryDetector.KEY_PREFERENCE_TIME_UPDATED, System.currentTimeMillis())
                  .putString(CountryDetector.KEY_PREFERENCE_CURRENT_COUNTRY, country)
                  .apply();
            })
        .onFailure(
            throwable ->
                LogUtil.w(
                    "CountryDetector.processLocationUpdate",
                    "exception occurred when getting geocoded country from location",
                    throwable))
        .build()
        .executeParallel(location);
  }

  /** Worker that given a {@link Location} returns an ISO 3166-1 two letter country code. */
  private static class GeocodeCountryWorker implements Worker<Location, String> {
    @NonNull private final Geocoder geocoder;

    GeocodeCountryWorker(@NonNull Geocoder geocoder) {
      this.geocoder = Assert.isNotNull(geocoder);
    }

    /** @return the ISO 3166-1 two letter country code if geocoded, else null */
    @Nullable
    @Override
    public String doInBackground(@Nullable Location location) throws Throwable {
      if (location == null) {
        return null;
      }

      List<Address> addresses =
          geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
      if (addresses != null && !addresses.isEmpty()) {
        return addresses.get(0).getCountryCode();
      }
      return null;
    }
  }
}
