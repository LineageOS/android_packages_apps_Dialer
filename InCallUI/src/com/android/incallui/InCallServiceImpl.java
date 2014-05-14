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
import android.telecomm.CallInfo;
import android.telecomm.GatewayInfo;
import android.telecomm.InCallAdapter;
import android.telephony.DisconnectCause;

import com.google.common.collect.ImmutableList;

/**
 * Used to receive updates about calls from the Telecomm component.  This service is bound to
 * Telecomm while there exist calls which potentially require UI. This includes ringing (incoming),
 * dialing (outgoing), and active calls. When the last call is disconnected, Telecomm will unbind to
 * the service triggering InCallActivity (via CallList) to finish soon after.
 */
public class InCallServiceImpl extends android.telecomm.InCallService {

    private static final ImmutableList<String> EMPTY_RESPONSE_TEXTS = ImmutableList.of();

    /** {@inheritDoc} */
    @Override public void onCreate() {
        Log.v(this, "onCreate");
        InCallPresenter inCallPresenter = InCallPresenter.getInstance();
        inCallPresenter.setUp(
                getApplicationContext(), CallList.getInstance(), AudioModeProvider.getInstance());
    }

    /** {@inheritDoc} */
    @Override public void onDestroy() {
        Log.v(this, "onDestroy");
        // Tear down the InCall system
        CallList.getInstance().clearOnDisconnect();
        InCallPresenter.getInstance().tearDown();
    }

    /**
     * TODO(santoscordon): Rename this to setTelecommAdapter.
     * {@inheritDoc}
     */
    @Override protected void setInCallAdapter(InCallAdapter inCallAdapter) {
        Log.v(this, "setInCallAdapter");
        TelecommAdapter.getInstance().setAdapter(inCallAdapter);
    }

    /** {@inheritDoc} */
    @Override protected void addCall(CallInfo callInfo) {
        Log.v(this, "addCall, state: " + callInfo.getState());
        Call call = new Call(callInfo.getId(),
                callInfo.getOriginalHandle().getSchemeSpecificPart());
        switch(callInfo.getState()) {
            case RINGING:
                call.setState(Call.State.INCOMING);
                break;
            case DIALING:
                call.setState(Call.State.DIALING);
                break;
            case ACTIVE:
                call.setState(Call.State.ACTIVE);
                break;
            case DISCONNECTED:
                call.setState(Call.State.DISCONNECTED);
                break;
            default:
                call.setState(Call.State.INVALID);
                break;
        }

        GatewayInfo gatewayInfo = callInfo.getGatewayInfo();
        if (gatewayInfo != null) {
            call.setGatewayNumber(gatewayInfo.getGatewayHandle().getSchemeSpecificPart());
            call.setGatewayPackage(gatewayInfo.getGatewayProviderPackageName());
        }

        call.addCapabilities(Call.Capabilities.HOLD | Call.Capabilities.MUTE);

        if (call.getState() == Call.State.INCOMING) {
            CallList.getInstance().onIncoming(call, EMPTY_RESPONSE_TEXTS);
        } else {
            CallList.getInstance().onUpdate(call);
        }
    }

    /** {@inheritDoc} */
    @Override protected void setActive(String callId) {
        Call call = CallList.getInstance().getCall(callId);
        Log.v(this, "setActive: " + call);
        if (null != call) {
            call.setState(Call.State.ACTIVE);
            if (call.getConnectTime() == 0) {
                call.setConnectTime(System.currentTimeMillis());
            }
            CallList.getInstance().onUpdate(call);
        }
    }

    /** {@inheritDoc} */
    @Override protected void setDialing(String callId) {
        Call call = CallList.getInstance().getCall(callId);
        Log.v(this, "setDialing: " + call);
        if (null != call) {
            call.setState(Call.State.DIALING);
            CallList.getInstance().onUpdate(call);
        }
    }

    /** {@inheritDoc} */
    @Override protected void setRinging(String callId) {
        // TODO(ihab): Implement this.
        Log.v(this, "setRinging");
    }

    /** {@inheritDoc} */
    @Override protected void setPostDial(String callId, String remaining) {
        // TODO(ihab): Add post-dial state to user interface
        // TODO(ihab: Do the equivalent in the new framework:
    }

    /** {@inheritDoc} */
    @Override protected void setPostDialWait(String callId, String remaining) {
        // TODO(ihab): Add post-dial state to user interface
        // TODO(ihab): Do the equivalent in the new framework:
    }

    /** {@inheritDoc} */
    @Override protected void setDisconnected(String callId, int disconnectCause) {
        Log.v(this, "setDisconnected");
        Call call = CallList.getInstance().getCall(callId);
        if (null != call) {
            call.setDisconnectCause(DisconnectCause.NORMAL);
            call.setState(Call.State.DISCONNECTED);
            CallList.getInstance().onDisconnect(call);
        }
    }

    /** {@inheritDoc} */
    @Override protected void setOnHold(String callId) {
        Call call = CallList.getInstance().getCall(callId);
        if (null != call) {
            call.setState(Call.State.ONHOLD);
            CallList.getInstance().onUpdate(call);
        }
    }

    /** {@inheritDoc} */
    @Override protected void onAudioStateChanged(CallAudioState audioState) {
        AudioModeProvider.getInstance().onAudioModeChange(audioState.route, audioState.isMuted);
        AudioModeProvider.getInstance().onSupportedAudioModeChange(audioState.supportedRouteMask);
    }
}
