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
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.dialer.R;

public class EmptyContentView extends LinearLayout implements View.OnClickListener {

    public static final int NO_LABEL = 0;

    private ImageView mImageView;
    private TextView mDescriptionView;
    private TextView mActionView;
    private String[] mPermissions = new String[] {};
    private OnEmptyViewActionButtonClickedListener mOnActionButtonClickedListener;

    public interface OnEmptyViewActionButtonClickedListener {
        public void onEmptyViewActionButtonClicked(String[] permissions);
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
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mImageView = (ImageView) findViewById(R.id.emptyListViewImage);
        mDescriptionView = (TextView) findViewById(R.id.emptyListViewMessage);
        mActionView = (TextView) findViewById(R.id.emptyListViewAction);
        mActionView.setOnClickListener(this);
    }

    public void setDescription(int resourceId) {
        mDescriptionView.setText(resourceId);
    }

    public void setImage(int resourceId) {
        mImageView.setImageResource(resourceId);
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

    public void setActionClickedListener(OnEmptyViewActionButtonClickedListener listener) {
        mOnActionButtonClickedListener = listener;
    }

    @Override
    public void onClick(View v) {
        if (mOnActionButtonClickedListener != null) {
            mOnActionButtonClickedListener.onEmptyViewActionButtonClicked(mPermissions);
        }
    }
}
