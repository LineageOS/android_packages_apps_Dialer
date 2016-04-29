/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
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

import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import java.util.Objects;

/**
 * Listens to call state changes from {@class InCallStateListener} and keeps track of the current
 * primary call.
 */
public class PrimaryCallTracker implements InCallStateListener, IncomingCallListener {

    private Call mPrimaryCall;

    public PrimaryCallTracker() {
    }

    @Override
    public void onIncomingCall(InCallPresenter.InCallState oldState,
            InCallPresenter.InCallState newState, Call call) {
        // same logic should happen as with onStateChange()
        onStateChange(oldState, InCallPresenter.InCallState.INCOMING, CallList.getInstance());
    }

    /**
     * Handles state changes (including incoming calls)
     *
     * @param newState The in call state.
     * @param callList The call list.
     */
    @Override
    public void onStateChange(InCallPresenter.InCallState oldState,
            InCallPresenter.InCallState newState, CallList callList) {
        Log.d(this, "onStateChange: oldState" + oldState + " newState=" + newState +
                "callList =" + callList);

        // Determine the primary active call.
        Call primaryCall = null;

        if (newState == InCallPresenter.InCallState.INCOMING) {
            primaryCall = callList.getIncomingCall();
        } else if (newState == InCallPresenter.InCallState.OUTGOING) {
            primaryCall = callList.getOutgoingCall();
        } else if (newState == InCallPresenter.InCallState.PENDING_OUTGOING) {
            primaryCall = callList.getPendingOutgoingCall();
        } else if (newState == InCallPresenter.InCallState.INCALL) {
            primaryCall = callList.getActiveOrBackgroundCall();
        }

        if (!Objects.equals(mPrimaryCall, primaryCall)) {
            mPrimaryCall = primaryCall;
        }
    }

    /**
     * Returns the current primary call.
     */
    public Call getPrimaryCall() {
        return mPrimaryCall;
    }

    /**
     * Checks if the current call passed in is a primary call. Returns true if it is, false
     * otherwise
     *
     * @param Call The call to be compared with the primary call.
     */
    public boolean isPrimaryCall(final Call call) {
        return Objects.equals(mPrimaryCall, call);
    }
}
