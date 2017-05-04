/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Class used to notify interested parties of incoming video related events. */
public class InCallVideoCallCallbackNotifier {

  /** Singleton instance of this class. */
  private static InCallVideoCallCallbackNotifier sInstance = new InCallVideoCallCallbackNotifier();

  /**
   * ConcurrentHashMap constructor params: 8 is initial table size, 0.9f is load factor before
   * resizing, 1 means we only expect a single thread to access the map so make only a single shard
   */
  private final Set<SurfaceChangeListener> mSurfaceChangeListeners =
      Collections.newSetFromMap(new ConcurrentHashMap<SurfaceChangeListener, Boolean>(8, 0.9f, 1));

  /** Private constructor. Instance should only be acquired through getRunningInstance(). */
  private InCallVideoCallCallbackNotifier() {}

  /** Static singleton accessor method. */
  public static InCallVideoCallCallbackNotifier getInstance() {
    return sInstance;
  }

  /**
   * Adds a new {@link SurfaceChangeListener}.
   *
   * @param listener The listener.
   */
  public void addSurfaceChangeListener(@NonNull SurfaceChangeListener listener) {
    Objects.requireNonNull(listener);
    mSurfaceChangeListeners.add(listener);
  }

  /**
   * Remove a {@link SurfaceChangeListener}.
   *
   * @param listener The listener.
   */
  public void removeSurfaceChangeListener(@Nullable SurfaceChangeListener listener) {
    if (listener != null) {
      mSurfaceChangeListeners.remove(listener);
    }
  }

  /**
   * Inform listeners of a change to peer dimensions.
   *
   * @param call The call.
   * @param width New peer width.
   * @param height New peer height.
   */
  public void peerDimensionsChanged(DialerCall call, int width, int height) {
    for (SurfaceChangeListener listener : mSurfaceChangeListeners) {
      listener.onUpdatePeerDimensions(call, width, height);
    }
  }

  /**
   * Inform listeners of a change to camera dimensions.
   *
   * @param call The call.
   * @param width The new camera video width.
   * @param height The new camera video height.
   */
  public void cameraDimensionsChanged(DialerCall call, int width, int height) {
    for (SurfaceChangeListener listener : mSurfaceChangeListeners) {
      listener.onCameraDimensionsChange(call, width, height);
    }
  }

  /**
   * Listener interface for any class that wants to be notified of changes to the video surfaces.
   */
  public interface SurfaceChangeListener {

    /**
     * Called when the peer video feed changes dimensions. This can occur when the peer rotates
     * their device, changing the aspect ratio of the video signal.
     *
     * @param call The call which experienced a peer video
     */
    void onUpdatePeerDimensions(DialerCall call, int width, int height);

    /**
     * Called when the local camera changes dimensions. This occurs when a change in camera occurs.
     *
     * @param call The call which experienced the camera dimension change.
     * @param width The new camera video width.
     * @param height The new camera video height.
     */
    void onCameraDimensionsChange(DialerCall call, int width, int height);
  }
}
