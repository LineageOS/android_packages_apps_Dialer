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

package com.android.dialer.widget;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.android.contacts.common.animation.AnimUtils;
import com.android.dialer.R;

public class SearchEditTextLayout extends FrameLayout {
    private static final int ANIMATION_DURATION = 200;

    private OnKeyListener mPreImeKeyListener;
    private int mTopMargin;
    private int mBottomMargin;
    private int mLeftMargin;
    private int mRightMargin;

    private float mCollapsedElevation;

    /* Subclass-visible for testing */
    protected boolean mIsExpanded = false;
    protected boolean mIsFadedOut = false;

    private View mCollapsed;
    private View mExpanded;
    private EditText mSearchView;

    private ValueAnimator mAnimator;

    private OnBackButtonClickedListener mOnBackButtonClickedListener;

    /**
     * Listener for the back button next to the search view being pressed
     */
    public interface OnBackButtonClickedListener {
        public void onBackButtonClicked();
    }

    public SearchEditTextLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setPreImeKeyListener(OnKeyListener listener) {
        mPreImeKeyListener = listener;
    }

    public void setOnBackButtonClickedListener(OnBackButtonClickedListener listener) {
        mOnBackButtonClickedListener = listener;
    }

    @Override
    protected void onFinishInflate() {
        MarginLayoutParams params = (MarginLayoutParams) getLayoutParams();
        mTopMargin = params.topMargin;
        mBottomMargin = params.bottomMargin;
        mLeftMargin = params.leftMargin;
        mRightMargin = params.rightMargin;

        mCollapsedElevation = getElevation();

        mCollapsed = findViewById(R.id.search_box_collapsed);
        mExpanded = findViewById(R.id.search_box_expanded);
        mSearchView = (EditText) mExpanded.findViewById(R.id.search_view);

        mSearchView.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    showInputMethod(v);
                }
            }
        });

        findViewById(R.id.search_close_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mSearchView.setText(null);
            }
        });

        findViewById(R.id.search_back_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mOnBackButtonClickedListener != null) {
                    mOnBackButtonClickedListener.onBackButtonClicked();
                }
            }
        });

        super.onFinishInflate();
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (mPreImeKeyListener != null) {
            if (mPreImeKeyListener.onKey(this, event.getKeyCode(), event)) {
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
        mIsFadedOut = true;
    }

    public void fadeIn() {
        AnimUtils.fadeIn(this, ANIMATION_DURATION);
        mIsFadedOut = false;
    }

    public void setVisible(boolean visible) {
        if (visible) {
            setAlpha(1);
            setVisibility(View.VISIBLE);
            mIsFadedOut = false;
        } else {
            setAlpha(0);
            setVisibility(View.GONE);
            mIsFadedOut = true;
        }
    }
    public void expand(boolean animate, boolean requestFocus) {
        if (animate) {
            AnimUtils.crossFadeViews(mExpanded, mCollapsed, ANIMATION_DURATION);
            mAnimator = ValueAnimator.ofFloat(1f, 0f);
            prepareAnimator(true);
        } else {
            mExpanded.setVisibility(View.VISIBLE);
            mExpanded.setAlpha(1);
            setMargins(0f);
            mCollapsed.setVisibility(View.GONE);
        }

        setBackgroundResource(R.drawable.search_shadow);
        setElevation(0);
        if (requestFocus) {
            mSearchView.requestFocus();
        }
        mIsExpanded = true;
    }

    public void collapse(boolean animate) {
        if (animate) {
            AnimUtils.crossFadeViews(mCollapsed, mExpanded, ANIMATION_DURATION);
            mAnimator = ValueAnimator.ofFloat(0f, 1f);
            prepareAnimator(false);
        } else {
            mCollapsed.setVisibility(View.VISIBLE);
            mCollapsed.setAlpha(1);
            setMargins(1f);
            mExpanded.setVisibility(View.GONE);
        }

        mIsExpanded = false;
        setElevation(mCollapsedElevation);
        setBackgroundResource(R.drawable.rounded_corner);
    }

    private void prepareAnimator(final boolean expand) {
        if (mAnimator != null) {
            mAnimator.cancel();
        }

        mAnimator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final Float fraction = (Float) animation.getAnimatedValue();
                setMargins(fraction);
            }
        });

        mAnimator.setDuration(ANIMATION_DURATION);
        mAnimator.start();
    }

    public boolean isExpanded() {
        return mIsExpanded;
    }

    public boolean isFadedOut() {
        return mIsFadedOut;
    }

    private void showInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, 0);
        }
    }

    /**
     * Assigns margins to the search box as a fraction of its maximum margin size
     *
     * @param fraction How large the margins should be as a fraction of their full size
     */
    private void setMargins(float fraction) {
        MarginLayoutParams params = (MarginLayoutParams) getLayoutParams();
        params.topMargin = (int) (mTopMargin * fraction);
        params.bottomMargin = (int) (mBottomMargin * fraction);
        params.leftMargin = (int) (mLeftMargin * fraction);
        params.rightMargin = (int) (mRightMargin * fraction);
        requestLayout();
    }
}