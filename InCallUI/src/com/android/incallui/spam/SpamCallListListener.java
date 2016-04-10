/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.incallui.spam;

import com.google.common.annotations.VisibleForTesting;

import android.content.Context;
import android.telecom.DisconnectCause;
import android.text.TextUtils;

import com.android.dialer.calllog.CallLogAsyncTaskUtil;
import com.android.incallui.Call;
import com.android.incallui.CallList;
import com.android.incallui.Log;

public class SpamCallListListener implements CallList.Listener {
    private static final String TAG  = "SpamCallListListener";

    private final Context mContext;

    public SpamCallListListener(Context context) {
        mContext = context;
    }

    @Override
    public void onIncomingCall(final Call call) {
        String number = call.getNumber();
        if (TextUtils.isEmpty(number)) {
            return;
        }
        CallLogAsyncTaskUtil.getNumberInCallHistory(mContext, number,
                new CallLogAsyncTaskUtil.OnGetNumberInCallHistoryListener() {
                    @Override
                    public void onComplete(boolean inCallHistory) {
                        call.setCallHistoryStatus(inCallHistory ?
                                Call.CALL_HISTORY_STATUS_PRESENT
                                : Call.CALL_HISTORY_STATUS_NOT_PRESENT);
                    }
                });
    }

    @Override
    public void onUpgradeToVideo(Call call) {}

    @Override
    public void onCallListChange(CallList callList) {}

    @Override
    public void onDisconnect(Call call) {
        if (shouldShowAfterCallNotification(call)) {
            showNotification(call.getNumber());
        }
    }

    /**
     * Posts the intent for displaying the after call spam notification to the user.
     */
    @VisibleForTesting
    /* package */ void showNotification(String number) {
        //TODO(mhashmi): build and show notifications here
    }

    /**
     * Determines if the after call notification should be shown for the specified call.
     */
    private boolean shouldShowAfterCallNotification(Call call) {
        String number = call.getNumber();
        if (TextUtils.isEmpty(number)) {
            return false;
        }

        Call.LogState logState = call.getLogState();
        if (!logState.isIncoming) {
            return false;
        }

        if (logState.duration <= 0) {
            return false;
        }

        if (logState.contactLookupResult != Call.LogState.LOOKUP_NOT_FOUND
                && logState.contactLookupResult != Call.LogState.LOOKUP_UNKNOWN) {
            return false;
        }

        int callHistoryStatus = call.getCallHistoryStatus();
        if (callHistoryStatus == Call.CALL_HISTORY_STATUS_PRESENT) {
            return false;
        } else if (callHistoryStatus == Call.CALL_HISTORY_STATUS_UNKNOWN) {
            Log.i(TAG, "Call history status is unknown, returning false");
            return false;
        }

        // Check if call disconnected because of either user hanging up
        int disconnectCause = call.getDisconnectCause().getCode();
        if (disconnectCause != DisconnectCause.LOCAL && disconnectCause != DisconnectCause.REMOTE) {
            return false;
        }

        Log.i(TAG, "shouldShowAfterCallNotification, returning true");
        return true;
    }
}