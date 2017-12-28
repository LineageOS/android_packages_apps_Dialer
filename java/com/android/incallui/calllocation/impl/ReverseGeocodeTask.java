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

import android.location.Location;
import android.net.TrafficStats;
import android.os.AsyncTask;
import com.android.dialer.common.LogUtil;
import com.android.dialer.constants.TrafficStatsTags;
import com.android.incallui.calllocation.impl.LocationPresenter.LocationUi;
import java.lang.ref.WeakReference;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class ReverseGeocodeTask extends AsyncTask<Location, Void, String> {

  // Below are the JSON keys for the reverse geocode response.
  // Source: https://developers.google.com/maps/documentation/geocoding/#ReverseGeocoding
  private static final String JSON_KEY_RESULTS = "results";
  private static final String JSON_KEY_ADDRESS = "formatted_address";
  private static final String JSON_KEY_ADDRESS_COMPONENTS = "address_components";
  private static final String JSON_KEY_PREMISE = "premise";
  private static final String JSON_KEY_TYPES = "types";
  private static final String JSON_KEY_LONG_NAME = "long_name";
  private static final String JSON_KEY_SHORT_NAME = "short_name";

  private WeakReference<LocationUi> uiReference;

  public ReverseGeocodeTask(WeakReference<LocationUi> uiReference) {
    this.uiReference = uiReference;
  }

  @Override
  protected String doInBackground(Location... locations) {
    LocationUi ui = uiReference.get();
    if (ui == null) {
      return null;
    }
    if (locations == null || locations.length == 0) {
      LogUtil.e("ReverseGeocodeTask.onLocationChanged", "No location provided");
      return null;
    }

    try {
      String address = null;
      String url = LocationUrlBuilder.getReverseGeocodeUrl(locations[0]);

      TrafficStats.setThreadStatsTag(TrafficStatsTags.REVERSE_GEOCODE_TAG);
      String jsonResponse = HttpFetcher.getRequestAsString(ui.getContext(), url);

      // Parse the JSON response for the formatted address of the first result.
      JSONObject responseObject = new JSONObject(jsonResponse);
      if (responseObject != null) {
        JSONArray results = responseObject.optJSONArray(JSON_KEY_RESULTS);
        if (results != null && results.length() > 0) {
          JSONObject topResult = results.optJSONObject(0);
          if (topResult != null) {
            address = topResult.getString(JSON_KEY_ADDRESS);

            // Strip off the Premise component from the address, if present.
            JSONArray components = topResult.optJSONArray(JSON_KEY_ADDRESS_COMPONENTS);
            if (components != null) {
              boolean stripped = false;
              for (int i = 0; !stripped && i < components.length(); i++) {
                JSONObject component = components.optJSONObject(i);
                JSONArray types = component.optJSONArray(JSON_KEY_TYPES);
                if (types != null) {
                  for (int j = 0; !stripped && j < types.length(); j++) {
                    if (JSON_KEY_PREMISE.equals(types.getString(j))) {
                      String premise = null;
                      if (component.has(JSON_KEY_SHORT_NAME)
                          && address.startsWith(component.getString(JSON_KEY_SHORT_NAME))) {
                        premise = component.getString(JSON_KEY_SHORT_NAME);
                      } else if (component.has(JSON_KEY_LONG_NAME)
                          && address.startsWith(component.getString(JSON_KEY_LONG_NAME))) {
                        premise = component.getString(JSON_KEY_SHORT_NAME);
                      }
                      if (premise != null) {
                        int index = address.indexOf(',', premise.length());
                        if (index > 0 && index < address.length()) {
                          address = address.substring(index + 1).trim();
                        }
                        stripped = true;
                        break;
                      }
                    }
                  }
                }
              }
            }

            // Strip off the country, if its USA.  Note: unfortunately the country in the formatted
            // address field doesn't match the country in the address component fields (USA != US)
            // so we can't easily strip off the country for all cases, thus this hack.
            if (address.endsWith(", USA")) {
              address = address.substring(0, address.length() - 5);
            }
          }
        }
      }

      return address;
    } catch (AuthException ex) {
      LogUtil.e("ReverseGeocodeTask.onLocationChanged", "AuthException", ex);
      return null;
    } catch (JSONException ex) {
      LogUtil.e("ReverseGeocodeTask.onLocationChanged", "JSONException", ex);
      return null;
    } catch (Exception ex) {
      LogUtil.e("ReverseGeocodeTask.onLocationChanged", "Exception!!!", ex);
      return null;
    } finally {
      TrafficStats.clearThreadStatsTag();
    }
  }

  @Override
  protected void onPostExecute(String address) {
    LocationUi ui = uiReference.get();
    if (ui == null) {
      return;
    }

    try {
      ui.setAddress(address);
    } catch (Exception ex) {
      LogUtil.e("ReverseGeocodeTask.onPostExecute", "Exception!!!", ex);
    }
  }
}
