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
 * limitations under the License
 */

package com.android.incallui.calllocation.impl;

import android.content.Context;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.support.annotation.IntDef;
import android.support.annotation.MainThread;
import android.support.v4.os.UserManagerCompat;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.util.PermissionsUtil;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/** Uses the Fused location service to get location and pass updates on to listeners. */
public class LocationHelper {

  private static final int FAST_MIN_UPDATE_INTERVAL_MS = 5 * 1000;
  private static final int SLOW_MIN_UPDATE_INTERVAL_MS = 30 * 1000;
  private static final int LAST_UPDATE_THRESHOLD_MS = 60 * 1000;
  private static final int LOCATION_ACCURACY_THRESHOLD_METERS = 100;

  public static final int LOCATION_STATUS_UNKNOWN = 0;
  public static final int LOCATION_STATUS_OK = 1;
  public static final int LOCATION_STATUS_STALE = 2;
  public static final int LOCATION_STATUS_INACCURATE = 3;
  public static final int LOCATION_STATUS_NO_LOCATION = 4;
  public static final int LOCATION_STATUS_MOCK = 5;

  /** Possible return values for {@code checkLocation()} */
  @IntDef({
    LOCATION_STATUS_UNKNOWN,
    LOCATION_STATUS_OK,
    LOCATION_STATUS_STALE,
    LOCATION_STATUS_INACCURATE,
    LOCATION_STATUS_NO_LOCATION,
    LOCATION_STATUS_MOCK
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface LocationStatus {}

  private final LocationHelperInternal locationHelperInternal;
  private final List<LocationListener> listeners = new ArrayList<>();

  @MainThread
  LocationHelper(Context context) {
    Assert.isMainThread();
    Assert.checkArgument(canGetLocation(context));
    locationHelperInternal = new LocationHelperInternal(context);
  }

  static boolean canGetLocation(Context context) {
    if (!PermissionsUtil.hasLocationPermissions(context)) {
      LogUtil.i("LocationHelper.canGetLocation", "no location permissions.");
      return false;
    }

    // Ensure that both system location setting is on and google location services are enabled.
    if (!GoogleLocationSettingHelper.isGoogleLocationServicesEnabled(context)
        || !GoogleLocationSettingHelper.isSystemLocationSettingEnabled(context)) {
      LogUtil.i("LocationHelper.canGetLocation", "location service is disabled.");
      return false;
    }

    if (!UserManagerCompat.isUserUnlocked(context)) {
      LogUtil.i("LocationHelper.canGetLocation", "location unavailable in FBE mode.");
      return false;
    }

    return true;
  }

  /**
   * Check whether the location is valid. We consider it valid if it was recorded within the
   * specified time threshold of the present and has an accuracy less than the specified distance
   * threshold.
   *
   * @param location The location to determine the validity of.
   * @return {@code LocationStatus} indicating if the location is valid or the reason its not valid
   */
  static @LocationStatus int checkLocation(Location location) {
    if (location == null) {
      LogUtil.i("LocationHelper.checkLocation", "no location");
      return LOCATION_STATUS_NO_LOCATION;
    }

    long locationTimeMs = location.getTime();
    long elapsedTimeMs = System.currentTimeMillis() - locationTimeMs;
    if (elapsedTimeMs > LAST_UPDATE_THRESHOLD_MS) {
      LogUtil.i("LocationHelper.checkLocation", "stale location, age: " + elapsedTimeMs);
      return LOCATION_STATUS_STALE;
    }

    if (location.getAccuracy() > LOCATION_ACCURACY_THRESHOLD_METERS) {
      LogUtil.i("LocationHelper.checkLocation", "poor accuracy: " + location.getAccuracy());
      return LOCATION_STATUS_INACCURATE;
    }

    if (location.isFromMockProvider()) {
      LogUtil.i("LocationHelper.checkLocation", "from mock provider");
      return LOCATION_STATUS_MOCK;
    }

    return LOCATION_STATUS_OK;
  }

  @MainThread
  void addLocationListener(LocationListener listener) {
    Assert.isMainThread();
    listeners.add(listener);
  }

  @MainThread
  void removeLocationListener(LocationListener listener) {
    Assert.isMainThread();
    listeners.remove(listener);
  }

  @MainThread
  void close() {
    Assert.isMainThread();
    LogUtil.enterBlock("LocationHelper.close");
    listeners.clear();
    locationHelperInternal.close();
  }

  @MainThread
  void onLocationChanged(Location location, boolean isConnected) {
    Assert.isMainThread();
    LogUtil.i("LocationHelper.onLocationChanged", "location: " + location);

    for (LocationListener listener : listeners) {
      listener.onLocationChanged(location);
    }
  }

  /**
   * This class contains all the asynchronous callbacks. It only posts location changes back to the
   * outer class on the main thread.
   */
  private class LocationHelperInternal implements LocationListener {

    private final FusedLocationProviderClient locationClient;
    private final ConnectivityManager connectivityManager;
    private final Handler mainThreadHandler = new Handler();
    private boolean gotGoodLocation;

    @MainThread
    LocationHelperInternal(Context context) {
      Assert.isMainThread();
      locationClient = LocationServices.getFusedLocationProviderClient(context);
      connectivityManager =
          (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      requestUpdates();
      getLocation();
    }

    void close() {
      LogUtil.enterBlock("LocationHelperInternal.close");
      locationClient.removeLocationUpdates(this);
    }

    private void requestUpdates() {
      LogUtil.enterBlock("LocationHelperInternal.requestUpdates");

      int interval = gotGoodLocation ? SLOW_MIN_UPDATE_INTERVAL_MS : FAST_MIN_UPDATE_INTERVAL_MS;
      LocationRequest locationRequest =
          LocationRequest.create()
              .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
              .setInterval(interval)
              .setFastestInterval(interval);

      locationClient
          .requestLocationUpdates(locationRequest, this)
          .addOnSuccessListener(
              result -> LogUtil.i("LocationHelperInternal.requestUpdates", "onSuccess"))
          .addOnFailureListener(
              e -> LogUtil.e("LocationHelperInternal.requestUpdates", "onFailure", e));
    }

    private void getLocation() {
      LogUtil.enterBlock("LocationHelperInternal.getLocation");

      locationClient
          .getLastLocation()
          .addOnSuccessListener(
              location -> {
                LogUtil.i("LocationHelperInternal.getLocation", "onSuccess");
                Assert.isMainThread();
                LocationHelper.this.onLocationChanged(location, isConnected());
                maybeAdjustUpdateInterval(location);
              })
          .addOnFailureListener(
              e -> LogUtil.e("LocationHelperInternal.getLocation", "onFailure", e));
    }

    @Override
    public void onLocationChanged(Location location) {
      // Post new location on main thread
      mainThreadHandler.post(
          new Runnable() {
            @Override
            public void run() {
              LocationHelper.this.onLocationChanged(location, isConnected());
              maybeAdjustUpdateInterval(location);
            }
          });
    }

    private void maybeAdjustUpdateInterval(Location location) {
      if (!gotGoodLocation && checkLocation(location) == LOCATION_STATUS_OK) {
        LogUtil.i("LocationHelperInternal.maybeAdjustUpdateInterval", "got good location");
        gotGoodLocation = true;
        requestUpdates();
      }
    }

    /** @return Whether the phone is connected to data. */
    private boolean isConnected() {
      if (connectivityManager == null) {
        return false;
      }
      NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
      return networkInfo != null && networkInfo.isConnectedOrConnecting();
    }
  }
}
