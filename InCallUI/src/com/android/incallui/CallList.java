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

import com.google.android.collect.Lists;
import com.google.android.collect.Maps;
import com.google.android.collect.Sets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import android.util.Log;

import com.android.services.telephony.common.Call;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Maintains the list of active calls received from CallHandlerService.
 * TODO(klp): This class should be used by InCallUI to read about
 * changes to calls.
 */
public class CallList {
    private static String TAG = CallList.class.getSimpleName();

    private static CallList sInstance;

    private final HashMap<Integer, Call> mCallMap = Maps.newHashMap();
    private final Set<Listener> mListeners = Sets.newArraySet();

    /**
     * Static singleton accessor method.
     */
    public static synchronized CallList getInstance() {
        if (sInstance == null) {
            sInstance = new CallList();
        }
        return sInstance;
    }

    /**
     * Private constructor.  Instance should only be acquired through getInstance().
     */
    private CallList() {
    }

    /**
     * Called when a single call has changed.
     */
    public void onUpdate(Call call) {
        updateCallInMap(call);

        notifyListenersOfChange();
    }

    /**
     * Called when multiple calls have changed.
     */
    public void onUpdate(List<Call> callsToUpdate) {
        Preconditions.checkNotNull(callsToUpdate);
        for (Call call : callsToUpdate) {
            updateCallInMap(call);
        }

        notifyListenersOfChange();
    }

    public void addListener(Listener listener) {
        Preconditions.checkNotNull(listener);
        mListeners.add(listener);
    }

    public void removeListener(Listener listener) {
        Preconditions.checkNotNull(listener);
        mListeners.remove(listener);
    }

    /**
     * TODO(klp): Change so that this function is not needed. Instead of assuming there is an active
     * call, the code should rely on the status of a specific Call and allow the presenters to
     * update the Call object when the active call changes.
     */
    public Call getIncomingOrActive() {
        Call retval = null;

        for (Call call : mCallMap.values()) {
            if (call.getState() == Call.State.INCOMING) {
                retval = call;
                // incoming call takes precedence, cut out early.
                break;
            } else if (retval == null && call.getState() == Call.State.ACTIVE) {
                retval = call;
            }
        }

        return retval;
    }

    /**
     * Sends a generic notification to all listeners that something has changed.
     * It is up to the listeners to call back to determine what changed.
     */
    private void notifyListenersOfChange() {
        for (Listener listener : mListeners) {
            listener.onCallListChange(this);
        }
    }

    private void updateCallInMap(Call call) {
        Preconditions.checkNotNull(call);

        final Integer id = new Integer(call.getCallId());

        if (!isCallDead(call)) {
            mCallMap.put(id, call);
        } else if (mCallMap.containsKey(id)) {
            mCallMap.remove(id);
        }
    }

    private boolean isCallDead(Call call) {
        final int state = call.getState();
        return Call.State.IDLE == state || Call.State.INVALID == state;
    }

    /**
     * Listener interface for any class that wants to be notified of changes
     * to the call list.
     */
    public interface Listener {
        public void onCallListChange(CallList callList);
    }
}
