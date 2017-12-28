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

import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.TrafficStats;
import android.os.AsyncTask;
import com.android.dialer.common.LogUtil;
import com.android.dialer.constants.TrafficStatsTags;
import com.android.incallui.calllocation.impl.LocationPresenter.LocationUi;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;

class DownloadMapImageTask extends AsyncTask<Location, Void, Drawable> {

  private static final String STATIC_MAP_SRC_NAME = "src";

  private final WeakReference<LocationUi> uiReference;

  public DownloadMapImageTask(WeakReference<LocationUi> uiReference) {
    this.uiReference = uiReference;
  }

  @Override
  protected Drawable doInBackground(Location... locations) {
    LocationUi ui = uiReference.get();
    if (ui == null) {
      return null;
    }
    if (locations == null || locations.length == 0) {
      LogUtil.e("DownloadMapImageTask.doInBackground", "No location provided");
      return null;
    }

    try {
      URL mapUrl = new URL(LocationUrlBuilder.getStaticMapUrl(ui.getContext(), locations[0]));
      TrafficStats.setThreadStatsTag(TrafficStatsTags.DOWNLOAD_LOCATION_MAP_TAG);
      InputStream content = (InputStream) mapUrl.getContent();

      return Drawable.createFromStream(content, STATIC_MAP_SRC_NAME);
    } catch (Exception ex) {
      LogUtil.e("DownloadMapImageTask.doInBackground", "Exception!!!", ex);
      return null;
    } finally {
      TrafficStats.clearThreadStatsTag();
    }
  }

  @Override
  protected void onPostExecute(Drawable mapImage) {
    LocationUi ui = uiReference.get();
    if (ui == null) {
      return;
    }

    try {
      ui.setMap(mapImage);
    } catch (Exception ex) {
      LogUtil.e("DownloadMapImageTask.onPostExecute", "Exception!!!", ex);
    }
  }
}
