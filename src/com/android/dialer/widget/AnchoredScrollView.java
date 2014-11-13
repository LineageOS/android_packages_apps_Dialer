/*
 * Copyright (C) 2013 The CyanogenMod Project
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
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

import com.android.dialer.R;

/**
 * A ScrollView that makes sure that the part of the 'anchored' view
 * that is defined by the anchor is never scrolled out of view.
 */
public class AnchoredScrollView extends ScrollView {
    private int mAnchorId;
    private int mAnchoredId;
    private boolean mAnchorAtBottom;

    private View mAnchorView;
    private View mAnchoredView;
    private int mOrigAnchoredTop;

    public AnchoredScrollView(Context context) {
        this(context, null);
    }

    public AnchoredScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.scrollViewStyle);
    }

    public AnchoredScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a =
            context.obtainStyledAttributes(attrs, R.styleable.AnchoredScrollView);

        mAnchorId = a.getResourceId(R.styleable.AnchoredScrollView_anchorView, 0);
        mAnchoredId = a.getResourceId(R.styleable.AnchoredScrollView_anchoredView, 0);
        mAnchorAtBottom = a.getBoolean(R.styleable.AnchoredScrollView_anchorAtBottom, false);

        a.recycle();
    }

    private boolean ensureViews() {
        if (mAnchorView == null) {
            mAnchorView = findViewTraversal(mAnchorId);
        }
        if (mAnchoredView == null) {
            mAnchoredView = findViewTraversal(mAnchoredId);
        }

        return mAnchorView != null && mAnchoredView != null;
    }

    private int getAnchor() {
        return mAnchorAtBottom ? mAnchorView.getBottom() : mAnchorView.getTop();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (ensureViews()) {
            mOrigAnchoredTop = mAnchoredView.getTop();
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);

        if (!ensureViews()) {
            return;
        }

        int currentOffset = mAnchoredView.getTop() - mOrigAnchoredTop;
        int matchDistance = getAnchor() - getScrollY();
        int desiredOffset = Math.max(-matchDistance, 0);
        int neededOffset = desiredOffset - currentOffset;

        if (neededOffset != 0) {
            mAnchoredView.offsetTopAndBottom(neededOffset);
        }
    }
}
