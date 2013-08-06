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
import com.google.common.collect.ImmutableMap;

import com.android.services.telephony.common.Call;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maintains the list of active calls received from CallHandlerService and notifies interested
 * classes of changes to the call list as they are received from the telephony stack.
 * Primary lister of changes to this class is InCallPresenter.
 */
public class CallList {
    private static final Map<Integer, String> STATE_MAP = ImmutableMap.<Integer, String>builder()
            .put(Call.State.ACTIVE, "ACTIVE")
            .put(Call.State.CALL_WAITING, "CALL_WAITING")
            .put(Call.State.DIALING, "DIALING")
            .put(Call.State.IDLE, "IDLE")
            .put(Call.State.INCOMING, "INCOMING")
            .put(Call.State.ONHOLD, "ONHOLD")
            .put(Call.State.INVALID, "INVALID")
            .build();

    private static CallList sInstance;

    private final HashMap<Integer, Call> mCallMap = Maps.newHashMap();
    private final HashMap<Integer, ArrayList<String>> mCallTextReponsesMap =
            Maps.newHashMap();
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
        Logger.d(this, "onUpdate - " + safeCallString(call));

        updateCallInMap(call);

        notifyListenersOfChange();
    }

    /**
     * Called when a single call has changed.
     */
    public void onUpdate(AbstractMap.SimpleEntry<Call, List<String> > incomingCall) {
        Logger.d(this, "onUpdate - " + safeCallString(incomingCall.getKey()));

        updateCallInMap(incomingCall.getKey());
        updateCallTextMap(incomingCall.getKey(), incomingCall.getValue());

        notifyListenersOfChange();
    }

    /**
     * Called when multiple calls have changed.
     */
    public void onUpdate(List<Call> callsToUpdate) {
        Logger.d(this, "onUpdate(...)");

        Preconditions.checkNotNull(callsToUpdate);
        for (Call call : callsToUpdate) {
            Logger.d(this, "\t" + safeCallString(call));

            updateCallInMap(call);
            updateCallTextMap(call, null);
        }

        notifyListenersOfChange();
    }

    public void addListener(Listener listener) {
        Preconditions.checkNotNull(listener);

        mListeners.add(listener);

        // Let the listener know about the active calls immediately.
        listener.onCallListChange(this);
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
        Call retval = getIncomingCall();
        if (retval == null) {
            retval = getActiveCall();
        }
        return retval;
    }

    public Call getActiveCall() {
        return getFirstCallWithState(Call.State.ACTIVE);
    }

    public Call getBackgroundCall() {
        return getFirstCallWithState(Call.State.ONHOLD);
    }

    public Call getSecondBackgroundCall() {
        return getCallWithState(Call.State.ONHOLD, 1);
    }

    public Call getActiveOrBackgroundCall() {
        Call call = getActiveCall();
        if (call == null) {
            call = getBackgroundCall();
        }
        return call;
    }

    public Call getIncomingCall() {
        Call call = getFirstCallWithState(Call.State.INCOMING);
        if (call == null) {
            call = getFirstCallWithState(Call.State.CALL_WAITING);
        }

        return call;
    }

    public boolean existsLiveCall() {
        for (Call call : mCallMap.values()) {
            if (!isCallDead(call)) {
                return true;
            }
        }
        return false;
    }

    public ArrayList<String> getTextResponses(Call call) {
        return mCallTextReponsesMap.get(call.getCallId());
    }

    /**
     * Returns first call found in the call map with the specified state.
     */
    public Call getFirstCallWithState(int state) {
        return getCallWithState(state, 0);
    }

    /**
     * Returns the [position]th call found in the call map with the specified state.
     * TODO(klp): Improve this logic to sort by call time.
     */
    public Call getCallWithState(int state, int positionToFind) {
        Call retval = null;
        int position = 0;
        for (Call call : mCallMap.values()) {
            if (call.getState() == state) {
                if (position >= positionToFind) {
                    retval = call;
                    break;
                } else {
                    position++;
                }
            }
        }

        Logger.d(this, "Found " + (retval == null ? "no " : "") + "call with state: " +
                STATE_MAP.get(state));
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

    private void updateCallTextMap(Call call, List<String> textResponses) {
        Preconditions.checkNotNull(call);

        final Integer id = new Integer(call.getCallId());

        if (!isCallDead(call)) {
            if (textResponses != null) {
                mCallTextReponsesMap.put(id, (ArrayList<String>) textResponses);
            }
        } else if (mCallMap.containsKey(id)) {
            mCallTextReponsesMap.remove(id);
        }
    }

    private boolean isCallDead(Call call) {
        final int state = call.getState();
        return Call.State.IDLE == state || Call.State.INVALID == state;
    }

    /**
     * Creates a log-safe string for call objects.
     */
    private String safeCallString(Call call) {
        final StringBuffer buffer = new StringBuffer();
        buffer.append("Call (")
                .append(call.getCallId())
                .append("), ")
                .append(STATE_MAP.get(call.getState()));
        return buffer.toString();
    }

    /**
     * Listener interface for any class that wants to be notified of changes
     * to the call list.
     */
    public interface Listener {
        public void onCallListChange(CallList callList);
    }
}
