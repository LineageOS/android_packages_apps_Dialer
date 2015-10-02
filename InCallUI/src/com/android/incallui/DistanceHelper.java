/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.incallui;

import android.location.Address;

/**
 * Superclass for a helper class to get the current location and distance to other locations.
 */
public abstract class DistanceHelper {
    public static final float DISTANCE_NOT_FOUND = -1;
    public static final float MILES_PER_METER = (float) 0.000621371192;
    public static final float KILOMETERS_PER_METER = (float) 0.001;

    public interface Listener {
        public void onLocationReady();
    }

    public void cleanUp() {}

    public float calculateDistance(Address address) {
        return DISTANCE_NOT_FOUND;
    }
}
