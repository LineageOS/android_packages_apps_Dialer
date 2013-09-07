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
import com.google.common.collect.Lists;

import java.util.ArrayList;
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
    private final ArrayList<IncomingCallListener> mIncomingCallListeners = Lists.newArrayList();

    private AudioModeProvider mAudioModeProvider;
    private StatusBarNotifier mStatusBarNotifier;
    private ContactInfoCache mContactInfoCache;
    private Context mContext;
    private CallList mCallList;
    private InCallActivity mInCallActivity;
    private boolean mServiceConnected = false;
    private InCallState mInCallState = InCallState.HIDDEN;
    private ProximitySensor mProximitySensor;

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

        mContactInfoCache = ContactInfoCache.getInstance(context);

        mStatusBarNotifier = new StatusBarNotifier(context, mContactInfoCache, mCallList);
        addListener(mStatusBarNotifier);
        addIncomingCallListener(mStatusBarNotifier);

        mAudioModeProvider = audioModeProvider;

        // This only gets called by the service so this is okay.
        mServiceConnected = true;

        mProximitySensor = new ProximitySensor(context, mAudioModeProvider);
        addListener(mProximitySensor);

        Log.d(this, "Finished InCallPresenter.setUp");
    }

    /**
     * Called when the telephony service has disconnected from us.  This will happen when there are
     * no more active calls. However, we may still want to continue showing the UI for
     * certain cases like showing "Call Ended".
     * What we really want is to wait for the activity and the service to both disconnect before we
     * tear things down. This method sets a serviceConnected boolean and calls a secondary method
     * that performs the aforementioned logic.
     */
    public void tearDown() {
        Log.d(this, "tearDown");
        mServiceConnected = false;
        attemptCleanup();
    }

    /**
     * Called when the UI begins or ends. Starts the callstate callbacks if the UI just began.
     * Attempts to tear down everything if the UI just ended. See #tearDown for more insight on
     * the tear-down process.
     */
    public void setActivity(InCallActivity inCallActivity) {
        mInCallActivity = inCallActivity;

        if (mInCallActivity != null) {
            Log.i(this, "UI Initialized");

            // Since the UI just came up, imitate an update from the call list
            // to set the proper UI state.
            onCallListChange(mCallList);
        } else {
            Log.i(this, "UI Destroyed)");
            attemptCleanup();
        }
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
        Log.i(this, "Phone switching state: " + mInCallState + " -> " + newState);
        mInCallState = newState;

        // notify listeners of new state
        for (InCallStateListener listener : mListeners) {
            Log.d(this, "Notify " + listener + " of state " + mInCallState.toString());
            listener.onStateChange(mInCallState, callList);
        }
    }

    /**
     * Called when there is a new incoming call.
     *
     * @param call
     */
    @Override
    public void onIncomingCall(Call call) {
        mInCallState = startOrFinishUi(InCallState.INCOMING);

        for (IncomingCallListener listener : mIncomingCallListeners) {
            listener.onIncomingCall(call);
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

    public void addIncomingCallListener(IncomingCallListener listener) {
        Preconditions.checkNotNull(listener);
        mIncomingCallListeners.add(listener);
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

    public ContactInfoCache getContactInfoCache() {
        return mContactInfoCache;
    }

    public ProximitySensor getProximitySensor() {
        return mProximitySensor;
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
     * Returns true of the activity has been created and is running.
     * Returns true as long as activity is not destroyed or finishing.  This ensures that we return
     * true even if the activity is paused (not in foreground).
     */
    public boolean isActivityStarted() {
        return (mInCallActivity != null &&
                !mInCallActivity.isDestroyed() &&
                !mInCallActivity.isFinishing());
    }

    /**
     * Called when the activity goes out of the foreground.
     */
    public void onUiShowing(boolean showing) {
        // We need to update the notification bar when we leave the UI because that
        // could trigger it to show again.
        if (mStatusBarNotifier != null) {
            mStatusBarNotifier.updateNotification(mInCallState, mCallList);
        }

        if (mProximitySensor != null) {
            mProximitySensor.onInCallShowing(showing);
        }
    }

    /**
     * Brings the app into the foreground if possible.
     */
    public void bringToForeground() {
        // Before we bring the incall UI to the foreground, we check to see if:
        // 1. there is an activity
        // 2. the activity is not already in the foreground
        // 3. We are in a state where we want to show the incall ui
        if (mInCallActivity != null &&
                !mInCallActivity.isForegroundActivity() &&
                mInCallState != InCallState.HIDDEN) {
            showInCall();
        }
    }

    public void onPostDialCharWait(int callId, String chars) {
        mInCallActivity.showPostCharWaitDialog(callId, chars);
    }

    /**
     * When the state of in-call changes, this is the first method to get called. It determines if
     * the UI needs to be started or finished depending on the new state and does it.
     */
    private InCallState startOrFinishUi(InCallState newState) {
        Log.d(this, "startOrFinishUi: " + mInCallState + " -> " + newState);

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

        if (showCallUi) {
            Log.i(this, "Start in call UI");
            showInCall();
        } else if (startStartupSequence) {
            Log.i(this, "Start Full Screen in call UI");
            mStatusBarNotifier.updateNotificationAndLaunchIncomingCallUi(newState, mCallList);
        } else if (newState == InCallState.HIDDEN) {
            Log.i(this, "Hide in call UI");

            // The new state is the hidden state (no calls).  Tear everything down.
            if (mInCallActivity != null) {
                if (mInCallActivity.isFinishing()) {
                    // Tear down process:
                    // When there are no more calls to handle, two things happen:
                    // 1. The binding connection with TeleService ends
                    // 2. InCallState changes to HIDDEN and we call activity.finish() here.
                    //
                    // Without the service connection, we will not get updates from the service
                    // and so will never get a new call to move out of the HIDDEN state. Since this
                    // code is called when we move from a different state into the HIDDEN state,
                    // it should never get hit twice. In case it does, log an error.
                    Log.e(this, "Attempting to finish incall activity twice.");
                } else {
                    mInCallActivity.finish();
                }

                // blow away stale contact info so that we get fresh data on
                // the next set of calls
                mContactInfoCache.clearCache();
            }
        }

        return newState;
    }

    /**
     * Checks to see if both the UI is gone and the service is disconnected. If so, tear it all
     * down.
     */
    private void attemptCleanup() {
        if (mInCallActivity == null && !mServiceConnected) {
            Log.i(this, "Start InCall presenter cleanup.");
            mProximitySensor = null;

            mAudioModeProvider = null;

            removeListener(mStatusBarNotifier);
            mStatusBarNotifier = null;

            mCallList.removeListener(this);
            mCallList = null;

            mContext = null;
            mInCallActivity = null;

            mListeners.clear();

            Log.d(this, "Finished InCallPresenter.CleanUp");
        }
    }

    private void showInCall() {
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

    public interface IncomingCallListener {
        public void onIncomingCall(Call call);
    }
}
