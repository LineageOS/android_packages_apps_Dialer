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

import com.google.android.collect.Sets;
import com.google.common.base.Preconditions;

import android.content.Context;
import android.content.Intent;

import com.android.services.telephony.common.Call;

import java.util.Set;

/**
 * Takes updates from the CallList and notifies the InCallActivity (UI)
 * of the changes.
 * Responsible for starting the activity for a new call and finishing the activity when all calls
 * are disconnected.
 * Creates and manages the in-call state and provides a listener pattern for the presenters
 * that want to listen in on the in-call state changes.
 * TODO(klp): This class has become more of a state machine at this point.  Consider renaming.
 */
public class InCallPresenter implements CallList.Listener {

    private static InCallPresenter sInCallPresenter;

    private final Set<InCallStateListener> mListeners = Sets.newHashSet();

    private AudioModeProvider mAudioModeProvider;
    private StatusBarNotifier mStatusBarNotifier;
    private Context mContext;
    private CallList mCallList;
    private InCallActivity mInCallActivity;

    private InCallState mInCallState = InCallState.HIDDEN;

    public static synchronized InCallPresenter getInstance() {
        if (sInCallPresenter == null) {
            sInCallPresenter = new InCallPresenter();
        }
        return sInCallPresenter;
    }

    public void setUp(Context context, CallList callList, AudioModeProvider audioModeProvider) {
        Preconditions.checkNotNull(context);
        mContext = context;

        mCallList = callList;
        mCallList.addListener(this);

        mStatusBarNotifier = new StatusBarNotifier(context);
        addListener(mStatusBarNotifier);

        mAudioModeProvider = audioModeProvider;
    }

    public void tearDown() {
        mAudioModeProvider = null;

        removeListener(mStatusBarNotifier);
        mStatusBarNotifier = null;

        mCallList.removeListener(this);
        mCallList = null;

        mContext = null;
        mInCallActivity = null;

        mListeners.clear();
    }

    public void setActivity(InCallActivity inCallActivity) {
        mInCallActivity = inCallActivity;

        Logger.d(this, "UI Initialized");

        // Since the UI just came up, imitate an update from the call list
        // to set the proper UI state.
        onCallListChange(mCallList);
    }

    /**
     * Called when there is a change to the call list.
     * Sets the In-Call state for the entire in-call app based on the information it gets from
     * CallList. Dispatches the in-call state to all listeners. Can trigger the creation or
     * destruction of the UI based on the states that is calculates.
     */
    @Override
    public void onCallListChange(CallList callList) {
        InCallState newState = getPotentialStateFromCallList(callList);
        newState = startOrFinishUi(newState);

        // Set the new state before announcing it to the world
        mInCallState = newState;

        // notify listeners of new state
        for (InCallStateListener listener : mListeners) {
            Logger.d(this, "Notify " + listener + " of state " + mInCallState.toString());
            listener.onStateChange(mInCallState, callList);
        }
    }

    /**
     * Given the call list, return the state in which the in-call screen should be.
     */
    public static InCallState getPotentialStateFromCallList(CallList callList) {
        InCallState newState = InCallState.HIDDEN;

        if (callList.getIncomingCall() != null) {
            newState = InCallState.INCOMING;
        } else if (callList.getOutgoingCall() != null) {
            newState = InCallState.OUTGOING;
        } else if (callList.getActiveCall() != null ||
                callList.getBackgroundCall() != null ||
                callList.getDisconnectedCall() != null) {
            newState = InCallState.INCALL;
        }

        return newState;
    }

    public void addListener(InCallStateListener listener) {
        Preconditions.checkNotNull(listener);
        mListeners.add(listener);
    }

    public void removeListener(InCallStateListener listener) {
        Preconditions.checkNotNull(listener);
        mListeners.remove(listener);
    }

    public AudioModeProvider getAudioModeProvider() {
        return mAudioModeProvider;
    }

    /**
     * Hangs up any active or outgoing calls.
     */
    public void hangUpOngoingCall() {
        Call call = mCallList.getOutgoingCall();
        if (call == null) {
            call = mCallList.getActiveOrBackgroundCall();
        }

        if (call != null) {
            CallCommandClient.getInstance().disconnectCall(call.getCallId());
        }
    }

    /**
     * Returns true if the incall app is the foreground application.
     */
    public boolean isShowingInCallUi() {
        return (mInCallActivity != null &&
                mInCallActivity.isForegroundActivity());
    }

    /**
     * Called when the activity goes out of the foreground.
     */
    public void onUiShowing(boolean showing) {
        // We need to update the notification bar when we leave the UI because that
        // could trigger it to show again.
        mStatusBarNotifier.updateNotification(mInCallState, mCallList);
    }

    /**
     * When the state of in-call changes, this is the first method to get called. It determines if
     * the UI needs to be started or finished depending on the new state and does it.
     */
    private InCallState startOrFinishUi(InCallState newState) {
        Logger.d(this, "startOrFInishUi: " + newState.toString());

        // TODO(klp): Consider a proper state machine implementation

        // If the state isn't changing, we have already done any starting/stopping of
        // activities in a previous pass...so lets cut out early
        if (newState == mInCallState) {
            return newState;
        }

        // A new Incoming call means that the user needs to be notified of the the call (since
        // it wasn't them who initiated it).  We do this through full screen notifications and
        // happens indirectly through {@link StatusBarListener}.
        //
        // The process for incoming calls is as follows:
        //
        // 1) CallList          - Announces existence of new INCOMING call
        // 2) InCallPresenter   - Gets announcement and calculates that the new InCallState
        //                      - should be set to INCOMING.
        // 3) InCallPresenter   - This method is called to see if we need to start or finish
        //                        the app given the new state.
        // 4) StatusBarNotifier - Listens to InCallState changes. InCallPresenter calls
        //                        StatusBarNotifier explicitly to issue a FullScreen Notification
        //                        that will either start the InCallActivity or show the user a
        //                        top-level notification dialog if the user is in an immersive app.
        //                        That notification can also start the InCallActivity.
        // 5) InCallActivity    - Main activity starts up and at the end of its onCreate will
        //                        call InCallPresenter::setActivity() to let the presenter
        //                        know that start-up is complete.
        //
        //          [ AND NOW YOU'RE IN THE CALL. voila! ]
        //
        // Our app is started using a fullScreen notification.  We need to do this whenever
        // we get an incoming call.
        final boolean startStartupSequence = (InCallState.INCOMING == newState);

        // A new outgoing call indicates that the user just now dialed a number and when that
        // happens we need to display the screen immediateley.
        //
        // This is different from the incoming call sequence because we do not need to shock the
        // user with a top-level notification.  Just show the call UI normally.
        final boolean showCallUi = (InCallState.OUTGOING == newState);

        Logger.v(this, "showCallUi: ", showCallUi);
        Logger.v(this, "startStartupSequence: ", startStartupSequence);


        if (startStartupSequence) {
            mStatusBarNotifier.updateNotificationAndLaunchIncomingCallUi(newState, mCallList);
        } else if (showCallUi) {
            showInCall();
        } else if (newState == InCallState.HIDDEN) {

            // The new state is the hidden state (no calls).  Tear everything down.
            if (mInCallActivity != null) {
                // Null out reference before we start end sequence
                InCallActivity temp = mInCallActivity;
                mInCallActivity = null;

                temp.finish();
            }
        }

        return newState;
    }

    private void showInCall() {
        Logger.d(this, "Showing in call manually.");
        mContext.startActivity(getInCallIntent());
    }

    private Intent getInCallIntent() {
        final Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        intent.setClass(mContext, InCallActivity.class);

        return intent;
    }

    /**
     * Private constructor. Must use getInstance() to get this singleton.
     */
    private InCallPresenter() {
    }

    /**
     * All the main states of InCallActivity.
     */
    public enum InCallState {
        // InCall Screen is off and there are no calls
        HIDDEN,

        // Incoming-call screen is up
        INCOMING,

        // In-call experience is showing
        INCALL,

        // User is dialing out
        OUTGOING;

        public boolean isIncoming() {
            return (this == INCOMING);
        }

        public boolean isHidden() {
            return (this == HIDDEN);
        }

        public boolean isConnectingOrConnected() {
            return (this == INCOMING ||
                    this == OUTGOING ||
                    this == INCALL);
        }
    }

    /**
     * Interface implemented by classes that need to know about the InCall State.
     */
    public interface InCallStateListener {
        // TODO(klp): Enhance state to contain the call objects instead of passing CallList
        public void onStateChange(InCallState state, CallList callList);
    }
}
