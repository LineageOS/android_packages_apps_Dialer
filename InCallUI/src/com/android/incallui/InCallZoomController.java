/* Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.incallui;

import android.content.Context;
import android.view.View;
import android.telecom.InCallService.VideoCall;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;
import org.codeaurora.ims.QtiCallConstants;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import java.lang.Integer;
import java.util.Objects;

import com.android.incallui.ZoomControl.OnZoomChangedListener;


/**
 * This class implements the zoom listener for zoom control and shows the dialog and zoom controls
 * on the InCall screen and maintains state info about the camera zoom index.
 */
public class InCallZoomController implements InCallPresenter.IncomingCallListener {

    private static InCallZoomController sInCallZoomController;

    private AlertDialog mAlertDialog;

    private InCallPresenter mInCallPresenter;

    private Context mContext;

    private String mCameraId;

    CameraManager mCameraManager;

    /**
     * This class implements the zoom listener for zoom control
     */
    private class ZoomChangeListener implements ZoomControl.OnZoomChangedListener {
        private VideoCall mVideoCall;

        public ZoomChangeListener(VideoCall videoCall) {
            mVideoCall = videoCall;
        }

        @Override
        public void onZoomValueChanged(int index) {
            Log.v("this", "onZoomValueChanged:  index = " + index);
            mZoomIndex = index;
            mVideoCall.setZoom(mZoomIndex);
        }
    }

    /**
     * Default zoom value for camera
     */
    private static final int DEFAULT_CAMERA_ZOOM_VALUE = 0;

    /**
     * Transparency value for alert dialog
     */
    private static final float DIALOG_ALPHA_INDEX = 0.6f;

    /**
     * Static variable for storing zoom index value to maintain state
     */
    private int mZoomIndex = DEFAULT_CAMERA_ZOOM_VALUE;

    /**
     * This method returns a singleton instance of {@class InCallZoomController}
     */
    public static synchronized InCallZoomController getInstance() {
        if (sInCallZoomController == null) {
            sInCallZoomController = new InCallZoomController();
        }
        return sInCallZoomController;
    }

    /**
     * Private constructor. Must use getInstance() to get this singleton.
     */
    private InCallZoomController() {
    }

    /**
     * Set up function called to add listener for camera selection changes
     */
    public void setUp(Context context) {
        mContext = context;
        mInCallPresenter = InCallPresenter.getInstance();
        mInCallPresenter.addIncomingCallListener(this);
        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
    }

    /**
     * Tear down function to reset all variables and remove camera selection listener
     */
    public void tearDown() {
        mAlertDialog = null;
        mContext = null;
        mCameraId = null;
        mZoomIndex = DEFAULT_CAMERA_ZOOM_VALUE;
        mInCallPresenter.removeIncomingCallListener(this);
        mInCallPresenter = null;
        mCameraManager = null;
    }

    /**
     * Sets the layout params for the alert dialog - transparency and clearing flag to dim
     * background UI
     */
    private static void setLayoutParams(AlertDialog alertDialog) {
        if (alertDialog == null) {
            return;
        }
        final Window window = alertDialog.getWindow();
        WindowManager.LayoutParams windowLayoutParams = window.getAttributes();
        windowLayoutParams.alpha = DIALOG_ALPHA_INDEX;
        window.setAttributes(windowLayoutParams);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    /**
     * Called when preview surface is clicked on the InCallUI screen. Notification comes from
     * {@class VideocallPresenter}. Create the alert dialog and the zoom control,
     * set layout params attributes, set zoom params if zoom is supported and video call is valid
     */
    public void onPreviewSurfaceClicked(VideoCall videoCall) {
        Log.d(this, "onPreviewSurfaceClicked: VideoCall - " + videoCall);

        if(videoCall == null || !isCameraZoomSupported()) {
            Log.e(this, "onPreviewSurfaceClicked: VideoCall is null or Zoom not supported ");
            return;
        }

        try {
            final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(
                    mInCallPresenter.getActivity(), AlertDialog.THEME_HOLO_DARK);
            final View zoomControlView = mInCallPresenter.getActivity().getLayoutInflater().
                    inflate(R.layout.qti_video_call_zoom_control, null);
            final ZoomControlBar zoomControl = (ZoomControlBar) zoomControlView.findViewById(
                    R.id.zoom_control);
            dialogBuilder.setView(zoomControlView);
            mAlertDialog = dialogBuilder.create();
            mAlertDialog.setCanceledOnTouchOutside(true);
            setLayoutParams(mAlertDialog);
            zoomControl.setOnZoomChangeListener(new ZoomChangeListener(videoCall));
            initZoomControl(zoomControl, mZoomIndex);
            mAlertDialog.show();
        } catch (Exception e) {
            Log.e(this, "onPreviewSurfaceClicked: Exception " + e);
            return;
        }
    }

    private static void initZoomControl(ZoomControlBar zoomControl, int zoomIndex) {
        zoomControl.setZoomMax(QtiCallConstants.CAMERA_MAX_ZOOM);
        zoomControl.setZoomIndex(zoomIndex);
        zoomControl.setEnabled(true);
    }

    /**
     * Queries the camera characteristics to figure out if zoom is supported or not
     */
    private boolean isCameraZoomSupported() {
        try {
            final InCallCameraManager inCallCameraManager = mInCallPresenter.
                    getInCallCameraManager();
            final float CAMERA_ZOOM_NOT_SUPPORTED = 1.0f;

            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(
                inCallCameraManager.getActiveCameraId());
            return (characteristics != null) && (characteristics.get(
                    CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                    > CAMERA_ZOOM_NOT_SUPPORTED);
        } catch (Exception e) {
            Log.e(this, "isCameraZoomSupported: Failed to retrieve Max Zoom, " + e);
            return false;
        }
    }

    /**
     * Called from the {@class VideoCallPresenter} when camera is enabled or disabled
     * Reset the zoom index and dismiss the alert if camera id changes
     */
    public void onCameraEnabled(String cameraId) {
        Log.d(this, "onCameraEnabled: - cameraId -" + cameraId);
        if (!Objects.equals(mCameraId, cameraId)) {
            mCameraId = cameraId;
            mZoomIndex = DEFAULT_CAMERA_ZOOM_VALUE;
            dismissAlertDialog();
        }
    }

    private void dismissAlertDialog() {
        try {
            if (mAlertDialog != null) {
                mAlertDialog.dismiss();
                mAlertDialog = null;
            }
        } catch (Exception e) {
            // Since exceptions caused in zoom control dialog should not crash the phone process,
            // we intentionally capture the exception and ignore.
            Log.e(this, "dismissAlertDialog: Exception: " + e);
        }
    }

    /**
     * Called when there is a new incoming call.
     * Dismiss the alert.
     */
    @Override
    public void onIncomingCall(InCallPresenter.InCallState oldState,
            InCallPresenter.InCallState newState, Call call) {
        Log.v(this, "onIncomingCall - Call " + call + "oldState " + oldState + "newState " +
                newState);
        dismissAlertDialog();
    }
}
