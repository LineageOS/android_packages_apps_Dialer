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

import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.telecom.AudioState;
import android.telecom.CameraCapabilities;
import android.telecom.InCallService.VideoCall;
import android.view.Surface;

import com.android.contacts.common.CallUtil;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallOrientationListener;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.InCallVideoCallListenerNotifier.SurfaceChangeListener;
import com.android.incallui.InCallVideoCallListenerNotifier.VideoEventListener;
import com.google.common.base.Preconditions;

import java.util.Objects;

/**
 * Logic related to the {@link VideoCallFragment} and for managing changes to the video calling
 * surfaces based on other user interface events and incoming events from the
 * {@class VideoCallListener}.
 * <p>
 * When a call's video state changes to bi-directional video, the
 * {@link com.android.incallui.VideoCallPresenter} performs the following negotiation with the
 * telephony layer:
 * <ul>
 *     <li>{@code VideoCallPresenter} creates and informs telephony of the display surface.</li>
 *     <li>{@code VideoCallPresenter} creates the preview surface.</li>
 *     <li>{@code VideoCallPresenter} informs telephony of the currently selected camera.</li>
 *     <li>Telephony layer sends {@link CameraCapabilities}, including the
 *     dimensions of the video for the current camera.</li>
 *     <li>{@code VideoCallPresenter} adjusts size of the preview surface to match the aspect
 *     ratio of the camera.</li>
 *     <li>{@code VideoCallPresenter} informs telephony of the new preview surface.</li>
 * </ul>
 * <p>
 * When downgrading to an audio-only video state, the {@code VideoCallPresenter} nulls both
 * surfaces.
 */
public class VideoCallPresenter extends Presenter<VideoCallPresenter.VideoCallUi>  implements
        IncomingCallListener, InCallOrientationListener, InCallStateListener,
        InCallDetailsListener, SurfaceChangeListener, VideoEventListener,
        InCallVideoCallListenerNotifier.SessionModificationListener {

    /**
     * Determines the device orientation (portrait/lanscape).
     */
    public int getDeviceOrientation() {
        return mDeviceOrientation;
    }

    /**
     * Defines the state of the preview surface negotiation with the telephony layer.
     */
    private class PreviewSurfaceState {
        /**
         * The camera has not yet been set on the {@link VideoCall}; negotiation has not yet
         * started.
         */
        private static final int NONE = 0;

        /**
         * The camera has been set on the {@link VideoCall}, but camera capabilities have not yet
         * been received.
         */
        private static final int CAMERA_SET = 1;

        /**
         * The camera capabilties have been received from telephony, but the surface has not yet
         * been set on the {@link VideoCall}.
         */
        private static final int CAPABILITIES_RECEIVED = 2;

        /**
         * The surface has been set on the {@link VideoCall}.
         */
        private static final int SURFACE_SET = 3;
    }

    /**
     * The minimum width or height of the preview surface.  Used when re-sizing the preview surface
     * to match the aspect ratio of the currently selected camera.
     */
    private float mMinimumVideoDimension;

    /**
     * The current context.
     */
    private Context mContext;

    /**
     * The call the video surfaces are currently related to
     */
    private Call mPrimaryCall;

    /**
     * The {@link VideoCall} used to inform the video telephony layer of changes to the video
     * surfaces.
     */
    private VideoCall mVideoCall;

    /**
     * Determines if the current UI state represents a video call.
     */
    private boolean mIsVideoCall;

    /**
     * Determines the device orientation (portrait/lanscape).
     */
    private int mDeviceOrientation;

    /**
     * Tracks the state of the preview surface negotiation with the telephony layer.
     */
    private int mPreviewSurfaceState = PreviewSurfaceState.NONE;

    /**
     * Determines whether the video surface is in full-screen mode.
     */
    private boolean mIsFullScreen = false;

    /**
     * Saves the audio mode which was selected prior to going into a video call.
     */
    private int mPreVideoAudioMode = AudioModeProvider.AUDIO_MODE_INVALID;

    /** Handler which resets request state to NO_REQUEST after an interval. */
    private Handler mSessionModificationResetHandler;
    private static final long SESSION_MODIFICATION_RESET_DELAY_MS = 3000;

    /**
     * Initializes the presenter.
     *
     * @param context The current context.
     */
    public void init(Context context) {
        mContext = Preconditions.checkNotNull(context);
        mMinimumVideoDimension = mContext.getResources().getDimension(
                R.dimen.video_preview_small_dimension);
        mSessionModificationResetHandler = new Handler();
    }

    /**
     * Called when the user interface is ready to be used.
     *
     * @param ui The Ui implementation that is now ready to be used.
     */
    @Override
    public void onUiReady(VideoCallUi ui) {
        super.onUiReady(ui);

        // Register for call state changes last
        InCallPresenter.getInstance().addListener(this);
        InCallPresenter.getInstance().addIncomingCallListener(this);
        InCallPresenter.getInstance().addOrientationListener(this);

        // Register for surface and video events from {@link InCallVideoCallListener}s.
        InCallVideoCallListenerNotifier.getInstance().addSurfaceChangeListener(this);
        InCallVideoCallListenerNotifier.getInstance().addVideoEventListener(this);
        InCallVideoCallListenerNotifier.getInstance().addSessionModificationListener(this);
        mIsVideoCall = false;
    }

    /**
     * Called when the user interface is no longer ready to be used.
     *
     * @param ui The Ui implementation that is no longer ready to be used.
     */
    @Override
    public void onUiUnready(VideoCallUi ui) {
        super.onUiUnready(ui);

        InCallPresenter.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);
        InCallPresenter.getInstance().removeOrientationListener(this);
        InCallVideoCallListenerNotifier.getInstance().removeSurfaceChangeListener(this);
        InCallVideoCallListenerNotifier.getInstance().removeVideoEventListener(this);
        InCallVideoCallListenerNotifier.getInstance().removeSessionModificationListener(this);
    }

    /**
     * @return The {@link VideoCall}.
     */
    private VideoCall getVideoCall() {
        return mVideoCall;
    }

    /**
     * Handles the creation of a surface in the {@link VideoCallFragment}.
     *
     * @param surface The surface which was created.
     */
    public void onSurfaceCreated(int surface) {
        final VideoCallUi ui = getUi();

        if (ui == null || mVideoCall == null) {
            return;
        }

        // If the preview surface has just been created and we have already received camera
        // capabilities, but not yet set the surface, we will set the surface now.
        if (surface == VideoCallFragment.SURFACE_PREVIEW &&
                mPreviewSurfaceState == PreviewSurfaceState.CAPABILITIES_RECEIVED) {

            mPreviewSurfaceState = PreviewSurfaceState.SURFACE_SET;
            mVideoCall.setPreviewSurface(ui.getPreviewVideoSurface());
        } else if (surface == VideoCallFragment.SURFACE_DISPLAY) {
            mVideoCall.setDisplaySurface(ui.getDisplayVideoSurface());
        }
    }

    /**
     * Handles structural changes (format or size) to a surface.
     *
     * @param surface The surface which changed.
     * @param format The new PixelFormat of the surface.
     * @param width The new width of the surface.
     * @param height The new height of the surface.
     */
    public void onSurfaceChanged(int surface, int format, int width, int height) {
        //Do stuff
    }

    /**
     * Handles the destruction of a surface in the {@link VideoCallFragment}.
     *
     * @param surface The surface which was destroyed.
     */
    public void onSurfaceDestroyed(int surface) {
        final VideoCallUi ui = getUi();
        if (ui == null || mVideoCall == null) {
            return;
        }

        if (surface == VideoCallFragment.SURFACE_DISPLAY) {
            mVideoCall.setDisplaySurface(null);
        } else if (surface == VideoCallFragment.SURFACE_PREVIEW) {
            mVideoCall.setPreviewSurface(null);
        }
    }

    /**
     * Handles clicks on the video surfaces by toggling full screen state.
     * Informs the {@link InCallPresenter} of the change so that it can inform the
     * {@link CallCardPresenter} of the change.
     *
     * @param surfaceId The video surface receiving the click.
     */
    public void onSurfaceClick(int surfaceId) {
        mIsFullScreen = !mIsFullScreen;
        InCallPresenter.getInstance().setFullScreenVideoState(mIsFullScreen);
    }


    /**
     * Handles incoming calls.
     *
     * @param state The in call state.
     * @param call The call.
     */
    @Override
    public void onIncomingCall(InCallPresenter.InCallState oldState,
            InCallPresenter.InCallState newState, Call call) {
        // same logic should happen as with onStateChange()
        onStateChange(oldState, newState, CallList.getInstance());
    }

    /**
     * Handles state changes (including incoming calls)
     *
     * @param newState The in call state.
     * @param callList The call list.
     */
    @Override
    public void onStateChange(InCallPresenter.InCallState oldState,
            InCallPresenter.InCallState newState, CallList callList) {
        // Bail if video calling is disabled for the device.
        if (!CallUtil.isVideoEnabled(mContext)) {
            return;
        }

        if (newState == InCallPresenter.InCallState.NO_CALLS) {
            exitVideoMode();
        }

        // Determine the primary active call).
        Call primary = null;
        if (newState == InCallPresenter.InCallState.INCOMING) {
            primary = callList.getIncomingCall();
        } else if (newState == InCallPresenter.InCallState.OUTGOING) {
            primary = callList.getOutgoingCall();
        } else if (newState == InCallPresenter.InCallState.INCALL) {
            primary = callList.getActiveCall();
        }

        final boolean primaryChanged = !Objects.equals(mPrimaryCall, primary);
        if (primaryChanged) {
            mPrimaryCall = primary;

            if (primary != null) {
                checkForVideoCallChange();
                mIsVideoCall = mPrimaryCall.isVideoCall(mContext);
                if (mIsVideoCall) {
                    enterVideoMode();
                } else {
                    exitVideoMode();
                }
            } else if (primary == null) {
                // If no primary call, ensure we exit video state and clean up the video surfaces.
                exitVideoMode();
            }
        }
    }

    /**
     * Handles changes to the details of the call.  The {@link VideoCallPresenter} is interested in
     * changes to the video state.
     *
     * @param call The call for which the details changed.
     * @param details The new call details.
     */
    @Override
    public void onDetailsChanged(Call call, android.telecom.Call.Details details) {
        // If the details change is not for the currently active call no update is required.
        if (!call.equals(mPrimaryCall)) {
            return;
        }

        checkForVideoStateChange();
    }

    /**
     * Checks for a change to the video call and changes it if required.
     */
    private void checkForVideoCallChange() {
        VideoCall videoCall = mPrimaryCall.getTelecommCall().getVideoCall();
        if (!Objects.equals(videoCall, mVideoCall)) {
            changeVideoCall(videoCall);
        }
    }

    /**
     * Checks to see if the current video state has changed and updates the UI if required.
     */
    private void checkForVideoStateChange() {
        boolean newVideoState = mPrimaryCall.isVideoCall(mContext);

        // Check if video state changed
        if (mIsVideoCall != newVideoState) {
            mIsVideoCall = newVideoState;

            if (mIsVideoCall) {
                enterVideoMode();
            } else {
                exitVideoMode();
            }
        }
    }

    /**
     * Handles a change to the video call.  Sets the surfaces on the previous call to null and sets
     * the surfaces on the new video call accordingly.
     *
     * @param videoCall The new video call.
     */
    private void changeVideoCall(VideoCall videoCall) {
        // Null out the surfaces on the previous video call.
        if (mVideoCall != null) {
            mVideoCall.setDisplaySurface(null);
            mVideoCall.setPreviewSurface(null);
        }

        mVideoCall = videoCall;
    }

    /**
     * Enters video mode by showing the video surfaces and making other adjustments (eg. audio).
     * TODO(vt): Need to adjust size and orientation of preview surface here.
     */
    private void enterVideoMode() {
        VideoCallUi ui = getUi();
        if (ui == null) {
            return;
        }

        ui.showVideoUi(true);
        InCallPresenter.getInstance().setInCallAllowsOrientationChange(true);

        // Communicate the current camera to telephony and make a request for the camera
        // capabilities.
        if (mVideoCall != null) {
            // Do not reset the surfaces if we just restarted the activity due to an orientation
            // change.
            if (ui.isActivityRestart()) {
                return;
            }

            mPreviewSurfaceState = PreviewSurfaceState.CAMERA_SET;
            InCallCameraManager cameraManager = InCallPresenter.getInstance().
                    getInCallCameraManager();
            mVideoCall.setCamera(cameraManager.getActiveCameraId());
            mVideoCall.requestCameraCapabilities();

            if (ui.isDisplayVideoSurfaceCreated()) {
                mVideoCall.setDisplaySurface(ui.getDisplayVideoSurface());
            }
        }

        mPreVideoAudioMode = AudioModeProvider.getInstance().getAudioMode();
        TelecomAdapter.getInstance().setAudioRoute(AudioState.ROUTE_SPEAKER);
    }

    /**
     * Exits video mode by hiding the video surfaces  and making other adjustments (eg. audio).
     */
    private void exitVideoMode() {
        VideoCallUi ui = getUi();
        if (ui == null) {
            return;
        }
        InCallPresenter.getInstance().setInCallAllowsOrientationChange(false);
        ui.showVideoUi(false);

        if (mPreVideoAudioMode != AudioModeProvider.AUDIO_MODE_INVALID) {
            TelecomAdapter.getInstance().setAudioRoute(mPreVideoAudioMode);
            mPreVideoAudioMode = AudioModeProvider.AUDIO_MODE_INVALID;
        }
    }

    /**
     * Handles peer video pause state changes.
     *
     * @param call The call which paused or un-pausedvideo transmission.
     * @param paused {@code True} when the video transmission is paused, {@code false} when video
     *               transmission resumes.
     */
    @Override
    public void onPeerPauseStateChanged(Call call, boolean paused) {
        if (!call.equals(mPrimaryCall)) {
            return;
        }

        // TODO(vt): Show/hide the peer contact photo.
    }

    /**
     * Handles peer video dimension changes.
     *
     * @param call The call which experienced a peer video dimension change.
     * @param width The new peer video width .
     * @param height The new peer video height.
     */
    @Override
    public void onUpdatePeerDimensions(Call call, int width, int height) {
        if (!call.equals(mPrimaryCall)) {
            return;
        }

        // TODO(vt): Change display surface aspect ratio.
    }

    /**
     * Handles a change to the dimensions of the local camera.  Receiving the camera capabilities
     * triggers the creation of the video
     *
     * @param call The call which experienced the camera dimension change.
     * @param width The new camera video width.
     * @param height The new camera video height.
     */
    @Override
    public void onCameraDimensionsChange(Call call, int width, int height) {
        VideoCallUi ui = getUi();
        if (ui == null) {
            return;
        }

        if (!call.equals(mPrimaryCall)) {
            return;
        }

        mPreviewSurfaceState = PreviewSurfaceState.CAPABILITIES_RECEIVED;

        // Configure the preview surface to the correct aspect ratio.
        float aspectRatio = 1.0f;
        if (width > 0 && height > 0) {
            aspectRatio = (float) width / (float) height;
        }
        setPreviewSize(mDeviceOrientation, aspectRatio);

        // Check if the preview surface is ready yet; if it is, set it on the {@code VideoCall}.
        // If it not yet ready, it will be set when when creation completes.
        if (ui.isPreviewVideoSurfaceCreated()) {
            mPreviewSurfaceState = PreviewSurfaceState.SURFACE_SET;
            mVideoCall.setPreviewSurface(ui.getPreviewVideoSurface());
        }
    }

    /**
     * Handles hanges to the device orientation.
     * See: {@link Configuration.ORIENTATION_LANDSCAPE}, {@link Configuration.ORIENTATION_PORTRAIT}
     * @param orientation The device orientation.
     */
    @Override
    public void onDeviceOrientationChanged(int orientation) {
        mDeviceOrientation = orientation;
    }

    @Override
    public void onUpgradeToVideoRequest(Call call) {
        mPrimaryCall.setSessionModificationState(
                Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST);
    }

    @Override
    public void onUpgradeToVideoSuccess(Call call) {
        if (mPrimaryCall == null || !Call.areSame(mPrimaryCall, call)) {
            return;
        }

        mPrimaryCall.setSessionModificationState(Call.SessionModificationState.NO_REQUEST);
    }

    @Override
    public void onUpgradeToVideoFail(Call call) {
        if (mPrimaryCall == null || !Call.areSame(mPrimaryCall, call)) {
            return;
        }

        call.setSessionModificationState(Call.SessionModificationState.REQUEST_FAILED);

        // Start handler to change state from REQUEST_FAILED to NO_REQUEST after an interval.
        mSessionModificationResetHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mPrimaryCall.setSessionModificationState(Call.SessionModificationState.NO_REQUEST);
            }
        }, SESSION_MODIFICATION_RESET_DELAY_MS);
    }

    @Override
    public void onDowngradeToAudio(Call call) {
        // Implementing to satsify interface.
    }

    /**
     * Sets the preview surface size based on the current device orientation.
     * See: {@link Configuration.ORIENTATION_LANDSCAPE}, {@link Configuration.ORIENTATION_PORTRAIT}
     *
     * @param orientation The device orientation.
     * @param aspectRatio The aspect ratio of the camera (width / height).
     */
    private void setPreviewSize(int orientation, float aspectRatio) {
        VideoCallUi ui = getUi();
        if (ui == null) {
            return;
        }

        int height;
        int width;

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            width = (int) (mMinimumVideoDimension * aspectRatio);
            height = (int) mMinimumVideoDimension;
        } else {
            width = (int) mMinimumVideoDimension;
            height = (int) (mMinimumVideoDimension * aspectRatio);
        }
        ui.setPreviewSize(width, height);
    }

    /**
     * Defines the VideoCallUI interactions.
     */
    public interface VideoCallUi extends Ui {
        void showVideoUi(boolean show);
        boolean isDisplayVideoSurfaceCreated();
        boolean isPreviewVideoSurfaceCreated();
        Surface getDisplayVideoSurface();
        Surface getPreviewVideoSurface();
        void setPreviewSize(int width, int height);
        void cleanupSurfaces();
        boolean isActivityRestart();
    }
}
