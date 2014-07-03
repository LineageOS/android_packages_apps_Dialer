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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.telecomm.InCallAdapter;

import com.google.common.base.Preconditions;

/** Wrapper around {@link InCallAdapter} that only forwards calls to the adapter when it's valid. */
final class TelecommAdapter {
    private static final String ADD_CALL_MODE_KEY = "add_call_mode";

    private static TelecommAdapter sInstance;
    private Context mContext;
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

    void setContext(Context context) {
        mContext = context;
    }

    void setAdapter(InCallAdapter adapter) {
        mAdapter = adapter;
    }

    void answerCall(String callId) {
        if (mAdapter != null) {
            mAdapter.answerCall(callId);
        } else {
            Log.e(this, "error answerCall, mAdapter is null");
        }
    }

    void rejectCall(String callId, boolean rejectWithMessage, String message) {
        if (mAdapter != null) {
            mAdapter.rejectCall(callId, rejectWithMessage, message);
        } else {
            Log.e(this, "error rejectCall, mAdapter is null");
        }
    }

    void disconnectCall(String callId) {
        if (mAdapter != null) {
            mAdapter.disconnectCall(callId);
        } else {
            Log.e(this, "error disconnectCall, mAdapter is null");
        }
    }

    void holdCall(String callId) {
        if (mAdapter != null) {
            mAdapter.holdCall(callId);
        } else {
            Log.e(this, "error holdCall, mAdapter is null");
        }
    }

    void unholdCall(String callId) {
        if (mAdapter != null) {
            mAdapter.unholdCall(callId);
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

    void separateCall(String callId) {
        if (mAdapter != null) {
            mAdapter.splitFromConference(callId);
        } else {
            Log.e(this, "error separateCall, mAdapter is null.");
        }
    }

    void merge(String callId) {
        if (mAdapter != null) {
            mAdapter.conference(callId);
        } else {
            Log.e(this, "error merge, mAdapter is null.");
        }
    }

    void swap() {
        Log.wtf(this, "swap not implemented");
    }

    void addCall() {
        if (mContext != null) {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // when we request the dialer come up, we also want to inform
            // it that we're going through the "add call" option from the
            // InCallScreen / PhoneUtils.
            intent.putExtra(ADD_CALL_MODE_KEY, true);
            try {
                Log.d(this, "Sending the add Call intent");
                mContext.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                // This is rather rare but possible.
                // Note: this method is used even when the phone is encrypted. At that moment
                // the system may not find any Activity which can accept this Intent.
                Log.e(this, "Activity for adding calls isn't found.", e);
            }
        }
    }

    void playDtmfTone(String callId, char digit) {
        if (mAdapter != null) {
            mAdapter.playDtmfTone(callId, digit);
        } else {
            Log.e(this, "error playDtmfTone, mAdapter is null");
        }
    }

    void stopDtmfTone(String callId) {
        if (mAdapter != null) {
            mAdapter.stopDtmfTone(callId);
        } else {
            Log.e(this, "error stopDtmfTone, mAdapter is null");
        }
    }

    void postDialContinue(String callId, boolean proceed) {
        if (mAdapter != null) {
            mAdapter.postDialContinue(callId, proceed);
        } else {
            Log.e(this, "error postDialContinue, mAdapter is null");
        }
    }

    void phoneAccountClicked(String callId) {
        if (mAdapter != null) {
            mAdapter.phoneAccountClicked(callId);
        } else {
            Log.e(this, "error phoneAccountClicked, mAdapter is null");
        }
    }

    void setSystemBarNavigationEnabled(boolean enable) {
        // TODO(sail): Implement this.
    }
}
