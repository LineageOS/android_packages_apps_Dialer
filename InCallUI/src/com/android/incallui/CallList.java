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
 * limitations under the License
 */

package com.android.incallui;

import com.google.android.collect.Maps;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import com.android.services.telephony.common.Call;

import java.util.HashMap;
import java.util.List;

/**
 * Maintains the list of active calls received from CallHandlerService.
 * TODO(klp): This class should be used by InCallUI to read about
 * changes to calls.
 */
public class CallList {

    final HashMap<Integer, Call> callMap = Maps.newHashMap();

    public void onUpdate(Call call) {
        updateCallInMap(call);
    }

    public void onUpdate(List<Call> callsToUpdate) {
        Preconditions.checkNotNull(callsToUpdate);
        for (Call call : callsToUpdate) {
            updateCallInMap(call);
        }
    }

    private void updateCallInMap(Call call) {
        Preconditions.checkNotNull(call);

        final Integer id = new Integer(call.getCallId());

        if (isCallActive(call)) {
            callMap.put(id, call);
        } else if (callMap.containsKey(id)) {
            callMap.remove(id);
        }
    }

    private boolean isCallActive(Call call) {
        final int state = call.getState();
        return Call.State.IDLE != state && Call.State.INVALID != state;
    }
}
