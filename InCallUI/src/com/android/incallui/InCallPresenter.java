/*
 * Copyright (C) 2006 The Android Open Source Project
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

import com.google.common.base.Preconditions;

import android.content.Context;
import android.content.Intent;
import android.util.Log;


/**
 * Takes updates from the CallList and notifies the InCallActivity (UI)
 * of the changes.  Also responsible for starting the InCallActivity when a call comes in.
 */
public class InCallPresenter implements CallList.Listener {
    private static final String TAG = InCallPresenter.class.getSimpleName();

    private static InCallPresenter sInCallPresenter;

    private Context mContext;
    private InCallState mInCallState = InCallState.HIDDEN;
    private InCallActivity mInCallActivity;

    public static synchronized InCallPresenter getInstance() {
        if (sInCallPresenter == null) {
            sInCallPresenter = new InCallPresenter();
        }
        return sInCallPresenter;
    }

    public void init(Context context) {
        Log.i(TAG, "InCallPresenter initialized with context " + context);
        Preconditions.checkState(mContext == null);

        mContext = context;
        CallList.getInstance().addListener(this);
    }

    public void setActivity(InCallActivity inCallActivity) {
        mInCallActivity = inCallActivity;

        mInCallActivity.showIncoming(CallList.getInstance().getIncomingCall() != null);
    }

    /**
     * Called when there is a change to the call list.  Responsible for starting and hiding
     * the InCall UI.
     */
    @Override
    public void onCallListChange(CallList callList) {
        // TODO: Organize this code a little better.  Too hard to read.

        final boolean showInCall = callList.existsLiveCall();

        if (showInCall && mInCallState == InCallState.HIDDEN) {

            // TODO(klp): Update the flags to match the PhoneApp activity
            final Intent intent = new Intent(mContext, InCallActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);

        } else if (!showInCall && mInCallState == InCallState.SHOWING_INCALL) {
            if (mInCallActivity != null) {
                mInCallActivity.finish();
            }
        }

        if (mInCallActivity != null) {
            boolean showIncoming = callList.getIncomingCall() != null;
            mInCallActivity.showIncoming(showIncoming);
        }

        mInCallState = showInCall ? InCallState.SHOWING_INCALL : InCallState.HIDDEN;
    }

    /**
     * Private constructor. Must use getInstance() to get this singleton.
     */
    private InCallPresenter() {
        CallList.getInstance().addListener(this);
    }

    /**
     * All the main states of InCallActivity.
     */
    private enum InCallState {
        HIDDEN,
        SHOWING_INCALL
    };
}
