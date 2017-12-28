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

package com.android.dialer.app.widget;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import com.android.dialer.animation.AnimUtils;
import com.android.dialer.app.R;
import com.android.dialer.util.DialerUtils;

public class SearchEditTextLayout extends FrameLayout {

  private static final float EXPAND_MARGIN_FRACTION_START = 0.8f;
  private static final int ANIMATION_DURATION = 200;
  /* Subclass-visible for testing */
  protected boolean isExpanded = false;
  protected boolean isFadedOut = false;
  private OnKeyListener preImeKeyListener;
  private int topMargin;
  private int bottomMargin;
  private int leftMargin;
  private int rightMargin;
  private float collapsedElevation;
  private View collapsed;
  private View expanded;
  private EditText searchView;
  private View searchIcon;
  private View collapsedSearchBox;
  private View voiceSearchButtonView;
  private View overflowButtonView;
  private View clearButtonView;

  private ValueAnimator animator;

  private Callback callback;

  private boolean voiceSearchEnabled;

  public SearchEditTextLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void setPreImeKeyListener(OnKeyListener listener) {
    preImeKeyListener = listener;
  }

  public void setCallback(Callback listener) {
    callback = listener;
  }

  public void setVoiceSearchEnabled(boolean enabled) {
    voiceSearchEnabled = enabled;
    updateVisibility(isExpanded);
  }

  @Override
  protected void onFinishInflate() {
    MarginLayoutParams params = (MarginLayoutParams) getLayoutParams();
    topMargin = params.topMargin;
    bottomMargin = params.bottomMargin;
    leftMargin = params.leftMargin;
    rightMargin = params.rightMargin;

    collapsedElevation = getElevation();

    collapsed = findViewById(R.id.search_box_collapsed);
    expanded = findViewById(R.id.search_box_expanded);
    searchView = (EditText) expanded.findViewById(R.id.search_view);

    searchIcon = findViewById(R.id.search_magnifying_glass);
    collapsedSearchBox = findViewById(R.id.search_box_start_search);
    voiceSearchButtonView = findViewById(R.id.voice_search_button);
    overflowButtonView = findViewById(R.id.dialtacts_options_menu_button);
    clearButtonView = findViewById(R.id.search_close_button);

    // Convert a long click into a click to expand the search box. Touch events are also
    // forwarded to the searchView. This accelerates the long-press scenario for copy/paste.
    collapsed.setOnLongClickListener(
        new OnLongClickListener() {
          @Override
          public boolean onLongClick(View view) {
            collapsed.performClick();
            return false;
          }
        });
    collapsed.setOnTouchListener(
        (v, event) -> {
          searchView.onTouchEvent(event);
          return false;
        });

    searchView.setOnFocusChangeListener(
        new OnFocusChangeListener() {
          @Override
          public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) {
              DialerUtils.showInputMethod(v);
            } else {
              DialerUtils.hideInputMethod(v);
            }
          }
        });

    searchView.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            if (callback != null) {
              callback.onSearchViewClicked();
            }
          }
        });

    searchView.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {
            clearButtonView.setVisibility(TextUtils.isEmpty(s) ? View.GONE : View.VISIBLE);
          }

          @Override
          public void afterTextChanged(Editable s) {}
        });

    findViewById(R.id.search_close_button)
        .setOnClickListener(
            new OnClickListener() {
              @Override
              public void onClick(View v) {
                searchView.setText(null);
              }
            });

    findViewById(R.id.search_back_button)
        .setOnClickListener(
            new OnClickListener() {
              @Override
              public void onClick(View v) {
                if (callback != null) {
                  callback.onBackButtonClicked();
                }
              }
            });

    super.onFinishInflate();
  }

  @Override
  public boolean dispatchKeyEventPreIme(KeyEvent event) {
    if (preImeKeyListener != null) {
      if (preImeKeyListener.onKey(this, event.getKeyCode(), event)) {
        return true;
      }
    }
    return super.dispatchKeyEventPreIme(event);
  }

  public void fadeOut() {
    fadeOut(null);
  }

  public void fadeOut(AnimUtils.AnimationCallback callback) {
    AnimUtils.fadeOut(this, ANIMATION_DURATION, callback);
    isFadedOut = true;
  }

  public void fadeIn() {
    AnimUtils.fadeIn(this, ANIMATION_DURATION);
    isFadedOut = false;
  }

  public void fadeIn(AnimUtils.AnimationCallback callback) {
    AnimUtils.fadeIn(this, ANIMATION_DURATION, AnimUtils.NO_DELAY, callback);
    isFadedOut = false;
  }

  public void setVisible(boolean visible) {
    if (visible) {
      setAlpha(1);
      setVisibility(View.VISIBLE);
      isFadedOut = false;
    } else {
      setAlpha(0);
      setVisibility(View.GONE);
      isFadedOut = true;
    }
  }

  public void expand(boolean animate, boolean requestFocus) {
    updateVisibility(true /* isExpand */);

    if (animate) {
      AnimUtils.crossFadeViews(expanded, collapsed, ANIMATION_DURATION);
      animator = ValueAnimator.ofFloat(EXPAND_MARGIN_FRACTION_START, 0f);
      setMargins(EXPAND_MARGIN_FRACTION_START);
      prepareAnimator();
    } else {
      expanded.setVisibility(View.VISIBLE);
      expanded.setAlpha(1);
      setMargins(0f);
      collapsed.setVisibility(View.GONE);
    }

    // Set 9-patch background. This owns the padding, so we need to restore the original values.
    int paddingTop = this.getPaddingTop();
    int paddingStart = this.getPaddingStart();
    int paddingBottom = this.getPaddingBottom();
    int paddingEnd = this.getPaddingEnd();
    setBackgroundResource(R.drawable.search_shadow);
    setElevation(0);
    setPaddingRelative(paddingStart, paddingTop, paddingEnd, paddingBottom);

    if (requestFocus) {
      searchView.requestFocus();
    }
    isExpanded = true;
  }

  public void collapse(boolean animate) {
    updateVisibility(false /* isExpand */);

    if (animate) {
      AnimUtils.crossFadeViews(collapsed, expanded, ANIMATION_DURATION);
      animator = ValueAnimator.ofFloat(0f, 1f);
      prepareAnimator();
    } else {
      collapsed.setVisibility(View.VISIBLE);
      collapsed.setAlpha(1);
      setMargins(1f);
      expanded.setVisibility(View.GONE);
    }

    isExpanded = false;
    setElevation(collapsedElevation);
    setBackgroundResource(R.drawable.rounded_corner);
  }

  /**
   * Updates the visibility of views depending on whether we will show the expanded or collapsed
   * search view. This helps prevent some jank with the crossfading if we are animating.
   *
   * @param isExpand Whether we are about to show the expanded search box.
   */
  private void updateVisibility(boolean isExpand) {
    int collapsedViewVisibility = isExpand ? View.GONE : View.VISIBLE;
    int expandedViewVisibility = isExpand ? View.VISIBLE : View.GONE;

    searchIcon.setVisibility(collapsedViewVisibility);
    collapsedSearchBox.setVisibility(collapsedViewVisibility);
    if (voiceSearchEnabled) {
      voiceSearchButtonView.setVisibility(collapsedViewVisibility);
    } else {
      voiceSearchButtonView.setVisibility(View.GONE);
    }
    overflowButtonView.setVisibility(collapsedViewVisibility);
    // TODO: Prevents keyboard from jumping up in landscape mode after exiting the
    // SearchFragment when the query string is empty. More elegant fix?
    // mExpandedSearchBox.setVisibility(expandedViewVisibility);
    if (TextUtils.isEmpty(searchView.getText())) {
      clearButtonView.setVisibility(View.GONE);
    } else {
      clearButtonView.setVisibility(expandedViewVisibility);
    }
  }

  private void prepareAnimator() {
    if (animator != null) {
      animator.cancel();
    }

    animator.addUpdateListener(
        new AnimatorUpdateListener() {
          @Override
          public void onAnimationUpdate(ValueAnimator animation) {
            final Float fraction = (Float) animation.getAnimatedValue();
            setMargins(fraction);
          }
        });

    animator.setDuration(ANIMATION_DURATION);
    animator.start();
  }

  public boolean isExpanded() {
    return isExpanded;
  }

  public boolean isFadedOut() {
    return isFadedOut;
  }

  /**
   * Assigns margins to the search box as a fraction of its maximum margin size
   *
   * @param fraction How large the margins should be as a fraction of their full size
   */
  private void setMargins(float fraction) {
    MarginLayoutParams params = (MarginLayoutParams) getLayoutParams();
    params.topMargin = (int) (topMargin * fraction);
    params.bottomMargin = (int) (bottomMargin * fraction);
    params.leftMargin = (int) (leftMargin * fraction);
    params.rightMargin = (int) (rightMargin * fraction);
    requestLayout();
  }

  /** Listener for the back button next to the search view being pressed */
  public interface Callback {

    void onBackButtonClicked();

    void onSearchViewClicked();
  }
}
