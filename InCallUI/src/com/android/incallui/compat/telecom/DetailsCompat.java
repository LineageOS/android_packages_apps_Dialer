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
package com.android.incallui.compat.telecom;

import android.os.Build;
import android.os.Bundle;
import android.telecom.Call.Details;

import com.android.contacts.common.compat.CompatUtils;
import com.android.incallui.Log;

/**
 * Compatibility class for {@link Details}
 */
public class DetailsCompat {

    /**
     * Constant formerly in L as PhoneCapabilities#ADD_CALL. It was transferred to
     * {@link Details#CAPABILITY_UNUSED_1} and hidden
     */
    public static final int CAPABILITY_UNUSED_1 = 0x00000010;

    /**
     * Returns the intent extras from the given {@link Details}
     * For Sdk version L and earlier, this will return {@link Details#getExtras()}
     *
     * @param details The details whose intent extras should be returned
     * @return The given details' intent extras
     */
    public static Bundle getIntentExtras(Details details) {
        if (CompatUtils.isMarshmallowCompatible()) {
            return details.getIntentExtras();
        }
        return details.getExtras();
    }

    /**
     * Compatibility method to check whether the supplied properties includes the
     * specified property.
     *
     * @param details The details whose properties should be checked.
     * @param property The property to check properties for.
     * @return Whether the specified property is supported.
     */
    public static boolean hasProperty(Details details, int property) {
        if (CompatUtils.isMarshmallowCompatible()) {
            return details.hasProperty(property);
        }
        return (details.getCallProperties() & property) != 0;
    }

    /**
     * Compatibility method to check whether the capabilities of the given {@code Details}
     * supports the specified capability.
     *
     * @param details The details whose capabilities should be checked.
     * @param capability The capability to check capabilities for.
     * @return Whether the specified capability is supported.
     */
    public static boolean can(Details details, int capability) {
        if (CompatUtils.isLollipopMr1Compatible()) {
            return details.can(capability);
        }
        return (details.getCallCapabilities() & capability) != 0;
    }

    /**
     * Render a set of capability bits ({@code CAPABILITY_*}) as a human readable string.
     *
     * @param capabilities A capability bit field.
     * @return A human readable string representation.
     */
    public static String capabilitiesToString(int capabilities) {
        if (CompatUtils.isLollipopMr1Compatible()) {
            return Details.capabilitiesToString(capabilities);
        }
        return capabilitiesToStringLollipop(capabilities);
    }

    /*
     * Use reflection to call PhoneCapabilities.toString. InCallUI code is only run on Google
     * Experience phones, so we will be the system Dialer and the method will exist
     */
    private static String capabilitiesToStringLollipop(int capabilities) {
        try {
            return (String) Class.forName("android.telecom.PhoneCapabilities")
                    .getMethod("toString", Integer.TYPE)
                    .invoke(null, capabilities);
        } catch (ReflectiveOperationException e) {
            Log.e(DetailsCompat.class, "Unable to use reflection to call "
                    + "android.telecom.PhoneCapabilities.toString(int)", e);
            return String.valueOf(capabilities);
        }
    }
}
