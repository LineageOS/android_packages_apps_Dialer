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
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.os.UserManagerCompat;
import android.telecom.Call.Details;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.Window;
import android.view.WindowManager;
import com.android.contacts.common.compat.CallCompat;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler.OnCheckBlockedListener;
import com.android.dialer.blocking.FilteredNumberCompat;
import com.android.dialer.blocking.FilteredNumbersUtil;
import com.android.dialer.common.LogUtil;
import com.android.dialer.enrichedcall.EnrichedCallComponent;
import com.android.dialer.location.GeoUtil;
import com.android.dialer.logging.InteractionEvent;
import com.android.dialer.logging.Logger;
import com.android.dialer.postcall.PostCall;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.TouchPointManager;
import com.android.incallui.InCallOrientationEventListener.ScreenOrientation;
import com.android.incallui.answerproximitysensor.PseudoScreenState;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.ExternalCallList;
import com.android.incallui.call.TelecomAdapter;
import com.android.incallui.disconnectdialog.DisconnectMessage;
import com.android.incallui.latencyreport.LatencyReport;
import com.android.incallui.legacyblocking.BlockedNumberContentObserver;
import com.android.incallui.spam.SpamCallListListener;
import com.android.incallui.util.TelecomCallUtil;
import com.android.incallui.videosurface.bindings.VideoSurfaceBindings;
import com.android.incallui.videosurface.protocol.VideoSurfaceTexture;
import com.android.incallui.videotech.utils.VideoUtils;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Takes updates from the CallList and notifies the InCallActivity (UI) of the changes. Responsible
 * for starting the activity for a new call and finishing the activity when all calls are
 * disconnected. Creates and manages the in-call state and provides a listener pattern for the
 * presenters that want to listen in on the in-call state changes. TODO: This class has become more
 * of a state machine at this point. Consider renaming.
 */
public class InCallPresenter implements CallList.Listener {

  private static final String EXTRA_FIRST_TIME_SHOWN =
      "com.android.incallui.intent.extra.FIRST_TIME_SHOWN";

  private static final long BLOCK_QUERY_TIMEOUT_MS = 1000;

  private static final Bundle EMPTY_EXTRAS = new Bundle();

  private static InCallPresenter sInCallPresenter;

  /**
   * ConcurrentHashMap constructor params: 8 is initial table size, 0.9f is load factor before
   * resizing, 1 means we only expect a single thread to access the map so make only a single shard
   */
  private final Set<InCallStateListener> mListeners =
      Collections.newSetFromMap(new ConcurrentHashMap<InCallStateListener, Boolean>(8, 0.9f, 1));

  private final List<IncomingCallListener> mIncomingCallListeners = new CopyOnWriteArrayList<>();
  private final Set<InCallDetailsListener> mDetailsListeners =
      Collections.newSetFromMap(new ConcurrentHashMap<InCallDetailsListener, Boolean>(8, 0.9f, 1));
  private final Set<CanAddCallListener> mCanAddCallListeners =
      Collections.newSetFromMap(new ConcurrentHashMap<CanAddCallListener, Boolean>(8, 0.9f, 1));
  private final Set<InCallUiListener> mInCallUiListeners =
      Collections.newSetFromMap(new ConcurrentHashMap<InCallUiListener, Boolean>(8, 0.9f, 1));
  private final Set<InCallOrientationListener> mOrientationListeners =
      Collections.newSetFromMap(
          new ConcurrentHashMap<InCallOrientationListener, Boolean>(8, 0.9f, 1));
  private final Set<InCallEventListener> mInCallEventListeners =
      Collections.newSetFromMap(new ConcurrentHashMap<InCallEventListener, Boolean>(8, 0.9f, 1));

  private StatusBarNotifier mStatusBarNotifier;
  private ExternalCallNotifier mExternalCallNotifier;
  private InCallVibrationHandler mVibrationHandler;
  private ContactInfoCache mContactInfoCache;
  private Context mContext;
  private final OnCheckBlockedListener mOnCheckBlockedListener =
      new OnCheckBlockedListener() {
        @Override
        public void onCheckComplete(final Integer id) {
          if (id != null && id != FilteredNumberAsyncQueryHandler.INVALID_ID) {
            // Silence the ringer now to prevent ringing and vibration before the call is
            // terminated when Telecom attempts to add it.
            TelecomUtil.silenceRinger(mContext);
          }
        }
      };
  private CallList mCallList;
  private ExternalCallList mExternalCallList;
  private InCallActivity mInCallActivity;
  private ManageConferenceActivity mManageConferenceActivity;
  private final android.telecom.Call.Callback mCallCallback =
      new android.telecom.Call.Callback() {
        @Override
        public void onPostDialWait(
            android.telecom.Call telecomCall, String remainingPostDialSequence) {
          final DialerCall call = mCallList.getDialerCallFromTelecomCall(telecomCall);
          if (call == null) {
            LogUtil.w(
                "InCallPresenter.onPostDialWait",
                "DialerCall not found in call list: " + telecomCall);
            return;
          }
          onPostDialCharWait(call.getId(), remainingPostDialSequence);
        }

        @Override
        public void onDetailsChanged(
            android.telecom.Call telecomCall, android.telecom.Call.Details details) {
          final DialerCall call = mCallList.getDialerCallFromTelecomCall(telecomCall);
          if (call == null) {
            LogUtil.w(
                "InCallPresenter.onDetailsChanged",
                "DialerCall not found in call list: " + telecomCall);
            return;
          }

          if (details.hasProperty(Details.PROPERTY_IS_EXTERNAL_CALL)
              && !mExternalCallList.isCallTracked(telecomCall)) {

            // A regular call became an external call so swap call lists.
            LogUtil.i("InCallPresenter.onDetailsChanged", "Call became external: " + telecomCall);
            mCallList.onInternalCallMadeExternal(mContext, telecomCall);
            mExternalCallList.onCallAdded(telecomCall);
            return;
          }

          for (InCallDetailsListener listener : mDetailsListeners) {
            listener.onDetailsChanged(call, details);
          }
        }

        @Override
        public void onConferenceableCallsChanged(
            android.telecom.Call telecomCall, List<android.telecom.Call> conferenceableCalls) {
          LogUtil.i(
              "InCallPresenter.onConferenceableCallsChanged",
              "onConferenceableCallsChanged: " + telecomCall);
          onDetailsChanged(telecomCall, telecomCall.getDetails());
        }
      };
  private InCallState mInCallState = InCallState.NO_CALLS;
  private ProximitySensor mProximitySensor;
  private final PseudoScreenState mPseudoScreenState = new PseudoScreenState();
  private boolean mServiceConnected;
  private InCallCameraManager mInCallCameraManager;
  private FilteredNumberAsyncQueryHandler mFilteredQueryHandler;
  private CallList.Listener mSpamCallListListener;
  /** Whether or not we are currently bound and waiting for Telecom to send us a new call. */
  private boolean mBoundAndWaitingForOutgoingCall;
  /** Determines if the InCall UI is in fullscreen mode or not. */
  private boolean mIsFullScreen = false;

  private boolean mScreenTimeoutEnabled = true;

  private PhoneStateListener mPhoneStateListener =
      new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
          if (state == TelephonyManager.CALL_STATE_RINGING) {
            if (FilteredNumbersUtil.hasRecentEmergencyCall(mContext)) {
              return;
            }
            // Check if the number is blocked, to silence the ringer.
            String countryIso = GeoUtil.getCurrentCountryIso(mContext);
            mFilteredQueryHandler.isBlockedNumber(
                mOnCheckBlockedListener, incomingNumber, countryIso);
          }
        }
      };
  /**
   * Is true when the activity has been previously started. Some code needs to know not just if the
   * activity is currently up, but if it had been previously shown in foreground for this in-call
   * session (e.g., StatusBarNotifier). This gets reset when the session ends in the tear-down
   * method.
   */
  private boolean mIsActivityPreviouslyStarted = false;

  /** Whether or not InCallService is bound to Telecom. */
  private boolean mServiceBound = false;

  /**
   * When configuration changes Android kills the current activity and starts a new one. The flag is
   * used to check if full clean up is necessary (activity is stopped and new activity won't be
   * started), or if a new activity will be started right after the current one is destroyed, and
   * therefore no need in release all resources.
   */
  private boolean mIsChangingConfigurations = false;

  private boolean mAwaitingCallListUpdate = false;

  private ExternalCallList.ExternalCallListener mExternalCallListener =
      new ExternalCallList.ExternalCallListener() {

        @Override
        public void onExternalCallPulled(android.telecom.Call call) {
          // Note: keep this code in sync with InCallPresenter#onCallAdded
          LatencyReport latencyReport = new LatencyReport(call);
          latencyReport.onCallBlockingDone();
          // Note: External calls do not require spam checking.
          mCallList.onCallAdded(mContext, call, latencyReport);
          call.registerCallback(mCallCallback);
        }

        @Override
        public void onExternalCallAdded(android.telecom.Call call) {
          // No-op
        }

        @Override
        public void onExternalCallRemoved(android.telecom.Call call) {
          // No-op
        }

        @Override
        public void onExternalCallUpdated(android.telecom.Call call) {
          // No-op
        }
      };

  private ThemeColorManager mThemeColorManager;
  private VideoSurfaceTexture mLocalVideoSurfaceTexture;
  private VideoSurfaceTexture mRemoteVideoSurfaceTexture;

  /** Inaccessible constructor. Must use getRunningInstance() to get this singleton. */
  @VisibleForTesting
  InCallPresenter() {}

  public static synchronized InCallPresenter getInstance() {
    if (sInCallPresenter == null) {
      sInCallPresenter = new InCallPresenter();
    }
    return sInCallPresenter;
  }

  @VisibleForTesting
  public static synchronized void setInstanceForTesting(InCallPresenter inCallPresenter) {
    sInCallPresenter = inCallPresenter;
  }

  /**
   * Determines whether or not a call has no valid phone accounts that can be used to make the call
   * with. Emergency calls do not require a phone account.
   *
   * @param call to check accounts for.
   * @return {@code true} if the call has no call capable phone accounts set, {@code false} if the
   *     call contains a phone account that could be used to initiate it with, or is an emergency
   *     call.
   */
  public static boolean isCallWithNoValidAccounts(DialerCall call) {
    if (call != null && !call.isEmergencyCall()) {
      Bundle extras = call.getIntentExtras();

      if (extras == null) {
        extras = EMPTY_EXTRAS;
      }

      final List<PhoneAccountHandle> phoneAccountHandles =
          extras.getParcelableArrayList(android.telecom.Call.AVAILABLE_PHONE_ACCOUNTS);

      if ((call.getAccountHandle() == null
          && (phoneAccountHandles == null || phoneAccountHandles.isEmpty()))) {
        LogUtil.i(
            "InCallPresenter.isCallWithNoValidAccounts", "No valid accounts for call " + call);
        return true;
      }
    }
    return false;
  }

  public InCallState getInCallState() {
    return mInCallState;
  }

  public CallList getCallList() {
    return mCallList;
  }

  public void setUp(
      @NonNull Context context,
      CallList callList,
      ExternalCallList externalCallList,
      StatusBarNotifier statusBarNotifier,
      ExternalCallNotifier externalCallNotifier,
      ContactInfoCache contactInfoCache,
      ProximitySensor proximitySensor,
      FilteredNumberAsyncQueryHandler filteredNumberQueryHandler) {
    if (mServiceConnected) {
      LogUtil.i("InCallPresenter.setUp", "New service connection replacing existing one.");
      if (context != mContext || callList != mCallList) {
        throw new IllegalStateException();
      }
      return;
    }

    Objects.requireNonNull(context);
    mContext = context;

    mContactInfoCache = contactInfoCache;

    mStatusBarNotifier = statusBarNotifier;
    mExternalCallNotifier = externalCallNotifier;
    addListener(mStatusBarNotifier);
    EnrichedCallComponent.get(mContext)
        .getEnrichedCallManager()
        .registerStateChangedListener(mStatusBarNotifier);

    mVibrationHandler = new InCallVibrationHandler(context);
    addListener(mVibrationHandler);

    mProximitySensor = proximitySensor;
    addListener(mProximitySensor);

    mThemeColorManager =
        new ThemeColorManager(new InCallUIMaterialColorMapUtils(mContext.getResources()));

    mCallList = callList;
    mExternalCallList = externalCallList;
    externalCallList.addExternalCallListener(mExternalCallNotifier);
    externalCallList.addExternalCallListener(mExternalCallListener);

    // This only gets called by the service so this is okay.
    mServiceConnected = true;

    // The final thing we do in this set up is add ourselves as a listener to CallList.  This
    // will kick off an update and the whole process can start.
    mCallList.addListener(this);

    // Create spam call list listener and add it to the list of listeners
    mSpamCallListListener = new SpamCallListListener(context);
    mCallList.addListener(mSpamCallListListener);

    VideoPauseController.getInstance().setUp(this);

    mFilteredQueryHandler = filteredNumberQueryHandler;
    mContext
        .getSystemService(TelephonyManager.class)
        .listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

    LogUtil.d("InCallPresenter.setUp", "Finished InCallPresenter.setUp");
  }

  /**
   * Called when the telephony service has disconnected from us. This will happen when there are no
   * more active calls. However, we may still want to continue showing the UI for certain cases like
   * showing "Call Ended". What we really want is to wait for the activity and the service to both
   * disconnect before we tear things down. This method sets a serviceConnected boolean and calls a
   * secondary method that performs the aforementioned logic.
   */
  public void tearDown() {
    LogUtil.d("InCallPresenter.tearDown", "tearDown");
    mCallList.clearOnDisconnect();

    mServiceConnected = false;

    mContext
        .getSystemService(TelephonyManager.class)
        .listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);

    attemptCleanup();
    VideoPauseController.getInstance().tearDown();
  }

  private void attemptFinishActivity() {
    mScreenTimeoutEnabled = true;
    final boolean doFinish = (mInCallActivity != null && isActivityStarted());
    LogUtil.i("InCallPresenter.attemptFinishActivity", "Hide in call UI: " + doFinish);
    if (doFinish) {
      mInCallActivity.setExcludeFromRecents(true);
      mInCallActivity.finish();
    }
  }

  /**
   * Called when the UI ends. Attempts to tear down everything if necessary. See {@link #tearDown()}
   * for more insight on the tear-down process.
   */
  public void unsetActivity(InCallActivity inCallActivity) {
    if (inCallActivity == null) {
      throw new IllegalArgumentException("unregisterActivity cannot be called with null");
    }
    if (mInCallActivity == null) {
      LogUtil.i(
          "InCallPresenter.unsetActivity", "No InCallActivity currently set, no need to unset.");
      return;
    }
    if (mInCallActivity != inCallActivity) {
      LogUtil.w(
          "InCallPresenter.unsetActivity",
          "Second instance of InCallActivity is trying to unregister when another"
              + " instance is active. Ignoring.");
      return;
    }
    updateActivity(null);
  }

  /**
   * Updates the current instance of {@link InCallActivity} with the provided one. If a {@code null}
   * activity is provided, it means that the activity was finished and we should attempt to cleanup.
   */
  private void updateActivity(InCallActivity inCallActivity) {
    boolean updateListeners = false;
    boolean doAttemptCleanup = false;

    if (inCallActivity != null) {
      if (mInCallActivity == null) {
        updateListeners = true;
        LogUtil.i("InCallPresenter.updateActivity", "UI Initialized");
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
        LogUtil.i("InCallPresenter.updateActivity", "UI Initialized, but no calls left. Shut down");
        attemptFinishActivity();
        return;
      }
    } else {
      LogUtil.i("InCallPresenter.updateActivity", "UI Destroyed");
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

  public void setManageConferenceActivity(
      @Nullable ManageConferenceActivity manageConferenceActivity) {
    mManageConferenceActivity = manageConferenceActivity;
  }

  public void onBringToForeground(boolean showDialpad) {
    LogUtil.i("InCallPresenter.onBringToForeground", "Bringing UI to foreground.");
    bringToForeground(showDialpad);
  }

  public void onCallAdded(final android.telecom.Call call) {
    LatencyReport latencyReport = new LatencyReport(call);
    if (shouldAttemptBlocking(call)) {
      maybeBlockCall(call, latencyReport);
    } else {
      if (call.getDetails().hasProperty(CallCompat.Details.PROPERTY_IS_EXTERNAL_CALL)) {
        mExternalCallList.onCallAdded(call);
      } else {
        latencyReport.onCallBlockingDone();
        mCallList.onCallAdded(mContext, call, latencyReport);
      }
    }

    // Since a call has been added we are no longer waiting for Telecom to send us a call.
    setBoundAndWaitingForOutgoingCall(false, null);
    call.registerCallback(mCallCallback);
  }

  private boolean shouldAttemptBlocking(android.telecom.Call call) {
    if (call.getState() != android.telecom.Call.STATE_RINGING) {
      return false;
    }
    if (!UserManagerCompat.isUserUnlocked(mContext)) {
      LogUtil.i(
          "InCallPresenter.shouldAttemptBlocking",
          "not attempting to block incoming call because user is locked");
      return false;
    }
    if (TelecomCallUtil.isEmergencyCall(call)) {
      LogUtil.i(
          "InCallPresenter.shouldAttemptBlocking",
          "Not attempting to block incoming emergency call");
      return false;
    }
    if (FilteredNumbersUtil.hasRecentEmergencyCall(mContext)) {
      LogUtil.i(
          "InCallPresenter.shouldAttemptBlocking",
          "Not attempting to block incoming call due to recent emergency call");
      return false;
    }
    if (call.getDetails().hasProperty(CallCompat.Details.PROPERTY_IS_EXTERNAL_CALL)) {
      return false;
    }
    if (FilteredNumberCompat.useNewFiltering(mContext)) {
      LogUtil.i(
          "InCallPresenter.shouldAttemptBlocking",
          "not attempting to block incoming call because framework blocking is in use");
      return false;
    }
    return true;
  }

  /**
   * Checks whether a call should be blocked, and blocks it if so. Otherwise, it adds the call to
   * the CallList so it can proceed as normal. There is a timeout, so if the function for checking
   * whether a function is blocked does not return in a reasonable time, we proceed with adding the
   * call anyways.
   */
  private void maybeBlockCall(final android.telecom.Call call, final LatencyReport latencyReport) {
    final String countryIso = GeoUtil.getCurrentCountryIso(mContext);
    final String number = TelecomCallUtil.getNumber(call);
    final long timeAdded = System.currentTimeMillis();

    // Though AtomicBoolean's can be scary, don't fear, as in this case it is only used on the
    // main UI thread. It is needed so we can change its value within different scopes, since
    // that cannot be done with a final boolean.
    final AtomicBoolean hasTimedOut = new AtomicBoolean(false);

    final Handler handler = new Handler();

    // Proceed if the query is slow; the call may still be blocked after the query returns.
    final Runnable runnable =
        new Runnable() {
          @Override
          public void run() {
            hasTimedOut.set(true);
            latencyReport.onCallBlockingDone();
            mCallList.onCallAdded(mContext, call, latencyReport);
          }
        };
    handler.postDelayed(runnable, BLOCK_QUERY_TIMEOUT_MS);

    OnCheckBlockedListener onCheckBlockedListener =
        new OnCheckBlockedListener() {
          @Override
          public void onCheckComplete(final Integer id) {
            if (isReadyForTearDown()) {
              LogUtil.i("InCallPresenter.onCheckComplete", "torn down, not adding call");
              return;
            }
            if (!hasTimedOut.get()) {
              handler.removeCallbacks(runnable);
            }
            if (id == null) {
              if (!hasTimedOut.get()) {
                latencyReport.onCallBlockingDone();
                mCallList.onCallAdded(mContext, call, latencyReport);
              }
            } else if (id == FilteredNumberAsyncQueryHandler.INVALID_ID) {
              LogUtil.d(
                  "InCallPresenter.onCheckComplete", "invalid number, skipping block checking");
              if (!hasTimedOut.get()) {
                handler.removeCallbacks(runnable);

                latencyReport.onCallBlockingDone();
                mCallList.onCallAdded(mContext, call, latencyReport);
              }
            } else {
              LogUtil.i(
                  "InCallPresenter.onCheckComplete", "Rejecting incoming call from blocked number");
              call.reject(false, null);
              Logger.get(mContext).logInteraction(InteractionEvent.Type.CALL_BLOCKED);

              /*
               * If mContext is null, then the InCallPresenter was torn down before the
               * block check had a chance to complete. The context is no longer valid, so
               * don't attempt to remove the call log entry.
               */
              if (mContext == null) {
                return;
              }
              // Register observer to update the call log.
              // BlockedNumberContentObserver will unregister after successful log or timeout.
              BlockedNumberContentObserver contentObserver =
                  new BlockedNumberContentObserver(mContext, new Handler(), number, timeAdded);
              contentObserver.register();
            }
          }
        };

    mFilteredQueryHandler.isBlockedNumber(onCheckBlockedListener, number, countryIso);
  }

  public void onCallRemoved(android.telecom.Call call) {
    if (call.getDetails().hasProperty(CallCompat.Details.PROPERTY_IS_EXTERNAL_CALL)) {
      mExternalCallList.onCallRemoved(call);
    } else {
      mCallList.onCallRemoved(mContext, call);
      call.unregisterCallback(mCallCallback);
    }
  }

  public void onCanAddCallChanged(boolean canAddCall) {
    for (CanAddCallListener listener : mCanAddCallListeners) {
      listener.onCanAddCallChanged(canAddCall);
    }
  }

  @Override
  public void onWiFiToLteHandover(DialerCall call) {
    if (mInCallActivity != null) {
      mInCallActivity.onWiFiToLteHandover(call);
    }
  }

  @Override
  public void onHandoverToWifiFailed(DialerCall call) {
    if (mInCallActivity != null) {
      mInCallActivity.onHandoverToWifiFailed(call);
    }
  }

  @Override
  public void onInternationalCallOnWifi(@NonNull DialerCall call) {
    LogUtil.enterBlock("InCallPresenter.onInternationalCallOnWifi");
    if (mInCallActivity != null) {
      mInCallActivity.onInternationalCallOnWifi(call);
    }
  }

  /**
   * Called when there is a change to the call list. Sets the In-Call state for the entire in-call
   * app based on the information it gets from CallList. Dispatches the in-call state to all
   * listeners. Can trigger the creation or destruction of the UI based on the states that is
   * calculates.
   */
  @Override
  public void onCallListChange(CallList callList) {
    if (mInCallActivity != null && mInCallActivity.isInCallScreenAnimating()) {
      mAwaitingCallListUpdate = true;
      return;
    }
    if (callList == null) {
      return;
    }

    mAwaitingCallListUpdate = false;

    InCallState newState = getPotentialStateFromCallList(callList);
    InCallState oldState = mInCallState;
    LogUtil.d(
        "InCallPresenter.onCallListChange",
        "onCallListChange oldState= " + oldState + " newState=" + newState);

    // If the user placed a call and was asked to choose the account, but then pressed "Home", the
    // incall activity for that call will still exist (even if it's not visible). In the case of
    // an incoming call in that situation, just disconnect that "waiting for account" call and
    // dismiss the dialog. The same activity will be reused to handle the new incoming call. See
    // b/33247755 for more details.
    DialerCall waitingForAccountCall;
    if (newState == InCallState.INCOMING
        && (waitingForAccountCall = callList.getWaitingForAccountCall()) != null) {
      waitingForAccountCall.disconnect();
      // The InCallActivity might be destroyed or not started yet at this point.
      if (isActivityStarted()) {
        mInCallActivity.dismissPendingDialogs();
      }
    }

    newState = startOrFinishUi(newState);
    LogUtil.d(
        "InCallPresenter.onCallListChange", "onCallListChange newState changed to " + newState);

    // Set the new state before announcing it to the world
    LogUtil.i(
        "InCallPresenter.onCallListChange",
        "Phone switching state: " + oldState + " -> " + newState);
    mInCallState = newState;

    // notify listeners of new state
    for (InCallStateListener listener : mListeners) {
      LogUtil.d(
          "InCallPresenter.onCallListChange",
          "Notify " + listener + " of state " + mInCallState.toString());
      listener.onStateChange(oldState, mInCallState, callList);
    }

    if (isActivityStarted()) {
      final boolean hasCall =
          callList.getActiveOrBackgroundCall() != null || callList.getOutgoingCall() != null;
      mInCallActivity.dismissKeyguard(hasCall);
    }
  }

  /** Called when there is a new incoming call. */
  @Override
  public void onIncomingCall(DialerCall call) {
    InCallState newState = startOrFinishUi(InCallState.INCOMING);
    InCallState oldState = mInCallState;

    LogUtil.i(
        "InCallPresenter.onIncomingCall", "Phone switching state: " + oldState + " -> " + newState);
    mInCallState = newState;

    for (IncomingCallListener listener : mIncomingCallListeners) {
      listener.onIncomingCall(oldState, mInCallState, call);
    }

    if (mInCallActivity != null) {
      // Re-evaluate which fragment is being shown.
      mInCallActivity.onPrimaryCallStateChanged();
    }
  }

  @Override
  public void onUpgradeToVideo(DialerCall call) {
    if (VideoUtils.hasReceivedVideoUpgradeRequest(call.getVideoTech().getSessionModificationState())
        && mInCallState == InCallPresenter.InCallState.INCOMING) {
      LogUtil.i(
          "InCallPresenter.onUpgradeToVideo",
          "rejecting upgrade request due to existing incoming call");
      call.getVideoTech().declineVideoRequest();
    }

    if (mInCallActivity != null) {
      // Re-evaluate which fragment is being shown.
      mInCallActivity.onPrimaryCallStateChanged();
    }
  }

  @Override
  public void onSessionModificationStateChange(DialerCall call) {
    int newState = call.getVideoTech().getSessionModificationState();
    LogUtil.i("InCallPresenter.onSessionModificationStateChange", "state: %d", newState);
    if (mProximitySensor == null) {
      LogUtil.i("InCallPresenter.onSessionModificationStateChange", "proximitySensor is null");
      return;
    }
    mProximitySensor.setIsAttemptingVideoCall(
        call.hasSentVideoUpgradeRequest() || call.hasReceivedVideoUpgradeRequest());
    if (mInCallActivity != null) {
      // Re-evaluate which fragment is being shown.
      mInCallActivity.onPrimaryCallStateChanged();
    }
  }

  /**
   * Called when a call becomes disconnected. Called everytime an existing call changes from being
   * connected (incoming/outgoing/active) to disconnected.
   */
  @Override
  public void onDisconnect(DialerCall call) {
    maybeShowErrorDialogOnDisconnect(call);

    // We need to do the run the same code as onCallListChange.
    onCallListChange(mCallList);

    if (isActivityStarted()) {
      mInCallActivity.dismissKeyguard(false);
    }

    if (call.isEmergencyCall()) {
      FilteredNumbersUtil.recordLastEmergencyCallTime(mContext);
    }

    if (!mCallList.hasLiveCall()
        && !call.getLogState().isIncoming
        && !isSecretCode(call.getNumber())
        && !CallerInfoUtils.isVoiceMailNumber(mContext, call)) {
      PostCall.onCallDisconnected(mContext, call.getNumber(), call.getConnectTimeMillis());
    }
  }

  private boolean isSecretCode(@Nullable String number) {
    return number != null
        && (number.length() <= 8 || number.startsWith("*#*#") || number.endsWith("#*#*"));
  }

  /** Given the call list, return the state in which the in-call screen should be. */
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
    } else if (callList.getActiveCall() != null
        || callList.getBackgroundCall() != null
        || callList.getDisconnectedCall() != null
        || callList.getDisconnectingCall() != null) {
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
    LogUtil.i(
        "InCallPresenter.setBoundAndWaitingForOutgoingCall",
        "setBoundAndWaitingForOutgoingCall: " + isBound);
    mBoundAndWaitingForOutgoingCall = isBound;
    mThemeColorManager.setPendingPhoneAccountHandle(handle);
    if (isBound && mInCallState == InCallState.NO_CALLS) {
      mInCallState = InCallState.OUTGOING;
    }
  }

  public void onShrinkAnimationComplete() {
    if (mAwaitingCallListUpdate) {
      onCallListChange(mCallList);
    }
  }

  public void addIncomingCallListener(IncomingCallListener listener) {
    Objects.requireNonNull(listener);
    mIncomingCallListeners.add(listener);
  }

  public void removeIncomingCallListener(IncomingCallListener listener) {
    if (listener != null) {
      mIncomingCallListeners.remove(listener);
    }
  }

  public void addListener(InCallStateListener listener) {
    Objects.requireNonNull(listener);
    mListeners.add(listener);
  }

  public void removeListener(InCallStateListener listener) {
    if (listener != null) {
      mListeners.remove(listener);
    }
  }

  public void addDetailsListener(InCallDetailsListener listener) {
    Objects.requireNonNull(listener);
    mDetailsListeners.add(listener);
  }

  public void removeDetailsListener(InCallDetailsListener listener) {
    if (listener != null) {
      mDetailsListeners.remove(listener);
    }
  }

  public void addCanAddCallListener(CanAddCallListener listener) {
    Objects.requireNonNull(listener);
    mCanAddCallListeners.add(listener);
  }

  public void removeCanAddCallListener(CanAddCallListener listener) {
    if (listener != null) {
      mCanAddCallListeners.remove(listener);
    }
  }

  public void addOrientationListener(InCallOrientationListener listener) {
    Objects.requireNonNull(listener);
    mOrientationListeners.add(listener);
  }

  public void removeOrientationListener(InCallOrientationListener listener) {
    if (listener != null) {
      mOrientationListeners.remove(listener);
    }
  }

  public void addInCallEventListener(InCallEventListener listener) {
    Objects.requireNonNull(listener);
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

  public PseudoScreenState getPseudoScreenState() {
    return mPseudoScreenState;
  }

  /** Returns true if the incall app is the foreground application. */
  public boolean isShowingInCallUi() {
    if (!isActivityStarted()) {
      return false;
    }
    if (mManageConferenceActivity != null && mManageConferenceActivity.isVisible()) {
      return true;
    }
    return mInCallActivity.isVisible();
  }

  /**
   * Returns true if the activity has been created and is running. Returns true as long as activity
   * is not destroyed or finishing. This ensures that we return true even if the activity is paused
   * (not in foreground).
   */
  public boolean isActivityStarted() {
    return (mInCallActivity != null
        && !mInCallActivity.isDestroyed()
        && !mInCallActivity.isFinishing());
  }

  /**
   * Determines if the In-Call app is currently changing configuration.
   *
   * @return {@code true} if the In-Call app is changing configuration.
   */
  public boolean isChangingConfigurations() {
    return mIsChangingConfigurations;
  }

  /**
   * Tracks whether the In-Call app is currently in the process of changing configuration (i.e.
   * screen orientation).
   */
  /*package*/
  void updateIsChangingConfigurations() {
    mIsChangingConfigurations = false;
    if (mInCallActivity != null) {
      mIsChangingConfigurations = mInCallActivity.isChangingConfigurations();
    }
    LogUtil.v(
        "InCallPresenter.updateIsChangingConfigurations",
        "updateIsChangingConfigurations = " + mIsChangingConfigurations);
  }

  /** Called when the activity goes in/out of the foreground. */
  public void onUiShowing(boolean showing) {
    // We need to update the notification bar when we leave the UI because that
    // could trigger it to show again.
    if (mStatusBarNotifier != null) {
      mStatusBarNotifier.updateNotification(mCallList);
    }

    if (mProximitySensor != null) {
      mProximitySensor.onInCallShowing(showing);
    }

    Intent broadcastIntent = Bindings.get(mContext).getUiReadyBroadcastIntent(mContext);
    if (broadcastIntent != null) {
      broadcastIntent.putExtra(EXTRA_FIRST_TIME_SHOWN, !mIsActivityPreviouslyStarted);

      if (showing) {
        LogUtil.d("InCallPresenter.onUiShowing", "Sending sticky broadcast: ", broadcastIntent);
        mContext.sendStickyBroadcast(broadcastIntent);
      } else {
        LogUtil.d("InCallPresenter.onUiShowing", "Removing sticky broadcast: ", broadcastIntent);
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

    if (mInCallActivity != null) {
      // Re-evaluate which fragment is being shown.
      mInCallActivity.onPrimaryCallStateChanged();
    }
  }

  public void refreshUi() {
    if (mInCallActivity != null) {
      // Re-evaluate which fragment is being shown.
      mInCallActivity.onPrimaryCallStateChanged();
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
    LogUtil.d("InCallPresenter.onActivityStarted", "onActivityStarted");
    notifyVideoPauseController(true);
    if (mStatusBarNotifier != null) {
      // TODO - b/36649622: Investigate this redundant call
      mStatusBarNotifier.updateNotification(mCallList);
    }
    applyScreenTimeout();
  }

  /*package*/
  void onActivityStopped() {
    LogUtil.d("InCallPresenter.onActivityStopped", "onActivityStopped");
    notifyVideoPauseController(false);
  }

  private void notifyVideoPauseController(boolean showing) {
    LogUtil.d(
        "InCallPresenter.notifyVideoPauseController",
        "mIsChangingConfigurations=" + mIsChangingConfigurations);
    if (!mIsChangingConfigurations) {
      VideoPauseController.getInstance().onUiShowing(showing);
    }
  }

  /** Brings the app into the foreground if possible. */
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
   *
   * @return true if we consumed the event.
   */
  public boolean handleCallKey() {
    LogUtil.v("InCallPresenter.handleCallKey", null);

    // The green CALL button means either "Answer", "Unhold", or
    // "Swap calls", or can be a no-op, depending on the current state
    // of the Phone.

    /** INCOMING CALL */
    final CallList calls = mCallList;
    final DialerCall incomingCall = calls.getIncomingCall();
    LogUtil.v("InCallPresenter.handleCallKey", "incomingCall: " + incomingCall);

    // (1) Attempt to answer a call
    if (incomingCall != null) {
      incomingCall.answer(VideoProfile.STATE_AUDIO_ONLY);
      return true;
    }

    /** STATE_ACTIVE CALL */
    final DialerCall activeCall = calls.getActiveCall();
    if (activeCall != null) {
      // TODO: This logic is repeated from CallButtonPresenter.java. We should
      // consolidate this logic.
      final boolean canMerge =
          activeCall.can(android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE);
      final boolean canSwap =
          activeCall.can(android.telecom.Call.Details.CAPABILITY_SWAP_CONFERENCE);

      LogUtil.v(
          "InCallPresenter.handleCallKey",
          "activeCall: " + activeCall + ", canMerge: " + canMerge + ", canSwap: " + canSwap);

      // (2) Attempt actions on conference calls
      if (canMerge) {
        TelecomAdapter.getInstance().merge(activeCall.getId());
        return true;
      } else if (canSwap) {
        TelecomAdapter.getInstance().swap(activeCall.getId());
        return true;
      }
    }

    /** BACKGROUND CALL */
    final DialerCall heldCall = calls.getBackgroundCall();
    if (heldCall != null) {
      // We have a hold call so presumeable it will always support HOLD...but
      // there is no harm in double checking.
      final boolean canHold = heldCall.can(android.telecom.Call.Details.CAPABILITY_HOLD);

      LogUtil.v("InCallPresenter.handleCallKey", "heldCall: " + heldCall + ", canHold: " + canHold);

      // (4) unhold call
      if (heldCall.getState() == DialerCall.State.ONHOLD && canHold) {
        heldCall.unhold();
        return true;
      }
    }

    // Always consume hard keys
    return true;
  }

  /**
   * A dialog could have prevented in-call screen from being previously finished. This function
   * checks to see if there should be any UI left and if not attempts to tear down the UI.
   */
  public void onDismissDialog() {
    LogUtil.i("InCallPresenter.onDismissDialog", "Dialog dismissed");
    if (mInCallState == InCallState.NO_CALLS) {
      attemptFinishActivity();
      attemptCleanup();
    }
  }

  /** Clears the previous fullscreen state. */
  public void clearFullscreen() {
    mIsFullScreen = false;
  }

  /**
   * Changes the fullscreen mode of the in-call UI.
   *
   * @param isFullScreen {@code true} if in-call should be in fullscreen mode, {@code false}
   *     otherwise.
   */
  public void setFullScreen(boolean isFullScreen) {
    setFullScreen(isFullScreen, false /* force */);
  }

  /**
   * Changes the fullscreen mode of the in-call UI.
   *
   * @param isFullScreen {@code true} if in-call should be in fullscreen mode, {@code false}
   *     otherwise.
   * @param force {@code true} if fullscreen mode should be set regardless of its current state.
   */
  public void setFullScreen(boolean isFullScreen, boolean force) {
    LogUtil.i("InCallPresenter.setFullScreen", "setFullScreen = " + isFullScreen);

    // As a safeguard, ensure we cannot enter fullscreen if the dialpad is shown.
    if (isDialpadVisible()) {
      isFullScreen = false;
      LogUtil.v(
          "InCallPresenter.setFullScreen",
          "setFullScreen overridden as dialpad is shown = " + isFullScreen);
    }

    if (mIsFullScreen == isFullScreen && !force) {
      LogUtil.v("InCallPresenter.setFullScreen", "setFullScreen ignored as already in that state.");
      return;
    }
    mIsFullScreen = isFullScreen;
    notifyFullscreenModeChange(mIsFullScreen);
  }

  /**
   * @return {@code true} if the in-call ui is currently in fullscreen mode, {@code false}
   *     otherwise.
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
   * For some disconnected causes, we show a dialog. This calls into the activity to show the dialog
   * if appropriate for the call.
   */
  private void maybeShowErrorDialogOnDisconnect(DialerCall call) {
    // For newly disconnected calls, we may want to show a dialog on specific error conditions
    if (isActivityStarted() && call.getState() == DialerCall.State.DISCONNECTED) {
      if (call.getAccountHandle() == null && !call.isConferenceCall()) {
        setDisconnectCauseForMissingAccounts(call);
      }
      mInCallActivity.maybeShowErrorDialogOnDisconnect(
          new DisconnectMessage(mInCallActivity, call));
    }
  }

  /**
   * When the state of in-call changes, this is the first method to get called. It determines if the
   * UI needs to be started or finished depending on the new state and does it.
   */
  private InCallState startOrFinishUi(InCallState newState) {
    LogUtil.d(
        "InCallPresenter.startOrFinishUi", "startOrFinishUi: " + mInCallState + " -> " + newState);

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
    // but Telecom has not yet returned with the details of the call (handle, gateway, etc.).
    // This pending outgoing state can also launch the call screen.
    //
    // This is different from the incoming call sequence because we do not need to shock the
    // user with a top-level notification.  Just show the call UI normally.
    boolean callCardFragmentVisible =
        mInCallActivity != null && mInCallActivity.getCallCardFragmentVisible();
    final boolean mainUiNotVisible = !isShowingInCallUi() || !callCardFragmentVisible;
    boolean showCallUi = InCallState.OUTGOING == newState && mainUiNotVisible;

    // Direct transition from PENDING_OUTGOING -> INCALL means that there was an error in the
    // outgoing call process, so the UI should be brought up to show an error dialog.
    showCallUi |=
        (InCallState.PENDING_OUTGOING == mInCallState
            && InCallState.INCALL == newState
            && !isShowingInCallUi());

    // Another exception - InCallActivity is in charge of disconnecting a call with no
    // valid accounts set. Bring the UI up if this is true for the current pending outgoing
    // call so that:
    // 1) The call can be disconnected correctly
    // 2) The UI comes up and correctly displays the error dialog.
    // TODO: Remove these special case conditions by making InCallPresenter a true state
    // machine. Telecom should also be the component responsible for disconnecting a call
    // with no valid accounts.
    showCallUi |=
        InCallState.PENDING_OUTGOING == newState
            && mainUiNotVisible
            && isCallWithNoValidAccounts(mCallList.getPendingOutgoingCall());

    // The only time that we have an instance of mInCallActivity and it isn't started is
    // when it is being destroyed.  In that case, lets avoid bringing up another instance of
    // the activity.  When it is finally destroyed, we double check if we should bring it back
    // up so we aren't going to lose anything by avoiding a second startup here.
    boolean activityIsFinishing = mInCallActivity != null && !isActivityStarted();
    if (activityIsFinishing) {
      LogUtil.i(
          "InCallPresenter.startOrFinishUi",
          "Undo the state change: " + newState + " -> " + mInCallState);
      return mInCallState;
    }

    // We're about the bring up the in-call UI for outgoing and incoming call. If we still have
    // dialogs up, we need to clear them out before showing in-call screen. This is necessary
    // to fix the bug that dialog will show up when data reaches limit even after makeing new
    // outgoing call after user ignore it by pressing home button.
    if ((newState == InCallState.INCOMING || newState == InCallState.PENDING_OUTGOING)
        && !showCallUi
        && isActivityStarted()) {
      mInCallActivity.dismissPendingDialogs();
    }

    if (showCallUi || showAccountPicker) {
      LogUtil.i("InCallPresenter.startOrFinishUi", "Start in call UI");
      showInCall(false /* showDialpad */, !showAccountPicker /* newOutgoingCall */);
    } else if (startIncomingCallSequence) {
      LogUtil.i("InCallPresenter.startOrFinishUi", "Start Full Screen in call UI");

      mStatusBarNotifier.updateNotification(mCallList);
    } else if (newState == InCallState.NO_CALLS) {
      // The new state is the no calls state.  Tear everything down.
      attemptFinishActivity();
      attemptCleanup();
    }

    return newState;
  }

  /**
   * Sets the DisconnectCause for a call that was disconnected because it was missing a PhoneAccount
   * or PhoneAccounts to select from.
   */
  private void setDisconnectCauseForMissingAccounts(DialerCall call) {

    Bundle extras = call.getIntentExtras();
    // Initialize the extras bundle to avoid NPE
    if (extras == null) {
      extras = new Bundle();
    }

    final List<PhoneAccountHandle> phoneAccountHandles =
        extras.getParcelableArrayList(android.telecom.Call.AVAILABLE_PHONE_ACCOUNTS);

    if (phoneAccountHandles == null || phoneAccountHandles.isEmpty()) {
      String scheme = call.getHandle().getScheme();
      final String errorMsg =
          PhoneAccount.SCHEME_TEL.equals(scheme)
              ? mContext.getString(R.string.callFailed_simError)
              : mContext.getString(R.string.incall_error_supp_service_unknown);
      DisconnectCause disconnectCause =
          new DisconnectCause(DisconnectCause.ERROR, null, errorMsg, errorMsg);
      call.setDisconnectCause(disconnectCause);
    }
  }

  /**
   * @return {@code true} if the InCallPresenter is ready to be torn down, {@code false} otherwise.
   *     Calling classes should use this as an indication whether to interact with the
   *     InCallPresenter or not.
   */
  public boolean isReadyForTearDown() {
    return mInCallActivity == null && !mServiceConnected && mInCallState == InCallState.NO_CALLS;
  }

  /**
   * Checks to see if both the UI is gone and the service is disconnected. If so, tear it all down.
   */
  private void attemptCleanup() {
    if (isReadyForTearDown()) {
      LogUtil.i("InCallPresenter.attemptCleanup", "Cleaning up");

      cleanupSurfaces();

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

      if (mStatusBarNotifier != null) {
        removeListener(mStatusBarNotifier);
        EnrichedCallComponent.get(mContext)
            .getEnrichedCallManager()
            .unregisterStateChangedListener(mStatusBarNotifier);
      }

      if (mExternalCallNotifier != null && mExternalCallList != null) {
        mExternalCallList.removeExternalCallListener(mExternalCallNotifier);
      }
      mStatusBarNotifier = null;

      if (mVibrationHandler != null) {
        removeListener(mVibrationHandler);
      }
      mVibrationHandler = null;

      if (mCallList != null) {
        mCallList.removeListener(this);
        mCallList.removeListener(mSpamCallListListener);
      }
      mCallList = null;

      mContext = null;
      mInCallActivity = null;
      mManageConferenceActivity = null;

      mListeners.clear();
      mIncomingCallListeners.clear();
      mDetailsListeners.clear();
      mCanAddCallListeners.clear();
      mOrientationListeners.clear();
      mInCallEventListeners.clear();
      mInCallUiListeners.clear();

      LogUtil.d("InCallPresenter.attemptCleanup", "finished");
    }
  }

  public void showInCall(boolean showDialpad, boolean newOutgoingCall) {
    LogUtil.i("InCallPresenter.showInCall", "Showing InCallActivity");
    mContext.startActivity(
        InCallActivity.getIntent(
            mContext, showDialpad, newOutgoingCall, false /* forFullScreen */));
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

    final Intent activityIntent =
        InCallActivity.getIntent(mContext, false, true, false /* forFullScreen */);
    activityIntent.putExtra(TouchPointManager.TOUCH_POINT, touchPoint);
    mContext.startActivity(activityIntent);
  }

  /**
   * Retrieves the current in-call camera manager instance, creating if necessary.
   *
   * @return The {@link InCallCameraManager}.
   */
  public InCallCameraManager getInCallCameraManager() {
    synchronized (this) {
      if (mInCallCameraManager == null) {
        mInCallCameraManager = new InCallCameraManager(mContext);
      }

      return mInCallCameraManager;
    }
  }

  /**
   * Notifies listeners of changes in orientation and notify calls of rotation angle change.
   *
   * @param orientation The screen orientation of the device (one of: {@link
   *     InCallOrientationEventListener#SCREEN_ORIENTATION_0}, {@link
   *     InCallOrientationEventListener#SCREEN_ORIENTATION_90}, {@link
   *     InCallOrientationEventListener#SCREEN_ORIENTATION_180}, {@link
   *     InCallOrientationEventListener#SCREEN_ORIENTATION_270}).
   */
  public void onDeviceOrientationChange(@ScreenOrientation int orientation) {
    LogUtil.d(
        "InCallPresenter.onDeviceOrientationChange",
        "onDeviceOrientationChange: orientation= " + orientation);

    if (mCallList != null) {
      mCallList.notifyCallsOfDeviceRotation(orientation);
    } else {
      LogUtil.w("InCallPresenter.onDeviceOrientationChange", "CallList is null.");
    }

    // Notify listeners of device orientation changed.
    for (InCallOrientationListener listener : mOrientationListeners) {
      listener.onDeviceOrientationChanged(orientation);
    }
  }

  /**
   * Configures the in-call UI activity so it can change orientations or not. Enables the
   * orientation event listener if allowOrientationChange is true, disables it if false.
   *
   * @param allowOrientationChange {@code true} if the in-call UI can change between portrait and
   *     landscape. {@code false} if the in-call UI should be locked in portrait.
   */
  public void setInCallAllowsOrientationChange(boolean allowOrientationChange) {
    if (mInCallActivity == null) {
      LogUtil.e(
          "InCallPresenter.setInCallAllowsOrientationChange",
          "InCallActivity is null. Can't set requested orientation.");
      return;
    }
    mInCallActivity.setAllowOrientationChange(allowOrientationChange);
  }

  public void enableScreenTimeout(boolean enable) {
    LogUtil.v("InCallPresenter.enableScreenTimeout", "enableScreenTimeout: value=" + enable);
    mScreenTimeoutEnabled = enable;
    applyScreenTimeout();
  }

  private void applyScreenTimeout() {
    if (mInCallActivity == null) {
      LogUtil.e("InCallPresenter.applyScreenTimeout", "InCallActivity is null.");
      return;
    }

    final Window window = mInCallActivity.getWindow();
    if (mScreenTimeoutEnabled) {
      window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    } else {
      window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
  }

  /**
   * Hides or shows the conference manager fragment.
   *
   * @param show {@code true} if the conference manager should be shown, {@code false} if it should
   *     be hidden.
   */
  public void showConferenceCallManager(boolean show) {
    if (mInCallActivity != null) {
      mInCallActivity.showConferenceFragment(show);
    }
    if (!show && mManageConferenceActivity != null) {
      mManageConferenceActivity.finish();
    }
  }

  /**
   * Determines if the dialpad is visible.
   *
   * @return {@code true} if the dialpad is visible, {@code false} otherwise.
   */
  public boolean isDialpadVisible() {
    if (mInCallActivity == null) {
      return false;
    }
    return mInCallActivity.isDialpadVisible();
  }

  public ThemeColorManager getThemeColorManager() {
    return mThemeColorManager;
  }

  /** Called when the foreground call changes. */
  public void onForegroundCallChanged(DialerCall newForegroundCall) {
    mThemeColorManager.onForegroundCallChanged(mContext, newForegroundCall);
    if (mInCallActivity != null) {
      mInCallActivity.onForegroundCallChanged(newForegroundCall);
    }
  }

  public InCallActivity getActivity() {
    return mInCallActivity;
  }

  /** Called when the UI begins, and starts the callstate callbacks if necessary. */
  public void setActivity(InCallActivity inCallActivity) {
    if (inCallActivity == null) {
      throw new IllegalArgumentException("registerActivity cannot be called with null");
    }
    if (mInCallActivity != null && mInCallActivity != inCallActivity) {
      LogUtil.w(
          "InCallPresenter.setActivity", "Setting a second activity before destroying the first.");
    }
    updateActivity(inCallActivity);
  }

  ExternalCallNotifier getExternalCallNotifier() {
    return mExternalCallNotifier;
  }

  VideoSurfaceTexture getLocalVideoSurfaceTexture() {
    if (mLocalVideoSurfaceTexture == null) {
      mLocalVideoSurfaceTexture = VideoSurfaceBindings.createLocalVideoSurfaceTexture();
    }
    return mLocalVideoSurfaceTexture;
  }

  VideoSurfaceTexture getRemoteVideoSurfaceTexture() {
    if (mRemoteVideoSurfaceTexture == null) {
      mRemoteVideoSurfaceTexture = VideoSurfaceBindings.createRemoteVideoSurfaceTexture();
    }
    return mRemoteVideoSurfaceTexture;
  }

  void cleanupSurfaces() {
    if (mRemoteVideoSurfaceTexture != null) {
      mRemoteVideoSurfaceTexture.setDoneWithSurface();
      mRemoteVideoSurfaceTexture = null;
    }
    if (mLocalVideoSurfaceTexture != null) {
      mLocalVideoSurfaceTexture.setDoneWithSurface();
      mLocalVideoSurfaceTexture = null;
    }
  }

  /** All the main states of InCallActivity. */
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
    // The UI is waiting for Telecom to respond.
    PENDING_OUTGOING,

    // User is dialing out
    OUTGOING;

    public boolean isIncoming() {
      return (this == INCOMING);
    }

    public boolean isConnectingOrConnected() {
      return (this == INCOMING || this == OUTGOING || this == INCALL);
    }
  }

  /** Interface implemented by classes that need to know about the InCall State. */
  public interface InCallStateListener {

    // TODO: Enhance state to contain the call objects instead of passing CallList
    void onStateChange(InCallState oldState, InCallState newState, CallList callList);
  }

  public interface IncomingCallListener {

    void onIncomingCall(InCallState oldState, InCallState newState, DialerCall call);
  }

  public interface CanAddCallListener {

    void onCanAddCallChanged(boolean canAddCall);
  }

  public interface InCallDetailsListener {

    void onDetailsChanged(DialerCall call, android.telecom.Call.Details details);
  }

  public interface InCallOrientationListener {

    void onDeviceOrientationChanged(@ScreenOrientation int orientation);
  }

  /**
   * Interface implemented by classes that need to know about events which occur within the In-Call
   * UI. Used as a means of communicating between fragments that make up the UI.
   */
  public interface InCallEventListener {

    void onFullscreenModeChanged(boolean isFullscreenMode);
  }

  public interface InCallUiListener {

    void onUiShowing(boolean showing);
  }
}
