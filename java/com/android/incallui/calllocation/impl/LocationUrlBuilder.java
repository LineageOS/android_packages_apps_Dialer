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
import android.content.Intent;
import android.content.res.Resources;
import android.location.Location;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import java.util.Locale;

class LocationUrlBuilder {

  // Static Map API path constants.
  private static final String HTTPS_SCHEME = "https";
  private static final String MAPS_API_DOMAIN = "maps.googleapis.com";
  private static final String MAPS_PATH = "maps";
  private static final String API_PATH = "api";
  private static final String STATIC_MAP_PATH = "staticmap";
  private static final String GEOCODE_PATH = "geocode";
  private static final String GEOCODE_OUTPUT_TYPE = "json";

  // Static Map API parameter constants.
  private static final String KEY_PARAM_KEY = "key";
  private static final String CENTER_PARAM_KEY = "center";
  private static final String ZOOM_PARAM_KEY = "zoom";
  private static final String SCALE_PARAM_KEY = "scale";
  private static final String SIZE_PARAM_KEY = "size";
  private static final String MARKERS_PARAM_KEY = "markers";

  private static final String ZOOM_PARAM_VALUE = Integer.toString(16);

  private static final String LAT_LONG_DELIMITER = ",";

  private static final String MARKER_DELIMITER = "|";
  private static final String MARKER_STYLE_DELIMITER = ":";
  private static final String MARKER_STYLE_COLOR = "color";
  private static final String MARKER_STYLE_COLOR_RED = "red";

  private static final String LAT_LNG_PARAM_KEY = "latlng";

  private static final String ANDROID_API_KEY_VALUE = "AIzaSyAXdDnif6B7sBYxU8hzw9qAp3pRPVHs060";
  private static final String BROWSER_API_KEY_VALUE = "AIzaSyBfLlvWYndiQ3RFEHli65qGQH36QIxdyCI";

  /**
   * Generates the URL to a static map image for the given location.
   *
   * <p>This image has the following characteristics:
   *
   * <p>- It is centered at the given latitude and longitutde. - It is scaled according to the
   * device's pixel density. - There is a red marker at the given latitude and longitude.
   *
   * <p>Source: https://developers.google.com/maps/documentation/staticmaps/
   *
   * @param contxt The context.
   * @param Location A location.
   * @return The URL of a static map image url of the given location.
   */
  public static String getStaticMapUrl(Context context, Location location) {
    final Uri.Builder builder = new Uri.Builder();
    Resources res = context.getResources();
    String size =
        res.getDimensionPixelSize(R.dimen.location_map_width)
            + "x"
            + res.getDimensionPixelSize(R.dimen.location_map_height);

    builder
        .scheme(HTTPS_SCHEME)
        .authority(MAPS_API_DOMAIN)
        .appendPath(MAPS_PATH)
        .appendPath(API_PATH)
        .appendPath(STATIC_MAP_PATH)
        .appendQueryParameter(CENTER_PARAM_KEY, getFormattedLatLng(location))
        .appendQueryParameter(ZOOM_PARAM_KEY, ZOOM_PARAM_VALUE)
        .appendQueryParameter(SIZE_PARAM_KEY, size)
        .appendQueryParameter(SCALE_PARAM_KEY, Float.toString(res.getDisplayMetrics().density))
        .appendQueryParameter(MARKERS_PARAM_KEY, getMarkerUrlParamValue(location))
        .appendQueryParameter(KEY_PARAM_KEY, ANDROID_API_KEY_VALUE);

    return builder.build().toString();
  }

  /**
   * Generates the URL for a request to reverse geocode the given location.
   *
   * <p>Source: https://developers.google.com/maps/documentation/geocoding/#ReverseGeocoding
   *
   * @param Location A location.
   */
  public static String getReverseGeocodeUrl(Location location) {
    final Uri.Builder builder = new Uri.Builder();

    builder
        .scheme(HTTPS_SCHEME)
        .authority(MAPS_API_DOMAIN)
        .appendPath(MAPS_PATH)
        .appendPath(API_PATH)
        .appendPath(GEOCODE_PATH)
        .appendPath(GEOCODE_OUTPUT_TYPE)
        .appendQueryParameter(LAT_LNG_PARAM_KEY, getFormattedLatLng(location))
        .appendQueryParameter(KEY_PARAM_KEY, BROWSER_API_KEY_VALUE);

    return builder.build().toString();
  }

  public static Intent getShowMapIntent(
      Location location, @Nullable CharSequence addressLine1, @Nullable CharSequence addressLine2) {

    String latLong = getFormattedLatLng(location);
    String url = String.format(Locale.US, "geo: %s?q=%s", latLong, latLong);

    // Add a map label
    if (addressLine1 != null) {
      if (addressLine2 != null) {
        url +=
            String.format(Locale.US, "(%s, %s)", addressLine1.toString(), addressLine2.toString());
      } else {
        url += String.format(Locale.US, "(%s)", addressLine1.toString());
      }
    } else {
      // TODO(mdooley): i18n
      url +=
          String.format(
              Locale.US,
              "(Latitude: %f, Longitude: %f)",
              location.getLatitude(),
              location.getLongitude());
    }

    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
    intent.setPackage("com.google.android.apps.maps");
    return intent;
  }

  /**
   * Returns a comma-separated latitude and longitude pair, formatted for use as a URL parameter
   * value.
   *
   * @param location A location.
   * @return The comma-separated latitude and longitude pair of that location.
   */
  @VisibleForTesting
  static String getFormattedLatLng(Location location) {
    return location.getLatitude() + LAT_LONG_DELIMITER + location.getLongitude();
  }

  /**
   * Returns the URL parameter value for the marker, specifying its style and position.
   *
   * @param location A location.
   * @return The URL parameter value for the marker.
   */
  @VisibleForTesting
  static String getMarkerUrlParamValue(Location location) {
    return MARKER_STYLE_COLOR
        + MARKER_STYLE_DELIMITER
        + MARKER_STYLE_COLOR_RED
        + MARKER_DELIMITER
        + getFormattedLatLng(location);
  }
}
