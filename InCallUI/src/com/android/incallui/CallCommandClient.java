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

import android.os.RemoteException;

import com.android.internal.util.Preconditions;
import com.android.services.telephony.common.ICallCommandService;

/**
 * Main interface for phone related commands.
 */
public class CallCommandClient {

    private static CallCommandClient sInstance;

    public static CallCommandClient getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("CallCommandClient has not been initialized.");
        }
        return sInstance;
    }

    // TODO(klp): Not sure if static call is ok. Might need to switch to normal service binding.
    public static void init(ICallCommandService service) {
        Preconditions.checkState(sInstance == null);
        sInstance = new CallCommandClient(service);
    }


    private ICallCommandService mCommandService;

    private CallCommandClient(ICallCommandService service) {
        mCommandService = service;
    }

    public void answerCall(int callId) {
        try {
            mCommandService.answerCall(callId);
        } catch (RemoteException e) {
            Logger.e(this, "Error answering call.", e);
        }
    }

    public void disconnectCall(int callId) {
        try {
            mCommandService.disconnectCall(callId);
        } catch (RemoteException e) {
            Logger.e(this, "Error answering call.", e);
        }
    }

    public void mute(boolean onOff) {
        try {
            mCommandService.mute(onOff);
        } catch (RemoteException e) {
            Logger.e(this, "Error muting phone.", e);
        }
    }

    public void hold(int callId, boolean onOff) {
        try {
            mCommandService.hold(callId, onOff);
        } catch (RemoteException e) {
            Logger.e(this, "Error holding call.", e);
        }
    }

    public void turnSpeakerOn(boolean onOff) {
        try {
            mCommandService.speaker(onOff);
        } catch (RemoteException e) {
            Logger.e(this, "Error setting speaker.", e);
        }
    }
}
