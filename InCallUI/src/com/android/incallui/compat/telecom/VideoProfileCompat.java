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

import android.telecom.VideoProfile;

import com.android.contacts.common.compat.CompatUtils;

/**
 * Compatibility class for {@link android.telecom.VideoProfile}
 */
public class VideoProfileCompat {

    /**
     * Generates a string representation of a video state.
     *
     * @param videoState The video state.
     * @return String representation of the video state.
     */
    public static String videoStateToString(int videoState) {
        if (CompatUtils.isMarshmallowCompatible()) {
            return VideoProfile.videoStateToString(videoState);
        }
        return videoStateToStringLollipop(videoState);
    }

    /**
     * Copied from {@link android.telecom.VideoProfile#videoStateToString}
     */
    private static String videoStateToStringLollipop(int videoState) {
        StringBuilder sb = new StringBuilder();
        sb.append("Audio");
        if (isAudioOnly(videoState)) {
            sb.append(" Only");
        } else {
            if (isTransmissionEnabled(videoState)) {
                sb.append(" Tx");
            }
            if (isReceptionEnabled(videoState)) {
                sb.append(" Rx");
            }
            if (isPaused(videoState)) {
                sb.append(" Pause");
            }
        }
        return sb.toString();
    }

    /**
     * Indicates whether the video state is audio only.
     *
     * @param videoState The video state.
     * @return {@code true} if the video state is audio only, {@code false} otherwise.
     */
    public static boolean isAudioOnly(int videoState) {
        if (CompatUtils.isMarshmallowCompatible()) {
            return VideoProfile.isAudioOnly(videoState);
        }
        return !hasState(videoState, VideoProfile.STATE_TX_ENABLED)
                && !hasState(videoState, VideoProfile.STATE_RX_ENABLED);
    }

    /**
     * Indicates whether the video state has video transmission enabled.
     *
     * @param videoState The video state.
     * @return {@code true} if video transmission is enabled, {@code false} otherwise.
     */
    public static boolean isTransmissionEnabled(int videoState) {
        if (CompatUtils.isMarshmallowCompatible()) {
            return VideoProfile.isTransmissionEnabled(videoState);
        }
        return hasState(videoState, VideoProfile.STATE_TX_ENABLED);
    }

    /**
     * Indicates whether the video state has video reception enabled.
     *
     * @param videoState The video state.
     * @return {@code true} if video reception is enabled, {@code false} otherwise.
     */
    public static boolean isReceptionEnabled(int videoState) {
        if (CompatUtils.isMarshmallowCompatible()) {
            return VideoProfile.isReceptionEnabled(videoState);
        }
        return hasState(videoState, VideoProfile.STATE_RX_ENABLED);
    }

    /**
     * Indicates whether the video state is paused.
     *
     * @param videoState The video state.
     * @return {@code true} if the video is paused, {@code false} otherwise.
     */
    public static boolean isPaused(int videoState) {
        if (CompatUtils.isMarshmallowCompatible()) {
            return VideoProfile.isPaused(videoState);
        }
        return hasState(videoState, VideoProfile.STATE_PAUSED);
    }

    /**
     * Copied from {@link android.telecom.VideoProfile}
     *
     * Determines if a specified state is set in a videoState bit-mask.
     *
     * @param videoState The video state bit-mask.
     * @param state The state to check.
     * @return {@code true} if the state is set.
     */
    private static boolean hasState(int videoState, int state) {
        return (videoState & state) == state;
    }

    /**
     * Indicates whether the video state is bi-directional.
     *
     * @param videoState The video state.
     * @return {@code True} if the video is bi-directional, {@code false} otherwise.
     */
    public static boolean isBidirectional(int videoState) {
        if (CompatUtils.isMarshmallowCompatible()) {
            return VideoProfile.isBidirectional(videoState);
        }
        return hasState(videoState, VideoProfile.STATE_BIDIRECTIONAL);
    }
}
