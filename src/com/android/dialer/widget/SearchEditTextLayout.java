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
import android.widget.LinearLayout;

import com.android.dialer.R;

public class SearchEditTextLayout extends LinearLayout {
    private OnKeyListener mPreImeKeyListener;
    private int mTopMargin;
    private int mBottomMargin;
    private int mLeftMargin;
    private int mRightMargin;

    private int mBackgroundColor;

    public SearchEditTextLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mBackgroundColor = getResources().getColor(R.color.searchbox_background_color);
    }

    public void setPreImeKeyListener(OnKeyListener listener) {
        mPreImeKeyListener = listener;
    }

    @Override
    protected void onFinishInflate() {
        MarginLayoutParams params = (MarginLayoutParams) getLayoutParams();
        mTopMargin = params.topMargin;
        mBottomMargin = params.bottomMargin;
        mLeftMargin = params.leftMargin;
        mRightMargin = params.rightMargin;
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

    public void animateExpandOrCollapse(boolean expand) {
        final ValueAnimator animator;
        if (expand) {
            animator = ValueAnimator.ofFloat(1f, 0f);
            setBackgroundColor(mBackgroundColor);
        } else {
            animator = ValueAnimator.ofFloat(0f, 1f);
            setBackgroundResource(R.drawable.rounded_corner);
        }
        animator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final Float fraction = (Float) animation.getAnimatedValue();
                MarginLayoutParams params = (MarginLayoutParams) getLayoutParams();
                params.topMargin = (int) (mTopMargin * fraction);
                params.bottomMargin = (int) (mBottomMargin * fraction);
                params.leftMargin = (int) (mLeftMargin * fraction);
                params.rightMargin = (int) (mRightMargin * fraction);
                requestLayout();
            }
        });
        animator.start();
    }
}