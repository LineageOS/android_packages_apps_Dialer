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
package com.android.dialer.compat;

import android.os.Build;

import com.android.contacts.common.compat.SdkVersionOverride;

public final class DialerCompatUtils {
    /**
     * Determines if this version is compatible with video calling. Can also force the version to be
     * lower through SdkVersionOverride.
     *
     * @return {@code true} if video calling is allowed, {@code false} otherwise.
     */
    public static boolean isVideoCompatible() {
        return SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.LOLLIPOP)
                >= Build.VERSION_CODES.M;
    }

    /**
     * Determines if this version is compatible with a default dialer. Can also force the version to
     * be lower through SdkVersionOverride.
     *
     * @return {@code true} if default dialer is a feature on this device, {@code false} otherwise.
     */
    public static boolean isDefaultDialerCompatible() {
        return SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.LOLLIPOP)
                >= Build.VERSION_CODES.M;
    }
}