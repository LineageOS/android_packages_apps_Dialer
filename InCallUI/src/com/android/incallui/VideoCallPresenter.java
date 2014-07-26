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

import com.android.incallui.CallVideoClientNotifier.SurfaceChangeListener;
import com.android.incallui.CallVideoClientNotifier.VideoEventListener;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;

import android.content.Context;
import android.telecomm.InCallService.VideoCall;
import android.view.Surface;

import java.util.Objects;

/**
 * Logic related to the {@link VideoCallFragment} and for managing changes to the video calling
 * surfaces based on other user interface events and incoming events from the
 * {@class CallVideoClient}.
 */
public class VideoCallPresenter extends Presenter<VideoCallPresenter.VideoCallUi>  implements
        IncomingCallListener, InCallStateListener,
        InCallDetailsListener, SurfaceChangeListener, VideoEventListener {

    /**
     * The current context.
     */
    private Context mContext;

    /**
     * The call the video surfaces are currently related to
     */
    private Call mCall;

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
     * Initializes the presenter.
     *
     * @param context The current context.
     */
    public void init(Context context) {
        mContext = Preconditions.checkNotNull(context);
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

        // Register for surface and video events from {@link InCallVideoProvider}s.
        CallVideoClientNotifier.getInstance().addSurfaceChangeListener(this);
        CallVideoClientNotifier.getInstance().addVideoEventListener(this);

        mIsVideoCall = false;
    }

    /**
     * Called when the user interface is no longer ready to be used.
     *
     * @param ui The Ui implementation that is no longer ready to be used.
     */
    public void unUiUnready(VideoCallUi ui) {
        super.onUiUnready(ui);

        InCallPresenter.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);
        CallVideoClientNotifier.getInstance().removeSurfaceChangeListener(this);
        CallVideoClientNotifier.getInstance().removeVideoEventListener(this);
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

        if (surface == VideoCallFragment.SURFACE_DISPLAY) {
            mVideoCall.setDisplaySurface(ui.getDisplayVideoSurface());
        } else if (surface == VideoCallFragment.SURFACE_PREVIEW) {
            mVideoCall.setPreviewSurface(ui.getPreviewVideoSurface());
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
     * Handles incoming calls.
     *
     * @param state The in call state.
     * @param call The call.
     */
    @Override
    public void onIncomingCall(InCallPresenter.InCallState state, Call call) {
        // same logic should happen as with onStateChange()
        onStateChange(state, CallList.getInstance());
    }

    /**
     * Handles state changes (including incoming calls)
     *
     * @param state The in call state.
     * @param callList The call list.
     */
    @Override
    public void onStateChange(InCallPresenter.InCallState state, CallList callList) {
        Call call = null;
        if (state == InCallPresenter.InCallState.INCOMING) {
            call = callList.getIncomingCall();
        } else if (state == InCallPresenter.InCallState.OUTGOING) {
            call = callList.getOutgoingCall();
        }

        if (call == null || getUi() == null) {
            return;
        }

        Log.d(this, "onStateChange "+call);

        final boolean callChanged = !Objects.equals(mCall, call);

        // If the call changed track it now.
        if (callChanged) {
            mCall = call;
        }

        checkForCallVideoProviderChange();
        checkForVideoStateChange();
    }

    /**
     * Handles changes to the details of the call.  The {@link VideoCallPresenter} is interested in
     * changes to the video state.
     *
     * @param call The call for which the details changed.
     * @param details The new call details.
     */
    @Override
    public void onDetailsChanged(Call call, android.telecomm.Call.Details details) {
        Log.d(this, "onDetailsChanged "+call);

        // If the details change is not for the currently active call no update is required.
        if (!call.equals(mCall)) {
            return;
        }

        checkForVideoStateChange();
    }

    /**
     * Checks for a change to the call video provider and changes it if required.
     */
    private void checkForCallVideoProviderChange() {
        VideoCall videoCall = mCall.getTelecommCall().getVideoCall();
        if (!Objects.equals(videoCall, mVideoCall)) {
            changeVideoCall(videoCall);
        }
    }

    /**
     * Checks to see if the current video state has changed and updates the UI if required.
     */
    private void checkForVideoStateChange() {
        boolean newVideoState = mCall.isVideoCall();

        // Check if video state changed
        if (mIsVideoCall != newVideoState) {
            mIsVideoCall = newVideoState;

            if (mIsVideoCall) {
                enterVideoState();
            } else {
                exitVideoState();
            }
        }
    }

    /**
     * Handles a change to the video call.  Sets the surfaces on the previous call to null and sets
     * the surfaces on the new provider accordingly.
     *
     * @param videoCall The new video call.
     */
    private void changeVideoCall(VideoCall videoCall) {
        Log.d(this, "changeCallVideoProvider");

        // Null out the surfaces on the previous provider
        if (mVideoCall != null) {
            mVideoCall.setDisplaySurface(null);
            mVideoCall.setPreviewSurface(null);
        }

        mVideoCall = videoCall;
        setSurfaces();

    }

    /**
     * Enters video mode by showing the video surfaces.
     * TODO(vt): Need to adjust size and orientation of preview surface here.
     */
    private void enterVideoState() {
        Log.d(this, "enterVideoState");
        VideoCallUi ui = getUi();
        if (ui == null) {
            return;
        }

        ui.showVideoUi(true);
    }

    /**
     * Sets the surfaces on the specified {@link Call.VideoCall}.
     */
    private void setSurfaces() {
        Log.d(this, "setSurfaces");
        VideoCallUi ui = getUi();
        if (ui == null || mVideoCall == null) {
            return;
        }

        if (getUi().isDisplayVideoSurfaceCreated()) {
            mVideoCall.setDisplaySurface(ui.getDisplayVideoSurface());
        }

        if (getUi().isPreviewVideoSurfaceCreated()) {
            mVideoCall.setPreviewSurface(ui.getPreviewVideoSurface());
        }
    }

    /**
     * Exits video mode by hiding the video surfaces.
     */
    private void exitVideoState() {
        Log.d(this, "exitVideoState");
        VideoCallUi ui = getUi();
        if (ui == null) {
            return;
        }

        ui.showVideoUi(false);
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
        if (!call.equals(mCall)) {
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
        if (!call.equals(mCall)) {
            return;
        }

        // TODO(vt): Change display surface aspect ratio.
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
    }
}
