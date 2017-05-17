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

import android.animation.LayoutTransition;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewAnimator;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.incallui.baseui.BaseFragment;
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

  // Indexes used to animate fading between views
  private static final int LOADING_VIEW_INDEX = 0;
  private static final int LOCATION_VIEW_INDEX = 1;
  private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(5);

  private ViewAnimator viewAnimator;
  private ImageView locationMap;
  private TextView addressLine1;
  private TextView addressLine2;
  private TextView latLongLine;
  private Location location;
  private ViewGroup locationLayout;

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
    locationMap = (ImageView) view.findViewById(R.id.location_map);
    addressLine1 = (TextView) view.findViewById(R.id.address_line_one);
    addressLine2 = (TextView) view.findViewById(R.id.address_line_two);
    latLongLine = (TextView) view.findViewById(R.id.lat_long_line);
    locationLayout = (ViewGroup) view.findViewById(R.id.location_layout);
    return view;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    handler.removeCallbacks(dataTimeoutRunnable);
  }

  @Override
  public void setMap(Drawable mapImage) {
    LogUtil.enterBlock("LocationFragment.setMap");
    isMapSet = true;
    locationMap.setVisibility(View.VISIBLE);
    locationMap.setImageDrawable(mapImage);
    displayWhenReady();
    Logger.get(getContext()).logImpression(DialerImpression.Type.EMERGENCY_GOT_MAP);
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
    }
    displayWhenReady();
  }

  private void displayWhenReady() {
    // Show the location if all data has loaded, otherwise prime the timeout
    if (isMapSet && isAddressSet && isLocationSet) {
      showLocationNow();
    } else if (!hasTimeoutStarted) {
      handler.postDelayed(dataTimeoutRunnable, TIMEOUT_MILLIS);
      hasTimeoutStarted = true;
    }
  }

  private void showLocationNow() {
    handler.removeCallbacks(dataTimeoutRunnable);
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
}
