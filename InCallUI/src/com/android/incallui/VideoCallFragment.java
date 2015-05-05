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

import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.google.common.base.Objects;

/**
 * Fragment containing video calling surfaces.
 */
public class VideoCallFragment extends BaseFragment<VideoCallPresenter,
        VideoCallPresenter.VideoCallUi> implements VideoCallPresenter.VideoCallUi {
    private static final String TAG = VideoCallFragment.class.getSimpleName();
    private static final boolean DEBUG = false;

    /**
     * Used to indicate that the surface dimensions are not set.
     */
    private static final int DIMENSIONS_NOT_SET = -1;

    /**
     * Surface ID for the display surface.
     */
    public static final int SURFACE_DISPLAY = 1;

    /**
     * Surface ID for the preview surface.
     */
    public static final int SURFACE_PREVIEW = 2;

    /**
     * Used to indicate that the UI rotation is unknown.
     */
    public static final int ORIENTATION_UNKNOWN = -1;

    // Static storage used to retain the video surfaces across Activity restart.
    // TextureViews are not parcelable, so it is not possible to store them in the saved state.
    private static boolean sVideoSurfacesInUse = false;
    private static VideoCallSurface sPreviewSurface = null;
    private static VideoCallSurface sDisplaySurface = null;
    private static Point sDisplaySize = null;

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
     * The {@link FrameLayout} containing the preview surface.
     */
    private View mPreviewVideoContainer;

    /**
     * Icon shown to indicate that the outgoing camera has been turned off.
     */
    private View mCameraOff;

    /**
     * {@link ImageView} containing the user's profile photo.
     */
    private ImageView mPreviewPhoto;

    /**
     * {@code True} when the layout of the activity has been completed.
     */
    private boolean mIsLayoutComplete = false;

    /**
     * {@code True} if in landscape mode.
     */
    private boolean mIsLandscape;

    /**
     * Inner-class representing a {@link TextureView} and its associated {@link SurfaceTexture} and
     * {@link Surface}.  Used to manage the lifecycle of these objects across device orientation
     * changes.
     */
    private static class VideoCallSurface implements TextureView.SurfaceTextureListener,
            View.OnClickListener, View.OnAttachStateChangeListener {
        private int mSurfaceId;
        private VideoCallPresenter mPresenter;
        private TextureView mTextureView;
        private SurfaceTexture mSavedSurfaceTexture;
        private Surface mSavedSurface;
        private boolean mIsDoneWithSurface;
        private int mWidth = DIMENSIONS_NOT_SET;
        private int mHeight = DIMENSIONS_NOT_SET;

        /**
         * Creates an instance of a {@link VideoCallSurface}.
         *
         * @param surfaceId The surface ID of the surface.
         * @param textureView The {@link TextureView} for the surface.
         */
        public VideoCallSurface(VideoCallPresenter presenter, int surfaceId,
                TextureView textureView) {
            this(presenter, surfaceId, textureView, DIMENSIONS_NOT_SET, DIMENSIONS_NOT_SET);
        }

        /**
         * Creates an instance of a {@link VideoCallSurface}.
         *
         * @param surfaceId The surface ID of the surface.
         * @param textureView The {@link TextureView} for the surface.
         * @param width The width of the surface.
         * @param height The height of the surface.
         */
        public VideoCallSurface(VideoCallPresenter presenter,int surfaceId, TextureView textureView,
                int width, int height) {
            Log.d(this, "VideoCallSurface: surfaceId=" + surfaceId +
                    " width=" + width + " height=" + height);
            mPresenter = presenter;
            mWidth = width;
            mHeight = height;
            mSurfaceId = surfaceId;

            recreateView(textureView);
        }

        /**
         * Recreates a {@link VideoCallSurface} after a device orientation change.  Re-applies the
         * saved {@link SurfaceTexture} to the
         *
         * @param view The {@link TextureView}.
         */
        public void recreateView(TextureView view) {
            if (DEBUG) {
                Log.i(TAG, "recreateView: " + view);
            }

            if (mTextureView == view) {
                return;
            }

            mTextureView = view;
            mTextureView.setSurfaceTextureListener(this);
            mTextureView.setOnClickListener(this);

            final boolean areSameSurfaces =
                    Objects.equal(mSavedSurfaceTexture, mTextureView.getSurfaceTexture());
            Log.d(this, "recreateView: SavedSurfaceTexture=" + mSavedSurfaceTexture
                    + " areSameSurfaces=" + areSameSurfaces);
            if (mSavedSurfaceTexture != null && !areSameSurfaces) {
                mTextureView.setSurfaceTexture(mSavedSurfaceTexture);
                if (createSurface(mWidth, mHeight)) {
                    onSurfaceCreated();
                }
            }
            mIsDoneWithSurface = false;
        }

        public void resetPresenter(VideoCallPresenter presenter) {
            Log.d(this, "resetPresenter: CurrentPresenter=" + mPresenter + " NewPresenter="
                    + presenter);
            mPresenter = presenter;
        }

        /**
         * Handles {@link SurfaceTexture} callback to indicate that a {@link SurfaceTexture} has
         * been successfully created.
         *
         * @param surfaceTexture The {@link SurfaceTexture} which has been created.
         * @param width The width of the {@link SurfaceTexture}.
         * @param height The height of the {@link SurfaceTexture}.
         */
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width,
                int height) {
            boolean surfaceCreated;
            if (DEBUG) {
                Log.i(TAG, "onSurfaceTextureAvailable: " + surfaceTexture);
            }
            // Where there is no saved {@link SurfaceTexture} available, use the newly created one.
            // If a saved {@link SurfaceTexture} is available, we are re-creating after an
            // orientation change.
            Log.d(this, " onSurfaceTextureAvailable mSurfaceId=" + mSurfaceId + " surfaceTexture="
                    + surfaceTexture + " width=" + width
                    + " height=" + height + " mSavedSurfaceTexture=" + mSavedSurfaceTexture);
            Log.d(this, " onSurfaceTextureAvailable VideoCallPresenter=" + mPresenter);
            if (mSavedSurfaceTexture == null) {
                mSavedSurfaceTexture = surfaceTexture;
                surfaceCreated = createSurface(width, height);
            } else {
                // A saved SurfaceTexture was found.
                Log.d(this, " onSurfaceTextureAvailable: Replacing with cached surface...");
                mTextureView.setSurfaceTexture(mSavedSurfaceTexture);
                surfaceCreated = true;
            }

            // Inform presenter that the surface is available.
            if (surfaceCreated) {
                onSurfaceCreated();
            }
        }

        private void onSurfaceCreated() {
            if (mPresenter != null) {
                mPresenter.onSurfaceCreated(mSurfaceId);
            } else {
                Log.e(this, "onSurfaceTextureAvailable: Presenter is null");
            }
        }

        /**
         * Handles a change in the {@link SurfaceTexture}'s size.
         *
         * @param surfaceTexture The {@link SurfaceTexture}.
         * @param width The new width.
         * @param height The new height.
         */
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width,
                int height) {
            // Not handled
        }

        /**
         * Handles {@link SurfaceTexture} destruct callback, indicating that it has been destroyed.
         *
         * @param surfaceTexture The {@link SurfaceTexture}.
         * @return {@code True} if the {@link TextureView} can release the {@link SurfaceTexture}.
         */
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            /**
             * Destroying the surface texture; inform the presenter so it can null the surfaces.
             */
            Log.d(this, " onSurfaceTextureDestroyed mSurfaceId=" + mSurfaceId + " surfaceTexture="
                    + surfaceTexture + " SavedSurfaceTexture=" + mSavedSurfaceTexture
                    + " SavedSurface=" + mSavedSurface);
            Log.d(this, " onSurfaceTextureDestroyed VideoCallPresenter=" + mPresenter);

            // Notify presenter if it is not null.
            onSurfaceDestroyed();

            if (mIsDoneWithSurface) {
                onSurfaceReleased();
                if (mSavedSurface != null) {
                    mSavedSurface.release();
                    mSavedSurface = null;
                }
            }
            return mIsDoneWithSurface;
        }

        private void onSurfaceDestroyed() {
            if (mPresenter != null) {
                mPresenter.onSurfaceDestroyed(mSurfaceId);
            } else {
                Log.e(this, "onSurfaceTextureDestroyed: Presenter is null.");
            }
        }

        /**
         * Handles {@link SurfaceTexture} update callback.
         * @param surface
         */
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            // Not Handled
        }

        @Override
        public void onViewAttachedToWindow(View v) {
            if (DEBUG) {
                Log.i(TAG, "OnViewAttachedToWindow");
            }
            if (mSavedSurfaceTexture != null) {
                mTextureView.setSurfaceTexture(mSavedSurfaceTexture);
            }
        }

        @Override
        public void onViewDetachedFromWindow(View v) {}

        /**
         * Retrieves the current {@link TextureView}.
         *
         * @return The {@link TextureView}.
         */
        public TextureView getTextureView() {
            return mTextureView;
        }

        /**
         * Called by the user presenter to indicate that the surface is no longer required due to a
         * change in video state.  Releases and clears out the saved surface and surface textures.
         */
        public void setDoneWithSurface() {
            Log.d(this, "setDoneWithSurface: SavedSurface=" + mSavedSurface
                    + " SavedSurfaceTexture=" + mSavedSurfaceTexture);
            mIsDoneWithSurface = true;
            if (mTextureView != null && mTextureView.isAvailable()) {
                return;
            }

            if (mSavedSurface != null) {
                onSurfaceReleased();
                mSavedSurface.release();
                mSavedSurface = null;
            }
            if (mSavedSurfaceTexture != null) {
                mSavedSurfaceTexture.release();
                mSavedSurfaceTexture = null;
            }
        }

        private void onSurfaceReleased() {
            if (mPresenter != null) {
                mPresenter.onSurfaceReleased(mSurfaceId);
            } else {
                Log.d(this, "setDoneWithSurface: Presenter is null.");
            }
        }

        /**
         * Retrieves the saved surface instance.
         *
         * @return The surface.
         */
        public Surface getSurface() {
            return mSavedSurface;
        }

        /**
         * Sets the dimensions of the surface.
         *
         * @param width The width of the surface, in pixels.
         * @param height The height of the surface, in pixels.
         */
        public void setSurfaceDimensions(int width, int height) {
            Log.d(this, "setSurfaceDimensions, width=" + width + " height=" + height);
            mWidth = width;
            mHeight = height;

            if (mSavedSurfaceTexture != null) {
                Log.d(this, "setSurfaceDimensions, mSavedSurfaceTexture is NOT equal to null.");
                createSurface(width, height);
            }
        }

        /**
         * Creates the {@link Surface}, adjusting the {@link SurfaceTexture} buffer size.
         * @param width The width of the surface to create.
         * @param height The height of the surface to create.
         */
        private boolean createSurface(int width, int height) {
            Log.d(this, "createSurface mSavedSurfaceTexture=" + mSavedSurfaceTexture
                    + " mSurfaceId =" + mSurfaceId + " mWidth " + width + " mHeight=" + height);
            if (width != DIMENSIONS_NOT_SET && height != DIMENSIONS_NOT_SET
                    && mSavedSurfaceTexture != null) {
                mSavedSurfaceTexture.setDefaultBufferSize(width, height);
                mSavedSurface = new Surface(mSavedSurfaceTexture);
                return true;
            }
            return false;
        }

        /**
         * Handles a user clicking the surface, which is the trigger to toggle the full screen
         * Video UI.
         *
         * @param view The view receiving the click.
         */
        @Override
        public void onClick(View view) {
            if (mPresenter != null) {
                mPresenter.onSurfaceClick(mSurfaceId);
            } else {
                Log.e(this, "onClick: Presenter is null.");
            }
        }

        /**
         * Returns the dimensions of the surface.
         *
         * @return The dimensions of the surface.
         */
        public Point getSurfaceDimensions() {
            return new Point(mWidth, mHeight);
        }
    };

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

        mIsLandscape = getResources().getBoolean(R.bool.is_layout_landscape);

        Log.d(this, "onActivityCreated: IsLandscape=" + mIsLandscape);
        getPresenter().init(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        final View view = inflater.inflate(R.layout.video_call_fragment, container, false);

        return view;
    }

    /**
     * Centers the display view vertically for portrait orientations. The view is centered within
     * the available space not occupied by the call card. This is a no-op for landscape mode.
     *
     * @param displayVideo The video view to center.
     */
    private void centerDisplayView(View displayVideo) {
        if (!mIsLandscape) {
            float spaceBesideCallCard = InCallPresenter.getInstance().getSpaceBesideCallCard();
            float videoViewTranslation = displayVideo.getHeight() / 2
                    - spaceBesideCallCard / 2;
            displayVideo.setTranslationY(videoViewTranslation);
        }
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
        Log.d(this, "onViewCreated: VideoSurfacesInUse=" + sVideoSurfacesInUse);

        mVideoViewsStub = (ViewStub) view.findViewById(R.id.videoCallViewsStub);

        // If the surfaces are already in use, we have just changed orientation or otherwise
        // re-created the fragment.  In this case we need to inflate the video call views and
        // restore the surfaces.
        if (sVideoSurfacesInUse) {
            inflateVideoCallViews();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(this, "onStop:");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(this, "onPause:");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(this, "onDestroyView:");
    }

    /**
     * Creates the presenter for the {@link VideoCallFragment}.
     * @return The presenter instance.
     */
    @Override
    public VideoCallPresenter createPresenter() {
        Log.d(this, "createPresenter");
        VideoCallPresenter presenter = new VideoCallPresenter();
        onPresenterChanged(presenter);
        return presenter;
    }

    /**
     * @return The user interface for the presenter, which is this fragment.
     */
    @Override
    public VideoCallPresenter.VideoCallUi getUi() {
        return this;
    }

    /**
     * Inflate video surfaces.
     *
     * @param show {@code True} if the video surfaces should be shown.
     */
    private void inflateVideoUi(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        getView().setVisibility(visibility);

        if (show) {
            inflateVideoCallViews();
        }

        if (mVideoViews != null) {
            mVideoViews.setVisibility(visibility);
        }
    }

    /**
     * Hides and shows the incoming video view and changes the outgoing video view's state based on
     * whether outgoing view is enabled or not.
     */
    public void showVideoViews(boolean previewPaused, boolean showIncoming) {
        inflateVideoUi(true);

        View incomingVideoView = mVideoViews.findViewById(R.id.incomingVideo);
        if (incomingVideoView != null) {
            incomingVideoView.setVisibility(showIncoming ? View.VISIBLE : View.INVISIBLE);
        }
        if (mCameraOff != null) {
            mCameraOff.setVisibility(!previewPaused ? View.VISIBLE : View.INVISIBLE);
        }
        if (mPreviewPhoto != null) {
            mPreviewPhoto.setVisibility(!previewPaused ? View.VISIBLE : View.INVISIBLE);
        }
    }

    /**
     * Hide all video views.
     */
    public void hideVideoUi() {
        inflateVideoUi(false);
    }

    /**
     * Cleans up the video telephony surfaces.  Used when the presenter indicates a change to an
     * audio-only state.  Since the surfaces are static, it is important to ensure they are cleaned
     * up promptly.
     */
    @Override
    public void cleanupSurfaces() {
        Log.d(this, "cleanupSurfaces");
        if (sDisplaySurface != null) {
            sDisplaySurface.setDoneWithSurface();
            sDisplaySurface = null;
        }
        if (sPreviewSurface != null) {
            sPreviewSurface.setDoneWithSurface();
            sPreviewSurface = null;
        }
        sVideoSurfacesInUse = false;
    }

    @Override
    public ImageView getPreviewPhotoView() {
        return mPreviewPhoto;
    }

    private void onPresenterChanged(VideoCallPresenter presenter) {
        Log.d(this, "onPresenterChanged: Presenter=" + presenter);
        if (sDisplaySurface != null) {
            sDisplaySurface.resetPresenter(presenter);;
        }
        if (sPreviewSurface != null) {
            sPreviewSurface.resetPresenter(presenter);
        }
    }

    /**
     * @return {@code True} if the display video surface has been created.
     */
    @Override
    public boolean isDisplayVideoSurfaceCreated() {
        boolean ret = sDisplaySurface != null && sDisplaySurface.getSurface() != null;
        Log.d(this, " isDisplayVideoSurfaceCreated returns " + ret);
        return ret;
    }

    /**
     * @return {@code True} if the preview video surface has been created.
     */
    @Override
    public boolean isPreviewVideoSurfaceCreated() {
        boolean ret = sPreviewSurface != null && sPreviewSurface.getSurface() != null;
        Log.d(this, " isPreviewVideoSurfaceCreated returns " + ret);
        return ret;
    }

    /**
     * {@link android.view.Surface} on which incoming video for a video call is displayed.
     * {@code Null} until the video views {@link android.view.ViewStub} is inflated.
     */
    @Override
    public Surface getDisplayVideoSurface() {
        return sDisplaySurface == null ? null : sDisplaySurface.getSurface();
    }

    /**
     * {@link android.view.Surface} on which a preview of the outgoing video for a video call is
     * displayed.  {@code Null} until the video views {@link android.view.ViewStub} is inflated.
     */
    @Override
    public Surface getPreviewVideoSurface() {
        return sPreviewSurface == null ? null : sPreviewSurface.getSurface();
    }

    /**
     * Changes the dimensions of the preview surface.  Called when the dimensions change due to a
     * device orientation change.
     *
     * @param width The new width.
     * @param height The new height.
     */
    @Override
    public void setPreviewSize(int width, int height) {
        Log.d(this, "setPreviewSize: width=" + width + " height=" + height);
        if (sPreviewSurface != null) {
            TextureView preview = sPreviewSurface.getTextureView();

            if (preview == null ) {
                return;
            }

            // Set the dimensions of both the video surface and the FrameLayout containing it.
            ViewGroup.LayoutParams params = preview.getLayoutParams();
            params.width = width;
            params.height = height;
            preview.setLayoutParams(params);

            if (mPreviewVideoContainer != null) {
                ViewGroup.LayoutParams containerParams = mPreviewVideoContainer.getLayoutParams();
                containerParams.width = width;
                containerParams.height = height;
                mPreviewVideoContainer.setLayoutParams(containerParams);
            }

            // The width and height are interchanged outside of this method based on the current
            // orientation, so we can transform using "width", which will be either the width or
            // the height.
            Matrix transform = new Matrix();
            transform.setScale(-1, 1, width/2, 0);
            preview.setTransform(transform);
        }
    }

    @Override
    public void setPreviewSurfaceSize(int width, int height) {
        final boolean isPreviewSurfaceAvailable = sPreviewSurface != null;
        Log.d(this, "setPreviewSurfaceSize: width=" + width + " height=" + height +
                " isPreviewSurfaceAvailable=" + isPreviewSurfaceAvailable);
        if (isPreviewSurfaceAvailable) {
            sPreviewSurface.setSurfaceDimensions(width, height);
        }
    }

    /**
     * returns UI's current orientation.
     */
    @Override
    public int getCurrentRotation() {
        try {
            return getActivity().getWindowManager().getDefaultDisplay().getRotation();
        } catch (Exception e) {
            Log.e(this, "getCurrentRotation: Retrieving current rotation failed. Ex=" + e);
        }
        return ORIENTATION_UNKNOWN;
    }

    /**
     * Changes the dimensions of the display video surface. Called when the dimensions change due to
     * a peer resolution update
     *
     * @param width The new width.
     * @param height The new height.
     */
    @Override
    public void setDisplayVideoSize(int width, int height) {
        Log.d(this, "setDisplayVideoSize: width=" + width + " height=" + height);
        if (sDisplaySurface != null) {
            TextureView displayVideo = sDisplaySurface.getTextureView();
            if (displayVideo == null) {
                Log.e(this, "Display Video texture view is null. Bail out");
                return;
            }
            sDisplaySize = new Point(width, height);
            setSurfaceSizeAndTranslation(displayVideo, sDisplaySize);
        } else {
            Log.e(this, "Display Video Surface is null. Bail out");
        }
    }

    /**
     * Determines the size of the device screen.
     *
     * @return {@link Point} specifying the width and height of the screen.
     */
    @Override
    public Point getScreenSize() {
        // Get current screen size.
        Display display = getActivity().getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        return size;
    }

    /**
     * Determines the size of the preview surface.
     *
     * @return {@link Point} specifying the width and height of the preview surface.
     */
    @Override
    public Point getPreviewSize() {
        if (sPreviewSurface == null) {
            return null;
        }
        return sPreviewSurface.getSurfaceDimensions();
    }

    /**
     * Inflates the {@link ViewStub} containing the incoming and outgoing surfaces, if necessary,
     * and creates {@link VideoCallSurface} instances to track the surfaces.
     */
    private void inflateVideoCallViews() {
        Log.d(this, "inflateVideoCallViews");
        if (mVideoViews == null ) {
            mVideoViews = mVideoViewsStub.inflate();
        }

        if (mVideoViews != null) {
            mPreviewVideoContainer = mVideoViews.findViewById(R.id.previewVideoContainer);
            mCameraOff = mVideoViews.findViewById(R.id.previewCameraOff);
            mPreviewPhoto = (ImageView) mVideoViews.findViewById(R.id.previewProfilePhoto);

            TextureView displaySurface = (TextureView) mVideoViews.findViewById(R.id.incomingVideo);

            Log.d(this, "inflateVideoCallViews: sVideoSurfacesInUse=" + sVideoSurfacesInUse);
            //If peer adjusted screen size is not available, set screen size to default display size
            Point screenSize = sDisplaySize == null ? getScreenSize() : sDisplaySize;
            setSurfaceSizeAndTranslation(displaySurface, screenSize);

            if (!sVideoSurfacesInUse) {
                // Where the video surfaces are not already in use (first time creating them),
                // setup new VideoCallSurface instances to track them.
                Log.d(this, " inflateVideoCallViews screenSize" + screenSize);

                sDisplaySurface = new VideoCallSurface(getPresenter(), SURFACE_DISPLAY,
                        (TextureView) mVideoViews.findViewById(R.id.incomingVideo), screenSize.x,
                        screenSize.y);
                sPreviewSurface = new VideoCallSurface(getPresenter(), SURFACE_PREVIEW,
                        (TextureView) mVideoViews.findViewById(R.id.previewVideo));
                sVideoSurfacesInUse = true;
            } else {
                // In this case, the video surfaces are already in use (we are recreating the
                // Fragment after a destroy/create cycle resulting from a rotation.
                sDisplaySurface.recreateView((TextureView) mVideoViews.findViewById(
                        R.id.incomingVideo));
                sPreviewSurface.recreateView((TextureView) mVideoViews.findViewById(
                        R.id.previewVideo));
            }

            // Attempt to center the incoming video view, if it is in the layout.
            final ViewTreeObserver observer = mVideoViews.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    // Check if the layout includes the incoming video surface -- this will only be the
                    // case for a video call.
                    View displayVideo = mVideoViews.findViewById(R.id.incomingVideo);
                    if (displayVideo != null) {
                        centerDisplayView(displayVideo);
                    }
                    mIsLayoutComplete = true;

                    // Remove the listener so we don't continually re-layout.
                    ViewTreeObserver observer = mVideoViews.getViewTreeObserver();
                    if (observer.isAlive()) {
                        observer.removeOnGlobalLayoutListener(this);
                    }
                }
            });
        }
    }

    /**
     * Resizes a surface so that it has the same size as the full screen and so that it is
     * centered vertically below the call card.
     *
     * @param textureView The {@link TextureView} to resize and position.
     * @param size The size of the screen.
     */
    private void setSurfaceSizeAndTranslation(TextureView textureView, Point size) {
        // Set the surface to have that size.
        ViewGroup.LayoutParams params = textureView.getLayoutParams();
        params.width = size.x;
        params.height = size.y;
        textureView.setLayoutParams(params);
        Log.d(this, "setSurfaceSizeAndTranslation: Size=" + size + "IsLayoutComplete=" +
                mIsLayoutComplete + "IsLandscape=" + mIsLandscape);

        // It is only possible to center the display view if layout of the views has completed.
        // It is only after layout is complete that the dimensions of the Call Card has been
        // established, which is a prerequisite to centering the view.
        // Incoming video calls will center the view
        if (mIsLayoutComplete) {
            centerDisplayView(textureView);
        }
    }
}
