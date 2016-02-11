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
 * limitations under the License
 */

package com.android.incallui;

import android.content.Context;
import android.content.res.Configuration;
import android.view.OrientationEventListener;
import android.hardware.SensorManager;
import android.view.Surface;
import android.content.pm.ActivityInfo;

/**
 * This class listens to Orientation events and overrides onOrientationChanged which gets
 * invoked when an orientation change occurs. When that happens, we notify InCallUI registrants
 * of the change.
 */
public class InCallOrientationEventListener extends OrientationEventListener {

    /**
     * Screen orientation angles one of 0, 90, 180, 270, 360 in degrees.
     */
    public static int SCREEN_ORIENTATION_0 = 0;
    public static int SCREEN_ORIENTATION_90 = 90;
    public static int SCREEN_ORIENTATION_180 = 180;
    public static int SCREEN_ORIENTATION_270 = 270;
    public static int SCREEN_ORIENTATION_360 = 360;

    public static int FULL_SENSOR_SCREEN_ORIENTATION =
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;

    public static int NO_SENSOR_SCREEN_ORIENTATION =
            ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;

    /**
     * This is to identify dead zones where we won't notify others of orientation changed.
     * Say for e.g our threshold is x degrees. We will only notify UI when our current rotation is
     * within x degrees right or left of the screen orientation angles. If it's not within those
     * ranges, we return SCREEN_ORIENTATION_UNKNOWN and ignore it.
     */
    private static int SCREEN_ORIENTATION_UNKNOWN = -1;

    // Rotation threshold is 10 degrees. So if the rotation angle is within 10 degrees of any of
    // the above angles, we will notify orientation changed.
    private static int ROTATION_THRESHOLD = 10;


    /**
     * Cache the current rotation of the device.
     */
    private static int sCurrentOrientation = SCREEN_ORIENTATION_0;

    public InCallOrientationEventListener(Context context) {
        super(context);
    }

    /**
     * Handles changes in device orientation. Notifies InCallPresenter of orientation changes.
     *
     * Note that this API receives sensor rotation in degrees as a param and we convert that to
     * one of our screen orientation constants - (one of: {@link SCREEN_ORIENTATION_0},
     * {@link SCREEN_ORIENTATION_90}, {@link SCREEN_ORIENTATION_180},
     * {@link SCREEN_ORIENTATION_270}).
     *
     * @param rotation The new device sensor rotation in degrees
     */
    @Override
    public void onOrientationChanged(int rotation) {
        if (rotation == OrientationEventListener.ORIENTATION_UNKNOWN) {
            return;
        }

        final int orientation = toScreenOrientation(rotation);

        if (orientation != SCREEN_ORIENTATION_UNKNOWN && sCurrentOrientation != orientation) {
            sCurrentOrientation = orientation;
            InCallPresenter.getInstance().onDeviceOrientationChange(sCurrentOrientation);
        }
    }

    /**
     * Enables the OrientationEventListener and notifies listeners of current orientation if
     * notify flag is true
     * @param notify true or false. Notify device orientation changed if true.
     */
    public void enable(boolean notify) {
        super.enable();
        if (notify) {
            InCallPresenter.getInstance().onDeviceOrientationChange(sCurrentOrientation);
        }
    }

    /**
     * Enables the OrientationEventListener with notify flag defaulting to false.
     */
    public void enable() {
        enable(false);
    }

    /**
     * Converts sensor rotation in degrees to screen orientation constants.
     * @param rotation sensor rotation angle in degrees
     * @return Screen orientation angle in degrees (0, 90, 180, 270). Returns -1 for degrees not
     * within threshold to identify zones where orientation change should not be trigerred.
     */
    private int toScreenOrientation(int rotation) {
        // Sensor orientation 90 is equivalent to screen orientation 270 and vice versa. This
        // function returns the screen orientation. So we convert sensor rotation 90 to 270 and
        // vice versa here.
        if (isInLeftRange(rotation, SCREEN_ORIENTATION_360, ROTATION_THRESHOLD) ||
                isInRightRange(rotation, SCREEN_ORIENTATION_0, ROTATION_THRESHOLD)) {
            return SCREEN_ORIENTATION_0;
        } else if (isWithinThreshold(rotation, SCREEN_ORIENTATION_90, ROTATION_THRESHOLD)) {
            return SCREEN_ORIENTATION_270;
        } else if (isWithinThreshold(rotation, SCREEN_ORIENTATION_180, ROTATION_THRESHOLD)) {
            return SCREEN_ORIENTATION_180;
        } else if (isWithinThreshold(rotation, SCREEN_ORIENTATION_270, ROTATION_THRESHOLD)) {
            return SCREEN_ORIENTATION_90;
        }
        return SCREEN_ORIENTATION_UNKNOWN;
    }

    private static boolean isWithinRange(int value, int begin, int end) {
        return value >= begin && value < end;
    }

    private static boolean isWithinThreshold(int value, int center, int threshold) {
        return isWithinRange(value, center - threshold, center + threshold);
    }

    private static boolean isInLeftRange(int value, int center, int threshold) {
        return isWithinRange(value, center - threshold, center);
    }

    private static boolean isInRightRange(int value, int center, int threshold) {
        return isWithinRange(value, center, center + threshold);
    }
}
