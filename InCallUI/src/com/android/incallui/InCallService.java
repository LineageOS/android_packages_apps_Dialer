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

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telecomm.CallInfo;
import android.telecomm.IInCallAdapter;
import android.telecomm.IInCallService;

import com.android.services.telephony.common.Call;

/**
 * Used to receive updates about calls from the Telecomm component.  This service is bound to
 * Telecomm while there exist calls which potentially require UI. This includes ringing (incoming),
 * dialing (outgoing), and active calls. When the last call is disconnected, Telecomm will unbind to
 * the service triggering InCallActivity (via CallList) to finish soon after.
 */
public class InCallService extends Service {

    /**
     * The actual service implementation that is passed to Telecomm as a binder. Implements all the
     * methods of {@link IInCallService}. Most methods ultimately update one or more calls in
     * {@link CallList} which in turn will trigger UI activity.
     */
    private class InCallServiceBinder extends IInCallService.Stub {
        /**
         * TODO(santoscordon): Rename this to setTelecommAdapter.
         * {@inheritDoc}
         */
        @Override public void setInCallAdapter(final IInCallAdapter inCallAdapter) {
            mHandler.post(new Runnable() {
                @Override public void run() {
                    InCallPresenter.getInstance().setTelecommAdapter(inCallAdapter);
                }
            });
        }

        /** {@inheritDoc} */
        @Override public void addCall(final CallInfo callInfo) {
            mHandler.post(new Runnable() {
                @Override public void run() {
                    Call call = CallInfoTranslator.getCall(callInfo);
                    CallList.getInstance().onUpdate(call);
                }
            });
        }

        /** {@inheritDoc} */
        @Override public void setActive(final String callId) {
            mHandler.post(new Runnable() {
                @Override public void run() {
                    Call call = CallInfoTranslator.getCall(callId);
                    if (null != call) {
                        call.setState(Call.State.ACTIVE);
                        if (call.getConnectTime() == 0) {
                            call.setConnectTime(System.currentTimeMillis());
                        }
                        CallList.getInstance().onUpdate(call);
                    }
                }
            });
        }

        /** {@inheritDoc} */
        @Override public void setDisconnected(final String callId) {
            mHandler.post(new Runnable() {
                @Override public void run() {
                    Call call = CallInfoTranslator.getCall(callId);
                    if (null != call) {
                        call.setState(Call.State.DISCONNECTED);
                        CallList.getInstance().onDisconnect(call);

                        // Remove it from the mapping since we no longer need to interact
                        // with the Call.
                        CallInfoTranslator.removeCall(callId);
                    }
                }
            });
        }
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    /** Instance of IInCallService which is sent to Telecomm. */
    private final InCallServiceBinder mBinder = new InCallServiceBinder();

    /** {@inheritDoc} */
    @Override public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /** {@inheritDoc} */
    @Override public void onCreate() {
        InCallPresenter inCallPresenter = InCallPresenter.getInstance();
        inCallPresenter.setUp(
                getApplicationContext(), CallList.getInstance(), AudioModeProvider.getInstance());
    }

    /** {@inheritDoc} */
    @Override public void onDestroy() {
        // Tear down the InCall system
        CallList.getInstance().clearOnDisconnect();
        InCallPresenter.getInstance().tearDown();
    }
}
