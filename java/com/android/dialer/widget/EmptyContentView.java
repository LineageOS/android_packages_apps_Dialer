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
import android.support.annotation.StringRes;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class EmptyContentView extends LinearLayout implements View.OnClickListener {

  /** Listener to call when action button is clicked. */
  public interface OnEmptyViewActionButtonClickedListener {
    void onEmptyViewActionButtonClicked();
  }

  public static final int NO_LABEL = 0;
  public static final int NO_IMAGE = 0;

  private ImageView mImageView;
  private TextView mDescriptionView;
  private TextView mActionView;
  private OnEmptyViewActionButtonClickedListener mOnActionButtonClickedListener;

  private @StringRes int actionLabel;

  public EmptyContentView(Context context) {
    this(context, null);
  }

  public EmptyContentView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public EmptyContentView(Context context, AttributeSet attrs, int defStyleAttr) {
    this(context, attrs, defStyleAttr, 0);
  }

  public EmptyContentView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    inflateLayout();

    // Don't let touches fall through the empty view.
    setClickable(true);
    mImageView = (ImageView) findViewById(R.id.empty_list_view_image);
    mDescriptionView = (TextView) findViewById(R.id.empty_list_view_message);
    mActionView = (TextView) findViewById(R.id.empty_list_view_action);
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

  public void setImage(int resourceId) {
    if (resourceId == NO_LABEL) {
      mImageView.setImageDrawable(null);
      mImageView.setVisibility(View.GONE);
    } else {
      mImageView.setImageResource(resourceId);
      mImageView.setVisibility(View.VISIBLE);
    }
  }

  public void setActionLabel(@StringRes int resourceId) {
    actionLabel = resourceId;
    if (resourceId == NO_LABEL) {
      mActionView.setText(null);
      mActionView.setVisibility(View.GONE);
    } else {
      mActionView.setText(resourceId);
      mActionView.setVisibility(View.VISIBLE);
    }
  }

  public @StringRes int getActionLabel() {
    return actionLabel;
  }

  public boolean isShowingContent() {
    return mImageView.getVisibility() == View.VISIBLE
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

  protected void inflateLayout() {
    setOrientation(LinearLayout.VERTICAL);
    final LayoutInflater inflater =
        (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.empty_content_view, this);
  }
}
