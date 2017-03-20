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

package com.android.incallui.maps;

import android.content.Context;
import com.android.dialer.inject.HasRootComponent;
import dagger.Subcomponent;
import com.android.incallui.maps.stub.StubMapsModule;

/** Subcomponent that can be used to access the maps implementation. */
public class MapsComponent {

  private static MapsComponent instance;
  private Maps maps;

  public Maps getMaps() {
    if (maps == null) {
        maps = new StubMapsModule.StubMaps();
    }
    return maps;
  }

  public static MapsComponent get(Context context) {
    if (instance == null) {
        instance = new MapsComponent();
    }
    return instance;
  }


  /** Used to refer to the root application component. */
  public interface HasComponent {
    MapsComponent mapsComponent();
  }
}
