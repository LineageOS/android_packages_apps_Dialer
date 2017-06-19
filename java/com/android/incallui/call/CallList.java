/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.incallui.call;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.Trace;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.os.BuildCompat;
import android.telecom.Call;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.util.ArrayMap;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.android.dialer.blocking.FilteredNumbersUtil;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.enrichedcall.EnrichedCallComponent;
import com.android.dialer.enrichedcall.EnrichedCallManager;
import com.android.dialer.location.GeoUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.shortcuts.ShortcutUsageReporter;
import com.android.dialer.spam.Spam;
import com.android.dialer.spam.SpamBindings;
import com.android.incallui.call.DialerCall.State;
import com.android.incallui.latencyreport.LatencyReport;
import com.android.incallui.util.TelecomCallUtil;
import com.android.incallui.videotech.utils.SessionModificationState;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains the list of active calls and notifies interested classes of changes to the call list as
 * they are received from the telephony stack. Primary listener of changes to this class is
 * InCallPresenter.
 */
public class CallList implements DialerCallDelegate {

  private static final int DISCONNECTED_CALL_SHORT_TIMEOUT_MS = 200;
  private static final int DISCONNECTED_CALL_MEDIUM_TIMEOUT_MS = 2000;
  private static final int DISCONNECTED_CALL_LONG_TIMEOUT_MS = 5000;

  private static final int EVENT_DISCONNECTED_TIMEOUT = 1;

  private static CallList sInstance = new CallList();

  private final Map<String, DialerCall> mCallById = new ArrayMap<>();
  private final Map<android.telecom.Call, DialerCall> mCallByTelecomCall = new ArrayMap<>();

  /**
   * ConcurrentHashMap constructor params: 8 is initial table size, 0.9f is load factor before
   * resizing, 1 means we only expect a single thread to access the map so make only a single shard
   */
  private final Set<Listener> mListeners =
      Collections.newSetFromMap(new ConcurrentHashMap<Listener, Boolean>(8, 0.9f, 1));

  private final Set<DialerCall> mPendingDisconnectCalls =
      Collections.newSetFromMap(new ConcurrentHashMap<DialerCall, Boolean>(8, 0.9f, 1));
  /** Handles the timeout for destroying disconnected calls. */
  private final Handler mHandler =
      new Handler() {
        @Override
        public void handleMessage(Message msg) {
          switch (msg.what) {
            case EVENT_DISCONNECTED_TIMEOUT:
              LogUtil.d("CallList.handleMessage", "EVENT_DISCONNECTED_TIMEOUT ", msg.obj);
              finishDisconnectedCall((DialerCall) msg.obj);
              break;
            default:
              LogUtil.e("CallList.handleMessage", "Message not expected: " + msg.what);
              break;
          }
        }
      };

  /**
   * USED ONLY FOR TESTING Testing-only constructor. Instance should only be acquired through
   * getRunningInstance().
   */
  @VisibleForTesting
  public CallList() {}

  @VisibleForTesting
  public static void setCallListInstance(CallList callList) {
    sInstance = callList;
  }

  /** Static singleton accessor method. */
  public static CallList getInstance() {
    return sInstance;
  }

  public void onCallAdded(
      final Context context, final android.telecom.Call telecomCall, LatencyReport latencyReport) {
    Trace.beginSection("onCallAdded");
    final DialerCall call =
        new DialerCall(context, this, telecomCall, latencyReport, true /* registerCallback */);
    logSecondIncomingCall(context, call);

    EnrichedCallManager manager = EnrichedCallComponent.get(context).getEnrichedCallManager();
    manager.registerCapabilitiesListener(call);
    manager.registerStateChangedListener(call);

    final DialerCallListenerImpl dialerCallListener = new DialerCallListenerImpl(call);
    call.addListener(dialerCallListener);
    LogUtil.d("CallList.onCallAdded", "callState=" + call.getState());
    if (Spam.get(context).isSpamEnabled()) {
      String number = TelecomCallUtil.getNumber(telecomCall);
      Spam.get(context)
          .checkSpamStatus(
              number,
              null,
              new SpamBindings.Listener() {
                @Override
                public void onComplete(boolean isSpam) {
                  boolean isIncomingCall =
                      call.getState() == DialerCall.State.INCOMING
                          || call.getState() == DialerCall.State.CALL_WAITING;
                  if (isSpam) {
                    if (!isIncomingCall) {
                      LogUtil.i(
                          "CallList.onCallAdded",
                          "marking spam call as not spam because it's not an incoming call");
                      isSpam = false;
                    } else if (isPotentialEmergencyCallback(context, call)) {
                      LogUtil.i(
                          "CallList.onCallAdded",
                          "marking spam call as not spam because an emergency call was made on this"
                              + " device recently");
                      isSpam = false;
                    }
                  }

                  if (isIncomingCall) {
                    Logger.get(context)
                        .logCallImpression(
                            isSpam
                                ? DialerImpression.Type.INCOMING_SPAM_CALL
                                : DialerImpression.Type.INCOMING_NON_SPAM_CALL,
                            call.getUniqueCallId(),
                            call.getTimeAddedMs());
                  }
                  call.setSpam(isSpam);
                  dialerCallListener.onDialerCallUpdate();
                }
              });

      updateUserMarkedSpamStatus(call, context, number, dialerCallListener);
    }

    FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler =
        new FilteredNumberAsyncQueryHandler(context);

    filteredNumberAsyncQueryHandler.isBlockedNumber(
        new FilteredNumberAsyncQueryHandler.OnCheckBlockedListener() {
          @Override
          public void onCheckComplete(Integer id) {
            if (id != null && id != FilteredNumberAsyncQueryHandler.INVALID_ID) {
              call.setBlockedStatus(true);
              dialerCallListener.onDialerCallUpdate();
            }
          }
        },
        call.getNumber(),
        GeoUtil.getCurrentCountryIso(context));

    if (call.getState() == DialerCall.State.INCOMING
        || call.getState() == DialerCall.State.CALL_WAITING) {
      onIncoming(call);
    } else {
      dialerCallListener.onDialerCallUpdate();
    }

    if (call.getState() != State.INCOMING) {
      // Only report outgoing calls
      ShortcutUsageReporter.onOutgoingCallAdded(context, call.getNumber());
    }

    Trace.endSection();
  }

  private void logSecondIncomingCall(@NonNull Context context, @NonNull DialerCall incomingCall) {
    DialerCall firstCall = getFirstCall();
    if (firstCall != null) {
      DialerImpression.Type impression;
      if (firstCall.isVideoCall()) {
        if (incomingCall.isVideoCall()) {
          impression = DialerImpression.Type.VIDEO_CALL_WITH_INCOMING_VIDEO_CALL;
        } else {
          impression = DialerImpression.Type.VIDEO_CALL_WITH_INCOMING_VOICE_CALL;
        }
      } else {
        if (incomingCall.isVideoCall()) {
          impression = DialerImpression.Type.VOICE_CALL_WITH_INCOMING_VIDEO_CALL;
        } else {
          impression = DialerImpression.Type.VOICE_CALL_WITH_INCOMING_VOICE_CALL;
        }
      }
      Assert.checkArgument(impression != null);
      Logger.get(context)
          .logCallImpression(
              impression, incomingCall.getUniqueCallId(), incomingCall.getTimeAddedMs());
    }
  }

  private static boolean isPotentialEmergencyCallback(Context context, DialerCall call) {
    if (BuildCompat.isAtLeastO()) {
      return call.isPotentialEmergencyCallback();
    } else {
      long timestampMillis = FilteredNumbersUtil.getLastEmergencyCallTimeMillis(context);
      return call.isInEmergencyCallbackWindow(timestampMillis);
    }
  }

  @Override
  public DialerCall getDialerCallFromTelecomCall(Call telecomCall) {
    return mCallByTelecomCall.get(telecomCall);
  }

  public void updateUserMarkedSpamStatus(
      final DialerCall call,
      final Context context,
      String number,
      final DialerCallListenerImpl dialerCallListener) {

    Spam.get(context)
        .checkUserMarkedNonSpamStatus(
            number,
            null,
            new SpamBindings.Listener() {
              @Override
              public void onComplete(boolean isInUserWhiteList) {
                call.setIsInUserWhiteList(isInUserWhiteList);
              }
            });

    Spam.get(context)
        .checkGlobalSpamListStatus(
            number,
            null,
            new SpamBindings.Listener() {
              @Override
              public void onComplete(boolean isInGlobalSpamList) {
                call.setIsInGlobalSpamList(isInGlobalSpamList);
              }
            });

    Spam.get(context)
        .checkUserMarkedSpamStatus(
            number,
            null,
            new SpamBindings.Listener() {
              @Override
              public void onComplete(boolean isInUserSpamList) {
                call.setIsInUserSpamList(isInUserSpamList);
              }
            });
  }

  public void onCallRemoved(Context context, android.telecom.Call telecomCall) {
    if (mCallByTelecomCall.containsKey(telecomCall)) {
      DialerCall call = mCallByTelecomCall.get(telecomCall);
      Assert.checkArgument(!call.isExternalCall());

      EnrichedCallManager manager = EnrichedCallComponent.get(context).getEnrichedCallManager();
      manager.unregisterCapabilitiesListener(call);
      manager.unregisterStateChangedListener(call);

      // Don't log an already logged call. logCall() might be called multiple times
      // for the same call due to b/24109437.
      if (call.getLogState() != null && !call.getLogState().isLogged) {
        getLegacyBindings(context).logCall(call);
        call.getLogState().isLogged = true;
      }

      if (updateCallInMap(call)) {
        LogUtil.w(
            "CallList.onCallRemoved", "Removing call not previously disconnected " + call.getId());
      }

      call.onRemovedFromCallList();
    }

    if (!hasLiveCall()) {
      DialerCall.clearRestrictedCount();
    }
  }

  InCallUiLegacyBindings getLegacyBindings(Context context) {
    Objects.requireNonNull(context);

    Context application = context.getApplicationContext();
    InCallUiLegacyBindings legacyInstance = null;
    if (application instanceof InCallUiLegacyBindingsFactory) {
      legacyInstance = ((InCallUiLegacyBindingsFactory) application).newInCallUiLegacyBindings();
    }

    if (legacyInstance == null) {
      legacyInstance = new InCallUiLegacyBindingsStub();
    }
    return legacyInstance;
  }

  /**
   * Handles the case where an internal call has become an exteral call. We need to
   *
   * @param context
   * @param telecomCall
   */
  public void onInternalCallMadeExternal(Context context, android.telecom.Call telecomCall) {

    if (mCallByTelecomCall.containsKey(telecomCall)) {
      DialerCall call = mCallByTelecomCall.get(telecomCall);

      // Don't log an already logged call. logCall() might be called multiple times
      // for the same call due to b/24109437.
      if (call.getLogState() != null && !call.getLogState().isLogged) {
        getLegacyBindings(context).logCall(call);
        call.getLogState().isLogged = true;
      }

      // When removing a call from the call list because it became an external call, we need to
      // ensure the callback is unregistered -- this is normally only done when calls disconnect.
      // However, the call won't be disconnected in this case.  Also, logic in updateCallInMap
      // would just re-add the call anyways.
      call.unregisterCallback();
      mCallById.remove(call.getId());
      mCallByTelecomCall.remove(telecomCall);
    }
  }

  /** Called when a single call has changed. */
  private void onIncoming(DialerCall call) {
    if (updateCallInMap(call)) {
      LogUtil.i("CallList.onIncoming", String.valueOf(call));
    }

    for (Listener listener : mListeners) {
      listener.onIncomingCall(call);
    }
  }

  public void addListener(@NonNull Listener listener) {
    Objects.requireNonNull(listener);

    mListeners.add(listener);

    // Let the listener know about the active calls immediately.
    listener.onCallListChange(this);
  }

  public void removeListener(@Nullable Listener listener) {
    if (listener != null) {
      mListeners.remove(listener);
    }
  }

  /**
   * TODO: Change so that this function is not needed. Instead of assuming there is an active call,
   * the code should rely on the status of a specific DialerCall and allow the presenters to update
   * the DialerCall object when the active call changes.
   */
  public DialerCall getIncomingOrActive() {
    DialerCall retval = getIncomingCall();
    if (retval == null) {
      retval = getActiveCall();
    }
    return retval;
  }

  public DialerCall getOutgoingOrActive() {
    DialerCall retval = getOutgoingCall();
    if (retval == null) {
      retval = getActiveCall();
    }
    return retval;
  }

  /** A call that is waiting for {@link PhoneAccount} selection */
  public DialerCall getWaitingForAccountCall() {
    return getFirstCallWithState(DialerCall.State.SELECT_PHONE_ACCOUNT);
  }

  public DialerCall getPendingOutgoingCall() {
    return getFirstCallWithState(DialerCall.State.CONNECTING);
  }

  public DialerCall getOutgoingCall() {
    DialerCall call = getFirstCallWithState(DialerCall.State.DIALING);
    if (call == null) {
      call = getFirstCallWithState(DialerCall.State.REDIALING);
    }
    if (call == null) {
      call = getFirstCallWithState(DialerCall.State.PULLING);
    }
    return call;
  }

  public DialerCall getActiveCall() {
    return getFirstCallWithState(DialerCall.State.ACTIVE);
  }

  public DialerCall getSecondActiveCall() {
    return getCallWithState(DialerCall.State.ACTIVE, 1);
  }

  public DialerCall getBackgroundCall() {
    return getFirstCallWithState(DialerCall.State.ONHOLD);
  }

  public DialerCall getDisconnectedCall() {
    return getFirstCallWithState(DialerCall.State.DISCONNECTED);
  }

  public DialerCall getDisconnectingCall() {
    return getFirstCallWithState(DialerCall.State.DISCONNECTING);
  }

  public DialerCall getSecondBackgroundCall() {
    return getCallWithState(DialerCall.State.ONHOLD, 1);
  }

  public DialerCall getActiveOrBackgroundCall() {
    DialerCall call = getActiveCall();
    if (call == null) {
      call = getBackgroundCall();
    }
    return call;
  }

  public DialerCall getIncomingCall() {
    DialerCall call = getFirstCallWithState(DialerCall.State.INCOMING);
    if (call == null) {
      call = getFirstCallWithState(DialerCall.State.CALL_WAITING);
    }

    return call;
  }

  public DialerCall getFirstCall() {
    DialerCall result = getIncomingCall();
    if (result == null) {
      result = getPendingOutgoingCall();
    }
    if (result == null) {
      result = getOutgoingCall();
    }
    if (result == null) {
      result = getFirstCallWithState(DialerCall.State.ACTIVE);
    }
    if (result == null) {
      result = getDisconnectingCall();
    }
    if (result == null) {
      result = getDisconnectedCall();
    }
    return result;
  }

  public boolean hasLiveCall() {
    DialerCall call = getFirstCall();
    return call != null && call != getDisconnectingCall() && call != getDisconnectedCall();
  }

  /**
   * Returns the first call found in the call map with the upgrade to video modification state.
   *
   * @return The first call with the upgrade to video state.
   */
  public DialerCall getVideoUpgradeRequestCall() {
    for (DialerCall call : mCallById.values()) {
      if (call.getVideoTech().getSessionModificationState()
          == SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
        return call;
      }
    }
    return null;
  }

  public DialerCall getCallById(String callId) {
    return mCallById.get(callId);
  }

  /** Returns first call found in the call map with the specified state. */
  public DialerCall getFirstCallWithState(int state) {
    return getCallWithState(state, 0);
  }

  /**
   * Returns the [position]th call found in the call map with the specified state. TODO: Improve
   * this logic to sort by call time.
   */
  public DialerCall getCallWithState(int state, int positionToFind) {
    DialerCall retval = null;
    int position = 0;
    for (DialerCall call : mCallById.values()) {
      if (call.getState() == state) {
        if (position >= positionToFind) {
          retval = call;
          break;
        } else {
          position++;
        }
      }
    }

    return retval;
  }

  /**
   * This is called when the service disconnects, either expectedly or unexpectedly. For the
   * expected case, it's because we have no calls left. For the unexpected case, it is likely a
   * crash of phone and we need to clean up our calls manually. Without phone, there can be no
   * active calls, so this is relatively safe thing to do.
   */
  public void clearOnDisconnect() {
    for (DialerCall call : mCallById.values()) {
      final int state = call.getState();
      if (state != DialerCall.State.IDLE
          && state != DialerCall.State.INVALID
          && state != DialerCall.State.DISCONNECTED) {

        call.setState(DialerCall.State.DISCONNECTED);
        call.setDisconnectCause(new DisconnectCause(DisconnectCause.UNKNOWN));
        updateCallInMap(call);
      }
    }
    notifyGenericListeners();
  }

  /**
   * Called when the user has dismissed an error dialog. This indicates acknowledgement of the
   * disconnect cause, and that any pending disconnects should immediately occur.
   */
  public void onErrorDialogDismissed() {
    final Iterator<DialerCall> iterator = mPendingDisconnectCalls.iterator();
    while (iterator.hasNext()) {
      DialerCall call = iterator.next();
      iterator.remove();
      finishDisconnectedCall(call);
    }
  }

  /**
   * Processes an update for a single call.
   *
   * @param call The call to update.
   */
  @VisibleForTesting
  void onUpdateCall(DialerCall call) {
    LogUtil.d("CallList.onUpdateCall", String.valueOf(call));
    if (!mCallById.containsKey(call.getId()) && call.isExternalCall()) {
      // When a regular call becomes external, it is removed from the call list, and there may be
      // pending updates to Telecom which are queued up on the Telecom call's handler which we no
      // longer wish to cause updates to the call in the CallList.  Bail here if the list of tracked
      // calls doesn't contain the call which received the update.
      return;
    }

    if (updateCallInMap(call)) {
      LogUtil.i("CallList.onUpdateCall", String.valueOf(call));
    }
  }

  /**
   * Sends a generic notification to all listeners that something has changed. It is up to the
   * listeners to call back to determine what changed.
   */
  private void notifyGenericListeners() {
    for (Listener listener : mListeners) {
      listener.onCallListChange(this);
    }
  }

  private void notifyListenersOfDisconnect(DialerCall call) {
    for (Listener listener : mListeners) {
      listener.onDisconnect(call);
    }
  }

  /**
   * Updates the call entry in the local map.
   *
   * @return false if no call previously existed and no call was added, otherwise true.
   */
  private boolean updateCallInMap(DialerCall call) {
    Objects.requireNonNull(call);

    boolean updated = false;

    if (call.getState() == DialerCall.State.DISCONNECTED) {
      // update existing (but do not add!!) disconnected calls
      if (mCallById.containsKey(call.getId())) {
        // For disconnected calls, we want to keep them alive for a few seconds so that the
        // UI has a chance to display anything it needs when a call is disconnected.

        // Set up a timer to destroy the call after X seconds.
        final Message msg = mHandler.obtainMessage(EVENT_DISCONNECTED_TIMEOUT, call);
        mHandler.sendMessageDelayed(msg, getDelayForDisconnect(call));
        mPendingDisconnectCalls.add(call);

        mCallById.put(call.getId(), call);
        mCallByTelecomCall.put(call.getTelecomCall(), call);
        updated = true;
      }
    } else if (!isCallDead(call)) {
      mCallById.put(call.getId(), call);
      mCallByTelecomCall.put(call.getTelecomCall(), call);
      updated = true;
    } else if (mCallById.containsKey(call.getId())) {
      mCallById.remove(call.getId());
      mCallByTelecomCall.remove(call.getTelecomCall());
      updated = true;
    }

    return updated;
  }

  private int getDelayForDisconnect(DialerCall call) {
    if (call.getState() != DialerCall.State.DISCONNECTED) {
      throw new IllegalStateException();
    }

    final int cause = call.getDisconnectCause().getCode();
    final int delay;
    switch (cause) {
      case DisconnectCause.LOCAL:
        delay = DISCONNECTED_CALL_SHORT_TIMEOUT_MS;
        break;
      case DisconnectCause.REMOTE:
      case DisconnectCause.ERROR:
        delay = DISCONNECTED_CALL_MEDIUM_TIMEOUT_MS;
        break;
      case DisconnectCause.REJECTED:
      case DisconnectCause.MISSED:
      case DisconnectCause.CANCELED:
        // no delay for missed/rejected incoming calls and canceled outgoing calls.
        delay = 0;
        break;
      default:
        delay = DISCONNECTED_CALL_LONG_TIMEOUT_MS;
        break;
    }

    return delay;
  }

  private boolean isCallDead(DialerCall call) {
    final int state = call.getState();
    return DialerCall.State.IDLE == state || DialerCall.State.INVALID == state;
  }

  /** Sets up a call for deletion and notifies listeners of change. */
  private void finishDisconnectedCall(DialerCall call) {
    if (mPendingDisconnectCalls.contains(call)) {
      mPendingDisconnectCalls.remove(call);
    }
    call.setState(DialerCall.State.IDLE);
    updateCallInMap(call);
    notifyGenericListeners();
  }

  /**
   * Notifies all video calls of a change in device orientation.
   *
   * @param rotation The new rotation angle (in degrees).
   */
  public void notifyCallsOfDeviceRotation(int rotation) {
    for (DialerCall call : mCallById.values()) {
      call.getVideoTech().setDeviceOrientation(rotation);
    }
  }

  public void onInCallUiShown(boolean forFullScreenIntent) {
    for (DialerCall call : mCallById.values()) {
      call.getLatencyReport().onInCallUiShown(forFullScreenIntent);
    }
  }

  /** Listener interface for any class that wants to be notified of changes to the call list. */
  public interface Listener {

    /**
     * Called when a new incoming call comes in. This is the only method that gets called for
     * incoming calls. Listeners that want to perform an action on incoming call should respond in
     * this method because {@link #onCallListChange} does not automatically get called for incoming
     * calls.
     */
    void onIncomingCall(DialerCall call);

    /**
     * Called when a new modify call request comes in This is the only method that gets called for
     * modify requests.
     */
    void onUpgradeToVideo(DialerCall call);

    /** Called when the session modification state of a call changes. */
    void onSessionModificationStateChange(DialerCall call);

    /**
     * Called anytime there are changes to the call list. The change can be switching call states,
     * updating information, etc. This method will NOT be called for new incoming calls and for
     * calls that switch to disconnected state. Listeners must add actions to those method
     * implementations if they want to deal with those actions.
     */
    void onCallListChange(CallList callList);

    /**
     * Called when a call switches to the disconnected state. This is the only method that will get
     * called upon disconnection.
     */
    void onDisconnect(DialerCall call);

    void onWiFiToLteHandover(DialerCall call);

    /**
     * Called when a user is in a video call and the call is unable to be handed off successfully to
     * WiFi
     */
    void onHandoverToWifiFailed(DialerCall call);

    /** Called when the user initiates a call to an international number while on WiFi. */
    void onInternationalCallOnWifi(@NonNull DialerCall call);
  }

  private class DialerCallListenerImpl implements DialerCallListener {

    @NonNull private final DialerCall mCall;

    DialerCallListenerImpl(@NonNull DialerCall call) {
      mCall = Assert.isNotNull(call);
    }

    @Override
    public void onDialerCallDisconnect() {
      if (updateCallInMap(mCall)) {
        LogUtil.i("DialerCallListenerImpl.onDialerCallDisconnect", String.valueOf(mCall));
        // notify those listening for all disconnects
        notifyListenersOfDisconnect(mCall);
      }
    }

    @Override
    public void onDialerCallUpdate() {
      Trace.beginSection("onUpdate");
      onUpdateCall(mCall);
      notifyGenericListeners();
      Trace.endSection();
    }

    @Override
    public void onDialerCallChildNumberChange() {}

    @Override
    public void onDialerCallLastForwardedNumberChange() {}

    @Override
    public void onDialerCallUpgradeToVideo() {
      for (Listener listener : mListeners) {
        listener.onUpgradeToVideo(mCall);
      }
    }

    @Override
    public void onWiFiToLteHandover() {
      for (Listener listener : mListeners) {
        listener.onWiFiToLteHandover(mCall);
      }
    }

    @Override
    public void onHandoverToWifiFailure() {
      for (Listener listener : mListeners) {
        listener.onHandoverToWifiFailed(mCall);
      }
    }

    @Override
    public void onInternationalCallOnWifi() {
      LogUtil.enterBlock("DialerCallListenerImpl.onInternationalCallOnWifi");
      for (Listener listener : mListeners) {
        listener.onInternationalCallOnWifi(mCall);
      }
    }

    @Override
    public void onEnrichedCallSessionUpdate() {}

    @Override
    public void onDialerCallSessionModificationStateChange() {
      for (Listener listener : mListeners) {
        listener.onSessionModificationStateChange(mCall);
      }
    }
  }
}
