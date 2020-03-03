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

package com.android.incallui.call;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.os.Trace;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.os.BuildCompat;
import android.telecom.Call;
import android.telecom.Call.Details;
import android.telecom.Call.RttCall;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.GatewayInfo;
import android.telecom.InCallService.VideoCall;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.text.TextUtils;
import android.widget.Toast;
import com.android.contacts.common.compat.CallCompat;
import com.android.dialer.assisteddialing.ConcreteCreator;
import com.android.dialer.assisteddialing.TransformationInfo;
import com.android.dialer.blocking.FilteredNumbersUtil;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentParser;
import com.android.dialer.callintent.CallSpecificAppData;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DefaultFutureCallback;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.configprovider.ConfigProviderComponent;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.enrichedcall.EnrichedCallCapabilities;
import com.android.dialer.enrichedcall.EnrichedCallComponent;
import com.android.dialer.enrichedcall.EnrichedCallManager;
import com.android.dialer.enrichedcall.EnrichedCallManager.CapabilitiesListener;
import com.android.dialer.enrichedcall.EnrichedCallManager.Filter;
import com.android.dialer.enrichedcall.EnrichedCallManager.StateChangedListener;
import com.android.dialer.enrichedcall.Session;
import com.android.dialer.location.GeoUtil;
import com.android.dialer.logging.ContactLookupResult;
import com.android.dialer.logging.ContactLookupResult.Type;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.preferredsim.PreferredAccountRecorder;
import com.android.dialer.rtt.RttTranscript;
import com.android.dialer.rtt.RttTranscriptUtil;
import com.android.dialer.spam.status.SpamStatus;
import com.android.dialer.telecom.TelecomCallUtil;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.theme.common.R;
import com.android.dialer.time.Clock;
import com.android.dialer.util.PermissionsUtil;
import com.android.incallui.audiomode.AudioModeProvider;
import com.android.incallui.call.state.DialerCallState;
import com.android.incallui.latencyreport.LatencyReport;
import com.android.incallui.rtt.protocol.RttChatMessage;
import com.android.incallui.videotech.VideoTech;
import com.android.incallui.videotech.VideoTech.VideoTechListener;
import com.android.incallui.videotech.duo.DuoVideoTech;
import com.android.incallui.videotech.empty.EmptyVideoTech;
import com.android.incallui.videotech.ims.ImsVideoTech;
import com.android.incallui.videotech.utils.VideoUtils;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/** Describes a single call and its state. */
public class DialerCall implements VideoTechListener, StateChangedListener, CapabilitiesListener {

  public static final int CALL_HISTORY_STATUS_UNKNOWN = 0;
  public static final int CALL_HISTORY_STATUS_PRESENT = 1;
  public static final int CALL_HISTORY_STATUS_NOT_PRESENT = 2;

  // Hard coded property for {@code Call}. Upstreamed change from Motorola.
  // TODO(a bug): Move it to Telecom in framework.
  public static final int PROPERTY_CODEC_KNOWN = 0x04000000;

  private static final String ID_PREFIX = "DialerCall_";

  @VisibleForTesting
  public static final String CONFIG_EMERGENCY_CALLBACK_WINDOW_MILLIS =
      "emergency_callback_window_millis";

  private static int idCounter = 0;

  public static final int UNKNOWN_PEER_DIMENSIONS = -1;

  /**
   * A counter used to append to restricted/private/hidden calls so that users can identify them in
   * a conversation. This value is reset in {@link CallList#onCallRemoved(Context, Call)} when there
   * are no live calls.
   */
  private static int hiddenCounter;

  /**
   * The unique call ID for every call. This will help us to identify each call and allow us the
   * ability to stitch impressions to calls if needed.
   */
  private final String uniqueCallId = UUID.randomUUID().toString();

  private final Call telecomCall;
  private final LatencyReport latencyReport;
  private final String id;
  private final int hiddenId;
  private final List<String> childCallIds = new ArrayList<>();
  private final LogState logState = new LogState();
  private final Context context;
  private final DialerCallDelegate dialerCallDelegate;
  private final List<DialerCallListener> listeners = new CopyOnWriteArrayList<>();
  private final List<CannedTextResponsesLoadedListener> cannedTextResponsesLoadedListeners =
      new CopyOnWriteArrayList<>();
  private final VideoTechManager videoTechManager;

  private boolean isSpeakEasyCall;
  private boolean isEmergencyCall;
  private Uri handle;
  private int state = DialerCallState.INVALID;
  private DisconnectCause disconnectCause;

  private boolean hasShownLteToWiFiHandoverToast;
  private boolean hasShownWiFiToLteHandoverToast;
  private boolean doNotShowDialogForHandoffToWifiFailure;

  private String childNumber;
  private String lastForwardedNumber;
  private boolean isCallForwarded;
  private String callSubject;
  @Nullable private PhoneAccountHandle phoneAccountHandle;
  @CallHistoryStatus private int callHistoryStatus = CALL_HISTORY_STATUS_UNKNOWN;

  @Nullable private SpamStatus spamStatus;
  private boolean isBlocked;
  private boolean isOutgoing;

  private boolean didShowCameraPermission;
  private boolean didDismissVideoChargesAlertDialog;
  private PersistableBundle carrierConfig;
  private String callProviderLabel;
  private String callbackNumber;
  private int cameraDirection = CameraDirection.CAMERA_DIRECTION_UNKNOWN;
  private EnrichedCallCapabilities enrichedCallCapabilities;
  private Session enrichedCallSession;

  private int answerAndReleaseButtonDisplayedTimes = 0;
  private boolean releasedByAnsweringSecondCall = false;
  // Times when a second call is received but AnswerAndRelease button is not shown
  // since it's not supported.
  private int secondCallWithoutAnswerAndReleasedButtonTimes = 0;
  private VideoTech videoTech;

  private com.android.dialer.logging.VideoTech.Type selectedAvailableVideoTechType =
      com.android.dialer.logging.VideoTech.Type.NONE;
  private boolean isVoicemailNumber;
  private List<PhoneAccountHandle> callCapableAccounts;
  private String countryIso;

  private volatile boolean feedbackRequested = false;

  private Clock clock = System::currentTimeMillis;

  @Nullable private PreferredAccountRecorder preferredAccountRecorder;
  private boolean isCallRemoved;

  public static String getNumberFromHandle(Uri handle) {
    return handle == null ? "" : handle.getSchemeSpecificPart();
  }

  /**
   * Whether the call is put on hold by remote party. This is different than the {@link
   * DialerCallState#ONHOLD} state which indicates that the call is being held locally on the
   * device.
   */
  private boolean isRemotelyHeld;

  /** Indicates whether this call is currently in the process of being merged into a conference. */
  private boolean isMergeInProcess;

  /**
   * Indicates whether the phone account associated with this call supports specifying a call
   * subject.
   */
  private boolean isCallSubjectSupported;

  public RttTranscript getRttTranscript() {
    return rttTranscript;
  }

  public void setRttTranscript(RttTranscript rttTranscript) {
    this.rttTranscript = rttTranscript;
  }

  private RttTranscript rttTranscript;

  private final Call.Callback telecomCallCallback =
      new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int newState) {
          LogUtil.v("TelecomCallCallback.onStateChanged", "call=" + call + " newState=" + newState);
          update();
        }

        @Override
        public void onParentChanged(Call call, Call newParent) {
          LogUtil.v(
              "TelecomCallCallback.onParentChanged", "call=" + call + " newParent=" + newParent);
          update();
        }

        @Override
        public void onChildrenChanged(Call call, List<Call> children) {
          update();
        }

        @Override
        public void onDetailsChanged(Call call, Call.Details details) {
          LogUtil.v(
              "TelecomCallCallback.onDetailsChanged", " call=" + call + " details=" + details);
          update();
        }

        @Override
        public void onCannedTextResponsesLoaded(Call call, List<String> cannedTextResponses) {
          LogUtil.v(
              "TelecomCallCallback.onCannedTextResponsesLoaded",
              "call=" + call + " cannedTextResponses=" + cannedTextResponses);
          for (CannedTextResponsesLoadedListener listener : cannedTextResponsesLoadedListeners) {
            listener.onCannedTextResponsesLoaded(DialerCall.this);
          }
        }

        @Override
        public void onPostDialWait(Call call, String remainingPostDialSequence) {
          LogUtil.v(
              "TelecomCallCallback.onPostDialWait",
              "call=" + call + " remainingPostDialSequence=" + remainingPostDialSequence);
          update();
        }

        @Override
        public void onVideoCallChanged(Call call, VideoCall videoCall) {
          LogUtil.v(
              "TelecomCallCallback.onVideoCallChanged", "call=" + call + " videoCall=" + videoCall);
          update();
        }

        @Override
        public void onCallDestroyed(Call call) {
          LogUtil.v("TelecomCallCallback.onCallDestroyed", "call=" + call);
          unregisterCallback();
        }

        @Override
        public void onConferenceableCallsChanged(Call call, List<Call> conferenceableCalls) {
          LogUtil.v(
              "TelecomCallCallback.onConferenceableCallsChanged",
              "call %s, conferenceable calls: %d",
              call,
              conferenceableCalls.size());
          update();
        }

        @Override
        public void onRttModeChanged(Call call, int mode) {
          LogUtil.v("TelecomCallCallback.onRttModeChanged", "mode=%d", mode);
        }

        @Override
        public void onRttRequest(Call call, int id) {
          LogUtil.v("TelecomCallCallback.onRttRequest", "id=%d", id);
          for (DialerCallListener listener : listeners) {
            listener.onDialerCallUpgradeToRtt(id);
          }
        }

        @Override
        public void onRttInitiationFailure(Call call, int reason) {
          LogUtil.v("TelecomCallCallback.onRttInitiationFailure", "reason=%d", reason);
          Toast.makeText(context, R.string.rtt_call_not_available_toast, Toast.LENGTH_LONG).show();
          update();
        }

        @Override
        public void onRttStatusChanged(Call call, boolean enabled, RttCall rttCall) {
          LogUtil.v("TelecomCallCallback.onRttStatusChanged", "enabled=%b", enabled);
          if (enabled) {
            Logger.get(context)
                .logCallImpression(
                    DialerImpression.Type.RTT_MID_CALL_ENABLED,
                    getUniqueCallId(),
                    getTimeAddedMs());
          }
          update();
        }

        @Override
        public void onConnectionEvent(android.telecom.Call call, String event, Bundle extras) {
          LogUtil.v(
              "TelecomCallCallback.onConnectionEvent",
              "Call: " + call + ", Event: " + event + ", Extras: " + extras);
          switch (event) {
              // The Previous attempt to Merge two calls together has failed in Telecom. We must
              // now update the UI to possibly re-enable the Merge button based on the number of
              // currently conferenceable calls available or Connection Capabilities.
            case android.telecom.Connection.EVENT_CALL_MERGE_FAILED:
              isMergeInProcess = false;
              update();
              break;
            case TelephonyManagerCompat.EVENT_HANDOVER_VIDEO_FROM_WIFI_TO_LTE:
              notifyWiFiToLteHandover();
              break;
            case TelephonyManagerCompat.EVENT_HANDOVER_VIDEO_FROM_LTE_TO_WIFI:
              onLteToWifiHandover();
              break;
            case TelephonyManagerCompat.EVENT_HANDOVER_TO_WIFI_FAILED:
              notifyHandoverToWifiFailed();
              break;
            case TelephonyManagerCompat.EVENT_CALL_REMOTELY_HELD:
              isRemotelyHeld = true;
              update();
              break;
            case TelephonyManagerCompat.EVENT_CALL_REMOTELY_UNHELD:
              isRemotelyHeld = false;
              update();
              break;
            case TelephonyManagerCompat.EVENT_NOTIFY_INTERNATIONAL_CALL_ON_WFC:
              notifyInternationalCallOnWifi();
              break;
            case TelephonyManagerCompat.EVENT_MERGE_START:
              LogUtil.i("DialerCall.onConnectionEvent", "merge start");
              isMergeInProcess = true;
              break;
            case TelephonyManagerCompat.EVENT_MERGE_COMPLETE:
              LogUtil.i("DialerCall.onConnectionEvent", "merge complete");
              isMergeInProcess = false;
              break;
            case TelephonyManagerCompat.EVENT_CALL_FORWARDED:
              // Only handle this event for P+ since it's unreliable pre-P.
              if (BuildCompat.isAtLeastP()) {
                isCallForwarded = true;
                update();
              }
              break;
            default:
              break;
          }
        }
      };

  private long timeAddedMs;
  private int peerDimensionWidth = UNKNOWN_PEER_DIMENSIONS;
  private int peerDimensionHeight = UNKNOWN_PEER_DIMENSIONS;

  public DialerCall(
      Context context,
      DialerCallDelegate dialerCallDelegate,
      Call telecomCall,
      LatencyReport latencyReport,
      boolean registerCallback) {
    Assert.isNotNull(context);
    this.context = context;
    this.dialerCallDelegate = dialerCallDelegate;
    this.telecomCall = telecomCall;
    this.latencyReport = latencyReport;
    id = ID_PREFIX + Integer.toString(idCounter++);

    // Must be after assigning mTelecomCall
    videoTechManager = new VideoTechManager(this);

    updateFromTelecomCall();
    if (isHiddenNumber() && TextUtils.isEmpty(getNumber())) {
      hiddenId = ++hiddenCounter;
    } else {
      hiddenId = 0;
    }

    if (registerCallback) {
      this.telecomCall.registerCallback(telecomCallCallback);
    }

    timeAddedMs = System.currentTimeMillis();
    parseCallSpecificAppData();

    updateEnrichedCallSession();
  }

  private static int translateState(int state) {
    switch (state) {
      case Call.STATE_NEW:
      case Call.STATE_CONNECTING:
        return DialerCallState.CONNECTING;
      case Call.STATE_SELECT_PHONE_ACCOUNT:
        return DialerCallState.SELECT_PHONE_ACCOUNT;
      case Call.STATE_DIALING:
        return DialerCallState.DIALING;
      case Call.STATE_PULLING_CALL:
        return DialerCallState.PULLING;
      case Call.STATE_RINGING:
        return DialerCallState.INCOMING;
      case Call.STATE_ACTIVE:
        return DialerCallState.ACTIVE;
      case Call.STATE_HOLDING:
        return DialerCallState.ONHOLD;
      case Call.STATE_DISCONNECTED:
        return DialerCallState.DISCONNECTED;
      case Call.STATE_DISCONNECTING:
        return DialerCallState.DISCONNECTING;
      default:
        return DialerCallState.INVALID;
    }
  }

  public static boolean areSame(DialerCall call1, DialerCall call2) {
    if (call1 == null && call2 == null) {
      return true;
    } else if (call1 == null || call2 == null) {
      return false;
    }

    // otherwise compare call Ids
    return call1.getId().equals(call2.getId());
  }

  public void addListener(DialerCallListener listener) {
    Assert.isMainThread();
    listeners.add(listener);
  }

  public void removeListener(DialerCallListener listener) {
    Assert.isMainThread();
    listeners.remove(listener);
  }

  public void addCannedTextResponsesLoadedListener(CannedTextResponsesLoadedListener listener) {
    Assert.isMainThread();
    cannedTextResponsesLoadedListeners.add(listener);
  }

  public void removeCannedTextResponsesLoadedListener(CannedTextResponsesLoadedListener listener) {
    Assert.isMainThread();
    cannedTextResponsesLoadedListeners.remove(listener);
  }

  private void onLteToWifiHandover() {
    LogUtil.enterBlock("DialerCall.onLteToWifiHandover");
    if (hasShownLteToWiFiHandoverToast) {
      return;
    }

    Toast.makeText(context, R.string.video_call_lte_to_wifi_handover_toast, Toast.LENGTH_LONG)
        .show();
    hasShownLteToWiFiHandoverToast = true;
  }

  public void notifyWiFiToLteHandover() {
    LogUtil.i("DialerCall.notifyWiFiToLteHandover", "");
    for (DialerCallListener listener : listeners) {
      listener.onWiFiToLteHandover();
    }
  }

  public void notifyHandoverToWifiFailed() {
    LogUtil.i("DialerCall.notifyHandoverToWifiFailed", "");
    for (DialerCallListener listener : listeners) {
      listener.onHandoverToWifiFailure();
    }
  }

  public void notifyInternationalCallOnWifi() {
    LogUtil.enterBlock("DialerCall.notifyInternationalCallOnWifi");
    for (DialerCallListener dialerCallListener : listeners) {
      dialerCallListener.onInternationalCallOnWifi();
    }
  }

  /* package-private */ Call getTelecomCall() {
    return telecomCall;
  }

  public StatusHints getStatusHints() {
    return telecomCall.getDetails().getStatusHints();
  }

  public int getCameraDir() {
    return cameraDirection;
  }

  public void setCameraDir(int cameraDir) {
    if (cameraDir == CameraDirection.CAMERA_DIRECTION_FRONT_FACING
        || cameraDir == CameraDirection.CAMERA_DIRECTION_BACK_FACING) {
      cameraDirection = cameraDir;
    } else {
      cameraDirection = CameraDirection.CAMERA_DIRECTION_UNKNOWN;
    }
  }

  public boolean wasParentCall() {
    return logState.conferencedCalls != 0;
  }

  public boolean isVoiceMailNumber() {
    return isVoicemailNumber;
  }

  public List<PhoneAccountHandle> getCallCapableAccounts() {
    return callCapableAccounts;
  }

  public String getCountryIso() {
    return countryIso;
  }

  private void updateIsVoiceMailNumber() {
    if (getHandle() != null && PhoneAccount.SCHEME_VOICEMAIL.equals(getHandle().getScheme())) {
      isVoicemailNumber = true;
      return;
    }

    if (!PermissionsUtil.hasPermission(context, permission.READ_PHONE_STATE)) {
      isVoicemailNumber = false;
      return;
    }

    isVoicemailNumber = TelecomUtil.isVoicemailNumber(context, getAccountHandle(), getNumber());
  }

  private void update() {
    Trace.beginSection("DialerCall.update");
    int oldState = getState();
    // Clear any cache here that could potentially change on update.
    videoTech = null;
    // We want to potentially register a video call callback here.
    updateFromTelecomCall();
    if (oldState != getState() && getState() == DialerCallState.DISCONNECTED) {
      for (DialerCallListener listener : listeners) {
        listener.onDialerCallDisconnect();
      }
      EnrichedCallComponent.get(context)
          .getEnrichedCallManager()
          .unregisterCapabilitiesListener(this);
      EnrichedCallComponent.get(context)
          .getEnrichedCallManager()
          .unregisterStateChangedListener(this);
    } else {
      for (DialerCallListener listener : listeners) {
        listener.onDialerCallUpdate();
      }
    }
    Trace.endSection();
  }

  @SuppressWarnings("MissingPermission")
  private void updateFromTelecomCall() {
    Trace.beginSection("DialerCall.updateFromTelecomCall");
    LogUtil.v("DialerCall.updateFromTelecomCall", telecomCall.toString());

    videoTechManager.dispatchCallStateChanged(telecomCall.getState(), getAccountHandle());

    final int translatedState = translateState(telecomCall.getState());
    if (state != DialerCallState.BLOCKED) {
      setState(translatedState);
      setDisconnectCause(telecomCall.getDetails().getDisconnectCause());
    }

    childCallIds.clear();
    final int numChildCalls = telecomCall.getChildren().size();
    for (int i = 0; i < numChildCalls; i++) {
      childCallIds.add(
          dialerCallDelegate
              .getDialerCallFromTelecomCall(telecomCall.getChildren().get(i))
              .getId());
    }

    // The number of conferenced calls can change over the course of the call, so use the
    // maximum number of conferenced child calls as the metric for conference call usage.
    logState.conferencedCalls = Math.max(numChildCalls, logState.conferencedCalls);

    updateFromCallExtras(telecomCall.getDetails().getExtras());

    // If the handle of the call has changed, update state for the call determining if it is an
    // emergency call.
    Uri newHandle = telecomCall.getDetails().getHandle();
    if (!Objects.equals(handle, newHandle)) {
      handle = newHandle;
      updateEmergencyCallState();
    }

    TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
    // If the phone account handle of the call is set, cache capability bit indicating whether
    // the phone account supports call subjects.
    PhoneAccountHandle newPhoneAccountHandle = telecomCall.getDetails().getAccountHandle();
    if (!Objects.equals(phoneAccountHandle, newPhoneAccountHandle)) {
      phoneAccountHandle = newPhoneAccountHandle;

      if (phoneAccountHandle != null) {
        PhoneAccount phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);
        if (phoneAccount != null) {
          isCallSubjectSupported =
              phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_CALL_SUBJECT);
          if (phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
            cacheCarrierConfiguration(phoneAccountHandle);
          }
        }
      }
    }
    if (PermissionsUtil.hasPermission(context, permission.READ_PHONE_STATE)) {
      updateIsVoiceMailNumber();
      callCapableAccounts = telecomManager.getCallCapablePhoneAccounts();
      countryIso = GeoUtil.getCurrentCountryIso(context);
    }
    Trace.endSection();
  }

  /**
   * Caches frequently used carrier configuration locally.
   *
   * @param accountHandle The PhoneAccount handle.
   */
  @SuppressLint("MissingPermission")
  private void cacheCarrierConfiguration(PhoneAccountHandle accountHandle) {
    if (!PermissionsUtil.hasPermission(context, permission.READ_PHONE_STATE)) {
      return;
    }
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return;
    }
    // TODO(a bug): This may take several seconds to complete, revisit it to move it to worker
    // thread.
    carrierConfig =
        TelephonyManagerCompat.getTelephonyManagerForPhoneAccountHandle(context, accountHandle)
            .getCarrierConfig();
  }

  /**
   * Tests corruption of the {@code callExtras} bundle by calling {@link
   * Bundle#containsKey(String)}. If the bundle is corrupted a {@link IllegalArgumentException} will
   * be thrown and caught by this function.
   *
   * @param callExtras the bundle to verify
   * @return {@code true} if the bundle is corrupted, {@code false} otherwise.
   */
  protected boolean areCallExtrasCorrupted(Bundle callExtras) {
    /**
     * There's currently a bug in Telephony service (a bug) that could corrupt the extras
     * bundle, resulting in a IllegalArgumentException while validating data under {@link
     * Bundle#containsKey(String)}.
     */
    try {
      callExtras.containsKey(Connection.EXTRA_CHILD_ADDRESS);
      return false;
    } catch (IllegalArgumentException e) {
      LogUtil.e(
          "DialerCall.areCallExtrasCorrupted", "callExtras is corrupted, ignoring exception", e);
      return true;
    }
  }

  protected void updateFromCallExtras(Bundle callExtras) {
    if (callExtras == null || areCallExtrasCorrupted(callExtras)) {
      /**
       * If the bundle is corrupted, abandon information update as a work around. These are not
       * critical for the dialer to function.
       */
      return;
    }
    // Check for a change in the child address and notify any listeners.
    if (callExtras.containsKey(Connection.EXTRA_CHILD_ADDRESS)) {
      String childNumber = callExtras.getString(Connection.EXTRA_CHILD_ADDRESS);
      if (!Objects.equals(childNumber, this.childNumber)) {
        this.childNumber = childNumber;
        for (DialerCallListener listener : listeners) {
          listener.onDialerCallChildNumberChange();
        }
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
          lastForwardedNumber = lastForwardedNumbers.get(lastForwardedNumbers.size() - 1);
        }

        if (!Objects.equals(lastForwardedNumber, this.lastForwardedNumber)) {
          this.lastForwardedNumber = lastForwardedNumber;
          for (DialerCallListener listener : listeners) {
            listener.onDialerCallLastForwardedNumberChange();
          }
        }
      }
    }

    // DialerCall subject is present in the extras at the start of call, so we do not need to
    // notify any other listeners of this.
    if (callExtras.containsKey(Connection.EXTRA_CALL_SUBJECT)) {
      String callSubject = callExtras.getString(Connection.EXTRA_CALL_SUBJECT);
      if (!Objects.equals(this.callSubject, callSubject)) {
        this.callSubject = callSubject;
      }
    }
  }

  public String getId() {
    return id;
  }

  /**
   * @return name appended with a number if the number is restricted/unknown and the user has
   *     received more than one restricted/unknown call.
   */
  @Nullable
  public String updateNameIfRestricted(@Nullable String name) {
    if (name != null && isHiddenNumber() && hiddenId != 0 && hiddenCounter > 1) {
      return context.getString(R.string.unknown_counter, name, hiddenId);
    }
    return name;
  }

  public static void clearRestrictedCount() {
    hiddenCounter = 0;
  }

  private boolean isHiddenNumber() {
    return getNumberPresentation() == TelecomManager.PRESENTATION_RESTRICTED
        || getNumberPresentation() == TelecomManager.PRESENTATION_UNKNOWN;
  }

  public boolean hasShownWiFiToLteHandoverToast() {
    return hasShownWiFiToLteHandoverToast;
  }

  public void setHasShownWiFiToLteHandoverToast() {
    hasShownWiFiToLteHandoverToast = true;
  }

  public boolean showWifiHandoverAlertAsToast() {
    return doNotShowDialogForHandoffToWifiFailure;
  }

  public void setDoNotShowDialogForHandoffToWifiFailure(boolean bool) {
    doNotShowDialogForHandoffToWifiFailure = bool;
  }

  public boolean showVideoChargesAlertDialog() {
    if (carrierConfig == null) {
      return false;
    }
    return carrierConfig.getBoolean(
        TelephonyManagerCompat.CARRIER_CONFIG_KEY_SHOW_VIDEO_CALL_CHARGES_ALERT_DIALOG_BOOL);
  }

  public long getTimeAddedMs() {
    return timeAddedMs;
  }

  @Nullable
  public String getNumber() {
    return TelecomCallUtil.getNumber(telecomCall);
  }

  public void blockCall() {
    telecomCall.reject(false, null);
    setState(DialerCallState.BLOCKED);
  }

  @Nullable
  public Uri getHandle() {
    return telecomCall == null ? null : telecomCall.getDetails().getHandle();
  }

  public boolean isEmergencyCall() {
    return isEmergencyCall;
  }

  public boolean isPotentialEmergencyCallback() {
    // The property PROPERTY_EMERGENCY_CALLBACK_MODE is only set for CDMA calls when the system
    // is actually in emergency callback mode (ie data is disabled).
    if (hasProperty(Details.PROPERTY_EMERGENCY_CALLBACK_MODE)) {
      return true;
    }

    // Call.EXTRA_LAST_EMERGENCY_CALLBACK_TIME_MILLIS is available starting in O
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      long timestampMillis = FilteredNumbersUtil.getLastEmergencyCallTimeMillis(context);
      return isInEmergencyCallbackWindow(timestampMillis);
    }

    // We want to treat any incoming call that arrives a short time after an outgoing emergency call
    // as a potential emergency callback.
    if (getExtras() != null
        && getExtras().getLong(Call.EXTRA_LAST_EMERGENCY_CALLBACK_TIME_MILLIS, 0) > 0) {
      long lastEmergencyCallMillis =
          getExtras().getLong(Call.EXTRA_LAST_EMERGENCY_CALLBACK_TIME_MILLIS, 0);
      if (isInEmergencyCallbackWindow(lastEmergencyCallMillis)) {
        return true;
      }
    }
    return false;
  }

  boolean isInEmergencyCallbackWindow(long timestampMillis) {
    long emergencyCallbackWindowMillis =
        ConfigProviderComponent.get(context)
            .getConfigProvider()
            .getLong(CONFIG_EMERGENCY_CALLBACK_WINDOW_MILLIS, TimeUnit.MINUTES.toMillis(5));
    return System.currentTimeMillis() - timestampMillis < emergencyCallbackWindowMillis;
  }

  public int getState() {
    if (telecomCall != null && telecomCall.getParent() != null) {
      return DialerCallState.CONFERENCED;
    } else {
      return state;
    }
  }

  public int getNonConferenceState() {
    return state;
  }

  public void setState(int state) {
    if (state == DialerCallState.INCOMING) {
      logState.isIncoming = true;
    }
    updateCallTiming(state);

    this.state = state;
  }

  private void updateCallTiming(int newState) {
    if (newState == DialerCallState.ACTIVE) {
      if (this.state == DialerCallState.ACTIVE) {
        LogUtil.i("DialerCall.updateCallTiming", "state is already active");
        return;
      }
      logState.dialerConnectTimeMillis = clock.currentTimeMillis();
      logState.dialerConnectTimeMillisElapsedRealtime = SystemClock.elapsedRealtime();
    }

    if (newState == DialerCallState.DISCONNECTED) {
      long newDuration =
          getConnectTimeMillis() == 0 ? 0 : clock.currentTimeMillis() - getConnectTimeMillis();
      if (this.state == DialerCallState.DISCONNECTED) {
        LogUtil.i(
            "DialerCall.setState",
            "ignoring state transition from DISCONNECTED to DISCONNECTED."
                + " Duration would have changed from %s to %s",
            logState.telecomDurationMillis,
            newDuration);
        return;
      }
      logState.telecomDurationMillis = newDuration;
      logState.dialerDurationMillis =
          logState.dialerConnectTimeMillis == 0
              ? 0
              : clock.currentTimeMillis() - logState.dialerConnectTimeMillis;
      logState.dialerDurationMillisElapsedRealtime =
          logState.dialerConnectTimeMillisElapsedRealtime == 0
              ? 0
              : SystemClock.elapsedRealtime() - logState.dialerConnectTimeMillisElapsedRealtime;
    } else if (state == DialerCallState.DIALING || state == DialerCallState.CONNECTING) {
      isOutgoing = true;
    }
  }

  @VisibleForTesting
  void setClock(Clock clock) {
    this.clock = clock;
  }

  public boolean isOutgoing() {
    return isOutgoing;
  }

  public int getNumberPresentation() {
    return telecomCall == null ? -1 : telecomCall.getDetails().getHandlePresentation();
  }

  public int getCnapNamePresentation() {
    return telecomCall == null ? -1 : telecomCall.getDetails().getCallerDisplayNamePresentation();
  }

  @Nullable
  public String getCnapName() {
    return telecomCall == null ? null : getTelecomCall().getDetails().getCallerDisplayName();
  }

  public Bundle getIntentExtras() {
    return telecomCall.getDetails().getIntentExtras();
  }

  @Nullable
  public Bundle getExtras() {
    return telecomCall == null ? null : telecomCall.getDetails().getExtras();
  }

  /** @return The child number for the call, or {@code null} if none specified. */
  public String getChildNumber() {
    return childNumber;
  }

  /** @return The last forwarded number for the call, or {@code null} if none specified. */
  public String getLastForwardedNumber() {
    return lastForwardedNumber;
  }

  public boolean isCallForwarded() {
    return isCallForwarded;
  }

  /** @return The call subject, or {@code null} if none specified. */
  public String getCallSubject() {
    return callSubject;
  }

  /**
   * @return {@code true} if the call's phone account supports call subjects, {@code false}
   *     otherwise.
   */
  public boolean isCallSubjectSupported() {
    return isCallSubjectSupported;
  }

  /** Returns call disconnect cause, defined by {@link DisconnectCause}. */
  public DisconnectCause getDisconnectCause() {
    if (state == DialerCallState.DISCONNECTED || state == DialerCallState.IDLE) {
      return disconnectCause;
    }

    return new DisconnectCause(DisconnectCause.UNKNOWN);
  }

  public void setDisconnectCause(DisconnectCause disconnectCause) {
    this.disconnectCause = disconnectCause;
    logState.disconnectCause = this.disconnectCause;
  }

  /** Returns the possible text message responses. */
  public List<String> getCannedSmsResponses() {
    return telecomCall.getCannedTextResponses();
  }

  /** Checks if the call supports the given set of capabilities supplied as a bit mask. */
  @TargetApi(28)
  public boolean can(int capabilities) {
    int supportedCapabilities = telecomCall.getDetails().getCallCapabilities();

    if ((capabilities & Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0) {
      boolean hasConferenceableCall = false;
      // RTT call is not conferenceable, it's a bug (a bug) in Telecom and we work around it
      // here before it's fixed in Telecom.
      for (Call call : telecomCall.getConferenceableCalls()) {
        if (!(BuildCompat.isAtLeastP() && call.isRttActive())) {
          hasConferenceableCall = true;
          break;
        }
      }
      // We allow you to merge if the capabilities allow it or if it is a call with
      // conferenceable calls.
      if (!hasConferenceableCall
          && ((Call.Details.CAPABILITY_MERGE_CONFERENCE & supportedCapabilities) == 0)) {
        // Cannot merge calls if there are no calls to merge with.
        return false;
      }
      capabilities &= ~Call.Details.CAPABILITY_MERGE_CONFERENCE;
    }
    return (capabilities == (capabilities & supportedCapabilities));
  }

  public boolean hasProperty(int property) {
    return telecomCall.getDetails().hasProperty(property);
  }

  @NonNull
  public String getUniqueCallId() {
    return uniqueCallId;
  }

  /** Gets the time when the call first became active. */
  public long getConnectTimeMillis() {
    return telecomCall.getDetails().getConnectTimeMillis();
  }

  /**
   * Gets the time when the call is created (see {@link Details#getCreationTimeMillis()}). This is
   * the same time that is logged as the start time in the Call Log (see {@link
   * android.provider.CallLog.Calls#DATE}).
   */
  @TargetApi(VERSION_CODES.O)
  public long getCreationTimeMillis() {
    return telecomCall.getDetails().getCreationTimeMillis();
  }

  public boolean isConferenceCall() {
    return hasProperty(Call.Details.PROPERTY_CONFERENCE);
  }

  @Nullable
  public GatewayInfo getGatewayInfo() {
    return telecomCall == null ? null : telecomCall.getDetails().getGatewayInfo();
  }

  @Nullable
  public PhoneAccountHandle getAccountHandle() {
    return telecomCall == null ? null : telecomCall.getDetails().getAccountHandle();
  }

  /** @return The {@link VideoCall} instance associated with the {@link Call}. */
  public VideoCall getVideoCall() {
    return telecomCall == null ? null : telecomCall.getVideoCall();
  }

  public List<String> getChildCallIds() {
    return childCallIds;
  }

  public String getParentId() {
    Call parentCall = telecomCall.getParent();
    if (parentCall != null) {
      return dialerCallDelegate.getDialerCallFromTelecomCall(parentCall).getId();
    }
    return null;
  }

  public int getVideoState() {
    return telecomCall.getDetails().getVideoState();
  }

  public boolean isVideoCall() {
    return getVideoTech().isTransmittingOrReceiving() || VideoProfile.isVideo(getVideoState());
  }

  @TargetApi(28)
  public boolean isActiveRttCall() {
    if (BuildCompat.isAtLeastP()) {
      return getTelecomCall().isRttActive();
    } else {
      return false;
    }
  }

  @TargetApi(28)
  @Nullable
  public RttCall getRttCall() {
    if (!isActiveRttCall()) {
      return null;
    }
    return getTelecomCall().getRttCall();
  }

  @TargetApi(28)
  public boolean isPhoneAccountRttCapable() {
    PhoneAccount phoneAccount = getPhoneAccount();
    if (phoneAccount == null) {
      return false;
    }
    if (!phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_RTT)) {
      return false;
    }
    return true;
  }

  @TargetApi(28)
  public boolean canUpgradeToRttCall() {
    if (!isPhoneAccountRttCapable()) {
      return false;
    }
    if (isActiveRttCall()) {
      return false;
    }
    if (isVideoCall()) {
      return false;
    }
    if (isConferenceCall()) {
      return false;
    }
    if (CallList.getInstance().hasActiveRttCall()) {
      return false;
    }
    return true;
  }

  @TargetApi(28)
  public void sendRttUpgradeRequest() {
    getTelecomCall().sendRttRequest();
  }

  @TargetApi(28)
  public void respondToRttRequest(boolean accept, int rttRequestId) {
    Logger.get(context)
        .logCallImpression(
            accept
                ? DialerImpression.Type.RTT_MID_CALL_ACCEPTED
                : DialerImpression.Type.RTT_MID_CALL_REJECTED,
            getUniqueCallId(),
            getTimeAddedMs());
    getTelecomCall().respondToRttRequest(rttRequestId, accept);
  }

  @TargetApi(28)
  private void saveRttTranscript() {
    if (!BuildCompat.isAtLeastP()) {
      return;
    }
    if (getRttCall() != null) {
      // Save any remaining text in the buffer that's not shown by UI yet.
      // This may happen when the call is switched to background before disconnect.
      try {
        String messageLeft = getRttCall().readImmediately();
        if (!TextUtils.isEmpty(messageLeft)) {
          rttTranscript =
              RttChatMessage.getRttTranscriptWithNewRemoteMessage(rttTranscript, messageLeft);
        }
      } catch (IOException e) {
        LogUtil.e("DialerCall.saveRttTranscript", "error when reading remaining message", e);
      }
    }
    // Don't save transcript if it's empty.
    if (rttTranscript.getMessagesCount() == 0) {
      return;
    }
    Futures.addCallback(
        RttTranscriptUtil.saveRttTranscript(context, rttTranscript),
        new DefaultFutureCallback<>(),
        MoreExecutors.directExecutor());
  }

  public boolean hasReceivedVideoUpgradeRequest() {
    return VideoUtils.hasReceivedVideoUpgradeRequest(getVideoTech().getSessionModificationState());
  }

  public boolean hasSentVideoUpgradeRequest() {
    return VideoUtils.hasSentVideoUpgradeRequest(getVideoTech().getSessionModificationState());
  }

  public boolean hasSentRttUpgradeRequest() {
    return false;
  }

  /**
   * Determines if the call handle is an emergency number or not and caches the result to avoid
   * repeated calls to isEmergencyNumber.
   */
  private void updateEmergencyCallState() {
    isEmergencyCall = TelecomCallUtil.isEmergencyCall(telecomCall);
  }

  public LogState getLogState() {
    return logState;
  }

  /**
   * Determines if the call is an external call.
   *
   * <p>An external call is one which does not exist locally for the {@link
   * android.telecom.ConnectionService} it is associated with.
   *
   * @return {@code true} if the call is an external call, {@code false} otherwise.
   */
  boolean isExternalCall() {
    return hasProperty(CallCompat.Details.PROPERTY_IS_EXTERNAL_CALL);
  }

  /**
   * Determines if answering this call will cause an ongoing video call to be dropped.
   *
   * @return {@code true} if answering this call will drop an ongoing video call, {@code false}
   *     otherwise.
   */
  public boolean answeringDisconnectsForegroundVideoCall() {
    Bundle extras = getExtras();
    if (extras == null
        || !extras.containsKey(CallCompat.Details.EXTRA_ANSWERING_DROPS_FOREGROUND_CALL)) {
      return false;
    }
    return extras.getBoolean(CallCompat.Details.EXTRA_ANSWERING_DROPS_FOREGROUND_CALL);
  }

  private void parseCallSpecificAppData() {
    if (isExternalCall()) {
      return;
    }

    logState.callSpecificAppData = CallIntentParser.getCallSpecificAppData(getIntentExtras());
    if (logState.callSpecificAppData == null) {

      logState.callSpecificAppData =
          CallSpecificAppData.newBuilder()
              .setCallInitiationType(CallInitiationType.Type.EXTERNAL_INITIATION)
              .build();
    }
    if (getState() == DialerCallState.INCOMING) {
      logState.callSpecificAppData =
          logState
              .callSpecificAppData
              .toBuilder()
              .setCallInitiationType(CallInitiationType.Type.INCOMING_INITIATION)
              .build();
    }
  }

  @Override
  public String toString() {
    if (telecomCall == null) {
      // This should happen only in testing since otherwise we would never have a null
      // Telecom call.
      return String.valueOf(id);
    }

    return String.format(
        Locale.US,
        "[%s, %s, %s, %s, children:%s, parent:%s, "
            + "conferenceable:%s, videoState:%s, mSessionModificationState:%d, CameraDir:%s]",
        id,
        DialerCallState.toString(getState()),
        Details.capabilitiesToString(telecomCall.getDetails().getCallCapabilities()),
        Details.propertiesToString(telecomCall.getDetails().getCallProperties()),
        childCallIds,
        getParentId(),
        this.telecomCall.getConferenceableCalls(),
        VideoProfile.videoStateToString(telecomCall.getDetails().getVideoState()),
        getVideoTech().getSessionModificationState(),
        getCameraDir());
  }

  public String toSimpleString() {
    return super.toString();
  }

  @CallHistoryStatus
  public int getCallHistoryStatus() {
    return callHistoryStatus;
  }

  public void setCallHistoryStatus(@CallHistoryStatus int callHistoryStatus) {
    this.callHistoryStatus = callHistoryStatus;
  }

  public boolean didShowCameraPermission() {
    return didShowCameraPermission;
  }

  public void setDidShowCameraPermission(boolean didShow) {
    didShowCameraPermission = didShow;
  }

  public boolean didDismissVideoChargesAlertDialog() {
    return didDismissVideoChargesAlertDialog;
  }

  public void setDidDismissVideoChargesAlertDialog(boolean didDismiss) {
    didDismissVideoChargesAlertDialog = didDismiss;
  }

  public void setSpamStatus(@Nullable SpamStatus spamStatus) {
    this.spamStatus = spamStatus;
  }

  public Optional<SpamStatus> getSpamStatus() {
    return Optional.fromNullable(spamStatus);
  }

  public boolean isSpam() {
    if (spamStatus == null || !spamStatus.isSpam()) {
      return false;
    }

    if (!isIncoming()) {
      return false;
    }

    if (isPotentialEmergencyCallback()) {
      return false;
    }

    return true;
  }

  public boolean isBlocked() {
    return isBlocked;
  }

  public void setBlockedStatus(boolean isBlocked) {
    this.isBlocked = isBlocked;
  }

  public boolean isRemotelyHeld() {
    return isRemotelyHeld;
  }

  public boolean isMergeInProcess() {
    return isMergeInProcess;
  }

  public boolean isIncoming() {
    return logState.isIncoming;
  }

  /**
   * Try and determine if the call used assisted dialing.
   *
   * <p>We will not be able to verify a call underwent assisted dialing until the Platform
   * implmentation is complete in P+.
   *
   * @return a boolean indicating assisted dialing may have been performed
   */
  public boolean isAssistedDialed() {
    if (getIntentExtras() != null) {
      // P and below uses the existence of USE_ASSISTED_DIALING to indicate assisted dialing
      // was used. The Dialer client is responsible for performing assisted dialing before
      // placing the outgoing call.
      //
      // The existence of the assisted dialing extras indicates that assisted dialing took place.
      if (getIntentExtras().getBoolean(TelephonyManagerCompat.USE_ASSISTED_DIALING, false)
          && getAssistedDialingExtras() != null
          && Build.VERSION.SDK_INT <= ConcreteCreator.BUILD_CODE_CEILING) {
        return true;
      }
    }

    return false;
  }

  @Nullable
  public TransformationInfo getAssistedDialingExtras() {
    if (getIntentExtras() == null) {
      return null;
    }

    if (getIntentExtras().getBundle(TelephonyManagerCompat.ASSISTED_DIALING_EXTRAS) == null) {
      return null;
    }

    // Used in N-OMR1
    return TransformationInfo.newInstanceFromBundle(
        getIntentExtras().getBundle(TelephonyManagerCompat.ASSISTED_DIALING_EXTRAS));
  }

  public LatencyReport getLatencyReport() {
    return latencyReport;
  }

  public int getAnswerAndReleaseButtonDisplayedTimes() {
    return answerAndReleaseButtonDisplayedTimes;
  }

  public void increaseAnswerAndReleaseButtonDisplayedTimes() {
    answerAndReleaseButtonDisplayedTimes++;
  }

  public boolean getReleasedByAnsweringSecondCall() {
    return releasedByAnsweringSecondCall;
  }

  public void setReleasedByAnsweringSecondCall(boolean releasedByAnsweringSecondCall) {
    this.releasedByAnsweringSecondCall = releasedByAnsweringSecondCall;
  }

  public int getSecondCallWithoutAnswerAndReleasedButtonTimes() {
    return secondCallWithoutAnswerAndReleasedButtonTimes;
  }

  public void increaseSecondCallWithoutAnswerAndReleasedButtonTimes() {
    secondCallWithoutAnswerAndReleasedButtonTimes++;
  }

  @Nullable
  public EnrichedCallCapabilities getEnrichedCallCapabilities() {
    return enrichedCallCapabilities;
  }

  public void setEnrichedCallCapabilities(
      @Nullable EnrichedCallCapabilities mEnrichedCallCapabilities) {
    this.enrichedCallCapabilities = mEnrichedCallCapabilities;
  }

  @Nullable
  public Session getEnrichedCallSession() {
    return enrichedCallSession;
  }

  public void setEnrichedCallSession(@Nullable Session mEnrichedCallSession) {
    this.enrichedCallSession = mEnrichedCallSession;
  }

  public void unregisterCallback() {
    telecomCall.unregisterCallback(telecomCallCallback);
  }

  public void phoneAccountSelected(PhoneAccountHandle accountHandle, boolean setDefault) {
    LogUtil.i(
        "DialerCall.phoneAccountSelected",
        "accountHandle: %s, setDefault: %b",
        accountHandle,
        setDefault);
    telecomCall.phoneAccountSelected(accountHandle, setDefault);
  }

  public void disconnect() {
    LogUtil.i("DialerCall.disconnect", "");
    setState(DialerCallState.DISCONNECTING);
    for (DialerCallListener listener : listeners) {
      listener.onDialerCallUpdate();
    }
    telecomCall.disconnect();
  }

  public void hold() {
    LogUtil.i("DialerCall.hold", "");
    telecomCall.hold();
  }

  public void unhold() {
    LogUtil.i("DialerCall.unhold", "");
    telecomCall.unhold();
  }

  public void splitFromConference() {
    LogUtil.i("DialerCall.splitFromConference", "");
    telecomCall.splitFromConference();
  }

  public void answer(int videoState) {
    LogUtil.i("DialerCall.answer", "videoState: " + videoState);
    telecomCall.answer(videoState);
  }

  public void answer() {
    answer(telecomCall.getDetails().getVideoState());
  }

  public void reject(boolean rejectWithMessage, String message) {
    LogUtil.i("DialerCall.reject", "");
    telecomCall.reject(rejectWithMessage, message);
  }

  /** Return the string label to represent the call provider */
  public String getCallProviderLabel() {
    if (callProviderLabel == null) {
      PhoneAccount account = getPhoneAccount();
      if (account != null && !TextUtils.isEmpty(account.getLabel())) {
        if (callCapableAccounts != null && callCapableAccounts.size() > 1) {
          callProviderLabel = account.getLabel().toString();
        }
      }
      if (callProviderLabel == null) {
        callProviderLabel = "";
      }
    }
    return callProviderLabel;
  }

  private PhoneAccount getPhoneAccount() {
    PhoneAccountHandle accountHandle = getAccountHandle();
    if (accountHandle == null) {
      return null;
    }
    return context.getSystemService(TelecomManager.class).getPhoneAccount(accountHandle);
  }

  public VideoTech getVideoTech() {
    if (videoTech == null) {
      videoTech = videoTechManager.getVideoTech(getAccountHandle());

      // Only store the first video tech type found to be available during the life of the call.
      if (selectedAvailableVideoTechType == com.android.dialer.logging.VideoTech.Type.NONE) {
        // Update the video tech.
        selectedAvailableVideoTechType = videoTech.getVideoTechType();
      }
    }
    return videoTech;
  }

  public String getCallbackNumber() {
    if (callbackNumber == null) {
      // Show the emergency callback number if either:
      // 1. This is an emergency call.
      // 2. The phone is in Emergency Callback Mode, which means we should show the callback
      //    number.
      boolean showCallbackNumber = hasProperty(Details.PROPERTY_EMERGENCY_CALLBACK_MODE);

      if (isEmergencyCall() || showCallbackNumber) {
        callbackNumber =
            context.getSystemService(TelecomManager.class).getLine1Number(getAccountHandle());
      }

      if (callbackNumber == null) {
        callbackNumber = "";
      }
    }
    return callbackNumber;
  }

  public String getSimCountryIso() {
    String simCountryIso =
        TelephonyManagerCompat.getTelephonyManagerForPhoneAccountHandle(context, getAccountHandle())
            .getSimCountryIso();
    if (!TextUtils.isEmpty(simCountryIso)) {
      simCountryIso = simCountryIso.toUpperCase(Locale.US);
    }
    return simCountryIso;
  }

  @Override
  public void onVideoTechStateChanged() {
    update();
  }

  @Override
  public void onSessionModificationStateChanged() {
    Trace.beginSection("DialerCall.onSessionModificationStateChanged");
    for (DialerCallListener listener : listeners) {
      listener.onDialerCallSessionModificationStateChange();
    }
    Trace.endSection();
  }

  @Override
  public void onCameraDimensionsChanged(int width, int height) {
    InCallVideoCallCallbackNotifier.getInstance().cameraDimensionsChanged(this, width, height);
  }

  @Override
  public void onPeerDimensionsChanged(int width, int height) {
    peerDimensionWidth = width;
    peerDimensionHeight = height;
    InCallVideoCallCallbackNotifier.getInstance().peerDimensionsChanged(this, width, height);
  }

  @Override
  public void onVideoUpgradeRequestReceived() {
    LogUtil.enterBlock("DialerCall.onVideoUpgradeRequestReceived");

    for (DialerCallListener listener : listeners) {
      listener.onDialerCallUpgradeToVideo();
    }

    update();

    Logger.get(context)
        .logCallImpression(
            DialerImpression.Type.VIDEO_CALL_REQUEST_RECEIVED, getUniqueCallId(), getTimeAddedMs());
  }

  @Override
  public void onUpgradedToVideo(boolean switchToSpeaker) {
    LogUtil.enterBlock("DialerCall.onUpgradedToVideo");

    if (!switchToSpeaker) {
      return;
    }

    CallAudioState audioState = AudioModeProvider.getInstance().getAudioState();

    if (0 != (CallAudioState.ROUTE_BLUETOOTH & audioState.getSupportedRouteMask())) {
      LogUtil.e(
          "DialerCall.onUpgradedToVideo",
          "toggling speakerphone not allowed when bluetooth supported.");
      return;
    }

    if (audioState.getRoute() == CallAudioState.ROUTE_SPEAKER) {
      return;
    }

    TelecomAdapter.getInstance().setAudioRoute(CallAudioState.ROUTE_SPEAKER);
  }

  @Override
  public void onCapabilitiesUpdated() {
    if (getNumber() == null) {
      return;
    }
    EnrichedCallCapabilities capabilities =
        EnrichedCallComponent.get(context).getEnrichedCallManager().getCapabilities(getNumber());
    if (capabilities != null) {
      setEnrichedCallCapabilities(capabilities);
      update();
    }
  }

  @Override
  public void onEnrichedCallStateChanged() {
    updateEnrichedCallSession();
  }

  @Override
  public void onImpressionLoggingNeeded(DialerImpression.Type impressionType) {
    Logger.get(context).logCallImpression(impressionType, getUniqueCallId(), getTimeAddedMs());
    if (impressionType == DialerImpression.Type.LIGHTBRINGER_UPGRADE_REQUESTED) {
      if (getLogState().contactLookupResult == Type.NOT_FOUND) {
        Logger.get(context)
            .logCallImpression(
                DialerImpression.Type.LIGHTBRINGER_NON_CONTACT_UPGRADE_REQUESTED,
                getUniqueCallId(),
                getTimeAddedMs());
      }
    }
  }

  private void updateEnrichedCallSession() {
    if (getNumber() == null) {
      return;
    }
    if (getEnrichedCallSession() != null) {
      // State changes to existing sessions are currently handled by the UI components (which have
      // their own listeners). Someday instead we could remove those and just call update() here and
      // have the usual onDialerCallUpdate update the UI.
      dispatchOnEnrichedCallSessionUpdate();
      return;
    }

    EnrichedCallManager manager = EnrichedCallComponent.get(context).getEnrichedCallManager();

    Filter filter =
        isIncoming()
            ? manager.createIncomingCallComposerFilter()
            : manager.createOutgoingCallComposerFilter();

    Session session = manager.getSession(getUniqueCallId(), getNumber(), filter);
    if (session == null) {
      return;
    }

    session.setUniqueDialerCallId(getUniqueCallId());
    setEnrichedCallSession(session);

    LogUtil.i(
        "DialerCall.updateEnrichedCallSession",
        "setting session %d's dialer id to %s",
        session.getSessionId(),
        getUniqueCallId());

    dispatchOnEnrichedCallSessionUpdate();
  }

  private void dispatchOnEnrichedCallSessionUpdate() {
    for (DialerCallListener listener : listeners) {
      listener.onEnrichedCallSessionUpdate();
    }
  }

  void onRemovedFromCallList() {
    LogUtil.enterBlock("DialerCall.onRemovedFromCallList");
    // Ensure we clean up when this call is removed.
    if (videoTechManager != null) {
      videoTechManager.dispatchRemovedFromCallList();
    }
    // TODO(wangqi): Consider moving this to a DialerCallListener.
    if (rttTranscript != null && !isCallRemoved) {
      saveRttTranscript();
    }
    isCallRemoved = true;
  }

  public com.android.dialer.logging.VideoTech.Type getSelectedAvailableVideoTechType() {
    return selectedAvailableVideoTechType;
  }

  public void markFeedbackRequested() {
    feedbackRequested = true;
  }

  public boolean isFeedbackRequested() {
    return feedbackRequested;
  }

  /**
   * If the in call UI has shown the phone account selection dialog for the call, the {@link
   * PreferredAccountRecorder} to record the result from the dialog.
   */
  @Nullable
  public PreferredAccountRecorder getPreferredAccountRecorder() {
    return preferredAccountRecorder;
  }

  public void setPreferredAccountRecorder(PreferredAccountRecorder preferredAccountRecorder) {
    this.preferredAccountRecorder = preferredAccountRecorder;
  }

  /** Indicates the call is eligible for SpeakEasy */
  public boolean isSpeakEasyEligible() {

    PhoneAccount phoneAccount = getPhoneAccount();

    if (phoneAccount == null) {
      return false;
    }

    if (!phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
      return false;
    }

    return !isPotentialEmergencyCallback()
        && !isEmergencyCall()
        && !isActiveRttCall()
        && !isConferenceCall()
        && !isVideoCall()
        && !isVoiceMailNumber()
        && !hasReceivedVideoUpgradeRequest()
        && !isVoipCallNotSupportedBySpeakeasy();
  }

  private boolean isVoipCallNotSupportedBySpeakeasy() {
    Bundle extras = getIntentExtras();

    if (extras == null) {
      return false;
    }

    // Indicates an VOIP call.
    String callid = extras.getString("callid");

    if (TextUtils.isEmpty(callid)) {
      LogUtil.i("DialerCall.isVoipCallNotSupportedBySpeakeasy", "callid was empty");
      return false;
    }

    LogUtil.i("DialerCall.isVoipCallNotSupportedBySpeakeasy", "call is not eligible");
    return true;
  }

  /** Indicates the user has selected SpeakEasy */
  public boolean isSpeakEasyCall() {
    if (!isSpeakEasyEligible()) {
      return false;
    }
    return isSpeakEasyCall;
  }

  /** Sets the user preference for SpeakEasy */
  public void setIsSpeakEasyCall(boolean isSpeakEasyCall) {
    this.isSpeakEasyCall = isSpeakEasyCall;
    if (listeners != null) {
      for (DialerCallListener listener : listeners) {
        listener.onDialerCallSpeakEasyStateChange();
      }
    }
  }

  /**
   * Specifies whether a number is in the call history or not. {@link #CALL_HISTORY_STATUS_UNKNOWN}
   * means there is no result.
   */
  @IntDef({
    CALL_HISTORY_STATUS_UNKNOWN,
    CALL_HISTORY_STATUS_PRESENT,
    CALL_HISTORY_STATUS_NOT_PRESENT
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface CallHistoryStatus {}

  /** Camera direction constants */
  public static class CameraDirection {
    public static final int CAMERA_DIRECTION_UNKNOWN = -1;
    public static final int CAMERA_DIRECTION_FRONT_FACING = CameraCharacteristics.LENS_FACING_FRONT;
    public static final int CAMERA_DIRECTION_BACK_FACING = CameraCharacteristics.LENS_FACING_BACK;
  }

  /**
   * Tracks any state variables that is useful for logging. There is some amount of overlap with
   * existing call member variables, but this duplication helps to ensure that none of these logging
   * variables will interface with/and affect call logic.
   */
  public static class LogState {

    public DisconnectCause disconnectCause;
    public boolean isIncoming = false;
    public ContactLookupResult.Type contactLookupResult =
        ContactLookupResult.Type.UNKNOWN_LOOKUP_RESULT_TYPE;
    public CallSpecificAppData callSpecificAppData;
    // If this was a conference call, the total number of calls involved in the conference.
    public int conferencedCalls = 0;
    public boolean isLogged = false;

    // Result of subtracting android.telecom.Call.Details#getConnectTimeMillis from the current time
    public long telecomDurationMillis = 0;

    // Result of a call to System.currentTimeMillis when Dialer sees that a call
    // moves to the ACTIVE state
    long dialerConnectTimeMillis = 0;

    // Same as dialer_connect_time_millis, using SystemClock.elapsedRealtime
    // instead
    long dialerConnectTimeMillisElapsedRealtime = 0;

    // Result of subtracting dialer_connect_time_millis from System.currentTimeMillis
    public long dialerDurationMillis = 0;

    // Same as dialerDurationMillis, using SystemClock.elapsedRealtime instead
    public long dialerDurationMillisElapsedRealtime = 0;

    private static String lookupToString(ContactLookupResult.Type lookupType) {
      switch (lookupType) {
        case LOCAL_CONTACT:
          return "Local";
        case LOCAL_CACHE:
          return "Cache";
        case REMOTE:
          return "Remote";
        case EMERGENCY:
          return "Emergency";
        case VOICEMAIL:
          return "Voicemail";
        default:
          return "Not found";
      }
    }

    private static String initiationToString(CallSpecificAppData callSpecificAppData) {
      if (callSpecificAppData == null) {
        return "null";
      }
      switch (callSpecificAppData.getCallInitiationType()) {
        case INCOMING_INITIATION:
          return "Incoming";
        case DIALPAD:
          return "Dialpad";
        case SPEED_DIAL:
          return "Speed Dial";
        case REMOTE_DIRECTORY:
          return "Remote Directory";
        case SMART_DIAL:
          return "Smart Dial";
        case REGULAR_SEARCH:
          return "Regular Search";
        case CALL_LOG:
          return "DialerCall Log";
        case CALL_LOG_FILTER:
          return "DialerCall Log Filter";
        case VOICEMAIL_LOG:
          return "Voicemail Log";
        case CALL_DETAILS:
          return "DialerCall Details";
        case QUICK_CONTACTS:
          return "Quick Contacts";
        case EXTERNAL_INITIATION:
          return "External";
        case LAUNCHER_SHORTCUT:
          return "Launcher Shortcut";
        default:
          return "Unknown: " + callSpecificAppData.getCallInitiationType();
      }
    }

    @Override
    public String toString() {
      return String.format(
          Locale.US,
          "["
              + "%s, " // DisconnectCause toString already describes the object type
              + "isIncoming: %s, "
              + "contactLookup: %s, "
              + "callInitiation: %s, "
              + "duration: %s"
              + "]",
          disconnectCause,
          isIncoming,
          lookupToString(contactLookupResult),
          initiationToString(callSpecificAppData),
          telecomDurationMillis);
    }
  }

  /** Coordinates the available VideoTech implementations for a call. */
  @VisibleForTesting
  public static class VideoTechManager {
    private final Context context;
    private final EmptyVideoTech emptyVideoTech = new EmptyVideoTech();
    private final VideoTech rcsVideoShare;
    private final List<VideoTech> videoTechs;
    private VideoTech savedTech;

    @VisibleForTesting
    public VideoTechManager(DialerCall call) {
      this.context = call.context;

      String phoneNumber = call.getNumber();
      phoneNumber = phoneNumber != null ? phoneNumber : "";
      phoneNumber = phoneNumber.replaceAll("[^+0-9]", "");

      // Insert order here determines the priority of that video tech option
      videoTechs = new ArrayList<>();

      videoTechs.add(new ImsVideoTech(Logger.get(call.context), call, call.telecomCall));

      rcsVideoShare =
          EnrichedCallComponent.get(call.context)
              .getRcsVideoShareFactory()
              .newRcsVideoShare(
                  EnrichedCallComponent.get(call.context).getEnrichedCallManager(),
                  call,
                  phoneNumber);
      videoTechs.add(rcsVideoShare);

      videoTechs.add(
          new DuoVideoTech(
              DuoComponent.get(call.context).getDuo(), call, call.telecomCall, phoneNumber));

      savedTech = emptyVideoTech;
    }

    @VisibleForTesting
    public VideoTech getVideoTech(PhoneAccountHandle phoneAccountHandle) {
      if (savedTech == emptyVideoTech) {
        for (VideoTech tech : videoTechs) {
          if (tech.isAvailable(context, phoneAccountHandle)) {
            savedTech = tech;
            savedTech.becomePrimary();
            break;
          }
        }
      } else if (savedTech instanceof DuoVideoTech
          && rcsVideoShare.isAvailable(context, phoneAccountHandle)) {
        // RCS Video Share will become available after the capability exchange which is slower than
        // Duo reading local contacts for reachability. If Video Share becomes available and we are
        // not in the middle of any session changes, let it take over.
        savedTech = rcsVideoShare;
        rcsVideoShare.becomePrimary();
      }

      return savedTech;
    }

    @VisibleForTesting
    public void dispatchCallStateChanged(int newState, PhoneAccountHandle phoneAccountHandle) {
      for (VideoTech videoTech : videoTechs) {
        videoTech.onCallStateChanged(context, newState, phoneAccountHandle);
      }
    }

    void dispatchRemovedFromCallList() {
      for (VideoTech videoTech : videoTechs) {
        videoTech.onRemovedFromCallList();
      }
    }
  }

  /** Called when canned text responses have been loaded. */
  public interface CannedTextResponsesLoadedListener {
    void onCannedTextResponsesLoaded(DialerCall call);
  }

  /** Gets peer dimension width. */
  public int getPeerDimensionWidth() {
    return peerDimensionWidth;
  }

  /** Gets peer dimension height. */
  public int getPeerDimensionHeight() {
    return peerDimensionHeight;
  }
}
