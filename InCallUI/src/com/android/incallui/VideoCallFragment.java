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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;

import java.util.Set;

/**
 * Fragment containing video calling surfaces.
 */
public class VideoCallFragment extends BaseFragment<VideoCallPresenter,
        VideoCallPresenter.VideoCallUi> implements VideoCallPresenter.VideoCallUi {

    public static final int SURFACE_DISPLAY = 1;
    public static final int SURFACE_PREVIEW = 2;

    /**
     * Listener interface used by classes interested in changed to the video telephony surfaces
     * in the {@link CallCardFragment}.
     */
    public interface VideoCallSurfaceListener {
        void onSurfaceCreated(int surface);
        void onSurfaceDestroyed(int surface);
        void onSurfaceChanged(int surface, int format, int width, int height);
    }

    /**
     * {@link ViewStub} holding the video call surfaces.  This is the parent for the
     * {@link VideoCallFragment}.  Used to ensure that the video surfaces are only inflated when
     * required.
     */
    private ViewStub mVideoViewsStub;

    /**
     * Inflated view containing the video call surfaces represented by the {@link ViewStub}.
     */
    private View mVideoViews;

    /**
     * The display video {@link SurfaceView}.  Incoming video from the remote party of the video
     * call is displayed here.
     */
    private SurfaceView mDisplayVideoSurface;

    /**
     * The surface holder for the display surface.  Provides access to the underlying
     * {@link Surface} in the {@link SurfaceView} and allows listening to surface related events.
     */
    private SurfaceHolder mDisplayVideoSurfaceHolder;

    /**
     * Determines if the display surface has been created or not.
     */
    private boolean mDisplayVideoSurfaceCreated;

    /**
     * The preview video {@link SurfaceView}.  A preview of the outgoing video to the remote party
     * of the video call is displayed here.
     */
    private SurfaceView mPreviewVideoSurface;

    /**
     * The surface holder for the preview surface.  Provides access to the underlying
     * {@link Surface} in the {@link SurfaceView} and allows listening to surface related events.
     */
    private SurfaceHolder mPreviewVideoSurfaceHolder;

    /**
     * Determines if the preview surface has been created or not.
     */
    private boolean mPreviewVideoSurfaceCreated;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /**
     * Handles creation of the activity and initialization of the presenter.
     *
     * @param savedInstanceState The saved instance state.
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getPresenter().init(getActivity());
    }

    /**
     * Handles creation of the fragment view.
     *
     * @param inflater The inflater.
     * @param container The view group containing the fragment.
     * @param savedInstanceState The saved instance state.
     * @return
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        return inflater.inflate(R.layout.video_call_fragment, container, false);
    }

    /**
     * After creation of the fragment view, retrieves the required views.
     *
     * @param view The fragment view.
     * @param savedInstanceState The saved instance state.
     */
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVideoViewsStub = (ViewStub) view.findViewById(R.id.videoCallViewsStub);
    }

    /**
     * Creates the presenter for the {@link VideoCallFragment}.
     * @return The presenter instance.
     */
    @Override
    public VideoCallPresenter createPresenter() {
        return new VideoCallPresenter();
    }

    /**
     * @return The user interface for the presenter, which is this fragment.
     */
    @Override
    VideoCallPresenter.VideoCallUi getUi() {
        return this;
    }

    /**
     * SurfaceHolder callback used to track lifecycle changes to the surfaces.
     */
    private SurfaceHolder.Callback mSurfaceHolderCallBack = new SurfaceHolder.Callback() {
        /**
         * Called immediately after the surface is first created.
         *
         * @param holder The surface holder.
         */
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            int surfaceId = getSurfaceId(holder);

            if (surfaceId == SURFACE_DISPLAY) {
                mDisplayVideoSurfaceCreated = true;
            } else {
                mPreviewVideoSurfaceCreated = true;
            }

            getPresenter().onSurfaceCreated(surfaceId);
        }

        /**
         * Called immediately after any structural changes (format or size) have been made to the
         * surface.
         *
         * @param holder The surface holder.
         * @param format
         * @param width
         * @param height
         */
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            getPresenter().onSurfaceChanged(getSurfaceId(holder), format, width, height);
        }

        /**
         * Called immediately before a surface is being destroyed.
         *
         * @param holder The surface holder.
         */
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            int surfaceId = getSurfaceId(holder);

            if (surfaceId == SURFACE_DISPLAY) {
                mDisplayVideoSurfaceCreated = false;
            } else {
                mPreviewVideoSurfaceCreated = false;
            }

            getPresenter().onSurfaceDestroyed(surfaceId);
        }

        /**
         * Determines the surface ID for a specified surface.
         *
         * @param holder The surface holder.
         * @return The surface ID.
         */
        private int getSurfaceId(SurfaceHolder holder) {
            int surface;
            if (holder == mDisplayVideoSurface.getHolder()) {
                surface = SURFACE_DISPLAY;
            } else {
                surface = SURFACE_PREVIEW;
            }
            return surface;
        }
    };

    /**
     * Toggles visibility of the video UI.
     *
     * @param show {@code True} if the video surfaces should be shown.
     */
    @Override
    public void showVideoUi(boolean show) {
        getView().setVisibility(show ? View.VISIBLE : View.GONE);

        if (show) {
            inflateVideoCallViews();
        }

        if (mVideoViews != null ) {
            int newVisibility = show ? View.VISIBLE : View.GONE;
            mVideoViews.setVisibility(newVisibility);
            mDisplayVideoSurface.setVisibility(newVisibility);
            mPreviewVideoSurface.setVisibility(newVisibility);
            mPreviewVideoSurface.setZOrderOnTop(show);
        }
    }

    /**
     * @return {@code True} if the display video surface has been created.
     */
    @Override
    public boolean isDisplayVideoSurfaceCreated() {
        return mDisplayVideoSurfaceCreated;
    }

    /**
     * @return {@code True} if the preview video surface has been created.
     */
    @Override
    public boolean isPreviewVideoSurfaceCreated() {
        return mPreviewVideoSurfaceCreated;
    }

    /**
     * {@link android.view.Surface} on which incoming video for a video call is displayed.
     * {@code Null} until the video views {@link android.view.ViewStub} is inflated.
     */
    public Surface getDisplayVideoSurface() {
        if (mDisplayVideoSurfaceHolder != null) {
            return mDisplayVideoSurfaceHolder.getSurface();
        }
        return null;
    }

    /**
     * {@link android.view.Surface} on which a preview of the outgoing video for a video call is
     * displayed.  {@code Null} until the video views {@link android.view.ViewStub} is inflated.
     */
    public Surface getPreviewVideoSurface() {
        if (mPreviewVideoSurfaceHolder != null) {
            return mPreviewVideoSurfaceHolder.getSurface();
        }
        return null;
    }

    /**
     * Inflates the {@link ViewStub} containing the incoming and outgoing video surfaces and sets
     * up a callback to listen for lifecycle changes to the surface.
     */
    private void inflateVideoCallViews() {
        if (mDisplayVideoSurface == null && mPreviewVideoSurface == null && mVideoViews == null ) {
            mVideoViews = mVideoViewsStub.inflate();

            if (mVideoViews != null) {
                mDisplayVideoSurface = (SurfaceView) mVideoViews.findViewById(R.id.incomingVideo);
                mDisplayVideoSurfaceHolder = mDisplayVideoSurface.getHolder();
                mDisplayVideoSurfaceHolder.addCallback(mSurfaceHolderCallBack);

                mPreviewVideoSurface = (SurfaceView) mVideoViews.findViewById(R.id.previewVideo);
                mPreviewVideoSurfaceHolder = mPreviewVideoSurface.getHolder();
                mPreviewVideoSurfaceHolder.addCallback(mSurfaceHolderCallBack);
                mPreviewVideoSurface.setZOrderOnTop(true);
            }
        }
    }
}
