/*
 * Copyright (C) 2012 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.dialer.list;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnDragListener;
import android.view.ViewConfiguration;
import android.widget.ListView;

import com.android.dialer.R;
import com.android.dialer.list.PhoneFavoritesTileAdapter.ContactTileRow;
import com.android.dialer.list.SwipeHelper.OnItemGestureListener;
import com.android.dialer.list.SwipeHelper.SwipeHelperCallback;

/**
 * The ListView composed of {@link ContactTileRow}.
 * This ListView handles both
 * - Swiping, which is borrowed from packages/apps/UnifiedEmail (com.android.mail.ui.Swipeable)
 * - Drag and drop
 */
public class PhoneFavoriteListView extends ListView implements
        SwipeHelperCallback, OnDragListener {

    public static final String LOG_TAG = PhoneFavoriteListView.class.getSimpleName();

    private SwipeHelper mSwipeHelper;
    private boolean mEnableSwipe = true;

    private OnItemGestureListener mOnItemGestureListener;

    private float mDensityScale;
    private float mTouchSlop;

    private int mTopScrollBound;
    private int mBottomScrollBound;
    private int mLastDragY;

    private Handler mScrollHandler;
    private final long SCROLL_HANDLER_DELAY_MILLIS = 5;
    private final int DRAG_SCROLL_PX_UNIT = 10;

    /**
     * {@link #mTopScrollBound} and {@link mBottomScrollBound} will be
     * offseted to the top / bottom by {@link #getHeight} * {@link #BOUND_GAP_RATIO} pixels.
     */
    private final float BOUND_GAP_RATIO = 0.2f;

    private final Runnable mDragScroller = new Runnable() {
        @Override
        public void run() {
            if (mLastDragY <= mTopScrollBound) {
                smoothScrollBy(-DRAG_SCROLL_PX_UNIT, (int) SCROLL_HANDLER_DELAY_MILLIS);
            } else if (mLastDragY >= mBottomScrollBound) {
                smoothScrollBy(DRAG_SCROLL_PX_UNIT, (int) SCROLL_HANDLER_DELAY_MILLIS);
            }
            mScrollHandler.postDelayed(this, SCROLL_HANDLER_DELAY_MILLIS);
        }
    };

    public PhoneFavoriteListView(Context context) {
        this(context, null);
    }

    public PhoneFavoriteListView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public PhoneFavoriteListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mDensityScale = getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(context).getScaledPagingTouchSlop();
        mSwipeHelper = new SwipeHelper(context, SwipeHelper.X, this,
                mDensityScale, mTouchSlop);
        setItemsCanFocus(true);
        setOnDragListener(this);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDensityScale= getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(getContext()).getScaledPagingTouchSlop();
        mSwipeHelper.setDensityScale(mDensityScale);
        mSwipeHelper.setPagingTouchSlop(mTouchSlop);
    }

    /**
     * Enable swipe gestures.
     */
    public void enableSwipe(boolean enable) {
        mEnableSwipe = enable;
    }

    public boolean isSwipeEnabled() {
        return mEnableSwipe && mOnItemGestureListener.isSwipeEnabled();
    }

    public void setOnItemSwipeListener(OnItemGestureListener listener) {
        mOnItemGestureListener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isSwipeEnabled()) {
            return mSwipeHelper.onInterceptTouchEvent(ev) || super.onInterceptTouchEvent(ev);
        } else {
            return super.onInterceptTouchEvent(ev);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mOnItemGestureListener != null) {
            mOnItemGestureListener.onTouch();
        }
        if (isSwipeEnabled()) {
            return mSwipeHelper.onTouchEvent(ev) || super.onTouchEvent(ev);
        } else {
            return super.onTouchEvent(ev);
        }
    }

    @Override
    public View getChildAtPosition(MotionEvent ev) {
        // find the view under the pointer, accounting for GONE views
        final int count = getChildCount();
        final int touchY = (int) ev.getY();
        View slidingChild;
        for (int childIdx = 0; childIdx < count; childIdx++) {
            slidingChild = getChildAt(childIdx);
            if (slidingChild.getVisibility() == GONE) {
                continue;
            }
            if (touchY >= slidingChild.getTop() && touchY <= slidingChild.getBottom()) {
                if (SwipeHelper.isSwipeable(slidingChild)) {
                    // If this view is swipable in this listview, then return it. Otherwise
                    // return a null view, which will simply be ignored by the swipe helper.
                    return slidingChild;
                } else {
                    return null;
                }
            }
        }
        return null;
    }

    @Override
    public View getChildContentView(View view) {
        return view.findViewById(R.id.contact_favorite_card);
    }

    @Override
    public void onScroll() {}

    @Override
    public boolean canChildBeDismissed(View v) {
        return SwipeHelper.isSwipeable(v);
    }

    @Override
    public void onChildDismissed(final View v) {
        if (v != null) {
            if (mOnItemGestureListener != null) {
                mOnItemGestureListener.onSwipe(v);
            }
        }
    }

    @Override
    public void onDragCancelled(View v) {
    }

    @Override
    public void onBeginDrag(View v) {
        final View tileRow = (View) v.getParent();

        // We do this so the underlying ScrollView knows that it won't get
        // the chance to intercept events anymore
        requestDisallowInterceptTouchEvent(true);
    }

    @Override
    public boolean dispatchDragEvent(DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_LOCATION:
                if (mScrollHandler == null) {
                    mScrollHandler = getHandler();
                }
                mLastDragY = (int) event.getY();
                mScrollHandler.postDelayed(mDragScroller, SCROLL_HANDLER_DELAY_MILLIS);
                break;
            case DragEvent.ACTION_DRAG_ENTERED:
                final int boundGap = (int) (getHeight() * BOUND_GAP_RATIO);
                mTopScrollBound = (getTop() + boundGap);
                mBottomScrollBound = (getBottom() - boundGap);
                break;
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED:
                mScrollHandler.removeCallbacks(mDragScroller);
                break;
            case DragEvent.ACTION_DRAG_STARTED:
                // Not a receiver
            case DragEvent.ACTION_DROP:
                // Not a receiver
            default:
                break;
        }
        return super.dispatchDragEvent(event);
    }

    @Override
    public boolean onDrag(View v, DragEvent event) {
        return true;
    }
}
