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

import android.os.Looper;
import android.telecomm.InCallAdapter;

import com.google.common.base.Preconditions;

/** Wrapper around {@link InCallAdapter} that only forwards calls to the adapter when it's valid. */
final class TelecommAdapter {
    private static TelecommAdapter sInstance;
    private InCallAdapter mAdapter;

    static TelecommAdapter getInstance() {
        Preconditions.checkState(Looper.getMainLooper().getThread() == Thread.currentThread());
        if (sInstance == null) {
            sInstance = new TelecommAdapter();
        }
        return sInstance;
    }

    private TelecommAdapter() {
    }

    void setAdapter(InCallAdapter adapter) {
        mAdapter = adapter;
    }

    void answerCall(int callId) {
        if (mAdapter != null) {
            mAdapter.answerCall(getTelecommCallId(callId));
        } else {
            Log.e(this, "error answerCall, mAdapter is null");
        }
    }

    void rejectCall(int callId, boolean rejectWithMessage, String message) {
        if (mAdapter != null) {
            // TODO(sail): Add support for reject with message.
            mAdapter.rejectCall(getTelecommCallId(callId));
        } else {
            Log.e(this, "error rejectCall, mAdapter is null");
        }
    }

    void disconnectCall(int callId) {
        if (mAdapter != null) {
            mAdapter.disconnectCall(getTelecommCallId(callId));
        } else {
            Log.e(this, "error disconnectCall, mAdapter is null");
        }
    }

    void holdCall(int callId) {
        if (mAdapter != null) {
            mAdapter.holdCall(getTelecommCallId(callId));
        } else {
            Log.e(this, "error holdCall, mAdapter is null");
        }
    }

    void unholdCall(int callId) {
        if (mAdapter != null) {
            mAdapter.unholdCall(getTelecommCallId(callId));
        } else {
            Log.e(this, "error unholdCall, mAdapter is null");
        }
    }

    void mute(boolean shouldMute) {
        if (mAdapter != null) {
            mAdapter.mute(shouldMute);
        } else {
            Log.e(this, "error mute, mAdapter is null");
        }
    }

    void setAudioRoute(int route) {
        if (mAdapter != null) {
            mAdapter.setAudioRoute(route);
        } else {
            Log.e(this, "error setAudioRoute, mAdapter is null");
        }
    }

    void separateCall(int callId) {
        Log.wtf(this, "separateCall not implemented");
    }

    void merge() {
        Log.wtf(this, "merge not implemented");
    }

    void swap() {
        Log.wtf(this, "swap not implemented");
    }

    void addCall() {
        Log.wtf(this, "addCall not implemented");
    }

    void playDtmfTone(int callId, char digit) {
        if (mAdapter != null) {
            mAdapter.playDtmfTone(getTelecommCallId(callId), digit);
        } else {
            Log.e(this, "error playDtmfTone, mAdapter is null");
        }
    }

    void stopDtmfTone(int callId) {
        if (mAdapter != null) {
            mAdapter.stopDtmfTone(getTelecommCallId(callId));
        } else {
            Log.e(this, "error stopDtmfTone, mAdapter is null");
        }
    }

    void postDialWaitContinue(int callId) {
        Log.wtf(this, "postDialWaitContinue not implemented");
    }

    void postDialCancel(int callId) {
        Log.wtf(this, "postDialCancel not implemented");
    }

    void setSystemBarNavigationEnabled(boolean enable) {
        // TODO(sail): Implement this.
    }

    private String getTelecommCallId(int callId) {
        return CallList.getInstance().getCall(callId).getTelecommCallId();
    }
}
