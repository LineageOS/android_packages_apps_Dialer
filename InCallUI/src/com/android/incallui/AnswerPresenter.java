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

import android.content.Context;
import android.provider.Settings;
import android.telecom.VideoProfile;
import android.telephony.SubscriptionManager;

import com.android.dialer.compat.UserManagerCompat;
import com.android.dialer.util.TelecomUtil;
import com.android.incallui.InCallPresenter.InCallState;

import java.util.List;

import org.codeaurora.ims.internal.IQtiImsExt;
import org.codeaurora.ims.QtiImsException;
import org.codeaurora.ims.QtiImsExtListenerBaseImpl;
import org.codeaurora.ims.QtiImsExtManager;
import org.codeaurora.ims.utils.QtiImsExtUtils;

/**
 * Presenter for the Incoming call widget. The {@link AnswerPresenter} handles the logic during
 * incoming calls. It is also in charge of responding to incoming calls, so there needs to be
 * an instance alive so that it can receive onIncomingCall callbacks.
 *
 * An instance of {@link AnswerPresenter} is created by InCallPresenter at startup, registers
 * for callbacks via InCallPresenter, and shows/hides the {@link AnswerFragment} via IncallActivity.
 *
 */
public class AnswerPresenter extends Presenter<AnswerPresenter.AnswerUi>
        implements CallList.CallUpdateListener, InCallPresenter.InCallUiListener,
                InCallPresenter.IncomingCallListener,
                CallList.Listener, CallList.ActiveSubChangeListener {

    private static final String TAG = AnswerPresenter.class.getSimpleName();

    private String mCallId[] = new String[InCallServiceImpl.sPhoneCount];
    private Call mCall[] = new Call[InCallServiceImpl.sPhoneCount];
    private final CallList mCalls = CallList.getInstance();
    private boolean mHasTextMessages = false;
    // Currently mVideoState is beeing used only for incoming calls.
    // As there is only one incoming call allowed there is no need of array here.
    private int mVideoState = VideoProfile.STATE_AUDIO_ONLY;

    /* QtiImsExtListenerBaseImpl instance to handle call deflection response */
    private QtiImsExtListenerBaseImpl imsInterfaceListener =
            new QtiImsExtListenerBaseImpl() {

        /* Handles call deflect response */
        @Override
        public void receiveCallDeflectResponse(int result) {
            Log.w(this, "receiveCallDeflectResponse: " + result);
        }
    };
    private static final int INVALID_PHONEID = -1;

    @Override
    public void onUiShowing(boolean showing) {
        if (showing) {
            mCalls.addListener(this);
            mCalls.addActiveSubChangeListener(this);
            Call call;
            // Consider incoming/waiting calls on both subscriptions
            // for DSDA.
            for (int i = 0; i < InCallServiceImpl.sPhoneCount; i++) {
                int subId = QtiCallUtils.getSubId(i);
                if (checkSubId(i)) {
                    call = mCalls.getCallWithState(Call.State.INCOMING, 0, subId);
                    if (call == null) {
                        call = mCalls.getCallWithState(Call.State.CALL_WAITING, 0, subId);
                    }
                    if (call != null) {
                        processIncomingCall(call);
                    }
                } else {
                    Log.d(TAG, "No valid sub");
                }
            }
            call = mCalls.getVideoUpgradeRequestCall();
            Log.d(this, "getVideoUpgradeRequestCall call =" + call);
            if (call != null) {
                showAnswerUi(true);
                processVideoUpgradeRequestCall(call);
            }
        } else {
            mCalls.removeListener(this);
            // This is necessary because the activity can be destroyed while an incoming call exists.
            // This happens when back button is pressed while incoming call is still being shown.
            for (int i = 0; i < InCallServiceImpl.sPhoneCount; i++) {
                int subId = QtiCallUtils.getSubId(i);
                if (checkSubId(i)) {
                    Call call = mCalls.getCallWithState(Call.State.INCOMING, 0, subId);
                    if (call == null) {
                        call = mCalls.getCallWithState(Call.State.CALL_WAITING, 0, subId);
                    }
                    if (call == null) {
                        call = mCalls.getCallWithState(Call.State.ACTIVE, 0, subId);
                    }
                    if (mCallId[i] != null && call == null) {
                        mCalls.removeCallUpdateListener(mCallId[i], this);
                        mCalls.removeActiveSubChangeListener(this);
                    }
                } else {
                    Log.d(TAG, "No valid sub");
                }
            }
        }
    }

    private boolean checkSubId(int phoneId) {
        int subId = QtiCallUtils.getSubId(phoneId);
        return (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);
    }

    @Override
    public void onIncomingCall(InCallState oldState, InCallState newState, Call call) {
        int subId = call.getSubId();
        int phoneId = QtiCallUtils.getPhoneId(subId);
        Log.d(this, "onIncomingCall: " + this);
        Call modifyCall = mCalls.getVideoUpgradeRequestCall();
        if (modifyCall != null) {
            showAnswerUi(false);
            Log.d(this, "declining upgrade request id: ");
            mCalls.removeCallUpdateListener(mCallId[phoneId], this);
            InCallPresenter.getInstance().declineUpgradeRequest();
        }
        if (!call.getId().equals(mCallId[phoneId])) {
            // A new call is coming in.
            processIncomingCall(call);
        }
    }

    @Override
    public void onIncomingCall(Call call) {
    }

    @Override
    public void onCallListChange(CallList list) {
    }

    @Override
    public void onDisconnect(Call call) {
        // no-op
        int subId = call.getSubId();
        int phoneId = QtiCallUtils.getPhoneId(subId);
        if (call.equals(mCall[phoneId])) {
            mCall[phoneId] = null;
        }
    }

    public void onSessionModificationStateChange(Call call, int sessionModificationState) {
        boolean isUpgradePending = sessionModificationState ==
                Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST;

        if (!isUpgradePending) {
            // Stop listening for updates.
            for (int i = 0; i < InCallServiceImpl.sPhoneCount; i++) {
                if (mCallId[i] != null) {
                    mCalls.removeCallUpdateListener(mCallId[i], this);
                }
            }
            showAnswerUi(false);
        }
    }

    @Override
    public void onLastForwardedNumberChange() {
        // no-op
    }

    @Override
    public void onChildNumberChange() {
        // no-op
    }

    private boolean isVideoUpgradePending(Call call) {
        return call.getSessionModificationState()
                == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST;
    }

    @Override
    public void onUpgradeToVideo(Call call) {
        Log.d(this, "onUpgradeToVideo: " + this + " call=" + call);
        showAnswerUi(true);
        boolean isUpgradePending = isVideoUpgradePending(call);
        InCallPresenter inCallPresenter = InCallPresenter.getInstance();
        if (isUpgradePending
                && inCallPresenter.getInCallState() == InCallPresenter.InCallState.INCOMING) {
            Log.d(this, "declining upgrade request");
            //If there is incoming call reject upgrade request
            inCallPresenter.declineUpgradeRequest(getUi().getContext());
        } else if (isUpgradePending) {
            Log.d(this, "process upgrade request as no MT call");
            processVideoUpgradeRequestCall(call);
        }
    }

    private void processIncomingCall(Call call) {
        int subId = call.getSubId();
        int phoneId = QtiCallUtils.getPhoneId(subId);
        mCallId[phoneId] = call.getId();
        mCall[phoneId] = call;

        mCalls.addListener(this);
        // Listen for call updates for the current call.
        mCalls.addCallUpdateListener(mCallId[phoneId], this);

        Log.d(TAG, "Showing incoming for call id: " + mCallId[phoneId] + " " + this);
        if (showAnswerUi(true)) {
            mVideoState = call.getVideoState();
            final List<String> textMsgs = mCalls.getTextResponses(call.getId());
            configureAnswerTargetsForSms(call, textMsgs);
        }
    }

    private boolean showAnswerUi(boolean show) {
        final InCallActivity activity = InCallPresenter.getInstance().getActivity();
        if (activity != null) {
            activity.showAnswerFragment(show);
            if (getUi() != null) {
                getUi().onShowAnswerUi(show);
            }
            return true;
        } else {
            return false;
        }
    }

    private void processVideoUpgradeRequestCall(Call call) {
        Log.d(this, " processVideoUpgradeRequestCall call=" + call);
        int subId = call.getSubId();
        int phoneId = QtiCallUtils.getPhoneId(subId);
        mCallId[phoneId] = call.getId();
        mCall[phoneId] = call;

        // Listen for call updates for the current call.
        CallList.getInstance().addCallUpdateListener(mCallId[phoneId], this);

        final int currentVideoState = call.getVideoState();
        final int modifyToVideoState = call.getRequestedVideoState();

        if (currentVideoState == modifyToVideoState) {
            Log.w(this, "processVideoUpgradeRequestCall: Video states are same. Return.");
            return;
        }

        AnswerUi ui = getUi();

        if (ui == null) {
            Log.e(this, "Ui is null. Can't process upgrade request");
            return;
        }
        showAnswerUi(true);
        ui.showTargets(QtiCallUtils.getSessionModificationOptions(getUi().getContext(),
                currentVideoState, modifyToVideoState));

    }

    private boolean isEnabled(int videoState, int mask) {
        return (videoState & mask) == mask;
    }

    @Override
    public void onCallChanged(Call call) {
        Log.d(this, "onCallStateChange() " + call + " " + this);
        if (call.getState() != Call.State.INCOMING) {
            boolean isUpgradePending = isVideoUpgradePending(call);
            int subId = call.getSubId();
            int phoneId = QtiCallUtils.getPhoneId(subId);
            if (!isUpgradePending) {
                // Stop listening for updates.
                mCalls.removeCallUpdateListener(mCallId[phoneId], this);
            }

            final Call incall = mCalls.getIncomingCall();
            if (incall != null || isUpgradePending) {
                showAnswerUi(true);
            } else {
                showAnswerUi(false);
            }

            mHasTextMessages = false;
        } else if (!mHasTextMessages || (mVideoState != call.getVideoState())) {
            final List<String> textMsgs = mCalls.getTextResponses(call.getId());
            mVideoState = call.getVideoState();
            configureAnswerTargetsForSms(call, textMsgs);
        }
    }

    // get active phoneId, for which call is visible to user
    private int getActivePhoneId() {
        int phoneId = INVALID_PHONEID;
        if (mCalls.isDsdaEnabled()) {
            int subId = mCalls.getActiveSubId();
            phoneId = QtiCallUtils.getPhoneId(subId);
        } else {
            for (int i = 0; i < mCall.length; i++) {
                if (mCall[i] != null) {
                    phoneId = i;
                }
            }
        }
        return phoneId;
    }

    public void onAnswer(int videoState, Context context) {
        int phoneId = getActivePhoneId();
        Log.i(this, "onAnswer  mCallId:" + mCallId + "phoneId:" + phoneId);
        if (mCallId == null || phoneId == INVALID_PHONEID) {
            return;
        }

        if (mCall[phoneId].getSessionModificationState()
                == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
            Log.d(this, "onAnswer (upgradeCall) mCallId=" + mCallId + " videoState=" + videoState);
            InCallPresenter.getInstance().acceptUpgradeRequest(videoState, context);
        } else {
            Log.d(this, "onAnswer (answerCall) mCallId=" + mCallId + " videoState=" + videoState);
            InCallPresenter.getInstance().answerIncomingCall(context, videoState);
        }
    }

    /**
     * TODO: We are using reject and decline interchangeably. We should settle on
     * reject since it seems to be more prevalent.
     */
    public void onDecline(Context context) {
        int phoneId = getActivePhoneId();
        Log.d(this, "onDecline mCallId:" + mCallId + "phoneId:" + phoneId);
        if (mCall[phoneId].getSessionModificationState()
                == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
            InCallPresenter.getInstance().declineUpgradeRequest(context);
        } else {
            TelecomAdapter.getInstance().rejectCall(mCall[phoneId].getId(), false, null);
        }
    }

    public void onText() {
        if (getUi() != null) {
            TelecomUtil.silenceRinger(getUi().getContext());
            getUi().showMessageDialog();
        }
    }

    /**
     * Deflect the incoming call.
     */
    public void onDeflect(Context context) {
        if (mCallId == null) {
            return;
        }
        Log.d(this, "onDeflect " + mCallId);

        String deflectCallNumber = QtiImsExtUtils.getCallDeflectNumber(
                                           context.getContentResolver());
        /* If not set properly, inform user via toast */
        if (deflectCallNumber == null) {
            Log.w(this, "getCallDeflectNumber is null or Empty.");
            QtiCallUtils.displayToast(context, R.string.qti_description_deflect_error);
        } else {
            int phoneId = 0;
            try {
                Log.d(this, "Sending deflect request with Phone id " + phoneId +
                        " to " + deflectCallNumber);
                QtiImsExtManager.getInstance().sendCallDeflectRequest(phoneId,
                        deflectCallNumber, imsInterfaceListener);
             } catch (QtiImsException e) {
                 Log.e(this, "sendCallDeflectRequest exception " + e);
                 QtiCallUtils.displayToast(getUi().getContext(),
                         R.string.qti_description_deflect_service_error);
             }
        }
    }

    public void rejectCallWithMessage(String message) {
        int phoneId = getActivePhoneId();
        Log.i(this, "sendTextToDefaultActivity()...phoneId:" + phoneId);
        if (phoneId != INVALID_PHONEID) {
            TelecomAdapter.getInstance().rejectCall(mCall[phoneId].getId(), true, message);
        }

        onDismissDialog();
    }

    public void onDismissDialog() {
        InCallPresenter.getInstance().onDismissDialog();
    }

    private void configureAnswerTargetsForSms(Call call, List<String> textMsgs) {
        if (getUi() == null) {
            return;
        }
        mHasTextMessages = textMsgs != null;
        boolean withSms = UserManagerCompat.isUserUnlocked(getUi().getContext())
                && call.can(android.telecom.Call.Details.CAPABILITY_RESPOND_VIA_TEXT)
                && mHasTextMessages;

        // Only present the user with the option to answer as a video call if the incoming call is
        // a bi-directional video call.
        if (VideoUtils.isVideoCall(call)) {
            if (withSms) {
                getUi().showTargets(QtiCallUtils.getIncomingCallAnswerOptions(
                        getUi().getContext(), call.getVideoState(), withSms));
                getUi().configureMessageDialog(textMsgs);
            } else {
                getUi().showTargets(QtiCallUtils.getIncomingCallAnswerOptions(
                        getUi().getContext(), call.getVideoState(), withSms));
            }
        } else if (isCallDeflectSupported()) {
            /**
             * Only present the user with the option to deflect call,
             * if the incoming call is only an audio call.
             */
            if (withSms) {
                getUi().showTargets(AnswerFragment.TARGET_SET_FOR_QTI_AUDIO_WITH_SMS);
                getUi().configureMessageDialog(textMsgs);
            } else {
                getUi().showTargets(AnswerFragment.TARGET_SET_FOR_QTI_AUDIO_WITHOUT_SMS);
            }
        } else {
            if (withSms) {
                getUi().showTargets(AnswerFragment.TARGET_SET_FOR_AUDIO_WITH_SMS);
                getUi().configureMessageDialog(textMsgs);
            } else {
                getUi().showTargets(AnswerFragment.TARGET_SET_FOR_AUDIO_WITHOUT_SMS);
            }
        }
    }

    /**
     * Checks the Settings to conclude on the call deflect support.
     * Returns true if call deflect is possible, false otherwise.
     */
    private boolean isCallDeflectSupported() {
        int value = 0;
        try{
            value = android.provider.Settings.Global.getInt(
                    getUi().getContext().getContentResolver(),
                    QtiImsExtUtils.QTI_IMS_DEFLECT_ENABLED);
        } catch(Settings.SettingNotFoundException e) {
            //do Nothing
            Log.e(this, "isCallDeflectSupported exception " + e);
        }
        return (value == 1);
    }

    interface AnswerUi extends Ui {
        public void onShowAnswerUi(boolean shown);
        public void showTargets(int targetSet);
        public void showTargets(int targetSet, int videoState);
        public void showMessageDialog();
        public void configureMessageDialog(List<String> textResponses);
        public Context getContext();
    }

    @Override
    public void onActiveSubChanged(int subId) {
        final Call call = mCalls.getIncomingCall();
        int phoneId = QtiCallUtils.getPhoneId(subId);
        if ((call != null) && (call.getId() == mCallId[phoneId])) {
            Log.d(this, "Show incoming for call id: " + mCallId[phoneId] + " " + this);
            if (showAnswerUi(true)) {
                final List<String> textMsgs = mCalls.getTextResponses(
                        call.getId());
                configureAnswerTargetsForSms(call, textMsgs);
            }
        } else if ((call == null) && (mCalls.hasAnyLiveCall(subId))) {
            Log.d(this, "Hide incoming for call id: " + mCallId[phoneId] + " " + this);
            showAnswerUi(false);
        } else {
            Log.d(this, "No incoming call present for sub = " + subId + " " + this);
        }
    }
}
