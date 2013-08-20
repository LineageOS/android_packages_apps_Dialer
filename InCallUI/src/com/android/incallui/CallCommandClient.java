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

    public static synchronized CallCommandClient getInstance() {
        if (sInstance == null) {
            sInstance = new CallCommandClient();
        }
        return sInstance;
    }

    private ICallCommandService mCommandService;

    private CallCommandClient() {
    }

    public void setService(ICallCommandService service) {
        mCommandService = service;
    }

    public void answerCall(int callId) {
        if (mCommandService == null) {
            Logger.e(this, "Cannot answer call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.answerCall(callId);
        } catch (RemoteException e) {
            Logger.e(this, "Error answering call.", e);
        }
    }

    public void rejectCall(int callId, boolean rejectWithMessage, String message) {
        if (mCommandService == null) {
            Logger.e(this, "Cannot reject call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.rejectCall(callId, rejectWithMessage, message);
        } catch (RemoteException e) {
            Logger.e(this, "Error rejecting call.", e);
        }
    }

    public void disconnectCall(int callId) {
        if (mCommandService == null) {
            Logger.e(this, "Cannot disconnect call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.disconnectCall(callId);
        } catch (RemoteException e) {
            Logger.e(this, "Error answering call.", e);
        }
    }

    public void mute(boolean onOff) {
        if (mCommandService == null) {
            Logger.e(this, "Cannot mute call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.mute(onOff);
        } catch (RemoteException e) {
            Logger.e(this, "Error muting phone.", e);
        }
    }

    public void hold(int callId, boolean onOff) {
        if (mCommandService == null) {
            Logger.e(this, "Cannot hold call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.hold(callId, onOff);
        } catch (RemoteException e) {
            Logger.e(this, "Error holding call.", e);
        }
    }

    public void merge() {
        if (mCommandService == null) {
            Logger.e(this, "Cannot merge call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.merge();
        } catch (RemoteException e) {
            Logger.e(this, "Error merging calls.", e);
        }
    }

    public void swap() {
        if (mCommandService == null) {
            Logger.e(this, "Cannot swap call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.swap();
        } catch (RemoteException e) {
            Logger.e(this, "Error merging calls.", e);
        }
    }

    public void addCall() {
        if (mCommandService == null) {
            Logger.e(this, "Cannot add call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.addCall();
        } catch (RemoteException e) {
            Logger.e(this, "Error merging calls.", e);
        }
    }

    public void setAudioMode(int mode) {
        if (mCommandService == null) {
            Logger.e(this, "Cannot set audio mode; CallCommandService == null");
            return;
        }
        try {
            mCommandService.setAudioMode(mode);
        } catch (RemoteException e) {
            Logger.e(this, "Error setting speaker.", e);
        }
    }

    public void playDtmfTone(char digit, boolean timedShortTone) {
        if (mCommandService == null) {
            Logger.e(this, "Cannot start dtmf tone; CallCommandService == null");
            return;
        }
        try {
            Logger.v(this, "Sending dtmf tone " + digit);
            mCommandService.playDtmfTone(digit, timedShortTone);
        } catch (RemoteException e) {
            Logger.e(this, "Error setting speaker.", e);
        }

    }

    public void stopDtmfTone() {
        if (mCommandService == null) {
            Logger.e(this, "Cannot stop dtmf tone; CallCommandService == null");
            return;
        }
        try {
            Logger.v(this, "Stop dtmf tone ");
            mCommandService.stopDtmfTone();
        } catch (RemoteException e) {
            Logger.e(this, "Error setting speaker.", e);
        }
    }
}
