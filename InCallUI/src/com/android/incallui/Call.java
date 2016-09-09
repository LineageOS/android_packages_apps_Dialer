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
 * limitations under the License.
 */

package com.android.incallui;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.net.Uri;
import android.os.Bundle;
import android.os.Trace;
import android.os.RemoteException;
import android.telecom.Call.Details;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.GatewayInfo;
import android.telecom.InCallService.VideoCall;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import android.telecom.VideoProfile;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.compat.SdkVersionOverride;
import com.android.contacts.common.compat.telecom.TelecomManagerCompat;
import com.android.contacts.common.testing.NeededForTesting;
import com.android.dialer.util.IntentUtil;
import com.android.incallui.util.TelecomCallUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.codeaurora.ims.internal.IQtiImsExt;
import org.codeaurora.ims.QtiCallConstants;
import org.codeaurora.ims.QtiImsException;
import org.codeaurora.ims.QtiImsExtListenerBaseImpl;
import org.codeaurora.ims.QtiImsExtManager;
import org.codeaurora.ims.utils.QtiImsExtUtils;

/**
 * Describes a single call and its state.
 */
@NeededForTesting
public class Call {
    /* Defines different states of this call */
    public static class State {
        public static final int INVALID = 0;
        public static final int NEW = 1;            /* The call is new. */
        public static final int IDLE = 2;           /* The call is idle.  Nothing active */
        public static final int ACTIVE = 3;         /* There is an active call */
        public static final int INCOMING = 4;       /* A normal incoming phone call */
        public static final int CALL_WAITING = 5;   /* Incoming call while another is active */
        public static final int DIALING = 6;        /* An outgoing call during dial phase */
        public static final int REDIALING = 7;      /* Subsequent dialing attempt after a failure */
        public static final int ONHOLD = 8;         /* An active phone call placed on hold */
        public static final int DISCONNECTING = 9;  /* A call is being ended. */
        public static final int DISCONNECTED = 10;  /* State after a call disconnects */
        public static final int CONFERENCED = 11;   /* Call part of a conference call */
        public static final int SELECT_PHONE_ACCOUNT = 12; /* Waiting for account selection */
        public static final int CONNECTING = 13;    /* Waiting for Telecom broadcast to finish */
        public static final int BLOCKED = 14;       /* The number was found on the block list */


        public static boolean isConnectingOrConnected(int state) {
            switch(state) {
                case ACTIVE:
                case INCOMING:
                case CALL_WAITING:
                case CONNECTING:
                case DIALING:
                case REDIALING:
                case ONHOLD:
                case CONFERENCED:
                    return true;
                default:
            }
            return false;
        }

        public static boolean isDialing(int state) {
            return state == DIALING || state == REDIALING;
        }

        public static String toString(int state) {
            switch (state) {
                case INVALID:
                    return "INVALID";
                case NEW:
                    return "NEW";
                case IDLE:
                    return "IDLE";
                case ACTIVE:
                    return "ACTIVE";
                case INCOMING:
                    return "INCOMING";
                case CALL_WAITING:
                    return "CALL_WAITING";
                case DIALING:
                    return "DIALING";
                case REDIALING:
                    return "REDIALING";
                case ONHOLD:
                    return "ONHOLD";
                case DISCONNECTING:
                    return "DISCONNECTING";
                case DISCONNECTED:
                    return "DISCONNECTED";
                case CONFERENCED:
                    return "CONFERENCED";
                case SELECT_PHONE_ACCOUNT:
                    return "SELECT_PHONE_ACCOUNT";
                case CONNECTING:
                    return "CONNECTING";
                case BLOCKED:
                    return "BLOCKED";
                default:
                    return "UNKNOWN";
            }
        }
    }

    /**
     * Defines different states of session modify requests, which are used to upgrade to video, or
     * downgrade to audio.
     */
    public static class SessionModificationState {
        public static final int NO_REQUEST = 0;
        public static final int WAITING_FOR_RESPONSE = 1;
        public static final int REQUEST_FAILED = 2;
        public static final int RECEIVED_UPGRADE_TO_VIDEO_REQUEST = 3;
        public static final int UPGRADE_TO_VIDEO_REQUEST_TIMED_OUT = 4;
        public static final int REQUEST_REJECTED = 5;
    }

    public static class VideoSettings {
        public static final int CAMERA_DIRECTION_UNKNOWN = -1;
        public static final int CAMERA_DIRECTION_FRONT_FACING =
                CameraCharacteristics.LENS_FACING_FRONT;
        public static final int CAMERA_DIRECTION_BACK_FACING =
                CameraCharacteristics.LENS_FACING_BACK;

        private int mCameraDirection = CAMERA_DIRECTION_UNKNOWN;

        /**
         * Sets the camera direction. if camera direction is set to CAMERA_DIRECTION_UNKNOWN,
         * the video state of the call should be used to infer the camera direction.
         *
         * @see {@link CameraCharacteristics#LENS_FACING_FRONT}
         * @see {@link CameraCharacteristics#LENS_FACING_BACK}
         */
        public void setCameraDir(int cameraDirection) {
            if (cameraDirection == CAMERA_DIRECTION_FRONT_FACING
               || cameraDirection == CAMERA_DIRECTION_BACK_FACING) {
                mCameraDirection = cameraDirection;
            } else {
                mCameraDirection = CAMERA_DIRECTION_UNKNOWN;
            }
        }

        /**
         * Gets the camera direction. if camera direction is set to CAMERA_DIRECTION_UNKNOWN,
         * the video state of the call should be used to infer the camera direction.
         *
         * @see {@link CameraCharacteristics#LENS_FACING_FRONT}
         * @see {@link CameraCharacteristics#LENS_FACING_BACK}
         */
        public int getCameraDir() {
            return mCameraDirection;
        }

        @Override
        public String toString() {
            return "(CameraDir:" + getCameraDir() + ")";
        }
    }

    /**
     * Tracks any state variables that is useful for logging. There is some amount of overlap with
     * existing call member variables, but this duplication helps to ensure that none of these
     * logging variables will interface with/and affect call logic.
     */
    public static class LogState {

        // Contact lookup type constants
        // Unknown lookup result (lookup not completed yet?)
        public static final int LOOKUP_UNKNOWN = 0;
        public static final int LOOKUP_NOT_FOUND = 1;
        public static final int LOOKUP_LOCAL_CONTACT = 2;
        public static final int LOOKUP_LOCAL_CACHE = 3;
        public static final int LOOKUP_REMOTE_CONTACT = 4;
        public static final int LOOKUP_EMERGENCY = 5;
        public static final int LOOKUP_VOICEMAIL = 6;

        // Call initiation type constants
        public static final int INITIATION_UNKNOWN = 0;
        public static final int INITIATION_INCOMING = 1;
        public static final int INITIATION_DIALPAD = 2;
        public static final int INITIATION_SPEED_DIAL = 3;
        public static final int INITIATION_REMOTE_DIRECTORY = 4;
        public static final int INITIATION_SMART_DIAL = 5;
        public static final int INITIATION_REGULAR_SEARCH = 6;
        public static final int INITIATION_CALL_LOG = 7;
        public static final int INITIATION_CALL_LOG_FILTER = 8;
        public static final int INITIATION_VOICEMAIL_LOG = 9;
        public static final int INITIATION_CALL_DETAILS = 10;
        public static final int INITIATION_QUICK_CONTACTS = 11;
        public static final int INITIATION_EXTERNAL = 12;

        public DisconnectCause disconnectCause;
        public boolean isIncoming = false;
        public int contactLookupResult = LOOKUP_UNKNOWN;
        public int callInitiationMethod = INITIATION_EXTERNAL;
        // If this was a conference call, the total number of calls involved in the conference.
        public int conferencedCalls = 0;
        public long duration = 0;
        public boolean isLogged = false;

        @Override
        public String toString() {
            return String.format(Locale.US, "["
                        + "%s, " // DisconnectCause toString already describes the object type
                        + "isIncoming: %s, "
                        + "contactLookup: %s, "
                        + "callInitiation: %s, "
                        + "duration: %s"
                        + "]",
                    disconnectCause,
                    isIncoming,
                    lookupToString(contactLookupResult),
                    initiationToString(callInitiationMethod),
                    duration);
        }

        private static String lookupToString(int lookupType) {
            switch (lookupType) {
                case LOOKUP_LOCAL_CONTACT:
                    return "Local";
                case LOOKUP_LOCAL_CACHE:
                    return "Cache";
                case LOOKUP_REMOTE_CONTACT:
                    return "Remote";
                case LOOKUP_EMERGENCY:
                    return "Emergency";
                case LOOKUP_VOICEMAIL:
                    return "Voicemail";
                default:
                    return "Not found";
            }
        }

        private static String initiationToString(int initiationType) {
            switch (initiationType) {
                case INITIATION_INCOMING:
                    return "Incoming";
                case INITIATION_DIALPAD:
                    return "Dialpad";
                case INITIATION_SPEED_DIAL:
                    return "Speed Dial";
                case INITIATION_REMOTE_DIRECTORY:
                    return "Remote Directory";
                case INITIATION_SMART_DIAL:
                    return "Smart Dial";
                case INITIATION_REGULAR_SEARCH:
                    return "Regular Search";
                case INITIATION_CALL_LOG:
                    return "Call Log";
                case INITIATION_CALL_LOG_FILTER:
                    return "Call Log Filter";
                case INITIATION_VOICEMAIL_LOG:
                    return "Voicemail Log";
                case INITIATION_CALL_DETAILS:
                    return "Call Details";
                case INITIATION_QUICK_CONTACTS:
                    return "Quick Contacts";
                default:
                    return "Unknown";
            }
        }
    }


    private static final String ID_PREFIX = Call.class.getSimpleName() + "_";
    private static int sIdCounter = 0;

    private final android.telecom.Call.Callback mTelecomCallCallback =
        new android.telecom.Call.Callback() {
            @Override
            public void onStateChanged(android.telecom.Call call, int newState) {
                Log.d(this, "TelecomCallCallback onStateChanged call=" + call + " newState="
                        + newState);
                update();
            }

            @Override
            public void onParentChanged(android.telecom.Call call,
                    android.telecom.Call newParent) {
                Log.d(this, "TelecomCallCallback onParentChanged call=" + call + " newParent="
                        + newParent);
                update();
            }

            @Override
            public void onChildrenChanged(android.telecom.Call call,
                    List<android.telecom.Call> children) {
                update();
            }

            @Override
            public void onDetailsChanged(android.telecom.Call call,
                    android.telecom.Call.Details details) {
                Log.d(this, "TelecomCallCallback onStateChanged call=" + call + " details="
                        + details);
                update();
            }

            @Override
            public void onCannedTextResponsesLoaded(android.telecom.Call call,
                    List<String> cannedTextResponses) {
                Log.d(this, "TelecomCallCallback onStateChanged call=" + call
                        + " cannedTextResponses=" + cannedTextResponses);
                update();
            }

            @Override
            public void onPostDialWait(android.telecom.Call call,
                    String remainingPostDialSequence) {
                Log.d(this, "TelecomCallCallback onStateChanged call=" + call
                        + " remainingPostDialSequence=" + remainingPostDialSequence);
                update();
            }

            @Override
            public void onVideoCallChanged(android.telecom.Call call,
                    VideoCall videoCall) {
                Log.d(this, "TelecomCallCallback onStateChanged call=" + call + " videoCall="
                        + videoCall);
                update();
            }

            @Override
            public void onCallDestroyed(android.telecom.Call call) {
                Log.d(this, "TelecomCallCallback onStateChanged call=" + call);
                call.unregisterCallback(this);
            }

            @Override
            public void onConferenceableCallsChanged(android.telecom.Call call,
                    List<android.telecom.Call> conferenceableCalls) {
                update();
            }
    };

    boolean mIsActiveSub = false;
    public static final String ACTIVE_SUBSCRIPTION = "active_sub";
    private android.telecom.Call mTelecomCall;
    private boolean mIsEmergencyCall;
    private Uri mHandle;
    private final String mId;
    private int mState = State.INVALID;
    private DisconnectCause mDisconnectCause;
    private int mSessionModificationState;
    private final List<String> mChildCallIds = new ArrayList<>();
    private final VideoSettings mVideoSettings = new VideoSettings();
    private int mVideoState;

    /**
     * mRequestedVideoState is used to store requested upgrade / downgrade video state
     */
    private int mRequestedVideoState = VideoProfile.STATE_AUDIO_ONLY;

    private InCallVideoCallCallback mVideoCallCallback;
    private boolean mIsVideoCallCallbackRegistered;
    private String mChildNumber;
    private String mLastForwardedNumber;
    private String mCallSubject;
    private PhoneAccountHandle mPhoneAccountHandle;
    private boolean mIsOutgoing = false;

    /**
     * Indicates whether the phone account associated with this call supports specifying a call
     * subject.
     */
    private boolean mIsCallSubjectSupported;

    private long mTimeAddedMs;

    private LogState mLogState = new LogState();

    /**
     * Used only to create mock calls for testing
     */
    @NeededForTesting
    Call(int state) {
        mTelecomCall = null;
        mId = ID_PREFIX + Integer.toString(sIdCounter++);
        setState(state);
    }

    public Call(android.telecom.Call telecomCall) {
        mTelecomCall = telecomCall;
        mId = ID_PREFIX + Integer.toString(sIdCounter++);

        updateFromTelecomCall();

        mTelecomCall.registerCallback(mTelecomCallCallback);

        mTimeAddedMs = System.currentTimeMillis();
    }

    public android.telecom.Call getTelecomCall() {
        return mTelecomCall;
    }

    /**
     * @return video settings of the call, null if the call is not a video call.
     * @see VideoProfile
     */
    public VideoSettings getVideoSettings() {
        return mVideoSettings;
    }

    private void update() {
        Trace.beginSection("Update");
        int oldState = getState();
        updateFromTelecomCall();
        if (oldState != getState() && getState() == Call.State.DISCONNECTED) {
            CallList.getInstance().onDisconnect(this);
        } else {
            CallList.getInstance().onUpdate(this);
        }
        Trace.endSection();
    }

    private void updateFromTelecomCall() {
        Log.d(this, "updateFromTelecomCall: " + mTelecomCall.toString());
        final int translatedState = translateState(mTelecomCall.getState());
        if (mState != State.BLOCKED) {
            setState(translatedState);
            setDisconnectCause(mTelecomCall.getDetails().getDisconnectCause());
            maybeCancelVideoUpgrade(mTelecomCall.getDetails().getVideoState());
        }

        if (mTelecomCall.getVideoCall() != null) {
            if (mVideoCallCallback == null) {
                mVideoCallCallback = new InCallVideoCallCallback(this);
            }
            mTelecomCall.getVideoCall().registerCallback(mVideoCallCallback);
            mIsVideoCallCallbackRegistered = true;
        }

        mChildCallIds.clear();
        final int numChildCalls = mTelecomCall.getChildren().size();
        for (int i = 0; i < numChildCalls; i++) {
            mChildCallIds.add(
                    CallList.getInstance().getCallByTelecomCall(
                            mTelecomCall.getChildren().get(i)).getId());
        }

        // The number of conferenced calls can change over the course of the call, so use the
        // maximum number of conferenced child calls as the metric for conference call usage.
        mLogState.conferencedCalls = Math.max(numChildCalls, mLogState.conferencedCalls);

        updateFromCallExtras(mTelecomCall.getDetails().getExtras());

        // If the handle of the call has changed, update state for the call determining if it is an
        // emergency call.
        Uri newHandle = mTelecomCall.getDetails().getHandle();
        if (!Objects.equals(mHandle, newHandle)) {
            mHandle = newHandle;
            updateEmergencyCallState();
        }

        // If the phone account handle of the call is set, cache capability bit indicating whether
        // the phone account supports call subjects.
        PhoneAccountHandle newPhoneAccountHandle = mTelecomCall.getDetails().getAccountHandle();
        if (!Objects.equals(mPhoneAccountHandle, newPhoneAccountHandle)) {
            mPhoneAccountHandle = newPhoneAccountHandle;

            if (mPhoneAccountHandle != null) {
                TelecomManager mgr = InCallPresenter.getInstance().getTelecomManager();
                PhoneAccount phoneAccount =
                        TelecomManagerCompat.getPhoneAccount(mgr, mPhoneAccountHandle);
                if (phoneAccount != null) {
                    mIsCallSubjectSupported = phoneAccount.hasCapabilities(
                            PhoneAccount.CAPABILITY_CALL_SUBJECT);
                }
            }
        }
    }

    /**
     * Tests corruption of the {@code callExtras} bundle by calling {@link
     * Bundle#containsKey(String)}. If the bundle is corrupted a {@link IllegalArgumentException}
     * will be thrown and caught by this function.
     *
     * @param callExtras the bundle to verify
     * @returns {@code true} if the bundle is corrupted, {@code false} otherwise.
     */
    protected boolean areCallExtrasCorrupted(Bundle callExtras) {
        /**
         * There's currently a bug in Telephony service (b/25613098) that could corrupt the
         * extras bundle, resulting in a IllegalArgumentException while validating data under
         * {@link Bundle#containsKey(String)}.
         */
        try {
            callExtras.containsKey(Connection.EXTRA_CHILD_ADDRESS);
            return false;
        } catch (IllegalArgumentException e) {
            Log.e(this, "CallExtras is corrupted, ignoring exception", e);
            return true;
        }
    }

    protected void updateFromCallExtras(Bundle callExtras) {
        if (callExtras == null || areCallExtrasCorrupted(callExtras)) {
            /**
             * If the bundle is corrupted, abandon information update as a work around. These are
             * not critical for the dialer to function.
             */
            return;
        }
        // Check for a change in the child address and notify any listeners.
        if (callExtras.containsKey(Connection.EXTRA_CHILD_ADDRESS)) {
            String childNumber = callExtras.getString(Connection.EXTRA_CHILD_ADDRESS);
            if (!Objects.equals(childNumber, mChildNumber)) {
                mChildNumber = childNumber;
                CallList.getInstance().onChildNumberChange(this);
            }
        }

        // Last forwarded number comes in as an array of strings.  We want to choose the
        // last item in the array.  The forwarding numbers arrive independently of when the
        // call is originally set up, so we need to notify the the UI of the change.
        if (callExtras.containsKey(Connection.EXTRA_LAST_FORWARDED_NUMBER)) {
            ArrayList<String> lastForwardedNumbers =
                    callExtras.getStringArrayList(Connection.EXTRA_LAST_FORWARDED_NUMBER);

            if (lastForwardedNumbers != null) {
                String lastForwardedNumber = null;
                if (!lastForwardedNumbers.isEmpty()) {
                    lastForwardedNumber = lastForwardedNumbers.get(
                            lastForwardedNumbers.size() - 1);
                }

                if (!Objects.equals(lastForwardedNumber, mLastForwardedNumber)) {
                    mLastForwardedNumber = lastForwardedNumber;
                    CallList.getInstance().onLastForwardedNumberChange(this);
                }
            }
        }

        // Call subject is present in the extras at the start of call, so we do not need to
        // notify any other listeners of this.
        if (callExtras.containsKey(Connection.EXTRA_CALL_SUBJECT)) {
            String callSubject = callExtras.getString(Connection.EXTRA_CALL_SUBJECT);
            if (!Objects.equals(mCallSubject, callSubject)) {
                mCallSubject = callSubject;
            }
        }
        if (callExtras.containsKey(ACTIVE_SUBSCRIPTION)) {
            mIsActiveSub = callExtras.getBoolean(ACTIVE_SUBSCRIPTION);
        }
    }

    /**
     * Determines if a received upgrade to video request should be cancelled.  This can happen if
     * another InCall UI responds to the upgrade to video request.
     *
     * @param newVideoState The new video state.
     */
    private void maybeCancelVideoUpgrade(int newVideoState) {
        boolean isVideoStateChanged = mVideoState != newVideoState;

        if (mSessionModificationState == SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST
                && isVideoStateChanged) {

            Log.v(this, "maybeCancelVideoUpgrade : cancelling upgrade notification");
            setSessionModificationState(SessionModificationState.NO_REQUEST);
        }
        mVideoState = newVideoState;
    }
    private static int translateState(int state) {
        switch (state) {
            case android.telecom.Call.STATE_NEW:
            case android.telecom.Call.STATE_CONNECTING:
                return Call.State.CONNECTING;
            case android.telecom.Call.STATE_SELECT_PHONE_ACCOUNT:
                return Call.State.SELECT_PHONE_ACCOUNT;
            case android.telecom.Call.STATE_DIALING:
                return Call.State.DIALING;
            case android.telecom.Call.STATE_RINGING:
                return Call.State.INCOMING;
            case android.telecom.Call.STATE_ACTIVE:
                return Call.State.ACTIVE;
            case android.telecom.Call.STATE_HOLDING:
                return Call.State.ONHOLD;
            case android.telecom.Call.STATE_DISCONNECTED:
                return Call.State.DISCONNECTED;
            case android.telecom.Call.STATE_DISCONNECTING:
                return Call.State.DISCONNECTING;
            default:
                return Call.State.INVALID;
        }
    }

    public String getId() {
        return mId;
    }

    public long getTimeAddedMs() {
        return mTimeAddedMs;
    }

    public String getNumber() {
        return TelecomCallUtil.getNumber(mTelecomCall);
    }

    public void blockCall() {
        mTelecomCall.reject(false, null);
        setState(State.BLOCKED);
    }

    public Uri getHandle() {
        return mTelecomCall == null ? null : mTelecomCall.getDetails().getHandle();
    }

    public boolean isEmergencyCall() {
        return mIsEmergencyCall;
    }

    public int getState() {
        if (mTelecomCall != null && mTelecomCall.getParent() != null) {
            return State.CONFERENCED;
        } else {
            return mState;
        }
    }

    public int getTrueState(){
        return mState;
    }

    public void setState(int state) {
        mState = state;
        if (mState == State.INCOMING) {
            mLogState.isIncoming = true;
        } else if (mState == State.DISCONNECTED) {
            mLogState.duration = getConnectTimeMillis() == 0 ?
                    0: System.currentTimeMillis() - getConnectTimeMillis();
        } else if (mState == State.DIALING || mState == State.CONNECTING) {
            mIsOutgoing = true;
        }
    }

    public boolean isOutgoing() {
        return mIsOutgoing;
    }

    public int getNumberPresentation() {
        return mTelecomCall == null ? null : mTelecomCall.getDetails().getHandlePresentation();
    }

    public int getCnapNamePresentation() {
        return mTelecomCall == null ? null
                : mTelecomCall.getDetails().getCallerDisplayNamePresentation();
    }

    public String getCnapName() {
        return mTelecomCall == null ? null
                : getTelecomCall().getDetails().getCallerDisplayName();
    }

    public Bundle getIntentExtras() {
        return mTelecomCall.getDetails().getIntentExtras();
    }

    public Bundle getExtras() {
        return mTelecomCall == null ? null : mTelecomCall.getDetails().getExtras();
    }

    /**
     * @return The child number for the call, or {@code null} if none specified.
     */
    public String getChildNumber() {
        return mChildNumber;
    }

    /**
     * @return The last forwarded number for the call, or {@code null} if none specified.
     */
    public String getLastForwardedNumber() {
        return mLastForwardedNumber;
    }

    /**
     * @return The call subject, or {@code null} if none specified.
     */
    public String getCallSubject() {
        return mCallSubject;
    }

    /**
     * @return {@code true} if the call's phone account supports call subjects, {@code false}
     *      otherwise.
     */
    public boolean isCallSubjectSupported() {
        return mIsCallSubjectSupported;
    }

    /** Returns call disconnect cause, defined by {@link DisconnectCause}. */
    public DisconnectCause getDisconnectCause() {
        if (mState == State.DISCONNECTED || mState == State.IDLE) {
            return mDisconnectCause;
        }

        return new DisconnectCause(DisconnectCause.UNKNOWN);
    }

    public void setDisconnectCause(DisconnectCause disconnectCause) {
        mDisconnectCause = disconnectCause;
        mLogState.disconnectCause = mDisconnectCause;
    }

    /** Returns the possible text message responses. */
    public List<String> getCannedSmsResponses() {
        return mTelecomCall.getCannedTextResponses();
    }

    /** Checks if the call supports the given set of capabilities supplied as a bit mask. */
    public boolean can(int capabilities) {
        int supportedCapabilities = mTelecomCall.getDetails().getCallCapabilities();

        if ((capabilities & android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0) {
            if (CallList.getInstance().isDsdaEnabled()) {
                List<android.telecom.Call> conferenceableCalls =
                        mTelecomCall.getConferenceableCalls();
                boolean hasConferenceableCall = false;
                if (!conferenceableCalls.isEmpty()){
                    int subId = getSubId();
                    for (android.telecom.Call call : conferenceableCalls) {
                        PhoneAccountHandle phHandle = call.getDetails().getAccountHandle();
                        if ((phHandle != null) && ((Integer.parseInt(phHandle.getId())) == subId)) {
                            hasConferenceableCall = true;
                            break;
                        }
                    }
                }
                if (!hasConferenceableCall &&
                    ((android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE
                            & supportedCapabilities) == 0)) {
                    // Cannot merge calls if there are no calls to merge with.
                    return false;
                }
            // We allow you to merge if the capabilities allow it or if it is a call with
            // conferenceable calls.
            } else if (mTelecomCall.getConferenceableCalls().isEmpty() &&
                ((android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE
                        & supportedCapabilities) == 0)) {
                // Cannot merge calls if there are no calls to merge with.
                return false;
            }
            capabilities &= ~android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE;
        }
        return (capabilities == (capabilities & mTelecomCall.getDetails().getCallCapabilities()));
    }

    public boolean hasProperty(int property) {
        return mTelecomCall.getDetails().hasProperty(property);
    }

    /** Gets the time when the call first became active. */
    public long getConnectTimeMillis() {
        return mTelecomCall.getDetails().getConnectTimeMillis();
    }

    public boolean isConferenceCall() {
        return hasProperty(android.telecom.Call.Details.PROPERTY_CONFERENCE);
    }

    public boolean isForwarded() {
        return hasProperty(android.telecom.Call.Details.PROPERTY_WAS_FORWARDED);
    }

    public boolean isWaitingForRemoteSide() {
        if (mState == State.ACTIVE
                && hasProperty(android.telecom.Call.Details.PROPERTY_HELD_REMOTELY)) {
            return true;
        }
        if (mState == State.DIALING
                && hasProperty(android.telecom.Call.Details.PROPERTY_DIALING_IS_WAITING)) {
            return true;
        }
        return false;
    }

    public boolean wasUnansweredForwarded() {
        return getDisconnectCause().getCode() == DisconnectCause.MISSED
                && hasProperty(android.telecom.Call.Details.PROPERTY_ADDITIONAL_CALL_FORWARDED);
    }

    public boolean missedBecauseIncomingCallsBarredRemotely() {
        return getDisconnectCause().getCode() == DisconnectCause.RESTRICTED
                && hasProperty(android.telecom.Call.Details.PROPERTY_REMOTE_INCOMING_CALLS_BARRED);
    }

    public GatewayInfo getGatewayInfo() {
        return mTelecomCall == null ? null : mTelecomCall.getDetails().getGatewayInfo();
    }

    public PhoneAccountHandle getAccountHandle() {
        return mTelecomCall == null ? null : mTelecomCall.getDetails().getAccountHandle();
    }

    public int getSubId() {
        PhoneAccountHandle ph = getAccountHandle();
        if (ph != null) {
            try {
                if (ph.getId() != null) {
                    return Integer.parseInt(getAccountHandle().getId());
                }
            } catch (NumberFormatException e) {
                Log.w(this, "sub id is not a number" + e);
            }
            return SubscriptionManager.getDefaultVoiceSubscriptionId();
        } else {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
    }

    /**
     * @return The {@link VideoCall} instance associated with the {@link android.telecom.Call}.
     *      Will return {@code null} until {@link #updateFromTelecomCall()} has registered a valid
     *      callback on the {@link VideoCall}.
     */
    public VideoCall getVideoCall() {
        return mTelecomCall == null || !mIsVideoCallCallbackRegistered ? null
                : mTelecomCall.getVideoCall();
    }

    public List<String> getChildCallIds() {
        return mChildCallIds;
    }

    public String getParentId() {
        android.telecom.Call parentCall = mTelecomCall.getParent();
        if (parentCall != null) {
            return CallList.getInstance().getCallByTelecomCall(parentCall).getId();
        }
        return null;
    }

    public int getVideoState() {
        return mTelecomCall.getDetails().getVideoState();
    }

    public boolean isVideoCall(Context context) {
        return CallUtil.isVideoEnabled(context) &&
                VideoUtils.isVideoCall(getVideoState());
    }

    /**
     * Handles incoming session modification requests.  Stores the pending video request and sets
     * the session modification state to
     * {@link SessionModificationState#RECEIVED_UPGRADE_TO_VIDEO_REQUEST} so that we can keep track
     * of the fact the request was received.  Only upgrade requests require user confirmation and
     * will be handled by this method.  The remote user can turn off their own camera without
     * confirmation.
     *
     * @param videoState The requested video state.
     */
    public void setRequestedVideoState(int videoState) {
        Log.d(this, "setRequestedVideoState - video state= " + videoState);
        if (videoState == getVideoState()) {
            mSessionModificationState = Call.SessionModificationState.NO_REQUEST;
            Log.w(this,"setRequestedVideoState - Clearing session modification state");
            return;
        }

        mSessionModificationState = Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST;
        mRequestedVideoState = videoState;
        CallList.getInstance().onUpgradeToVideo(this);

        Log.d(this, "setRequestedVideoState - mSessionModificationState="
            + mSessionModificationState + " video state= " + videoState);
        update();
    }

    /**
     * Set the session modification state.  Used to keep track of pending video session modification
     * operations and to inform listeners of these changes.
     *
     * @param state the new session modification state.
     */
    public void setSessionModificationState(int state) {
        boolean hasChanged = mSessionModificationState != state;
        mSessionModificationState = state;
        Log.d(this, "setSessionModificationState " + state + " mSessionModificationState="
                + mSessionModificationState);
        if (hasChanged) {
            CallList.getInstance().onSessionModificationStateChange(this, state);
        }
    }

    /**
     * Determines if the call handle is an emergency number or not and caches the result to avoid
     * repeated calls to isEmergencyNumber.
     */
    private void updateEmergencyCallState() {
        Uri handle = mTelecomCall.getDetails().getHandle();
        mIsEmergencyCall = QtiCallUtils.isEmergencyNumber
                (handle == null ? "" : handle.getSchemeSpecificPart());
    }

    /**
     * Gets the video state which was requested via a session modification request.
     *
     * @return The video state.
     */
    public int getRequestedVideoState() {
        return mRequestedVideoState;
    }

    public static boolean areSame(Call call1, Call call2) {
        if (call1 == null && call2 == null) {
            return true;
        } else if (call1 == null || call2 == null) {
            return false;
        }

        // otherwise compare call Ids
        return call1.getId().equals(call2.getId());
    }

    public static boolean areSameNumber(Call call1, Call call2) {
        if (call1 == null && call2 == null) {
            return true;
        } else if (call1 == null || call2 == null) {
            return false;
        }

        // otherwise compare call Numbers
        return TextUtils.equals(call1.getNumber(), call2.getNumber());
    }

    /**
     *  Gets the current video session modification state.
     *
     * @return The session modification state.
     */
    public int getSessionModificationState() {
        return mSessionModificationState;
    }

    /* QtiImsExtListenerBaseImpl instance to handle call transfer response */
    private QtiImsExtListenerBaseImpl mQtiImsInterfaceListener =
            new QtiImsExtListenerBaseImpl() {

        /* Handles call transfer response */
        @Override
        public void receiveCallTransferResponse(int result) {
            Log.w(this, "receiveCallTransferResponse: " + result);
        }
    };

    public int getTransferCapabilities() {
        Bundle extras = getExtras();
        return (extras == null)? 0 :
                extras.getInt(QtiImsExtUtils.QTI_IMS_TRANSFER_EXTRA_KEY, 0);
    }

    public boolean sendCallTransferRequest(int type, String number) {
        int phoneId = 0;
        try {
            Log.d(this, "sendCallTransferRequest: Phoneid-" + phoneId + " type-" + type +
                    " number: " + number);
            QtiImsExtManager.getInstance().sendCallTransferRequest(phoneId, type, number,
                    mQtiImsInterfaceListener);
        } catch (QtiImsException e) {
            Log.e(this, "sendCallDeflectRequest exception " + e);
            return false;
        }
        return true;
    }

    public LogState getLogState() {
        return mLogState;
    }

    /**
     * Logging utility methods
     */
    public void logCallInitiationType() {
        if (getState() == State.INCOMING) {
            getLogState().callInitiationMethod = LogState.INITIATION_INCOMING;
        } else if (getIntentExtras() != null) {
            getLogState().callInitiationMethod =
                getIntentExtras().getInt(IntentUtil.EXTRA_CALL_INITIATION_TYPE,
                        LogState.INITIATION_EXTERNAL);
        }
    }

    @Override
    public String toString() {
        if (mTelecomCall == null) {
            // This should happen only in testing since otherwise we would never have a null
            // Telecom call.
            return String.valueOf(mId);
        }

        return String.format(Locale.US, "[%s, %s, %s, children:%s, parent:%s, conferenceable:%s, " +
                "videoState:%s, mSessionModificationState:%d, VideoSettings:%s, mIsActivSub:%b]",
                mId,
                State.toString(getState()),
                Details.capabilitiesToString(mTelecomCall.getDetails().getCallCapabilities()),
                mChildCallIds,
                getParentId(),
                this.mTelecomCall.getConferenceableCalls(),
                VideoProfile.videoStateToString(mTelecomCall.getDetails().getVideoState()),
                mSessionModificationState,
                getVideoSettings(), mIsActiveSub);
    }

    public String toSimpleString() {
        return super.toString();
    }

    public boolean isIncomingConfCall() {
        int callState = getState();
        if (callState == State.INCOMING || callState == State.CALL_WAITING) {
            Bundle extras = getExtras();
            boolean incomingConf = (extras == null)? false :
                    extras.getBoolean(QtiImsExtUtils.QTI_IMS_INCOMING_CONF_EXTRA_KEY, false);
            Log.d(this, "isIncomingConfCall = " + incomingConf);
            return incomingConf;
        }
        return false;
    }

    public int getWifiQuality() {
        Bundle extras = getExtras();
        return (extras == null)? QtiCallConstants.VOWIFI_QUALITY_NONE :
                extras.getInt(QtiCallConstants.VOWIFI_CALL_QUALITY_EXTRA_KEY,
                QtiCallConstants.VOWIFI_QUALITY_NONE);
    }
}
