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

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.telecom.CallAudioState;
import android.view.Display;
import com.android.dialer.common.LogUtil;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.audiomode.AudioModeProvider;
import com.android.incallui.audiomode.AudioModeProvider.AudioModeListener;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;

/**
 * Class manages the proximity sensor for the in-call UI. We enable the proximity sensor while the
 * user in a phone call. The Proximity sensor turns off the touchscreen and display when the user is
 * close to the screen to prevent user's cheek from causing touch events. The class requires special
 * knowledge of the activity and device state to know when the proximity sensor should be enabled
 * and disabled. Most of that state is fed into this class through public methods.
 */
public class ProximitySensor
    implements AccelerometerListener.OrientationListener, InCallStateListener, AudioModeListener {

  private static final String TAG = ProximitySensor.class.getSimpleName();

  private final PowerManager mPowerManager;
  private final PowerManager.WakeLock mProximityWakeLock;
  private final AudioModeProvider mAudioModeProvider;
  private final AccelerometerListener mAccelerometerListener;
  private final ProximityDisplayListener mDisplayListener;
  private int mOrientation = AccelerometerListener.ORIENTATION_UNKNOWN;
  private boolean mUiShowing = false;
  private boolean mIsPhoneOffhook = false;
  private boolean mDialpadVisible;
  private boolean mIsAttemptingVideoCall;
  private boolean mIsVideoCall;

  public ProximitySensor(
      @NonNull Context context,
      @NonNull AudioModeProvider audioModeProvider,
      @NonNull AccelerometerListener accelerometerListener) {
    mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    if (mPowerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
      mProximityWakeLock =
          mPowerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG);
    } else {
      LogUtil.i("ProximitySensor.constructor", "Device does not support proximity wake lock.");
      mProximityWakeLock = null;
    }
    mAccelerometerListener = accelerometerListener;
    mAccelerometerListener.setListener(this);

    mDisplayListener =
        new ProximityDisplayListener(
            (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE));
    mDisplayListener.register();

    mAudioModeProvider = audioModeProvider;
    mAudioModeProvider.addListener(this);
  }

  public void tearDown() {
    mAudioModeProvider.removeListener(this);

    mAccelerometerListener.enable(false);
    mDisplayListener.unregister();

    turnOffProximitySensor(true);
  }

  /** Called to identify when the device is laid down flat. */
  @Override
  public void orientationChanged(int orientation) {
    mOrientation = orientation;
    updateProximitySensorMode();
  }

  /** Called to keep track of the overall UI state. */
  @Override
  public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
    // We ignore incoming state because we do not want to enable proximity
    // sensor during incoming call screen. We check hasLiveCall() because a disconnected call
    // can also put the in-call screen in the INCALL state.
    boolean hasOngoingCall = InCallState.INCALL == newState && callList.hasLiveCall();
    boolean isOffhook = (InCallState.OUTGOING == newState) || hasOngoingCall;

    DialerCall activeCall = callList.getActiveCall();
    boolean isVideoCall = activeCall != null && activeCall.isVideoCall();

    if (isOffhook != mIsPhoneOffhook || mIsVideoCall != isVideoCall) {
      mIsPhoneOffhook = isOffhook;
      mIsVideoCall = isVideoCall;

      mOrientation = AccelerometerListener.ORIENTATION_UNKNOWN;
      mAccelerometerListener.enable(mIsPhoneOffhook);

      updateProximitySensorMode();
    }
  }

  @Override
  public void onAudioStateChanged(CallAudioState audioState) {
    updateProximitySensorMode();
  }

  public void onDialpadVisible(boolean visible) {
    mDialpadVisible = visible;
    updateProximitySensorMode();
  }

  public void setIsAttemptingVideoCall(boolean isAttemptingVideoCall) {
    LogUtil.i(
        "ProximitySensor.setIsAttemptingVideoCall",
        "isAttemptingVideoCall: %b",
        isAttemptingVideoCall);
    mIsAttemptingVideoCall = isAttemptingVideoCall;
    updateProximitySensorMode();
  }
  /** Used to save when the UI goes in and out of the foreground. */
  public void onInCallShowing(boolean showing) {
    if (showing) {
      mUiShowing = true;

      // We only consider the UI not showing for instances where another app took the foreground.
      // If we stopped showing because the screen is off, we still consider that showing.
    } else if (mPowerManager.isScreenOn()) {
      mUiShowing = false;
    }
    updateProximitySensorMode();
  }

  void onDisplayStateChanged(boolean isDisplayOn) {
    LogUtil.i("ProximitySensor.onDisplayStateChanged", "isDisplayOn: %b", isDisplayOn);
    mAccelerometerListener.enable(isDisplayOn);
  }

  /**
   * TODO: There is no way to determine if a screen is off due to proximity or if it is legitimately
   * off, but if ever we can do that in the future, it would be useful here. Until then, this
   * function will simply return true of the screen is off. TODO: Investigate whether this can be
   * replaced with the ProximityDisplayListener.
   */
  public boolean isScreenReallyOff() {
    return !mPowerManager.isScreenOn();
  }

  private void turnOnProximitySensor() {
    if (mProximityWakeLock != null) {
      if (!mProximityWakeLock.isHeld()) {
        LogUtil.i("ProximitySensor.turnOnProximitySensor", "acquiring wake lock");
        mProximityWakeLock.acquire();
      } else {
        LogUtil.i("ProximitySensor.turnOnProximitySensor", "wake lock already acquired");
      }
    }
  }

  private void turnOffProximitySensor(boolean screenOnImmediately) {
    if (mProximityWakeLock != null) {
      if (mProximityWakeLock.isHeld()) {
        LogUtil.i("ProximitySensor.turnOffProximitySensor", "releasing wake lock");
        int flags = (screenOnImmediately ? 0 : PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
        mProximityWakeLock.release(flags);
      } else {
        LogUtil.i("ProximitySensor.turnOffProximitySensor", "wake lock already released");
      }
    }
  }

  /**
   * Updates the wake lock used to control proximity sensor behavior, based on the current state of
   * the phone.
   *
   * <p>On devices that have a proximity sensor, to avoid false touches during a call, we hold a
   * PROXIMITY_SCREEN_OFF_WAKE_LOCK wake lock whenever the phone is off hook. (When held, that wake
   * lock causes the screen to turn off automatically when the sensor detects an object close to the
   * screen.)
   *
   * <p>This method is a no-op for devices that don't have a proximity sensor.
   *
   * <p>Proximity wake lock will be released if any of the following conditions are true: the audio
   * is routed through bluetooth, a wired headset, or the speaker; the user requested, received a
   * request for, or is in a video call; or the phone is horizontal while in a call.
   */
  private synchronized void updateProximitySensorMode() {
    final int audioRoute = mAudioModeProvider.getAudioState().getRoute();

    boolean screenOnImmediately =
        (CallAudioState.ROUTE_WIRED_HEADSET == audioRoute
            || CallAudioState.ROUTE_SPEAKER == audioRoute
            || CallAudioState.ROUTE_BLUETOOTH == audioRoute
            || mIsAttemptingVideoCall
            || mIsVideoCall);

    // We do not keep the screen off when the user is outside in-call screen and we are
    // horizontal, but we do not force it on when we become horizontal until the
    // proximity sensor goes negative.
    final boolean horizontal = (mOrientation == AccelerometerListener.ORIENTATION_HORIZONTAL);
    screenOnImmediately |= !mUiShowing && horizontal;

    // We do not keep the screen off when dialpad is visible, we are horizontal, and
    // the in-call screen is being shown.
    // At that moment we're pretty sure users want to use it, instead of letting the
    // proximity sensor turn off the screen by their hands.
    screenOnImmediately |= mDialpadVisible && horizontal;

    LogUtil.i(
        "ProximitySensor.updateProximitySensorMode",
        "screenOnImmediately: %b, dialPadVisible: %b, "
            + "offHook: %b, horizontal: %b, uiShowing: %b, audioRoute: %s",
        screenOnImmediately,
        mDialpadVisible,
        mIsPhoneOffhook,
        mOrientation == AccelerometerListener.ORIENTATION_HORIZONTAL,
        mUiShowing,
        CallAudioState.audioRouteToString(audioRoute));

    if (mIsPhoneOffhook && !screenOnImmediately) {
      LogUtil.v("ProximitySensor.updateProximitySensorMode", "turning on proximity sensor");
      // Phone is in use!  Arrange for the screen to turn off
      // automatically when the sensor detects a close object.
      turnOnProximitySensor();
    } else {
      LogUtil.v("ProximitySensor.updateProximitySensorMode", "turning off proximity sensor");
      // Phone is either idle, or ringing.  We don't want any special proximity sensor
      // behavior in either case.
      turnOffProximitySensor(screenOnImmediately);
    }
  }

  /**
   * Implementation of a {@link DisplayListener} that maintains a binary state: Screen on vs screen
   * off. Used by the proximity sensor manager to decide whether or not it needs to listen to
   * accelerometer events.
   */
  public class ProximityDisplayListener implements DisplayListener {

    private DisplayManager mDisplayManager;
    private boolean mIsDisplayOn = true;

    ProximityDisplayListener(DisplayManager displayManager) {
      mDisplayManager = displayManager;
    }

    void register() {
      mDisplayManager.registerDisplayListener(this, null);
    }

    void unregister() {
      mDisplayManager.unregisterDisplayListener(this);
    }

    @Override
    public void onDisplayRemoved(int displayId) {}

    @Override
    public void onDisplayChanged(int displayId) {
      if (displayId == Display.DEFAULT_DISPLAY) {
        final Display display = mDisplayManager.getDisplay(displayId);

        final boolean isDisplayOn = display.getState() != Display.STATE_OFF;
        // For call purposes, we assume that as long as the screen is not truly off, it is
        // considered on, even if it is in an unknown or low power idle state.
        if (isDisplayOn != mIsDisplayOn) {
          mIsDisplayOn = isDisplayOn;
          onDisplayStateChanged(mIsDisplayOn);
        }
      }
    }

    @Override
    public void onDisplayAdded(int displayId) {}
  }
}
