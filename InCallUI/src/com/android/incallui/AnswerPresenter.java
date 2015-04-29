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

import com.android.incallui.InCallPresenter.InCallState;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;

import java.util.List;

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
                CallList.Listener {

    private static final String TAG = AnswerPresenter.class.getSimpleName();

    private String mCallId;
    private Call mCall = null;
    private boolean mHasTextMessages = false;

    @Override
    public void onUiShowing(boolean showing) {
        if (showing) {
            final CallList calls = CallList.getInstance();
            Call call;
            call = calls.getIncomingCall();
            if (call != null) {
                processIncomingCall(call);
            }
            call = calls.getVideoUpgradeRequestCall();
            Log.d(this, "getVideoUpgradeRequestCall call =" + call);
            if (call != null) {
                processVideoUpgradeRequestCall(call);
            }
        } else {
            // This is necessary because the activity can be destroyed while an incoming call exists.
            // This happens when back button is pressed while incoming call is still being shown.
            if (mCallId != null) {
                CallList.getInstance().removeCallUpdateListener(mCallId, this);
            }
        }
    }

    @Override
    public void onIncomingCall(InCallState oldState, InCallState newState, Call call) {
        Log.d(this, "onIncomingCall: " + this);
        if (getUi() != null) {
            Call modifyCall = CallList.getInstance().getVideoUpgradeRequestCall();
            if (modifyCall != null) {
                showAnswerUi(false);
                Log.d(this, "declining upgrade request id: ");
                CallList.getInstance().removeCallUpdateListener(mCallId, this);
                InCallPresenter.getInstance().declineUpgradeRequest(getUi().getContext());
            }
            if (!call.getId().equals(mCallId)) {
                // A new call is coming in.
                processIncomingCall(call);
            }
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
    }

    private boolean isVideoUpgradePending(Call call) {
        return call.getSessionModificationState()
                == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST;
    }

    @Override
    public void onUpgradeToVideo(Call call) {
        Log.d(this, "onUpgradeToVideo: " + this + " call=" + call);
        if (getUi() == null) {
            Log.d(this, "onUpgradeToVideo ui is null");
            return;
        }
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
        mCallId = call.getId();
        mCall = call;

        // Listen for call updates for the current call.
        CallList.getInstance().addCallUpdateListener(mCallId, this);

        Log.d(TAG, "Showing incoming for call id: " + mCallId + " " + this);
        if (showAnswerUi(true)) {
            final List<String> textMsgs = CallList.getInstance().getTextResponses(call.getId());
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
        mCallId = call.getId();
        mCall = call;

        // Listen for call updates for the current call.
        CallList.getInstance().addCallUpdateListener(mCallId, this);

        final int currentVideoState = call.getVideoState();
        final int modifyToVideoState = call.getModifyToVideoState();

        if (currentVideoState == modifyToVideoState) {
            Log.w(this, "processVideoUpgradeRequestCall: Video states are same. Return.");
            return;
        }

        showAnswerUi(true);
        getUi().showTargets(getUiTarget(currentVideoState, modifyToVideoState));

    }

    private int getUiTarget(int currentVideoState, int modifyToVideoState) {
        if (showVideoUpgradeOptions(currentVideoState, modifyToVideoState)) {
            return AnswerFragment.TARGET_SET_FOR_VIDEO_UPGRADE_REQUEST;
        } else if (isEnabled(modifyToVideoState, VideoProfile.VideoState.BIDIRECTIONAL)) {
            return AnswerFragment.TARGET_SET_FOR_BIDIRECTIONAL_VIDEO_ACCEPT_REJECT_REQUEST;
        }  else if (isEnabled(modifyToVideoState, VideoProfile.VideoState.TX_ENABLED)) {
            return AnswerFragment.TARGET_SET_FOR_VIDEO_TRANSMIT_ACCEPT_REJECT_REQUEST;
        }  else if (isEnabled(modifyToVideoState, VideoProfile.VideoState.RX_ENABLED)) {
            return AnswerFragment.TARGET_SET_FOR_VIDEO_RECEIVE_ACCEPT_REJECT_REQUEST;
        }
        return AnswerFragment.TARGET_SET_FOR_VIDEO_UPGRADE_REQUEST;
    }

    private boolean showVideoUpgradeOptions(int currentVideoState, int modifyToVideoState) {
        return currentVideoState == VideoProfile.VideoState.AUDIO_ONLY &&
            isEnabled(modifyToVideoState, VideoProfile.VideoState.BIDIRECTIONAL);
    }

    private boolean isEnabled(int videoState, int mask) {
        return (videoState & mask) == mask;
    }

    @Override
    public void onCallChanged(Call call) {
        Log.d(this, "onCallStateChange() " + call + " " + this);
        if (call.getState() != Call.State.INCOMING) {
            boolean isUpgradePending = isVideoUpgradePending(call);
            if (!isUpgradePending) {
                // Stop listening for updates.
                CallList.getInstance().removeCallUpdateListener(mCallId, this);
            }

            final Call incall = CallList.getInstance().getIncomingCall();
            if (incall != null || isUpgradePending) {
                showAnswerUi(true);
            } else {
                showAnswerUi(false);
            }

            mHasTextMessages = false;
        } else if (!mHasTextMessages) {
            final List<String> textMsgs = CallList.getInstance().getTextResponses(call.getId());
            if (textMsgs != null) {
                configureAnswerTargetsForSms(call, textMsgs);
            }
        }
    }

    public void onAnswer(int videoState, Context context) {
        Log.d(this, "onAnswer mCallId=" + mCallId + " videoState=" + videoState);
        if (mCallId == null) {
            return;
        }

        if (mCall.getSessionModificationState()
                == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
            InCallPresenter.getInstance().acceptUpgradeRequest(videoState, context);
        } else {
            TelecomAdapter.getInstance().answerCall(mCall.getId(), videoState);
        }
    }

    /**
     * TODO: We are using reject and decline interchangeably. We should settle on
     * reject since it seems to be more prevalent.
     */
    public void onDecline(Context context) {
        Log.d(this, "onDecline " + mCallId);
        if (mCall.getSessionModificationState()
                == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
            InCallPresenter.getInstance().declineUpgradeRequest(context);
        } else {
            TelecomAdapter.getInstance().rejectCall(mCall.getId(), false, null);
        }
    }

    public void onText() {
        if (getUi() != null) {
            InCallPresenter.getInstance().getTelecomManager().silenceRinger();
            getUi().showMessageDialog();
        }
    }

    public void rejectCallWithMessage(String message) {
        Log.d(this, "sendTextToDefaultActivity()...");
        TelecomAdapter.getInstance().rejectCall(mCall.getId(), true, message);

        onDismissDialog();
    }

    public void onDismissDialog() {
        InCallPresenter.getInstance().onDismissDialog();
    }

    private void configureAnswerTargetsForSms(Call call, List<String> textMsgs) {
        if (getUi() == null) {
            return;
        }

        final Context context = getUi().getContext();

        mHasTextMessages = textMsgs != null;
        boolean withSms =
                call.can(android.telecom.Call.Details.CAPABILITY_RESPOND_VIA_TEXT)
                && mHasTextMessages;
        if (call.isVideoCall(context)) {
            if (withSms) {
                getUi().showTargets(AnswerFragment.TARGET_SET_FOR_VIDEO_WITH_SMS);
                getUi().configureMessageDialog(textMsgs);
            } else {
                getUi().showTargets(AnswerFragment.TARGET_SET_FOR_VIDEO_WITHOUT_SMS);
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

    interface AnswerUi extends Ui {
        public void onShowAnswerUi(boolean shown);
        public void showTargets(int targetSet);
        public void showMessageDialog();
        public void configureMessageDialog(List<String> textResponses);
        public Context getContext();
    }
}
