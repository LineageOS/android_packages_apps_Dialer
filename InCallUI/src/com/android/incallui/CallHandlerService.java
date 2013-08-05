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

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.ICallCommandService;
import com.android.services.telephony.common.ICallHandlerService;

import java.util.List;

/**
 * Service used to listen for call state changes.
 */
public class CallHandlerService extends Service {

    private static final int ON_UPDATE_CALL = 1;
    private static final int ON_UPDATE_MULTI_CALL = 2;

    private CallList mCallList;
    private Handler mMainHandler;
    private InCallPresenter mInCallPresenter;

    @Override
    public void onCreate() {
        super.onCreate();

        mCallList = CallList.getInstance();
        mMainHandler = new MainHandler();
        mInCallPresenter = InCallPresenter.init(this);
    }

    @Override
    public void onDestroy() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final ICallHandlerService.Stub mBinder = new ICallHandlerService.Stub() {

        @Override
        public void setCallCommandService(ICallCommandService service) {
            Logger.d(this, "onConnected: " + service.toString());
            CallCommandClient.init(service);
        }

        @Override
        public void onDisconnect(Call call) {
            mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_UPDATE_CALL, 0, 0, call));
        }

        @Override
        public void onUpdate(List<Call> calls, boolean fullUpdate) {
            // TODO(klp): Add use of fullUpdate to message
            mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_UPDATE_MULTI_CALL, 0, 0, calls));
        }
    };

    /**
     * Handles messages from the service so that they get executed on the main thread, where they
     * can interact with UI.
     */
    private class MainHandler extends Handler {
        MainHandler() {
            super(getApplicationContext().getMainLooper(), null, true);
        }

        @Override
        public void handleMessage(Message msg) {
            executeMessage(msg);
        }
    }

    private void executeMessage(Message msg) {
        switch (msg.what) {
            case ON_UPDATE_CALL:
                mCallList.onUpdate((Call) msg.obj);
                break;
            case ON_UPDATE_MULTI_CALL:
                mCallList.onUpdate((List<Call>) msg.obj);
                break;
            default:
                break;
        }
    }
}
