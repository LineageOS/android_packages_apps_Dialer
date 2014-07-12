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
import android.graphics.drawable.Drawable;
import android.telecomm.CallCapabilities;

import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.contacts.common.util.TelephonyManagerUtils;
import com.android.incallui.AudioModeProvider.AudioModeListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.service.AuxiliaryActionService;
import com.android.incalluibind.ServiceFactory;
import com.android.services.telephony.common.AudioMode;

import android.app.Fragment;
import android.telephony.PhoneNumberUtils;

/**
 * Logic for call buttons.
 */
public class CallButtonPresenter extends Presenter<CallButtonPresenter.CallButtonUi>
        implements InCallStateListener, AudioModeListener, IncomingCallListener,
        AuxiliaryActionService.Client {

    private Call mCall;
    private boolean mAutomaticallyMuted = false;
    private boolean mPreviousMuteState = false;

    private boolean mShowGenericMerge = false;
    private boolean mShowManageConference = false;

    private InCallState mPreviousState = null;

    private AuxiliaryActionService mAuxiliaryActionService = null;

    public CallButtonPresenter() {
    }

    @Override
    public void onUiReady(CallButtonUi ui) {
        super.onUiReady(ui);

        AudioModeProvider.getInstance().addListener(this);

        // register for call state changes last
        InCallPresenter.getInstance().addListener(this);
        InCallPresenter.getInstance().addIncomingCallListener(this);

        Context context = ((Fragment) ui).getActivity();
        mAuxiliaryActionService = ServiceFactory.newAuxiliaryActionService(context);
        if (mAuxiliaryActionService != null) {
            mAuxiliaryActionService.setClient(this);
        }
    }

    @Override
    public void onUiUnready(CallButtonUi ui) {
        super.onUiUnready(ui);

        InCallPresenter.getInstance().removeListener(this);
        AudioModeProvider.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);
        if (mAuxiliaryActionService != null) {
            mAuxiliaryActionService.setClient(null);
        }
    }

    @Override
    public void onStateChange(InCallState state, CallList callList) {
        CallButtonUi ui = getUi();

        if (state == InCallState.OUTGOING) {
            mCall = callList.getOutgoingCall();
        } else if (state == InCallState.INCALL) {
            mCall = callList.getActiveOrBackgroundCall();

            // When connected to voice mail, automatically shows the dialpad.
            // (On previous releases we showed it when in-call shows up, before waiting for
            // OUTGOING.  We may want to do that once we start showing "Voice mail" label on
            // the dialpad too.)
            if (ui != null) {
                final Fragment callButtonFragment = (Fragment) ui;
                if (mPreviousState == InCallState.OUTGOING && mCall != null
                        && PhoneNumberUtils.isVoiceMailNumber(mCall.getNumber())) {
                    ui.displayDialpad(true /* show */, true /* animate */);
                }
            }
        } else if (state == InCallState.INCOMING) {
            if (ui != null) {
                ui.displayDialpad(false /* show */, true /* animate */);
            }
            mCall = null;
        } else {
            mCall = null;
        }
        updateUi(state, mCall);

        mPreviousState = state;
    }

    @Override
    public void onIncomingCall(InCallState state, Call call) {
        onStateChange(state, CallList.getInstance());
    }

    @Override
    public void onAudioMode(int mode) {
        if (getUi() != null) {
            getUi().setAudio(mode);
        }
    }

    @Override
    public void onSupportedAudioMode(int mask) {
        if (getUi() != null) {
            getUi().setSupportedAudio(mask);
        }
    }

    @Override
    public void onMute(boolean muted) {
        if (getUi() != null && !mAutomaticallyMuted) {
            getUi().setMute(muted);
        }
    }

    @Override
    public void onAuxiliaryActionStateChanged() {
        updateAuxiliaryActionButton();
    }

    public int getAudioMode() {
        return AudioModeProvider.getInstance().getAudioMode();
    }

    public int getSupportedAudio() {
        return AudioModeProvider.getInstance().getSupportedModes();
    }

    public void setAudioMode(int mode) {

        // TODO: Set a intermediate state in this presenter until we get
        // an update for onAudioMode().  This will make UI response immediate
        // if it turns out to be slow

        Log.d(this, "Sending new Audio Mode: " + AudioMode.toString(mode));
        TelecommAdapter.getInstance().setAudioRoute(mode);
    }

    /**
     * Function assumes that bluetooth is not supported.
     */
    public void toggleSpeakerphone() {
        // this function should not be called if bluetooth is available
        if (0 != (AudioMode.BLUETOOTH & getSupportedAudio())) {

            // It's clear the UI is wrong, so update the supported mode once again.
            Log.e(this, "toggling speakerphone not allowed when bluetooth supported.");
            getUi().setSupportedAudio(getSupportedAudio());
            return;
        }

        int newMode = AudioMode.SPEAKER;

        // if speakerphone is already on, change to wired/earpiece
        if (getAudioMode() == AudioMode.SPEAKER) {
            newMode = AudioMode.WIRED_OR_EARPIECE;
        }

        setAudioMode(newMode);
    }

    public void manageConferenceButtonClicked() {
        getUi().displayManageConferencePanel(true);
    }

    public void muteClicked(boolean checked) {
        Log.d(this, "turning on mute: " + checked);
        TelecommAdapter.getInstance().mute(checked);
    }

    public void holdClicked(boolean checked) {
        if (mCall == null) {
            return;
        }
        if (checked) {
            Log.i(this, "Putting the call on hold: " + mCall);
            TelecommAdapter.getInstance().holdCall(mCall.getCallId());
        } else {
            Log.i(this, "Removing the call from hold: " + mCall);
            TelecommAdapter.getInstance().unholdCall(mCall.getCallId());
        }
    }

    public void mergeClicked() {
        TelecommAdapter.getInstance().merge(mCall.getCallId());
    }

    public void addCallClicked() {
        // Automatically mute the current call
        mAutomaticallyMuted = true;
        mPreviousMuteState = AudioModeProvider.getInstance().getMute();
        // Simulate a click on the mute button
        muteClicked(true);

        TelecommAdapter.getInstance().addCall();
    }

    public void swapClicked() {
        TelecommAdapter.getInstance().swap(mCall.getCallId());
    }

    public void showDialpadClicked(boolean checked) {
        Log.v(this, "Show dialpad " + String.valueOf(checked));
        getUi().displayDialpad(checked /* show */, true /* animate */);
        updateExtraButtonRow();
    }

    public void auxiliaryActionButtonClicked() {
        mAuxiliaryActionService.performAction();
    }

    private void updateUi(InCallState state, Call call) {
        final CallButtonUi ui = getUi();
        if (ui == null) {
            return;
        }

        final boolean isEnabled = state.isConnectingOrConnected() &&
                !state.isIncoming() && call != null;

        ui.setEnabled(isEnabled);

        Log.d(this, "Updating call UI for call: ", call);

        if (isEnabled) {
            Log.v(this, "Show hold ", call.can(CallCapabilities.SUPPORT_HOLD));
            Log.v(this, "Enable hold", call.can(CallCapabilities.HOLD));
            Log.v(this, "Show merge ", call.can(CallCapabilities.MERGE_CALLS));
            Log.v(this, "Show swap ", call.can(CallCapabilities.SWAP_CALLS));
            Log.v(this, "Show add call ", call.can(CallCapabilities.ADD_CALL));
            Log.v(this, "Show mute ", call.can(CallCapabilities.MUTE));

            final boolean canMerge = call.can(CallCapabilities.MERGE_CALLS);
            final boolean canAdd = call.can(CallCapabilities.ADD_CALL);
            final boolean isGenericConference = call.can(CallCapabilities.GENERIC_CONFERENCE);


            final boolean showMerge = !isGenericConference && canMerge;

            if (showMerge) {
                ui.showMerge(true);
                ui.showAddCall(false);
            } else {
                ui.showMerge(false);
                ui.showAddCall(true);
                ui.enableAddCall(canAdd);
            }

            final boolean canHold = call.can(CallCapabilities.HOLD);
            final boolean canSwap = call.can(CallCapabilities.SWAP_CALLS);
            final boolean supportHold = call.can(CallCapabilities.SUPPORT_HOLD);

            if (canHold) {
                ui.showHold(true);
                ui.setHold(call.getState() == Call.State.ONHOLD);
                ui.enableHold(true);
                ui.showSwap(false);
            } else if (canSwap) {
                ui.showHold(false);
                ui.showSwap(true);
            } else {
                // Neither "Hold" nor "Swap" is available.  This can happen for two
                // reasons:
                //   (1) this is a transient state on a device that *can*
                //       normally hold or swap, or
                //   (2) this device just doesn't have the concept of hold/swap.
                //
                // In case (1), show the "Hold" button in a disabled state.  In case
                // (2), remove the button entirely.  (This means that the button row
                // will only have 4 buttons on some devices.)

                if (supportHold) {
                    ui.showHold(true);
                    ui.enableHold(false);
                    ui.setHold(call.getState() == Call.State.ONHOLD);
                    ui.showSwap(false);
                } else {
                    ui.showHold(false);
                    ui.showSwap(false);
                }
            }

            ui.enableMute(call.can(CallCapabilities.MUTE));

            // Finally, update the "extra button row": It's displayed above the
            // "End" button, but only if necessary.  Also, it's never displayed
            // while the dialpad is visible (since it would overlap.)
            //
            // The row contains two buttons:
            //
            // - "Manage conference" (used only on GSM devices)
            // - "Merge" button (used only on CDMA devices)

            mShowGenericMerge = isGenericConference && canMerge;
            mShowManageConference = (call.isConferenceCall() && !isGenericConference);

            updateExtraButtonRow();
            updateAuxiliaryActionButton();
        }
    }

    private void updateExtraButtonRow() {
        final boolean showExtraButtonRow = (mShowGenericMerge || mShowManageConference) &&
                !getUi().isDialpadVisible();

        Log.d(this, "isGeneric: " + mShowGenericMerge);
        Log.d(this, "mShowManageConference : " + mShowManageConference);
        Log.d(this, "mShowGenericMerge: " + mShowGenericMerge);
        if (showExtraButtonRow) {
            if (mShowGenericMerge) {
                getUi().showGenericMergeButton();
            } else if (mShowManageConference) {
                getUi().showManageConferenceCallButton();
            }
        } else {
            getUi().hideExtraRow();
        }
    }

    private void updateAuxiliaryActionButton() {
        if (mAuxiliaryActionService == null) {
            return;
        }
        final CallButtonUi ui = getUi();
        if (ui == null) {
            return;
        }
        if (mCall != null) {
            mAuxiliaryActionService.setRemotePhoneNumber(mCall.getNumber());
        }
        ui.updateAuxiliaryActionButton(mAuxiliaryActionService.isActionEnabled(),
                mAuxiliaryActionService.getActionDescription(),
                mAuxiliaryActionService.getActionDrawable());
    }

    public void refreshMuteState() {
        // Restore the previous mute state
        if (mAutomaticallyMuted &&
                AudioModeProvider.getInstance().getMute() != mPreviousMuteState) {
            if (getUi() == null) {
                return;
            }
            muteClicked(mPreviousMuteState);
        }
        mAutomaticallyMuted = false;
    }

    public interface CallButtonUi extends Ui {
        void setEnabled(boolean on);
        void setMute(boolean on);
        void enableMute(boolean enabled);
        void setHold(boolean on);
        void showHold(boolean show);
        void enableHold(boolean enabled);
        void showMerge(boolean show);
        void showSwap(boolean show);
        void showAddCall(boolean show);
        void enableAddCall(boolean enabled);
        void displayDialpad(boolean on, boolean animate);
        boolean isDialpadVisible();
        void setAudio(int mode);
        void setSupportedAudio(int mask);
        void showManageConferenceCallButton();
        void showGenericMergeButton();
        void hideExtraRow();
        void displayManageConferencePanel(boolean on);
        void updateAuxiliaryActionButton(boolean show, String description, Drawable drawable);
    }
}
