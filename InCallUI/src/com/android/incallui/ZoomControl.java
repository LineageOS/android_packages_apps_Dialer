/* Copyright (c) 2012 - 2015, The Linux Foundation. All rights reserved.
 * Not a Contribution, Apache license notifications and license are retained
 * for attribution purposes only.
 *
 * Copyright (C) 2011 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.incallui;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.RelativeLayout;

/**
 * A view that contains camera zoom control which could adjust the zoom in/out
 * if the camera supports zooming.
 */
public abstract class ZoomControl extends RelativeLayout{
    protected ImageView mZoomIn;
    protected ImageView mZoomOut;
    protected ImageView mZoomSlider;
    protected int mOrientation;

    public interface OnZoomChangedListener {
        void onZoomValueChanged(int index);  // only for immediate zoom
    }

    // The interface OnZoomIndexChangedListener is used to inform the
    // ZoomIndexBar about the zoom index change. The index position is between
    // 0 (the index is zero) and 1.0 (the index is mZoomMax).
    public interface OnZoomIndexChangedListener {
        void onZoomIndexChanged(double indexPosition);
    }

    protected int mZoomMax, mZoomIndex;
    private OnZoomChangedListener mListener;

    private int mStep;

    public ZoomControl(Context context, AttributeSet attrs) {
        super(context, attrs);
        mZoomIn = addImageView(context, R.drawable.ic_zoom_in);
        mZoomSlider = addImageView(context, R.drawable.ic_zoom_slider);
        mZoomOut = addImageView(context, R.drawable.ic_zoom_out);
    }

    public void startZoomControl() {
        mZoomSlider.setPressed(true);
        setZoomIndex(mZoomIndex); // Update the zoom index bar.
    }

    protected ImageView addImageView(Context context, int iconResourceId) {
        ImageView image = new ImageView(context);
        image.setImageResource(iconResourceId);
        addView(image);
        return image;
    }

    public void closeZoomControl() {
        mZoomSlider.setPressed(false);
    }

    public void setZoomMax(int zoomMax) {
        mZoomMax = zoomMax;

        // Layout should be requested as the maximum zoom level is the key to
        // show the correct zoom slider position.
        requestLayout();
    }

    public int getZoomMax() {
        return mZoomMax;
    }

    public void setOnZoomChangeListener(OnZoomChangedListener listener) {
        mListener = listener;
    }

    public void setZoomIndex(int index) {
        if (index < 0 || index > mZoomMax) {
            throw new IllegalArgumentException("Invalid zoom value:" + index);
        }
        mZoomIndex = index;
        invalidate();
    }

    public int getZoomIndex() {
        return mZoomIndex;
    }

    protected void setZoomStep(int step) {
        mStep = step;
    }

    // Called from ZoomControlBar to change the zoom level.
    protected void performZoom(double zoomPercentage) {
        int index = (int) (mZoomMax * zoomPercentage);
        if (mZoomIndex == index) return;
        changeZoomIndex(index);
   }

    private boolean changeZoomIndex(int index) {
        if (mListener != null) {
            if (index > mZoomMax) index = mZoomMax;
            if (index < 0) index = 0;
            mListener.onZoomValueChanged(index);
            mZoomIndex = index;
        }
        return true;
    }

    @Override
    public void setActivated(boolean activated) {
        super.setActivated(activated);
        mZoomIn.setActivated(activated);
        mZoomOut.setActivated(activated);
    }
}
