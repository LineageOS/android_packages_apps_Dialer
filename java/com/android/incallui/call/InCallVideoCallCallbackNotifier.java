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
import com.android.dialer.common.LogUtil;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class used by {@link InCallService.VideoCallCallback} to notify interested parties of incoming
 * events.
 */
public class InCallVideoCallCallbackNotifier {

  /** Singleton instance of this class. */
  private static InCallVideoCallCallbackNotifier sInstance = new InCallVideoCallCallbackNotifier();

  /**
   * ConcurrentHashMap constructor params: 8 is initial table size, 0.9f is load factor before
   * resizing, 1 means we only expect a single thread to access the map so make only a single shard
   */
  private final Set<SessionModificationListener> mSessionModificationListeners =
      Collections.newSetFromMap(
          new ConcurrentHashMap<SessionModificationListener, Boolean>(8, 0.9f, 1));

  private final Set<VideoEventListener> mVideoEventListeners =
      Collections.newSetFromMap(new ConcurrentHashMap<VideoEventListener, Boolean>(8, 0.9f, 1));
  private final Set<SurfaceChangeListener> mSurfaceChangeListeners =
      Collections.newSetFromMap(new ConcurrentHashMap<SurfaceChangeListener, Boolean>(8, 0.9f, 1));

  /** Private constructor. Instance should only be acquired through getInstance(). */
  private InCallVideoCallCallbackNotifier() {}

  /** Static singleton accessor method. */
  public static InCallVideoCallCallbackNotifier getInstance() {
    return sInstance;
  }

  /**
   * Adds a new {@link SessionModificationListener}.
   *
   * @param listener The listener.
   */
  public void addSessionModificationListener(@NonNull SessionModificationListener listener) {
    Objects.requireNonNull(listener);
    mSessionModificationListeners.add(listener);
  }

  /**
   * Remove a {@link SessionModificationListener}.
   *
   * @param listener The listener.
   */
  public void removeSessionModificationListener(@Nullable SessionModificationListener listener) {
    if (listener != null) {
      mSessionModificationListeners.remove(listener);
    }
  }

  /**
   * Adds a new {@link VideoEventListener}.
   *
   * @param listener The listener.
   */
  public void addVideoEventListener(@NonNull VideoEventListener listener) {
    Objects.requireNonNull(listener);
    mVideoEventListeners.add(listener);
  }

  /**
   * Remove a {@link VideoEventListener}.
   *
   * @param listener The listener.
   */
  public void removeVideoEventListener(@Nullable VideoEventListener listener) {
    if (listener != null) {
      mVideoEventListeners.remove(listener);
    }
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
   * Inform listeners of an upgrade to video request for a call.
   *
   * @param call The call.
   * @param videoState The video state we want to upgrade to.
   */
  public void upgradeToVideoRequest(DialerCall call, int videoState) {
    LogUtil.v(
        "InCallVideoCallCallbackNotifier.upgradeToVideoRequest",
        "call = " + call + " new video state = " + videoState);
    for (SessionModificationListener listener : mSessionModificationListeners) {
      listener.onUpgradeToVideoRequest(call, videoState);
    }
  }

  /**
   * Inform listeners of a call session event.
   *
   * @param event The call session event.
   */
  public void callSessionEvent(int event) {
    for (VideoEventListener listener : mVideoEventListeners) {
      listener.onCallSessionEvent(event);
    }
  }

  /**
   * Inform listeners of a downgrade to audio.
   *
   * @param call The call.
   * @param paused The paused state.
   */
  public void peerPausedStateChanged(DialerCall call, boolean paused) {
    for (VideoEventListener listener : mVideoEventListeners) {
      listener.onPeerPauseStateChanged(call, paused);
    }
  }

  /**
   * Inform listeners of any change in the video quality of the call
   *
   * @param call The call.
   * @param videoQuality The updated video quality of the call.
   */
  public void videoQualityChanged(DialerCall call, int videoQuality) {
    for (VideoEventListener listener : mVideoEventListeners) {
      listener.onVideoQualityChanged(call, videoQuality);
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
   * Inform listeners of a change to call data usage.
   *
   * @param dataUsage data usage value
   */
  public void callDataUsageChanged(long dataUsage) {
    for (VideoEventListener listener : mVideoEventListeners) {
      listener.onCallDataUsageChange(dataUsage);
    }
  }

  /** Listener interface for any class that wants to be notified of upgrade to video request. */
  public interface SessionModificationListener {

    /**
     * Called when a peer request is received to upgrade an audio-only call to a video call.
     *
     * @param call The call the request was received for.
     * @param videoState The requested video state.
     */
    void onUpgradeToVideoRequest(DialerCall call, int videoState);
  }

  /**
   * Listener interface for any class that wants to be notified of video events, including pause and
   * un-pause of peer video, video quality changes.
   */
  public interface VideoEventListener {

    /**
     * Called when the peer pauses or un-pauses video transmission.
     *
     * @param call The call which paused or un-paused video transmission.
     * @param paused {@code True} when the video transmission is paused, {@code false} otherwise.
     */
    void onPeerPauseStateChanged(DialerCall call, boolean paused);

    /**
     * Called when the video quality changes.
     *
     * @param call The call whose video quality changes.
     * @param videoCallQuality - values are QUALITY_HIGH, MEDIUM, LOW and UNKNOWN.
     */
    void onVideoQualityChanged(DialerCall call, int videoCallQuality);

    /*
     * Called when call data usage value is requested or when call data usage value is updated
     * because of a call state change
     *
     * @param dataUsage call data usage value
     */
    void onCallDataUsageChange(long dataUsage);

    /**
     * Called when call session event is raised.
     *
     * @param event The call session event.
     */
    void onCallSessionEvent(int event);
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
