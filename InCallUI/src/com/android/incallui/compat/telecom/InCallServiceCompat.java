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

import android.support.annotation.Nullable;
import android.telecom.InCallService;

import com.android.contacts.common.compat.CompatUtils;
import com.android.incallui.Call;
import com.android.incallui.InCallServiceImpl;

/**
 * Compatibility class for {@link android.telecom.InCallService}
 */
public class InCallServiceCompat {

    /**
     * Sets the microphone mute state. When this request is honored, there
     * will be a change to the {@link android.telecom.CallAudioState}.
     *
     * Note: Noop for Sdk versions less than M where inCallService is not of type
     * {@link InCallServiceImpl}
     *
     * @param inCallService the {@link InCallService} to act on
     * @param shouldMute {@code true} if the microphone should be muted; {@code false} otherwise.
     */
    public static void setMuted(@Nullable InCallService inCallService, boolean shouldMute) {
        if (inCallService == null) {
            return;
        }
        if (CompatUtils.isMarshmallowCompatible()) {
            inCallService.setMuted(shouldMute);
            return;
        }

        if (inCallService instanceof InCallServiceImpl) {
            ((InCallServiceImpl) inCallService).setMutedCompat(shouldMute);
        }
    }

    /**
     * Sets the audio route (speaker, bluetooth, etc...).  When this request is honored, there will
     * be change to the {@link android.telecom.CallAudioState}.
     *
     * Note: Noop for Sdk versions less than M where inCallService is not of type
     * {@link InCallServiceImpl}
     *
     * @param inCallService the {@link InCallService} to act on
     * @param route The audio route to use.
     */
    public static void setAudioRoute(@Nullable InCallService inCallService, int route) {
        if (inCallService == null) {
            return;
        }
        if (CompatUtils.isMarshmallowCompatible()) {
            inCallService.setAudioRoute(route);
            return;
        }

        if (inCallService instanceof InCallServiceImpl) {
            ((InCallServiceImpl) inCallService).setAudioRouteCompat(route);
        }
    }

    /**
     * Returns if the device can support additional calls.
     *
     * @param inCallService the {@link InCallService} to act on
     * @param call a {@link Call} to use if needed due to compatibility reasons
     * @return Whether the phone supports adding more calls, defaulting to true if inCallService
     *    is null
     */
    public static boolean canAddCall(@Nullable InCallService inCallService, Call call) {
        if (inCallService == null) {
            return true;
        }

        if (CompatUtils.isMarshmallowCompatible()) {
            // Default to true if we are not connected to telecom.
            return inCallService.canAddCall();
        }
        return call.can(DetailsCompat.CAPABILITY_UNUSED_1);
    }
}
