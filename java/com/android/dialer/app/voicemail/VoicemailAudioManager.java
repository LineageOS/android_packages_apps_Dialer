/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.dialer.app.voicemail;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.telecom.CallAudioState;
import com.android.dialer.common.LogUtil;
import java.util.concurrent.RejectedExecutionException;

/** This class manages all audio changes for voicemail playback. */
public final class VoicemailAudioManager
    implements OnAudioFocusChangeListener, WiredHeadsetManager.Listener {

  private static final String TAG = "VoicemailAudioManager";

  public static final int PLAYBACK_STREAM = AudioManager.STREAM_VOICE_CALL;

  private AudioManager mAudioManager;
  private VoicemailPlaybackPresenter mVoicemailPlaybackPresenter;
  private WiredHeadsetManager mWiredHeadsetManager;
  private boolean mWasSpeakerOn;
  private CallAudioState mCallAudioState;
  private boolean mBluetoothScoEnabled;

  public VoicemailAudioManager(
      Context context, VoicemailPlaybackPresenter voicemailPlaybackPresenter) {
    mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    mVoicemailPlaybackPresenter = voicemailPlaybackPresenter;
    mWiredHeadsetManager = new WiredHeadsetManager(context);
    mWiredHeadsetManager.setListener(this);

    mCallAudioState = getInitialAudioState();
    LogUtil.i(
        "VoicemailAudioManager.VoicemailAudioManager", "Initial audioState = " + mCallAudioState);
  }

  public void requestAudioFocus() {
    int result =
        mAudioManager.requestAudioFocus(
            this, PLAYBACK_STREAM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
    if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      throw new RejectedExecutionException("Could not capture audio focus.");
    }
    updateBluetoothScoState(true);
  }

  public void abandonAudioFocus() {
    updateBluetoothScoState(false);
    mAudioManager.abandonAudioFocus(this);
  }

  @Override
  public void onAudioFocusChange(int focusChange) {
    LogUtil.d("VoicemailAudioManager.onAudioFocusChange", "focusChange=" + focusChange);
    mVoicemailPlaybackPresenter.onAudioFocusChange(focusChange == AudioManager.AUDIOFOCUS_GAIN);
  }

  @Override
  public void onWiredHeadsetPluggedInChanged(boolean oldIsPluggedIn, boolean newIsPluggedIn) {
    LogUtil.i(
        "VoicemailAudioManager.onWiredHeadsetPluggedInChanged",
        "wired headset was plugged in changed: " + oldIsPluggedIn + " -> " + newIsPluggedIn);

    if (oldIsPluggedIn == newIsPluggedIn) {
      return;
    }

    int newRoute = mCallAudioState.getRoute(); // start out with existing route
    if (newIsPluggedIn) {
      newRoute = CallAudioState.ROUTE_WIRED_HEADSET;
    } else {
      if (mWasSpeakerOn) {
        newRoute = CallAudioState.ROUTE_SPEAKER;
      } else {
        newRoute = CallAudioState.ROUTE_EARPIECE;
      }
    }

    mVoicemailPlaybackPresenter.setSpeakerphoneOn(newRoute == CallAudioState.ROUTE_SPEAKER);

    // We need to call this every time even if we do not change the route because the supported
    // routes changed either to include or not include WIRED_HEADSET.
    setSystemAudioState(
        new CallAudioState(false /* muted */, newRoute, calculateSupportedRoutes()));
  }

  public void setSpeakerphoneOn(boolean on) {
    setAudioRoute(on ? CallAudioState.ROUTE_SPEAKER : CallAudioState.ROUTE_WIRED_OR_EARPIECE);
  }

  public boolean isWiredHeadsetPluggedIn() {
    return mWiredHeadsetManager.isPluggedIn();
  }

  public void registerReceivers() {
    // Receivers is plural because we expect to add bluetooth support.
    mWiredHeadsetManager.registerReceiver();
  }

  public void unregisterReceivers() {
    mWiredHeadsetManager.unregisterReceiver();
  }

  /**
   * Bluetooth SCO (Synchronous Connection-Oriented) is the "phone" bluetooth audio. The system will
   * route to the bluetooth headset automatically if A2DP ("media") is available, but if the headset
   * only supports SCO then dialer must route it manually.
   */
  private void updateBluetoothScoState(boolean hasAudioFocus) {
    if (hasAudioFocus) {
      if (hasMediaAudioCapability()) {
        mBluetoothScoEnabled = false;
      } else {
        mBluetoothScoEnabled = true;
        LogUtil.i(
            "VoicemailAudioManager.updateBluetoothScoState",
            "bluetooth device doesn't support media, using SCO instead");
      }
    } else {
      mBluetoothScoEnabled = false;
    }
    applyBluetoothScoState();
  }

  private void applyBluetoothScoState() {
    if (mBluetoothScoEnabled) {
      mAudioManager.startBluetoothSco();
      // The doc for startBluetoothSco() states it could take seconds to establish the SCO
      // connection, so we should probably resume the playback after we've acquired SCO.
      // In practice the delay is unnoticeable so this is ignored for simplicity.
      mAudioManager.setBluetoothScoOn(true);
    } else {
      mAudioManager.setBluetoothScoOn(false);
      mAudioManager.stopBluetoothSco();
    }
  }

  private boolean hasMediaAudioCapability() {
    for (AudioDeviceInfo info : mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
      if (info.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
        return true;
      }
    }
    return false;
  }

  /**
   * Change the audio route, for example from earpiece to speakerphone.
   *
   * @param route The new audio route to use. See {@link CallAudioState}.
   */
  void setAudioRoute(int route) {
    LogUtil.v(
        "VoicemailAudioManager.setAudioRoute",
        "route: " + CallAudioState.audioRouteToString(route));

    // Change ROUTE_WIRED_OR_EARPIECE to a single entry.
    int newRoute = selectWiredOrEarpiece(route, mCallAudioState.getSupportedRouteMask());

    // If route is unsupported, do nothing.
    if ((mCallAudioState.getSupportedRouteMask() | newRoute) == 0) {
      LogUtil.w(
          "VoicemailAudioManager.setAudioRoute",
          "Asking to set to a route that is unsupported: " + newRoute);
      return;
    }

    // Remember the new speaker state so it can be restored when the user plugs and unplugs
    // a headset.
    mWasSpeakerOn = newRoute == CallAudioState.ROUTE_SPEAKER;
    setSystemAudioState(
        new CallAudioState(false /* muted */, newRoute, mCallAudioState.getSupportedRouteMask()));
  }

  private CallAudioState getInitialAudioState() {
    int supportedRouteMask = calculateSupportedRoutes();
    int route = selectWiredOrEarpiece(CallAudioState.ROUTE_WIRED_OR_EARPIECE, supportedRouteMask);
    return new CallAudioState(false /* muted */, route, supportedRouteMask);
  }

  private int calculateSupportedRoutes() {
    int routeMask = CallAudioState.ROUTE_SPEAKER;
    if (mWiredHeadsetManager.isPluggedIn()) {
      routeMask |= CallAudioState.ROUTE_WIRED_HEADSET;
    } else {
      routeMask |= CallAudioState.ROUTE_EARPIECE;
    }
    return routeMask;
  }

  private int selectWiredOrEarpiece(int route, int supportedRouteMask) {
    // Since they are mutually exclusive and one is ALWAYS valid, we allow a special input of
    // ROUTE_WIRED_OR_EARPIECE so that callers don't have to make a call to check which is
    // supported before calling setAudioRoute.
    if (route == CallAudioState.ROUTE_WIRED_OR_EARPIECE) {
      route = CallAudioState.ROUTE_WIRED_OR_EARPIECE & supportedRouteMask;
      if (route == 0) {
        LogUtil.e(
            "VoicemailAudioManager.selectWiredOrEarpiece",
            "One of wired headset or earpiece should always be valid.");
        // assume earpiece in this case.
        route = CallAudioState.ROUTE_EARPIECE;
      }
    }
    return route;
  }

  private void setSystemAudioState(CallAudioState callAudioState) {
    CallAudioState oldAudioState = mCallAudioState;
    mCallAudioState = callAudioState;

    LogUtil.i(
        "VoicemailAudioManager.setSystemAudioState",
        "changing from " + oldAudioState + " to " + mCallAudioState);

    // Audio route.
    if (mCallAudioState.getRoute() == CallAudioState.ROUTE_SPEAKER) {
      turnOnSpeaker(true);
    } else if (mCallAudioState.getRoute() == CallAudioState.ROUTE_EARPIECE
        || mCallAudioState.getRoute() == CallAudioState.ROUTE_WIRED_HEADSET) {
      // Just handle turning off the speaker, the system will handle switching between wired
      // headset and earpiece.
      turnOnSpeaker(false);
      // BluetoothSco is not handled by the system so it has to be reset.
      applyBluetoothScoState();
    }
  }

  private void turnOnSpeaker(boolean on) {
    if (mAudioManager.isSpeakerphoneOn() != on) {
      LogUtil.i("VoicemailAudioManager.turnOnSpeaker", "turning speaker phone on: " + on);
      mAudioManager.setSpeakerphoneOn(on);
    }
  }
}
