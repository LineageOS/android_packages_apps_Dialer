/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.util.Preconditions;
import com.android.services.telephony.common.ICallCommandService;
import com.android.services.telephony.common.ICallMonitorService;

/**
 * Service used to listen for call state changes.
 */
public class CallMonitorService extends Service {

    private static final String TAG = CallMonitorService.class.getSimpleName();
    private static final boolean DBG = false; // TODO: Have a shared location for this.

    private static ICallCommandService mCallCommandService;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final ICallMonitorService.Stub mBinder = new ICallMonitorService.Stub() {

        @Override
        public void setCallCommandService(ICallCommandService service) {
            logD("onConnected: " + service.toString());
            mCallCommandService = service;
        }

        @Override
        public void onIncomingCall(int callId) {
            final Intent intent = new Intent(getApplication(), InCallActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    };

    // TODO(klp): Not sure if static call is ok. Might need to switch to normal service binding.
    public static void answerCall(int callId) {
        Preconditions.checkState(mCallCommandService != null);

        try {
            mCallCommandService.answerCall(callId);
        } catch (RemoteException e) {
            Log.e(TAG, "answerCall : " + e);
        }
    }

    private void logD(String message) {
        if (DBG) {
            Log.d(TAG, message);
        }
    }
}
