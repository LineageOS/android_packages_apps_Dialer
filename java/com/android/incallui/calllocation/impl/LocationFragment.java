/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.animation.LayoutTransition;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ViewAnimator;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.incallui.baseui.BaseFragment;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Fragment which shows location during E911 calls, to supplement the user with accurate location
 * information in case the user is asked for their location by the emergency responder.
 *
 * <p>If location data is inaccurate, stale, or unavailable, this should not be shown.
 */
public class LocationFragment extends BaseFragment<LocationPresenter, LocationPresenter.LocationUi>
    implements LocationPresenter.LocationUi {

  private static final String ADDRESS_DELIMITER = ",";

  // Indexes used to animate fading between views, 0 for LOADING_VIEW_INDEX
  private static final int LOCATION_VIEW_INDEX = 1;
  private static final int LOCATION_ERROR_INDEX = 2;

  private static final long FIND_LOCATION_SPINNING_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(5);
  private static final long LOAD_DATA_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(5);

  private static final float MAP_ZOOM_LEVEL = 15f;

  private ViewAnimator viewAnimator;
  private MapView locationMapView;
  private TextView addressLine1;
  private TextView addressLine2;
  private TextView latLongLine;
  private Location location;
  private ViewGroup locationLayout;
  private GoogleMap savedGoogleMap;

  private boolean isMapSet;
  private boolean isAddressSet;
  private boolean isLocationSet;
  private boolean hasTimeoutStarted;

  private final Handler handler = new Handler();
  private final Runnable dataTimeoutRunnable =
      () -> {
        LogUtil.i(
            "LocationFragment.dataTimeoutRunnable",
            "timed out so animate any future layout changes");
        locationLayout.setLayoutTransition(new LayoutTransition());
        showLocationNow();
      };

  private final Runnable spinningTimeoutRunnable =
      () -> {
        if (!(isAddressSet || isLocationSet || isMapSet)) {
          // No data received, show error
          viewAnimator.setDisplayedChild(LOCATION_ERROR_INDEX);
        }
      };

  @Override
  public LocationPresenter createPresenter() {
    return new LocationPresenter();
  }

  @Override
  public LocationPresenter.LocationUi getUi() {
    return this;
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    LogUtil.enterBlock("LocationFragment.onCreateView");
    final View view = inflater.inflate(R.layout.location_fragment, container, false);
    viewAnimator = (ViewAnimator) view.findViewById(R.id.location_view_animator);
    addressLine1 = (TextView) view.findViewById(R.id.address_line_one);
    addressLine2 = (TextView) view.findViewById(R.id.address_line_two);
    latLongLine = (TextView) view.findViewById(R.id.lat_long_line);
    locationLayout = (ViewGroup) view.findViewById(R.id.location_layout);
    locationMapView = (MapView) view.findViewById(R.id.location_map_view);
    locationMapView.onCreate(savedInstanceState);
    return view;
  }

  @Override
  public void onStart() {
    super.onStart();
    handler.postDelayed(spinningTimeoutRunnable, FIND_LOCATION_SPINNING_TIMEOUT_MILLIS);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    handler.removeCallbacks(dataTimeoutRunnable);
    handler.removeCallbacks(spinningTimeoutRunnable);
  }

  private void setMap(@NonNull Location location) {
    LogUtil.enterBlock("LocationFragment.setMap");
    Assert.isNotNull(location);

    if (savedGoogleMap == null) {
      locationMapView.getMapAsync(
          (googleMap) -> {
            LogUtil.enterBlock("LocationFragment.onMapReady");
            savedGoogleMap = googleMap;
            savedGoogleMap.getUiSettings().setMapToolbarEnabled(false);
            updateMap(location);
            isMapSet = true;
            locationMapView.setVisibility(View.VISIBLE);

            // Hide Google logo
            View child = locationMapView.getChildAt(0);
            if (child instanceof ViewGroup) {
              // Only the first child (View) is useful.
              // Google logo can be in any other child (ViewGroup).
              for (int i = 1; i < ((ViewGroup) child).getChildCount(); ++i) {
                ((ViewGroup) child).getChildAt(i).setVisibility(View.GONE);
              }
            }
          });
    } else {
      updateMap(location);
    }
    displayWhenReady();
    Logger.get(getContext()).logImpression(DialerImpression.Type.EMERGENCY_GOT_MAP);
  }

  private void updateMap(@NonNull Location location) {
    Assert.isNotNull(location);
    Assert.isNotNull(savedGoogleMap);
    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
    savedGoogleMap.clear();
    savedGoogleMap.addMarker(new MarkerOptions().position(latLng).flat(true).draggable(false));
    savedGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, MAP_ZOOM_LEVEL));
  }

  @Override
  public void setAddress(String address) {
    LogUtil.i("LocationFragment.setAddress", address);
    isAddressSet = true;
    addressLine1.setVisibility(View.VISIBLE);
    addressLine2.setVisibility(View.VISIBLE);
    if (TextUtils.isEmpty(address)) {
      addressLine1.setText(null);
      addressLine2.setText(null);
    } else {

      // Split the address after the first delimiter for display, if present.
      // For example, "1600 Amphitheatre Parkway, Mountain View, CA 94043"
      //     => "1600 Amphitheatre Parkway"
      //     => "Mountain View, CA 94043"
      int splitIndex = address.indexOf(ADDRESS_DELIMITER);
      if (splitIndex >= 0) {
        updateText(addressLine1, address.substring(0, splitIndex).trim());
        updateText(addressLine2, address.substring(splitIndex + 1).trim());
      } else {
        updateText(addressLine1, address);
        updateText(addressLine2, null);
      }

      Logger.get(getContext()).logImpression(DialerImpression.Type.EMERGENCY_GOT_ADDRESS);
    }
    displayWhenReady();
  }

  @Override
  public void setLocation(Location location) {
    LogUtil.i("LocationFragment.setLocation", String.valueOf(location));
    isLocationSet = true;
    this.location = location;

    if (location != null) {
      latLongLine.setVisibility(View.VISIBLE);
      latLongLine.setText(
          getContext()
              .getString(
                  R.string.lat_long_format, location.getLatitude(), location.getLongitude()));

      Logger.get(getContext()).logImpression(DialerImpression.Type.EMERGENCY_GOT_LOCATION);
      setMap(location);
    }
    displayWhenReady();
  }

  private void displayWhenReady() {
    // Show the location if all data has loaded, otherwise prime the timeout
    if (isMapSet && isAddressSet && isLocationSet) {
      showLocationNow();
    } else if (!hasTimeoutStarted) {
      handler.postDelayed(dataTimeoutRunnable, LOAD_DATA_TIMEOUT_MILLIS);
      hasTimeoutStarted = true;
    }
  }

  private void showLocationNow() {
    handler.removeCallbacks(dataTimeoutRunnable);
    handler.removeCallbacks(spinningTimeoutRunnable);
    if (viewAnimator.getDisplayedChild() != LOCATION_VIEW_INDEX) {
      viewAnimator.setDisplayedChild(LOCATION_VIEW_INDEX);
      viewAnimator.setOnClickListener(v -> launchMap());
    }
  }

  @Override
  public Context getContext() {
    return getActivity();
  }

  private void launchMap() {
    if (location != null) {
      startActivity(
          LocationUrlBuilder.getShowMapIntent(
              location, addressLine1.getText(), addressLine2.getText()));

      Logger.get(getContext()).logImpression(DialerImpression.Type.EMERGENCY_LAUNCHED_MAP);
    }
  }

  private static void updateText(TextView view, String text) {
    if (!Objects.equals(text, view.getText())) {
      view.setText(text);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    locationMapView.onResume();
  }

  @Override
  public void onPause() {
    locationMapView.onPause();
    super.onPause();
  }
}
