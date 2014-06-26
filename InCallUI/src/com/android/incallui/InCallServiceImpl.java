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

import android.telecomm.CallAudioState;
import android.telecomm.CallState;
import android.telecomm.InCallAdapter;
import android.telecomm.InCallCall;
import android.telecomm.InCallService;

import com.google.common.collect.ImmutableList;

/**
 * Used to receive updates about calls from the Telecomm component.  This service is bound to
 * Telecomm while there exist calls which potentially require UI. This includes ringing (incoming),
 * dialing (outgoing), and active calls. When the last call is disconnected, Telecomm will unbind to
 * the service triggering InCallActivity (via CallList) to finish soon after.
 */
public class InCallServiceImpl extends InCallService {
    private static final ImmutableList<String> EMPTY_RESPONSE_TEXTS = ImmutableList.of();

    /** {@inheritDoc} */
    @Override public void onCreate() {
        Log.v(this, "onCreate");
        InCallPresenter inCallPresenter = InCallPresenter.getInstance();
        inCallPresenter.setUp(
                getApplicationContext(), CallList.getInstance(), AudioModeProvider.getInstance());
        TelecommAdapter.getInstance().setContext(this);
    }

    /** {@inheritDoc} */
    @Override public void onDestroy() {
        Log.v(this, "onDestroy");
        // Tear down the InCall system
        TelecommAdapter.getInstance().setAdapter(null);
        TelecommAdapter.getInstance().setContext(null);
        CallList.getInstance().clearOnDisconnect();
        InCallPresenter.getInstance().tearDown();
    }

    /** {@inheritDoc} */
    @Override protected void onAdapterAttached(InCallAdapter inCallAdapter) {
        Log.v(this, "onAdapterAttached");
        TelecommAdapter.getInstance().setAdapter(inCallAdapter);
    }

    /** {@inheritDoc} */
    @Override protected void addCall(InCallCall telecommCall) {
        Call call = new Call(telecommCall.getId());
        updateCall(call, telecommCall);
        Log.i(this, "addCall: " + call);

        if (call.getState() == Call.State.INCOMING) {
            CallList.getInstance().onIncoming(call, call.getCannedSmsResponses());
        } else {
            CallList.getInstance().onUpdate(call);
        }
    }

    /** {@inheritDoc} */
    @Override protected void updateCall(InCallCall telecommCall) {
        Call call = CallList.getInstance().getCall(telecommCall.getId());
        if (call == null) {
            Log.v(this, "updateCall for unknown call: " + telecommCall.getId());
            return;
        }

        int oldState = call.getState();
        updateCall(call, telecommCall);
        Log.i(this, "updateCall: " + telecommCall + " => " + call);

        if (oldState != call.getState() && call.getState() == Call.State.DISCONNECTED) {
            CallList.getInstance().onDisconnect(call);
        } else {
            CallList.getInstance().onUpdate(call);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void setPostDial(String callId, String remaining) {
        // TODO(ihab): Add post-dial state to user interface
    }

    /** {@inheritDoc} */
    @Override
    protected void setPostDialWait(String callId, String remaining) {
        InCallPresenter.getInstance().onPostDialCharWait(callId, remaining);
    }

    /** {@inheritDoc} */
    @Override
    protected void onAudioStateChanged(CallAudioState audioState) {
        AudioModeProvider.getInstance().onAudioModeChange(audioState.route, audioState.isMuted);
        AudioModeProvider.getInstance().onSupportedAudioModeChange(audioState.supportedRouteMask);
    }

    /** {@inheritDoc} */
    @Override
    protected void bringToForeground(boolean showDialpad) {
        Log.i(this, "Bringing UI to foreground.");
        InCallPresenter.getInstance().bringToForeground(showDialpad);
    }

    private void updateCall(Call call, InCallCall telecommCall) {
        call.setHandle(telecommCall.getHandle());
        call.setDisconnectCause(telecommCall.getDisconnectCauseCode());
        call.setCannedSmsResponses(telecommCall.getCannedSmsResponses());
        call.setCapabilities(telecommCall.getCapabilities());
        call.setConnectTimeMillis(telecommCall.getConnectTimeMillis());
        call.setGatewayInfo(telecommCall.getGatewayInfo());
        call.setSubscription(telecommCall.getSubscription());
        call.setCurrentCallServiceDescriptor(telecommCall.getCurrentCallServiceDescriptor());
        call.setHandoffCallServiceDescriptor(telecommCall.getHandoffCallServiceDescriptor());
        call.setState(translateState(telecommCall.getState()));
        call.setParentId(telecommCall.getParentCallId());
        call.setChildCallIds(telecommCall.getChildCallIds());
    }

    private static int translateState(CallState state) {
        switch (state) {
            case DIALING:
            case NEW:
                return Call.State.DIALING;
            case RINGING:
                return Call.State.INCOMING;
            case POST_DIAL:
            case POST_DIAL_WAIT:
            case ACTIVE:
                return Call.State.ACTIVE;
            case ON_HOLD:
                return Call.State.ONHOLD;
            case DISCONNECTED:
                return Call.State.DISCONNECTED;
            default:
                return Call.State.INVALID;
        }
    }
}
