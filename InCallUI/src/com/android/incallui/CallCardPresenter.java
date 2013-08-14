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
import android.text.format.DateUtils;

import com.android.incallui.AudioModeProvider.AudioModeListener;
import com.android.incallui.ContactInfoCache.ContactCacheEntry;
import com.android.incallui.ContactInfoCache.ContactInfoCacheCallback;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;

import com.android.services.telephony.common.AudioMode;
import com.android.services.telephony.common.Call;

/**
 * Presenter for the Call Card Fragment.
 * This class listens for changes to InCallState and passes it along to the fragment.
 */
public class CallCardPresenter extends Presenter<CallCardPresenter.CallCardUi>
        implements InCallStateListener, AudioModeListener, ContactInfoCacheCallback {

    private static final long CALL_TIME_UPDATE_INTERVAL = 1000; // in milliseconds

    private AudioModeProvider mAudioModeProvider;
    private ContactInfoCache mContactInfoCache;
    private Call mPrimary;
    private Call mSecondary;
    private ContactCacheEntry mPrimaryContactInfo;
    private ContactCacheEntry mSecondaryContactInfo;
    private CallTimer mCallTimer;

    public CallCardPresenter() {

        // create the call timer
        mCallTimer = new CallTimer(new Runnable() {
            @Override
            public void run() {
                updateCallTime();
            }
        });
    }

    @Override
    public void onUiReady(CallCardUi ui) {
        super.onUiReady(ui);

        if (mAudioModeProvider != null) {
            mAudioModeProvider.addListener(this);
        }
    }

    @Override
    public void onUiUnready(CallCardUi ui) {
        super.onUiUnready(ui);

        if (mAudioModeProvider != null) {
            mAudioModeProvider.removeListener(this);
        }
        mPrimary = null;
        mPrimaryContactInfo = null;
        mSecondaryContactInfo = null;
    }

    @Override
    public void onStateChange(InCallState state, CallList callList) {
        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }

        Call primary = null;
        Call secondary = null;

        if (state == InCallState.INCOMING) {
            primary = callList.getIncomingCall();
        } else if (state == InCallState.OUTGOING) {
            primary = callList.getOutgoingCall();

            // getCallToDisplay doesn't go through outgoing or incoming calls. It will return the
            // highest priority call to display as the secondary call.
            secondary = getCallToDisplay(callList, null);
        } else if (state == InCallState.INCALL) {
            primary = getCallToDisplay(callList, null);
            secondary = getCallToDisplay(callList, primary);
        }

        Logger.d(this, "Primary call: " + primary);
        Logger.d(this, "Secondary call: " + secondary);

        mPrimary = primary;
        mSecondary = secondary;

        // Query for contact data. This will call back on onContactInfoComplete at least once
        // synchronously, and potentially a second time asynchronously if it needs to make
        // a full query for the data.
        // It is in that callback that we set the values into the Ui.
        startContactInfoSearch();

        // Start/Stop the call time update timer
        if (mPrimary != null && mPrimary.getState() == Call.State.ACTIVE) {
            Logger.d(this, "Starting the calltime timer");
            mCallTimer.start(CALL_TIME_UPDATE_INTERVAL);
        } else {
            Logger.d(this, "Canceling the calltime timer");
            mCallTimer.cancel();
            ui.setPrimaryCallElapsedTime(false, null);
        }

        // Set the call state
        if (mPrimary != null) {
            final boolean bluetoothOn = mAudioModeProvider != null &&
                    mAudioModeProvider.getAudioMode() == AudioMode.BLUETOOTH;
            ui.setCallState(mPrimary.getState(), mPrimary.getDisconnectCause(), bluetoothOn);
        } else {
            ui.setCallState(Call.State.INVALID, Call.DisconnectCause.UNKNOWN, false);
        }
    }

    @Override
    public void onAudioMode(int mode) {
        if (mPrimary != null && getUi() != null) {
            final boolean bluetoothOn = (AudioMode.BLUETOOTH == mode);

            getUi().setCallState(mPrimary.getState(), mPrimary.getDisconnectCause(), bluetoothOn);
        }
    }

    @Override
    public void onSupportedAudioMode(int mask) {
    }

    public void updateCallTime() {
        final CallCardUi ui = getUi();

        if (ui == null || mPrimary == null || mPrimary.getState() != Call.State.ACTIVE) {
            ui.setPrimaryCallElapsedTime(false, null);
            mCallTimer.cancel();
        }

        final long callStart = mPrimary.getConnectTime();
        final long duration = System.currentTimeMillis() - callStart;
        ui.setPrimaryCallElapsedTime(true, DateUtils.formatElapsedTime(duration / 1000));
    }


    public void setContactInfoCache(ContactInfoCache cache) {
        mContactInfoCache = cache;
        startContactInfoSearch();
    }

    /**
     * Starts a query for more contact data for the save primary and secondary calls.
     */
    private void startContactInfoSearch() {
        if (mPrimary != null && mContactInfoCache != null) {
            mContactInfoCache.findInfo(mPrimary, this);
        } else {
            mPrimaryContactInfo = null;
            updatePrimaryDisplayInfo();
        }

        if (mSecondary != null && mContactInfoCache != null) {
            mContactInfoCache.findInfo(mSecondary, this);
        } else {
            mSecondaryContactInfo = null;
            updateSecondaryDisplayInfo();
        }
    }

    /**
     * Get the highest priority call to display.
     * Goes through the calls and chooses which to return based on priority of which type of call
     * to display to the user. Callers can use the "ignore" feature to get the second best call
     * by passing a previously found primary call as ignore.
     *
     * @param ignore A call to ignore if found.
     */
    private Call getCallToDisplay(CallList callList, Call ignore) {

        // Disconnected calls get primary position to let user know quickly
        // what call has disconnected. Disconnected calls are very short lived.
        Call retval = callList.getDisconnectedCall();
        if (retval != null && retval != ignore) {
            return retval;
        }

        // Active calls come second.  An active call always gets precedent.
        retval = callList.getActiveCall();
        if (retval != null && retval != ignore) {
            return retval;
        }

        // Then we go to background call (calls on hold)
        retval = callList.getBackgroundCall();
        if (retval != null && retval != ignore) {
            return retval;
        }

        // Lastly, we go to a second background call.
        retval = callList.getSecondBackgroundCall();

        return retval;
    }

    /**
     * Callback received when Contact info data query completes.
     */
    @Override
    public void onContactInfoComplete(int callId, ContactCacheEntry entry) {
        Logger.d(this, "onContactInfoComplete: ", entry.name);
        Logger.d(this, "onContactInfoComplete: ", entry.number);
        Logger.d(this, "onContactInfoComplete: ", entry.label);
        Logger.d(this, "onContactInfoComplete: ", entry.photo);

        if (mPrimary != null && mPrimary.getCallId() == callId) {
            mPrimaryContactInfo = entry;
            updatePrimaryDisplayInfo();
        }
        if (mSecondary != null && mSecondary.getCallId() == callId) {
            mSecondaryContactInfo = entry;
            updateSecondaryDisplayInfo();
        }

    }

    private void updatePrimaryDisplayInfo() {
        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }

        if (mPrimaryContactInfo != null) {
            ui.setPrimary(mPrimaryContactInfo.number, mPrimaryContactInfo.name,
                    mPrimaryContactInfo.label, mPrimaryContactInfo.photo);
        } else {
            // reset to nothing (like at end of call)
            ui.setPrimary(null, null, null, null);
        }

    }

    private void updateSecondaryDisplayInfo() {
        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }

        if (mSecondaryContactInfo != null) {
            ui.setSecondary(true, mSecondaryContactInfo.number, mSecondaryContactInfo.name,
                    mSecondaryContactInfo.label, mSecondaryContactInfo.photo);
        } else {
            // reset to nothing so that it starts off blank next time we use it.
            ui.setSecondary(false, null, null, null, null);
        }
    }

    public void setAudioModeProvider(AudioModeProvider audioModeProvider) {
        mAudioModeProvider = audioModeProvider;
        mAudioModeProvider.addListener(this);
    }

    public interface CallCardUi extends Ui {
        void setVisible(boolean on);
        void setPrimary(String number, String name, String label, Drawable photo);
        void setSecondary(boolean show, String number, String name, String label, Drawable photo);
        void setCallState(int state, Call.DisconnectCause cause, boolean bluetoothOn);
        void setPrimaryCallElapsedTime(boolean show, String duration);
    }
}
