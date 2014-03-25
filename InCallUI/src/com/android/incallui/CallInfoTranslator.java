/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.telecomm.CallInfo;
import android.telecomm.CallState;
import android.telecomm.GatewayInfo;

import com.android.services.telephony.common.Call;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.Map;

/**
 * Translates {@link CallInfo} objects into {@link Call} objects so that call-infos received from
 * Telecomm can be used easily with {@link CallList}. Manages Telecomm call ID to {@link Call}
 * mappings. This class uses IDs for both {@link Call} objects and {@link CallInfo} objects which
 * are both normally referred to as "call ID". To distinguish the two, the code and comments refer
 * to them as "call ID" and "Telecomm call ID" respectively.
 * TODO(santoscordon): This class is a temporary solution until all calls are coming in through
 * Telecomm at which point we can rewrite the standard Call object format.
 */
final class CallInfoTranslator {
    /**
     * Maps String-based Telecomm call IDs to call objects and back. Entries are added with calls to
     * {@link #getCall} and removed with explicit calls to {@link #removeCall}.
     */
    private static final BiMap<String, Call> sCallsByTelecommId = HashBiMap.create();

    /**
     * Stores the next available ID usable by Call objects. IDs start at 100000 and increase by one
     * with each use. 100000 is used so as not to conflict with traditional call IDs which start
     * at 1. Non-conflict theory based on the notion that a user is highly unlikely to receive
     * 100000 more traditional phone calls than those of type CallInfo (from Telecom).
     * TODO(santoscordon): Remove this once Telecomm is the only source of phone calls.
     */
    private static int sNextAvailableCallId = 100000;

    /**
     * Performs the translation from a {@link CallInfo} into a {@link Call}. Looks up the Telecomm
     * call ID to see if an integer call ID has been assigned. If it has not, then a new call ID
     * will be created for the call.
     *
     * @param callInfo The call-info object from which to create a Call.
     */
    static Call getCall(CallInfo callInfo) {
        String telecommCallId = callInfo.getId();
        Call call = getCall(telecommCallId);
        if (call == null) {
            call = new Call(sNextAvailableCallId++);
            sCallsByTelecommId.put(telecommCallId, call);
        }

        call.setState(translateCallState(callInfo.getState()));
        call.setNumber(callInfo.getOriginalHandle().getSchemeSpecificPart());

        GatewayInfo gatewayInfo = callInfo.getGatewayInfo();
        if (gatewayInfo != null) {
            call.setGatewayNumber(gatewayInfo.getGatewayHandle().getSchemeSpecificPart());
            call.setGatewayPackage(gatewayInfo.getGatewayProviderPackageName());
        }

        // TODO: Each CallService needs to provide information what kind of call capabilities they
        // support. For now, always assume that all calls support hold by default.
        call.addCapabilities(Call.Capabilities.HOLD | Call.Capabilities.MUTE);

        return call;
    }

    /**
     * Returns the call which maps from the specified Telecomm call ID.
     *
     * @param telecommCallId The Telecomm call ID to map.
     * @return The call associated with the specified Telecomm call ID, or null if no association
     *         exists.
     */
    static Call getCall(String telecommCallId) {
        Preconditions.checkState(!Strings.isNullOrEmpty(telecommCallId));
        return sCallsByTelecommId.get(telecommCallId);
    }

    /**
     * Returns the Telecomm call ID for the given call object.
     *
     * @param call The call object associated with the Telecomm call ID.
     * @return The telecomm call ID or null if it cannot be found.
     */
    static String getTelecommCallId(Call call) {
        return sCallsByTelecommId.inverse().get(call);
    }

    /**
     * Removes the specified Telecomm call ID from the map.
     *
     * @param telecommCallId The Telecomm call ID to remove.
     */
    static void removeCall(String telecommCallId) {
        sCallsByTelecommId.remove(telecommCallId);
    }

    /**
     * Converts {@link CallState} to its {@link Call#State} equivalent.
     *
     * @param callState The call state from Telecomm.
     */
    private static int translateCallState(CallState callState) {
        switch(callState) {
            case RINGING:
                return Call.State.INCOMING;
            case DIALING:
                return Call.State.DIALING;
            case ACTIVE:
                return Call.State.ACTIVE;
            default:
                return Call.State.INVALID;
        }
    }
}
