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

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Trace;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telecom.Call;
import android.telecom.Call.Details;
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
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import com.android.contacts.common.compat.CallCompat;
import com.android.contacts.common.compat.TelephonyManagerCompat;
import com.android.contacts.common.compat.telecom.TelecomManagerCompat;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentParser;
import com.android.dialer.callintent.CallSpecificAppData;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.enrichedcall.EnrichedCallCapabilities;
import com.android.dialer.enrichedcall.EnrichedCallComponent;
import com.android.dialer.enrichedcall.EnrichedCallManager;
import com.android.dialer.enrichedcall.EnrichedCallManager.CapabilitiesListener;
import com.android.dialer.enrichedcall.EnrichedCallManager.Filter;
import com.android.dialer.enrichedcall.EnrichedCallManager.StateChangedListener;
import com.android.dialer.enrichedcall.Session;
import com.android.dialer.lightbringer.LightbringerComponent;
import com.android.dialer.logging.ContactLookupResult;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.theme.R;
import com.android.incallui.audiomode.AudioModeProvider;
import com.android.incallui.latencyreport.LatencyReport;
import com.android.incallui.util.TelecomCallUtil;
import com.android.incallui.videotech.VideoTech;
import com.android.incallui.videotech.VideoTech.VideoTechListener;
import com.android.incallui.videotech.empty.EmptyVideoTech;
import com.android.incallui.videotech.ims.ImsVideoTech;
import com.android.incallui.videotech.lightbringer.LightbringerTech;
import com.android.incallui.videotech.utils.VideoUtils;
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
  // TODO(b/35359461): Move it to Telecom in framework.
  public static final int PROPERTY_CODEC_KNOWN = 0x04000000;

  private static final String ID_PREFIX = "DialerCall_";
  private static final String CONFIG_EMERGENCY_CALLBACK_WINDOW_MILLIS =
      "emergency_callback_window_millis";
  private static int sIdCounter = 0;

  /**
   * A counter used to append to restricted/private/hidden calls so that users can identify them in
   * a conversation. This value is reset in {@link CallList#onCallRemoved(Context, Call)} when there
   * are no live calls.
   */
  private static int sHiddenCounter;

  /**
   * The unique call ID for every call. This will help us to identify each call and allow us the
   * ability to stitch impressions to calls if needed.
   */
  private final String uniqueCallId = UUID.randomUUID().toString();

  private final Call mTelecomCall;
  private final LatencyReport mLatencyReport;
  private final String mId;
  private final int mHiddenId;
  private final List<String> mChildCallIds = new ArrayList<>();
  private final LogState mLogState = new LogState();
  private final Context mContext;
  private final DialerCallDelegate mDialerCallDelegate;
  private final List<DialerCallListener> mListeners = new CopyOnWriteArrayList<>();
  private final List<CannedTextResponsesLoadedListener> mCannedTextResponsesLoadedListeners =
      new CopyOnWriteArrayList<>();
  private final VideoTechManager mVideoTechManager;

  private boolean mIsEmergencyCall;
  private Uri mHandle;
  private int mState = State.INVALID;
  private DisconnectCause mDisconnectCause;

  private boolean hasShownWiFiToLteHandoverToast;
  private boolean doNotShowDialogForHandoffToWifiFailure;

  private String mChildNumber;
  private String mLastForwardedNumber;
  private String mCallSubject;
  private PhoneAccountHandle mPhoneAccountHandle;
  @CallHistoryStatus private int mCallHistoryStatus = CALL_HISTORY_STATUS_UNKNOWN;
  private boolean mIsOutgoing = false;
  private boolean mIsSpam;
  private boolean mIsBlocked;
  private boolean isInUserSpamList;
  private boolean isInUserWhiteList;
  private boolean isInGlobalSpamList;
  private boolean didShowCameraPermission;
  private String callProviderLabel;
  private String callbackNumber;
  private int mCameraDirection = CameraDirection.CAMERA_DIRECTION_UNKNOWN;
  private EnrichedCallCapabilities mEnrichedCallCapabilities;
  private Session mEnrichedCallSession;

  private int answerAndReleaseButtonDisplayedTimes = 0;
  private boolean releasedByAnsweringSecondCall = false;
  // Times when a second call is received but AnswerAndRelease button is not shown
  // since it's not supported.
  private int secondCallWithoutAnswerAndReleasedButtonTimes = 0;

  public static String getNumberFromHandle(Uri handle) {
    return handle == null ? "" : handle.getSchemeSpecificPart();
  }

  /**
   * Whether the call is put on hold by remote party. This is different than the {@link
   * State#ONHOLD} state which indicates that the call is being held locally on the device.
   */
  private boolean isRemotelyHeld;

  /** Indicates whether this call is currently in the process of being merged into a conference. */
  private boolean isMergeInProcess;

  /**
   * Indicates whether the phone account associated with this call supports specifying a call
   * subject.
   */
  private boolean mIsCallSubjectSupported;

  private final Call.Callback mTelecomCallCallback =
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
          LogUtil.v("TelecomCallCallback.onStateChanged", " call=" + call + " details=" + details);
          update();
        }

        @Override
        public void onCannedTextResponsesLoaded(Call call, List<String> cannedTextResponses) {
          LogUtil.v(
              "TelecomCallCallback.onStateChanged",
              "call=" + call + " cannedTextResponses=" + cannedTextResponses);
          for (CannedTextResponsesLoadedListener listener : mCannedTextResponsesLoadedListeners) {
            listener.onCannedTextResponsesLoaded(DialerCall.this);
          }
        }

        @Override
        public void onPostDialWait(Call call, String remainingPostDialSequence) {
          LogUtil.v(
              "TelecomCallCallback.onStateChanged",
              "call=" + call + " remainingPostDialSequence=" + remainingPostDialSequence);
          update();
        }

        @Override
        public void onVideoCallChanged(Call call, VideoCall videoCall) {
          LogUtil.v(
              "TelecomCallCallback.onStateChanged", "call=" + call + " videoCall=" + videoCall);
          update();
        }

        @Override
        public void onCallDestroyed(Call call) {
          LogUtil.v("TelecomCallCallback.onStateChanged", "call=" + call);
          unregisterCallback();
        }

        @Override
        public void onConferenceableCallsChanged(Call call, List<Call> conferenceableCalls) {
          LogUtil.v(
              "DialerCall.onConferenceableCallsChanged",
              "call %s, conferenceable calls: %d",
              call,
              conferenceableCalls.size());
          update();
        }

        @Override
        public void onConnectionEvent(android.telecom.Call call, String event, Bundle extras) {
          LogUtil.v(
              "DialerCall.onConnectionEvent",
              "Call: " + call + ", Event: " + event + ", Extras: " + extras);
          switch (event) {
              // The Previous attempt to Merge two calls together has failed in Telecom. We must
              // now update the UI to possibly re-enable the Merge button based on the number of
              // currently conferenceable calls available or Connection Capabilities.
            case android.telecom.Connection.EVENT_CALL_MERGE_FAILED:
              update();
              break;
            case TelephonyManagerCompat.EVENT_HANDOVER_VIDEO_FROM_WIFI_TO_LTE:
              notifyWiFiToLteHandover();
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
            default:
              break;
          }
        }
      };

  private long mTimeAddedMs;

  public DialerCall(
      Context context,
      DialerCallDelegate dialerCallDelegate,
      Call telecomCall,
      LatencyReport latencyReport,
      boolean registerCallback) {
    Assert.isNotNull(context);
    mContext = context;
    mDialerCallDelegate = dialerCallDelegate;
    mTelecomCall = telecomCall;
    mLatencyReport = latencyReport;
    mId = ID_PREFIX + Integer.toString(sIdCounter++);

    // Must be after assigning mTelecomCall
    mVideoTechManager = new VideoTechManager(this);

    updateFromTelecomCall();
    if (isHiddenNumber() && TextUtils.isEmpty(getNumber())) {
      mHiddenId = ++sHiddenCounter;
    } else {
      mHiddenId = 0;
    }

    if (registerCallback) {
      mTelecomCall.registerCallback(mTelecomCallCallback);
    }

    mTimeAddedMs = System.currentTimeMillis();
    parseCallSpecificAppData();

    updateEnrichedCallSession();
  }

  private static int translateState(int state) {
    switch (state) {
      case Call.STATE_NEW:
      case Call.STATE_CONNECTING:
        return DialerCall.State.CONNECTING;
      case Call.STATE_SELECT_PHONE_ACCOUNT:
        return DialerCall.State.SELECT_PHONE_ACCOUNT;
      case Call.STATE_DIALING:
        return DialerCall.State.DIALING;
      case Call.STATE_PULLING_CALL:
        return DialerCall.State.PULLING;
      case Call.STATE_RINGING:
        return DialerCall.State.INCOMING;
      case Call.STATE_ACTIVE:
        return DialerCall.State.ACTIVE;
      case Call.STATE_HOLDING:
        return DialerCall.State.ONHOLD;
      case Call.STATE_DISCONNECTED:
        return DialerCall.State.DISCONNECTED;
      case Call.STATE_DISCONNECTING:
        return DialerCall.State.DISCONNECTING;
      default:
        return DialerCall.State.INVALID;
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

  public static boolean areSameNumber(DialerCall call1, DialerCall call2) {
    if (call1 == null && call2 == null) {
      return true;
    } else if (call1 == null || call2 == null) {
      return false;
    }

    // otherwise compare call Numbers
    return TextUtils.equals(call1.getNumber(), call2.getNumber());
  }

  public void addListener(DialerCallListener listener) {
    Assert.isMainThread();
    mListeners.add(listener);
  }

  public void removeListener(DialerCallListener listener) {
    Assert.isMainThread();
    mListeners.remove(listener);
  }

  public void addCannedTextResponsesLoadedListener(CannedTextResponsesLoadedListener listener) {
    Assert.isMainThread();
    mCannedTextResponsesLoadedListeners.add(listener);
  }

  public void removeCannedTextResponsesLoadedListener(CannedTextResponsesLoadedListener listener) {
    Assert.isMainThread();
    mCannedTextResponsesLoadedListeners.remove(listener);
  }

  public void notifyWiFiToLteHandover() {
    LogUtil.i("DialerCall.notifyWiFiToLteHandover", "");
    for (DialerCallListener listener : mListeners) {
      listener.onWiFiToLteHandover();
    }
  }

  public void notifyHandoverToWifiFailed() {
    LogUtil.i("DialerCall.notifyHandoverToWifiFailed", "");
    for (DialerCallListener listener : mListeners) {
      listener.onHandoverToWifiFailure();
    }
  }

  public void notifyInternationalCallOnWifi() {
    LogUtil.enterBlock("DialerCall.notifyInternationalCallOnWifi");
    for (DialerCallListener dialerCallListener : mListeners) {
      dialerCallListener.onInternationalCallOnWifi();
    }
  }

  /* package-private */ Call getTelecomCall() {
    return mTelecomCall;
  }

  public StatusHints getStatusHints() {
    return mTelecomCall.getDetails().getStatusHints();
  }

  public int getCameraDir() {
    return mCameraDirection;
  }

  public void setCameraDir(int cameraDir) {
    if (cameraDir == CameraDirection.CAMERA_DIRECTION_FRONT_FACING
        || cameraDir == CameraDirection.CAMERA_DIRECTION_BACK_FACING) {
      mCameraDirection = cameraDir;
    } else {
      mCameraDirection = CameraDirection.CAMERA_DIRECTION_UNKNOWN;
    }
  }

  private void update() {
    Trace.beginSection("Update");
    int oldState = getState();
    // We want to potentially register a video call callback here.
    updateFromTelecomCall();
    if (oldState != getState() && getState() == DialerCall.State.DISCONNECTED) {
      for (DialerCallListener listener : mListeners) {
        listener.onDialerCallDisconnect();
      }
      EnrichedCallComponent.get(mContext)
          .getEnrichedCallManager()
          .unregisterCapabilitiesListener(this);
      EnrichedCallComponent.get(mContext)
          .getEnrichedCallManager()
          .unregisterCapabilitiesListener(this);
    } else {
      for (DialerCallListener listener : mListeners) {
        listener.onDialerCallUpdate();
      }
    }
    Trace.endSection();
  }

  private void updateFromTelecomCall() {
    LogUtil.v("DialerCall.updateFromTelecomCall", mTelecomCall.toString());

    mVideoTechManager.dispatchCallStateChanged(mTelecomCall.getState());

    final int translatedState = translateState(mTelecomCall.getState());
    if (mState != State.BLOCKED) {
      setState(translatedState);
      setDisconnectCause(mTelecomCall.getDetails().getDisconnectCause());
    }

    mChildCallIds.clear();
    final int numChildCalls = mTelecomCall.getChildren().size();
    for (int i = 0; i < numChildCalls; i++) {
      mChildCallIds.add(
          mDialerCallDelegate
              .getDialerCallFromTelecomCall(mTelecomCall.getChildren().get(i))
              .getId());
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
        PhoneAccount phoneAccount =
            mContext.getSystemService(TelecomManager.class).getPhoneAccount(mPhoneAccountHandle);
        if (phoneAccount != null) {
          mIsCallSubjectSupported =
              phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_CALL_SUBJECT);
        }
      }
    }
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
     * There's currently a bug in Telephony service (b/25613098) that could corrupt the extras
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
      if (!Objects.equals(childNumber, mChildNumber)) {
        mChildNumber = childNumber;
        for (DialerCallListener listener : mListeners) {
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

        if (!Objects.equals(lastForwardedNumber, mLastForwardedNumber)) {
          mLastForwardedNumber = lastForwardedNumber;
          for (DialerCallListener listener : mListeners) {
            listener.onDialerCallLastForwardedNumberChange();
          }
        }
      }
    }

    // DialerCall subject is present in the extras at the start of call, so we do not need to
    // notify any other listeners of this.
    if (callExtras.containsKey(Connection.EXTRA_CALL_SUBJECT)) {
      String callSubject = callExtras.getString(Connection.EXTRA_CALL_SUBJECT);
      if (!Objects.equals(mCallSubject, callSubject)) {
        mCallSubject = callSubject;
      }
    }
  }

  public String getId() {
    return mId;
  }

  /**
   * @return name appended with a number if the number is restricted/unknown and the user has
   *     received more than one restricted/unknown call.
   */
  @Nullable
  public String updateNameIfRestricted(@Nullable String name) {
    if (name != null && isHiddenNumber() && mHiddenId != 0 && sHiddenCounter > 1) {
      return mContext.getString(R.string.unknown_counter, name, mHiddenId);
    }
    return name;
  }

  public static void clearRestrictedCount() {
    sHiddenCounter = 0;
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

  public long getTimeAddedMs() {
    return mTimeAddedMs;
  }

  @Nullable
  public String getNumber() {
    return TelecomCallUtil.getNumber(mTelecomCall);
  }

  public void blockCall() {
    mTelecomCall.reject(false, null);
    setState(State.BLOCKED);
  }

  @Nullable
  public Uri getHandle() {
    return mTelecomCall == null ? null : mTelecomCall.getDetails().getHandle();
  }

  public boolean isEmergencyCall() {
    return mIsEmergencyCall;
  }

  public boolean isPotentialEmergencyCallback() {
    // The property PROPERTY_EMERGENCY_CALLBACK_MODE is only set for CDMA calls when the system
    // is actually in emergency callback mode (ie data is disabled).
    if (hasProperty(Details.PROPERTY_EMERGENCY_CALLBACK_MODE)) {
      return true;
    }
    // We want to treat any incoming call that arrives a short time after an outgoing emergency call
    // as a potential emergency callback.
    if (getExtras() != null
        && getExtras().getLong(TelecomManagerCompat.EXTRA_LAST_EMERGENCY_CALLBACK_TIME_MILLIS, 0)
            > 0) {
      long lastEmergencyCallMillis =
          getExtras().getLong(TelecomManagerCompat.EXTRA_LAST_EMERGENCY_CALLBACK_TIME_MILLIS, 0);
      if (isInEmergencyCallbackWindow(lastEmergencyCallMillis)) {
        return true;
      }
    }
    return false;
  }

  boolean isInEmergencyCallbackWindow(long timestampMillis) {
    long emergencyCallbackWindowMillis =
        ConfigProviderBindings.get(mContext)
            .getLong(CONFIG_EMERGENCY_CALLBACK_WINDOW_MILLIS, TimeUnit.MINUTES.toMillis(5));
    return System.currentTimeMillis() - timestampMillis < emergencyCallbackWindowMillis;
  }

  public int getState() {
    if (mTelecomCall != null && mTelecomCall.getParent() != null) {
      return State.CONFERENCED;
    } else {
      return mState;
    }
  }

  public void setState(int state) {
    mState = state;
    if (mState == State.INCOMING) {
      mLogState.isIncoming = true;
    } else if (mState == State.DISCONNECTED) {
      mLogState.duration =
          getConnectTimeMillis() == 0 ? 0 : System.currentTimeMillis() - getConnectTimeMillis();
    } else if (mState == State.DIALING || mState == State.CONNECTING) {
      mIsOutgoing = true;
    }
  }

  public boolean isOutgoing() {
    return mIsOutgoing;
  }

  public int getNumberPresentation() {
    return mTelecomCall == null ? -1 : mTelecomCall.getDetails().getHandlePresentation();
  }

  public int getCnapNamePresentation() {
    return mTelecomCall == null ? -1 : mTelecomCall.getDetails().getCallerDisplayNamePresentation();
  }

  @Nullable
  public String getCnapName() {
    return mTelecomCall == null ? null : getTelecomCall().getDetails().getCallerDisplayName();
  }

  public Bundle getIntentExtras() {
    return mTelecomCall.getDetails().getIntentExtras();
  }

  @Nullable
  public Bundle getExtras() {
    return mTelecomCall == null ? null : mTelecomCall.getDetails().getExtras();
  }

  /** @return The child number for the call, or {@code null} if none specified. */
  public String getChildNumber() {
    return mChildNumber;
  }

  /** @return The last forwarded number for the call, or {@code null} if none specified. */
  public String getLastForwardedNumber() {
    return mLastForwardedNumber;
  }

  /** @return The call subject, or {@code null} if none specified. */
  public String getCallSubject() {
    return mCallSubject;
  }

  /**
   * @return {@code true} if the call's phone account supports call subjects, {@code false}
   *     otherwise.
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

    if ((capabilities & Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0) {
      // We allow you to merge if the capabilities allow it or if it is a call with
      // conferenceable calls.
      if (mTelecomCall.getConferenceableCalls().isEmpty()
          && ((Call.Details.CAPABILITY_MERGE_CONFERENCE & supportedCapabilities) == 0)) {
        // Cannot merge calls if there are no calls to merge with.
        return false;
      }
      capabilities &= ~Call.Details.CAPABILITY_MERGE_CONFERENCE;
    }
    return (capabilities == (capabilities & supportedCapabilities));
  }

  public boolean hasProperty(int property) {
    return mTelecomCall.getDetails().hasProperty(property);
  }

  @NonNull
  public String getUniqueCallId() {
    return uniqueCallId;
  }

  /** Gets the time when the call first became active. */
  public long getConnectTimeMillis() {
    return mTelecomCall.getDetails().getConnectTimeMillis();
  }

  /** Gets the time when call was first constructed */
  public long getCreationTimeMillis() {
    return mTelecomCall.getDetails().getCreationTimeMillis();
  }

  public boolean isConferenceCall() {
    return hasProperty(Call.Details.PROPERTY_CONFERENCE);
  }

  @Nullable
  public GatewayInfo getGatewayInfo() {
    return mTelecomCall == null ? null : mTelecomCall.getDetails().getGatewayInfo();
  }

  @Nullable
  public PhoneAccountHandle getAccountHandle() {
    return mTelecomCall == null ? null : mTelecomCall.getDetails().getAccountHandle();
  }

  /** @return The {@link VideoCall} instance associated with the {@link Call}. */
  public VideoCall getVideoCall() {
    return mTelecomCall == null ? null : mTelecomCall.getVideoCall();
  }

  public List<String> getChildCallIds() {
    return mChildCallIds;
  }

  public String getParentId() {
    Call parentCall = mTelecomCall.getParent();
    if (parentCall != null) {
      return mDialerCallDelegate.getDialerCallFromTelecomCall(parentCall).getId();
    }
    return null;
  }

  public int getVideoState() {
    return mTelecomCall.getDetails().getVideoState();
  }

  public boolean isVideoCall() {
    return getVideoTech().isTransmittingOrReceiving();
  }

  public boolean hasReceivedVideoUpgradeRequest() {
    return VideoUtils.hasReceivedVideoUpgradeRequest(getVideoTech().getSessionModificationState());
  }

  public boolean hasSentVideoUpgradeRequest() {
    return VideoUtils.hasSentVideoUpgradeRequest(getVideoTech().getSessionModificationState());
  }

  /**
   * Determines if the call handle is an emergency number or not and caches the result to avoid
   * repeated calls to isEmergencyNumber.
   */
  private void updateEmergencyCallState() {
    mIsEmergencyCall = TelecomCallUtil.isEmergencyCall(mTelecomCall);
  }

  public LogState getLogState() {
    return mLogState;
  }

  /**
   * Determines if the call is an external call.
   *
   * <p>An external call is one which does not exist locally for the {@link
   * android.telecom.ConnectionService} it is associated with.
   *
   * <p>External calls are only supported in N and higher.
   *
   * @return {@code true} if the call is an external call, {@code false} otherwise.
   */
  public boolean isExternalCall() {
    return VERSION.SDK_INT >= VERSION_CODES.N
        && hasProperty(CallCompat.Details.PROPERTY_IS_EXTERNAL_CALL);
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

    mLogState.callSpecificAppData = CallIntentParser.getCallSpecificAppData(getIntentExtras());
    if (mLogState.callSpecificAppData == null) {

      mLogState.callSpecificAppData =
          CallSpecificAppData.newBuilder()
              .setCallInitiationType(CallInitiationType.Type.EXTERNAL_INITIATION)
              .build();
    }
    if (getState() == State.INCOMING) {
      mLogState.callSpecificAppData =
          mLogState
              .callSpecificAppData
              .toBuilder()
              .setCallInitiationType(CallInitiationType.Type.INCOMING_INITIATION)
              .build();
    }
  }

  @Override
  public String toString() {
    if (mTelecomCall == null) {
      // This should happen only in testing since otherwise we would never have a null
      // Telecom call.
      return String.valueOf(mId);
    }

    return String.format(
        Locale.US,
        "[%s, %s, %s, %s, children:%s, parent:%s, "
            + "conferenceable:%s, videoState:%s, mSessionModificationState:%d, CameraDir:%s]",
        mId,
        State.toString(getState()),
        Details.capabilitiesToString(mTelecomCall.getDetails().getCallCapabilities()),
        Details.propertiesToString(mTelecomCall.getDetails().getCallProperties()),
        mChildCallIds,
        getParentId(),
        this.mTelecomCall.getConferenceableCalls(),
        VideoProfile.videoStateToString(mTelecomCall.getDetails().getVideoState()),
        getVideoTech().getSessionModificationState(),
        getCameraDir());
  }

  public String toSimpleString() {
    return super.toString();
  }

  @CallHistoryStatus
  public int getCallHistoryStatus() {
    return mCallHistoryStatus;
  }

  public void setCallHistoryStatus(@CallHistoryStatus int callHistoryStatus) {
    mCallHistoryStatus = callHistoryStatus;
  }

  public boolean didShowCameraPermission() {
    return didShowCameraPermission;
  }

  public void setDidShowCameraPermission(boolean didShow) {
    didShowCameraPermission = didShow;
  }

  public boolean isInGlobalSpamList() {
    return isInGlobalSpamList;
  }

  public void setIsInGlobalSpamList(boolean inSpamList) {
    isInGlobalSpamList = inSpamList;
  }

  public boolean isInUserSpamList() {
    return isInUserSpamList;
  }

  public void setIsInUserSpamList(boolean inSpamList) {
    isInUserSpamList = inSpamList;
  }

  public boolean isInUserWhiteList() {
    return isInUserWhiteList;
  }

  public void setIsInUserWhiteList(boolean inWhiteList) {
    isInUserWhiteList = inWhiteList;
  }

  public boolean isSpam() {
    return mIsSpam;
  }

  public void setSpam(boolean isSpam) {
    mIsSpam = isSpam;
  }

  public boolean isBlocked() {
    return mIsBlocked;
  }

  public void setBlockedStatus(boolean isBlocked) {
    mIsBlocked = isBlocked;
  }

  public boolean isRemotelyHeld() {
    return isRemotelyHeld;
  }

  public boolean isMergeInProcess() {
    return isMergeInProcess;
  }

  public boolean isIncoming() {
    return mLogState.isIncoming;
  }

  public LatencyReport getLatencyReport() {
    return mLatencyReport;
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
    return mEnrichedCallCapabilities;
  }

  public void setEnrichedCallCapabilities(
      @Nullable EnrichedCallCapabilities mEnrichedCallCapabilities) {
    this.mEnrichedCallCapabilities = mEnrichedCallCapabilities;
  }

  @Nullable
  public Session getEnrichedCallSession() {
    return mEnrichedCallSession;
  }

  public void setEnrichedCallSession(@Nullable Session mEnrichedCallSession) {
    this.mEnrichedCallSession = mEnrichedCallSession;
  }

  public void unregisterCallback() {
    mTelecomCall.unregisterCallback(mTelecomCallCallback);
  }

  public void phoneAccountSelected(PhoneAccountHandle accountHandle, boolean setDefault) {
    LogUtil.i(
        "DialerCall.phoneAccountSelected",
        "accountHandle: %s, setDefault: %b",
        accountHandle,
        setDefault);
    mTelecomCall.phoneAccountSelected(accountHandle, setDefault);
  }

  public void disconnect() {
    LogUtil.i("DialerCall.disconnect", "");
    setState(DialerCall.State.DISCONNECTING);
    for (DialerCallListener listener : mListeners) {
      listener.onDialerCallUpdate();
    }
    mTelecomCall.disconnect();
  }

  public void hold() {
    LogUtil.i("DialerCall.hold", "");
    mTelecomCall.hold();
  }

  public void unhold() {
    LogUtil.i("DialerCall.unhold", "");
    mTelecomCall.unhold();
  }

  public void splitFromConference() {
    LogUtil.i("DialerCall.splitFromConference", "");
    mTelecomCall.splitFromConference();
  }

  public void answer(int videoState) {
    LogUtil.i("DialerCall.answer", "videoState: " + videoState);
    mTelecomCall.answer(videoState);
  }

  public void answer() {
    answer(mTelecomCall.getDetails().getVideoState());
  }

  public void reject(boolean rejectWithMessage, String message) {
    LogUtil.i("DialerCall.reject", "");
    mTelecomCall.reject(rejectWithMessage, message);
  }

  /** Return the string label to represent the call provider */
  public String getCallProviderLabel() {
    if (callProviderLabel == null) {
      PhoneAccount account = getPhoneAccount();
      if (account != null && !TextUtils.isEmpty(account.getLabel())) {
        List<PhoneAccountHandle> accounts =
            mContext.getSystemService(TelecomManager.class).getCallCapablePhoneAccounts();
        if (accounts != null && accounts.size() > 1) {
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
    return mContext.getSystemService(TelecomManager.class).getPhoneAccount(accountHandle);
  }

  public VideoTech getVideoTech() {
    return mVideoTechManager.getVideoTech();
  }

  public String getCallbackNumber() {
    if (callbackNumber == null) {
      // Show the emergency callback number if either:
      // 1. This is an emergency call.
      // 2. The phone is in Emergency Callback Mode, which means we should show the callback
      //    number.
      boolean showCallbackNumber = hasProperty(Details.PROPERTY_EMERGENCY_CALLBACK_MODE);

      if (isEmergencyCall() || showCallbackNumber) {
        callbackNumber = getSubscriptionNumber();
      } else {
        StatusHints statusHints = getTelecomCall().getDetails().getStatusHints();
        if (statusHints != null) {
          Bundle extras = statusHints.getExtras();
          if (extras != null) {
            callbackNumber = extras.getString(TelecomManager.EXTRA_CALL_BACK_NUMBER);
          }
        }
      }

      String simNumber =
          mContext.getSystemService(TelecomManager.class).getLine1Number(getAccountHandle());
      if (!showCallbackNumber && PhoneNumberUtils.compare(callbackNumber, simNumber)) {
        LogUtil.v(
            "DialerCall.getCallbackNumber",
            "numbers are the same (and callback number is not being forced to show);"
                + " not showing the callback number");
        callbackNumber = "";
      }
      if (callbackNumber == null) {
        callbackNumber = "";
      }
    }
    return callbackNumber;
  }

  private String getSubscriptionNumber() {
    // If it's an emergency call, and they're not populating the callback number,
    // then try to fall back to the phone sub info (to hopefully get the SIM's
    // number directly from the telephony layer).
    PhoneAccountHandle accountHandle = getAccountHandle();
    if (accountHandle != null) {
      PhoneAccount account =
          mContext.getSystemService(TelecomManager.class).getPhoneAccount(accountHandle);
      if (account != null) {
        return getNumberFromHandle(account.getSubscriptionAddress());
      }
    }
    return null;
  }

  @Override
  public void onVideoTechStateChanged() {
    update();
  }

  @Override
  public void onSessionModificationStateChanged() {
    for (DialerCallListener listener : mListeners) {
      listener.onDialerCallSessionModificationStateChange();
    }
  }

  @Override
  public void onCameraDimensionsChanged(int width, int height) {
    InCallVideoCallCallbackNotifier.getInstance().cameraDimensionsChanged(this, width, height);
  }

  @Override
  public void onPeerDimensionsChanged(int width, int height) {
    InCallVideoCallCallbackNotifier.getInstance().peerDimensionsChanged(this, width, height);
  }

  @Override
  public void onVideoUpgradeRequestReceived() {
    LogUtil.enterBlock("DialerCall.onVideoUpgradeRequestReceived");

    for (DialerCallListener listener : mListeners) {
      listener.onDialerCallUpgradeToVideo();
    }

    update();

    Logger.get(mContext)
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
        EnrichedCallComponent.get(mContext).getEnrichedCallManager().getCapabilities(getNumber());
    if (capabilities != null) {
      setEnrichedCallCapabilities(capabilities);
      update();
    }
  }

  @Override
  public void onEnrichedCallStateChanged() {
    updateEnrichedCallSession();
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

    EnrichedCallManager manager = EnrichedCallComponent.get(mContext).getEnrichedCallManager();

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
    for (DialerCallListener listener : mListeners) {
      listener.onEnrichedCallSessionUpdate();
    }
  }

  void onRemovedFromCallList() {
    // Ensure we clean up when this call is removed.
    mVideoTechManager.dispatchRemovedFromCallList();
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

  /* Defines different states of this call */
  public static class State {

    public static final int INVALID = 0;
    public static final int NEW = 1; /* The call is new. */
    public static final int IDLE = 2; /* The call is idle.  Nothing active */
    public static final int ACTIVE = 3; /* There is an active call */
    public static final int INCOMING = 4; /* A normal incoming phone call */
    public static final int CALL_WAITING = 5; /* Incoming call while another is active */
    public static final int DIALING = 6; /* An outgoing call during dial phase */
    public static final int REDIALING = 7; /* Subsequent dialing attempt after a failure */
    public static final int ONHOLD = 8; /* An active phone call placed on hold */
    public static final int DISCONNECTING = 9; /* A call is being ended. */
    public static final int DISCONNECTED = 10; /* State after a call disconnects */
    public static final int CONFERENCED = 11; /* DialerCall part of a conference call */
    public static final int SELECT_PHONE_ACCOUNT = 12; /* Waiting for account selection */
    public static final int CONNECTING = 13; /* Waiting for Telecom broadcast to finish */
    public static final int BLOCKED = 14; /* The number was found on the block list */
    public static final int PULLING = 15; /* An external call being pulled to the device */

    public static boolean isConnectingOrConnected(int state) {
      switch (state) {
        case ACTIVE:
        case INCOMING:
        case CALL_WAITING:
        case CONNECTING:
        case DIALING:
        case PULLING:
        case REDIALING:
        case ONHOLD:
        case CONFERENCED:
          return true;
        default:
          return false;
      }
    }

    public static boolean isDialing(int state) {
      return state == DIALING || state == PULLING || state == REDIALING;
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
        case PULLING:
          return "PULLING";
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
    public long duration = 0;
    public boolean isLogged = false;

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
          duration);
    }
  }

  private static class VideoTechManager {
    private final Context context;
    private final EmptyVideoTech emptyVideoTech = new EmptyVideoTech();
    private final List<VideoTech> videoTechs;
    private VideoTech savedTech;

    VideoTechManager(DialerCall call) {
      this.context = call.mContext;

      String phoneNumber = call.getNumber();
      phoneNumber = phoneNumber != null ? phoneNumber : "";
      phoneNumber = phoneNumber.replaceAll("[^+0-9]", "");

      // Insert order here determines the priority of that video tech option
      videoTechs = new ArrayList<>();
      videoTechs.add(new ImsVideoTech(Logger.get(call.mContext), call, call.mTelecomCall));

      VideoTech rcsVideoTech =
          EnrichedCallComponent.get(call.mContext)
              .getRcsVideoShareFactory()
              .newRcsVideoShare(
                  EnrichedCallComponent.get(call.mContext).getEnrichedCallManager(),
                  call,
                  phoneNumber);
      if (rcsVideoTech != null) {
        videoTechs.add(rcsVideoTech);
      }

      videoTechs.add(
          new LightbringerTech(
              LightbringerComponent.get(call.mContext).getLightbringer(),
              call,
              call.mTelecomCall,
              phoneNumber));
    }

    VideoTech getVideoTech() {
      if (savedTech != null) {
        return savedTech;
      }

      for (VideoTech tech : videoTechs) {
        if (tech.isAvailable(context)) {
          // Remember the first VideoTech that becomes available and always use it
          savedTech = tech;
          return savedTech;
        }
      }

      return emptyVideoTech;
    }

    void dispatchCallStateChanged(int newState) {
      for (VideoTech videoTech : videoTechs) {
        videoTech.onCallStateChanged(context, newState);
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
}
