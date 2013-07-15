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
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.android.services.telephony.common.ICallMonitorService;

/**
 * Service used to listen for call state changes.
 */
public class CallMonitorService extends Service {

    private static final String TAG = CallMonitorService.class.getSimpleName();
    private static final boolean DBG = false; // TODO: Have a shared location for this.
    private MainHandler mMainHandler;

    private static final int DO_SHOW_ALERT = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        mMainHandler = new MainHandler();
    }

    @Override
    public void onDestroy() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final ICallMonitorService.Stub mBinder = new ICallMonitorService.Stub() {
        public void onIncomingCall(int callId) {
            final Message msg = mMainHandler.obtainMessage(DO_SHOW_ALERT, 0, 0,
                    "Incoming call with call Id: " + callId);
            mMainHandler.sendMessage(msg);

            final Intent intent = new Intent(getApplication(), InCallActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    };

    /**
     * Handles messages from the Service methods so that they get called on the
     * main thread.  Service methods by default are handled in background threads.
     */
    class MainHandler extends Handler {
        MainHandler() {
            super(getApplicationContext().getMainLooper(), null, true);
        }

        @Override
        public void handleMessage(Message msg) {
            executeMessage(msg);
        }
    }

    private void showAlert(String message) {
        Context context = getApplicationContext();
        int duration = Toast.LENGTH_SHORT;

        Toast.makeText(context, message, duration).show();
    }

    private void executeMessage(Message msg) {
        if (DBG) {
            Log.d(TAG, "executeMessage(" + msg.what + ")");
        }
        switch (msg.what) {
            case DO_SHOW_ALERT:
                showAlert((String) msg.obj);
                break;
            default:
                break;

        }
    }
 }
