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
import android.os.Parcel;
import android.os.Parcelable;
import android.telecom.CallAudioState;

import com.android.contacts.common.compat.SdkVersionOverride;

import java.util.Locale;

/**
 * Compatibility class for {@link CallAudioState}
 */
public class CallAudioStateCompat {

    /**
     * Direct the audio stream through the device's earpiece.
     */
    public static final int ROUTE_EARPIECE = CallAudioState.ROUTE_EARPIECE;

    /**
     * Direct the audio stream through Bluetooth.
     */
    public static final int ROUTE_BLUETOOTH = CallAudioState.ROUTE_BLUETOOTH;

    /**
     * Direct the audio stream through a wired headset.
     */
    public static final int ROUTE_WIRED_HEADSET = CallAudioState.ROUTE_WIRED_HEADSET;

    /**
     * Direct the audio stream through the device's speakerphone.
     */
    public static final int ROUTE_SPEAKER = CallAudioState.ROUTE_SPEAKER;

    /**
     * Direct the audio stream through the device's earpiece or wired headset if one is connected.
     */
    public static final int ROUTE_WIRED_OR_EARPIECE = CallAudioState.ROUTE_WIRED_OR_EARPIECE;

    private final CallAudioStateImpl mCallAudioState;

    /**
     * Constructor for a {@link CallAudioStateCompat} object.
     *
     * @param muted {@code true} if the call is muted, {@code false} otherwise.
     * @param route The current audio route being used. Allowed values: {@link #ROUTE_EARPIECE}
     * {@link #ROUTE_BLUETOOTH} {@link #ROUTE_WIRED_HEADSET} {@link #ROUTE_SPEAKER}
     * @param supportedRouteMask Bit mask of all routes supported by this call. This should be a
     * bitwise combination of the following values: {@link #ROUTE_EARPIECE} {@link #ROUTE_BLUETOOTH}
     * {@link #ROUTE_WIRED_HEADSET} {@link #ROUTE_SPEAKER}
     */
    public CallAudioStateCompat(boolean muted, int route, int supportedRouteMask) {
        if (SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.M)
                < Build.VERSION_CODES.M) {
            mCallAudioState = new CallAudioStateBase(muted, route, supportedRouteMask);
        } else {
            mCallAudioState = new CallAudioStateMarshmallow(muted, route, supportedRouteMask);
        }
    }

    /**
     * @return {@code true} if the call is muted, {@code false} otherwise.
     */
    public boolean isMuted() {
        return mCallAudioState.isMuted();
    }

    /**
     * @return The current audio route being used.
     */
    public int getRoute() {
        return mCallAudioState.getRoute();
    }

    /**
     * @return Bit mask of all routes supported by this call.
     */
    public int getSupportedRouteMask() {
        return mCallAudioState.getSupportedRouteMask();
    }

    /**
     * Converts the provided audio route into a human readable string representation.
     *
     * @param route to convert into a string.
     * @return String representation of the provided audio route.
     */
    public static String audioRouteToString(int route) {
        if (SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.M)
                < Build.VERSION_CODES.M) {
            return CallAudioStateBase.audioRouteToString(route);
        }
        return CallAudioStateMarshmallow.audioRouteToString(route);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CallAudioStateCompat that = (CallAudioStateCompat) o;

        return mCallAudioState.equals(that.mCallAudioState);

    }

    @Override
    public int hashCode() {
        return mCallAudioState.hashCode();
    }

    @Override
    public String toString() {
        return mCallAudioState.toString();
    }

    private interface CallAudioStateImpl {
        boolean isMuted();
        int getRoute();
        int getSupportedRouteMask();
    }

    /**
     * CallAudioStateImpl to use if the Sdk version is lower than
     * {@link android.os.Build.VERSION_CODES.M}
     *
     * Coped from {@link android.telecom.CallAudioState}
     *
     * Encapsulates the telecom audio state, including the current audio routing, supported audio
     * routing and mute.
     */
    private static class CallAudioStateBase implements CallAudioStateImpl, Parcelable {

        /**
         * Bit mask of all possible audio routes.
         */
        private static final int ROUTE_ALL = ROUTE_EARPIECE | ROUTE_BLUETOOTH | ROUTE_WIRED_HEADSET
                | ROUTE_SPEAKER;

        private final boolean isMuted;
        private final int route;
        private final int supportedRouteMask;

        /**
         * Constructor for a {@link CallAudioStateBase} object.
         *
         * @param muted {@code true} if the call is muted, {@code false} otherwise.
         * @param route The current audio route being used. Allowed values: {@link #ROUTE_EARPIECE}
         *      {@link #ROUTE_BLUETOOTH}, {@link #ROUTE_WIRED_HEADSET}, {@link #ROUTE_SPEAKER}
         * @param supportedRouteMask Bit mask of all routes supported by this call. This should be a
         *      bitwise combination of the following values: {@link #ROUTE_EARPIECE},
         *      {@link #ROUTE_BLUETOOTH}, {@link #ROUTE_WIRED_HEADSET}, {@link #ROUTE_SPEAKER}
         */
        public CallAudioStateBase(boolean muted, int route, int supportedRouteMask) {
            this.isMuted = muted;
            this.route = route;
            this.supportedRouteMask = supportedRouteMask;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof CallAudioStateBase)) {
                return false;
            }
            CallAudioStateBase state = (CallAudioStateBase) obj;
            return isMuted() == state.isMuted() && getRoute() == state.getRoute() &&
                    getSupportedRouteMask() == state.getSupportedRouteMask();
        }

        @Override
        public String toString() {
            return String.format(Locale.US,
                    "[AudioState isMuted: %b, route: %s, supportedRouteMask: %s]",
                    isMuted,
                    audioRouteToString(route),
                    audioRouteToString(supportedRouteMask));
        }

        /**
         * @return {@code true} if the call is muted, {@code false} otherwise.
         */
        @Override
        public boolean isMuted() {
            return isMuted;
        }

        /**
         * @return The current audio route being used.
         */
        public int getRoute() {
            return route;
        }

        /**
         * @return Bit mask of all routes supported by this call.
         */
        public int getSupportedRouteMask() {
            return supportedRouteMask;
        }

        /**
         * Converts the provided audio route into a human readable string representation.
         *
         * @param route to convert into a string.
         * @return String representation of the provided audio route.
         */
        public static String audioRouteToString(int route) {
            if (route == 0 || (route & ~ROUTE_ALL) != 0x0) {
                return "UNKNOWN";
            }

            StringBuffer buffer = new StringBuffer();
            if ((route & ROUTE_EARPIECE) == ROUTE_EARPIECE) {
                listAppend(buffer, "EARPIECE");
            }
            if ((route & ROUTE_BLUETOOTH) == ROUTE_BLUETOOTH) {
                listAppend(buffer, "BLUETOOTH");
            }
            if ((route & ROUTE_WIRED_HEADSET) == ROUTE_WIRED_HEADSET) {
                listAppend(buffer, "WIRED_HEADSET");
            }
            if ((route & ROUTE_SPEAKER) == ROUTE_SPEAKER) {
                listAppend(buffer, "SPEAKER");
            }

            return buffer.toString();
        }

        /**
         * Responsible for creating AudioState objects for deserialized Parcels.
         */
        public static final Parcelable.Creator<CallAudioStateBase> CREATOR =
                new Parcelable.Creator<CallAudioStateBase>() {

                    @Override
                    public CallAudioStateBase createFromParcel(Parcel source) {
                        boolean isMuted = source.readByte() == 0 ? false : true;
                        int route = source.readInt();
                        int supportedRouteMask = source.readInt();
                        return new CallAudioStateBase(isMuted, route, supportedRouteMask);
                    }

                    @Override
                    public CallAudioStateBase[] newArray(int size) {
                        return new CallAudioStateBase[size];
                    }
                };

        /**
         * {@inheritDoc}
         */
        @Override
        public int describeContents() {
            return 0;
        }

        /**
         * Writes AudioState object into a serializeable Parcel.
         */
        @Override
        public void writeToParcel(Parcel destination, int flags) {
            destination.writeByte((byte) (isMuted ? 1 : 0));
            destination.writeInt(route);
            destination.writeInt(supportedRouteMask);
        }

        private static void listAppend(StringBuffer buffer, String str) {
            if (buffer.length() > 0) {
                buffer.append(", ");
            }
            buffer.append(str);
        }
    }

    /**
     * CallAudioStateImpl to use if the Sdk version is at least
     * {@link android.os.Build.VERSION_CODES.M}
     */
    private static class CallAudioStateMarshmallow implements CallAudioStateImpl {

        private final CallAudioState mCallAudioStateDelegate;

        public CallAudioStateMarshmallow(boolean muted, int route, int supportedRouteMask) {
            mCallAudioStateDelegate = new CallAudioState(muted, route, supportedRouteMask);
        }

        @Override
        public boolean isMuted() {
            return mCallAudioStateDelegate.isMuted();
        }

        @Override
        public int getRoute() {
            return mCallAudioStateDelegate.getRoute();
        }

        @Override
        public int getSupportedRouteMask() {
            return mCallAudioStateDelegate.getSupportedRouteMask();
        }

        public static String audioRouteToString(int route) {
            return CallAudioState.audioRouteToString(route);
        }
    }
}
