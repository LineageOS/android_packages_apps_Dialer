/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.incallui.call;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.support.annotation.VisibleForTesting;
import android.telecom.InCallService;
import com.android.dialer.common.LogUtil;
import java.util.List;

/** Wrapper around Telecom APIs. */
public class TelecomAdapter implements InCallServiceListener {

  private static final String ADD_CALL_MODE_KEY = "add_call_mode";

  private static TelecomAdapter sInstance;
  private InCallService mInCallService;

  private TelecomAdapter() {}

  @MainThread
  public static TelecomAdapter getInstance() {
    if (!Looper.getMainLooper().isCurrentThread()) {
      throw new IllegalStateException();
    }
    if (sInstance == null) {
      sInstance = new TelecomAdapter();
    }
    return sInstance;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  public static void setInstanceForTesting(TelecomAdapter telecomAdapter) {
    sInstance = telecomAdapter;
  }

  @Override
  public void setInCallService(InCallService inCallService) {
    mInCallService = inCallService;
  }

  @Override
  public void clearInCallService() {
    mInCallService = null;
  }

  private android.telecom.Call getTelecomCallById(String callId) {
    DialerCall call = CallList.getInstance().getCallById(callId);
    return call == null ? null : call.getTelecomCall();
  }

  public void mute(boolean shouldMute) {
    if (mInCallService != null) {
      mInCallService.setMuted(shouldMute);
    } else {
      LogUtil.e("TelecomAdapter.mute", "mInCallService is null");
    }
  }

  public void setAudioRoute(int route) {
    if (mInCallService != null) {
      mInCallService.setAudioRoute(route);
    } else {
      LogUtil.e("TelecomAdapter.setAudioRoute", "mInCallService is null");
    }
  }

  public void merge(String callId) {
    android.telecom.Call call = getTelecomCallById(callId);
    if (call != null) {
      List<android.telecom.Call> conferenceable = call.getConferenceableCalls();
      if (!conferenceable.isEmpty()) {
        call.conference(conferenceable.get(0));
        // It's safe to clear restrict count for merge action.
        DialerCall.clearRestrictedCount();
      } else {
        if (call.getDetails().can(android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
          call.mergeConference();
          // It's safe to clear restrict count for merge action.
          DialerCall.clearRestrictedCount();
        }
      }
    } else {
      LogUtil.e("TelecomAdapter.merge", "call not in call list " + callId);
    }
  }

  public void swap(String callId) {
    android.telecom.Call call = getTelecomCallById(callId);
    if (call != null) {
      if (call.getDetails().can(android.telecom.Call.Details.CAPABILITY_SWAP_CONFERENCE)) {
        call.swapConference();
      }
    } else {
      LogUtil.e("TelecomAdapter.swap", "call not in call list " + callId);
    }
  }

  public void addCall() {
    if (mInCallService != null) {
      Intent intent = new Intent(Intent.ACTION_DIAL);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

      // when we request the dialer come up, we also want to inform
      // it that we're going through the "add call" option from the
      // InCallScreen / PhoneUtils.
      intent.putExtra(ADD_CALL_MODE_KEY, true);
      try {
        LogUtil.d("TelecomAdapter.addCall", "Sending the add DialerCall intent");
        mInCallService.startActivity(intent);
      } catch (ActivityNotFoundException e) {
        // This is rather rare but possible.
        // Note: this method is used even when the phone is encrypted. At that moment
        // the system may not find any Activity which can accept this Intent.
        LogUtil.e("TelecomAdapter.addCall", "Activity for adding calls isn't found.", e);
      }
    }
  }

  public void playDtmfTone(String callId, char digit) {
    android.telecom.Call call = getTelecomCallById(callId);
    if (call != null) {
      call.playDtmfTone(digit);
    } else {
      LogUtil.e("TelecomAdapter.playDtmfTone", "call not in call list " + callId);
    }
  }

  public void stopDtmfTone(String callId) {
    android.telecom.Call call = getTelecomCallById(callId);
    if (call != null) {
      call.stopDtmfTone();
    } else {
      LogUtil.e("TelecomAdapter.stopDtmfTone", "call not in call list " + callId);
    }
  }

  public void postDialContinue(String callId, boolean proceed) {
    android.telecom.Call call = getTelecomCallById(callId);
    if (call != null) {
      call.postDialContinue(proceed);
    } else {
      LogUtil.e("TelecomAdapter.postDialContinue", "call not in call list " + callId);
    }
  }

  public boolean canAddCall() {
    if (mInCallService != null) {
      return mInCallService.canAddCall();
    }
    return false;
  }
}
