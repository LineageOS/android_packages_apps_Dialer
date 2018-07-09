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
import android.text.TextUtils;
import android.util.ArrayMap;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.android.dialer.blocking.FilteredNumbersUtil;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.enrichedcall.EnrichedCallComponent;
import com.android.dialer.enrichedcall.EnrichedCallManager;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.metrics.Metrics;
import com.android.dialer.metrics.MetricsComponent;
import com.android.dialer.shortcuts.ShortcutUsageReporter;
import com.android.dialer.spam.Spam;
import com.android.dialer.spam.SpamComponent;
import com.android.dialer.telecom.TelecomCallUtil;
import com.android.incallui.call.DialerCall.State;
import com.android.incallui.latencyreport.LatencyReport;
import com.android.incallui.videotech.utils.SessionModificationState;
import java.util.Collection;
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

  private static CallList instance = new CallList();

  private final Map<String, DialerCall> callById = new ArrayMap<>();
  private final Map<android.telecom.Call, DialerCall> callByTelecomCall = new ArrayMap<>();

  /**
   * ConcurrentHashMap constructor params: 8 is initial table size, 0.9f is load factor before
   * resizing, 1 means we only expect a single thread to access the map so make only a single shard
   */
  private final Set<Listener> listeners =
      Collections.newSetFromMap(new ConcurrentHashMap<Listener, Boolean>(8, 0.9f, 1));

  private final Set<DialerCall> pendingDisconnectCalls =
      Collections.newSetFromMap(new ConcurrentHashMap<DialerCall, Boolean>(8, 0.9f, 1));

  private UiListener uiListeners;
  /** Handles the timeout for destroying disconnected calls. */
  private final Handler handler =
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
    instance = callList;
  }

  /** Static singleton accessor method. */
  public static CallList getInstance() {
    return instance;
  }

  public void onCallAdded(
      final Context context, final android.telecom.Call telecomCall, LatencyReport latencyReport) {
    Trace.beginSection("CallList.onCallAdded");
    if (telecomCall.getState() == Call.STATE_CONNECTING) {
      MetricsComponent.get(context)
          .metrics()
          .startTimer(Metrics.ON_CALL_ADDED_TO_ON_INCALL_UI_SHOWN_OUTGOING);
    } else if (telecomCall.getState() == Call.STATE_RINGING) {
      MetricsComponent.get(context)
          .metrics()
          .startTimer(Metrics.ON_CALL_ADDED_TO_ON_INCALL_UI_SHOWN_INCOMING);
    }
    if (uiListeners != null) {
      uiListeners.onCallAdded();
    }
    final DialerCall call =
        new DialerCall(context, this, telecomCall, latencyReport, true /* registerCallback */);
    if (getFirstCall() != null) {
      logSecondIncomingCall(context, getFirstCall(), call);
    }

    EnrichedCallManager manager = EnrichedCallComponent.get(context).getEnrichedCallManager();
    manager.registerCapabilitiesListener(call);
    manager.registerStateChangedListener(call);

    Trace.beginSection("checkSpam");
    call.addListener(new DialerCallListenerImpl(call));
    LogUtil.d("CallList.onCallAdded", "callState=" + call.getState());
    if (SpamComponent.get(context).spam().isSpamEnabled()) {
      String number = TelecomCallUtil.getNumber(telecomCall);
      SpamComponent.get(context)
          .spam()
          .checkSpamStatus(
              number,
              call.getCountryIso(),
              new Spam.Listener() {
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
                  onUpdateCall(call);
                  notifyGenericListeners();
                }
              });

      Trace.beginSection("updateUserMarkedSpamStatus");
      updateUserMarkedSpamStatus(call, context, number);
      Trace.endSection();
    }
    Trace.endSection();

    Trace.beginSection("checkBlock");
    FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler =
        new FilteredNumberAsyncQueryHandler(context);

    filteredNumberAsyncQueryHandler.isBlockedNumber(
        new FilteredNumberAsyncQueryHandler.OnCheckBlockedListener() {
          @Override
          public void onCheckComplete(Integer id) {
            if (id != null && id != FilteredNumberAsyncQueryHandler.INVALID_ID) {
              call.setBlockedStatus(true);
              // No need to update UI since it's only used for logging.
            }
          }
        },
        call.getNumber(),
        call.getCountryIso());
    Trace.endSection();

    if (call.getState() == DialerCall.State.INCOMING
        || call.getState() == DialerCall.State.CALL_WAITING) {
      onIncoming(call);
    } else {
      onUpdateCall(call);
      notifyGenericListeners();
    }

    if (call.getState() != State.INCOMING) {
      // Only report outgoing calls
      ShortcutUsageReporter.onOutgoingCallAdded(context, call.getNumber());
    }

    Trace.endSection();
  }

  private void logSecondIncomingCall(
      @NonNull Context context, @NonNull DialerCall firstCall, @NonNull DialerCall incomingCall) {
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
    return callByTelecomCall.get(telecomCall);
  }

  private void updateUserMarkedSpamStatus(
      final DialerCall call, final Context context, String number) {

    SpamComponent.get(context)
        .spam()
        .checkUserMarkedNonSpamStatus(
            number,
            call.getCountryIso(),
            new Spam.Listener() {
              @Override
              public void onComplete(boolean isInUserWhiteList) {
                call.setIsInUserWhiteList(isInUserWhiteList);
              }
            });

    SpamComponent.get(context)
        .spam()
        .checkGlobalSpamListStatus(
            number,
            call.getCountryIso(),
            new Spam.Listener() {
              @Override
              public void onComplete(boolean isInGlobalSpamList) {
                call.setIsInGlobalSpamList(isInGlobalSpamList);
              }
            });

    SpamComponent.get(context)
        .spam()
        .checkUserMarkedSpamStatus(
            number,
            call.getCountryIso(),
            new Spam.Listener() {
              @Override
              public void onComplete(boolean isInUserSpamList) {
                call.setIsInUserSpamList(isInUserSpamList);
              }
            });
  }

  public void onCallRemoved(Context context, android.telecom.Call telecomCall) {
    if (callByTelecomCall.containsKey(telecomCall)) {
      DialerCall call = callByTelecomCall.get(telecomCall);
      Assert.checkArgument(!call.isExternalCall());

      EnrichedCallManager manager = EnrichedCallComponent.get(context).getEnrichedCallManager();
      manager.unregisterCapabilitiesListener(call);
      manager.unregisterStateChangedListener(call);

      // Don't log an already logged call. logCall() might be called multiple times
      // for the same call due to a bug.
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

    if (callByTelecomCall.containsKey(telecomCall)) {
      DialerCall call = callByTelecomCall.get(telecomCall);

      // Don't log an already logged call. logCall() might be called multiple times
      // for the same call due to a bug.
      if (call.getLogState() != null && !call.getLogState().isLogged) {
        getLegacyBindings(context).logCall(call);
        call.getLogState().isLogged = true;
      }

      // When removing a call from the call list because it became an external call, we need to
      // ensure the callback is unregistered -- this is normally only done when calls disconnect.
      // However, the call won't be disconnected in this case.  Also, logic in updateCallInMap
      // would just re-add the call anyways.
      call.unregisterCallback();
      callById.remove(call.getId());
      callByTelecomCall.remove(telecomCall);
    }
  }

  /** Called when a single call has changed. */
  private void onIncoming(DialerCall call) {
    Trace.beginSection("CallList.onIncoming");
    if (updateCallInMap(call)) {
      LogUtil.i("CallList.onIncoming", String.valueOf(call));
    }

    for (Listener listener : listeners) {
      listener.onIncomingCall(call);
    }
    Trace.endSection();
  }

  public void addListener(@NonNull Listener listener) {
    Objects.requireNonNull(listener);

    listeners.add(listener);

    // Let the listener know about the active calls immediately.
    listener.onCallListChange(this);
  }

  public void setUiListener(UiListener uiListener) {
    uiListeners = uiListener;
  }

  public void removeListener(@Nullable Listener listener) {
    if (listener != null) {
      listeners.remove(listener);
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
    for (DialerCall call : callById.values()) {
      if (call.getVideoTech().getSessionModificationState()
          == SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
        return call;
      }
    }
    return null;
  }

  public DialerCall getCallById(String callId) {
    return callById.get(callId);
  }

  public Collection<DialerCall> getAllCalls() {
    return callById.values();
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
    for (DialerCall call : callById.values()) {
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

  public DialerCall getCallWithStateAndNumber(int state, String number) {
    for (DialerCall call : callById.values()) {
      if (TextUtils.equals(call.getNumber(), number) && call.getState() == state) {
        return call;
      }
    }
    return null;
  }

  /**
   * Return if there is any active or background call which was not a parent call (never had a child
   * call)
   */
  public boolean hasNonParentActiveOrBackgroundCall() {
    for (DialerCall call : callById.values()) {
      if ((call.getState() == State.ACTIVE
              || call.getState() == State.ONHOLD
              || call.getState() == State.CONFERENCED)
          && !call.wasParentCall()) {
        return true;
      }
    }
    return false;
  }

  /**
   * This is called when the service disconnects, either expectedly or unexpectedly. For the
   * expected case, it's because we have no calls left. For the unexpected case, it is likely a
   * crash of phone and we need to clean up our calls manually. Without phone, there can be no
   * active calls, so this is relatively safe thing to do.
   */
  public void clearOnDisconnect() {
    for (DialerCall call : callById.values()) {
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
    final Iterator<DialerCall> iterator = pendingDisconnectCalls.iterator();
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
    Trace.beginSection("CallList.onUpdateCall");
    LogUtil.d("CallList.onUpdateCall", String.valueOf(call));
    if (!callById.containsKey(call.getId()) && call.isExternalCall()) {
      // When a regular call becomes external, it is removed from the call list, and there may be
      // pending updates to Telecom which are queued up on the Telecom call's handler which we no
      // longer wish to cause updates to the call in the CallList.  Bail here if the list of tracked
      // calls doesn't contain the call which received the update.
      return;
    }

    if (updateCallInMap(call)) {
      LogUtil.i("CallList.onUpdateCall", String.valueOf(call));
    }
    Trace.endSection();
  }

  /**
   * Sends a generic notification to all listeners that something has changed. It is up to the
   * listeners to call back to determine what changed.
   */
  private void notifyGenericListeners() {
    Trace.beginSection("CallList.notifyGenericListeners");
    for (Listener listener : listeners) {
      listener.onCallListChange(this);
    }
    Trace.endSection();
  }

  private void notifyListenersOfDisconnect(DialerCall call) {
    for (Listener listener : listeners) {
      listener.onDisconnect(call);
    }
  }

  /**
   * Updates the call entry in the local map.
   *
   * @return false if no call previously existed and no call was added, otherwise true.
   */
  private boolean updateCallInMap(DialerCall call) {
    Trace.beginSection("CallList.updateCallInMap");
    Objects.requireNonNull(call);

    boolean updated = false;

    if (call.getState() == DialerCall.State.DISCONNECTED) {
      // update existing (but do not add!!) disconnected calls
      if (callById.containsKey(call.getId())) {
        // For disconnected calls, we want to keep them alive for a few seconds so that the
        // UI has a chance to display anything it needs when a call is disconnected.

        // Set up a timer to destroy the call after X seconds.
        final Message msg = handler.obtainMessage(EVENT_DISCONNECTED_TIMEOUT, call);
        handler.sendMessageDelayed(msg, getDelayForDisconnect(call));
        pendingDisconnectCalls.add(call);

        callById.put(call.getId(), call);
        callByTelecomCall.put(call.getTelecomCall(), call);
        updated = true;
      }
    } else if (!isCallDead(call)) {
      callById.put(call.getId(), call);
      callByTelecomCall.put(call.getTelecomCall(), call);
      updated = true;
    } else if (callById.containsKey(call.getId())) {
      callById.remove(call.getId());
      callByTelecomCall.remove(call.getTelecomCall());
      updated = true;
    }

    Trace.endSection();
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
    if (pendingDisconnectCalls.contains(call)) {
      pendingDisconnectCalls.remove(call);
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
    for (DialerCall call : callById.values()) {
      call.getVideoTech().setDeviceOrientation(rotation);
    }
  }

  public void onInCallUiShown(boolean forFullScreenIntent) {
    for (DialerCall call : callById.values()) {
      call.getLatencyReport().onInCallUiShown(forFullScreenIntent);
    }
    if (uiListeners != null) {
      uiListeners.onInCallUiShown();
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

  /** UiListener interface for measuring incall latency.(used by testing only) */
  public interface UiListener {

    /** Called when a new call gets added into call list from IncallServiceImpl */
    void onCallAdded();

    /** Called in the end of onResume method of IncallActivityCommon. */
    void onInCallUiShown();
  }

  private class DialerCallListenerImpl implements DialerCallListener {

    @NonNull private final DialerCall call;

    DialerCallListenerImpl(@NonNull DialerCall call) {
      this.call = Assert.isNotNull(call);
    }

    @Override
    public void onDialerCallDisconnect() {
      if (updateCallInMap(call)) {
        LogUtil.i("DialerCallListenerImpl.onDialerCallDisconnect", String.valueOf(call));
        // notify those listening for all disconnects
        notifyListenersOfDisconnect(call);
      }
    }

    @Override
    public void onDialerCallUpdate() {
      Trace.beginSection("CallList.onDialerCallUpdate");
      onUpdateCall(call);
      notifyGenericListeners();
      Trace.endSection();
    }

    @Override
    public void onDialerCallChildNumberChange() {}

    @Override
    public void onDialerCallLastForwardedNumberChange() {}

    @Override
    public void onDialerCallUpgradeToVideo() {
      for (Listener listener : listeners) {
        listener.onUpgradeToVideo(call);
      }
    }

    @Override
    public void onWiFiToLteHandover() {
      for (Listener listener : listeners) {
        listener.onWiFiToLteHandover(call);
      }
    }

    @Override
    public void onHandoverToWifiFailure() {
      for (Listener listener : listeners) {
        listener.onHandoverToWifiFailed(call);
      }
    }

    @Override
    public void onInternationalCallOnWifi() {
      LogUtil.enterBlock("DialerCallListenerImpl.onInternationalCallOnWifi");
      for (Listener listener : listeners) {
        listener.onInternationalCallOnWifi(call);
      }
    }

    @Override
    public void onEnrichedCallSessionUpdate() {}

    @Override
    public void onDialerCallSessionModificationStateChange() {
      for (Listener listener : listeners) {
        listener.onSessionModificationStateChange(call);
      }
    }
  }
}
