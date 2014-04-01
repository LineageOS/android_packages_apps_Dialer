/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.telecomm.CallCapabilities;
import android.telecomm.CallNumberPresentation;
import android.telecomm.GatewayInfo;
import android.telephony.DisconnectCause;

import com.android.internal.telephony.PhoneConstants;
import com.google.android.collect.Sets;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.primitives.Ints;

import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Class object used across CallHandlerService APIs.
 * Describes a single call and its state.
 */
public final class Call {

    /* Defines different states of this call */
    public static class State {
        public static final int INVALID = 0;
        public static final int IDLE = 1;           /* The call is idle.  Nothing active */
        public static final int ACTIVE = 2;         /* There is an active call */
        public static final int INCOMING = 3;       /* A normal incoming phone call */
        public static final int CALL_WAITING = 4;   /* Incoming call while another is active */
        public static final int DIALING = 5;        /* An outgoing call during dial phase */
        public static final int REDIALING = 6;      /* Subsequent dialing attempt after a failure */
        public static final int ONHOLD = 7;         /* An active phone call placed on hold */
        public static final int DISCONNECTING = 8;  /* A call is being ended. */
        public static final int DISCONNECTED = 9;   /* State after a call disconnects */
        public static final int CONFERENCED = 10;   /* Call part of a conference call */

        public static boolean isConnected(int state) {
            switch(state) {
                case ACTIVE:
                case INCOMING:
                case CALL_WAITING:
                case DIALING:
                case REDIALING:
                case ONHOLD:
                case CONFERENCED:
                    return true;
                default:
            }
            return false;
        }

        public static boolean isDialing(int state) {
            return state == DIALING || state == REDIALING;
        }

        public static String toString(int state) {
            switch (state) {
                case INVALID:
                    return "INVALID";
                case IDLE:
                    return "IDLE";
                case ACTIVE:
                    return "ACTIVE";
                case INCOMING:
                    return "INCOMING";
                case CALL_WAITING:
                    return "CALL_WAITING";
                case DIALING:
                    return "DIALING";
                case REDIALING:
                    return "REDIALING";
                case ONHOLD:
                    return "ONHOLD";
                case DISCONNECTING:
                    return "DISCONNECTING";
                case DISCONNECTED:
                    return "DISCONNECTED";
                case CONFERENCED:
                    return "CONFERENCED";
                default:
                    return "UNKOWN";
            }
        }
    }

    // Unique identifier for the call
    private String mCallId;

    // The current state of the call
    private int mState = State.INVALID;

    // TODO: Probably need to change to wifi call state.  Re-use mState?
    // State.WIFI_CONNECTING
    // State.WIFI_CONNECTED
    // Using this simple boolean for now so we can see the UI mock.
    private boolean mIsWifiCall = false;

    // Reason for disconnect. Valid when the call state is DISCONNECTED.
    // Valid values are defined in {@link DisconnectCause}.
    private int mDisconnectCause = DisconnectCause.NOT_VALID;

    // Bit mask of capabilities unique to this call.
    private int mCapabilities;

    // Time that this call transitioned into ACTIVE state from INCOMING, WAITING, or OUTGOING.
    private long mConnectTime = 0;

    private String mNumber;

    private GatewayInfo mGatewayInfo;

    public Call(String callId, String number) {
        mCallId = callId;
        mNumber = number;
    }

    public String getCallId() {
        return mCallId;
    }

    public String getNumber() {
        return mNumber;
    }

    public int getState() {
        return mState;
    }

    public void setState(int state) {
        mState = state;
    }

    public boolean isWifiCall() {
        return mIsWifiCall;
    }

    public CallNumberPresentation getNumberPresentation() {
        return CallNumberPresentation.ALLOWED;
    }

    public CallNumberPresentation getCnapNamePresentation() {
        return CallNumberPresentation.ALLOWED;
    }

    public String getCnapName() {
        return "";
    }

    /** Returns call disconnect cause; values are defined in {@link DisconnectCause}. */
    public int getDisconnectCause() {
        if (mState == State.DISCONNECTED || mState == State.IDLE) {
            return mDisconnectCause;
        }

        return DisconnectCause.NOT_DISCONNECTED;
    }

    /** Sets the call disconnect cause; values are defined in {@link DisconnectCause}. */
    public void setDisconnectCause(int cause) {
        mDisconnectCause = cause;
    }

    public void setCapabilities(int capabilities) {
        mCapabilities = (CallCapabilities.ALL & capabilities);
    }

    public boolean can(int capabilities) {
        return (capabilities == (capabilities & mCapabilities));
    }

    public void addCapabilities(int capabilities) {
        setCapabilities(capabilities | mCapabilities);
    }

    public void setConnectTime(long connectTime) {
        mConnectTime = connectTime;
    }

    public long getConnectTime() {
        return mConnectTime;
    }

    public ImmutableSortedSet<Integer> getChildCallIds() {
        return ImmutableSortedSet.of();
    }

    public boolean isConferenceCall() {
        return false;
    }

    public GatewayInfo getGatewayInfo() {
        return mGatewayInfo;
    }

    public void setGatewayInfo(GatewayInfo gatewayInfo) {
        mGatewayInfo = gatewayInfo;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "[%s, %s]", mCallId, State.toString(mState));
    }
}
