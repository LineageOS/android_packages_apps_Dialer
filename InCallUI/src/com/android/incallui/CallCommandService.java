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
import android.util.Log;

import com.android.internal.util.Preconditions;
import com.android.services.telephony.common.ICallCommandService;

/**
 * Main interface for phone related commands.
 */
public class CallCommandService {

    private static final String TAG = CallCommandService.class.getSimpleName();

    private static CallCommandService sInstance;

    public static CallCommandService getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("CallCommandService has not been initialized.");
        }
        return sInstance;
    }

    // TODO(klp): Not sure if static call is ok. Might need to switch to normal service binding.
    public static void init(ICallCommandService service) {
        Preconditions.checkState(sInstance == null);
        sInstance = new CallCommandService(service);
    }


    private ICallCommandService mCommandService;

    private CallCommandService(ICallCommandService service) {
        mCommandService = service;
    }

    public void answerCall(int callId) {
        try {
            mCommandService.answerCall(callId);
        } catch (RemoteException e) {
            Log.e(TAG, "answerCall : " + e);
        }
    }

    public void mute() {

    }

    public void turnSpeakerOn() {

    }
}
