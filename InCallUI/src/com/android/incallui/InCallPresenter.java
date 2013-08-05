/*
 * Copyright (C) 2006 The Android Open Source Project
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

import java.util.Set;

/**
 * Takes updates from the CallList and notifies the InCallActivity (UI)
 * of the changes.
 * Responsible for starting the activity for a new call and finishing the activity when all calls
 * are disconnected.
 * Creates and manages the in-call state and provides a listener pattern for the presenters
 * that want to listen in on the in-call state changes.
 */
public class InCallPresenter implements CallList.Listener {

    private static InCallPresenter sInCallPresenter;

    private Context mContext;
    private InCallState mInCallState = InCallState.HIDDEN;
    private InCallActivity mInCallActivity;
    private final Set<InCallStateListener> mListeners = Sets.newHashSet();

    public static synchronized InCallPresenter getInstance() {
        if (sInCallPresenter == null) {
            sInCallPresenter = new InCallPresenter();
        }
        return sInCallPresenter;
    }

    public void init(Context context) {
        Logger.i(this, "InCallPresenter initialized with context " + context);
        Preconditions.checkState(mContext == null);

        mContext = context;
        CallList.getInstance().addListener(this);
    }

    public void setActivity(InCallActivity inCallActivity) {
        mInCallActivity = inCallActivity;
        mInCallState = InCallState.STARTED;

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
            return;
        }

        InCallState newState = mInCallState;
        if (callList.getIncomingCall() != null) {
            newState = InCallState.INCOMING;
        } else if (callList.getActiveCall() != null) {
            newState = InCallState.INCALL;
        } else {
            newState = InCallState.HIDDEN;
        }

        newState = startOrFinishUi(newState);

        // finally set the new state before announcing it to the world
        mInCallState = newState;

        // notify listeners of new state
        for (InCallStateListener listener : mListeners) {
            Logger.d(this, "Notify " + listener + " of state " + mInCallState.toString());
            listener.onStateChange(mInCallState, callList);
        }
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
        // TODO(klp): Consider a proper state machine implementation

        // if we need to show something, we need to start the Ui...
        if (newState != InCallState.HIDDEN) {

            // ...only if the UI is currently hidden
            if (mInCallState == InCallState.HIDDEN) {
                // TODO(klp): Update the flags to match the PhoneApp activity
                final Intent intent = new Intent(mContext, InCallActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);

                return InCallState.STARTING_UP;
            }

        // (newState == InCallState.HIDDEN)
        // Else, we need to hide the UI...if it exists
        } else if (mInCallActivity != null) {
            mListeners.clear();

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
    private InCallPresenter() {
        CallList.getInstance().addListener(this);
    }

    /**
     * All the main states of InCallActivity.
     */
    public enum InCallState {
        HIDDEN,
        STARTING_UP,
        STARTED,
        INCOMING,
        INCALL
    };

    /**
     * Interface implemented by classes that need to know about the InCall State.
     */
    public interface InCallStateListener {
        // TODO(klp): Enhance state to contain the call objects instead of passing CallList
        public void onStateChange(InCallState state, CallList callList);
    }
}
