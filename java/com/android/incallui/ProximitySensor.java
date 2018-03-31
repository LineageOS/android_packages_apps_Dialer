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
import android.os.Trace;
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

  private final PowerManager powerManager;
  private final PowerManager.WakeLock proximityWakeLock;
  private final AudioModeProvider audioModeProvider;
  private final AccelerometerListener accelerometerListener;
  private final ProximityDisplayListener displayListener;
  private int orientation = AccelerometerListener.ORIENTATION_UNKNOWN;
  private boolean uiShowing = false;
  private boolean isPhoneOffhook = false;
  private boolean dialpadVisible;
  private boolean isAttemptingVideoCall;
  private boolean isVideoCall;
  private boolean isRttCall;

  public ProximitySensor(
      @NonNull Context context,
      @NonNull AudioModeProvider audioModeProvider,
      @NonNull AccelerometerListener accelerometerListener) {
    Trace.beginSection("ProximitySensor.Constructor");
    powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
      proximityWakeLock =
          powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG);
    } else {
      LogUtil.i("ProximitySensor.constructor", "Device does not support proximity wake lock.");
      proximityWakeLock = null;
    }
    this.accelerometerListener = accelerometerListener;
    this.accelerometerListener.setListener(this);

    displayListener =
        new ProximityDisplayListener(
            (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE));
    displayListener.register();

    this.audioModeProvider = audioModeProvider;
    this.audioModeProvider.addListener(this);
    Trace.endSection();
  }

  public void tearDown() {
    audioModeProvider.removeListener(this);

    accelerometerListener.enable(false);
    displayListener.unregister();

    turnOffProximitySensor(true);
  }

  /** Called to identify when the device is laid down flat. */
  @Override
  public void orientationChanged(int orientation) {
    this.orientation = orientation;
    updateProximitySensorMode();
  }

  /** Called to keep track of the overall UI state. */
  @Override
  public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
    // We ignore incoming state because we do not want to enable proximity
    // sensor during incoming call screen. We check hasLiveCall() because a disconnected call
    // can also put the in-call screen in the INCALL state.
    boolean hasOngoingCall = InCallState.INCALL == newState && callList.hasLiveCall();
    boolean isOffhook =
        InCallState.PENDING_OUTGOING == newState
            || InCallState.OUTGOING == newState
            || hasOngoingCall;

    DialerCall activeCall = callList.getActiveCall();
    boolean isVideoCall = activeCall != null && activeCall.isVideoCall();
    boolean isRttCall = activeCall != null && activeCall.isActiveRttCall();

    if (isOffhook != isPhoneOffhook
        || this.isVideoCall != isVideoCall
        || this.isRttCall != isRttCall) {
      isPhoneOffhook = isOffhook;
      this.isVideoCall = isVideoCall;
      this.isRttCall = isRttCall;

      orientation = AccelerometerListener.ORIENTATION_UNKNOWN;
      accelerometerListener.enable(isPhoneOffhook);

      updateProximitySensorMode();
    }
  }

  @Override
  public void onAudioStateChanged(CallAudioState audioState) {
    updateProximitySensorMode();
  }

  public void onDialpadVisible(boolean visible) {
    dialpadVisible = visible;
    updateProximitySensorMode();
  }

  public void setIsAttemptingVideoCall(boolean isAttemptingVideoCall) {
    LogUtil.i(
        "ProximitySensor.setIsAttemptingVideoCall",
        "isAttemptingVideoCall: %b",
        isAttemptingVideoCall);
    this.isAttemptingVideoCall = isAttemptingVideoCall;
    updateProximitySensorMode();
  }
  /** Used to save when the UI goes in and out of the foreground. */
  public void onInCallShowing(boolean showing) {
    if (showing) {
      uiShowing = true;

      // We only consider the UI not showing for instances where another app took the foreground.
      // If we stopped showing because the screen is off, we still consider that showing.
    } else if (powerManager.isScreenOn()) {
      uiShowing = false;
    }
    updateProximitySensorMode();
  }

  void onDisplayStateChanged(boolean isDisplayOn) {
    LogUtil.i("ProximitySensor.onDisplayStateChanged", "isDisplayOn: %b", isDisplayOn);
    accelerometerListener.enable(isDisplayOn);
  }

  /**
   * TODO: There is no way to determine if a screen is off due to proximity or if it is legitimately
   * off, but if ever we can do that in the future, it would be useful here. Until then, this
   * function will simply return true of the screen is off. TODO: Investigate whether this can be
   * replaced with the ProximityDisplayListener.
   */
  public boolean isScreenReallyOff() {
    return !powerManager.isScreenOn();
  }

  private void turnOnProximitySensor() {
    if (proximityWakeLock != null) {
      if (!proximityWakeLock.isHeld()) {
        LogUtil.i("ProximitySensor.turnOnProximitySensor", "acquiring wake lock");
        proximityWakeLock.acquire();
      } else {
        LogUtil.i("ProximitySensor.turnOnProximitySensor", "wake lock already acquired");
      }
    }
  }

  private void turnOffProximitySensor(boolean screenOnImmediately) {
    if (proximityWakeLock != null) {
      if (proximityWakeLock.isHeld()) {
        LogUtil.i("ProximitySensor.turnOffProximitySensor", "releasing wake lock");
        int flags = (screenOnImmediately ? 0 : PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
        proximityWakeLock.release(flags);
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
    Trace.beginSection("ProximitySensor.updateProximitySensorMode");
    final int audioRoute = audioModeProvider.getAudioState().getRoute();

    boolean screenOnImmediately =
        (CallAudioState.ROUTE_WIRED_HEADSET == audioRoute
            || CallAudioState.ROUTE_SPEAKER == audioRoute
            || CallAudioState.ROUTE_BLUETOOTH == audioRoute
            || isAttemptingVideoCall
            || isVideoCall
            || isRttCall);

    // We do not keep the screen off when the user is outside in-call screen and we are
    // horizontal, but we do not force it on when we become horizontal until the
    // proximity sensor goes negative.
    final boolean horizontal = (orientation == AccelerometerListener.ORIENTATION_HORIZONTAL);
    screenOnImmediately |= !uiShowing && horizontal;

    // We do not keep the screen off when dialpad is visible, we are horizontal, and
    // the in-call screen is being shown.
    // At that moment we're pretty sure users want to use it, instead of letting the
    // proximity sensor turn off the screen by their hands.
    screenOnImmediately |= dialpadVisible && horizontal;

    LogUtil.i(
        "ProximitySensor.updateProximitySensorMode",
        "screenOnImmediately: %b, dialPadVisible: %b, "
            + "offHook: %b, horizontal: %b, uiShowing: %b, audioRoute: %s",
        screenOnImmediately,
        dialpadVisible,
        isPhoneOffhook,
        orientation == AccelerometerListener.ORIENTATION_HORIZONTAL,
        uiShowing,
        CallAudioState.audioRouteToString(audioRoute));

    if (isPhoneOffhook && !screenOnImmediately) {
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
    Trace.endSection();
  }

  /**
   * Implementation of a {@link DisplayListener} that maintains a binary state: Screen on vs screen
   * off. Used by the proximity sensor manager to decide whether or not it needs to listen to
   * accelerometer events.
   */
  public class ProximityDisplayListener implements DisplayListener {

    private DisplayManager displayManager;
    private boolean isDisplayOn = true;

    ProximityDisplayListener(DisplayManager displayManager) {
      this.displayManager = displayManager;
    }

    void register() {
      displayManager.registerDisplayListener(this, null);
    }

    void unregister() {
      displayManager.unregisterDisplayListener(this);
    }

    @Override
    public void onDisplayRemoved(int displayId) {}

    @Override
    public void onDisplayChanged(int displayId) {
      if (displayId == Display.DEFAULT_DISPLAY) {
        final Display display = displayManager.getDisplay(displayId);

        final boolean isDisplayOn = display.getState() != Display.STATE_OFF;
        // For call purposes, we assume that as long as the screen is not truly off, it is
        // considered on, even if it is in an unknown or low power idle state.
        if (isDisplayOn != this.isDisplayOn) {
          this.isDisplayOn = isDisplayOn;
          onDisplayStateChanged(this.isDisplayOn);
        }
      }
    }

    @Override
    public void onDisplayAdded(int displayId) {}
  }
}
