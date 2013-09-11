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

import com.android.services.telephony.common.AudioMode;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.ICallCommandService;
import com.android.services.telephony.common.ICallHandlerService;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

/**
 * Service used to listen for call state changes.
 */
public class CallHandlerService extends Service {

    private final static String TAG = CallHandlerService.class.getSimpleName();

    private static final int ON_UPDATE_CALL = 1;
    private static final int ON_UPDATE_MULTI_CALL = 2;
    private static final int ON_UPDATE_CALL_WITH_TEXT_RESPONSES = 3;
    private static final int ON_AUDIO_MODE = 4;
    private static final int ON_SUPPORTED_AUDIO_MODE = 5;
    private static final int ON_DISCONNECT_CALL = 6;
    private static final int ON_BRING_TO_FOREGROUND = 7;
    private static final int ON_POST_CHAR_WAIT = 8;
    private static final int ON_CREATE = 9;
    private static final int ON_DESTROY = 10;

    private static final int LARGEST_MSG_ID = ON_DESTROY;


    private CallList mCallList;
    private Handler mMainHandler;
    private Object mHandlerInitLock = new Object();
    private InCallPresenter mInCallPresenter;
    private AudioModeProvider mAudioModeProvider;

    @Override
    public void onCreate() {
        Log.i(this, "onCreate");
        super.onCreate();

        synchronized(mHandlerInitLock) {
            if (mMainHandler == null) {
                mMainHandler = new MainHandler();
            }
        }

        // Creation (and destruction) are sent to the message handler.  The reason for this is that
        // at any time the service could potentially unbind for both legitimate reasons as well as
        // app crashes and it's better to queue up create/destroy because:
        // (1) Previous actions like onUpdate/onDisconnect could be queued up and we dont want to
        //     destroy the system from a different thread in the middle of executing those actions.
        // (2) If we queue up destruction we must also queue up creation or else we risk having a
        //     second "create" happen before the first "destroy".
        //     (e.g., create1, queue destroy1, create2, do destroy1)
        mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_CREATE));

        // TODO: consider optimization of checking to see if any ON_DESTROY messages exist
        // in the queue and in those cases simply remove the pending message.
    }

    @Override
    public void onDestroy() {
        Log.i(this, "onDestroy");

        mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_DESTROY));
    }


    @Override
    public IBinder onBind(Intent intent) {
        Log.i(this, "onBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(this, "onUnbind");

        // Returning true here means we get called on rebind, which is a feature we do not need.
        // Return false so that all reconections happen with a call to onBind().
        return false;
    }

    private final ICallHandlerService.Stub mBinder = new ICallHandlerService.Stub() {

        @Override
        public void setCallCommandService(ICallCommandService service) {
            try {
                Log.d(CallHandlerService.this, "onConnected: " + service.toString());
                CallCommandClient.getInstance().setService(service);
            } catch (Exception e) {
                Log.e(TAG, "Error processing setCallCommandservice() call", e);
            }
        }

        @Override
        public void onDisconnect(Call call) {
            try {
                mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_DISCONNECT_CALL, call));
            } catch (Exception e) {
                Log.e(TAG, "Error processing onDisconnect() call.", e);
            }
        }

        @Override
        public void onIncoming(Call call, List<String> textResponses) {
            try {
                Map.Entry<Call, List<String>> incomingCall
                        = new AbstractMap.SimpleEntry<Call, List<String>>(call, textResponses);
                mMainHandler.sendMessage(mMainHandler.obtainMessage(
                        ON_UPDATE_CALL_WITH_TEXT_RESPONSES, incomingCall));
            } catch (Exception e) {
                Log.e(TAG, "Error processing onIncoming() call.", e);
            }
        }

        @Override
        public void onUpdate(List<Call> calls) {
            try {
                mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_UPDATE_MULTI_CALL, calls));
            } catch (Exception e) {
                Log.e(TAG, "Error processing onUpdate() call.", e);
            }
        }

        @Override
        public void onAudioModeChange(int mode, boolean muted) {
            try {
                mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_AUDIO_MODE, mode,
                            muted ? 1 : 0, null));
            } catch (Exception e) {
                Log.e(TAG, "Error processing onAudioModeChange() call.", e);
            }
        }

        @Override
        public void onSupportedAudioModeChange(int modeMask) {
            try {
                mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_SUPPORTED_AUDIO_MODE,
                        modeMask, 0, null));
            } catch (Exception e) {
                Log.e(TAG, "Error processing onSupportedAudioModeChange() call.", e);
            }
        }

        @Override
        public void bringToForeground() {
            mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_BRING_TO_FOREGROUND));
        }

        @Override
        public void onPostDialWait(int callId, String chars) {
            mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_POST_CHAR_WAIT, callId, 0,
                    chars));
        }
    };

    private void doCreate() {
        Log.i(this, "doCreate");

        mCallList = CallList.getInstance();
        mAudioModeProvider = AudioModeProvider.getInstance();
        mInCallPresenter = InCallPresenter.getInstance();

        mInCallPresenter.setUp(getApplicationContext(), mCallList, mAudioModeProvider);
    }

    public void doDestroy() {
        Log.i(this, "doDestroy");

        // The service gets disconnected under two circumstances:
        // 1. When there are no more calls
        // 2. When the phone app crashes.
        // If (2) happens, we can't leave the UI thinking that there are still
        // live calls.  So we will tell the callList to clear as a final request.
        mCallList.clearOnDisconnect();
        mCallList = null;

        mInCallPresenter.tearDown();
        mInCallPresenter = null;
        mAudioModeProvider = null;
    }

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
        if (msg.what > LARGEST_MSG_ID) {
            // If you got here, you may have added a new message and forgotten to
            // update LARGEST_MSG_ID
            Log.wtf(this, "Cannot handle message larger than LARGEST_MSG_ID.");
        }

        Log.d(this, "executeMessage " + msg.what);

        switch (msg.what) {
            case ON_UPDATE_CALL:
                Log.i(CallHandlerService.this, "onUpdate: " + msg.obj);
                mCallList.onUpdate((Call) msg.obj);
                break;
            case ON_UPDATE_MULTI_CALL:
                Log.i(CallHandlerService.this, "onUpdateMulti: " + msg.obj);
                mCallList.onUpdate((List<Call>) msg.obj);
                break;
            case ON_UPDATE_CALL_WITH_TEXT_RESPONSES:
                AbstractMap.SimpleEntry<Call, List<String>> entry
                        = (AbstractMap.SimpleEntry<Call, List<String>>) msg.obj;
                Log.i(CallHandlerService.this, "onIncomingCall: " + entry.getKey());
                mCallList.onIncoming(entry.getKey(), entry.getValue());
                break;
            case ON_DISCONNECT_CALL:
                Log.i(CallHandlerService.this, "onDisconnected: " + msg.obj);
                mCallList.onDisconnect((Call) msg.obj);
                break;
            case ON_POST_CHAR_WAIT:
                mInCallPresenter.onPostDialCharWait(msg.arg1, (String) msg.obj);
                break;
            case ON_AUDIO_MODE:
                Log.i(CallHandlerService.this, "onAudioModeChange : " +
                        AudioMode.toString(msg.arg1) + ", muted (" + (msg.arg2 == 1) + ")");
                mAudioModeProvider.onAudioModeChange(msg.arg1, msg.arg2 == 1);
                break;
            case ON_SUPPORTED_AUDIO_MODE:
                Log.i(CallHandlerService.this, "onSupportedAudioModeChange : " + AudioMode.toString(
                        msg.arg1));

                mAudioModeProvider.onSupportedAudioModeChange(msg.arg1);
                break;
            case ON_BRING_TO_FOREGROUND:
                if (mInCallPresenter != null) {
                    mInCallPresenter.bringToForeground();
                }
                break;
            case ON_CREATE:
                doCreate();
                break;
            case ON_DESTROY:
                doDestroy();
                break;
            default:
                break;
        }
    }
}
