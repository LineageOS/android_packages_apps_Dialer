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

import android.app.ActivityManager.TaskDescription;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.android.contacts.common.interactions.TouchPointManager;
import com.android.contacts.common.testing.NeededForTesting;
import com.android.contacts.common.util.MaterialColorMapUtils.MaterialPalette;
import com.android.incalluibind.ObjectFactory;
import com.google.common.base.Preconditions;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Takes updates from the CallList and notifies the InCallActivity (UI)
 * of the changes.
 * Responsible for starting the activity for a new call and finishing the activity when all calls
 * are disconnected.
 * Creates and manages the in-call state and provides a listener pattern for the presenters
 * that want to listen in on the in-call state changes.
 * TODO: This class has become more of a state machine at this point.  Consider renaming.
 */
public class InCallPresenter implements CallList.Listener,
        CircularRevealFragment.OnCircularRevealCompleteListener {

    private static final String EXTRA_FIRST_TIME_SHOWN =
            "com.android.incallui.intent.extra.FIRST_TIME_SHOWN";

    private static final Bundle EMPTY_EXTRAS = new Bundle();

    private static InCallPresenter sInCallPresenter;

    /**
     * ConcurrentHashMap constructor params: 8 is initial table size, 0.9f is
     * load factor before resizing, 1 means we only expect a single thread to
     * access the map so make only a single shard
     */
    private final Set<InCallStateListener> mListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<InCallStateListener, Boolean>(8, 0.9f, 1));
    private final List<IncomingCallListener> mIncomingCallListeners = new CopyOnWriteArrayList<>();
    private final Set<InCallDetailsListener> mDetailsListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<InCallDetailsListener, Boolean>(8, 0.9f, 1));
    private final Set<CanAddCallListener> mCanAddCallListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<CanAddCallListener, Boolean>(8, 0.9f, 1));
    private final Set<InCallUiListener> mInCallUiListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<InCallUiListener, Boolean>(8, 0.9f, 1));
    private final Set<InCallOrientationListener> mOrientationListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<InCallOrientationListener, Boolean>(8, 0.9f, 1));
    private final Set<InCallEventListener> mInCallEventListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<InCallEventListener, Boolean>(8, 0.9f, 1));

    private AudioModeProvider mAudioModeProvider;
    private StatusBarNotifier mStatusBarNotifier;
    private ContactInfoCache mContactInfoCache;
    private Context mContext;
    private CallList mCallList;
    private InCallActivity mInCallActivity;
    private InCallState mInCallState = InCallState.NO_CALLS;
    private ProximitySensor mProximitySensor;
    private boolean mServiceConnected = false;
    private boolean mAccountSelectionCancelled = false;
    private InCallCameraManager mInCallCameraManager = null;
    private AnswerPresenter mAnswerPresenter = new AnswerPresenter();

    /**
     * Whether or not we are currently bound and waiting for Telecom to send us a new call.
     */
    private boolean mBoundAndWaitingForOutgoingCall;

    /**
     * If there is no actual call currently in the call list, this will be used as a fallback
     * to determine the theme color for InCallUI.
     */
    private PhoneAccountHandle mPendingPhoneAccountHandle;

    /**
     * Determines if the InCall UI is in fullscreen mode or not.
     */
    private boolean mIsFullScreen = false;

    private final android.telecom.Call.Callback mCallCallback =
            new android.telecom.Call.Callback() {
        @Override
        public void onPostDialWait(android.telecom.Call telecomCall,
                String remainingPostDialSequence) {
            final Call call = mCallList.getCallByTelecommCall(telecomCall);
            if (call == null) {
                Log.w(this, "Call not found in call list: " + telecomCall);
                return;
            }
            onPostDialCharWait(call.getId(), remainingPostDialSequence);
        }

        @Override
        public void onDetailsChanged(android.telecom.Call telecomCall,
                android.telecom.Call.Details details) {
            final Call call = mCallList.getCallByTelecommCall(telecomCall);
            if (call == null) {
                Log.w(this, "Call not found in call list: " + telecomCall);
                return;
            }
            for (InCallDetailsListener listener : mDetailsListeners) {
                listener.onDetailsChanged(call, details);
            }
        }

        @Override
        public void onConferenceableCallsChanged(android.telecom.Call telecomCall,
                List<android.telecom.Call> conferenceableCalls) {
            Log.i(this, "onConferenceableCallsChanged: " + telecomCall);
            onDetailsChanged(telecomCall, telecomCall.getDetails());
        }
    };

    /**
     * Is true when the activity has been previously started. Some code needs to know not just if
     * the activity is currently up, but if it had been previously shown in foreground for this
     * in-call session (e.g., StatusBarNotifier). This gets reset when the session ends in the
     * tear-down method.
     */
    private boolean mIsActivityPreviouslyStarted = false;

    /**
     * Whether or not InCallService is bound to Telecom.
     */
    private boolean mServiceBound = false;

    /**
     * When configuration changes Android kills the current activity and starts a new one.
     * The flag is used to check if full clean up is necessary (activity is stopped and new
     * activity won't be started), or if a new activity will be started right after the current one
     * is destroyed, and therefore no need in release all resources.
     */
    private boolean mIsChangingConfigurations = false;

    /** Display colors for the UI. Consists of a primary color and secondary (darker) color */
    private MaterialPalette mThemeColors;

    private TelecomManager mTelecomManager;

    public static synchronized InCallPresenter getInstance() {
        if (sInCallPresenter == null) {
            sInCallPresenter = new InCallPresenter();
        }
        return sInCallPresenter;
    }

    @NeededForTesting
    static synchronized void setInstance(InCallPresenter inCallPresenter) {
        sInCallPresenter = inCallPresenter;
    }

    public InCallState getInCallState() {
        return mInCallState;
    }

    public CallList getCallList() {
        return mCallList;
    }

    public void setUp(Context context,
            CallList callList,
            AudioModeProvider audioModeProvider,
            StatusBarNotifier statusBarNotifier,
            ContactInfoCache contactInfoCache,
            ProximitySensor proximitySensor) {
        if (mServiceConnected) {
            Log.i(this, "New service connection replacing existing one.");
            // retain the current resources, no need to create new ones.
            Preconditions.checkState(context == mContext);
            Preconditions.checkState(callList == mCallList);
            Preconditions.checkState(audioModeProvider == mAudioModeProvider);
            return;
        }

        Preconditions.checkNotNull(context);
        mContext = context;

        mContactInfoCache = contactInfoCache;

        mStatusBarNotifier = statusBarNotifier;
        addListener(mStatusBarNotifier);

        mAudioModeProvider = audioModeProvider;

        mProximitySensor = proximitySensor;
        addListener(mProximitySensor);

        addIncomingCallListener(mAnswerPresenter);
        addInCallUiListener(mAnswerPresenter);

        mCallList = callList;

        // This only gets called by the service so this is okay.
        mServiceConnected = true;

        // The final thing we do in this set up is add ourselves as a listener to CallList.  This
        // will kick off an update and the whole process can start.
        mCallList.addListener(this);

        VideoPauseController.getInstance().setUp(this);

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

        VideoPauseController.getInstance().tearDown();
    }

    private void attemptFinishActivity() {
        final boolean doFinish = (mInCallActivity != null && isActivityStarted());
        Log.i(this, "Hide in call UI: " + doFinish);
        if (doFinish) {
            mInCallActivity.setExcludeFromRecents(true);
            mInCallActivity.finish();

            if (mAccountSelectionCancelled) {
                // This finish is a result of account selection cancellation
                // do not include activity ending transition
                mInCallActivity.overridePendingTransition(0, 0);
            }
        }
    }

    /**
     * Called when the UI begins, and starts the callstate callbacks if necessary.
     */
    public void setActivity(InCallActivity inCallActivity) {
        if (inCallActivity == null) {
            throw new IllegalArgumentException("registerActivity cannot be called with null");
        }
        if (mInCallActivity != null && mInCallActivity != inCallActivity) {
            Log.w(this, "Setting a second activity before destroying the first.");
        }
        updateActivity(inCallActivity);
    }

    /**
     * Called when the UI ends. Attempts to tear down everything if necessary. See
     * {@link #tearDown()} for more insight on the tear-down process.
     */
    public void unsetActivity(InCallActivity inCallActivity) {
        if (inCallActivity == null) {
            throw new IllegalArgumentException("unregisterActivity cannot be called with null");
        }
        if (mInCallActivity == null) {
            Log.i(this, "No InCallActivity currently set, no need to unset.");
            return;
        }
        if (mInCallActivity != inCallActivity) {
            Log.w(this, "Second instance of InCallActivity is trying to unregister when another"
                    + " instance is active. Ignoring.");
            return;
        }
        updateActivity(null);
    }

    /**
     * Updates the current instance of {@link InCallActivity} with the provided one. If a
     * {@code null} activity is provided, it means that the activity was finished and we should
     * attempt to cleanup.
     */
    private void updateActivity(InCallActivity inCallActivity) {
        boolean updateListeners = false;
        boolean doAttemptCleanup = false;

        if (inCallActivity != null) {
            if (mInCallActivity == null) {
                updateListeners = true;
                Log.i(this, "UI Initialized");
            } else {
                // since setActivity is called onStart(), it can be called multiple times.
                // This is fine and ignorable, but we do not want to update the world every time
                // this happens (like going to/from background) so we do not set updateListeners.
            }

            mInCallActivity = inCallActivity;
            mInCallActivity.setExcludeFromRecents(false);

            // By the time the UI finally comes up, the call may already be disconnected.
            // If that's the case, we may need to show an error dialog.
            if (mCallList != null && mCallList.getDisconnectedCall() != null) {
                maybeShowErrorDialogOnDisconnect(mCallList.getDisconnectedCall());
            }

            // When the UI comes up, we need to first check the in-call state.
            // If we are showing NO_CALLS, that means that a call probably connected and
            // then immediately disconnected before the UI was able to come up.
            // If we dont have any calls, start tearing down the UI instead.
            // NOTE: This code relies on {@link #mInCallActivity} being set so we run it after
            // it has been set.
            if (mInCallState == InCallState.NO_CALLS) {
                Log.i(this, "UI Initialized, but no calls left.  shut down.");
                attemptFinishActivity();
                return;
            }
        } else {
            Log.i(this, "UI Destroyed");
            updateListeners = true;
            mInCallActivity = null;

            // We attempt cleanup for the destroy case but only after we recalculate the state
            // to see if we need to come back up or stay shut down. This is why we do the
            // cleanup after the call to onCallListChange() instead of directly here.
            doAttemptCleanup = true;
        }

        // Messages can come from the telephony layer while the activity is coming up
        // and while the activity is going down.  So in both cases we need to recalculate what
        // state we should be in after they complete.
        // Examples: (1) A new incoming call could come in and then get disconnected before
        //               the activity is created.
        //           (2) All calls could disconnect and then get a new incoming call before the
        //               activity is destroyed.
        //
        // b/1122139 - We previously had a check for mServiceConnected here as well, but there are
        // cases where we need to recalculate the current state even if the service in not
        // connected.  In particular the case where startOrFinish() is called while the app is
        // already finish()ing. In that case, we skip updating the state with the knowledge that
        // we will check again once the activity has finished. That means we have to recalculate the
        // state here even if the service is disconnected since we may not have finished a state
        // transition while finish()ing.
        if (updateListeners) {
            onCallListChange(mCallList);
        }

        if (doAttemptCleanup) {
            attemptCleanup();
        }
    }

    private boolean mAwaitingCallListUpdate = false;

    public void onBringToForeground(boolean showDialpad) {
        Log.i(this, "Bringing UI to foreground.");
        bringToForeground(showDialpad);
    }

    /**
     * TODO: Consider listening to CallList callbacks to do this instead of receiving a direct
     * method invocation from InCallService.
     */
    public void onCallAdded(android.telecom.Call call) {
        // Since a call has been added we are no longer waiting for Telecom to send us a
        // call.
        setBoundAndWaitingForOutgoingCall(false, null);
        call.registerCallback(mCallCallback);
    }

    /**
     * TODO: Consider listening to CallList callbacks to do this instead of receiving a direct
     * method invocation from InCallService.
     */
    public void onCallRemoved(android.telecom.Call call) {
        call.unregisterCallback(mCallCallback);
    }

    public void onCanAddCallChanged(boolean canAddCall) {
        for (CanAddCallListener listener : mCanAddCallListeners) {
            listener.onCanAddCallChanged(canAddCall);
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
        if (mInCallActivity != null && mInCallActivity.getCallCardFragment() != null &&
                mInCallActivity.getCallCardFragment().isAnimating()) {
            mAwaitingCallListUpdate = true;
            return;
        }
        if (callList == null) {
            return;
        }

        mAwaitingCallListUpdate = false;

        InCallState newState = getPotentialStateFromCallList(callList);
        InCallState oldState = mInCallState;
        Log.d(this, "onCallListChange oldState= " + oldState + " newState=" + newState);
        newState = startOrFinishUi(newState);
        Log.d(this, "onCallListChange newState changed to " + newState);

        // Set the new state before announcing it to the world
        Log.i(this, "Phone switching state: " + oldState + " -> " + newState);
        mInCallState = newState;

        // notify listeners of new state
        for (InCallStateListener listener : mListeners) {
            Log.d(this, "Notify " + listener + " of state " + mInCallState.toString());
            listener.onStateChange(oldState, mInCallState, callList);
        }

        if (isActivityStarted()) {
            final boolean hasCall = callList.getActiveOrBackgroundCall() != null ||
                    callList.getOutgoingCall() != null;
            mInCallActivity.dismissKeyguard(hasCall);
        }
    }

    /**
     * Called when there is a new incoming call.
     *
     * @param call
     */
    @Override
    public void onIncomingCall(Call call) {
        InCallState newState = startOrFinishUi(InCallState.INCOMING);
        InCallState oldState = mInCallState;

        Log.i(this, "Phone switching state: " + oldState + " -> " + newState);
        mInCallState = newState;

        for (IncomingCallListener listener : mIncomingCallListeners) {
            listener.onIncomingCall(oldState, mInCallState, call);
        }
    }

    @Override
    public void onUpgradeToVideo(Call call) {
        //NO-OP
    }
    /**
     * Called when a call becomes disconnected. Called everytime an existing call
     * changes from being connected (incoming/outgoing/active) to disconnected.
     */
    @Override
    public void onDisconnect(Call call) {
        maybeShowErrorDialogOnDisconnect(call);

        // We need to do the run the same code as onCallListChange.
        onCallListChange(mCallList);

        if (isActivityStarted()) {
            mInCallActivity.dismissKeyguard(false);
        }
    }

    /**
     * Given the call list, return the state in which the in-call screen should be.
     */
    public InCallState getPotentialStateFromCallList(CallList callList) {

        InCallState newState = InCallState.NO_CALLS;

        if (callList == null) {
            return newState;
        }
        if (callList.getIncomingCall() != null) {
            newState = InCallState.INCOMING;
        } else if (callList.getWaitingForAccountCall() != null) {
            newState = InCallState.WAITING_FOR_ACCOUNT;
        } else if (callList.getPendingOutgoingCall() != null) {
            newState = InCallState.PENDING_OUTGOING;
        } else if (callList.getOutgoingCall() != null) {
            newState = InCallState.OUTGOING;
        } else if (callList.getActiveCall() != null ||
                callList.getBackgroundCall() != null ||
                callList.getDisconnectedCall() != null ||
                callList.getDisconnectingCall() != null) {
            newState = InCallState.INCALL;
        }

        if (newState == InCallState.NO_CALLS) {
            if (mBoundAndWaitingForOutgoingCall) {
                return InCallState.OUTGOING;
            }
        }

        return newState;
    }

    public boolean isBoundAndWaitingForOutgoingCall() {
        return mBoundAndWaitingForOutgoingCall;
    }

    public void setBoundAndWaitingForOutgoingCall(boolean isBound, PhoneAccountHandle handle) {
        // NOTE: It is possible for there to be a race and have handle become null before
        // the circular reveal starts. This should not cause any problems because CallCardFragment
        // should fallback to the actual call in the CallList at that point in time to determine
        // the theme color.
        Log.i(this, "setBoundAndWaitingForOutgoingCall: " + isBound);
        mBoundAndWaitingForOutgoingCall = isBound;
        mPendingPhoneAccountHandle = handle;
        if (isBound && mInCallState == InCallState.NO_CALLS) {
            mInCallState = InCallState.OUTGOING;
        }
    }

    @Override
    public void onCircularRevealComplete(FragmentManager fm) {
        if (mInCallActivity != null) {
            mInCallActivity.showCallCardFragment(true);
            mInCallActivity.getCallCardFragment().animateForNewOutgoingCall();
            CircularRevealFragment.endCircularReveal(mInCallActivity.getFragmentManager());
        }
    }

    public void onShrinkAnimationComplete() {
        if (mAwaitingCallListUpdate) {
            onCallListChange(mCallList);
        }
    }

    public void addIncomingCallListener(IncomingCallListener listener) {
        Preconditions.checkNotNull(listener);
        mIncomingCallListeners.add(listener);
    }

    public void removeIncomingCallListener(IncomingCallListener listener) {
        if (listener != null) {
            mIncomingCallListeners.remove(listener);
        }
    }

    public void addListener(InCallStateListener listener) {
        Preconditions.checkNotNull(listener);
        mListeners.add(listener);
    }

    public void removeListener(InCallStateListener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    public void addDetailsListener(InCallDetailsListener listener) {
        Preconditions.checkNotNull(listener);
        mDetailsListeners.add(listener);
    }

    public void removeDetailsListener(InCallDetailsListener listener) {
        if (listener != null) {
            mDetailsListeners.remove(listener);
        }
    }

    public void addCanAddCallListener(CanAddCallListener listener) {
        Preconditions.checkNotNull(listener);
        mCanAddCallListeners.add(listener);
    }

    public void removeCanAddCallListener(CanAddCallListener listener) {
        if (listener != null) {
            mCanAddCallListeners.remove(listener);
        }
    }

    public void addOrientationListener(InCallOrientationListener listener) {
        Preconditions.checkNotNull(listener);
        mOrientationListeners.add(listener);
    }

    public void removeOrientationListener(InCallOrientationListener listener) {
        if (listener != null) {
            mOrientationListeners.remove(listener);
        }
    }

    public void addInCallEventListener(InCallEventListener listener) {
        Preconditions.checkNotNull(listener);
        mInCallEventListeners.add(listener);
    }

    public void removeInCallEventListener(InCallEventListener listener) {
        if (listener != null) {
            mInCallEventListeners.remove(listener);
        }
    }

    public ProximitySensor getProximitySensor() {
        return mProximitySensor;
    }

    public void handleAccountSelection(PhoneAccountHandle accountHandle, boolean setDefault) {
        if (mCallList != null) {
            Call call = mCallList.getWaitingForAccountCall();
            if (call != null) {
                String callId = call.getId();
                TelecomAdapter.getInstance().phoneAccountSelected(callId, accountHandle, setDefault);
            }
        }
    }

    public void cancelAccountSelection() {
        mAccountSelectionCancelled = true;
        if (mCallList != null) {
            Call call = mCallList.getWaitingForAccountCall();
            if (call != null) {
                String callId = call.getId();
                TelecomAdapter.getInstance().disconnectCall(callId);
            }
        }
    }

    /**
     * Hangs up any active or outgoing calls.
     */
    public void hangUpOngoingCall(Context context) {
        // By the time we receive this intent, we could be shut down and call list
        // could be null.  Bail in those cases.
        if (mCallList == null) {
            if (mStatusBarNotifier == null) {
                // The In Call UI has crashed but the notification still stayed up. We should not
                // come to this stage.
                StatusBarNotifier.clearAllCallNotifications(context);
            }
            return;
        }

        Call call = mCallList.getOutgoingCall();
        if (call == null) {
            call = mCallList.getActiveOrBackgroundCall();
        }

        if (call != null) {
            TelecomAdapter.getInstance().disconnectCall(call.getId());
            call.setState(Call.State.DISCONNECTING);
            mCallList.onUpdate(call);
        }
    }

    /**
     * Answers any incoming call.
     */
    public void answerIncomingCall(Context context, int videoState) {
        // By the time we receive this intent, we could be shut down and call list
        // could be null.  Bail in those cases.
        if (mCallList == null) {
            StatusBarNotifier.clearAllCallNotifications(context);
            return;
        }

        Call call = mCallList.getIncomingCall();
        if (call != null) {
            TelecomAdapter.getInstance().answerCall(call.getId(), videoState);
            showInCall(false, false/* newOutgoingCall */);
        }
    }

    /**
     * Declines any incoming call.
     */
    public void declineIncomingCall(Context context) {
        // By the time we receive this intent, we could be shut down and call list
        // could be null.  Bail in those cases.
        if (mCallList == null) {
            StatusBarNotifier.clearAllCallNotifications(context);
            return;
        }

        Call call = mCallList.getIncomingCall();
        if (call != null) {
            TelecomAdapter.getInstance().rejectCall(call.getId(), false, null);
        }
    }

    public void acceptUpgradeRequest(int videoState, Context context) {
        Log.d(this, " acceptUpgradeRequest videoState " + videoState);
        // Bail if we have been shut down and the call list is null.
        if (mCallList == null) {
            StatusBarNotifier.clearAllCallNotifications(context);
            Log.e(this, " acceptUpgradeRequest mCallList is empty so returning");
            return;
        }

        Call call = mCallList.getVideoUpgradeRequestCall();
        if (call != null) {
            VideoProfile videoProfile = new VideoProfile(videoState);
            call.getVideoCall().sendSessionModifyResponse(videoProfile);
            call.setSessionModificationState(Call.SessionModificationState.NO_REQUEST);
        }
    }

    public void declineUpgradeRequest(Context context) {
        Log.d(this, " declineUpgradeRequest");
        // Bail if we have been shut down and the call list is null.
        if (mCallList == null) {
            StatusBarNotifier.clearAllCallNotifications(context);
            Log.e(this, " declineUpgradeRequest mCallList is empty so returning");
            return;
        }

        Call call = mCallList.getVideoUpgradeRequestCall();
        if (call != null) {
            VideoProfile videoProfile =
                    new VideoProfile(call.getVideoState());
            call.getVideoCall().sendSessionModifyResponse(videoProfile);
            call.setSessionModificationState(Call.SessionModificationState.NO_REQUEST);
        }
    }

    /**
     * Returns true if the incall app is the foreground application.
     */
    public boolean isShowingInCallUi() {
        return (isActivityStarted() && mInCallActivity.isVisible());
    }

    /**
     * Returns true if the activity has been created and is running.
     * Returns true as long as activity is not destroyed or finishing.  This ensures that we return
     * true even if the activity is paused (not in foreground).
     */
    public boolean isActivityStarted() {
        return (mInCallActivity != null &&
                !mInCallActivity.isDestroyed() &&
                !mInCallActivity.isFinishing());
    }

    public boolean isActivityPreviouslyStarted() {
        return mIsActivityPreviouslyStarted;
    }

    public boolean isChangingConfigurations() {
        return mIsChangingConfigurations;
    }

    /*package*/
    void updateIsChangingConfigurations() {
        mIsChangingConfigurations = false;
        if (mInCallActivity != null) {
            mIsChangingConfigurations = mInCallActivity.isChangingConfigurations();
        }
        Log.d(this, "IsChangingConfigurations=" + mIsChangingConfigurations);
    }


    /**
     * Called when the activity goes in/out of the foreground.
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

        Intent broadcastIntent = ObjectFactory.getUiReadyBroadcastIntent(mContext);
        if (broadcastIntent != null) {
            broadcastIntent.putExtra(EXTRA_FIRST_TIME_SHOWN, !mIsActivityPreviouslyStarted);

            if (showing) {
                Log.d(this, "Sending sticky broadcast: ", broadcastIntent);
                mContext.sendStickyBroadcast(broadcastIntent);
            } else {
                Log.d(this, "Removing sticky broadcast: ", broadcastIntent);
                mContext.removeStickyBroadcast(broadcastIntent);
            }
        }

        if (showing) {
            mIsActivityPreviouslyStarted = true;
        } else {
            updateIsChangingConfigurations();
        }

        for (InCallUiListener listener : mInCallUiListeners) {
            listener.onUiShowing(showing);
        }
    }

    public void addInCallUiListener(InCallUiListener listener) {
        mInCallUiListeners.add(listener);
    }

    public boolean removeInCallUiListener(InCallUiListener listener) {
        return mInCallUiListeners.remove(listener);
    }

    /*package*/
    void onActivityStarted() {
        Log.d(this, "onActivityStarted");
        notifyVideoPauseController(true);
    }

    /*package*/
    void onActivityStopped() {
        Log.d(this, "onActivityStopped");
        notifyVideoPauseController(false);
    }

    private void notifyVideoPauseController(boolean showing) {
        Log.d(this, "notifyVideoPauseController: mIsChangingConfigurations=" +
                mIsChangingConfigurations);
        if (!mIsChangingConfigurations) {
            VideoPauseController.getInstance().onUiShowing(showing);
        }
    }

    /**
     * Brings the app into the foreground if possible.
     */
    public void bringToForeground(boolean showDialpad) {
        // Before we bring the incall UI to the foreground, we check to see if:
        // 1. It is not currently in the foreground
        // 2. We are in a state where we want to show the incall ui (i.e. there are calls to
        // be displayed)
        // If the activity hadn't actually been started previously, yet there are still calls
        // present (e.g. a call was accepted by a bluetooth or wired headset), we want to
        // bring it up the UI regardless.
        if (!isShowingInCallUi() && mInCallState != InCallState.NO_CALLS) {
            showInCall(showDialpad, false /* newOutgoingCall */);
        }
    }

    public void onPostDialCharWait(String callId, String chars) {
        if (isActivityStarted()) {
            mInCallActivity.showPostCharWaitDialog(callId, chars);
        }
    }

    /**
     * Handles the green CALL key while in-call.
     * @return true if we consumed the event.
     */
    public boolean handleCallKey() {
        Log.v(this, "handleCallKey");

        // The green CALL button means either "Answer", "Unhold", or
        // "Swap calls", or can be a no-op, depending on the current state
        // of the Phone.

        /**
         * INCOMING CALL
         */
        final CallList calls = mCallList;
        final Call incomingCall = calls.getIncomingCall();
        Log.v(this, "incomingCall: " + incomingCall);

        // (1) Attempt to answer a call
        if (incomingCall != null) {
            TelecomAdapter.getInstance().answerCall(
                    incomingCall.getId(), VideoProfile.STATE_AUDIO_ONLY);
            return true;
        }

        /**
         * STATE_ACTIVE CALL
         */
        final Call activeCall = calls.getActiveCall();
        if (activeCall != null) {
            // TODO: This logic is repeated from CallButtonPresenter.java. We should
            // consolidate this logic.
            final boolean canMerge = activeCall.can(
                    android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE);
            final boolean canSwap = activeCall.can(
                    android.telecom.Call.Details.CAPABILITY_SWAP_CONFERENCE);

            Log.v(this, "activeCall: " + activeCall + ", canMerge: " + canMerge +
                    ", canSwap: " + canSwap);

            // (2) Attempt actions on conference calls
            if (canMerge) {
                TelecomAdapter.getInstance().merge(activeCall.getId());
                return true;
            } else if (canSwap) {
                TelecomAdapter.getInstance().swap(activeCall.getId());
                return true;
            }
        }

        /**
         * BACKGROUND CALL
         */
        final Call heldCall = calls.getBackgroundCall();
        if (heldCall != null) {
            // We have a hold call so presumeable it will always support HOLD...but
            // there is no harm in double checking.
            final boolean canHold = heldCall.can(android.telecom.Call.Details.CAPABILITY_HOLD);

            Log.v(this, "heldCall: " + heldCall + ", canHold: " + canHold);

            // (4) unhold call
            if (heldCall.getState() == Call.State.ONHOLD && canHold) {
                TelecomAdapter.getInstance().unholdCall(heldCall.getId());
                return true;
            }
        }

        // Always consume hard keys
        return true;
    }

    /**
     * A dialog could have prevented in-call screen from being previously finished.
     * This function checks to see if there should be any UI left and if not attempts
     * to tear down the UI.
     */
    public void onDismissDialog() {
        Log.i(this, "Dialog dismissed");
        if (mInCallState == InCallState.NO_CALLS) {
            attemptFinishActivity();
            attemptCleanup();
        }
    }

    /**
     * Toggles whether the application is in fullscreen mode or not.
     *
     * @return {@code true} if in-call is now in fullscreen mode.
     */
    public boolean toggleFullscreenMode() {
        mIsFullScreen = !mIsFullScreen;
        Log.v(this, "toggleFullscreenMode = " + mIsFullScreen);
        notifyFullscreenModeChange(mIsFullScreen);
        return mIsFullScreen;
    }

    /**
     * Changes the fullscreen mode of the in-call UI.
     *
     * @param isFullScreen {@code true} if in-call should be in fullscreen mode, {@code false}
     *                                 otherwise.
     */
    public void setFullScreen(boolean isFullScreen) {
        Log.v(this, "setFullScreen = " + isFullScreen);
        if (mIsFullScreen == isFullScreen) {
            Log.v(this, "setFullScreen ignored as already in that state.");
            return;
        }
        mIsFullScreen = isFullScreen;
        notifyFullscreenModeChange(mIsFullScreen);
    }

    /**
     * @return {@code true} if the in-call ui is currently in fullscreen mode, {@code false}
     * otherwise.
     */
    public boolean isFullscreen() {
        return mIsFullScreen;
    }


    /**
     * Called by the {@link VideoCallPresenter} to inform of a change in full screen video status.
     *
     * @param isFullscreenMode {@code True} if entering full screen mode.
     */
    public void notifyFullscreenModeChange(boolean isFullscreenMode) {
        for (InCallEventListener listener : mInCallEventListeners) {
            listener.onFullscreenModeChanged(isFullscreenMode);
        }
    }

    /**
     * For some disconnected causes, we show a dialog.  This calls into the activity to show
     * the dialog if appropriate for the call.
     */
    private void maybeShowErrorDialogOnDisconnect(Call call) {
        // For newly disconnected calls, we may want to show a dialog on specific error conditions
        if (isActivityStarted() && call.getState() == Call.State.DISCONNECTED) {
            if (call.getAccountHandle() == null && !call.isConferenceCall()) {
                setDisconnectCauseForMissingAccounts(call);
            }
            mInCallActivity.maybeShowErrorDialogOnDisconnect(call.getDisconnectCause());
        }
    }

    /**
     * When the state of in-call changes, this is the first method to get called. It determines if
     * the UI needs to be started or finished depending on the new state and does it.
     */
    private InCallState startOrFinishUi(InCallState newState) {
        Log.d(this, "startOrFinishUi: " + mInCallState + " -> " + newState);

        // TODO: Consider a proper state machine implementation

        // If the state isn't changing we have already done any starting/stopping of activities in
        // a previous pass...so lets cut out early
        if (newState == mInCallState) {
            return newState;
        }

        // A new Incoming call means that the user needs to be notified of the the call (since
        // it wasn't them who initiated it).  We do this through full screen notifications and
        // happens indirectly through {@link StatusBarNotifier}.
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
        // we get an incoming call. Depending on the current context of the device, either a
        // incoming call HUN or the actual InCallActivity will be shown.
        final boolean startIncomingCallSequence = (InCallState.INCOMING == newState);

        // A dialog to show on top of the InCallUI to select a PhoneAccount
        final boolean showAccountPicker = (InCallState.WAITING_FOR_ACCOUNT == newState);

        // A new outgoing call indicates that the user just now dialed a number and when that
        // happens we need to display the screen immediately or show an account picker dialog if
        // no default is set. However, if the main InCallUI is already visible, we do not want to
        // re-initiate the start-up animation, so we do not need to do anything here.
        //
        // It is also possible to go into an intermediate state where the call has been initiated
        // but Telecomm has not yet returned with the details of the call (handle, gateway, etc.).
        // This pending outgoing state can also launch the call screen.
        //
        // This is different from the incoming call sequence because we do not need to shock the
        // user with a top-level notification.  Just show the call UI normally.
        final boolean mainUiNotVisible = !isShowingInCallUi() || !getCallCardFragmentVisible();
        boolean showCallUi = InCallState.OUTGOING == newState && mainUiNotVisible;

        // Direct transition from PENDING_OUTGOING -> INCALL means that there was an error in the
        // outgoing call process, so the UI should be brought up to show an error dialog.
        showCallUi |= (InCallState.PENDING_OUTGOING == mInCallState
                && InCallState.INCALL == newState && !isActivityStarted());

        // Another exception - InCallActivity is in charge of disconnecting a call with no
        // valid accounts set. Bring the UI up if this is true for the current pending outgoing
        // call so that:
        // 1) The call can be disconnected correctly
        // 2) The UI comes up and correctly displays the error dialog.
        // TODO: Remove these special case conditions by making InCallPresenter a true state
        // machine. Telecom should also be the component responsible for disconnecting a call
        // with no valid accounts.
        showCallUi |= InCallState.PENDING_OUTGOING == newState && mainUiNotVisible
                && isCallWithNoValidAccounts(mCallList.getPendingOutgoingCall());

        // The only time that we have an instance of mInCallActivity and it isn't started is
        // when it is being destroyed.  In that case, lets avoid bringing up another instance of
        // the activity.  When it is finally destroyed, we double check if we should bring it back
        // up so we aren't going to lose anything by avoiding a second startup here.
        boolean activityIsFinishing = mInCallActivity != null && !isActivityStarted();
        if (activityIsFinishing) {
            Log.i(this, "Undo the state change: " + newState + " -> " + mInCallState);
            return mInCallState;
        }

        if (showCallUi || showAccountPicker) {
            Log.i(this, "Start in call UI");
            showInCall(false /* showDialpad */, !showAccountPicker /* newOutgoingCall */);
        } else if (startIncomingCallSequence) {
            Log.i(this, "Start Full Screen in call UI");

            // We're about the bring up the in-call UI for an incoming call. If we still have
            // dialogs up, we need to clear them out before showing incoming screen.
            if (isActivityStarted()) {
                mInCallActivity.dismissPendingDialogs();
            }
            if (!startUi(newState)) {
                // startUI refused to start the UI. This indicates that it needed to restart the
                // activity.  When it finally restarts, it will call us back, so we do not actually
                // change the state yet (we return mInCallState instead of newState).
                return mInCallState;
            }
        } else if (newState == InCallState.NO_CALLS) {
            // The new state is the no calls state.  Tear everything down.
            attemptFinishActivity();
            attemptCleanup();
        }

        return newState;
    }

    /**
     * Determines whether or not a call has no valid phone accounts that can be used to make the
     * call with. Emergency calls do not require a phone account.
     *
     * @param call to check accounts for.
     * @return {@code true} if the call has no call capable phone accounts set, {@code false} if
     * the call contains a phone account that could be used to initiate it with, or is an emergency
     * call.
     */
    public static boolean isCallWithNoValidAccounts(Call call) {
        if (call != null && !call.isEmergencyCall()) {
            Bundle extras = call.getIntentExtras();

            if (extras == null) {
                extras = EMPTY_EXTRAS;
            }

            final List<PhoneAccountHandle> phoneAccountHandles = extras
                    .getParcelableArrayList(android.telecom.Call.AVAILABLE_PHONE_ACCOUNTS);

            if ((call.getAccountHandle() == null &&
                    (phoneAccountHandles == null || phoneAccountHandles.isEmpty()))) {
                Log.i(InCallPresenter.getInstance(), "No valid accounts for call " + call);
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the DisconnectCause for a call that was disconnected because it was missing a
     * PhoneAccount or PhoneAccounts to select from.
     * @param call
     */
    private void setDisconnectCauseForMissingAccounts(Call call) {
        android.telecom.Call telecomCall = call.getTelecommCall();

        Bundle extras = telecomCall.getDetails().getIntentExtras();
        // Initialize the extras bundle to avoid NPE
        if (extras == null) {
            extras = new Bundle();
        }

        final List<PhoneAccountHandle> phoneAccountHandles = extras.getParcelableArrayList(
                android.telecom.Call.AVAILABLE_PHONE_ACCOUNTS);

        if (phoneAccountHandles == null || phoneAccountHandles.isEmpty()) {
            String scheme = telecomCall.getDetails().getHandle().getScheme();
            final String errorMsg = PhoneAccount.SCHEME_TEL.equals(scheme) ?
                    mContext.getString(R.string.callFailed_simError) :
                        mContext.getString(R.string.incall_error_supp_service_unknown);
            DisconnectCause disconnectCause =
                    new DisconnectCause(DisconnectCause.ERROR, null, errorMsg, errorMsg);
            call.setDisconnectCause(disconnectCause);
        }
    }

    private boolean startUi(InCallState inCallState) {
        boolean isCallWaiting = mCallList.getActiveCall() != null &&
                mCallList.getIncomingCall() != null;

        // If the screen is off, we need to make sure it gets turned on for incoming calls.
        // This normally works just fine thanks to FLAG_TURN_SCREEN_ON but that only works
        // when the activity is first created. Therefore, to ensure the screen is turned on
        // for the call waiting case, we finish() the current activity and start a new one.
        // There should be no jank from this since the screen is already off and will remain so
        // until our new activity is up.

        if (isCallWaiting) {
            if (mProximitySensor.isScreenReallyOff() && isActivityStarted()) {
                Log.i(this, "Restarting InCallActivity to turn screen on for call waiting");
                mInCallActivity.finish();
                // When the activity actually finishes, we will start it again if there are
                // any active calls, so we do not need to start it explicitly here. Note, we
                // actually get called back on this function to restart it.

                // We return false to indicate that we did not actually start the UI.
                return false;
            } else {
                showInCall(false, false);
            }
        } else {
            mStatusBarNotifier.updateNotification(inCallState, mCallList);
        }
        return true;
    }

    /**
     * Checks to see if both the UI is gone and the service is disconnected. If so, tear it all
     * down.
     */
    private void attemptCleanup() {
        boolean shouldCleanup = (mInCallActivity == null && !mServiceConnected &&
                mInCallState == InCallState.NO_CALLS);
        Log.i(this, "attemptCleanup? " + shouldCleanup);

        if (shouldCleanup) {
            mIsActivityPreviouslyStarted = false;
            mIsChangingConfigurations = false;

            // blow away stale contact info so that we get fresh data on
            // the next set of calls
            if (mContactInfoCache != null) {
                mContactInfoCache.clearCache();
            }
            mContactInfoCache = null;

            if (mProximitySensor != null) {
                removeListener(mProximitySensor);
                mProximitySensor.tearDown();
            }
            mProximitySensor = null;

            mAudioModeProvider = null;

            if (mStatusBarNotifier != null) {
                removeListener(mStatusBarNotifier);
            }
            mStatusBarNotifier = null;

            if (mCallList != null) {
                mCallList.removeListener(this);
            }
            mCallList = null;

            mContext = null;
            mInCallActivity = null;

            mListeners.clear();
            mIncomingCallListeners.clear();
            mDetailsListeners.clear();
            mCanAddCallListeners.clear();
            mOrientationListeners.clear();
            mInCallEventListeners.clear();

            Log.d(this, "Finished InCallPresenter.CleanUp");
        }
    }

    public void showInCall(final boolean showDialpad, final boolean newOutgoingCall) {
        Log.i(this, "Showing InCallActivity");
        mContext.startActivity(getInCallIntent(showDialpad, newOutgoingCall));
    }

    public void onServiceBind() {
        mServiceBound = true;
    }

    public void onServiceUnbind() {
        InCallPresenter.getInstance().setBoundAndWaitingForOutgoingCall(false, null);
        mServiceBound = false;
    }

    public boolean isServiceBound() {
        return mServiceBound;
    }

    public void maybeStartRevealAnimation(Intent intent) {
        if (intent == null || mInCallActivity != null) {
            return;
        }
        final Bundle extras = intent.getBundleExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS);
        if (extras == null) {
            // Incoming call, just show the in-call UI directly.
            return;
        }

        if (extras.containsKey(android.telecom.Call.AVAILABLE_PHONE_ACCOUNTS)) {
            // Account selection dialog will show up so don't show the animation.
            return;
        }

        final PhoneAccountHandle accountHandle =
                intent.getParcelableExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE);
        final Point touchPoint = extras.getParcelable(TouchPointManager.TOUCH_POINT);

        InCallPresenter.getInstance().setBoundAndWaitingForOutgoingCall(true, accountHandle);

        final Intent incallIntent = getInCallIntent(false, true);
        incallIntent.putExtra(TouchPointManager.TOUCH_POINT, touchPoint);
        mContext.startActivity(incallIntent);
    }

    public Intent getInCallIntent(boolean showDialpad, boolean newOutgoingCall) {
        final Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_ACTIVITY_NEW_TASK);

        intent.setClass(mContext, InCallActivity.class);
        if (showDialpad) {
            intent.putExtra(InCallActivity.SHOW_DIALPAD_EXTRA, true);
        }
        intent.putExtra(InCallActivity.NEW_OUTGOING_CALL_EXTRA, newOutgoingCall);
        return intent;
    }

    /**
     * Retrieves the current in-call camera manager instance, creating if necessary.
     *
     * @return The {@link InCallCameraManager}.
     */
    public InCallCameraManager getInCallCameraManager() {
        synchronized(this) {
            if (mInCallCameraManager == null) {
                mInCallCameraManager = new InCallCameraManager(mContext);
            }

            return mInCallCameraManager;
        }
    }

    /**
     * Handles changes to the device rotation.
     *
     * @param rotation The device rotation (one of: {@link Surface#ROTATION_0},
     *      {@link Surface#ROTATION_90}, {@link Surface#ROTATION_180},
     *      {@link Surface#ROTATION_270}).
     */
    public void onDeviceRotationChange(int rotation) {
        Log.d(this, "onDeviceRotationChange: rotation=" + rotation);
        // First translate to rotation in degrees.
        if (mCallList != null) {
            mCallList.notifyCallsOfDeviceRotation(toRotationAngle(rotation));
        } else {
            Log.w(this, "onDeviceRotationChange: CallList is null.");
        }
    }

    /**
     * Converts rotation constants to rotation in degrees.
     * @param rotation Rotation constants (one of: {@link Surface#ROTATION_0},
     *      {@link Surface#ROTATION_90}, {@link Surface#ROTATION_180},
     *      {@link Surface#ROTATION_270}).
     */
    public static int toRotationAngle(int rotation) {
        int rotationAngle;
        switch (rotation) {
            case Surface.ROTATION_0:
                rotationAngle = 0;
                break;
            case Surface.ROTATION_90:
                rotationAngle = 90;
                break;
            case Surface.ROTATION_180:
                rotationAngle = 180;
                break;
            case Surface.ROTATION_270:
                rotationAngle = 270;
                break;
            default:
                rotationAngle = 0;
        }
        return rotationAngle;
    }

    /**
     * Notifies listeners of changes in orientation (e.g. portrait/landscape).
     *
     * @param orientation The orientation of the device (one of: {@link Surface#ROTATION_0},
     *      {@link Surface#ROTATION_90}, {@link Surface#ROTATION_180},
     *      {@link Surface#ROTATION_270}).
     */
    public void onDeviceOrientationChange(int orientation) {
        for (InCallOrientationListener listener : mOrientationListeners) {
            listener.onDeviceOrientationChanged(orientation);
        }
    }

    /**
     * Configures the in-call UI activity so it can change orientations or not.
     *
     * @param allowOrientationChange {@code True} if the in-call UI can change between portrait
     *      and landscape.  {@Code False} if the in-call UI should be locked in portrait.
     */
    public void setInCallAllowsOrientationChange(boolean allowOrientationChange) {
        if (mInCallActivity == null) {
            Log.e(this, "InCallActivity is null. Can't set requested orientation.");
            return;
        }

        if (!allowOrientationChange) {
            mInCallActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        } else {
            mInCallActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        }
    }

    public void enableScreenTimeout(boolean enable) {
        Log.v(this, "enableScreenTimeout: value=" + enable);
        if (mInCallActivity == null) {
            Log.e(this, "enableScreenTimeout: InCallActivity is null.");
            return;
        }

        final Window window = mInCallActivity.getWindow();
        if (enable) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    /**
     * Returns the space available beside the call card.
     *
     * @return The space beside the call card.
     */
    public float getSpaceBesideCallCard() {
        if (mInCallActivity != null && mInCallActivity.getCallCardFragment() != null) {
            return mInCallActivity.getCallCardFragment().getSpaceBesideCallCard();
        }
        return 0;
    }

    /**
     * Returns whether the call card fragment is currently visible.
     *
     * @return True if the call card fragment is visible.
     */
    public boolean getCallCardFragmentVisible() {
        if (mInCallActivity != null && mInCallActivity.getCallCardFragment() != null) {
            return mInCallActivity.getCallCardFragment().isVisible();
        }
        return false;
    }

    /**
     * Hides or shows the conference manager fragment.
     *
     * @param show {@code true} if the conference manager should be shown, {@code false} if it
     *                         should be hidden.
     */
    public void showConferenceCallManager(boolean show) {
        if (mInCallActivity == null) {
            return;
        }

        mInCallActivity.showConferenceFragment(show);
    }

    /**
     * @return True if the application is currently running in a right-to-left locale.
     */
    public static boolean isRtl() {
        return TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) ==
                View.LAYOUT_DIRECTION_RTL;
    }

    /**
     * Extract background color from call object. The theme colors will include a primary color
     * and a secondary color.
     */
    public void setThemeColors() {
        // This method will set the background to default if the color is PhoneAccount.NO_COLOR.
        mThemeColors = getColorsFromCall(mCallList.getFirstCall());

        if (mInCallActivity == null) {
            return;
        }

        final Resources resources = mInCallActivity.getResources();
        final int color;
        if (resources.getBoolean(R.bool.is_layout_landscape)) {
            color = resources.getColor(R.color.statusbar_background_color, null);
        } else {
            color = mThemeColors.mSecondaryColor;
        }

        mInCallActivity.getWindow().setStatusBarColor(color);
        final TaskDescription td = new TaskDescription(
                resources.getString(R.string.notification_ongoing_call), null, color);
        mInCallActivity.setTaskDescription(td);
    }

    /**
     * @return A palette for colors to display in the UI.
     */
    public MaterialPalette getThemeColors() {
        return mThemeColors;
    }

    private MaterialPalette getColorsFromCall(Call call) {
        if (call == null) {
            return getColorsFromPhoneAccountHandle(mPendingPhoneAccountHandle);
        } else {
            return getColorsFromPhoneAccountHandle(call.getAccountHandle());
        }
    }

    private MaterialPalette getColorsFromPhoneAccountHandle(PhoneAccountHandle phoneAccountHandle) {
        int highlightColor = PhoneAccount.NO_HIGHLIGHT_COLOR;
        if (phoneAccountHandle != null) {
            final TelecomManager tm = getTelecomManager();

            if (tm != null) {
                final PhoneAccount account = tm.getPhoneAccount(phoneAccountHandle);
                // For single-sim devices, there will be no selected highlight color, so the phone
                // account will default to NO_HIGHLIGHT_COLOR.
                if (account != null) {
                    highlightColor = account.getHighlightColor();
                }
            }
        }
        return new InCallUIMaterialColorMapUtils(
                mContext.getResources()).calculatePrimaryAndSecondaryColor(highlightColor);
    }

    /**
     * @return An instance of TelecomManager.
     */
    public TelecomManager getTelecomManager() {
        if (mTelecomManager == null) {
            mTelecomManager = (TelecomManager)
                    mContext.getSystemService(Context.TELECOM_SERVICE);
        }
        return mTelecomManager;
    }

    InCallActivity getActivity() {
        return mInCallActivity;
    }

    AnswerPresenter getAnswerPresenter() {
        return mAnswerPresenter;
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
        NO_CALLS,

        // Incoming-call screen is up
        INCOMING,

        // In-call experience is showing
        INCALL,

        // Waiting for user input before placing outgoing call
        WAITING_FOR_ACCOUNT,

        // UI is starting up but no call has been initiated yet.
        // The UI is waiting for Telecomm to respond.
        PENDING_OUTGOING,

        // User is dialing out
        OUTGOING;

        public boolean isIncoming() {
            return (this == INCOMING);
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
        // TODO: Enhance state to contain the call objects instead of passing CallList
        public void onStateChange(InCallState oldState, InCallState newState, CallList callList);
    }

    public interface IncomingCallListener {
        public void onIncomingCall(InCallState oldState, InCallState newState, Call call);
    }

    public interface CanAddCallListener {
        public void onCanAddCallChanged(boolean canAddCall);
    }

    public interface InCallDetailsListener {
        public void onDetailsChanged(Call call, android.telecom.Call.Details details);
    }

    public interface InCallOrientationListener {
        public void onDeviceOrientationChanged(int orientation);
    }

    /**
     * Interface implemented by classes that need to know about events which occur within the
     * In-Call UI.  Used as a means of communicating between fragments that make up the UI.
     */
    public interface InCallEventListener {
        public void onFullscreenModeChanged(boolean isFullscreenMode);
    }

    public interface InCallUiListener {
        void onUiShowing(boolean showing);
    }
}
