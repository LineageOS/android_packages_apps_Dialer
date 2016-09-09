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

import com.google.common.base.Preconditions;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.telecom.Call.Details;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.ListAdapter;

import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.compat.telecom.TelecomManagerCompat;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.common.testing.NeededForTesting;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.dialer.R;
import com.android.incallui.Call.State;
import com.android.incallui.ContactInfoCache.ContactCacheEntry;
import com.android.incallui.ContactInfoCache.ContactInfoCacheCallback;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallEventListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incalluibind.ObjectFactory;

import java.lang.ref.WeakReference;

import static com.android.contacts.common.compat.CallSdkCompat.Details.PROPERTY_ENTERPRISE_CALL;
/**
 * Presenter for the Call Card Fragment.
 * <p>
 * This class listens for changes to InCallState and passes it along to the fragment.
 */
public class CallCardPresenter extends Presenter<CallCardPresenter.CallCardUi>
        implements InCallStateListener, IncomingCallListener, InCallDetailsListener,
        InCallEventListener, CallList.CallUpdateListener, DistanceHelper.Listener {

    public interface EmergencyCallListener {
        public void onCallUpdated(BaseFragment fragment, boolean isEmergency);
    }

    private static final String TAG = CallCardPresenter.class.getSimpleName();
    private static final long CALL_TIME_UPDATE_INTERVAL_MS = 1000;

    private static final int IDP_NONE = 0;
    private static final int IDP_CDMA = 1;
    private static final int IDP_GSM = 2;
    private static final int IDP_BOTH = 3;

    private final EmergencyCallListener mEmergencyCallListener =
            ObjectFactory.newEmergencyCallListener();
    private DistanceHelper mDistanceHelper;

    private Call mPrimary;
    private Call mSecondary;
    private ContactCacheEntry mPrimaryContactInfo;
    private ContactCacheEntry mSecondaryContactInfo;
    private CallTimer mCallTimer;
    private Context mContext;
    @Nullable private ContactsPreferences mContactsPreferences;
    private boolean mSpinnerShowing = false;
    private boolean mHasShownToast = false;
    private InCallContactInteractions mInCallContactInteractions;
    private boolean mIsFullscreen = false;

    public static class ContactLookupCallback implements ContactInfoCacheCallback {
        private final WeakReference<CallCardPresenter> mCallCardPresenter;
        private final boolean mIsPrimary;

        public ContactLookupCallback(CallCardPresenter callCardPresenter, boolean isPrimary) {
            mCallCardPresenter = new WeakReference<CallCardPresenter>(callCardPresenter);
            mIsPrimary = isPrimary;
        }

        @Override
        public void onContactInfoComplete(String callId, ContactCacheEntry entry) {
            CallCardPresenter presenter = mCallCardPresenter.get();
            if (presenter != null) {
                presenter.onContactInfoComplete(callId, entry, mIsPrimary);
            }
        }

        @Override
        public void onImageLoadComplete(String callId, ContactCacheEntry entry) {
            CallCardPresenter presenter = mCallCardPresenter.get();
            if (presenter != null) {
                presenter.onImageLoadComplete(callId, entry);
            }
        }

        @Override
        public void onContactInteractionsInfoComplete(String callId, ContactCacheEntry entry) {
            CallCardPresenter presenter = mCallCardPresenter.get();
            if (presenter != null) {
                presenter.onContactInteractionsInfoComplete(callId, entry);
            }
        }
    }

    public CallCardPresenter() {
        // create the call timer
        mCallTimer = new CallTimer(new Runnable() {
            @Override
            public void run() {
                updateCallTime();
            }
        });
    }

    private boolean isGeocoderLocationNeeded(Call call) {
        Log.d(this, "isGeocoderLocationNeeded getState() = " + call.getState());
        if (call.getState() == Call.State.INCOMING ||
                call.getState() == Call.State.CALL_WAITING ||
                call.getState() == Call.State.DIALING ||
                call.getState() == Call.State.CONNECTING ||
                call.getState() == Call.State.SELECT_PHONE_ACCOUNT) {
            return true;
        };
        return false;
    }

    public void init(Context context, Call call) {
        mContext = Preconditions.checkNotNull(context);
        mDistanceHelper = ObjectFactory.newDistanceHelper(mContext, this);
        mContactsPreferences = ContactsPreferencesFactory.newContactsPreferences(mContext);

        // Call may be null if disconnect happened already.
        if (call != null) {
            mPrimary = call;
            if (shouldShowNoteSentToast(mPrimary)) {
                final CallCardUi ui = getUi();
                if (ui != null) {
                    ui.showNoteSentToast();
                }
            }
            CallList.getInstance().addCallUpdateListener(call.getId(), this);

            // start processing lookups right away.
            if (!call.isConferenceCall()) {
                startContactInfoSearch(call, true, isGeocoderLocationNeeded(call));
            } else {
                updateContactEntry(null, true);
            }
        }

        onStateChange(null, InCallPresenter.getInstance().getInCallState(), CallList.getInstance());
    }

    @Override
    public void onUiReady(CallCardUi ui) {
        super.onUiReady(ui);

        if (mContactsPreferences != null) {
            mContactsPreferences.refreshValue(ContactsPreferences.DISPLAY_ORDER_KEY);
        }

        // Contact search may have completed before ui is ready.
        if (mPrimaryContactInfo != null) {
            updatePrimaryDisplayInfo();
        }

        // Register for call state changes last
        InCallPresenter.getInstance().addListener(this);
        InCallPresenter.getInstance().addIncomingCallListener(this);
        InCallPresenter.getInstance().addDetailsListener(this);
        InCallPresenter.getInstance().addInCallEventListener(this);
    }

    @Override
    public void onUiUnready(CallCardUi ui) {
        super.onUiUnready(ui);

        // stop getting call state changes
        InCallPresenter.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);
        InCallPresenter.getInstance().removeDetailsListener(this);
        InCallPresenter.getInstance().removeInCallEventListener(this);
        if (mPrimary != null) {
            CallList.getInstance().removeCallUpdateListener(mPrimary.getId(), this);
        }

        if (mDistanceHelper != null) {
            mDistanceHelper.cleanUp();
        }

        mPrimary = null;
        mPrimaryContactInfo = null;
        mSecondaryContactInfo = null;
    }

    @Override
    public void onIncomingCall(InCallState oldState, InCallState newState, Call call) {
        // same logic should happen as with onStateChange()
        onStateChange(oldState, newState, CallList.getInstance());
    }

    @Override
    public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
        Log.d(this, "onStateChange() " + newState);
        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }

        Call primary = null;
        Call secondary = null;

        if (newState == InCallState.INCOMING) {
            primary = callList.getIncomingCall();
        } else if (newState == InCallState.PENDING_OUTGOING || newState == InCallState.OUTGOING) {
            primary = callList.getOutgoingCall();
            if (primary == null) {
                primary = callList.getPendingOutgoingCall();
            }

            // getCallToDisplay doesn't go through outgoing or incoming calls. It will return the
            // highest priority call to display as the secondary call.
            secondary = getCallToDisplay(callList, null, true);
        } else if (newState == InCallState.INCALL) {
            primary = getCallToDisplay(callList, null, false);
            secondary = getCallToDisplay(callList, primary, true);
        }

        if (mInCallContactInteractions != null &&
                (oldState == InCallState.INCOMING || newState == InCallState.INCOMING)) {
            ui.showContactContext(newState != InCallState.INCOMING);
        }

        Log.d(this, "Primary call: " + primary);
        Log.d(this, "Secondary call: " + secondary);

        final boolean primaryChanged = !(Call.areSame(mPrimary, primary) &&
                Call.areSameNumber(mPrimary, primary));
        final boolean secondaryChanged = !(Call.areSame(mSecondary, secondary) &&
                Call.areSameNumber(mSecondary, secondary));
        final boolean primaryForwardedChanged = isForwarded(primary) != isForwarded(mPrimary);

        mSecondary = secondary;
        Call previousPrimary = mPrimary;
        mPrimary = primary;

        if (primaryChanged && shouldShowNoteSentToast(primary)) {
            ui.showNoteSentToast();
        }

        // Refresh primary call information if either:
        // 1. Primary call changed.
        // 2. The call's ability to manage conference has changed.
        // 3. The call subject should be shown or hidden.
        if (shouldRefreshPrimaryInfo(primaryChanged, ui, shouldShowCallSubject(mPrimary))) {
            // primary call has changed
            if (previousPrimary != null) {
                //clear progess spinner (if any) related to previous primary call
                maybeShowProgressSpinner(previousPrimary.getState(),
                        Call.SessionModificationState.NO_REQUEST);
                CallList.getInstance().removeCallUpdateListener(previousPrimary.getId(), this);
            }
            CallList.getInstance().addCallUpdateListener(mPrimary.getId(), this);

            mPrimaryContactInfo = ContactInfoCache.buildCacheEntryFromCall(mContext, mPrimary,
                    isGeocoderLocationNeeded(mPrimary));
            updatePrimaryDisplayInfo();
            maybeStartSearch(mPrimary, true);
            maybeClearSessionModificationState(mPrimary);
        } else if (primaryForwardedChanged && mPrimary != null) {
            updatePrimaryDisplayInfo();
        }

        if (previousPrimary != null && mPrimary == null) {
            //clear progess spinner (if any) related to previous primary call
            maybeShowProgressSpinner(previousPrimary.getState(),
                    Call.SessionModificationState.NO_REQUEST);
            CallList.getInstance().removeCallUpdateListener(previousPrimary.getId(), this);
        }

        if (mSecondary == null) {
            // Secondary call may have ended.  Update the ui.
            mSecondaryContactInfo = null;
            updateSecondaryDisplayInfo();
        } else if (secondaryChanged) {
            // secondary call has changed
            mSecondaryContactInfo = ContactInfoCache.buildCacheEntryFromCall(mContext, mSecondary,
                    mSecondary.getState() == Call.State.INCOMING);
            updateSecondaryDisplayInfo();
            maybeStartSearch(mSecondary, false);
            maybeClearSessionModificationState(mSecondary);
        }

        // Start/stop timers.
        if (isPrimaryCallActive()) {
            Log.d(this, "Starting the calltime timer");
            mCallTimer.start(CALL_TIME_UPDATE_INTERVAL_MS);
        } else {
            Log.d(this, "Canceling the calltime timer");
            mCallTimer.cancel();
            ui.setPrimaryCallElapsedTime(false, 0);
        }

        // Set the call state
        int callState = Call.State.IDLE;
        if (mPrimary != null) {
            callState = mPrimary.getState();
            updatePrimaryCallState();
        } else {
            getUi().setCallState(
                    callState,
                    VideoProfile.STATE_AUDIO_ONLY,
                    Call.SessionModificationState.NO_REQUEST,
                    new DisconnectCause(DisconnectCause.UNKNOWN),
                    null,
                    null,
                    null,
                    false /* isWifi */,
                    false /* isConference */,
                    false /* isWaitingForRemoteSide */,
                    false /* isWorkCall */);
            getUi().showHdAudioIndicator(false);
        }

        maybeShowManageConferenceCallButton();

        // Hide the end call button instantly if we're receiving an incoming call.
        getUi().setEndCallButtonEnabled(shouldShowEndCallButton(mPrimary, callState),
                callState != Call.State.INCOMING /* animate */);

        maybeSendAccessibilityEvent(oldState, newState, primaryChanged);
    }

    @Override
    public void onDetailsChanged(Call call, Details details) {
        updatePrimaryCallState();

        if (call.can(Details.CAPABILITY_MANAGE_CONFERENCE) !=
                details.can(Details.CAPABILITY_MANAGE_CONFERENCE)) {
            maybeShowManageConferenceCallButton();
        }
    }

    @Override
    public void onCallChanged(Call call) {
        // No-op; specific call updates handled elsewhere.
    }

    /**
     * Handles a change to the session modification state for a call.  Triggers showing the progress
     * spinner, as well as updating the call state label.
     *
     * @param sessionModificationState The new session modification state.
     */
    @Override
    public void onSessionModificationStateChange(int sessionModificationState) {
        Log.d(this, "onSessionModificationStateChange : sessionModificationState = " +
                sessionModificationState);

        if (mPrimary == null) {
            return;
        }
        maybeShowProgressSpinner(mPrimary.getState(), sessionModificationState);
        getUi().setEndCallButtonEnabled(sessionModificationState !=
                        Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST,
                true /* shouldAnimate */);
        updatePrimaryCallState();
    }

    /**
     * Handles a change to the last forwarding number by refreshing the primary call info.
     */
    @Override
    public void onLastForwardedNumberChange() {
        Log.v(this, "onLastForwardedNumberChange");

        if (mPrimary == null) {
            return;
        }
        updatePrimaryDisplayInfo();
    }

    /**
     * Handles a change to the child number by refreshing the primary call info.
     */
    @Override
    public void onChildNumberChange() {
        Log.v(this, "onChildNumberChange");

        if (mPrimary == null) {
            return;
        }
        updatePrimaryDisplayInfo();
    }

    private static boolean isForwarded(Call call) {
        return call != null && call.isForwarded();
    }

    private boolean shouldRefreshPrimaryInfo(boolean primaryChanged, CallCardUi ui,
            boolean shouldShowCallSubject) {
        if (mPrimary == null) {
            return false;
        }
        return primaryChanged ||
                ui.isManageConferenceVisible() != shouldShowManageConference() ||
                ui.isCallSubjectVisible() != shouldShowCallSubject;
    }

    private String getSubscriptionNumber() {
        // If it's an emergency call, and they're not populating the callback number,
        // then try to fall back to the phone sub info (to hopefully get the SIM's
        // number directly from the telephony layer).
        PhoneAccountHandle accountHandle = mPrimary.getAccountHandle();
        if (accountHandle != null) {
            TelecomManager mgr = InCallPresenter.getInstance().getTelecomManager();
            PhoneAccount account = TelecomManagerCompat.getPhoneAccount(mgr, accountHandle);
            if (account != null) {
                return getNumberFromHandle(account.getSubscriptionAddress());
            }
        }
        return null;
    }

    private void updatePrimaryCallState() {
        if (getUi() != null && mPrimary != null) {
            boolean isWorkCall = mPrimary.hasProperty(PROPERTY_ENTERPRISE_CALL)
                    || (mPrimaryContactInfo == null ? false
                            : mPrimaryContactInfo.userType == ContactsUtils.USER_TYPE_WORK);
            InCallPresenter.getInstance().setThemeColors();
            boolean isConfCall = mPrimary.isConferenceCall() || mPrimary.isIncomingConfCall();
            getUi().setCallState(
                    mPrimary.getState(),
                    mPrimary.getVideoState(),
                    mPrimary.getSessionModificationState(),
                    mPrimary.getDisconnectCause(),
                    getConnectionLabel(),
                    getCallStateIcon(),
                    getGatewayNumber(),
                    mPrimary.hasProperty(Details.PROPERTY_WIFI),
                    isConfCall,
                    mPrimary.isWaitingForRemoteSide(),
                    isWorkCall);

            maybeShowHdAudioIcon();
            setCallbackNumber();
        }
    }

    /**
     * Show the HD icon if the call is active and has {@link Details#PROPERTY_HIGH_DEF_AUDIO},
     * except if the call has a last forwarded number (we will show that icon instead).
     */
    private void maybeShowHdAudioIcon() {
        boolean showHdAudioIndicator =
                isPrimaryCallActive() && mPrimary.hasProperty(Details.PROPERTY_HIGH_DEF_AUDIO) &&
                TextUtils.isEmpty(mPrimary.getLastForwardedNumber());
        getUi().showHdAudioIndicator(showHdAudioIndicator);
    }

    /**
     * Only show the conference call button if we can manage the conference.
     */
    private void maybeShowManageConferenceCallButton() {
        getUi().showManageConferenceCallButton(shouldShowManageConference());
    }

    /**
     * Determines if a pending session modification exists for the current call.  If so, the
     * progress spinner is shown, and the call state is updated.
     *
     * @param callState The call state.
     * @param sessionModificationState The session modification state.
     */
    private void maybeShowProgressSpinner(int callState, int sessionModificationState) {
        final boolean show = sessionModificationState ==
                Call.SessionModificationState.WAITING_FOR_RESPONSE
                && callState == Call.State.ACTIVE;
        if (show != mSpinnerShowing) {
            getUi().setProgressSpinnerVisible(show);
            mSpinnerShowing = show;
        }
    }

    /**
     * Determines if the manage conference button should be visible, based on the current primary
     * call.
     *
     * @return {@code True} if the manage conference button should be visible.
     */
    private boolean shouldShowManageConference() {
        if (mPrimary == null) {
            return false;
        }

        return mPrimary.can(android.telecom.Call.Details.CAPABILITY_MANAGE_CONFERENCE)
                && !mIsFullscreen;
    }

    private void setCallbackNumber() {
        String callbackNumber = null;

        // Show the emergency callback number if either:
        // 1. This is an emergency call.
        // 2. The phone is in Emergency Callback Mode, which means we should show the callback
        //    number.
        boolean showCallbackNumber = mPrimary.hasProperty(Details.PROPERTY_EMERGENCY_CALLBACK_MODE);

        if (mPrimary.isEmergencyCall() || showCallbackNumber) {
            callbackNumber = getSubscriptionNumber();
        } else {
            StatusHints statusHints = mPrimary.getTelecomCall().getDetails().getStatusHints();
            if (statusHints != null) {
                Bundle extras = statusHints.getExtras();
                if (extras != null) {
                    callbackNumber = extras.getString(TelecomManager.EXTRA_CALL_BACK_NUMBER);
                }
            }
        }

        final String simNumber = TelecomManagerCompat.getLine1Number(
                InCallPresenter.getInstance().getTelecomManager(),
                InCallPresenter.getInstance().getTelephonyManager(),
                mPrimary.getAccountHandle());
        if (!showCallbackNumber && PhoneNumberUtils.compare(callbackNumber, simNumber)) {
            Log.d(this, "Numbers are the same (and callback number is not being forced to show);" +
                    " not showing the callback number");
            callbackNumber = null;
        }

        getUi().setCallbackNumber(callbackNumber, mPrimary.isEmergencyCall() || showCallbackNumber);
    }

    public void updateCallTime() {
        final CallCardUi ui = getUi();

        if (ui == null) {
            mCallTimer.cancel();
        } else if (!isPrimaryCallActive()) {
            ui.setPrimaryCallElapsedTime(false, 0);
            mCallTimer.cancel();
        } else {
            final long callStart = mPrimary.getConnectTimeMillis();
            final long duration = System.currentTimeMillis() - callStart;
            ui.setPrimaryCallElapsedTime(true, duration);
        }
    }

    public void onCallStateButtonTouched() {
        Intent broadcastIntent = ObjectFactory.getCallStateButtonBroadcastIntent(mContext);
        if (broadcastIntent != null) {
            Log.d(this, "Sending call state button broadcast: ", broadcastIntent);
            mContext.sendBroadcast(broadcastIntent, Manifest.permission.READ_PHONE_STATE);
        }
    }

    /**
     * Handles click on the contact photo by toggling fullscreen mode if the current call is a video
     * call.
     */
    public void onContactPhotoClick() {
        if (mPrimary != null && mPrimary.isVideoCall(mContext)) {
            InCallPresenter.getInstance().toggleFullscreenMode();
        }
    }

    private void maybeStartSearch(Call call, boolean isPrimary) {
        // no need to start search for conference calls which show generic info.
        if (call != null && !call.isConferenceCall()) {
            startContactInfoSearch(call, isPrimary, isGeocoderLocationNeeded(call));
        }
    }

    private void maybeClearSessionModificationState(Call call) {
        if (call.getSessionModificationState() !=
                Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
            call.setSessionModificationState(Call.SessionModificationState.NO_REQUEST);
        }
    }

    /**
     * Starts a query for more contact data for the save primary and secondary calls.
     */
    private void startContactInfoSearch(final Call call, final boolean isPrimary,
            boolean isIncoming) {
        final ContactInfoCache cache = ContactInfoCache.getInstance(mContext);

        cache.findInfo(call, isIncoming, new ContactLookupCallback(this, isPrimary));
    }

    private void onContactInfoComplete(String callId, ContactCacheEntry entry, boolean isPrimary) {
        final boolean entryMatchesExistingCall =
                (isPrimary && mPrimary != null && TextUtils.equals(callId,  mPrimary.getId())) ||
                (!isPrimary && mSecondary != null && TextUtils.equals(callId, mSecondary.getId()));
        if (entryMatchesExistingCall) {
            updateContactEntry(entry, isPrimary);
        } else {
            Log.w(this, "Dropping stale contact lookup info for " + callId);
        }

        final Call call = CallList.getInstance().getCallById(callId);
        if (call != null) {
            call.getLogState().contactLookupResult = entry.contactLookupResult;
        }
        if (entry.contactUri != null) {
            CallerInfoUtils.sendViewNotification(mContext, entry.contactUri);
        }
    }

    private void onImageLoadComplete(String callId, ContactCacheEntry entry) {
        if (getUi() == null) {
            return;
        }

        if (entry.photo != null) {
            if (mPrimary != null && callId.equals(mPrimary.getId())) {
                boolean showContactPhoto = !VideoCallPresenter.showIncomingVideo(
                        mPrimary.getVideoState(), mPrimary.getState());
                getUi().setPrimaryImage(entry.photo, showContactPhoto);
            }
        }
    }

    private void onContactInteractionsInfoComplete(String callId, ContactCacheEntry entry) {
        if (getUi() == null) {
            return;
        }

        if (mPrimary != null && callId.equals(mPrimary.getId())) {
            mPrimaryContactInfo.locationAddress = entry.locationAddress;
            updateContactInteractions();
        }
    }

    @Override
    public void onLocationReady() {
        // This will only update the contacts interactions data if the location returns after
        // the contact information is found.
        updateContactInteractions();
    }

    private void updateContactInteractions() {
        if (mPrimary != null && mPrimaryContactInfo != null
                && (mPrimaryContactInfo.locationAddress != null
                        || mPrimaryContactInfo.openingHours != null)) {
            // TODO: This is hardcoded to "isBusiness" because functionality to differentiate
            // between business and personal has not yet been added.
            if (setInCallContactInteractionsType(true /* isBusiness */)) {
                getUi().setContactContextTitle(
                        mInCallContactInteractions.getBusinessListHeaderView());
            }

            mInCallContactInteractions.setBusinessInfo(
                    mPrimaryContactInfo.locationAddress,
                    mDistanceHelper.calculateDistance(mPrimaryContactInfo.locationAddress),
                    mPrimaryContactInfo.openingHours);
            getUi().setContactContextContent(mInCallContactInteractions.getListAdapter());
            getUi().showContactContext(mPrimary.getState() != State.INCOMING);
        } else {
            getUi().showContactContext(false);
        }
    }

    /**
     * Update the contact interactions type so that the correct UI is shown.
     *
     * @param isBusiness {@code true} if the interaction is a business interaction, {@code false} if
     * it is a personal contact.
     *
     * @return {@code true} if this is a new type of contact interaction (business or personal).
     * {@code false} if it hasn't changed.
     */
    private boolean setInCallContactInteractionsType(boolean isBusiness) {
        if (mInCallContactInteractions == null) {
            mInCallContactInteractions =
                    new InCallContactInteractions(mContext, isBusiness);
            return true;
        }

        return mInCallContactInteractions.switchContactType(isBusiness);
    }

    private void updateContactEntry(ContactCacheEntry entry, boolean isPrimary) {
        if (isPrimary) {
            mPrimaryContactInfo = entry;
            updatePrimaryDisplayInfo();
        } else {
            mSecondaryContactInfo = entry;
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
    private Call getCallToDisplay(CallList callList, Call ignore, boolean skipDisconnected) {
        // Active calls come second.  An active call always gets precedent.
        Call retval = callList.getActiveCall();
        if (retval != null && retval != ignore) {
            return retval;
        }

        // Sometimes there is intemediate state that two calls are in active even one is about
        // to be on hold.
        retval = callList.getSecondActiveCall();
        if (retval != null && retval != ignore) {
            return retval;
        }

        // Disconnected calls get primary position if there are no active calls
        // to let user know quickly what call has disconnected. Disconnected
        // calls are very short lived.
        if (!skipDisconnected) {
            retval = callList.getDisconnectingCall();
            if (retval != null && retval != ignore) {
                return retval;
            }
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

    private boolean checkIdpEnable(int subId) {
        boolean ret = false;
        final int checkIdp = mContext.getResources().getInteger(R.integer.check_idp);
        final int phoneType = TelephonyManager.getDefault().getCurrentPhoneType(subId);

        if (checkIdp == IDP_BOTH ||
                (checkIdp == IDP_CDMA && phoneType == TelephonyManager.PHONE_TYPE_CDMA) ||
                (checkIdp == IDP_GSM && phoneType == TelephonyManager.PHONE_TYPE_GSM)) {
            ret = true;
        }

        Log.i(this, "checkIdp phone type:" + phoneType + " enabled:" + ret);
        return ret;
    }

    private int getSubId() {
        int subId = SubscriptionManager.getDefaultVoiceSubscriptionId();

        PhoneAccountHandle accountHandle = mPrimary.getAccountHandle();
        if (accountHandle != null) {
            TelecomManager mgr = InCallPresenter.getInstance().getTelecomManager();
            PhoneAccount account = mgr.getPhoneAccount(accountHandle);
            if (account != null) {
                subId = TelephonyManager.getDefault().getSubIdForPhoneAccount(account);
                Log.d(this, "get sub id from phone account = " + subId);
            }
        }

        return subId;
    }

    private String checkIdp(String number, boolean nameIsNumber, boolean isIncoming) {
        int subId = getSubId();
        String checkedNumber = number;
        String[] idp = mContext.getResources().getStringArray(R.array.international_idp);
        String[] idpValues = mContext.getResources().
                getStringArray(R.array.international_idp_values);
        boolean isDataInValid = (idp.length == 0 || idpValues.length == 0 ||
                idp.length != idpValues.length);

        if (checkIdpEnable(subId) && nameIsNumber && !isDataInValid) {
            if (!isIncoming) {
                final int checkRoamingIdx = mContext.getResources().
                        getInteger(R.integer.check_idp_roaming_idx);
                final boolean isNetworkRoaming =
                        TelephonyManager.getDefault().isNetworkRoaming(subId);
                for (int i = 0; i < idp.length; i++) {
                    Log.i(this, "checkIdp idp:" + idp[i] + " idp value:" + idpValues[i] +
                            " roaming idx:" + checkRoamingIdx);
                    int indexIdp = checkedNumber.indexOf(idp[i]);

                    if (indexIdp != -1) {
                        if ((checkRoamingIdx == i && !isNetworkRoaming) ||
                                checkRoamingIdx != i) {
                            checkedNumber = checkedNumber.substring(0, indexIdp) + idpValues[i] +
                                    checkedNumber.substring(indexIdp + idp[i].length());
                        }
                    }
                }
            } else {
                final int checkReconvertIdx = mContext.getResources().
                        getInteger(R.integer.check_idp_reconvert_idx);
                if (checkReconvertIdx >= 0 && checkReconvertIdx < idp.length &&
                        number.indexOf(idpValues[checkReconvertIdx]) == 0) {
                    checkedNumber = idp[checkReconvertIdx] +
                            number.substring(idpValues[checkReconvertIdx].length());
                }
            }
        }

        Log.i(this, "checkIdp number: " + number + " subid:" + subId + " isIncoming:" +
                isIncoming + " checked number:" + checkedNumber + " inValid:" +
                isDataInValid + " nameIsNumber" + nameIsNumber);
        return checkedNumber;
    }

    private void updatePrimaryDisplayInfo() {
        final CallCardUi ui = getUi();
        if (ui == null) {
            // TODO: May also occur if search result comes back after ui is destroyed. Look into
            // removing that case completely.
            Log.d(TAG, "updatePrimaryDisplayInfo called but ui is null!");
            return;
        }

        if (mPrimary == null) {
            // Clear the primary display info.
            ui.setPrimary(null, null, false, null, null, false, false, false, false);
            return;
        }

        // Hide the contact photo if we are in a video call and the incoming video surface is
        // showing.
        boolean showContactPhoto = !VideoCallPresenter
                .showIncomingVideo(mPrimary.getVideoState(), mPrimary.getState());

        // Call placed through a work phone account.
        boolean hasWorkCallProperty = mPrimary.hasProperty(PROPERTY_ENTERPRISE_CALL);

        if (mPrimary.isConferenceCall()) {
            Log.d(TAG, "Update primary display info for conference call.");

            ui.setPrimary(
                    null /* number */,
                    getConferenceString(mPrimary),
                    false /* nameIsNumber */,
                    null /* label */,
                    getConferencePhoto(mPrimary),
                    false /* isSipCall */,
                    false /* isForwarded */,
                    showContactPhoto,
                    hasWorkCallProperty);
        } else if (mPrimaryContactInfo != null) {
            Log.d(TAG, "Update primary display info for " + mPrimaryContactInfo);

            String name = getNameForCall(mPrimaryContactInfo);
            String number;

            boolean isChildNumberShown = !TextUtils.isEmpty(mPrimary.getChildNumber());
            boolean isForwardedNumberShown = !TextUtils.isEmpty(mPrimary.getLastForwardedNumber());
            boolean isCallSubjectShown = shouldShowCallSubject(mPrimary);

            if (isCallSubjectShown) {
                ui.setCallSubject(mPrimary.getCallSubject());
            } else {
                ui.setCallSubject(null);
            }

            if (isCallSubjectShown) {
                number = null;
            } else if (isChildNumberShown) {
                number = mContext.getString(R.string.child_number, mPrimary.getChildNumber());
            } else if (isForwardedNumberShown) {
                // Use last forwarded number instead of second line, if present.
                number = mPrimary.getLastForwardedNumber();
            } else {
                number = getNumberForCall(mPrimaryContactInfo);
            }

            ui.showForwardIndicator(isForwardedNumberShown);
            maybeShowHdAudioIcon();

            boolean nameIsNumber = name != null && name.equals(mPrimaryContactInfo.number);
            // Call with caller that is a work contact.
            boolean isWorkContact = (mPrimaryContactInfo.userType == ContactsUtils.USER_TYPE_WORK);
            boolean isIncoming = mPrimary.getState() == Call.State.INCOMING;
            boolean isForwarded = isForwarded(mPrimary);
            boolean isWithPrefix = mContext.getResources().
                getBoolean(R.bool.phone_number_with_intl_prefix);
            if(isWithPrefix == true){
                name = checkIdp(name, nameIsNumber, isIncoming);
            }
            ui.setPrimary(
                    number,
                    name,
                    nameIsNumber,
                    isChildNumberShown || isCallSubjectShown ? null : mPrimaryContactInfo.label,
                    mPrimaryContactInfo.photo,
                    mPrimaryContactInfo.isSipCall,
                    isForwarded,
                    showContactPhoto,
                    hasWorkCallProperty || isWorkContact);

            updateContactInteractions();
        } else {
            // Clear the primary display info.
            ui.setPrimary(null, null, false, null, null, false, false, false, false);
        }

        if (mEmergencyCallListener != null) {
            boolean isEmergencyCall = mPrimary.isEmergencyCall();
            mEmergencyCallListener.onCallUpdated((BaseFragment) ui, isEmergencyCall);
        }
    }

    private void updateSecondaryDisplayInfo() {
        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }

        if (mSecondary == null) {
            // Clear the secondary display info.
            ui.setSecondary(false, null, false, null, null, false /* isConference */,
                    false /* isVideoCall */, mIsFullscreen);
            return;
        }

        if (mSecondary.isConferenceCall()) {
            ui.setSecondary(
                    true /* show */,
                    getConferenceString(mSecondary),
                    false /* nameIsNumber */,
                    null /* label */,
                    getCallProviderLabel(mSecondary),
                    true /* isConference */,
                    mSecondary.isVideoCall(mContext),
                    mIsFullscreen);
        } else if (mSecondaryContactInfo != null) {
            Log.d(TAG, "updateSecondaryDisplayInfo() " + mSecondaryContactInfo);
            String name = getNameForCall(mSecondaryContactInfo);
            boolean nameIsNumber = name != null && name.equals(mSecondaryContactInfo.number);
            ui.setSecondary(
                    true /* show */,
                    name,
                    nameIsNumber,
                    mSecondaryContactInfo.label,
                    getCallProviderLabel(mSecondary),
                    false /* isConference */,
                    mSecondary.isVideoCall(mContext),
                    mIsFullscreen);
        } else {
            // Clear the secondary display info.
            ui.setSecondary(false, null, false, null, null, false /* isConference */,
                    false /* isVideoCall */, mIsFullscreen);
        }
    }


    /**
     * Gets the phone account to display for a call.
     */
    private PhoneAccount getAccountForCall(Call call) {
        PhoneAccountHandle accountHandle = call.getAccountHandle();
        if (accountHandle == null) {
            return null;
        }
        return TelecomManagerCompat.getPhoneAccount(
                InCallPresenter.getInstance().getTelecomManager(),
                accountHandle);
    }

    /**
     * Returns the gateway number for any existing outgoing call.
     */
    private String getGatewayNumber() {
        if (hasOutgoingGatewayCall()) {
            return getNumberFromHandle(mPrimary.getGatewayInfo().getGatewayAddress());
        }
        return null;
    }

    /**
     * Return the string label to represent the call provider
     */
    private String getCallProviderLabel(Call call) {
        PhoneAccount account = getAccountForCall(call);
        TelecomManager mgr = InCallPresenter.getInstance().getTelecomManager();
        if (account != null && !TextUtils.isEmpty(account.getLabel())
                && TelecomManagerCompat.getCallCapablePhoneAccounts(mgr).size() >= 1) {
            return account.getLabel().toString();
        }
        return null;
    }

    /**
     * Return the icon to represent the call provider
     */
    private Drawable getCallProviderIcon(Call call) {
        PhoneAccount account = getAccountForCall(call);
        TelecomManager mgr = InCallPresenter.getInstance().getTelecomManager();
        if (account != null && account.getIcon()!= null &&
                mgr.getCallCapablePhoneAccounts().size() > 1 &&
                SubscriptionManager.from(mContext).getActiveSubscriptionInfoCount() > 1) {
            return account.getIcon().loadDrawable(mContext);
        }
        return null;
    }

    /**
     * Returns the label (line of text above the number/name) for any given call.
     * For example, "calling via [Account/Google Voice]" for outgoing calls.
     */
    private String getConnectionLabel() {
        StatusHints statusHints = mPrimary.getTelecomCall().getDetails().getStatusHints();
        if (statusHints != null && !TextUtils.isEmpty(statusHints.getLabel())) {
            return statusHints.getLabel().toString();
        }

        if (hasOutgoingGatewayCall() && getUi() != null) {
            // Return the label for the gateway app on outgoing calls.
            final PackageManager pm = mContext.getPackageManager();
            try {
                ApplicationInfo info = pm.getApplicationInfo(
                        mPrimary.getGatewayInfo().getGatewayProviderPackageName(), 0);
                return pm.getApplicationLabel(info).toString();
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(this, "Gateway Application Not Found.", e);
                return null;
            }
        }
        return getCallProviderLabel(mPrimary);
    }

    private Drawable getCallStateIcon() {
        // Return connection icon if one exists.
        StatusHints statusHints = mPrimary.getTelecomCall().getDetails().getStatusHints();
        if (statusHints != null && statusHints.getIcon() != null) {
            Drawable icon = statusHints.getIcon().loadDrawable(mContext);
            if (icon != null) {
                return icon;
            }
        }

        return getCallProviderIcon(mPrimary);
    }

    private boolean hasOutgoingGatewayCall() {
        // We only display the gateway information while STATE_DIALING so return false for any other
        // call state.
        // TODO: mPrimary can be null because this is called from updatePrimaryDisplayInfo which
        // is also called after a contact search completes (call is not present yet).  Split the
        // UI update so it can receive independent updates.
        if (mPrimary == null) {
            return false;
        }
        return Call.State.isDialing(mPrimary.getState()) && mPrimary.getGatewayInfo() != null &&
                !mPrimary.getGatewayInfo().isEmpty();
    }

    /**
     * Gets the name to display for the call.
     */
    @NeededForTesting
    String getNameForCall(ContactCacheEntry contactInfo) {
        String preferredName = ContactDisplayUtils.getPreferredDisplayName(
                contactInfo.namePrimary,
                contactInfo.nameAlternative,
                mContactsPreferences);
        if (TextUtils.isEmpty(preferredName)) {
            return contactInfo.number;
        }
        return preferredName;
    }

    /**
     * Gets the number to display for a call.
     */
    @NeededForTesting
    String getNumberForCall(ContactCacheEntry contactInfo) {
        // If the name is empty, we use the number for the name...so don't show a second
        // number in the number field
        String preferredName = ContactDisplayUtils.getPreferredDisplayName(
                    contactInfo.namePrimary,
                    contactInfo.nameAlternative,
                    mContactsPreferences);
        if (TextUtils.isEmpty(preferredName)) {
            return contactInfo.location;
        }
        return contactInfo.number;
    }

    public void secondaryInfoClicked() {
        if (mSecondary == null) {
            Log.w(this, "Secondary info clicked but no secondary call.");
            return;
        }

        Log.i(this, "Swapping call to foreground: " + mSecondary);
        TelecomAdapter.getInstance().unholdCall(mSecondary.getId());
    }

    public void endCallClicked() {
        if (mPrimary == null) {
            return;
        }

        Log.i(this, "Disconnecting call: " + mPrimary);
        final String callId = mPrimary.getId();
        mPrimary.setState(Call.State.DISCONNECTING);
        CallList.getInstance().onUpdate(mPrimary);
        TelecomAdapter.getInstance().disconnectCall(callId);
    }

    private String getNumberFromHandle(Uri handle) {
        return handle == null ? "" : handle.getSchemeSpecificPart();
    }

    /**
     * Handles a change to the fullscreen mode of the in-call UI.
     *
     * @param isFullscreenMode {@code True} if the in-call UI is entering full screen mode.
     */
    @Override
    public void onFullscreenModeChanged(boolean isFullscreenMode) {
        mIsFullscreen = isFullscreenMode;
        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }
        ui.setCallCardVisible(!isFullscreenMode);
        ui.setSecondaryInfoVisible(!isFullscreenMode);
        maybeShowManageConferenceCallButton();
    }

    @Override
    public void onSecondaryCallerInfoVisibilityChanged(boolean isVisible, int height) {
        // No-op - the Call Card is the origin of this event.
    }

    private boolean isPrimaryCallActive() {
        return mPrimary != null && mPrimary.getState() == Call.State.ACTIVE;
    }

    private String getConferenceString(Call call) {
        boolean isGenericConference = call.hasProperty(Details.PROPERTY_GENERIC_CONFERENCE);
        Log.v(this, "getConferenceString: " + isGenericConference);

        final int resId = isGenericConference
                ? R.string.card_title_in_call : R.string.card_title_conf_call;
        return mContext.getResources().getString(resId);
    }

    private Drawable getConferencePhoto(Call call) {
        boolean isGenericConference = call.hasProperty(Details.PROPERTY_GENERIC_CONFERENCE);
        Log.v(this, "getConferencePhoto: " + isGenericConference);

        final int resId = isGenericConference
                ? R.drawable.img_phone : R.drawable.img_conference;
        Drawable photo = mContext.getResources().getDrawable(resId);
        photo.setAutoMirrored(true);
        return photo;
    }

    private boolean shouldShowEndCallButton(Call primary, int callState) {
        if (primary == null) {
            return false;
        }
        if ((!Call.State.isConnectingOrConnected(callState)
                && callState != Call.State.DISCONNECTING) || callState == Call.State.INCOMING) {
            return false;
        }
        if (mPrimary.getSessionModificationState()
                == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
            return false;
        }
        return true;
    }

    private void maybeSendAccessibilityEvent(InCallState oldState, InCallState newState,
                                             boolean primaryChanged) {
        if (mContext == null) {
            return;
        }
        final AccessibilityManager am = (AccessibilityManager) mContext.getSystemService(
                Context.ACCESSIBILITY_SERVICE);
        if (!am.isEnabled()) {
            return;
        }
        // Announce the current call if it's new incoming/outgoing call or primary call is changed
        // due to switching calls between two ongoing calls (one is on hold).
        if ((oldState != InCallState.OUTGOING && newState == InCallState.OUTGOING)
                || (oldState != InCallState.INCOMING && newState == InCallState.INCOMING)
                || primaryChanged) {
            if (getUi() != null) {
                getUi().sendAccessibilityAnnouncement();
            }
        }
    }

    /**
     * Determines whether the call subject should be visible on the UI.  For the call subject to be
     * visible, the call has to be in an incoming or waiting state, and the subject must not be
     * empty.
     *
     * @param call The call.
     * @return {@code true} if the subject should be shown, {@code false} otherwise.
     */
    private boolean shouldShowCallSubject(Call call) {
        if (call == null) {
            return false;
        }

        boolean isIncomingOrWaiting = mPrimary.getState() == Call.State.INCOMING ||
                mPrimary.getState() == Call.State.CALL_WAITING;
        return isIncomingOrWaiting && !TextUtils.isEmpty(call.getCallSubject()) &&
                call.getNumberPresentation() == TelecomManager.PRESENTATION_ALLOWED &&
                call.isCallSubjectSupported();
    }

    /**
     * Determines whether the "note sent" toast should be shown.  It should be shown for a new
     * outgoing call with a subject.
     *
     * @param call The call
     * @return {@code true} if the toast should be shown, {@code false} otherwise.
     */
    private boolean shouldShowNoteSentToast(Call call) {
        return call != null && hasCallSubject(call) && (call.getState() == Call.State.DIALING
                || call.getState() == Call.State.CONNECTING);
    }

    private static boolean hasCallSubject(Call call) {
        return !TextUtils.isEmpty(call.getTelecomCall().getDetails().getIntentExtras()
                .getString(TelecomManager.EXTRA_CALL_SUBJECT));
    }

    public interface CallCardUi extends Ui {
        void setVisible(boolean on);
        void setContactContextTitle(View listHeaderView);
        void setContactContextContent(ListAdapter listAdapter);
        void showContactContext(boolean show);
        void setCallCardVisible(boolean visible);
        void setPrimary(String number, String name, boolean nameIsNumber, String label,
                Drawable photo, boolean isSipCall, boolean isForwarded,
                boolean isContactPhotoShown, boolean isWorkCall);
        void setSecondary(boolean show, String name, boolean nameIsNumber, String label,
                String providerLabel, boolean isConference, boolean isVideoCall,
                boolean isFullscreen);
        void setSecondaryInfoVisible(boolean visible);
        void setCallState(int state, int videoState, int sessionModificationState,
                DisconnectCause disconnectCause, String connectionLabel,
                Drawable connectionIcon, String gatewayNumber, boolean isWifi,
                boolean isConference, boolean isWaitingForRemoteSide, boolean isWorkCall);
        void setPrimaryCallElapsedTime(boolean show, long duration);
        void setPrimaryName(String name, boolean nameIsNumber);
        void setPrimaryImage(Drawable image, boolean isVisible);
        void setPrimaryPhoneNumber(String phoneNumber);
        void setPrimaryLabel(String label);
        void setEndCallButtonEnabled(boolean enabled, boolean animate);
        void setCallbackNumber(String number, boolean isEmergencyCalls);
        void setCallSubject(String callSubject);
        void setProgressSpinnerVisible(boolean visible);
        void showHdAudioIndicator(boolean visible);
        void showForwardIndicator(boolean visible);
        void showManageConferenceCallButton(boolean visible);
        boolean isManageConferenceVisible();
        boolean isCallSubjectVisible();
        void animateForNewOutgoingCall();
        void sendAccessibilityAnnouncement();
        void showNoteSentToast();
    }
}
