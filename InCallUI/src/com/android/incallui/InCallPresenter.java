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

import com.google.android.collect.Sets;
import com.google.common.base.Preconditions;

import android.content.Context;
import android.content.Intent;

import com.android.services.telephony.common.Call;

import java.util.Set;

/**
 * Takes updates from the CallList and notifies the InCallActivity (UI)
 * of the changes.
 * Responsible for starting the activity for a new call and finishing the activity when all calls
 * are disconnected.
 * Creates and manages the in-call state and provides a listener pattern for the presenters
 * that want to listen in on the in-call state changes.
 * TODO(klp): This class has become more of a state machine at this point.  Consider renaming.
 */
public class InCallPresenter implements CallList.Listener {

    private static InCallPresenter sInCallPresenter;

    private final StatusBarNotifier mStatusBarNotifier;
    private final Set<InCallStateListener> mListeners = Sets.newHashSet();

    private InCallState mInCallState = InCallState.HIDDEN;
    private InCallActivity mInCallActivity;

    public static InCallPresenter getInstance() {
        Preconditions.checkNotNull(sInCallPresenter);
        return sInCallPresenter;
    }

    public static synchronized InCallPresenter init(Context context) {
        Preconditions.checkState(sInCallPresenter == null);
        sInCallPresenter = new InCallPresenter(context);
        return sInCallPresenter;
    }

    public void setActivity(InCallActivity inCallActivity) {
        mInCallActivity = inCallActivity;
        mInCallState = InCallState.STARTED;

        Logger.d(this, "UI Initialized");

        // Since the UI just came up, imitate an update from the call list
        // to set the proper UI state.
        onCallListChange(CallList.getInstance());
    }

    /**
     * Called when there is a change to the call list.
     * Sets the In-Call state for the entire in-call app based on the information it gets from
     * CallList. Dispatches the in-call state to all listeners. Can trigger the creation or
     * destruction of the UI based on the states that is calculates.
     */
    @Override
    public void onCallListChange(CallList callList) {
        // fast fail if we are still starting up
        if (mInCallState == InCallState.STARTING_UP) {
            Logger.d(this, "Already on STARTING_UP, ignoring until ready");
            return;
        }

        InCallState newState = getPotentialStateFromCallList(callList);
        newState = startOrFinishUi(newState);

        // Set the new state before announcing it to the world
        mInCallState = newState;

        // notify listeners of new state
        for (InCallStateListener listener : mListeners) {
            Logger.d(this, "Notify " + listener + " of state " + mInCallState.toString());
            listener.onStateChange(mInCallState, callList);
        }
    }

    /**
     * Given the call list, return the state in which the in-call screen should be.
     */
    public InCallState getPotentialStateFromCallList(CallList callList) {
        InCallState newState = InCallState.HIDDEN;

        if (callList.getIncomingCall() != null) {
            newState = InCallState.INCOMING;
        } else if (callList.getOutgoingCall() != null) {
            newState = InCallState.OUTGOING;
        } else if (callList.getActiveCall() != null ||
                callList.getBackgroundCall() != null) {
            newState = InCallState.INCALL;
        }

        return newState;
    }

    public void addListener(InCallStateListener listener) {
        Preconditions.checkNotNull(listener);
        mListeners.add(listener);
    }

    public void removeListener(InCallStateListener listener) {
        Preconditions.checkNotNull(listener);
        mListeners.remove(listener);
    }

    /**
     * When the state of in-call changes, this is the first method to get called. It determines if
     * the UI needs to be started or finished depending on the new state and does it.
     * It returns a potential new middle state (STARTING_UP) if appropriate.
     */
    private InCallState startOrFinishUi(InCallState newState) {
        Logger.d(this, "startOrFInishUi: " + newState.toString());

        // TODO(klp): Consider a proper state machine implementation

        // if we need to show something, we need to start the Ui...
        if (!newState.isHidden()) {

            // When we attempt to go to any state from HIDDEN, it means that we need to create the
            // entire UI. However, the StatusBarNotifier is in charge of starting up the Ui because
            // it has special behavior in case we have to deal with an immersive foreground app.
            // We set the STARTING_UP state to let StatusBarNotifier know it needs to start the
            // the Ui.
            if (mInCallState.isHidden()) {
                return InCallState.STARTING_UP;
            }

        } else if (mInCallActivity != null) {
            // Null out reference before we start end sequence
            InCallActivity temp = mInCallActivity;
            mInCallActivity = null;

            temp.finish();
        }

        return newState;
    }

    /**
     * Private constructor. Must use getInstance() to get this singleton.
     */
    private InCallPresenter(Context context) {
        Preconditions.checkNotNull(context);

        mStatusBarNotifier = new StatusBarNotifier(context);
        addListener(mStatusBarNotifier);

        CallList.getInstance().addListener(this);
    }

    /**
     * All the main states of InCallActivity.
     */
    public enum InCallState {
        // InCall Screen is off and there are no calls
        HIDDEN,

        // In call is in the process of starting up
        STARTING_UP,

        // In call has started but is not displaying any information
        STARTED,

        // Incoming-call screen is up
        INCOMING,

        // In-call experience is showing
        INCALL,

        // User is dialing out
        OUTGOING;

        public boolean isIncoming() {
            return (this == INCOMING);
        }

        public boolean isHidden() {
            return (this == HIDDEN);
        }

        public boolean isConnectingOrConnected() {
            return (this == INCOMING ||
                    this == OUTGOING ||
                    this == INCALL);
        }
    }

    /**
     * Interface implemented by classes that need to know about the InCall State.
     */
    public interface InCallStateListener {
        // TODO(klp): Enhance state to contain the call objects instead of passing CallList
        public void onStateChange(InCallState state, CallList callList);
    }
}
