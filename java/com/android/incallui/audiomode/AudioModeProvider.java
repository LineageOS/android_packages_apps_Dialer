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

package com.android.incallui.audiomode;

import android.telecom.CallAudioState;
import java.util.ArrayList;
import java.util.List;

/** Proxy class for getting and setting the audio mode. */
public class AudioModeProvider {
  private static final int SUPPORTED_AUDIO_ROUTE_ALL =
      CallAudioState.ROUTE_EARPIECE
          | CallAudioState.ROUTE_BLUETOOTH
          | CallAudioState.ROUTE_WIRED_HEADSET
          | CallAudioState.ROUTE_SPEAKER;

  private static final AudioModeProvider instance = new AudioModeProvider();
  private final List<AudioModeListener> listeners = new ArrayList<>();
  private CallAudioState audioState =
      new CallAudioState(false, CallAudioState.ROUTE_EARPIECE, SUPPORTED_AUDIO_ROUTE_ALL);

  public static AudioModeProvider getInstance() {
    return instance;
  }

  public void onAudioStateChanged(CallAudioState audioState) {
    if (!this.audioState.equals(audioState)) {
      this.audioState = audioState;
      for (AudioModeListener listener : listeners) {
        listener.onAudioStateChanged(audioState);
      }
    }
  }

  public void addListener(AudioModeListener listener) {
    if (!listeners.contains(listener)) {
      listeners.add(listener);
      listener.onAudioStateChanged(audioState);
    }
  }

  public void removeListener(AudioModeListener listener) {
    listeners.remove(listener);
  }

  public CallAudioState getAudioState() {
    return audioState;
  }

  /** Notified on changes to audio mode. */
  public interface AudioModeListener {

    void onAudioStateChanged(CallAudioState audioState);
  }
}
