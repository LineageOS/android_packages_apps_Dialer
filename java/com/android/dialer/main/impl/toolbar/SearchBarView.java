/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.main.impl.toolbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import com.android.dialer.animation.AnimUtils;
import com.android.dialer.util.DialerUtils;
import com.google.common.base.Optional;

/** Search bar for {@link MainToolbar}. Mostly used to handle expand and collapse animation. */
final class SearchBarView extends FrameLayout {

  private static final int ANIMATION_DURATION = 200;
  private static final float EXPAND_MARGIN_FRACTION_START = 0.8f;

  private final float margin;
  private final float animationEndHeight;

  private SearchBarListener listener;
  private EditText searchBox;

  private int initialHeight;
  private boolean isExpanded;
  private View searchBoxCollapsed;
  private View searchBoxExpanded;
  private View clearButton;

  public SearchBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    margin = getContext().getResources().getDimension(R.dimen.search_bar_margin);
    animationEndHeight =
        getContext().getResources().getDimension(R.dimen.expanded_search_bar_height);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    clearButton = findViewById(R.id.search_clear_button);
    searchBox = findViewById(R.id.search_view);
    searchBoxCollapsed = findViewById(R.id.search_box_collapsed);
    searchBoxExpanded = findViewById(R.id.search_box_expanded);

    setOnClickListener(v -> expand(true, Optional.absent()));
    findViewById(R.id.voice_search_button).setOnClickListener(v -> voiceSearchClicked());
    findViewById(R.id.search_back_button).setOnClickListener(v -> onSearchBackButtonClicked());
    clearButton.setOnClickListener(v -> onSearchClearButtonClicked());
    searchBox.addTextChangedListener(new SearchBoxTextWatcher());
  }

  private void onSearchClearButtonClicked() {
    searchBox.setText("");
  }

  private void onSearchBackButtonClicked() {
    listener.onSearchBackButtonClicked();
    collapse(true);
  }

  private void voiceSearchClicked() {
    listener.onVoiceButtonClicked(
        result -> {
          if (!TextUtils.isEmpty(result)) {
            expand(true, Optional.of(result));
          }
        });
  }

  /** Expand the search bar and populate it with text if any exists. */
  private void expand(boolean animate, Optional<String> text) {
    if (isExpanded) {
      return;
    }
    initialHeight = getHeight();

    int duration = animate ? ANIMATION_DURATION : 0;
    searchBoxExpanded.setVisibility(VISIBLE);
    AnimUtils.crossFadeViews(searchBoxExpanded, searchBoxCollapsed, duration);
    ValueAnimator animator = ValueAnimator.ofFloat(EXPAND_MARGIN_FRACTION_START, 0f);
    animator.addUpdateListener(animation -> setMargins((Float) animation.getAnimatedValue()));
    animator.setDuration(duration);
    animator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationStart(Animator animation) {
            super.onAnimationStart(animation);
            DialerUtils.showInputMethod(searchBox);
            isExpanded = true;
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            super.onAnimationEnd(animation);
            if (text.isPresent()) {
              searchBox.setText(text.get());
            }
            searchBox.requestFocus();
          }
        });
    animator.start();
  }

  /** Collapse the search bar and clear it's text. */
  private void collapse(boolean animate) {
    if (!isExpanded) {
      return;
    }

    int duration = animate ? ANIMATION_DURATION : 0;
    AnimUtils.crossFadeViews(searchBoxCollapsed, searchBoxExpanded, duration);
    ValueAnimator animator = ValueAnimator.ofFloat(0f, EXPAND_MARGIN_FRACTION_START);
    animator.addUpdateListener(animation -> setMargins((Float) animation.getAnimatedValue()));
    animator.setDuration(duration);

    animator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationStart(Animator animation) {
            super.onAnimationStart(animation);
            DialerUtils.hideInputMethod(searchBox);
            isExpanded = false;
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            super.onAnimationEnd(animation);
            searchBox.setText("");
            searchBoxExpanded.setVisibility(INVISIBLE);
          }
        });
    animator.start();
  }

  /**
   * Assigns margins to the search box as a fraction of its maximum margin size
   *
   * @param fraction How large the margins should be as a fraction of their full size
   */
  private void setMargins(float fraction) {
    int margin = (int) (this.margin * fraction);
    MarginLayoutParams params = (MarginLayoutParams) getLayoutParams();
    params.topMargin = margin;
    params.bottomMargin = margin;
    params.leftMargin = margin;
    params.rightMargin = margin;
    searchBoxExpanded.getLayoutParams().height =
        (int) (animationEndHeight - (animationEndHeight - initialHeight) * fraction);
    requestLayout();
  }

  public void setSearchBarListener(SearchBarListener listener) {
    this.listener = listener;
  }

  /** Handles logic for text changes in the search box. */
  private class SearchBoxTextWatcher implements TextWatcher {

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
      clearButton.setVisibility(TextUtils.isEmpty(s) ? GONE : VISIBLE);
      listener.onSearchQueryUpdated(s.toString());
    }
  }
}
