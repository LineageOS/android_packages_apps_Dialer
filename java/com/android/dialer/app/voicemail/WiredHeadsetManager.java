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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import com.android.dialer.common.LogUtil;

/** Listens for and caches headset state. */
class WiredHeadsetManager {

  private static final String TAG = WiredHeadsetManager.class.getSimpleName();
  private final WiredHeadsetBroadcastReceiver receiver;
  private boolean isPluggedIn;
  private Listener listener;
  private Context context;

  WiredHeadsetManager(Context context) {
    this.context = context;
    receiver = new WiredHeadsetBroadcastReceiver();

    AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    isPluggedIn = audioManager.isWiredHeadsetOn();
  }

  void setListener(Listener listener) {
    this.listener = listener;
  }

  boolean isPluggedIn() {
    return isPluggedIn;
  }

  void registerReceiver() {
    // Register for misc other intent broadcasts.
    IntentFilter intentFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
    context.registerReceiver(receiver, intentFilter);
  }

  void unregisterReceiver() {
    context.unregisterReceiver(receiver);
  }

  private void onHeadsetPluggedInChanged(boolean isPluggedIn) {
    if (this.isPluggedIn != isPluggedIn) {
      LogUtil.v(
          TAG,
          "onHeadsetPluggedInChanged, mIsPluggedIn: " + this.isPluggedIn + " -> " + isPluggedIn);
      boolean oldIsPluggedIn = this.isPluggedIn;
      this.isPluggedIn = isPluggedIn;
      if (listener != null) {
        listener.onWiredHeadsetPluggedInChanged(oldIsPluggedIn, this.isPluggedIn);
      }
    }
  }

  interface Listener {

    void onWiredHeadsetPluggedInChanged(boolean oldIsPluggedIn, boolean newIsPluggedIn);
  }

  /** Receiver for wired headset plugged and unplugged events. */
  private class WiredHeadsetBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      if (AudioManager.ACTION_HEADSET_PLUG.equals(intent.getAction())) {
        boolean isPluggedIn = intent.getIntExtra("state", 0) == 1;
        LogUtil.v(TAG, "ACTION_HEADSET_PLUG event, plugged in: " + isPluggedIn);
        onHeadsetPluggedInChanged(isPluggedIn);
      }
    }
  }
}
