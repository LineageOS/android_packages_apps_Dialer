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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.Bitmap;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.android.incallui.AudioModeProvider.AudioModeListener;
import com.android.incallui.ContactInfoCache.ContactCacheEntry;
import com.android.incallui.ContactInfoCache.ContactInfoCacheCallback;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.services.telephony.common.AudioMode;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.CallIdentification;
import com.google.common.base.Preconditions;

/**
 * Presenter for the Call Card Fragment.
 * <p>
 * This class listens for changes to InCallState and passes it along to the fragment.
 */
public class CallCardPresenter extends Presenter<CallCardPresenter.CallCardUi>
        implements InCallStateListener, AudioModeListener {

    private static final String TAG = CallCardPresenter.class.getSimpleName();
    private static final long CALL_TIME_UPDATE_INTERVAL = 1000; // in milliseconds

    private Call mPrimary;
    private Call mSecondary;
    private ContactCacheEntry mPrimaryContactInfo;
    private ContactCacheEntry mSecondaryContactInfo;
    private CallTimer mCallTimer;
    private Context mContext;

    public CallCardPresenter() {
        // create the call timer
        mCallTimer = new CallTimer(new Runnable() {
            @Override
            public void run() {
                updateCallTime();
            }
        });
    }


    public void init(Context context, Call call) {
        mContext = Preconditions.checkNotNull(context);

        // Call may be null if disconnect happened already.
        if (call != null) {
            mPrimary = call;

            final CallIdentification identification = call.getIdentification();

            // TODO(klp): Logic to determine which ui field get what data resides in
            // contactInfoCache.
            // It needs to be moved so it can be re-used.
            mPrimaryContactInfo = ContactInfoCache.buildCacheEntryFromCall(mContext, identification,
                    call.getState() == Call.State.INCOMING);

            // start processing lookups right away.
            startContactInfoSearch(identification, true, false,
                    call.getState() == Call.State.INCOMING);
        }
    }

    @Override
    public void onUiReady(CallCardUi ui) {
        super.onUiReady(ui);

        AudioModeProvider.getInstance().addListener(this);

        // Contact search may have completed before ui is ready.
        if (mPrimaryContactInfo != null) {
            updatePrimaryDisplayInfo(mPrimaryContactInfo, false);
        }

        // Register for call state changes last
        InCallPresenter.getInstance().addListener(this);
    }

    @Override
    public void onUiUnready(CallCardUi ui) {
        super.onUiUnready(ui);

        // stop getting call state changes
        InCallPresenter.getInstance().removeListener(this);

        AudioModeProvider.getInstance().removeListener(this);

        mPrimary = null;
        mPrimaryContactInfo = null;
        mSecondaryContactInfo = null;
    }

    @Override
    public void onStateChange(InCallState state, CallList callList) {
        Log.d(this, "onStateChange() " + state);
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
            secondary = getCallToDisplay(callList, null, true);
        } else if (state == InCallState.INCALL) {
            primary = getCallToDisplay(callList, null, false);
            secondary = getCallToDisplay(callList, primary, true);
        }

        Log.d(this, "Primary call: " + primary);
        Log.d(this, "Secondary call: " + secondary);

        if (primary != null) {
            if (mPrimary == null || mPrimary.getCallId() != primary.getCallId()) {
                // primary call has changed
                mPrimaryContactInfo = ContactInfoCache.buildCacheEntryFromCall(mContext,
                        primary.getIdentification(), primary.getState() == Call.State.INCOMING);
                updatePrimaryDisplayInfo(mPrimaryContactInfo, isConference(primary));
                startContactInfoSearch(primary.getIdentification(), true,
                        primary.isConferenceCall(), primary.getState() == Call.State.INCOMING);
            }
        }

        if (secondary == null) {
            // Secondary call may have ended.  Update the ui.
            mSecondaryContactInfo = null;
            updateSecondaryDisplayInfo(false);
        } else {
            if (mSecondary == null || mSecondary.getCallId() != secondary.getCallId()) {
                // secondary call has changed
                mSecondaryContactInfo = ContactInfoCache.buildCacheEntryFromCall(mContext,
                        secondary.getIdentification(), secondary.getState() == Call.State.INCOMING);
                updateSecondaryDisplayInfo(secondary.isConferenceCall());
                startContactInfoSearch(secondary.getIdentification(), false,
                        secondary.isConferenceCall(), secondary.getState() == Call.State.INCOMING);
            }
        }

        mPrimary = primary;
        mSecondary = secondary;

        // Start/Stop the call time update timer
        if (mPrimary != null && mPrimary.getState() == Call.State.ACTIVE) {
            Log.d(this, "Starting the calltime timer");
            mCallTimer.start(CALL_TIME_UPDATE_INTERVAL);
        } else {
            Log.d(this, "Canceling the calltime timer");
            mCallTimer.cancel();
            ui.setPrimaryCallElapsedTime(false, null);
        }

        // Set the call state
        if (mPrimary != null) {
            final boolean bluetoothOn =
                    (AudioModeProvider.getInstance().getAudioMode() == AudioMode.BLUETOOTH);
            ui.setCallState(mPrimary.getState(), mPrimary.getDisconnectCause(), bluetoothOn,
                    getGatewayLabel(), getGatewayNumber());
        } else {
            ui.setCallState(Call.State.IDLE, Call.DisconnectCause.UNKNOWN, false, null, null);
        }
    }

    @Override
    public void onAudioMode(int mode) {
        if (mPrimary != null && getUi() != null) {
            final boolean bluetoothOn = (AudioMode.BLUETOOTH == mode);

            getUi().setCallState(mPrimary.getState(), mPrimary.getDisconnectCause(), bluetoothOn,
                    getGatewayLabel(), getGatewayNumber());
        }
    }

    @Override
    public void onSupportedAudioMode(int mask) {
    }

    @Override
    public void onMute(boolean muted) {
    }

    public void updateCallTime() {
        final CallCardUi ui = getUi();

        if (ui == null || mPrimary == null || mPrimary.getState() != Call.State.ACTIVE) {
            if (ui != null) {
                ui.setPrimaryCallElapsedTime(false, null);
            }
            mCallTimer.cancel();
        } else {
            final long callStart = mPrimary.getConnectTime();
            final long duration = System.currentTimeMillis() - callStart;
            ui.setPrimaryCallElapsedTime(true, DateUtils.formatElapsedTime(duration / 1000));
        }
    }

    /**
     * Starts a query for more contact data for the save primary and secondary calls.
     */
    private void startContactInfoSearch(final CallIdentification identification,
            final boolean isPrimary, final boolean isConference, boolean isIncoming) {

        final ContactInfoCache cache = ContactInfoCache.getInstance(mContext);

        cache.findInfo(identification, isIncoming, new ContactInfoCacheCallback() {
                @Override
                public void onContactInfoComplete(int callId, ContactCacheEntry entry) {
                    updateContactEntry(entry, isPrimary, isConference);
                    if (entry.name != null) {
                        Log.d(TAG, "Contact found: " + entry);
                    }
                }

                @Override
                public void onImageLoadComplete(int callId, Bitmap photo) {
                    if (getUi() == null) {
                        return;
                    }
                    if (mPrimary != null && callId == mPrimary.getCallId()) {
                        getUi().setPrimaryImage(photo);
                    } else if (mSecondary != null && callId == mSecondary.getCallId()) {
                        getUi().setSecondaryImage(photo);
                    }
                }
            });
    }

    private boolean isConference(Call call) {
        if (call == null) {
            return false;
        }
        return call.isConferenceCall();
    }

    private void updateContactEntry(ContactCacheEntry entry, boolean isPrimary,
            boolean isConference) {
        if (isPrimary) {
            mPrimaryContactInfo = entry;
            updatePrimaryDisplayInfo(entry, isConference);
        } else {
            mSecondaryContactInfo = entry;
            updateSecondaryDisplayInfo(isConference);
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
    private Call getCallToDisplay(CallList callList, Call ignore, boolean skipDisconnected) {

        // Active calls come second.  An active call always gets precedent.
        Call retval = callList.getActiveCall();
        if (retval != null && retval != ignore) {
            return retval;
        }

        // Disconnected calls get primary position if there are no active calls
        // to let user know quickly what call has disconnected. Disconnected
        // calls are very short lived.
        if (!skipDisconnected) {
            retval = callList.getDisconnectedCall();
            if (retval != null && retval != ignore) {
                return retval;
            }
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

    private void updatePrimaryDisplayInfo(ContactCacheEntry entry, boolean isConference) {
        Log.d(TAG, "Update primary display " + entry);
        final CallCardUi ui = getUi();
        if (ui == null) {
            // TODO: May also occur if search result comes back after ui is destroyed. Look into
            // removing that case completely.
            Log.d(TAG, "updatePrimaryDisplayInfo called but ui is null!");
            return;
        }

        if (entry != null) {
            final String name = getNameForCall(entry);
            final String number = getNumberForCall(entry);
            final boolean nameIsNumber = name != null && name.equals(entry.number);

            ui.setPrimary(number, name, nameIsNumber, entry.label,
                    entry.photo, isConference);
        } else {
            // reset to nothing (like at end of call)
            ui.setPrimary(null, null, false, null, null, false);
        }

    }

    private void updateSecondaryDisplayInfo(boolean isConference) {

        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }

        if (mSecondaryContactInfo != null) {
            Log.d(TAG, "updateSecondaryDisplayInfo() " + mSecondaryContactInfo);
            final String nameForCall = getNameForCall(mSecondaryContactInfo);

            final boolean nameIsNumber = nameForCall != null && nameForCall.equals(
                    mSecondaryContactInfo.number);
            ui.setSecondary(true, nameForCall, nameIsNumber, mSecondaryContactInfo.label,
                    mSecondaryContactInfo.photo, isConference);
        } else {
            // reset to nothing so that it starts off blank next time we use it.
            ui.setSecondary(false, null, false, null, null, false);
        }
    }

    /**
     * Returns the gateway number for any existing outgoing call.
     */
    private String getGatewayNumber() {
        if (hasOutgoingGatewayCall()) {
            return mPrimary.getGatewayNumber();
        }

        return null;
    }

    /**
     * Returns the label for the gateway app for any existing outgoing call.
     */
    private String getGatewayLabel() {
        if (hasOutgoingGatewayCall() && getUi() != null) {
            final PackageManager pm = mContext.getPackageManager();
            try {
                final ApplicationInfo info = pm.getApplicationInfo(mPrimary.getGatewayPackage(), 0);
                return mContext.getString(R.string.calling_via_template,
                        pm.getApplicationLabel(info).toString());
            } catch (PackageManager.NameNotFoundException e) {
            }
        }
        return null;
    }

    private boolean hasOutgoingGatewayCall() {
        // We only display the gateway information while DIALING so return false for any othe
        // call state.
        // TODO: mPrimary can be null because this is called from updatePrimaryDisplayInfo which
        // is also called after a contact search completes (call is not present yet).  Split the
        // UI update so it can receive independent updates.
        if (mPrimary == null) {
            return false;
        }
        return (mPrimary.getState() == Call.State.DIALING &&
                !TextUtils.isEmpty(mPrimary.getGatewayNumber()) &&
                !TextUtils.isEmpty(mPrimary.getGatewayPackage()));
    }

    /**
     * Gets the name to display for the call.
     */
    private static String getNameForCall(ContactCacheEntry contactInfo) {
        if (TextUtils.isEmpty(contactInfo.name)) {
            return contactInfo.number;
        }
        return contactInfo.name;
    }

    /**
     * Gets the number to display for a call.
     */
    private static String getNumberForCall(ContactCacheEntry contactInfo) {
        // If the name is empty, we use the number for the name...so dont show a second
        // number in the number field
        if (TextUtils.isEmpty(contactInfo.name)) {
            return contactInfo.location;
        }
        return contactInfo.number;
    }

    public void secondaryPhotoClicked() {
        CallCommandClient.getInstance().swap();
    }

    public interface CallCardUi extends Ui {
        void setVisible(boolean on);
        void setPrimary(String number, String name, boolean nameIsNumber, String label,
                Drawable photo, boolean isConference);
        void setSecondary(boolean show, String name, boolean nameIsNumber, String label,
                Drawable photo, boolean isConference);
        void setSecondaryImage(Bitmap bitmap);
        void setCallState(int state, Call.DisconnectCause cause, boolean bluetoothOn,
                String gatewayLabel, String gatewayNumber);
        void setPrimaryCallElapsedTime(boolean show, String duration);
        void setPrimaryName(String name, boolean nameIsNumber);
        void setPrimaryImage(Bitmap bitmap);
        void setPrimaryPhoneNumber(String phoneNumber);
        void setPrimaryLabel(String label);
    }
}
