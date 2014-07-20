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

import com.android.internal.util.Preconditions;

import android.content.Context;
import android.telecomm.RemoteCallVideoProvider;
import android.view.Surface;

import java.util.Objects;

/**
 * Logic related to the {@link VideoCallFragment} and for managing changes to the video calling
 * surfaces based on other user interface events and incoming events from the
 * {@class CallVideoClient}.
 */
public class VideoCallPresenter extends Presenter<VideoCallPresenter.VideoCallUi>  implements
        InCallPresenter.IncomingCallListener, InCallPresenter.InCallStateListener {

    /**
     * The current context.
     */
    private Context mContext;

    /**
     * The call the video surfaces are currently related to
     */
    private Call mCall;

    /**
     * The {@link RemoteCallVideoProvider} used to inform the video telephony layer of changes
     * to the video surfaces.
     */
    private RemoteCallVideoProvider mCallVideoProvider;

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
        mIsVideoCall = false;
    }

    /**
     * @return The {@link RemoteCallVideoProvider}.
     */
    private RemoteCallVideoProvider getCallVideoProvider() {
        return mCallVideoProvider;
    }

    /**
     * Handles the creation of a surface in the {@link VideoCallFragment}.
     *
     * @param surface The surface which was created.
     */
    public void onSurfaceCreated(int surface) {
        final VideoCallUi ui = getUi();
        final RemoteCallVideoProvider callVideoProvider = getCallVideoProvider();

        if (ui == null || callVideoProvider == null) {
            return;
        }

        if (surface == VideoCallFragment.SURFACE_DISPLAY) {
            mCallVideoProvider.setDisplaySurface(ui.getDisplayVideoSurface());
        } else if (surface == VideoCallFragment.SURFACE_PREVIEW) {
            mCallVideoProvider.setPreviewSurface(ui.getPreviewVideoSurface());
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
        final RemoteCallVideoProvider callVideoProvider = getCallVideoProvider();

        if (ui == null || callVideoProvider == null) {
            return;
        }

        if (surface == VideoCallFragment.SURFACE_DISPLAY) {
            mCallVideoProvider.setDisplaySurface(null);
        } else if (surface == VideoCallFragment.SURFACE_PREVIEW) {
            mCallVideoProvider.setPreviewSurface(null);
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
        final boolean callChanged = !Objects.equals(mCall, call);

        // If the call changed track it now.
        if (callChanged) {
            mCall = call;
        }

        RemoteCallVideoProvider callVideoProvider =
                mCall.getTelecommCall().getCallVideoProvider();
        if (callVideoProvider != mCallVideoProvider) {
            changeCallVideoProvider(callVideoProvider);
        }

        boolean newVideoState = call.isVideoCall();

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
     * Handles a change to the call video provider.  Sets the surfaces on the previous provider
     * to null and sets the surfaces on the new provider accordingly.
     *
     * @param callVideoProvider The new call video provider.
     */
    private void changeCallVideoProvider(RemoteCallVideoProvider callVideoProvider) {
        // Null out the surfaces on the previous provider
        if (mCallVideoProvider != null) {
            mCallVideoProvider.setDisplaySurface(null);
            mCallVideoProvider.setPreviewSurface(null);
        }

        mCallVideoProvider = callVideoProvider;
        setSurfaces();

    }

    /**
     * Enters video mode by showing the video surfaces.
     * TODO(vt): Need to adjust size and orientation of preview surface here.
     */
    private void enterVideoState() {
        VideoCallUi ui = getUi();
        if (ui == null) {
            return;
        }

        ui.showVideoUi(true);
    }

    /**
     * Sets the surfaces on the specified {@link RemoteCallVideoProvider}.
     */
    private void setSurfaces() {
        VideoCallUi ui = getUi();
        if (ui == null || mCallVideoProvider == null) {
            return;
        }

        if (getUi().isDisplayVideoSurfaceCreated()) {
            mCallVideoProvider.setDisplaySurface(ui.getDisplayVideoSurface());
        }

        if (getUi().isPreviewVideoSurfaceCreated()) {
            mCallVideoProvider.setPreviewSurface(ui.getPreviewVideoSurface());
        }
    }

    /**
     * Exits video mode by hiding the video surfaces.
     */
    private void exitVideoState() {
        VideoCallUi ui = getUi();
        if (ui == null) {
            return;
        }

        ui.showVideoUi(false);
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
