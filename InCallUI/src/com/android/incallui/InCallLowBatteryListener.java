
/* Copyright (c) 2016, The Linux Foundation. All rights reserved.
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
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnKeyListener;
import android.os.Bundle;
import android.telecom.VideoProfile;
import android.view.KeyEvent;
import android.view.WindowManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.android.incallui.Call.State;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.InCallUiListener;

import com.google.common.base.Preconditions;
import org.codeaurora.ims.QtiCallConstants;
import org.codeaurora.ims.QtiImsException;
import org.codeaurora.ims.QtiImsExtManager;


/**
 * This class is responsible for processing video call low battery indication.
 * On detecting a low battery Video call, user shall be notified via a warning
 * dialog for user to take informed decision (i.e. convert video call to voice
 * call or hangup the video call etc). This class maintains a low battery map
 * and entry is added to this map on detecting a low battery video call. The
 * low battery map holds key value pairs with call object as key and a boolean
 * as value. The boolean value determines whether low battery indication for the
 * call can be processed or not. The map holds boolean value TRUE if low battery
 * indication for the call can be processed and holds FALSE when the low battery
 * indication processing is deferred or is done. For eg. the map holds FALSE
 * value on detecting a low battery MT Video call and the handling is deferred until
 * user decides to answer the call as Video in which case the map holds TRUE value
 * signalling that low battery indication for the call can be processed now.
 */
public class InCallLowBatteryListener implements CallList.Listener, InCallDetailsListener,
        InCallUiListener {

    private static InCallLowBatteryListener sInCallLowBatteryListener;
    private PrimaryCallTracker mPrimaryCallTracker;
    private CallList mCallList = null;
    private AlertDialog mAlert = null;
    private Map<Call, Boolean> mLowBatteryMap = new ConcurrentHashMap<Call, Boolean>();
    private final Boolean PROCESS_LOW_BATTERY = true;

    /**
     * Private constructor. Must use getInstance() to get this singleton.
     */
    private InCallLowBatteryListener() {
    }

    /**
     * Handles set up of the {@class InCallLowBatteryListener}.
     */
    public void setUp(Context context) {
        mPrimaryCallTracker = new PrimaryCallTracker();
        mCallList = CallList.getInstance();
        mCallList.addListener(this);
        InCallPresenter.getInstance().addListener(mPrimaryCallTracker);
        InCallPresenter.getInstance().addIncomingCallListener(mPrimaryCallTracker);
        InCallPresenter.getInstance().addDetailsListener(this);
        InCallPresenter.getInstance().addInCallUiListener(this);
    }

    /**
     * Handles tear down of the {@class InCallLowBatteryListener}.
     */
    public void tearDown() {
        if (mCallList != null) {
            mCallList.removeListener(this);
            mCallList = null;
        }
        InCallPresenter.getInstance().removeListener(mPrimaryCallTracker);
        InCallPresenter.getInstance().removeIncomingCallListener(mPrimaryCallTracker);
        InCallPresenter.getInstance().removeDetailsListener(this);
        InCallPresenter.getInstance().removeInCallUiListener(this);
        mLowBatteryMap.clear();
        mPrimaryCallTracker = null;
    }

     /**
     * This method returns a singleton instance of {@class InCallLowBatteryListener}
     */
    public static synchronized InCallLowBatteryListener getInstance() {
        if (sInCallLowBatteryListener == null) {
            sInCallLowBatteryListener = new InCallLowBatteryListener();
        }
        return sInCallLowBatteryListener;
    }

    /**
     * This method overrides onIncomingCall method of {@interface CallList.Listener}
     * @param call The call that is in incoming state
     */
    @Override
    public void onIncomingCall(Call call) {
        maybeAddToLowBatteryMap(call);
        // if low battery dialog is already visible to user, dismiss it
        dismissPendingDialogs();
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
     * @param call The call for which upgrade request is received
     */
    @Override
    public void onUpgradeToVideo(Call call) {
        //if low battery dialog is visible to user, dismiss it
        dismissPendingDialogs();
    }

    /**
     * This method overrides onDisconnect method of {@interface CallList.Listener}
     * @param call The call that is disconnected
     */
    @Override
    public void onDisconnect(Call call) {
        Log.d(this, "onDisconnect call: " + call);
        if (call == null) {
            return;
        }

        mLowBatteryMap.remove(call);
    }

    /**
      * This API handles InCallActivity destroy when low battery dialog is showing
      */
    public void onDestroyInCallActivity() {
        if (dismissPendingDialogs()) {
            Log.i(this, "onDestroyInCallActivity dismissed low battery dialog");

            /* Activity is destroyed when low battery dialog is showing, possibly
               by removing the activity from recent tasks list etc. Handle this by
               dismissing the existing low battery dialog and marking the entry
               against the call in low battery map that the low battery indication
               needs to be reprocessed for eg. when user brings back the call to
               foreground by pulling it from notification bar */
            Call call = mPrimaryCallTracker.getPrimaryCall();
            if (call == null) {
                Log.w(this, "onDestroyInCallActivity call is null");
                return;
            }
            mLowBatteryMap.replace(call, PROCESS_LOW_BATTERY);
        }
    }

    /**
     * This API conveys if incall experience is showing or not.
     *
     * @param showing TRUE if incall experience is showing else FALSE
     */
    @Override
    public void onUiShowing(boolean showing) {
        Call call = mPrimaryCallTracker.getPrimaryCall();
        Log.d(this, "onUiShowing showing: " + showing + " call = " + call);
        if (call == null || !showing) {
            return;
        }

        maybeProcessLowBatteryIndication(call);
    }

    /**
     * When call is answered, this API checks to see if UE is under low battery or not
     * and accordingly processes the low battery video call and returns TRUE if
     * user action to answer the call is handled by this API else FALSE.
     *
     * @param call The call that is being answered
     * @param videoState The videoState type with which user answered the MT call
     */
    public boolean onAnswerIncomingCall(Call call, int videoState) {
        Log.d(this, "onAnswerIncomingCall = " + call + " videoState = " + videoState);
        if (call == null || !mPrimaryCallTracker.isPrimaryCall(call)) {
            return false;
        }

        if(!(isLowBatteryVideoCall(call) && VideoProfile.isBidirectional(videoState))) {
            /* As user didnt accept the call as Video, low battery
               processing for that call isn't required any more */
            return false;
        }

        if (!mLowBatteryMap.containsKey(call)) {
            Log.w(this, "onAnswerIncomingCall no call in low battery map");
            return false;
        }

        /* There can be multiple user attempts to answer the call as Video from
           HUN (HeadsUp Notification) by pulling the call from notification bar.
           In such cases, avoid multiple processing of low battery indication */
        if (!isLowBatteryDialogShowing()) {
           /* There is user action to answer the call as Video. Update the low battery map
              to indicate that low battery indication for this call can be processed now */
            mLowBatteryMap.replace(call, PROCESS_LOW_BATTERY);
            maybeProcessLowBatteryIndication(call);
        }
        return true;
    }

    private boolean isLowBattery(android.telecom.Call.Details details) {
        final Bundle extras =  (details != null) ? details.getExtras() : null;
        final boolean isLowBattery = (extras != null) ? extras.getBoolean(
                QtiCallConstants.LOW_BATTERY_EXTRA_KEY, false) : false;
        Log.d(this, "isLowBattery : " + isLowBattery);
        return isLowBattery;
    }

    private boolean isActiveUnPausedVideoCall(Call call) {
        return VideoUtils.isActiveVideoCall(call) && !VideoProfile.isPaused(call.getVideoState());
    }

    private boolean isLowBatteryVideoCall(Call call) {
        return call != null && VideoUtils.isVideoCall(call) &&
                isLowBattery(call.getTelecomCall().getDetails());
    }

    // Return TRUE if low battery indication for the call can be processed else return FALSE
    private boolean canProcessLowBatteryIndication(Call call) {
        /* Low Battery indication can be processed if:
           1. Value stored against the call in low battery map is true
           2. InCallActivity is created */
        if (call == null || InCallPresenter.getInstance().getActivity() == null) {
            return false;
        }

        // we get null value if map contains no mapping for the key
        if (mLowBatteryMap.get(call) != null) {
            return mLowBatteryMap.get(call);
        }

        Log.w(this, "canProcessLowBatteryIndication no mapping for call in low battery map");
        return false;
    }

    private void maybeAddToLowBatteryMap(Call call) {
        if (call == null) {
            return;
        }

        if(isLowBatteryVideoCall(call)) {
           /* For MT Video calls, do not mark right away that Low battery indication
              can to be processed since the handling kicks-in only after user decides
              to answer the call as Video handled via onAnswerIncomingCall API*/
            mLowBatteryMap.putIfAbsent(call, !VideoUtils.isIncomingVideoCall(call));
        }
    }

    /**
     * Handles changes to the details of the call.
     *
     * @param call The call for which the details changed.
     * @param details The new call details.
     */
    @Override
    public void onDetailsChanged(Call call, android.telecom.Call.Details details) {
        Log.i(this, "onDetailsChanged call = " + call + " details = " + details);

        if (mPrimaryCallTracker.getPrimaryCall() == null) {
           /* primarycall is null may signal the possibility that there is only a single call and
              is getting disconnected. So, try to dismiss low battery alert dialogue (if any). This
              is to handle unintentional dismiss for add VT call use-cases wherein low battery alert
              dialog is waiting for user input and the held call is remotely disconnected */
            Log.i(this,"onDetailsChanged: no primary call.Clear the map/dismiss low battery alert");
            mLowBatteryMap.remove(call);
            dismissPendingDialogs();
            return;
        }

        if (call == null || !mPrimaryCallTracker.isPrimaryCall(call)) {
            Log.d(this," onDetailsChanged: call is null/Details not for primary call");
            return;
        }

        /* Low Battery handling for MT Video call kicks in only when user decides
           to answer the call as Video call so ignore the incoming video call
           processing here for now */
        if (VideoUtils.isIncomingVideoCall(call)) {
            return;
        }

        if (!VideoUtils.isVideoCall(call)) {
            /* There can be chances that Video call gets downgraded when
               low battery alert dialog is waiting for user confirmation.
               Handle this by dismissing the dialog (if any) */
            dismissPendingDialogs();
            return;
        }

        //process MO/Active Video call low battery indication
        maybeAddToLowBatteryMap(call);
        maybeProcessLowBatteryIndication(call);
    }

    /**
      * disconnects MO video call that is waiting for user confirmation on
      * low battery dialog
      * @param call The probable call that may need to be disconnected
      **/
    private void maybeDisconnectMoCall(Call call) {
        if (call == null || !isLowBatteryVideoCall(call)) {
            return;
        }

        if (call.getState() == Call.State.DIALING) {
            // dismiss the low battery dialog that is waiting for user input
            dismissPendingDialogs();

            Log.d(this, "disconnect MO call this is waiting for user input");
            TelecomAdapter.getInstance().disconnectCall(call.getId());
        }
    }

    private void maybeProcessLowBatteryIndication(Call call) {
        if (!isLowBatteryVideoCall(call)) {
            return;
        }

        if (canProcessLowBatteryIndication(call)) {
            displayLowBatteryAlert(call);
            //mark against the call that the respective low battery indication is processed
            mLowBatteryMap.replace(call, !PROCESS_LOW_BATTERY);
        }
    }

    /*
     * This method displays one of below alert dialogs when UE is in low battery
     * For Active Video Calls:
     *     1. hangup alert dialog in absence of voice capabilities
     *     2. downgrade to voice call alert dialog in the presence of voice
     *        capabilities
     * For MT Video calls wherein user decided to accept the call as Video and for MO Video Calls:
     *     1. alert dialog asking user confirmation to convert the video call to voice call or
     *        to continue the call as video call
     * For MO Video calls, seek user confirmation to continue the video call as is or convert the
     * video call to voice call
     */
    private void displayLowBatteryAlert(final Call call) {
        //if low battery dialog is already visible to user, dismiss it
        dismissPendingDialogs();

        final InCallActivity inCallActivity = InCallPresenter.getInstance().getActivity();
        if (inCallActivity == null) {
            Log.w(this, "displayLowBatteryAlert inCallActivity is NULL");
            return;
        }

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(inCallActivity);
        alertDialog.setTitle(R.string.low_battery);
        alertDialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(final DialogInterface dialog) {
            }
        });

        if (VideoUtils.isIncomingVideoCall(call)) {
            alertDialog.setNegativeButton(R.string.low_battery_convert, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                     Log.d(this, "displayLowBatteryAlert answer as Voice Call");
                     TelecomAdapter.getInstance().answerCall(call.getId(),
                             VideoProfile.STATE_AUDIO_ONLY);
                }
            });

            alertDialog.setMessage(R.string.low_battery_msg);
            alertDialog.setPositiveButton(android.R.string.yes, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                     Log.d(this, "displayLowBatteryAlert answer as Video Call");
                     TelecomAdapter.getInstance().answerCall(call.getId(),
                             VideoProfile.STATE_BIDIRECTIONAL);
                }
            });
        } else if (VideoUtils.isOutgoingVideoCall(call)) {
            alertDialog.setNegativeButton(R.string.low_battery_convert, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                     Log.d(this, "displayLowBatteryAlert place Voice Call");
                     //Change the audio route to earpiece
                     InCallAudioManager.getInstance().onModifyCallClicked(call,
                             VideoProfile.STATE_AUDIO_ONLY);
                     try {
                         QtiImsExtManager.getInstance().resumePendingCall(
                                 VideoProfile.STATE_AUDIO_ONLY);
                     } catch (QtiImsException e) {
                         Log.e(this, "resumePendingCall exception " + e);
                     }
                }
            });

            alertDialog.setMessage(R.string.low_battery_msg);
            alertDialog.setPositiveButton(android.R.string.yes, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                     Log.d(this, "displayLowBatteryAlert place Video Call");
                     try {
                         QtiImsExtManager.getInstance().resumePendingCall(
                                 VideoProfile.STATE_BIDIRECTIONAL);
                     } catch (QtiImsException e) {
                         Log.e(this, "resumePendingCall exception " + e);
                     }
                }
            });
        } else if (isActiveUnPausedVideoCall(call)) {
            if (QtiCallUtils.hasVoiceCapabilities(call)) {
                //active video call can be downgraded to voice
                alertDialog.setMessage(R.string.low_battery_msg);
                alertDialog.setPositiveButton(android.R.string.yes, null);
                alertDialog.setNegativeButton(R.string.low_battery_convert, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.d(this, "displayLowBatteryAlert downgrading to voice call");
                        QtiCallUtils.downgradeToVoiceCall(call);
                    }
                });
            } else {
                /* video call doesn't have downgrade capabilities, so alert the user
                   with a hangup dialog*/
                alertDialog.setMessage(R.string.low_battery_hangup_msg);
                alertDialog.setNegativeButton(android.R.string.no, null);
                alertDialog.setPositiveButton(android.R.string.yes, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.d(this, "displayLowBatteryAlert hanging up the call: " + call);
                        call.setState(Call.State.DISCONNECTING);
                        CallList.getInstance().onUpdate(call);
                        TelecomAdapter.getInstance().disconnectCall(call.getId());
                    }
                });
            }
        }

        mAlert = alertDialog.create();
        mAlert.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                Log.d(this, "on Alert displayLowBattery keyCode = " + keyCode);
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    // On Back key press, disconnect MO low battery video call
                    // that is waiting for user input
                    maybeDisconnectMoCall(call);
                    return true;
                }
                return false;
            }
        });
        mAlert.setCanceledOnTouchOutside(false);
        mAlert.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        mAlert.show();
    }

    /*
     * This method returns true if dialog is showing else false
     */
    private boolean isLowBatteryDialogShowing() {
        return mAlert != null && mAlert.isShowing();
    }

    /*
     * This method dismisses the low battery dialog and
     * returns true if dialog is dimissed else false
     */
    public boolean dismissPendingDialogs() {
        if (isLowBatteryDialogShowing()) {
            mAlert.dismiss();
            mAlert = null;
            return true;
        }
        return false;
    }
}
