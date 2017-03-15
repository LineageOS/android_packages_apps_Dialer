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
 * limitations under the License
 */

package com.android.incallui.maps.impl;

import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

/** Shows a static map centered on a specified location */
public class StaticMapFragment extends Fragment implements OnMapReadyCallback {

  private static final String ARG_LOCATION = "location";

  public static StaticMapFragment newInstance(@NonNull Location location) {
    Bundle args = new Bundle();
    args.putParcelable(ARG_LOCATION, Assert.isNotNull(location));
    StaticMapFragment fragment = new StaticMapFragment();
    fragment.setArguments(args);
    return fragment;
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup, @Nullable Bundle bundle) {
    return layoutInflater.inflate(R.layout.static_map_fragment, viewGroup, false);
  }

  @Override
  public void onViewCreated(View view, @Nullable Bundle bundle) {
    super.onViewCreated(view, bundle);
    SupportMapFragment mapFragment =
        (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.static_map);
    if (mapFragment != null) {
      mapFragment.getMapAsync(this);
    } else {
      LogUtil.w("StaticMapFragment.onViewCreated", "No map fragment found!");
    }
  }

  @Override
  public void onMapReady(GoogleMap googleMap) {
    Location location = getArguments().getParcelable(ARG_LOCATION);
    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
    googleMap.addMarker(new MarkerOptions().position(latLng).flat(true).draggable(false));
    googleMap.getUiSettings().setMapToolbarEnabled(false);
    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f));
  }
}
