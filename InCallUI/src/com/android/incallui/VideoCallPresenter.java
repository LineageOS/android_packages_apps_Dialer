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
import android.database.Cursor;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.InCallService.VideoCall;
import android.telecom.VideoProfile;
import android.telecom.VideoProfile.CameraCapabilities;
import android.view.Surface;
import android.widget.ImageView;

import com.android.contacts.common.ContactPhotoManager;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallOrientationListener;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.InCallVideoCallCallbackNotifier.SurfaceChangeListener;
import com.android.incallui.InCallVideoCallCallbackNotifier.VideoEventListener;

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
public class VideoCallPresenter extends Presenter<VideoCallPresenter.VideoCallUi> implements
        IncomingCallListener, InCallOrientationListener, InCallStateListener,
        InCallDetailsListener, SurfaceChangeListener, VideoEventListener,
        InCallVideoCallCallbackNotifier.SessionModificationListener,
        InCallPresenter.InCallEventListener {
    public static final String TAG = "VideoCallPresenter";

    public static final boolean DEBUG = false;

    /**
     * Runnable which is posted to schedule automatically entering fullscreen mode.
     */
    private Runnable mAutoFullscreenRunnable =  new Runnable() {
        @Override
        public void run() {
            if (mAutoFullScreenPending) {
                Log.v(this, "Automatically entering fullscreen mode.");
                InCallPresenter.getInstance().setFullScreen(true);
                mAutoFullScreenPending = false;
            } else {
                Log.v(this, "Skipping scheduled fullscreen mode.");
            }
        }
    };

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
    private int mCurrentVideoState;

    /**
     * Call's current state
     */
    private int mCurrentCallState = Call.State.INVALID;

    /**
     * Determines the device orientation (portrait/lanscape).
     */
    private int mDeviceOrientation;

    /**
     * Tracks the state of the preview surface negotiation with the telephony layer.
     */
    private int mPreviewSurfaceState = PreviewSurfaceState.NONE;

    /**
     * Saves the audio mode which was selected prior to going into a video call.
     */
    private static int sPrevVideoAudioMode = AudioModeProvider.AUDIO_MODE_INVALID;

    private static boolean mIsVideoMode = false;

    /**
     * Contact photo manager to retrieve cached contact photo information.
     */
    private ContactPhotoManager mContactPhotoManager = null;

    /**
     * The URI for the user's profile photo, or {@code null} if not specified.
     */
    private ContactInfoCache.ContactCacheEntry mProfileInfo = null;

    /**
     * UI thread handler used for delayed task execution.
     */
    private Handler mHandler;

    /**
     * Determines whether video calls should automatically enter full screen mode after
     * {@link #mAutoFullscreenTimeoutMillis} milliseconds.
     */
    private boolean mIsAutoFullscreenEnabled = false;

    /**
     * Determines the number of milliseconds after which a video call will automatically enter
     * fullscreen mode.  Requires {@link #mIsAutoFullscreenEnabled} to be {@code true}.
     */
    private int mAutoFullscreenTimeoutMillis = 0;

    /**
     * Determines if the countdown is currently running to automatically enter full screen video
     * mode.
     */
    private boolean mAutoFullScreenPending = false;

    /**
     * Initializes the presenter.
     *
     * @param context The current context.
     */
    public void init(Context context) {
        mContext = context;
        mMinimumVideoDimension = mContext.getResources().getDimension(
                R.dimen.video_preview_small_dimension);
        mHandler = new Handler(Looper.getMainLooper());
        mIsAutoFullscreenEnabled = mContext.getResources()
                .getBoolean(R.bool.video_call_auto_fullscreen);
        mAutoFullscreenTimeoutMillis = mContext.getResources().getInteger(
                R.integer.video_call_auto_fullscreen_timeout);
    }

    /**
     * Called when the user interface is ready to be used.
     *
     * @param ui The Ui implementation that is now ready to be used.
     */
    @Override
    public void onUiReady(VideoCallUi ui) {
        super.onUiReady(ui);
        Log.d(this, "onUiReady:");

        // Register for call state changes last
        InCallPresenter.getInstance().addListener(this);
        InCallPresenter.getInstance().addDetailsListener(this);
        InCallPresenter.getInstance().addIncomingCallListener(this);
        InCallPresenter.getInstance().addOrientationListener(this);
        // To get updates of video call details changes
        InCallPresenter.getInstance().addDetailsListener(this);

        // Register for surface and video events from {@link InCallVideoCallListener}s.
        InCallVideoCallCallbackNotifier.getInstance().addSurfaceChangeListener(this);
        InCallVideoCallCallbackNotifier.getInstance().addVideoEventListener(this);
        InCallVideoCallCallbackNotifier.getInstance().addSessionModificationListener(this);
        mCurrentVideoState = VideoProfile.STATE_AUDIO_ONLY;
        mCurrentCallState = Call.State.INVALID;
    }

    /**
     * Called when the user interface is no longer ready to be used.
     *
     * @param ui The Ui implementation that is no longer ready to be used.
     */
    @Override
    public void onUiUnready(VideoCallUi ui) {
        super.onUiUnready(ui);
        Log.d(this, "onUiUnready:");

        InCallPresenter.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeDetailsListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);
        InCallPresenter.getInstance().removeOrientationListener(this);

        InCallVideoCallCallbackNotifier.getInstance().removeSurfaceChangeListener(this);
        InCallVideoCallCallbackNotifier.getInstance().removeVideoEventListener(this);
        InCallVideoCallCallbackNotifier.getInstance().removeSessionModificationListener(this);
    }

    /**
     * Handles the creation of a surface in the {@link VideoCallFragment}.
     *
     * @param surface The surface which was created.
     */
    public void onSurfaceCreated(int surface) {
        Log.d(this, "onSurfaceCreated surface=" + surface + " mVideoCall=" + mVideoCall);
        Log.d(this, "onSurfaceCreated PreviewSurfaceState=" + mPreviewSurfaceState);
        Log.d(this, "onSurfaceCreated presenter=" + this);

        final VideoCallUi ui = getUi();
        if (ui == null || mVideoCall == null) {
            Log.w(this, "onSurfaceCreated: Error bad state VideoCallUi=" + ui + " mVideoCall="
                    + mVideoCall);
            return;
        }

        // If the preview surface has just been created and we have already received camera
        // capabilities, but not yet set the surface, we will set the surface now.
        if (surface == VideoCallFragment.SURFACE_PREVIEW ) {
            if (mPreviewSurfaceState == PreviewSurfaceState.CAPABILITIES_RECEIVED) {
                mPreviewSurfaceState = PreviewSurfaceState.SURFACE_SET;
                mVideoCall.setPreviewSurface(ui.getPreviewVideoSurface());
            } else if (mPreviewSurfaceState == PreviewSurfaceState.NONE && isCameraRequired()){
                enableCamera(mVideoCall, true);
            }
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
     * Note: The surface is being released, that is, it is no longer valid.
     *
     * @param surface The surface which was destroyed.
     */
    public void onSurfaceReleased(int surface) {
        Log.d(this, "onSurfaceReleased: mSurfaceId=" + surface);
        if ( mVideoCall == null) {
            Log.w(this, "onSurfaceReleased: VideoCall is null. mSurfaceId=" +
                    surface);
            return;
        }

        if (surface == VideoCallFragment.SURFACE_DISPLAY) {
            mVideoCall.setDisplaySurface(null);
        } else if (surface == VideoCallFragment.SURFACE_PREVIEW) {
            mVideoCall.setPreviewSurface(null);
            enableCamera(mVideoCall, false);
        }
    }

    /**
     * Called by {@link VideoCallFragment} when the surface is detached from UI (TextureView).
     * Note: The surface will be cached by {@link VideoCallFragment}, so we don't immediately
     * null out incoming video surface.
     * @see VideoCallPresenter#onSurfaceReleased(int)
     *
     * @param surface The surface which was detached.
     */
    public void onSurfaceDestroyed(int surface) {
        Log.d(this, "onSurfaceDestroyed: mSurfaceId=" + surface);
        if (mVideoCall == null) {
            return;
        }

        final boolean isChangingConfigurations =
                InCallPresenter.getInstance().isChangingConfigurations();
        Log.d(this, "onSurfaceDestroyed: isChangingConfigurations=" + isChangingConfigurations);

        if (surface == VideoCallFragment.SURFACE_PREVIEW) {
            if (!isChangingConfigurations) {
                enableCamera(mVideoCall, false);
            } else {
                Log.w(this, "onSurfaceDestroyed: Activity is being destroyed due "
                        + "to configuration changes. Not closing the camera.");
            }
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
        boolean isFullscreen = InCallPresenter.getInstance().toggleFullscreenMode();
        Log.v(this, "toggleFullScreen = " + isFullscreen);
    }

    /**
     * Handles incoming calls.
     *
     * @param oldState The old in call state.
     * @param newState The new in call state.
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
        Log.d(this, "onStateChange oldState" + oldState + " newState=" + newState +
                " isVideoMode=" + isVideoMode());

        if (newState == InCallPresenter.InCallState.NO_CALLS) {
            updateAudioMode(false);

            if (isVideoMode()) {
                exitVideoMode();
            }

            cleanupSurfaces();
        }

        // Determine the primary active call).
        Call primary = null;

        // Determine the call which is the focus of the user's attention.  In the case of an
        // incoming call waiting call, the primary call is still the active video call, however
        // the determination of whether we should be in fullscreen mode is based on the type of the
        // incoming call, not the active video call.
        Call currentCall = null;

        if (newState == InCallPresenter.InCallState.INCOMING) {
            // We don't want to replace active video call (primary call)
            // with a waiting call, since user may choose to ignore/decline the waiting call and
            // this should have no impact on current active video call, that is, we should not
            // change the camera or UI unless the waiting VT call becomes active.
            primary = callList.getActiveCall();
            currentCall = callList.getIncomingCall();
            if (!CallUtils.isActiveVideoCall(primary)) {
                primary = callList.getIncomingCall();
            }
        } else if (newState == InCallPresenter.InCallState.OUTGOING) {
            currentCall = primary = callList.getOutgoingCall();
        } else if (newState == InCallPresenter.InCallState.PENDING_OUTGOING) {
            currentCall = primary = callList.getPendingOutgoingCall();
        } else if (newState == InCallPresenter.InCallState.INCALL) {
            currentCall = primary = callList.getActiveCall();
        }

        final boolean primaryChanged = !Objects.equals(mPrimaryCall, primary);
        Log.d(this, "onStateChange primaryChanged=" + primaryChanged);
        Log.d(this, "onStateChange primary= " + primary);
        Log.d(this, "onStateChange mPrimaryCall = " + mPrimaryCall);
        if (primaryChanged) {
            onPrimaryCallChanged(primary);
        } else if (mPrimaryCall != null) {
            updateVideoCall(primary);
        }
        updateCallCache(primary);

        // If the call context changed, potentially exit fullscreen or schedule auto enter of
        // fullscreen mode.
        // If the current call context is no longer a video call, exit fullscreen mode.
        maybeExitFullscreen(currentCall);
        // Schedule auto-enter of fullscreen mode if the current call context is a video call
        maybeAutoEnterFullscreen(currentCall);
    }

    /**
     * Handles a change to the fullscreen mode of the app.
     *
     * @param isFullscreenMode {@code true} if the app is now fullscreen, {@code false} otherwise.
     */
    @Override
    public void onFullscreenModeChanged(boolean isFullscreenMode) {
        cancelAutoFullScreen();
    }

    private void checkForVideoStateChange(Call call) {
        final boolean isVideoCall = CallUtils.isVideoCall(call);
        final boolean hasVideoStateChanged = mCurrentVideoState != call.getVideoState();

        Log.d(this, "checkForVideoStateChange: isVideoCall= " + isVideoCall
                + " hasVideoStateChanged=" + hasVideoStateChanged + " isVideoMode="
                + isVideoMode() + " previousVideoState: " +
                VideoProfile.videoStateToString(mCurrentVideoState) + " newVideoState: "
                + VideoProfile.videoStateToString(call.getVideoState()));

        if (!hasVideoStateChanged) {
            return;
        }

        updateCameraSelection(call);

        if (isVideoCall) {
            enterVideoMode(call);
        } else if (isVideoMode()) {
            exitVideoMode();
        }
    }

    private void checkForCallStateChange(Call call) {
        final boolean isVideoCall = CallUtils.isVideoCall(call);
        final boolean hasCallStateChanged = mCurrentCallState != call.getState();

        Log.d(this, "checkForCallStateChange: isVideoCall= " + isVideoCall
                + " hasCallStateChanged=" +
                hasCallStateChanged + " isVideoMode=" + isVideoMode());

        if (!hasCallStateChanged) {
            return;
        }

        if (isVideoCall) {
            final InCallCameraManager cameraManager = InCallPresenter.getInstance().
                    getInCallCameraManager();

            String prevCameraId = cameraManager.getActiveCameraId();
            updateCameraSelection(call);
            String newCameraId = cameraManager.getActiveCameraId();

            if (!Objects.equals(prevCameraId, newCameraId) && CallUtils.isActiveVideoCall(call)) {
                enableCamera(call.getVideoCall(), true);
            }
        }

        // Make sure we hide or show the video UI if needed.
        showVideoUi(call.getVideoState(), call.getState());
    }

    private void cleanupSurfaces() {
        final VideoCallUi ui = getUi();
        if (ui == null) {
            Log.w(this, "cleanupSurfaces");
            return;
        }
        ui.cleanupSurfaces();
    }

    private void onPrimaryCallChanged(Call newPrimaryCall) {
        final boolean isVideoCall = CallUtils.isVideoCall(newPrimaryCall);
        final boolean isVideoMode = isVideoMode();

        Log.d(this, "onPrimaryCallChanged: isVideoCall=" + isVideoCall + " isVideoMode="
                + isVideoMode);

        if (!isVideoCall && isVideoMode) {
            // Terminate video mode if new primary call is not a video call
            // and we are currently in video mode.
            Log.d(this, "onPrimaryCallChanged: Exiting video mode...");
            exitVideoMode();
        } else if (isVideoCall) {
            Log.d(this, "onPrimaryCallChanged: Entering video mode...");

            updateCameraSelection(newPrimaryCall);
            enterVideoMode(newPrimaryCall);
        }
    }

    private boolean isVideoMode() {
        return mIsVideoMode;
    }

    private void updateCallCache(Call call) {
        if (call == null) {
            mCurrentVideoState = VideoProfile.STATE_AUDIO_ONLY;
            mCurrentCallState = Call.State.INVALID;
            mVideoCall = null;
            mPrimaryCall = null;
        } else {
            mCurrentVideoState = call.getVideoState();
            mVideoCall = call.getVideoCall();
            mCurrentCallState = call.getState();
            mPrimaryCall = call;
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
        Log.d(this, " onDetailsChanged call=" + call + " details=" + details + " mPrimaryCall="
                + mPrimaryCall);
        if (call == null) {
            return;
        }
        // If the details change is not for the currently active call no update is required.
        if (!call.equals(mPrimaryCall)) {
            Log.d(this," onDetailsChanged: Details not for current active call so returning. ");
            return;
        }

        updateVideoCall(call);

        updateCallCache(call);
    }

    private void updateVideoCall(Call call) {
        checkForVideoCallChange(call);
        checkForVideoStateChange(call);
        checkForCallStateChange(call);
        checkForOrientationAllowedChange(call);
    }

    private void checkForOrientationAllowedChange(Call call) {
        InCallPresenter.getInstance().setInCallAllowsOrientationChange(CallUtils.isVideoCall(call));
    }

    /**
     * Checks for a change to the video call and changes it if required.
     */
    private void checkForVideoCallChange(Call call) {
        final VideoCall videoCall = call.getTelecommCall().getVideoCall();
        Log.d(this, "checkForVideoCallChange: videoCall=" + videoCall + " mVideoCall="
                + mVideoCall);
        if (!Objects.equals(videoCall, mVideoCall)) {
            changeVideoCall(call);
        }
    }

    /**
     * Handles a change to the video call. Sets the surfaces on the previous call to null and sets
     * the surfaces on the new video call accordingly.
     *
     * @param videoCall The new video call.
     */
    private void changeVideoCall(Call call) {
        final VideoCall videoCall = call.getTelecommCall().getVideoCall();
        Log.d(this, "changeVideoCall to videoCall=" + videoCall + " mVideoCall=" + mVideoCall);
        // Null out the surfaces on the previous video call.
        if (mVideoCall != null) {
            // Log.d(this, "Null out the surfaces on the previous video call.");
            // mVideoCall.setDisplaySurface(null);
            // mVideoCall.setPreviewSurface(null);
        }

        final boolean hasChanged = mVideoCall == null && videoCall != null;

        mVideoCall = videoCall;
        if (mVideoCall == null || call == null) {
            Log.d(this, "Video call or primary call is null. Return");
            return;
        }

        if (CallUtils.isVideoCall(call) && hasChanged) {
            enterVideoMode(call);
        }
    }

    private static boolean isCameraRequired(int videoState) {
        return VideoProfile.isBidirectional(videoState) ||
                VideoProfile.isTransmissionEnabled(videoState);
    }

    private boolean isCameraRequired() {
        return mPrimaryCall != null ? isCameraRequired(mPrimaryCall.getVideoState()) : false;
    }

    /**
     * Enters video mode by showing the video surfaces and making other adjustments (eg. audio).
     * TODO(vt): Need to adjust size and orientation of preview surface here.
     */
    private void enterVideoMode(Call call) {
        VideoCall videoCall = call.getVideoCall();
        int newVideoState = call.getVideoState();

        Log.d(this, "enterVideoMode videoCall= " + videoCall + " videoState: " + newVideoState);
        VideoCallUi ui = getUi();
        if (ui == null) {
            Log.e(this, "Error VideoCallUi is null so returning");
            return;
        }

        showVideoUi(newVideoState, call.getState());

        // Communicate the current camera to telephony and make a request for the camera
        // capabilities.
        if (videoCall != null) {
            if (ui.isDisplayVideoSurfaceCreated()) {
                Log.d(this, "Calling setDisplaySurface with " + ui.getDisplayVideoSurface());
                videoCall.setDisplaySurface(ui.getDisplayVideoSurface());
            }

            final int rotation = ui.getCurrentRotation();
            if (rotation != VideoCallFragment.ORIENTATION_UNKNOWN) {
                videoCall.setDeviceOrientation(InCallPresenter.toRotationAngle(rotation));
            }

            enableCamera(videoCall, isCameraRequired(newVideoState));
        }
        mCurrentVideoState = newVideoState;
        updateAudioMode(true);

        mIsVideoMode = true;

        maybeAutoEnterFullscreen(call);
    }

    //TODO: Move this into Telecom. InCallUI should not be this close to audio functionality.
    private void updateAudioMode(boolean enableSpeaker) {
        if (!isSpeakerEnabledForVideoCalls()) {
            Log.d(this, "Speaker is disabled. Can't update audio mode");
            return;
        }

        final TelecomAdapter telecomAdapter = TelecomAdapter.getInstance();
        final boolean isPrevAudioModeValid =
            sPrevVideoAudioMode != AudioModeProvider.AUDIO_MODE_INVALID;

        Log.d(this, "Is previous audio mode valid = " + isPrevAudioModeValid + " enableSpeaker is "
                + enableSpeaker);

        // Set audio mode to previous mode if enableSpeaker is false.
        if (isPrevAudioModeValid && !enableSpeaker) {
            telecomAdapter.setAudioRoute(sPrevVideoAudioMode);
            sPrevVideoAudioMode = AudioModeProvider.AUDIO_MODE_INVALID;
            return;
        }

        int currentAudioMode = AudioModeProvider.getInstance().getAudioMode();

        // Set audio mode to speaker if enableSpeaker is true and bluetooth or headset are not
        // connected and it's a video call.
        if (!isAudioRouteEnabled(currentAudioMode,
            CallAudioState.ROUTE_BLUETOOTH | CallAudioState.ROUTE_WIRED_HEADSET) &&
            !isPrevAudioModeValid && enableSpeaker && CallUtils.isVideoCall(mPrimaryCall)) {
            sPrevVideoAudioMode = currentAudioMode;

            Log.d(this, "Routing audio to speaker");
            telecomAdapter.setAudioRoute(CallAudioState.ROUTE_SPEAKER);
        }
    }

    private static boolean isSpeakerEnabledForVideoCalls() {
        // TODO: Make this a carrier configurable setting. For now this is always true. b/20090407
        return true;
    }

    private void enableCamera(VideoCall videoCall, boolean isCameraRequired) {
        Log.d(this, "enableCamera: VideoCall=" + videoCall + " enabling=" + isCameraRequired);
        if (videoCall == null) {
            Log.w(this, "enableCamera: VideoCall is null.");
            return;
        }

        if (isCameraRequired) {
            InCallCameraManager cameraManager = InCallPresenter.getInstance().
                    getInCallCameraManager();
            videoCall.setCamera(cameraManager.getActiveCameraId());
            mPreviewSurfaceState = PreviewSurfaceState.CAMERA_SET;

            videoCall.requestCameraCapabilities();
        } else {
            mPreviewSurfaceState = PreviewSurfaceState.NONE;
            videoCall.setCamera(null);
        }
    }

    /**
     * Exits video mode by hiding the video surfaces and making other adjustments (eg. audio).
     */
    private void exitVideoMode() {
        Log.d(this, "exitVideoMode");

        showVideoUi(VideoProfile.STATE_AUDIO_ONLY, Call.State.ACTIVE);
        enableCamera(mVideoCall, false);
        InCallPresenter.getInstance().setFullScreen(false);

        mIsVideoMode = false;
    }

    /**
     * Based on the current video state and call state, show or hide the incoming and
     * outgoing video surfaces.  The outgoing video surface is shown any time video is transmitting.
     * The incoming video surface is shown whenever the video is un-paused and active.
     *
     * @param videoState The video state.
     * @param callState The call state.
     */
    private void showVideoUi(int videoState, int callState) {
        VideoCallUi ui = getUi();
        if (ui == null) {
            Log.e(this, "showVideoUi, VideoCallUi is null returning");
            return;
        }
        boolean isPaused = VideoProfile.isPaused(videoState);
        boolean isCallActive = callState == Call.State.ACTIVE;
        if (VideoProfile.isBidirectional(videoState)) {
            ui.showVideoViews(true, !isPaused && isCallActive);
        } else if (VideoProfile.isTransmissionEnabled(videoState)) {
            ui.showVideoViews(true, false);
        } else if (VideoProfile.isReceptionEnabled(videoState)) {
            ui.showVideoViews(false, !isPaused && isCallActive);
            loadProfilePhotoAsync();
        } else {
            ui.hideVideoUi();
        }

        InCallPresenter.getInstance().enableScreenTimeout(
                VideoProfile.isAudioOnly(videoState));
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
        Log.d(this, "onUpdatePeerDimensions: width= " + width + " height= " + height);
        VideoCallUi ui = getUi();
        if (ui == null) {
            Log.e(this, "VideoCallUi is null. Bail out");
            return;
        }
        if (!call.equals(mPrimaryCall)) {
            Log.e(this, "Current call is not equal to primary call. Bail out");
            return;
        }

        // Change size of display surface to match the peer aspect ratio
        if (width > 0 && height > 0) {
            setDisplayVideoSize(width, height);
        }
    }

    /**
     * Handles any video quality changes in the call.
     *
     * @param call The call which experienced a video quality change.
     * @param videoQuality The new video call quality.
     */
    @Override
    public void onVideoQualityChanged(Call call, int videoQuality) {
        // No-op
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
        Log.d(this, "onCameraDimensionsChange call=" + call + " width=" + width + " height="
                + height);
        VideoCallUi ui = getUi();
        if (ui == null) {
            Log.e(this, "onCameraDimensionsChange ui is null");
            return;
        }

        if (!call.equals(mPrimaryCall)) {
            Log.e(this, "Call is not primary call");
            return;
        }

        mPreviewSurfaceState = PreviewSurfaceState.CAPABILITIES_RECEIVED;
        changePreviewDimensions(width, height);

        // Check if the preview surface is ready yet; if it is, set it on the {@code VideoCall}.
        // If it not yet ready, it will be set when when creation completes.
        if (ui.isPreviewVideoSurfaceCreated()) {
            mPreviewSurfaceState = PreviewSurfaceState.SURFACE_SET;
            mVideoCall.setPreviewSurface(ui.getPreviewVideoSurface());
        }
    }

    /**
     * Changes the dimensions of the preview surface.
     *
     * @param width The new width.
     * @param height The new height.
     */
    private void changePreviewDimensions(int width, int height) {
        VideoCallUi ui = getUi();
        if (ui == null) {
            return;
        }

        // Resize the surface used to display the preview video
        ui.setPreviewSurfaceSize(width, height);

        // Configure the preview surface to the correct aspect ratio.
        float aspectRatio = 1.0f;
        if (width > 0 && height > 0) {
            aspectRatio = (float) width / (float) height;
        }

        // Resize the textureview housing the preview video and rotate it appropriately based on
        // the device orientation
        setPreviewSize(mDeviceOrientation, aspectRatio);
    }

    /**
     * Called when call session event is raised.
     *
     * @param event The call session event.
     */
    @Override
    public void onCallSessionEvent(int event) {
        StringBuilder sb = new StringBuilder();
        sb.append("onCallSessionEvent = ");

        switch (event) {
            case Connection.VideoProvider.SESSION_EVENT_RX_PAUSE:
                sb.append("rx_pause");
                break;
            case Connection.VideoProvider.SESSION_EVENT_RX_RESUME:
                sb.append("rx_resume");
                break;
            case Connection.VideoProvider.SESSION_EVENT_CAMERA_FAILURE:
                sb.append("camera_failure");
                break;
            case Connection.VideoProvider.SESSION_EVENT_CAMERA_READY:
                sb.append("camera_ready");
                break;
            default:
                sb.append("unknown event = ");
                sb.append(event);
                break;
        }
        Log.d(this, sb.toString());
    }

    /**
     * Handles a change to the call data usage
     *
     * @param dataUsage call data usage value
     */
    @Override
    public void onCallDataUsageChange(long dataUsage) {
        Log.d(this, "onCallDataUsageChange dataUsage=" + dataUsage);
    }

    /**
     * Handles changes to the device orientation.
     *
     * @param orientation The device orientation (one of: {@link Surface#ROTATION_0},
     *      {@link Surface#ROTATION_90}, {@link Surface#ROTATION_180},
     *      {@link Surface#ROTATION_270}).
     */
    @Override
    public void onDeviceOrientationChanged(int orientation) {
        mDeviceOrientation = orientation;
        Point previewDimensions = getUi().getPreviewSize();
        if (previewDimensions == null) {
            return;
        }
        Log.d(this, "onDeviceOrientationChanged: orientation=" + orientation + " size: "
                + previewDimensions);
        changePreviewDimensions(previewDimensions.x, previewDimensions.y);
    }

    @Override
    public void onUpgradeToVideoRequest(Call call, int videoState) {
        Log.d(this, "onUpgradeToVideoRequest call = " + call + " new video state = " + videoState);
        if (mPrimaryCall == null || !Call.areSame(mPrimaryCall, call)) {
            Log.w(this, "UpgradeToVideoRequest received for non-primary call");
        }

        if (call == null) {
            return;
        }

        call.setSessionModificationTo(videoState);
    }

    @Override
    public void onUpgradeToVideoSuccess(Call call) {
        Log.d(this, "onUpgradeToVideoSuccess call=" + call);
        if (mPrimaryCall == null || !Call.areSame(mPrimaryCall, call)) {
            Log.w(this, "UpgradeToVideoSuccess received for non-primary call");
        }

        if (call == null) {
            return;
        }
    }

    @Override
    public void onUpgradeToVideoFail(int status, Call call) {
        Log.d(this, "onUpgradeToVideoFail call=" + call);
        if (mPrimaryCall == null || !Call.areSame(mPrimaryCall, call)) {
            Log.w(this, "UpgradeToVideoFail received for non-primary call");
        }

        if (call == null) {
            return;
        }
    }

    @Override
    public void onDowngradeToAudio(Call call) {
        call.setSessionModificationState(Call.SessionModificationState.NO_REQUEST);
        // exit video mode
        exitVideoMode();
    }

    /**
     * Sets the preview surface size based on the current device orientation.
     *
     * @param orientation The device orientation (one of: {@link Surface#ROTATION_0},
     *      {@link Surface#ROTATION_90}, {@link Surface#ROTATION_180},
     *      {@link Surface#ROTATION_270}).
     * @param aspectRatio The aspect ratio of the camera (width / height).
     */
    private void setPreviewSize(int orientation, float aspectRatio) {
        VideoCallUi ui = getUi();
        if (ui == null) {
            return;
        }

        int height;
        int width;

        if (orientation == Surface.ROTATION_90 || orientation == Surface.ROTATION_270) {
            // Landscape or reverse landscape orientation.
            width = (int) (mMinimumVideoDimension * aspectRatio);
            height = (int) mMinimumVideoDimension;
        } else {
            // Portrait or reverse portrait orientation.
            width = (int) mMinimumVideoDimension;
            height = (int) (mMinimumVideoDimension * aspectRatio);
        }
        ui.setPreviewSize(width, height);
    }

    /**
     * Sets the display video surface size based on peer width and height
     *
     * @param width peer width
     * @param height peer height
     */
    private void setDisplayVideoSize(int width, int height) {
        Log.d(this, "setDisplayVideoSize:Received peer width=" + width + " peer height=" + height);
        VideoCallUi ui = getUi();
        if (ui == null) {
            return;
        }

        // Get current display size
        Point size = ui.getScreenSize();
        Log.d("VideoCallPresenter", "setDisplayVideoSize: windowmgr width=" + size.x
                + " windowmgr height=" + size.y);
        if (size.y * width > size.x * height) {
            // current display height is too much. Correct it
            size.y = (int) (size.x * height / width);
        } else if (size.y * width < size.x * height) {
            // current display width is too much. Correct it
            size.x = (int) (size.y * width / height);
        }
        ui.setDisplayVideoSize(size.x, size.y);
    }

    /**
     * Exits fullscreen mode if the current call context has changed to a non-video call.
     *
     * @param call The call.
     */
    protected void maybeExitFullscreen(Call call) {
        if (call == null) {
            return;
        }

        if (!CallUtils.isVideoCall(call) || call.getState() == Call.State.INCOMING) {
            InCallPresenter.getInstance().setFullScreen(false);
        }
    }

    /**
     * Schedules auto-entering of fullscreen mode.
     * Will not enter full screen mode if any of the following conditions are met:
     * 1. No call
     * 2. Call is not active
     * 3. Call is not video call
     * 4. Already in fullscreen mode
     *
     * @param call The current call.
     */
    protected void maybeAutoEnterFullscreen(Call call) {
        if (!mIsAutoFullscreenEnabled) {
            return;
        }

        if (call == null || (
                call != null && (call.getState() != Call.State.ACTIVE ||
                        !CallUtils.isVideoCall(call)) ||
                        InCallPresenter.getInstance().isFullscreen())) {
            // Ensure any previously scheduled attempt to enter fullscreen is cancelled.
            cancelAutoFullScreen();
            return;
        }

        if (mAutoFullScreenPending) {
            Log.v(this, "maybeAutoEnterFullscreen : already pending.");
            return;
        }
        Log.v(this, "maybeAutoEnterFullscreen : scheduled");
        mAutoFullScreenPending = true;
        mHandler.postDelayed(mAutoFullscreenRunnable, mAutoFullscreenTimeoutMillis);
    }

    /**
     * Cancels pending auto fullscreen mode.
     */
    public void cancelAutoFullScreen() {
        if (!mAutoFullScreenPending) {
            Log.v(this, "cancelAutoFullScreen : none pending.");
            return;
        }
        Log.v(this, "cancelAutoFullScreen : cancelling pending");
        mAutoFullScreenPending = false;
    }

    private static boolean isAudioRouteEnabled(int audioRoute, int audioRouteMask) {
        return ((audioRoute & audioRouteMask) != 0);
    }

    private static void updateCameraSelection(Call call) {
        Log.d(TAG, "updateCameraSelection: call=" + call);
        Log.d(TAG, "updateCameraSelection: call=" + toSimpleString(call));

        final Call activeCall = CallList.getInstance().getActiveCall();
        int cameraDir = Call.VideoSettings.CAMERA_DIRECTION_UNKNOWN;

        // this function should never be called with null call object, however if it happens we
        // should handle it gracefully.
        if (call == null) {
            cameraDir = Call.VideoSettings.CAMERA_DIRECTION_UNKNOWN;
            com.android.incallui.Log.e(TAG, "updateCameraSelection: Call object is null."
                    + " Setting camera direction to default value (CAMERA_DIRECTION_UNKNOWN)");
        }

        // Clear camera direction if this is not a video call.
        else if (CallUtils.isAudioCall(call)) {
            cameraDir = Call.VideoSettings.CAMERA_DIRECTION_UNKNOWN;
            call.getVideoSettings().setCameraDir(cameraDir);
        }

        // If this is a waiting video call, default to active call's camera,
        // since we don't want to change the current camera for waiting call
        // without user's permission.
        else if (CallUtils.isVideoCall(activeCall) && CallUtils.isIncomingVideoCall(call)) {
            cameraDir = activeCall.getVideoSettings().getCameraDir();
        }

        // Infer the camera direction from the video state and store it,
        // if this is an outgoing video call.
        else if (CallUtils.isOutgoingVideoCall(call) && !isCameraDirectionSet(call) ) {
            cameraDir = toCameraDirection(call.getVideoState());
            call.getVideoSettings().setCameraDir(cameraDir);
        }

        // Use the stored camera dir if this is an outgoing video call for which camera direction
        // is set.
        else if (CallUtils.isOutgoingVideoCall(call)) {
            cameraDir = call.getVideoSettings().getCameraDir();
        }

        // Infer the camera direction from the video state and store it,
        // if this is an active video call and camera direction is not set.
        else if (CallUtils.isActiveVideoCall(call) && !isCameraDirectionSet(call)) {
            cameraDir = toCameraDirection(call.getVideoState());
            call.getVideoSettings().setCameraDir(cameraDir);
        }

        // Use the stored camera dir if this is an active video call for which camera direction
        // is set.
        else if (CallUtils.isActiveVideoCall(call)) {
            cameraDir = call.getVideoSettings().getCameraDir();
        }

        // For all other cases infer the camera direction but don't store it in the call object.
        else {
            cameraDir = toCameraDirection(call.getVideoState());
        }

        com.android.incallui.Log.d(TAG, "updateCameraSelection: Setting camera direction to " +
                cameraDir + " Call=" + call);
        final InCallCameraManager cameraManager = InCallPresenter.getInstance().
                getInCallCameraManager();
        cameraManager.setUseFrontFacingCamera(cameraDir ==
                Call.VideoSettings.CAMERA_DIRECTION_FRONT_FACING);
    }

    private static int toCameraDirection(int videoState) {
        return VideoProfile.isTransmissionEnabled(videoState) &&
                !VideoProfile.isBidirectional(videoState)
                ? Call.VideoSettings.CAMERA_DIRECTION_BACK_FACING
                : Call.VideoSettings.CAMERA_DIRECTION_FRONT_FACING;
    }

    private static boolean isCameraDirectionSet(Call call) {
        return CallUtils.isVideoCall(call) && call.getVideoSettings().getCameraDir()
                    != Call.VideoSettings.CAMERA_DIRECTION_UNKNOWN;
    }

    private static String toSimpleString(Call call) {
        return call == null ? null : call.toSimpleString();
    }

    /**
     * Starts an asynchronous load of the user's profile photo.
     */
    public void loadProfilePhotoAsync() {
        final VideoCallUi ui = getUi();
        if (ui == null) {
            return;
        }

        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            /**
             * Performs asynchronous load of the user profile information.
             *
             * @param params The parameters of the task.
             *
             * @return {@code null}.
             */
            @Override
            protected Void doInBackground(Void... params) {
                if (mProfileInfo == null) {
                    // Try and read the photo URI from the local profile.
                    mProfileInfo = new ContactInfoCache.ContactCacheEntry();
                    final Cursor cursor = mContext.getContentResolver().query(
                            ContactsContract.Profile.CONTENT_URI, new String[]{
                                    ContactsContract.CommonDataKinds.Phone._ID,
                                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            }, null, null, null);
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                mProfileInfo.lookupKey = cursor.getString(cursor.getColumnIndex(
                                        ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY));
                                String photoUri = cursor.getString(cursor.getColumnIndex(
                                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI));
                                mProfileInfo.displayPhotoUri = photoUri == null ? null
                                        : Uri.parse(photoUri);
                                mProfileInfo.name = cursor.getString(cursor.getColumnIndex(
                                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                // If user profile information was found, issue an async request to load the user's
                // profile photo.
                if (mProfileInfo != null) {
                    if (mContactPhotoManager == null) {
                        mContactPhotoManager = ContactPhotoManager.getInstance(mContext);
                    }
                    ContactPhotoManager.DefaultImageRequest imageRequest = (mProfileInfo != null)
                            ? null :
                            new ContactPhotoManager.DefaultImageRequest(mProfileInfo.name,
                                    mProfileInfo.lookupKey, false /* isCircularPhoto */);

                    ImageView photoView = ui.getPreviewPhotoView();
                    if (photoView == null) {
                        return;
                    }
                    mContactPhotoManager.loadDirectoryPhoto(photoView,
                                    mProfileInfo.displayPhotoUri,
                                    false /* darkTheme */, false /* isCircular */, imageRequest);
                }
            }
        };

        task.execute();
    }

    /**
     * Defines the VideoCallUI interactions.
     */
    public interface VideoCallUi extends Ui {
        void showVideoViews(boolean showPreview, boolean showIncoming);
        void hideVideoUi();
        boolean isDisplayVideoSurfaceCreated();
        boolean isPreviewVideoSurfaceCreated();
        Surface getDisplayVideoSurface();
        Surface getPreviewVideoSurface();
        int getCurrentRotation();
        void setPreviewSize(int width, int height);
        void setPreviewSurfaceSize(int width, int height);
        void setDisplayVideoSize(int width, int height);
        Point getScreenSize();
        Point getPreviewSize();
        void cleanupSurfaces();
        ImageView getPreviewPhotoView();
    }
}
