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
import android.support.annotation.StringRes;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.android.dialer.animation.AnimUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.UiUtil;
import com.android.dialer.util.DialerUtils;
import com.google.common.base.Optional;

/** Search bar for {@link MainToolbar}. Mostly used to handle expand and collapse animation. */
final class SearchBarView extends FrameLayout {

  private static final int ANIMATION_DURATION = 200;
  private static final float EXPAND_MARGIN_FRACTION_START = 0.8f;

  private final float margin;
  private final float animationEndHeight;
  private final float animationStartHeight;

  private SearchBarListener listener;
  private EditText searchBox;
  private TextView searchBoxTextView;
  // This useful for when the query didn't actually change. We want to avoid making excessive calls
  // where we can since IPCs can take a long time on slow networks.
  private boolean skipLatestTextChange;

  private boolean isExpanded;
  private View searchBoxCollapsed;
  private View searchBoxExpanded;
  private View clearButton;

  public SearchBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    margin = getContext().getResources().getDimension(R.dimen.search_bar_margin);
    animationEndHeight =
        getContext().getResources().getDimension(R.dimen.expanded_search_bar_height);
    animationStartHeight =
        getContext().getResources().getDimension(R.dimen.collapsed_search_bar_height);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    clearButton = findViewById(R.id.search_clear_button);
    searchBox = findViewById(R.id.search_view);
    searchBoxTextView = findViewById(R.id.search_box_start_search);
    searchBoxCollapsed = findViewById(R.id.search_box_collapsed);
    searchBoxExpanded = findViewById(R.id.search_box_expanded);

    setOnClickListener(v -> listener.onSearchBarClicked());
    findViewById(R.id.voice_search_button).setOnClickListener(v -> voiceSearchClicked());
    findViewById(R.id.search_back_button).setOnClickListener(v -> onSearchBackButtonClicked());
    clearButton.setOnClickListener(v -> onSearchClearButtonClicked());
    searchBox.addTextChangedListener(new SearchBoxTextWatcher());
  }

  private void onSearchClearButtonClicked() {
    searchBox.setText("");
  }

  private void onSearchBackButtonClicked() {
    if (!isExpanded) {
      return;
    }

    listener.onSearchBackButtonClicked();
    collapse(true);
  }

  private void voiceSearchClicked() {
    listener.onVoiceButtonClicked(
        result -> {
          if (!TextUtils.isEmpty(result)) {
            expand(/* animate */ true, Optional.of(result), /* requestFocus */ true);
          }
        });
  }

  /**
   * Expand the search bar and populate it with text if any exists.
   *
   * @param requestFocus should be false if showing the dialpad
   */
  /* package-private */ void expand(boolean animate, Optional<String> text, boolean requestFocus) {
    if (isExpanded) {
      return;
    }

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
            // Don't request focus unless we're actually showing the search box, otherwise
            // physical/bluetooth keyboards will type into this box when the dialpad is open.
            if (requestFocus) {
              searchBox.requestFocus();
            }
            setBackgroundResource(R.drawable.search_bar_background);
          }
        });
    animator.start();
  }

  /** Collapse the search bar and clear it's text. */
  /* package-private */ void collapse(boolean animate) {
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
            setBackgroundResource(R.drawable.search_bar_background_rounded_corners);
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
        (int) (animationEndHeight - (animationEndHeight - animationStartHeight) * fraction);
  }

  /* package-private */ void setSearchBarListener(@NonNull SearchBarListener listener) {
    this.listener = Assert.isNotNull(listener);
  }

  public String getQuery() {
    return searchBox.getText().toString();
  }

  public boolean isExpanded() {
    return isExpanded;
  }

  public void setQueryWithoutUpdate(String query) {
    skipLatestTextChange = true;
    searchBox.setText(query);
    searchBox.setSelection(searchBox.getText().length());
  }

  public void hideKeyboard() {
    UiUtil.hideKeyboardFrom(getContext(), searchBox);
  }

  public void showKeyboard() {
    UiUtil.forceOpenKeyboardFrom(getContext(), searchBox);
  }

  public void setHint(@StringRes int hint) {
    searchBox.setHint(hint);
    searchBoxTextView.setText(hint);
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
      if (skipLatestTextChange) {
        skipLatestTextChange = false;
        return;
      }

      // afterTextChanged is called each time the device is rotated (or the activity is recreated).
      // That means that this method could potentially be called before the listener is set and
      // we should check if it's null. In the case that it is null, assert that the query is empty
      // because the listener must be notified of non-empty queries.
      if (listener != null) {
        listener.onSearchQueryUpdated(s.toString());
      } else {
        Assert.checkArgument(TextUtils.isEmpty(s.toString()));
      }
    }
  }
}
