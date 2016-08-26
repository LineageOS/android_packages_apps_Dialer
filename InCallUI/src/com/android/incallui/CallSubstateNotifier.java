/* Copyright (c) 2015, 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.incallui;

import org.codeaurora.ims.QtiCallConstants;
import android.os.Bundle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.HashMap;
import java.util.List;
import com.google.common.base.Preconditions;
import com.android.incallui.InCallPresenter.InCallDetailsListener;

/**
 * This class listens to incoming events from the {@class InCallDetailsListener}.
 * When call details change, this class is notified and we parse the callExtras from the details to
 * figure out if call substate has changed and notify the {@class InCallMessageController} to
 * display the indication on UI.
 *
 */
public class CallSubstateNotifier implements InCallDetailsListener, CallList.Listener {

    private final List<InCallSubstateListener> mCallSubstateListeners =
            new CopyOnWriteArrayList<>();

    private static CallSubstateNotifier sCallSubstateNotifier;
    private final HashMap<String, Integer> mCallSubstateMap = new HashMap<>();

    /**
     * This method returns a singleton instance of {@class CallSubstateNotifier}
     */
    public static synchronized CallSubstateNotifier getInstance() {
        if (sCallSubstateNotifier == null) {
            sCallSubstateNotifier = new CallSubstateNotifier();
        }
        return sCallSubstateNotifier;
    }

    /**
     * This method adds a new call substate listener. Users interested in listening to call
     * substate changes should add a listener of type {@class InCallSubstateListener}
     */
    public void addListener(InCallSubstateListener listener) {
        Preconditions.checkNotNull(listener);
        mCallSubstateListeners.add(listener);
    }

    /**
     * This method removes an existing call substate listener. Users listening to call
     * substate changes when not interested any more can de-register an existing listener of type
     * {@class InCallSubstateListener}
     */
    public void removeListener(InCallSubstateListener listener) {
        if (listener != null) {
            mCallSubstateListeners.remove(listener);
        } else {
            Log.e(this, "Can't remove null listener");
        }
    }

    /**
     * Private constructor. Must use getInstance() to get this singleton.
     */
    private CallSubstateNotifier() {
    }

    private int getCallSubstate(Bundle callExtras) {
        return callExtras.getInt(QtiCallConstants.CALL_SUBSTATE_EXTRA_KEY,
                QtiCallConstants.CALL_SUBSTATE_NONE);
    }

    /**
     * This method overrides onDetailsChanged method of {@class InCallDetailsListener}. We are
     * notified when call details change and extract the call substate from the callExtras, detect
     * if call substate changed and notify all registered listeners.
     */
    @Override
    public void onDetailsChanged(Call call, android.telecom.Call.Details details) {
        Log.d(this, "onDetailsChanged - call: " + call + "details: " + details);

        if (call == null || details == null ||
                !Call.State.isConnectingOrConnected(call.getState())) {
            Log.d(this, "onDetailsChanged - Call/details is null/Call is not connected. Return");
            return;
        }

        final Bundle callExtras = details.getExtras();

        if (callExtras == null) {
            return;
        }

        final String callId = call.getId();

        final int oldCallSubstate = mCallSubstateMap.containsKey(callId) ?
                mCallSubstateMap.get(callId) : QtiCallConstants.CALL_SUBSTATE_NONE;
        final int newCallSubstate = getCallSubstate(callExtras);

        if (oldCallSubstate == newCallSubstate) {
            return;
        }

        mCallSubstateMap.put(callId, newCallSubstate);
        Preconditions.checkNotNull(mCallSubstateListeners);
        for (InCallSubstateListener listener : mCallSubstateListeners) {
            listener.onCallSubstateChanged(call, newCallSubstate);
        }
    }

    /**
     * This method overrides onDisconnect method of {@interface CallList.Listener}
     */
    @Override
    public void onDisconnect(final Call call) {
        Log.d(this, "onDisconnect: call: " + call);
        mCallSubstateMap.remove(call.getId());
    }

    @Override
    public void onUpgradeToVideo(Call call) {
        //NO-OP
    }

    @Override
    public void onIncomingCall(Call call) {
        //NO-OP
    }

    @Override
    public void onCallListChange(CallList callList) {
        //NO-OP
    }
}
