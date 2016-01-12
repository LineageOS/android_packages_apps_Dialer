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

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.testing.NeededForTesting;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.net.Uri;
import android.os.Bundle;
import android.os.Trace;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.GatewayInfo;
import android.telecom.InCallService.VideoCall;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
        public static final int CONNECTING = 13;    /* Waiting for Telecomm broadcast to finish */


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

        public String toString() {
            return "(CameraDir:" + getCameraDir() + ")";
        }
    }


    private static final String ID_PREFIX = Call.class.getSimpleName() + "_";
    private static int sIdCounter = 0;

    private android.telecom.Call.Callback mTelecomCallCallback =
            new android.telecom.Call.Callback() {
                @Override
                public void onStateChanged(android.telecom.Call call, int newState) {
                    Log.d(this, "TelecommCallCallback onStateChanged call=" + call + " newState="
                            + newState);
                    update();
                }

                @Override
                public void onParentChanged(android.telecom.Call call,
                        android.telecom.Call newParent) {
                    Log.d(this, "TelecommCallCallback onParentChanged call=" + call + " newParent="
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
                    Log.d(this, "TelecommCallCallback onStateChanged call=" + call + " details="
                            + details);
                    update();
                }

                @Override
                public void onCannedTextResponsesLoaded(android.telecom.Call call,
                        List<String> cannedTextResponses) {
                    Log.d(this, "TelecommCallCallback onStateChanged call=" + call
                            + " cannedTextResponses=" + cannedTextResponses);
                    update();
                }

                @Override
                public void onPostDialWait(android.telecom.Call call,
                        String remainingPostDialSequence) {
                    Log.d(this, "TelecommCallCallback onStateChanged call=" + call
                            + " remainingPostDialSequence=" + remainingPostDialSequence);
                    update();
                }

                @Override
                public void onVideoCallChanged(android.telecom.Call call,
                        VideoCall videoCall) {
                    Log.d(this, "TelecommCallCallback onStateChanged call=" + call + " videoCall="
                            + videoCall);
                    update();
                }

                @Override
                public void onCallDestroyed(android.telecom.Call call) {
                    Log.d(this, "TelecommCallCallback onStateChanged call=" + call);
                    call.unregisterCallback(mTelecomCallCallback);
                }

                @Override
                public void onConferenceableCallsChanged(android.telecom.Call call,
                        List<android.telecom.Call> conferenceableCalls) {
                    update();
                }
            };

    private android.telecom.Call mTelecommCall;
    private boolean mIsEmergencyCall;
    private Uri mHandle;
    private final String mId;
    private int mState = State.INVALID;
    private DisconnectCause mDisconnectCause;
    private int mSessionModificationState;
    private final List<String> mChildCallIds = new ArrayList<>();
    private final VideoSettings mVideoSettings = new VideoSettings();
    /**
     * mModifyToVideoState is used to store requested upgrade / downgrade video state
     */
    private int mModifyToVideoState = VideoProfile.STATE_AUDIO_ONLY;

    private InCallVideoCallCallback mVideoCallCallback;
    private String mChildNumber;
    private String mLastForwardedNumber;
    private String mCallSubject;
    private PhoneAccountHandle mPhoneAccountHandle;

    /**
     * Indicates whether the phone account associated with this call supports specifying a call
     * subject.
     */
    private boolean mIsCallSubjectSupported;

    /**
     * Used only to create mock calls for testing
     */
    @NeededForTesting
    Call(int state) {
        mTelecommCall = null;
        mId = ID_PREFIX + Integer.toString(sIdCounter++);
        setState(state);
    }

    public Call(android.telecom.Call telecommCall) {
        mTelecommCall = telecommCall;
        mId = ID_PREFIX + Integer.toString(sIdCounter++);

        updateFromTelecommCall();
        mTelecommCall.registerCallback(mTelecomCallCallback);
    }

    public android.telecom.Call getTelecommCall() {
        return mTelecommCall;
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
        updateFromTelecommCall();
        if (oldState != getState() && getState() == Call.State.DISCONNECTED) {
            CallList.getInstance().onDisconnect(this);
        } else {
            CallList.getInstance().onUpdate(this);
        }
        Trace.endSection();
    }

    private void updateFromTelecommCall() {
        Log.d(this, "updateFromTelecommCall: " + mTelecommCall.toString());
        setState(translateState(mTelecommCall.getState()));
        setDisconnectCause(mTelecommCall.getDetails().getDisconnectCause());

        if (mTelecommCall.getVideoCall() != null) {
            if (mVideoCallCallback == null) {
                mVideoCallCallback = new InCallVideoCallCallback(this);
            }
            mTelecommCall.getVideoCall().registerCallback(mVideoCallCallback);
        }

        mChildCallIds.clear();
        for (int i = 0; i < mTelecommCall.getChildren().size(); i++) {
            mChildCallIds.add(
                    CallList.getInstance().getCallByTelecommCall(
                            mTelecommCall.getChildren().get(i)).getId());
        }

        updateFromCallExtras(mTelecommCall.getDetails().getExtras());

        // If the handle of the call has changed, update state for the call determining if it is an
        // emergency call.
        Uri newHandle = mTelecommCall.getDetails().getHandle();
        if (!Objects.equals(mHandle, newHandle)) {
            mHandle = newHandle;
            updateEmergencyCallState();
        }

        // If the phone account handle of the call is set, cache capability bit indicating whether
        // the phone account supports call subjects.
        PhoneAccountHandle newPhoneAccountHandle = mTelecommCall.getDetails().getAccountHandle();
        if (!Objects.equals(mPhoneAccountHandle, newPhoneAccountHandle)) {
            mPhoneAccountHandle = newPhoneAccountHandle;

            if (mPhoneAccountHandle != null) {
                TelecomManager mgr = InCallPresenter.getInstance().getTelecomManager();
                PhoneAccount phoneAccount = mgr.getPhoneAccount(mPhoneAccountHandle);
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

    public String getNumber() {
        if (mTelecommCall == null) {
            return null;
        }
        if (mTelecommCall.getDetails().getGatewayInfo() != null) {
            return mTelecommCall.getDetails().getGatewayInfo()
                    .getOriginalAddress().getSchemeSpecificPart();
        }
        return getHandle() == null ? null : getHandle().getSchemeSpecificPart();
    }

    public Uri getHandle() {
        return mTelecommCall == null ? null : mTelecommCall.getDetails().getHandle();
    }

    public boolean isEmergencyCall() {
        return mIsEmergencyCall;
    }

    public int getState() {
        if (mTelecommCall != null && mTelecommCall.getParent() != null) {
            return State.CONFERENCED;
        } else {
            return mState;
        }
    }

    public void setState(int state) {
        mState = state;
    }

    public int getNumberPresentation() {
        return mTelecommCall == null ? null : mTelecommCall.getDetails().getHandlePresentation();
    }

    public int getCnapNamePresentation() {
        return mTelecommCall == null ? null
                : mTelecommCall.getDetails().getCallerDisplayNamePresentation();
    }

    public String getCnapName() {
        return mTelecommCall == null ? null
                : getTelecommCall().getDetails().getCallerDisplayName();
    }

    public Bundle getIntentExtras() {
        return mTelecommCall == null ? null : mTelecommCall.getDetails().getIntentExtras();
    }

    public Bundle getExtras() {
        return mTelecommCall == null ? null : mTelecommCall.getDetails().getExtras();
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
    }

    /** Returns the possible text message responses. */
    public List<String> getCannedSmsResponses() {
        return mTelecommCall.getCannedTextResponses();
    }

    /** Checks if the call supports the given set of capabilities supplied as a bit mask. */
    public boolean can(int capabilities) {
        int supportedCapabilities = mTelecommCall.getDetails().getCallCapabilities();

        if ((capabilities & android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0) {
            // We allow you to merge if the capabilities allow it or if it is a call with
            // conferenceable calls.
            if (mTelecommCall.getConferenceableCalls().isEmpty() &&
                ((android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE
                        & supportedCapabilities) == 0)) {
                // Cannot merge calls if there are no calls to merge with.
                return false;
            }
            capabilities &= ~android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE;
        }
        return (capabilities == (capabilities & mTelecommCall.getDetails().getCallCapabilities()));
    }

    public boolean hasProperty(int property) {
        return mTelecommCall.getDetails().hasProperty(property);
    }

    /** Gets the time when the call first became active. */
    public long getConnectTimeMillis() {
        return mTelecommCall.getDetails().getConnectTimeMillis();
    }

    public boolean isConferenceCall() {
        return mTelecommCall.getDetails().hasProperty(
                android.telecom.Call.Details.PROPERTY_CONFERENCE);
    }

    public GatewayInfo getGatewayInfo() {
        return mTelecommCall == null ? null : mTelecommCall.getDetails().getGatewayInfo();
    }

    public PhoneAccountHandle getAccountHandle() {
        return mTelecommCall == null ? null : mTelecommCall.getDetails().getAccountHandle();
    }

    public VideoCall getVideoCall() {
        return mTelecommCall == null ? null : mTelecommCall.getVideoCall();
    }

    public List<String> getChildCallIds() {
        return mChildCallIds;
    }

    public String getParentId() {
        android.telecom.Call parentCall = mTelecommCall.getParent();
        if (parentCall != null) {
            return CallList.getInstance().getCallByTelecommCall(parentCall).getId();
        }
        return null;
    }

    public int getVideoState() {
        return mTelecommCall.getDetails().getVideoState();
    }

    public boolean isVideoCall(Context context) {
        return CallUtil.isVideoEnabled(context) &&
                CallUtils.isVideoCall(getVideoState());
    }

    /**
     * This method is called when we request for a video upgrade or downgrade. This handles the
     * session modification state RECEIVED_UPGRADE_TO_VIDEO_REQUEST and sets the video state we
     * want to upgrade/downgrade to.
     */
    public void setSessionModificationTo(int videoState) {
        Log.d(this, "setSessionModificationTo - video state= " + videoState);
        if (videoState == getVideoState()) {
            mSessionModificationState = Call.SessionModificationState.NO_REQUEST;
            Log.w(this,"setSessionModificationTo - Clearing session modification state");
        } else {
            mSessionModificationState =
                Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST;
            setModifyToVideoState(videoState);
            CallList.getInstance().onUpgradeToVideo(this);
        }

        Log.d(this, "setSessionModificationTo - mSessionModificationState="
            + mSessionModificationState + " video state= " + videoState);
        update();
    }

    /**
     * This method is called to handle any other session modification states other than
     * RECEIVED_UPGRADE_TO_VIDEO_REQUEST. We set the modification state and reset the video state
     * when an upgrade request has been completed or failed.
     */
    public void setSessionModificationState(int state) {
        if (state == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
            Log.e(this,
                    "setSessionModificationState not valid for RECEIVED_UPGRADE_TO_VIDEO_REQUEST");
            return;
        }

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
        Uri handle = mTelecommCall.getDetails().getHandle();
        mIsEmergencyCall = PhoneNumberUtils.isEmergencyNumber(
                handle == null ? "" : handle.getSchemeSpecificPart());
    }

    private void setModifyToVideoState(int newVideoState) {
        mModifyToVideoState = newVideoState;
    }

    public int getModifyToVideoState() {
        return mModifyToVideoState;
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

    public int getSessionModificationState() {
        return mSessionModificationState;
    }

    @Override
    public String toString() {
        if (mTelecommCall == null) {
            // This should happen only in testing since otherwise we would never have a null
            // Telecom call.
            return String.valueOf(mId);
        }

        return String.format(Locale.US, "[%s, %s, %s, children:%s, parent:%s, conferenceable:%s, " +
                "videoState:%s, mSessionModificationState:%d, VideoSettings:%s]",
                mId,
                State.toString(getState()),
                android.telecom.Call.Details
                        .capabilitiesToString(mTelecommCall.getDetails().getCallCapabilities()),
                mChildCallIds,
                getParentId(),
                this.mTelecommCall.getConferenceableCalls(),
                VideoProfile.videoStateToString(mTelecommCall.getDetails().getVideoState()),
                mSessionModificationState,
                getVideoSettings());
    }

    public String toSimpleString() {
        return super.toString();
    }
}
