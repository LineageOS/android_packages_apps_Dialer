/* Copyright (c) 2015, 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.incallui;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.net.Uri;
import android.view.WindowManager;
import android.os.Bundle;

import org.codeaurora.ims.utils.QtiCallUtils;
import org.codeaurora.ims.QtiCallConstants;

/*
 * This class handles redialing a call on CS domain when current call ends with reason
 * cs retry required
 */
public class InCallCsRedialHandler implements CallList.Listener {

    private static InCallCsRedialHandler sInCallCsRedialHandler;
    private Context mContext;
    private CallList mCallList = null;
    private AlertDialog mAlert = null;

    /**
     * Private constructor. Must use getInstance() to get this singleton.
     */
    private InCallCsRedialHandler() {
    }

    /**
     * Handles set up of the {@class InCallCsRedialHandler}. Instantiates the context needed by
     * the class and adds a listener to listen to call substate changes.
     */
    public void setUp(Context context) {
        mContext = context;
        mCallList = CallList.getInstance();
        mCallList.addListener(this);
    }

    /**
     * Handles tear down of the {@class InCallCsRedialHandler}. Sets the context to null and
     * unregisters it's call substate listener.
     */
    public void tearDown() {
        mContext = null;
        if (mCallList != null) {
            mCallList.removeListener(this);
            mCallList = null;
        }
    }

    /**
     * This method overrides onIncomingCall method of {@interface CallList.Listener}
     * Added for completeness. No implementation yet.
     */
    @Override
    public void onIncomingCall(Call call) {
        // no-op
    }

    /**
     * This method overrides onCallListChange method of {@interface CallList.Listener}
     * Added for completeness. No implementation yet.
     */
    @Override
    public void onCallListChange(CallList list) {
        // no-op
    }

    /**
     * This method overrides onUpgradeToVideo method of {@interface CallList.Listener}
     * Added for completeness. No implementation yet.
     */
    @Override
    public void onUpgradeToVideo(Call call) {
        // no-op
    }

    /**
     * This method overrides onDisconnect method of {@interface CallList.Listener}
     */
    @Override
    public void onDisconnect(Call call) {
        Log.i(this, "onDisconnect");
        checkForCsRetry(call);
    }

    /**
     * This method returns a singleton instance of {@class InCallCsRedialHandler}
     */
    public static synchronized InCallCsRedialHandler getInstance() {
        if (sInCallCsRedialHandler == null) {
            sInCallCsRedialHandler = new InCallCsRedialHandler();
        }
        return sInCallCsRedialHandler;
    }

    /*
     * This method gets fail cause value corresponding to EXTRAS_KEY_CALL_FAIL_EXTRA_CODE key
     */
    private int getFailCauseFromExtras(Bundle extras) {
        int failCause = QtiCallConstants.DISCONNECT_CAUSE_UNSPECIFIED;
        if (extras != null) {
            failCause = extras.getInt(QtiCallConstants.EXTRAS_KEY_CALL_FAIL_EXTRA_CODE,
                    QtiCallConstants.DISCONNECT_CAUSE_UNSPECIFIED);
        }
        return failCause;
    }

    /*
     * This method checks to see if CS Retry is required or not and if
     * required, the method further checks the user selection option to decide
     * whether to CS Redial automatically or based on user confirmation
     */
    private void checkForCsRetry(final Call call) {
        final int failCause = getFailCauseFromExtras(call.getExtras());
        Log.i(this, "checkForCsRetry failCause: " + failCause);
        if (failCause != QtiCallConstants.CALL_FAIL_EXTRA_CODE_CALL_CS_RETRY_REQUIRED) {
            return;
        }

        if (QtiCallUtils.isCsRetryEnabledByUser(mContext)) {
            dialCsCall(call.getNumber());
        } else {
            showCsRedialDialogOnDisconnect(call.getNumber());
        }
    }

    /*
     * This method initiates a CS call
     */
    private void dialCsCall(String number) {
        Log.i(this, "dialCsCall number: " + number);

        final Uri uri = Uri.fromParts("tel", number, null);
        final Intent intent = new Intent(Intent.ACTION_CALL, uri);
        intent.putExtra(QtiCallConstants.EXTRA_CALL_DOMAIN, QtiCallConstants.DOMAIN_CS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            mContext.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(this, "Activity for dialing new call isn't found.");
        }
    }

    /*
     * If user confirmation is required to retry the call on CS domain, this method
     * displays a dialog seeking user confirmation
     */
    private void showCsRedialDialogOnDisconnect(final String dialString) {
        final InCallActivity inCallActivity = InCallPresenter.getInstance().getActivity();

        if (inCallActivity == null) {
            Log.e(this, "showCsRedialDialogOnDisconnect inCallActivity is NULL");
            return;
        }
        inCallActivity.dismissPendingDialogs();

        mAlert = new AlertDialog.Builder(inCallActivity).setTitle(R.string.cs_redial_option)
                .setMessage(R.string.cs_redial_msg)
                .setPositiveButton(android.R.string.yes, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialCsCall(dialString);
                    }
                })
                .setNegativeButton(android.R.string.no, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //No implementation. Added for completeness
                    }
                })
                .setOnDismissListener(new OnDismissListener() {
                    @Override
                    public void onDismiss(final DialogInterface dialog) {
                        Log.d(this, "showCsRedialDialogOnDisconnect calling onDialogDismissed");
                        onDialogDismissed();
                    }
                })
                .create();

        mAlert.setCanceledOnTouchOutside(false);
        mAlert.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        mAlert.show();
    }

    /*
     * This method returns true if dialog is showing else false
     */
    private boolean isCsRetryDialogShowing() {
        return mAlert != null && mAlert.isShowing();
    }

    /**
     * A dialog could have prevented in-call screen from being previously finished.
     * This function checks to see if there should be any UI left and if not attempts
     * to tear down the UI.
     */
    private void onDialogDismissed() {
        mAlert = null;
        InCallPresenter.getInstance().onDismissDialog();
    }

    /*
     * This method dismisses the CS retry dialog
     */
    public void dismissPendingDialogs() {
        if (isCsRetryDialogShowing()) {
            mAlert.dismiss();
            mAlert = null;
        }
    }

    /*
     * This method returns true if the dialog is still visible and waiting for user confirmation
     * else false
     */
    public boolean hasPendingDialogs() {
        return mAlert != null;
    }
}
