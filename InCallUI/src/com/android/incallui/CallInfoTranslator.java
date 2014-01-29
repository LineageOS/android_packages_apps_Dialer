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

import com.android.services.telephony.common.Call;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * Translates {@link CallInfo} objects into {@link Call} objects so that call-infos received from
 * Telecomm can be used easily with {@link CallList}. Manages Telecomm call ID to {@link Call}
 * mappings. This class uses IDs for both {@link Call} objects and {@link CallInfo} objects which
 * are both normally referred to as "call id". To distinguish the two, the code and comments refer
 * to them as "call ID" and "Telecomm call ID" respectively.
 * TODO(santoscordon): This class is a temporary solution until all calls are coming in through
 * Telecomm at which point we can rewrite the standard Call object format.
 */
final class CallInfoTranslator {
    /**
     * Maps String-based Telecomm call IDs to call objects. Entries are added with calls to
     * {@link #getCall} and removed with explicit calls to {@link #removeCall}.
     */
    private static final Map<String, Call> sCallsById = Maps.newHashMap();

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
        Call call = getCall(callInfo.getId());
        if (call == null) {
            call = new Call(sNextAvailableCallId++);
        }

        // TODO(santoscordon): Remove assumption that all calls are dialing by default once
        // CallInfo supports Call States
        call.setState(Call.State.DIALING);
        call.setNumber(callInfo.getHandle());

        return call;
    }

    /**
     * Returns the call which maps from the specified Telecomm call ID. If no call was previously
     * associated with the specified ID then return null.
     *
     * @param telecommCallId The Telecomm call ID to map.
     * @return The call associated with the specified Telecomm call ID.
     */
    static Call getCall(String telecommCallId) {
        Preconditions.checkState(!Strings.isNullOrEmpty(telecommCallId));

        if (sCallsById.containsKey(telecommCallId)) {
            return sCallsById.get(telecommCallId);
        }

        return null;
    }

    /**
     * Removes the specified Telecomm call ID from the map.
     *
     * @param telecommCallId The Telecomm call ID to remove.
     */
    static void removeCall(String telecommCallId) {
        sCallsById.remove(telecommCallId);
    }
}
