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

package com.android.incallui;

import com.google.common.base.Preconditions;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class used by {@link InCallService.VideoCallListener} to notify interested parties of incoming
 * events.
 */
public class InCallVideoCallListenerNotifier {
    /**
     * Singleton instance of this class.
     */
    private static InCallVideoCallListenerNotifier sInstance = new InCallVideoCallListenerNotifier();

    /**
     * ConcurrentHashMap constructor params: 8 is initial table size, 0.9f is
     * load factor before resizing, 1 means we only expect a single thread to
     * access the map so make only a single shard
     */
    private final Set<SessionModificationListener> mSessionModificationListeners =
            Collections.newSetFromMap(new ConcurrentHashMap<SessionModificationListener, Boolean>
                    (8, 0.9f, 1));
    private final Set<VideoEventListener> mVideoEventListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<VideoEventListener, Boolean>(8, 0.9f, 1));
    private final Set<SurfaceChangeListener> mSurfaceChangeListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<SurfaceChangeListener, Boolean>(8, 0.9f, 1));

    /**
     * Static singleton accessor method.
     */
    public static InCallVideoCallListenerNotifier getInstance() {
        return sInstance;
    }

    /**
     * Private constructor.  Instance should only be acquired through getInstance().
     */
    private InCallVideoCallListenerNotifier() {
    }

    /**
     * Adds a new {@link SessionModificationListener}.
     *
     * @param listener The listener.
     */
    public void addSessionModificationListener(SessionModificationListener listener) {
        Preconditions.checkNotNull(listener);
        mSessionModificationListeners.add(listener);
    }

    /**
     * Remove a {@link SessionModificationListener}.
     *
     * @param listener The listener.
     */
    public void removeSessionModificationListener(SessionModificationListener listener) {
        if (listener != null) {
            mSessionModificationListeners.remove(listener);
        }
    }

    /**
     * Adds a new {@link VideoEventListener}.
     *
     * @param listener The listener.
     */
    public void addVideoEventListener(VideoEventListener listener) {
        Preconditions.checkNotNull(listener);
        mVideoEventListeners.add(listener);
    }

    /**
     * Remove a {@link VideoEventListener}.
     *
     * @param listener The listener.
     */
    public void removeVideoEventListener(VideoEventListener listener) {
        if (listener != null) {
            mVideoEventListeners.remove(listener);
        }
    }

    /**
     * Adds a new {@link SurfaceChangeListener}.
     *
     * @param listener The listener.
     */
    public void addSurfaceChangeListener(SurfaceChangeListener listener) {
        Preconditions.checkNotNull(listener);
        mSurfaceChangeListeners.add(listener);
    }

    /**
     * Remove a {@link SurfaceChangeListener}.
     *
     * @param listener The listener.
     */
    public void removeSurfaceChangeListener(SurfaceChangeListener listener) {
        if (listener != null) {
            mSurfaceChangeListeners.remove(listener);
        }
    }

    /**
     * Inform listeners of an upgrade to video request for a call.
     *
     * @param call The call.
     */
    public void upgradeToVideoRequest(Call call) {
        for (SessionModificationListener listener : mSessionModificationListeners) {
            listener.onUpgradeToVideoRequest(call);
        }
    }

    /**
     * Inform listeners of a successful response to a video request for a call.
     *
     * @param call The call.
     */
    public void upgradeToVideoSuccess(Call call) {
        for (SessionModificationListener listener : mSessionModificationListeners) {
            listener.onUpgradeToVideoSuccess(call);
        }
    }

    /**
     * Inform listeners of an unsuccessful response to a video request for a call.
     *
     * @param call The call.
     */
    public void upgradeToVideoFail(Call call) {
        for (SessionModificationListener listener : mSessionModificationListeners) {
            listener.onUpgradeToVideoFail(call);
        }
    }

    /**
     * Inform listeners of a downgrade to audio.
     *
     * @param call The call.
     */
    public void downgradeToAudio(Call call) {
        for (SessionModificationListener listener : mSessionModificationListeners) {
            listener.onDowngradeToAudio(call);
        }
    }

    /**
     * Inform listeners of a downgrade to audio.
     *
     * @param call The call.
     * @param paused The paused state.
     */
    public void peerPausedStateChanged(Call call, boolean paused) {
        for (VideoEventListener listener : mVideoEventListeners) {
            listener.onPeerPauseStateChanged(call, paused);
        }
    }

    /**
     * Inform listeners of a change to peer dimensions.
     *
     * @param call The call.
     * @param width New peer width.
     * @param height New peer height.
     */
    public void peerDimensionsChanged(Call call, int width, int height) {
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
    public void cameraDimensionsChanged(Call call, int width, int height) {
        for (SurfaceChangeListener listener : mSurfaceChangeListeners) {
            listener.onCameraDimensionsChange(call, width, height);
        }
    }

    /**
     * Listener interface for any class that wants to be notified of upgrade to video and downgrade
     * to audio session modification requests.
     */
    public interface SessionModificationListener {
        /**
         * Called when a peer request is received to upgrade an audio-only call to a video call.
         *
         * @param call The call the request was received for.
         */
        public void onUpgradeToVideoRequest(Call call);

        /**
         * Called when a request to a peer to upgrade an audio-only call to a video call is
         * successful.
         *
         * @param call The call the request was successful for.
         */
        public void onUpgradeToVideoSuccess(Call call);

        /**
         * Called when a request to a peer to upgrade an audio-only call to a video call is
         * NOT successful. This can be if the peer chooses rejects the the video call, or if the
         * peer does not support video calling, or if there is some error in sending the request.
         *
         * @param call The call the request was successful for.
         */
        public void onUpgradeToVideoFail(Call call);

        /**
         * Called when a call has been downgraded to audio-only.
         *
         * @param call The call which was downgraded to audio-only.
         */
        public void onDowngradeToAudio(Call call);
    }

    /**
     * Listener interface for any class that wants to be notified of video events, including pause
     * and un-pause of peer video.
     */
    public interface VideoEventListener {
        /**
         * Called when the peer pauses or un-pauses video transmission.
         *
         * @param call   The call which paused or un-paused video transmission.
         * @param paused {@code True} when the video transmission is paused, {@code false}
         *               otherwise.
         */
        public void onPeerPauseStateChanged(Call call, boolean paused);
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
         * @param width
         * @param height
         */
        public void onUpdatePeerDimensions(Call call, int width, int height);

        /**
         * Called when the local camera changes dimensions.  This occurs when a change in camera
         * occurs.
         *
         * @param call The call which experienced the camera dimension change.
         * @param width The new camera video width.
         * @param height The new camera video height.
         */
        public void onCameraDimensionsChange(Call call, int width, int height);
    }
}
