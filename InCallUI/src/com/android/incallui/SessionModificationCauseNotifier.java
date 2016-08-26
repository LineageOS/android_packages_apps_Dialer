/* Copyright (c) 2015, The Linux Foundation. All rights reserved.
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

import android.os.Bundle;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.google.common.base.Preconditions;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.HashMap;
import java.util.List;

import org.codeaurora.ims.QtiCallConstants;

/**
 * This class listens to incoming events from the {@class InCallDetailsListener}.
 * When call details change, this class is notified and we parse the extras from the details to
 * figure out if session modification cause has been sent when a call upgrades/downgrades and
 * notify the {@class InCallMessageController} to display the indication on UI.
 */
public class SessionModificationCauseNotifier implements InCallDetailsListener, CallList.Listener {

    private final List<InCallSessionModificationCauseListener> mSessionModificationCauseListeners
            = new CopyOnWriteArrayList<>();

    private static SessionModificationCauseNotifier sSessionModificationCauseNotifier;
    private final HashMap<String, Integer> mSessionModificationCauseMap = new HashMap<>();

    /**
     * Returns a singleton instance of {@class SessionModificationCauseNotifier}
     */
    public static synchronized SessionModificationCauseNotifier getInstance() {
        if (sSessionModificationCauseNotifier == null) {
            sSessionModificationCauseNotifier = new SessionModificationCauseNotifier();
        }
        return sSessionModificationCauseNotifier;
    }

    /**
     * Adds a new session modification cause listener. Users interested in this cause
     * should add a listener of type {@class InCallSessionModificationCauseListener}
     */
    public void addListener(InCallSessionModificationCauseListener listener) {
        Preconditions.checkNotNull(listener);
        mSessionModificationCauseListeners.add(listener);
    }

    /**
     * Removes an existing session modification cause listener. Users listening to any cause
     * changes when not interested any more can de-register an existing listener of type
     * {@class InCallSessionModificationCauseListener}
     */
    public void removeListener(InCallSessionModificationCauseListener listener) {
        if (listener != null) {
            mSessionModificationCauseListeners.remove(listener);
        } else {
            Log.e(this, "Can't remove null listener");
        }
    }

    /**
     * Private constructor. Must use getInstance() to get this singleton.
     */
    private SessionModificationCauseNotifier() {
    }

    private int getSessionModificationCause(Bundle callExtras) {
        return callExtras.getInt(QtiCallConstants.SESSION_MODIFICATION_CAUSE_EXTRA_KEY,
                QtiCallConstants.CAUSE_CODE_UNSPECIFIED);
    }
    /**
     * Overrides onDetailsChanged method of {@class InCallDetailsListener}. We are
     * notified when call details change and extract the session modification cause from the
     * extras, detect if the cause has changed and notify all registered listeners.
     */
    @Override
    public void onDetailsChanged(Call call, android.telecom.Call.Details details) {
        Log.d(this, "onDetailsChanged: - call: " + call + "details: " + details);

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

        final int oldSessionModificationCause = mSessionModificationCauseMap.containsKey(callId) ?
            mSessionModificationCauseMap.get(callId) : QtiCallConstants.CAUSE_CODE_UNSPECIFIED;
        final int newSessionModificationCause = getSessionModificationCause(callExtras);

        if (oldSessionModificationCause == newSessionModificationCause) {
            return;
        }

        mSessionModificationCauseMap.put(callId, newSessionModificationCause);
        // Notify all listeners only when there is a valid value
        if (newSessionModificationCause != QtiCallConstants.CAUSE_CODE_UNSPECIFIED) {
            Preconditions.checkNotNull(mSessionModificationCauseListeners);
            for (InCallSessionModificationCauseListener listener :
                mSessionModificationCauseListeners) {
                    listener.onSessionModificationCauseChanged(call, newSessionModificationCause);
            }
        }
    }

    /**
     * This method overrides onDisconnect method of {@interface CallList.Listener}
     */
    @Override
    public void onDisconnect(final Call call) {
        Log.d(this, "onDisconnect: call: " + call);
        mSessionModificationCauseMap.remove(call.getId());
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
