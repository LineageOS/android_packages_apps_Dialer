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
import android.content.res.ColorStateList;
import android.support.annotation.StringRes;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.dialer.theme.base.ThemeComponent;

public class EmptyContentView extends LinearLayout implements View.OnClickListener {

  /** Listener to call when action button is clicked. */
  public interface OnEmptyViewActionButtonClickedListener {
    void onEmptyViewActionButtonClicked();
  }

  public static final int NO_LABEL = 0;
  public static final int NO_IMAGE = 0;

  private ImageView imageView;
  private TextView descriptionView;
  private TextView actionView;
  private OnEmptyViewActionButtonClickedListener onActionButtonClickedListener;

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
    imageView = (ImageView) findViewById(R.id.empty_list_view_image);
    descriptionView = (TextView) findViewById(R.id.empty_list_view_message);
    actionView = (TextView) findViewById(R.id.empty_list_view_action);
    actionView.setOnClickListener(this);

    imageView.setImageTintList(
        ColorStateList.valueOf(ThemeComponent.get(context).theme().getColorIconSecondary()));
  }

  public void setDescription(int resourceId) {
    if (resourceId == NO_LABEL) {
      descriptionView.setText(null);
      descriptionView.setVisibility(View.GONE);
    } else {
      descriptionView.setText(resourceId);
      descriptionView.setVisibility(View.VISIBLE);
    }
  }

  public void setImage(int resourceId) {
    if (resourceId == NO_LABEL) {
      imageView.setImageDrawable(null);
      imageView.setVisibility(View.GONE);
    } else {
      imageView.setImageResource(resourceId);
      imageView.setVisibility(View.VISIBLE);
    }
  }

  public void setActionLabel(@StringRes int resourceId) {
    actionLabel = resourceId;
    if (resourceId == NO_LABEL) {
      actionView.setText(null);
      actionView.setVisibility(View.GONE);
    } else {
      actionView.setText(resourceId);
      actionView.setVisibility(View.VISIBLE);
    }
  }

  public @StringRes int getActionLabel() {
    return actionLabel;
  }

  public boolean isShowingContent() {
    return imageView.getVisibility() == View.VISIBLE
        || descriptionView.getVisibility() == View.VISIBLE
        || actionView.getVisibility() == View.VISIBLE;
  }

  public void setActionClickedListener(OnEmptyViewActionButtonClickedListener listener) {
    onActionButtonClickedListener = listener;
  }

  @Override
  public void onClick(View v) {
    if (onActionButtonClickedListener != null) {
      onActionButtonClickedListener.onEmptyViewActionButtonClicked();
    }
  }

  protected void inflateLayout() {
    setOrientation(LinearLayout.VERTICAL);
    final LayoutInflater inflater =
        (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.empty_content_view, this);
  }
}
