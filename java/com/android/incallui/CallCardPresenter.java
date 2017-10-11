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
 * limitations under the License
 */

package com.android.incallui;

import static com.android.contacts.common.compat.CallCompat.Details.PROPERTY_ENTERPRISE_CALL;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.telecom.Call.Details;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.view.Display;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.ActivityCompat;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.multimedia.MultimediaData;
import com.android.dialer.oem.MotorolaUtils;
import com.android.incallui.ContactInfoCache.ContactCacheEntry;
import com.android.incallui.ContactInfoCache.ContactInfoCacheCallback;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallEventListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCallListener;
import com.android.incallui.calllocation.CallLocation;
import com.android.incallui.calllocation.CallLocationComponent;
import com.android.incallui.incall.protocol.ContactPhotoType;
import com.android.incallui.incall.protocol.InCallScreen;
import com.android.incallui.incall.protocol.InCallScreenDelegate;
import com.android.incallui.incall.protocol.PrimaryCallState;
import com.android.incallui.incall.protocol.PrimaryInfo;
import com.android.incallui.incall.protocol.SecondaryInfo;
import com.android.incallui.videotech.utils.SessionModificationState;
import java.lang.ref.WeakReference;

/**
 * Controller for the Call Card Fragment. This class listens for changes to InCallState and passes
 * it along to the fragment.
 */
public class CallCardPresenter
    implements InCallStateListener,
        IncomingCallListener,
        InCallDetailsListener,
        InCallEventListener,
        InCallScreenDelegate,
        DialerCallListener {

  /**
   * Amount of time to wait before sending an announcement via the accessibility manager. When the
   * call state changes to an outgoing or incoming state for the first time, the UI can often be
   * changing due to call updates or contact lookup. This allows the UI to settle to a stable state
   * to ensure that the correct information is announced.
   */
  private static final long ACCESSIBILITY_ANNOUNCEMENT_DELAY_MILLIS = 500;

  /** Flag to allow the user's current location to be shown during emergency calls. */
  private static final String CONFIG_ENABLE_EMERGENCY_LOCATION = "config_enable_emergency_location";

  private static final boolean CONFIG_ENABLE_EMERGENCY_LOCATION_DEFAULT = true;

  /**
   * Make it possible to not get location during an emergency call if the battery is too low, since
   * doing so could trigger gps and thus potentially cause the phone to die in the middle of the
   * call.
   */
  private static final String CONFIG_MIN_BATTERY_PERCENT_FOR_EMERGENCY_LOCATION =
      "min_battery_percent_for_emergency_location";

  private static final long CONFIG_MIN_BATTERY_PERCENT_FOR_EMERGENCY_LOCATION_DEFAULT = 10;

  private final Context mContext;
  private final Handler handler = new Handler();

  private DialerCall mPrimary;
  private DialerCall mSecondary;
  private ContactCacheEntry mPrimaryContactInfo;
  private ContactCacheEntry mSecondaryContactInfo;
  @Nullable private ContactsPreferences mContactsPreferences;
  private boolean mIsFullscreen = false;
  private InCallScreen mInCallScreen;
  private boolean isInCallScreenReady;
  private boolean shouldSendAccessibilityEvent;

  @NonNull private final CallLocation callLocation;
  private final Runnable sendAccessibilityEventRunnable =
      new Runnable() {
        @Override
        public void run() {
          shouldSendAccessibilityEvent = !sendAccessibilityEvent(mContext, getUi());
          LogUtil.i(
              "CallCardPresenter.sendAccessibilityEventRunnable",
              "still should send: %b",
              shouldSendAccessibilityEvent);
          if (!shouldSendAccessibilityEvent) {
            handler.removeCallbacks(this);
          }
        }
      };

  public CallCardPresenter(Context context) {
    LogUtil.i("CallCardController.constructor", null);
    mContext = Assert.isNotNull(context).getApplicationContext();
    callLocation = CallLocationComponent.get(mContext).getCallLocation();
  }

  private static boolean hasCallSubject(DialerCall call) {
    return !TextUtils.isEmpty(call.getCallSubject());
  }

  @Override
  public void onInCallScreenDelegateInit(InCallScreen inCallScreen) {
    Assert.isNotNull(inCallScreen);
    mInCallScreen = inCallScreen;
    mContactsPreferences = ContactsPreferencesFactory.newContactsPreferences(mContext);

    // Call may be null if disconnect happened already.
    DialerCall call = CallList.getInstance().getFirstCall();
    if (call != null) {
      mPrimary = call;
      if (shouldShowNoteSentToast(mPrimary)) {
        mInCallScreen.showNoteSentToast();
      }
      call.addListener(this);

      // start processing lookups right away.
      if (!call.isConferenceCall()) {
        startContactInfoSearch(call, true, call.getState() == DialerCall.State.INCOMING);
      } else {
        updateContactEntry(null, true);
      }
    }

    onStateChange(null, InCallPresenter.getInstance().getInCallState(), CallList.getInstance());
  }

  @Override
  public void onInCallScreenReady() {
    LogUtil.i("CallCardController.onInCallScreenReady", null);
    Assert.checkState(!isInCallScreenReady);
    if (mContactsPreferences != null) {
      mContactsPreferences.refreshValue(ContactsPreferences.DISPLAY_ORDER_KEY);
    }

    // Contact search may have completed before ui is ready.
    if (mPrimaryContactInfo != null) {
      updatePrimaryDisplayInfo();
    }

    // Register for call state changes last
    InCallPresenter.getInstance().addListener(this);
    InCallPresenter.getInstance().addIncomingCallListener(this);
    InCallPresenter.getInstance().addDetailsListener(this);
    InCallPresenter.getInstance().addInCallEventListener(this);
    isInCallScreenReady = true;

    // Log location impressions
    if (isOutgoingEmergencyCall(mPrimary)) {
      Logger.get(mContext).logImpression(DialerImpression.Type.EMERGENCY_NEW_EMERGENCY_CALL);
    } else if (isIncomingEmergencyCall(mPrimary) || isIncomingEmergencyCall(mSecondary)) {
      Logger.get(mContext).logImpression(DialerImpression.Type.EMERGENCY_CALLBACK);
    }

    // Showing the location may have been skipped if the UI wasn't ready during previous layout.
    if (shouldShowLocation()) {
      updatePrimaryDisplayInfo();

      // Log location impressions
      if (!hasLocationPermission()) {
        Logger.get(mContext).logImpression(DialerImpression.Type.EMERGENCY_NO_LOCATION_PERMISSION);
      } else if (isBatteryTooLowForEmergencyLocation()) {
        Logger.get(mContext)
            .logImpression(DialerImpression.Type.EMERGENCY_BATTERY_TOO_LOW_TO_GET_LOCATION);
      } else if (!callLocation.canGetLocation(mContext)) {
        Logger.get(mContext).logImpression(DialerImpression.Type.EMERGENCY_CANT_GET_LOCATION);
      }
    }
  }

  @Override
  public void onInCallScreenUnready() {
    LogUtil.i("CallCardController.onInCallScreenUnready", null);
    Assert.checkState(isInCallScreenReady);

    // stop getting call state changes
    InCallPresenter.getInstance().removeListener(this);
    InCallPresenter.getInstance().removeIncomingCallListener(this);
    InCallPresenter.getInstance().removeDetailsListener(this);
    InCallPresenter.getInstance().removeInCallEventListener(this);
    if (mPrimary != null) {
      mPrimary.removeListener(this);
    }

    callLocation.close();

    mPrimary = null;
    mPrimaryContactInfo = null;
    mSecondaryContactInfo = null;
    isInCallScreenReady = false;
  }

  @Override
  public void onIncomingCall(InCallState oldState, InCallState newState, DialerCall call) {
    // same logic should happen as with onStateChange()
    onStateChange(oldState, newState, CallList.getInstance());
  }

  @Override
  public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
    LogUtil.v("CallCardPresenter.onStateChange", "oldState: %s, newState: %s", oldState, newState);
    if (mInCallScreen == null) {
      return;
    }

    DialerCall primary = null;
    DialerCall secondary = null;

    if (newState == InCallState.INCOMING) {
      primary = callList.getIncomingCall();
    } else if (newState == InCallState.PENDING_OUTGOING || newState == InCallState.OUTGOING) {
      primary = callList.getOutgoingCall();
      if (primary == null) {
        primary = callList.getPendingOutgoingCall();
      }

      // getCallToDisplay doesn't go through outgoing or incoming calls. It will return the
      // highest priority call to display as the secondary call.
      secondary = getCallToDisplay(callList, null, true);
    } else if (newState == InCallState.INCALL) {
      primary = getCallToDisplay(callList, null, false);
      secondary = getCallToDisplay(callList, primary, true);
    }

    LogUtil.v("CallCardPresenter.onStateChange", "primary call: " + primary);
    LogUtil.v("CallCardPresenter.onStateChange", "secondary call: " + secondary);

    final boolean primaryChanged =
        !(DialerCall.areSame(mPrimary, primary) && DialerCall.areSameNumber(mPrimary, primary));
    final boolean secondaryChanged =
        !(DialerCall.areSame(mSecondary, secondary)
            && DialerCall.areSameNumber(mSecondary, secondary));

    mSecondary = secondary;
    DialerCall previousPrimary = mPrimary;
    mPrimary = primary;

    if (mPrimary != null) {
      InCallPresenter.getInstance().onForegroundCallChanged(mPrimary);
      mInCallScreen.updateInCallScreenColors();
    }

    if (primaryChanged && shouldShowNoteSentToast(primary)) {
      mInCallScreen.showNoteSentToast();
    }

    // Refresh primary call information if either:
    // 1. Primary call changed.
    // 2. The call's ability to manage conference has changed.
    if (shouldRefreshPrimaryInfo(primaryChanged)) {
      // primary call has changed
      if (previousPrimary != null) {
        previousPrimary.removeListener(this);
      }
      mPrimary.addListener(this);

      mPrimaryContactInfo =
          ContactInfoCache.buildCacheEntryFromCall(
              mContext, mPrimary, mPrimary.getState() == DialerCall.State.INCOMING);
      updatePrimaryDisplayInfo();
      maybeStartSearch(mPrimary, true);
    }

    if (previousPrimary != null && mPrimary == null) {
      previousPrimary.removeListener(this);
    }

    if (mSecondary == null) {
      // Secondary call may have ended.  Update the ui.
      mSecondaryContactInfo = null;
      updateSecondaryDisplayInfo();
    } else if (secondaryChanged) {
      // secondary call has changed
      mSecondaryContactInfo =
          ContactInfoCache.buildCacheEntryFromCall(
              mContext, mSecondary, mSecondary.getState() == DialerCall.State.INCOMING);
      updateSecondaryDisplayInfo();
      maybeStartSearch(mSecondary, false);
    }

    // Set the call state
    int callState = DialerCall.State.IDLE;
    if (mPrimary != null) {
      callState = mPrimary.getState();
      updatePrimaryCallState();
    } else {
      getUi().setCallState(PrimaryCallState.createEmptyPrimaryCallState());
    }

    maybeShowManageConferenceCallButton();

    // Hide the end call button instantly if we're receiving an incoming call.
    getUi()
        .setEndCallButtonEnabled(
            shouldShowEndCallButton(mPrimary, callState),
            callState != DialerCall.State.INCOMING /* animate */);

    maybeSendAccessibilityEvent(oldState, newState, primaryChanged);
  }

  @Override
  public void onDetailsChanged(DialerCall call, Details details) {
    updatePrimaryCallState();

    if (call.can(Details.CAPABILITY_MANAGE_CONFERENCE)
        != details.can(Details.CAPABILITY_MANAGE_CONFERENCE)) {
      maybeShowManageConferenceCallButton();
    }
  }

  @Override
  public void onDialerCallDisconnect() {}

  @Override
  public void onDialerCallUpdate() {
    // No-op; specific call updates handled elsewhere.
  }

  @Override
  public void onWiFiToLteHandover() {}

  @Override
  public void onHandoverToWifiFailure() {}

  @Override
  public void onInternationalCallOnWifi() {}

  @Override
  public void onEnrichedCallSessionUpdate() {
    LogUtil.enterBlock("CallCardPresenter.onEnrichedCallSessionUpdate");
    updatePrimaryDisplayInfo();
  }

  /** Handles a change to the child number by refreshing the primary call info. */
  @Override
  public void onDialerCallChildNumberChange() {
    LogUtil.v("CallCardPresenter.onDialerCallChildNumberChange", "");

    if (mPrimary == null) {
      return;
    }
    updatePrimaryDisplayInfo();
  }

  /** Handles a change to the last forwarding number by refreshing the primary call info. */
  @Override
  public void onDialerCallLastForwardedNumberChange() {
    LogUtil.v("CallCardPresenter.onDialerCallLastForwardedNumberChange", "");

    if (mPrimary == null) {
      return;
    }
    updatePrimaryDisplayInfo();
    updatePrimaryCallState();
  }

  @Override
  public void onDialerCallUpgradeToVideo() {}

  /** Handles a change to the session modification state for a call. */
  @Override
  public void onDialerCallSessionModificationStateChange() {
    LogUtil.enterBlock("CallCardPresenter.onDialerCallSessionModificationStateChange");

    if (mPrimary == null) {
      return;
    }
    getUi()
        .setEndCallButtonEnabled(
            mPrimary.getVideoTech().getSessionModificationState()
                != SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST,
            true /* shouldAnimate */);
    updatePrimaryCallState();
  }

  private boolean shouldRefreshPrimaryInfo(boolean primaryChanged) {
    if (mPrimary == null) {
      return false;
    }
    return primaryChanged
        || mInCallScreen.isManageConferenceVisible() != shouldShowManageConference();
  }

  private void updatePrimaryCallState() {
    if (getUi() != null && mPrimary != null) {
      boolean isWorkCall =
          mPrimary.hasProperty(PROPERTY_ENTERPRISE_CALL)
              || (mPrimaryContactInfo != null
                  && mPrimaryContactInfo.userType == ContactsUtils.USER_TYPE_WORK);
      boolean isHdAudioCall =
          isPrimaryCallActive() && mPrimary.hasProperty(Details.PROPERTY_HIGH_DEF_AUDIO);
      boolean isAttemptingHdAudioCall =
          !isHdAudioCall
              && !mPrimary.hasProperty(DialerCall.PROPERTY_CODEC_KNOWN)
              && MotorolaUtils.shouldBlinkHdIconWhenConnectingCall(mContext);

      boolean isBusiness = mPrimaryContactInfo != null && mPrimaryContactInfo.isBusiness;

      // Check for video state change and update the visibility of the contact photo.  The contact
      // photo is hidden when the incoming video surface is shown.
      // The contact photo visibility can also change in setPrimary().
      boolean shouldShowContactPhoto =
          !VideoCallPresenter.showIncomingVideo(mPrimary.getVideoState(), mPrimary.getState());
      getUi()
          .setCallState(
              new PrimaryCallState(
                  mPrimary.getState(),
                  mPrimary.isVideoCall(),
                  mPrimary.getVideoTech().getSessionModificationState(),
                  mPrimary.getDisconnectCause(),
                  getConnectionLabel(),
                  getCallStateIcon(),
                  getGatewayNumber(),
                  shouldShowCallSubject(mPrimary) ? mPrimary.getCallSubject() : null,
                  mPrimary.getCallbackNumber(),
                  mPrimary.hasProperty(Details.PROPERTY_WIFI),
                  mPrimary.isConferenceCall()
                      && !mPrimary.hasProperty(Details.PROPERTY_GENERIC_CONFERENCE),
                  isWorkCall,
                  isAttemptingHdAudioCall,
                  isHdAudioCall,
                  mPrimary.isForwarded(),
                  shouldShowContactPhoto,
                  mPrimary.getConnectTimeMillis(),
                  CallerInfoUtils.isVoiceMailNumber(mContext, mPrimary),
                  mPrimary.isRemotelyHeld(),
                  mPrimary.isDialingWaitingForRemoteSide(),
                  isBusiness,
                  supports2ndCallOnHold()));

      InCallActivity activity =
          (InCallActivity) (mInCallScreen.getInCallScreenFragment().getActivity());
      if (activity != null) {
        activity.onPrimaryCallStateChanged();
      }
    }
  }

  /** Only show the conference call button if we can manage the conference. */
  private void maybeShowManageConferenceCallButton() {
    getUi().showManageConferenceCallButton(shouldShowManageConference());
  }

  /**
   * Determines if the manage conference button should be visible, based on the current primary
   * call.
   *
   * @return {@code True} if the manage conference button should be visible.
   */
  private boolean shouldShowManageConference() {
    if (mPrimary == null) {
      return false;
    }

    return mPrimary.can(android.telecom.Call.Details.CAPABILITY_MANAGE_CONFERENCE)
        && !mIsFullscreen;
  }

  private boolean supports2ndCallOnHold() {
    DialerCall firstCall = CallList.getInstance().getActiveOrBackgroundCall();
    DialerCall incomingCall = CallList.getInstance().getIncomingCall();
    if (firstCall != null && incomingCall != null && firstCall != incomingCall) {
      return incomingCall.can(Details.CAPABILITY_HOLD);
    }
    return true;
  }

  @Override
  public void onCallStateButtonClicked() {
    Intent broadcastIntent = Bindings.get(mContext).getCallStateButtonBroadcastIntent(mContext);
    if (broadcastIntent != null) {
      LogUtil.v(
          "CallCardPresenter.onCallStateButtonClicked",
          "sending call state button broadcast: " + broadcastIntent);
      mContext.sendBroadcast(broadcastIntent, Manifest.permission.READ_PHONE_STATE);
    }
  }

  @Override
  public void onManageConferenceClicked() {
    InCallActivity activity =
        (InCallActivity) (mInCallScreen.getInCallScreenFragment().getActivity());
    activity.showConferenceFragment(true);
  }

  @Override
  public void onShrinkAnimationComplete() {
    InCallPresenter.getInstance().onShrinkAnimationComplete();
  }

  @Override
  public Drawable getDefaultContactPhotoDrawable() {
    return ContactInfoCache.getInstance(mContext).getDefaultContactPhotoDrawable();
  }

  private void maybeStartSearch(DialerCall call, boolean isPrimary) {
    // no need to start search for conference calls which show generic info.
    if (call != null && !call.isConferenceCall()) {
      startContactInfoSearch(call, isPrimary, call.getState() == DialerCall.State.INCOMING);
    }
  }

  /** Starts a query for more contact data for the save primary and secondary calls. */
  private void startContactInfoSearch(
      final DialerCall call, final boolean isPrimary, boolean isIncoming) {
    final ContactInfoCache cache = ContactInfoCache.getInstance(mContext);

    cache.findInfo(call, isIncoming, new ContactLookupCallback(this, isPrimary));
  }

  private void onContactInfoComplete(String callId, ContactCacheEntry entry, boolean isPrimary) {
    final boolean entryMatchesExistingCall =
        (isPrimary && mPrimary != null && TextUtils.equals(callId, mPrimary.getId()))
            || (!isPrimary && mSecondary != null && TextUtils.equals(callId, mSecondary.getId()));
    if (entryMatchesExistingCall) {
      updateContactEntry(entry, isPrimary);
    } else {
      LogUtil.e(
          "CallCardPresenter.onContactInfoComplete",
          "dropping stale contact lookup info for " + callId);
    }

    final DialerCall call = CallList.getInstance().getCallById(callId);
    if (call != null) {
      call.getLogState().contactLookupResult = entry.contactLookupResult;
    }
    if (entry.contactUri != null) {
      CallerInfoUtils.sendViewNotification(mContext, entry.contactUri);
    }
  }

  private void onImageLoadComplete(String callId, ContactCacheEntry entry) {
    if (getUi() == null) {
      return;
    }

    if (entry.photo != null) {
      if (mPrimary != null && callId.equals(mPrimary.getId())) {
        updateContactEntry(entry, true /* isPrimary */);
      } else if (mSecondary != null && callId.equals(mSecondary.getId())) {
        updateContactEntry(entry, false /* isPrimary */);
      }
    }
  }

  private void updateContactEntry(ContactCacheEntry entry, boolean isPrimary) {
    if (isPrimary) {
      mPrimaryContactInfo = entry;
      updatePrimaryDisplayInfo();
    } else {
      mSecondaryContactInfo = entry;
      updateSecondaryDisplayInfo();
    }
  }

  /**
   * Get the highest priority call to display. Goes through the calls and chooses which to return
   * based on priority of which type of call to display to the user. Callers can use the "ignore"
   * feature to get the second best call by passing a previously found primary call as ignore.
   *
   * @param ignore A call to ignore if found.
   */
  private DialerCall getCallToDisplay(
      CallList callList, DialerCall ignore, boolean skipDisconnected) {
    // Active calls come second.  An active call always gets precedent.
    DialerCall retval = callList.getActiveCall();
    if (retval != null && retval != ignore) {
      return retval;
    }

    // Sometimes there is intemediate state that two calls are in active even one is about
    // to be on hold.
    retval = callList.getSecondActiveCall();
    if (retval != null && retval != ignore) {
      return retval;
    }

    // Disconnected calls get primary position if there are no active calls
    // to let user know quickly what call has disconnected. Disconnected
    // calls are very short lived.
    if (!skipDisconnected) {
      retval = callList.getDisconnectingCall();
      if (retval != null && retval != ignore) {
        return retval;
      }
      retval = callList.getDisconnectedCall();
      if (retval != null && retval != ignore) {
        return retval;
      }
    }

    // Then we go to background call (calls on hold)
    retval = callList.getBackgroundCall();
    if (retval != null && retval != ignore) {
      return retval;
    }

    // Lastly, we go to a second background call.
    retval = callList.getSecondBackgroundCall();

    return retval;
  }

  private void updatePrimaryDisplayInfo() {
    if (mInCallScreen == null) {
      // TODO: May also occur if search result comes back after ui is destroyed. Look into
      // removing that case completely.
      LogUtil.v(
          "CallCardPresenter.updatePrimaryDisplayInfo",
          "updatePrimaryDisplayInfo called but ui is null!");
      return;
    }

    if (mPrimary == null) {
      // Clear the primary display info.
      mInCallScreen.setPrimary(PrimaryInfo.createEmptyPrimaryInfo());
      return;
    }

    // Hide the contact photo if we are in a video call and the incoming video surface is
    // showing.
    boolean showContactPhoto =
        !VideoCallPresenter.showIncomingVideo(mPrimary.getVideoState(), mPrimary.getState());

    // DialerCall placed through a work phone account.
    boolean hasWorkCallProperty = mPrimary.hasProperty(PROPERTY_ENTERPRISE_CALL);

    MultimediaData multimediaData = null;
    if (mPrimary.getEnrichedCallSession() != null) {
      multimediaData = mPrimary.getEnrichedCallSession().getMultimediaData();
    }

    if (mPrimary.isConferenceCall()) {
      LogUtil.v(
          "CallCardPresenter.updatePrimaryDisplayInfo",
          "update primary display info for conference call.");

      mInCallScreen.setPrimary(
          new PrimaryInfo(
              null /* number */,
              CallerInfoUtils.getConferenceString(
                  mContext, mPrimary.hasProperty(Details.PROPERTY_GENERIC_CONFERENCE)),
              false /* nameIsNumber */,
              null /* location */,
              null /* label */,
              null /* photo */,
              ContactPhotoType.DEFAULT_PLACEHOLDER,
              false /* isSipCall */,
              showContactPhoto,
              hasWorkCallProperty,
              false /* isSpam */,
              false /* answeringDisconnectsOngoingCall */,
              shouldShowLocation(),
              null /* contactInfoLookupKey */,
              null /* enrichedCallMultimediaData */,
              mPrimary.getNumberPresentation()));
    } else if (mPrimaryContactInfo != null) {
      LogUtil.v(
          "CallCardPresenter.updatePrimaryDisplayInfo",
          "update primary display info for " + mPrimaryContactInfo);

      String name = getNameForCall(mPrimaryContactInfo);
      String number;

      boolean isChildNumberShown = !TextUtils.isEmpty(mPrimary.getChildNumber());
      boolean isForwardedNumberShown = !TextUtils.isEmpty(mPrimary.getLastForwardedNumber());
      boolean isCallSubjectShown = shouldShowCallSubject(mPrimary);

      if (isCallSubjectShown) {
        number = null;
      } else if (isChildNumberShown) {
        number = mContext.getString(R.string.child_number, mPrimary.getChildNumber());
      } else if (isForwardedNumberShown) {
        // Use last forwarded number instead of second line, if present.
        number = mPrimary.getLastForwardedNumber();
      } else {
        number = mPrimaryContactInfo.number;
      }

      boolean nameIsNumber = name != null && name.equals(mPrimaryContactInfo.number);

      // DialerCall with caller that is a work contact.
      boolean isWorkContact = (mPrimaryContactInfo.userType == ContactsUtils.USER_TYPE_WORK);
      mInCallScreen.setPrimary(
          new PrimaryInfo(
              number,
              mPrimary.updateNameIfRestricted(name),
              nameIsNumber,
              shouldShowLocationAsLabel(nameIsNumber, mPrimaryContactInfo.shouldShowLocation)
                  ? mPrimaryContactInfo.location
                  : null,
              isChildNumberShown || isCallSubjectShown ? null : mPrimaryContactInfo.label,
              mPrimaryContactInfo.photo,
              mPrimaryContactInfo.photoType,
              mPrimaryContactInfo.isSipCall,
              showContactPhoto,
              hasWorkCallProperty || isWorkContact,
              mPrimary.isSpam(),
              mPrimary.answeringDisconnectsForegroundVideoCall(),
              shouldShowLocation(),
              mPrimaryContactInfo.lookupKey,
              multimediaData,
              mPrimary.getNumberPresentation()));
    } else {
      // Clear the primary display info.
      mInCallScreen.setPrimary(PrimaryInfo.createEmptyPrimaryInfo());
    }

    if (isInCallScreenReady) {
      mInCallScreen.showLocationUi(getLocationFragment());
    } else {
      LogUtil.i("CallCardPresenter.updatePrimaryDisplayInfo", "UI not ready, not showing location");
    }
  }

  private static boolean shouldShowLocationAsLabel(
      boolean nameIsNumber, boolean shouldShowLocation) {
    if (nameIsNumber) {
      return true;
    }
    if (shouldShowLocation) {
      return true;
    }
    return false;
  }

  private Fragment getLocationFragment() {
    if (!ConfigProviderBindings.get(mContext)
        .getBoolean(CONFIG_ENABLE_EMERGENCY_LOCATION, CONFIG_ENABLE_EMERGENCY_LOCATION_DEFAULT)) {
      LogUtil.i("CallCardPresenter.getLocationFragment", "disabled by config.");
      return null;
    }
    if (!shouldShowLocation()) {
      LogUtil.i("CallCardPresenter.getLocationFragment", "shouldn't show location");
      return null;
    }
    if (!hasLocationPermission()) {
      LogUtil.i("CallCardPresenter.getLocationFragment", "no location permission.");
      return null;
    }
    if (isBatteryTooLowForEmergencyLocation()) {
      LogUtil.i("CallCardPresenter.getLocationFragment", "low battery.");
      return null;
    }
    if (ActivityCompat.isInMultiWindowMode(mInCallScreen.getInCallScreenFragment().getActivity())) {
      LogUtil.i("CallCardPresenter.getLocationFragment", "in multi-window mode");
      return null;
    }
    if (mPrimary.isVideoCall()) {
      LogUtil.i("CallCardPresenter.getLocationFragment", "emergency video calls not supported");
      return null;
    }
    if (!callLocation.canGetLocation(mContext)) {
      LogUtil.i("CallCardPresenter.getLocationFragment", "can't get current location");
      return null;
    }
    LogUtil.i("CallCardPresenter.getLocationFragment", "returning location fragment");
    return callLocation.getLocationFragment(mContext);
  }

  private boolean shouldShowLocation() {
    if (isOutgoingEmergencyCall(mPrimary)) {
      LogUtil.i("CallCardPresenter.shouldShowLocation", "new emergency call");
      return true;
    } else if (isIncomingEmergencyCall(mPrimary)) {
      LogUtil.i("CallCardPresenter.shouldShowLocation", "potential emergency callback");
      return true;
    } else if (isIncomingEmergencyCall(mSecondary)) {
      LogUtil.i("CallCardPresenter.shouldShowLocation", "has potential emergency callback");
      return true;
    }
    return false;
  }

  private static boolean isOutgoingEmergencyCall(@Nullable DialerCall call) {
    return call != null && !call.isIncoming() && call.isEmergencyCall();
  }

  private static boolean isIncomingEmergencyCall(@Nullable DialerCall call) {
    return call != null && call.isIncoming() && call.isPotentialEmergencyCallback();
  }

  private boolean hasLocationPermission() {
    return ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
        == PackageManager.PERMISSION_GRANTED;
  }

  private boolean isBatteryTooLowForEmergencyLocation() {
    Intent batteryStatus =
        mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
    if (status == BatteryManager.BATTERY_STATUS_CHARGING
        || status == BatteryManager.BATTERY_STATUS_FULL) {
      // Plugged in or full battery
      return false;
    }
    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    float batteryPercent = (100f * level) / scale;
    long threshold =
        ConfigProviderBindings.get(mContext)
            .getLong(
                CONFIG_MIN_BATTERY_PERCENT_FOR_EMERGENCY_LOCATION,
                CONFIG_MIN_BATTERY_PERCENT_FOR_EMERGENCY_LOCATION_DEFAULT);
    LogUtil.i(
        "CallCardPresenter.isBatteryTooLowForEmergencyLocation",
        "percent charged: " + batteryPercent + ", min required charge: " + threshold);
    return batteryPercent < threshold;
  }

  private void updateSecondaryDisplayInfo() {
    if (mInCallScreen == null) {
      return;
    }

    if (mSecondary == null) {
      // Clear the secondary display info.
      mInCallScreen.setSecondary(SecondaryInfo.createEmptySecondaryInfo(mIsFullscreen));
      return;
    }

    if (mSecondary.isMergeInProcess()) {
      LogUtil.i(
          "CallCardPresenter.updateSecondaryDisplayInfo",
          "secondary call is merge in process, clearing info");
      mInCallScreen.setSecondary(SecondaryInfo.createEmptySecondaryInfo(mIsFullscreen));
      return;
    }

    if (mSecondary.isConferenceCall()) {
      mInCallScreen.setSecondary(
          new SecondaryInfo(
              true /* show */,
              CallerInfoUtils.getConferenceString(
                  mContext, mSecondary.hasProperty(Details.PROPERTY_GENERIC_CONFERENCE)),
              false /* nameIsNumber */,
              null /* label */,
              mSecondary.getCallProviderLabel(),
              true /* isConference */,
              mSecondary.isVideoCall(),
              mIsFullscreen));
    } else if (mSecondaryContactInfo != null) {
      LogUtil.v("CallCardPresenter.updateSecondaryDisplayInfo", "" + mSecondaryContactInfo);
      String name = getNameForCall(mSecondaryContactInfo);
      boolean nameIsNumber = name != null && name.equals(mSecondaryContactInfo.number);
      mInCallScreen.setSecondary(
          new SecondaryInfo(
              true /* show */,
              mSecondary.updateNameIfRestricted(name),
              nameIsNumber,
              mSecondaryContactInfo.label,
              mSecondary.getCallProviderLabel(),
              false /* isConference */,
              mSecondary.isVideoCall(),
              mIsFullscreen));
    } else {
      // Clear the secondary display info.
      mInCallScreen.setSecondary(SecondaryInfo.createEmptySecondaryInfo(mIsFullscreen));
    }
  }

  /** Returns the gateway number for any existing outgoing call. */
  private String getGatewayNumber() {
    if (hasOutgoingGatewayCall()) {
      return DialerCall.getNumberFromHandle(mPrimary.getGatewayInfo().getGatewayAddress());
    }
    return null;
  }

  /**
   * Returns the label (line of text above the number/name) for any given call. For example,
   * "calling via [Account/Google Voice]" for outgoing calls.
   */
  private String getConnectionLabel() {
    if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_PHONE_STATE)
        != PackageManager.PERMISSION_GRANTED) {
      return null;
    }
    StatusHints statusHints = mPrimary.getStatusHints();
    if (statusHints != null && !TextUtils.isEmpty(statusHints.getLabel())) {
      return statusHints.getLabel().toString();
    }

    if (hasOutgoingGatewayCall() && getUi() != null) {
      // Return the label for the gateway app on outgoing calls.
      final PackageManager pm = mContext.getPackageManager();
      try {
        ApplicationInfo info =
            pm.getApplicationInfo(mPrimary.getGatewayInfo().getGatewayProviderPackageName(), 0);
        return pm.getApplicationLabel(info).toString();
      } catch (PackageManager.NameNotFoundException e) {
        LogUtil.e("CallCardPresenter.getConnectionLabel", "gateway Application Not Found.", e);
        return null;
      }
    }
    return mPrimary.getCallProviderLabel();
  }

  private Drawable getCallStateIcon() {
    // Return connection icon if one exists.
    StatusHints statusHints = mPrimary.getStatusHints();
    if (statusHints != null && statusHints.getIcon() != null) {
      Drawable icon = statusHints.getIcon().loadDrawable(mContext);
      if (icon != null) {
        return icon;
      }
    }

    return null;
  }

  private boolean hasOutgoingGatewayCall() {
    // We only display the gateway information while STATE_DIALING so return false for any other
    // call state.
    // TODO: mPrimary can be null because this is called from updatePrimaryDisplayInfo which
    // is also called after a contact search completes (call is not present yet).  Split the
    // UI update so it can receive independent updates.
    if (mPrimary == null) {
      return false;
    }
    return DialerCall.State.isDialing(mPrimary.getState())
        && mPrimary.getGatewayInfo() != null
        && !mPrimary.getGatewayInfo().isEmpty();
  }

  /** Gets the name to display for the call. */
  private String getNameForCall(ContactCacheEntry contactInfo) {
    String preferredName =
        ContactDisplayUtils.getPreferredDisplayName(
            contactInfo.namePrimary, contactInfo.nameAlternative, mContactsPreferences);
    if (TextUtils.isEmpty(preferredName)) {
      return contactInfo.number;
    }
    return preferredName;
  }

  @Override
  public void onSecondaryInfoClicked() {
    if (mSecondary == null) {
      LogUtil.e(
          "CallCardPresenter.onSecondaryInfoClicked",
          "secondary info clicked but no secondary call.");
      return;
    }

    LogUtil.i(
        "CallCardPresenter.onSecondaryInfoClicked", "swapping call to foreground: " + mSecondary);
    mSecondary.unhold();
  }

  @Override
  public void onEndCallClicked() {
    LogUtil.i("CallCardPresenter.onEndCallClicked", "disconnecting call: " + mPrimary);
    if (mPrimary != null) {
      mPrimary.disconnect();
    }
  }

  /**
   * Handles a change to the fullscreen mode of the in-call UI.
   *
   * @param isFullscreenMode {@code True} if the in-call UI is entering full screen mode.
   */
  @Override
  public void onFullscreenModeChanged(boolean isFullscreenMode) {
    mIsFullscreen = isFullscreenMode;
    if (mInCallScreen == null) {
      return;
    }
    maybeShowManageConferenceCallButton();
  }

  private boolean isPrimaryCallActive() {
    return mPrimary != null && mPrimary.getState() == DialerCall.State.ACTIVE;
  }

  private boolean shouldShowEndCallButton(DialerCall primary, int callState) {
    if (primary == null) {
      return false;
    }
    if ((!DialerCall.State.isConnectingOrConnected(callState)
            && callState != DialerCall.State.DISCONNECTING
            && callState != DialerCall.State.DISCONNECTED)
        || callState == DialerCall.State.INCOMING) {
      return false;
    }
    if (mPrimary.getVideoTech().getSessionModificationState()
        == SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
      return false;
    }
    return true;
  }

  @Override
  public void onInCallScreenResumed() {
    updatePrimaryDisplayInfo();

    if (shouldSendAccessibilityEvent) {
      handler.postDelayed(sendAccessibilityEventRunnable, ACCESSIBILITY_ANNOUNCEMENT_DELAY_MILLIS);
    }
  }

  @Override
  public void onInCallScreenPaused() {}

  static boolean sendAccessibilityEvent(Context context, InCallScreen inCallScreen) {
    AccessibilityManager am =
        (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    if (!am.isEnabled()) {
      LogUtil.w("CallCardPresenter.sendAccessibilityEvent", "accessibility is off");
      return false;
    }
    if (inCallScreen == null) {
      LogUtil.w("CallCardPresenter.sendAccessibilityEvent", "incallscreen is null");
      return false;
    }
    Fragment fragment = inCallScreen.getInCallScreenFragment();
    if (fragment == null || fragment.getView() == null || fragment.getView().getParent() == null) {
      LogUtil.w("CallCardPresenter.sendAccessibilityEvent", "fragment/view/parent is null");
      return false;
    }

    DisplayManager displayManager =
        (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
    Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
    boolean screenIsOn = display.getState() == Display.STATE_ON;
    LogUtil.d("CallCardPresenter.sendAccessibilityEvent", "screen is on: %b", screenIsOn);
    if (!screenIsOn) {
      return false;
    }

    AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT);
    inCallScreen.dispatchPopulateAccessibilityEvent(event);
    View view = inCallScreen.getInCallScreenFragment().getView();
    view.getParent().requestSendAccessibilityEvent(view, event);
    return true;
  }

  private void maybeSendAccessibilityEvent(
      InCallState oldState, final InCallState newState, boolean primaryChanged) {
    shouldSendAccessibilityEvent = false;
    if (mContext == null) {
      return;
    }
    final AccessibilityManager am =
        (AccessibilityManager) mContext.getSystemService(Context.ACCESSIBILITY_SERVICE);
    if (!am.isEnabled()) {
      return;
    }
    // Announce the current call if it's new incoming/outgoing call or primary call is changed
    // due to switching calls between two ongoing calls (one is on hold).
    if ((oldState != InCallState.OUTGOING && newState == InCallState.OUTGOING)
        || (oldState != InCallState.INCOMING && newState == InCallState.INCOMING)
        || primaryChanged) {
      LogUtil.i(
          "CallCardPresenter.maybeSendAccessibilityEvent", "schedule accessibility announcement");
      shouldSendAccessibilityEvent = true;
      handler.postDelayed(sendAccessibilityEventRunnable, ACCESSIBILITY_ANNOUNCEMENT_DELAY_MILLIS);
    }
  }

  /**
   * Determines whether the call subject should be visible on the UI. For the call subject to be
   * visible, the call has to be in an incoming or waiting state, and the subject must not be empty.
   *
   * @param call The call.
   * @return {@code true} if the subject should be shown, {@code false} otherwise.
   */
  private boolean shouldShowCallSubject(DialerCall call) {
    if (call == null) {
      return false;
    }

    boolean isIncomingOrWaiting =
        mPrimary.getState() == DialerCall.State.INCOMING
            || mPrimary.getState() == DialerCall.State.CALL_WAITING;
    return isIncomingOrWaiting
        && !TextUtils.isEmpty(call.getCallSubject())
        && call.getNumberPresentation() == TelecomManager.PRESENTATION_ALLOWED
        && call.isCallSubjectSupported();
  }

  /**
   * Determines whether the "note sent" toast should be shown. It should be shown for a new outgoing
   * call with a subject.
   *
   * @param call The call
   * @return {@code true} if the toast should be shown, {@code false} otherwise.
   */
  private boolean shouldShowNoteSentToast(DialerCall call) {
    return call != null
        && hasCallSubject(call)
        && (call.getState() == DialerCall.State.DIALING
            || call.getState() == DialerCall.State.CONNECTING);
  }

  private InCallScreen getUi() {
    return mInCallScreen;
  }

  public static class ContactLookupCallback implements ContactInfoCacheCallback {

    private final WeakReference<CallCardPresenter> mCallCardPresenter;
    private final boolean mIsPrimary;

    public ContactLookupCallback(CallCardPresenter callCardPresenter, boolean isPrimary) {
      mCallCardPresenter = new WeakReference<CallCardPresenter>(callCardPresenter);
      mIsPrimary = isPrimary;
    }

    @Override
    public void onContactInfoComplete(String callId, ContactCacheEntry entry) {
      CallCardPresenter presenter = mCallCardPresenter.get();
      if (presenter != null) {
        presenter.onContactInfoComplete(callId, entry, mIsPrimary);
      }
    }

    @Override
    public void onImageLoadComplete(String callId, ContactCacheEntry entry) {
      CallCardPresenter presenter = mCallCardPresenter.get();
      if (presenter != null) {
        presenter.onImageLoadComplete(callId, entry);
      }
    }
  }
}
