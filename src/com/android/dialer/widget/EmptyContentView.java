/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.dialer.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.dialer.R;

public class EmptyContentView extends LinearLayout implements View.OnClickListener {

    public static final int NO_LABEL = 0;
    public static final int NO_IMAGE = 0;

    private ImageView mImageView;
    private ImageView mImageViewSide;
    private TextView mDescriptionView;
    private TextView mSubView;
    private TextView mActionView;
    private OnEmptyViewActionButtonClickedListener mOnActionButtonClickedListener;

    public interface OnEmptyViewActionButtonClickedListener {
        public void onEmptyViewActionButtonClicked();
    }

    public EmptyContentView(Context context) {
        this(context, null);
    }

    public EmptyContentView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EmptyContentView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public EmptyContentView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setOrientation(LinearLayout.VERTICAL);

        final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.empty_content_view, this);
        // Don't let touches fall through the empty view.
        setClickable(true);
        mImageView = (ImageView) findViewById(R.id.emptyListViewImage);
        mImageViewSide = (ImageView) findViewById(R.id.emptyListViewImageSide);
        mDescriptionView = (TextView) findViewById(R.id.emptyListViewMessage);
        mSubView = (TextView) findViewById(R.id.emptyListViewSubMessage);
        mActionView = (TextView) findViewById(R.id.emptyListViewAction);
        mActionView.setOnClickListener(this);
    }

    public void setDescription(int resourceId) {
        if (resourceId == NO_LABEL) {
            mDescriptionView.setText(null);
            mDescriptionView.setVisibility(View.GONE);
        } else {
            mDescriptionView.setText(resourceId);
            mDescriptionView.setVisibility(View.VISIBLE);
        }
    }

    public void setDescription(String string) {
        if (TextUtils.isEmpty(string)) {
            mDescriptionView.setText(null);
            mDescriptionView.setVisibility(View.GONE);
        } else {
            mDescriptionView.setText(string);
            mDescriptionView.setVisibility(View.VISIBLE);
        }
    }

    public void setSubMessage(int resourceId) {
        if (resourceId == NO_LABEL) {
            mSubView.setText(null);
            mSubView.setVisibility(View.GONE);
        } else {
            mSubView.setText(resourceId);
            mSubView.setVisibility(View.VISIBLE);
        }
    }

    public void setSubMessage(String string) {
        if (TextUtils.isEmpty(string)) {
            mSubView.setText(null);
            mSubView.setVisibility(View.GONE);
        } else {
            mSubView.setText(string);
            mSubView.setVisibility(View.VISIBLE);
        }
    }

    public void setImage(int resourceId) {
        setImage(resourceId, false);
    }

    public void setImage(Drawable drawable) {
        setImage(drawable, false);
    }

    public void setImage(int resourceId, boolean side) {
        mImageView.setImageResource(resourceId);
        mImageViewSide.setImageResource(resourceId);

        if (resourceId == NO_LABEL) {
            mImageView.setVisibility(View.GONE);
            mImageViewSide.setVisibility(View.GONE);
        } else {
            if (side) {
                mImageView.setVisibility(View.GONE);
                mImageViewSide.setVisibility(View.VISIBLE);
            } else {
                mImageView.setVisibility(View.VISIBLE);
                mImageViewSide.setVisibility(View.GONE);
            }
        }

        if (side) {
            mDescriptionView.setGravity(Gravity.LEFT|Gravity.TOP);
        } else {
            mDescriptionView.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.TOP);
        }
    }

    public void setImage(Drawable drawable, boolean side) {
        mImageView.setImageDrawable(drawable);
        mImageViewSide.setImageDrawable(drawable);

        if (drawable == null) {
            mImageView.setVisibility(View.GONE);
            mImageViewSide.setVisibility(View.GONE);
        } else {
            if (side) {
                mImageView.setVisibility(View.GONE);
                mImageViewSide.setVisibility(View.VISIBLE);
            } else {
                mImageView.setVisibility(View.VISIBLE);
                mImageViewSide.setVisibility(View.GONE);
            }
        }

        if (side) {
            mDescriptionView.setGravity(Gravity.LEFT | Gravity.TOP);
        } else {
            mDescriptionView.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.TOP);
        }
    }

    public void setActionLabel(int resourceId) {
        if (resourceId == NO_LABEL) {
            mActionView.setText(null);
            mActionView.setVisibility(View.GONE);
        } else {
            mActionView.setText(resourceId);
            mActionView.setVisibility(View.VISIBLE);
        }
    }

    public void setWidth(int width) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        lp.width = width;
        lp.gravity = Gravity.LEFT;
        requestLayout();
    }

    public boolean isShowingContent() {
        return mImageView.getVisibility() == View.VISIBLE
                || mImageViewSide.getVisibility() == View.VISIBLE
                || mDescriptionView.getVisibility() == View.VISIBLE
                || mActionView.getVisibility() == View.VISIBLE;
    }

    public void setActionClickedListener(OnEmptyViewActionButtonClickedListener listener) {
        mOnActionButtonClickedListener = listener;
    }

    @Override
    public void onClick(View v) {
        if (mOnActionButtonClickedListener != null) {
            mOnActionButtonClickedListener.onEmptyViewActionButtonClicked();
        }
    }
}
