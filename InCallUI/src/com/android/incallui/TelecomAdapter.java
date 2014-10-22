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
import android.telecom.InCallAdapter;
import android.telecom.Phone;
import android.telecom.PhoneAccountHandle;

import android.telecom.PhoneCapabilities;

import com.google.common.base.Preconditions;

import java.util.List;

/** Wrapper around {@link InCallAdapter} that only forwards calls to the adapter when it's valid. */
final class TelecomAdapter implements InCallPhoneListener {
    private static final String ADD_CALL_MODE_KEY = "add_call_mode";

    private static TelecomAdapter sInstance;
    private Context mContext;
    private Phone mPhone;

    static TelecomAdapter getInstance() {
        Preconditions.checkState(Looper.getMainLooper().getThread() == Thread.currentThread());
        if (sInstance == null) {
            sInstance = new TelecomAdapter();
        }
        return sInstance;
    }

    private TelecomAdapter() {
    }

    void setContext(Context context) {
        mContext = context;
    }

    @Override
    public void setPhone(Phone phone) {
        mPhone = phone;
    }

    @Override
    public void clearPhone() {
        mPhone = null;
    }

    private android.telecom.Call getTelecommCallById(String callId) {
        final Call call = CallList.getInstance().getCallById(callId);
        return call == null ? null : call.getTelecommCall();
    }

    void answerCall(String callId, int videoState) {
        if (mPhone != null) {
            final android.telecom.Call call = getTelecommCallById(callId);
            if (call != null) {
                call.answer(videoState);
            } else {
                Log.e(this, "error answerCall, call not in call list: " + callId);
            }
        } else {
            Log.e(this, "error answerCall, mPhone is null");
        }
    }

    void rejectCall(String callId, boolean rejectWithMessage, String message) {
        if (mPhone != null) {
            final android.telecom.Call call = getTelecommCallById(callId);
            if (call != null) {
                call.reject(rejectWithMessage, message);
            } else {
                Log.e(this, "error rejectCall, call not in call list: " + callId);
            }
        } else {
            Log.e(this, "error rejectCall, mPhone is null");
        }
    }

    void disconnectCall(String callId) {
        if (mPhone != null) {
            getTelecommCallById(callId).disconnect();
        } else {
            Log.e(this, "error disconnectCall, mPhone is null");
        }
    }

    void holdCall(String callId) {
        if (mPhone != null) {
            getTelecommCallById(callId).hold();
        } else {
            Log.e(this, "error holdCall, mPhone is null");
        }
    }

    void unholdCall(String callId) {
        if (mPhone != null) {
            getTelecommCallById(callId).unhold();
        } else {
            Log.e(this, "error unholdCall, mPhone is null");
        }
    }

    void mute(boolean shouldMute) {
        if (mPhone != null) {
            mPhone.setMuted(shouldMute);
        } else {
            Log.e(this, "error mute, mPhone is null");
        }
    }

    void setAudioRoute(int route) {
        if (mPhone != null) {
            mPhone.setAudioRoute(route);
        } else {
            Log.e(this, "error setAudioRoute, mPhone is null");
        }
    }

    void turnOnProximitySensor() {
        if (mPhone != null) {
            mPhone.setProximitySensorOn();
        } else {
            Log.e(this, "error setProximitySensorOn, mPhone is null");
        }
    }

    void turnOffProximitySensor(boolean screenOnImmediately) {
        if (mPhone != null) {
            mPhone.setProximitySensorOff(screenOnImmediately);
        } else {
            Log.e(this, "error setProximitySensorOff, mPhone is null");
        }
    }

    void separateCall(String callId) {
        if (mPhone != null) {
            getTelecommCallById(callId).splitFromConference();
        } else {
            Log.e(this, "error separateCall, mPhone is null.");
        }
    }

    void merge(String callId) {
        if (mPhone != null) {
            android.telecom.Call call = getTelecommCallById(callId);
            List<android.telecom.Call> conferenceable = call.getConferenceableCalls();
            if (!conferenceable.isEmpty()) {
                call.conference(conferenceable.get(0));
            } else {
                int capabilities = call.getDetails().getCallCapabilities();
                if (0 != (capabilities & PhoneCapabilities.MERGE_CONFERENCE)) {
                    call.mergeConference();
                }
            }
        } else {
            Log.e(this, "error merge, mPhone is null.");
        }
    }

    void swap(String callId) {
        if (mPhone != null) {
            android.telecom.Call call = getTelecommCallById(callId);
            int capabilities = call.getDetails().getCallCapabilities();
            if (0 != (capabilities & PhoneCapabilities.SWAP_CONFERENCE)) {
                call.swapConference();
            }
        } else {
            Log.e(this, "Error swap, mPhone is null.");
        }
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
        if (mPhone != null) {
            getTelecommCallById(callId).playDtmfTone(digit);
        } else {
            Log.e(this, "error playDtmfTone, mPhone is null");
        }
    }

    void stopDtmfTone(String callId) {
        if (mPhone != null) {
            getTelecommCallById(callId).stopDtmfTone();
        } else {
            Log.e(this, "error stopDtmfTone, mPhone is null");
        }
    }

    void postDialContinue(String callId, boolean proceed) {
        if (mPhone != null) {
            getTelecommCallById(callId).postDialContinue(proceed);
        } else {
            Log.e(this, "error postDialContinue, mPhone is null");
        }
    }

    void phoneAccountSelected(String callId, PhoneAccountHandle accountHandle) {
        if (mPhone != null) {
            getTelecommCallById(callId).phoneAccountSelected(accountHandle);
        }  else {
            Log.e(this, "error phoneAccountSelected, mAdapter is null");
        }

        if (accountHandle == null) {
            Log.e(this, "error phoneAccountSelected, accountHandle is null");
        }
    }
}
