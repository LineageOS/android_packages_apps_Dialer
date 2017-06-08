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
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.IntDef;
import android.support.annotation.MainThread;
import android.support.v4.os.UserManagerCompat;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.util.PermissionsUtil;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/** Uses the Fused location service to get location and pass updates on to listeners. */
public class LocationHelper {

  private static final int MIN_UPDATE_INTERVAL_MS = 30 * 1000;
  private static final int LAST_UPDATE_THRESHOLD_MS = 60 * 1000;
  private static final int LOCATION_ACCURACY_THRESHOLD_METERS = 100;

  public static final int LOCATION_STATUS_UNKNOWN = 0;
  public static final int LOCATION_STATUS_OK = 1;
  public static final int LOCATION_STATUS_STALE = 2;
  public static final int LOCATION_STATUS_INACCURATE = 3;
  public static final int LOCATION_STATUS_NO_LOCATION = 4;

  /** Possible return values for {@code checkLocation()} */
  @IntDef({
    LOCATION_STATUS_UNKNOWN,
    LOCATION_STATUS_OK,
    LOCATION_STATUS_STALE,
    LOCATION_STATUS_INACCURATE,
    LOCATION_STATUS_NO_LOCATION
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

    if (locationHelperInternal != null) {
      locationHelperInternal.close();
    }
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
  private class LocationHelperInternal
      implements ConnectionCallbacks, OnConnectionFailedListener, LocationListener {

    private final GoogleApiClient apiClient;
    private final ConnectivityManager connectivityManager;
    private final Handler mainThreadHandler = new Handler();

    @MainThread
    LocationHelperInternal(Context context) {
      Assert.isMainThread();
      apiClient =
          new GoogleApiClient.Builder(context)
              .addApi(LocationServices.API)
              .addConnectionCallbacks(this)
              .addOnConnectionFailedListener(this)
              .build();

      LogUtil.i("LocationHelperInternal", "Connecting to location service...");
      apiClient.connect();

      connectivityManager =
          (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    void close() {
      if (apiClient.isConnected()) {
        LogUtil.i("LocationHelperInternal", "disconnecting");
        LocationServices.FusedLocationApi.removeLocationUpdates(apiClient, this);
        apiClient.disconnect();
      }
    }

    @Override
    public void onConnected(Bundle bundle) {
      LogUtil.enterBlock("LocationHelperInternal.onConnected");
      LocationRequest locationRequest =
          LocationRequest.create()
              .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
              .setInterval(MIN_UPDATE_INTERVAL_MS)
              .setFastestInterval(MIN_UPDATE_INTERVAL_MS);

      LocationServices.FusedLocationApi.requestLocationUpdates(apiClient, locationRequest, this)
          .setResultCallback(
              new ResultCallback<Status>() {
                @Override
                public void onResult(Status status) {
                  if (status.getStatus().isSuccess()) {
                    onLocationChanged(LocationServices.FusedLocationApi.getLastLocation(apiClient));
                  }
                }
              });
    }

    @Override
    public void onConnectionSuspended(int i) {
      // Do nothing.
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
      // Do nothing.
    }

    @Override
    public void onLocationChanged(Location location) {
      // Post new location on main thread
      mainThreadHandler.post(
          new Runnable() {
            @Override
            public void run() {
              LocationHelper.this.onLocationChanged(location, isConnected());
            }
          });
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
