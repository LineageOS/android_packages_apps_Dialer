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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageView;
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
public class PhoneFavoriteListView extends ListView implements SwipeHelperCallback,
        OnDragDropListener {

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
    private final int DRAG_SCROLL_PX_UNIT = 25;

    private boolean mIsDragScrollerRunning = false;
    private int mTouchDownForDragStartX;
    private int mTouchDownForDragStartY;

    private Bitmap mDragShadowBitmap;
    private ImageView mDragShadowOverlay;
    private int mAnimationDuration;

    // X and Y offsets inside the item from where the user grabbed to the
    // child's left coordinate. This is used to aid in the drawing of the drag shadow.
    private int mTouchOffsetToChildLeft;
    private int mTouchOffsetToChildTop;

    private int mDragShadowLeft;
    private int mDragShadowTop;

    private DragDropController mDragDropController = new DragDropController();

    private final float DRAG_SHADOW_ALPHA = 0.7f;

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

    private final AnimatorListenerAdapter mDragShadowOverAnimatorListener =
            new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            if (mDragShadowBitmap != null) {
                mDragShadowBitmap.recycle();
                mDragShadowBitmap = null;
            }
            mDragShadowOverlay.setVisibility(GONE);
            mDragShadowOverlay.setImageBitmap(null);
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
        mAnimationDuration = context.getResources().getInteger(R.integer.fade_duration);
        mDensityScale = getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(context).getScaledPagingTouchSlop();
        mSwipeHelper = new SwipeHelper(context, SwipeHelper.X, this,
                mDensityScale, mTouchSlop);
        setItemsCanFocus(true);
        mDragDropController.addOnDragDropListener(this);
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

    /**
     * TODO: This is all swipe to remove code (nothing to do with drag to remove). This should
     * be cleaned up and removed once drag to remove becomes the only way to remove contacts.
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mTouchDownForDragStartX = (int) ev.getX();
            mTouchDownForDragStartY = (int) ev.getY();
        }
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
        final View view = getViewAtPosition((int) ev.getX(), (int) ev.getY());
        if (view != null &&
                SwipeHelper.isSwipeable(view) &&
                view.getVisibility() != GONE) {
            // If this view is swipable in this listview, then return it. Otherwise
            // return a null view, which will simply be ignored by the swipe helper.
            return view;
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
    public void onDragCancelled(View v) {}

    @Override
    public void onBeginDrag(View v) {
        final View tileRow = (View) v.getParent();

        // We do this so the underlying ScrollView knows that it won't get
        // the chance to intercept events anymore
        requestDisallowInterceptTouchEvent(true);
    }

    /**
     * End of swipe-to-remove code
     */

    @Override
    public boolean dispatchDragEvent(DragEvent event) {
        final int action = event.getAction();
        final int eX = (int) event.getX();
        final int eY = (int) event.getY();
        switch (action) {
            case DragEvent.ACTION_DRAG_STARTED:
                final int[] coordinates = new int[2];
                getLocationOnScreen(coordinates);
                // Calculate the X and Y coordinates of the drag event relative to the view
                final int viewX = eX - coordinates[0];
                final int viewY = eY - coordinates[1];
                final View child = getViewAtPosition(viewX, viewY);

                if (!(child instanceof ContactTileRow)) {
                    // Bail early.
                    return false;
                }

                final ContactTileRow tile = (ContactTileRow) child;

                // Disable drag and drop if there is a contact that has been swiped and is currently
                // in the pending remove state
                if (tile.getTileAdapter().hasPotentialRemoveEntryIndex()) {
                    return false;
                }

                if (!mDragDropController.handleDragStarted(viewX, viewY, tile)) {
                    return false;
                }
                break;
            case DragEvent.ACTION_DRAG_LOCATION:
                mLastDragY = eY;
                final View view = getViewAtPosition(eX, eY);
                mDragDropController.handleDragHovered(eX, eY, view);
                // Kick off {@link #mScrollHandler} if it's not started yet.
                if (!mIsDragScrollerRunning &&
                        // And if the distance traveled while dragging exceeds the touch slop
                        (Math.abs(mLastDragY - mTouchDownForDragStartY) >= 4 * mTouchSlop)) {
                    mIsDragScrollerRunning = true;
                    ensureScrollHandler();
                    mScrollHandler.postDelayed(mDragScroller, SCROLL_HANDLER_DELAY_MILLIS);
                }
                break;
            case DragEvent.ACTION_DRAG_ENTERED:
                final int boundGap = (int) (getHeight() * BOUND_GAP_RATIO);
                mTopScrollBound = (getTop() + boundGap);
                mBottomScrollBound = (getBottom() - boundGap);
                break;
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED:
            case DragEvent.ACTION_DROP:
                ensureScrollHandler();
                mScrollHandler.removeCallbacks(mDragScroller);
                mIsDragScrollerRunning = false;
                // Either a successful drop or it's ended with out drop.
                if (action == DragEvent.ACTION_DROP || action == DragEvent.ACTION_DRAG_ENDED) {
                    mDragDropController.handleDragFinished(eX, eY, false);
                }
                break;
            default:
                break;
        }
        // This ListView will consume the drag events on behalf of its children.
        return true;
    }

    public void setDragShadowOverlay(ImageView overlay) {
        mDragShadowOverlay = overlay;
    }

    /**
     * Find the view under the pointer.
     */
    private View getViewAtPosition(int x, int y) {
        final int count = getChildCount();
        View child;
        for (int childIdx = 0; childIdx < count; childIdx++) {
            child = getChildAt(childIdx);
            if (y >= child.getTop() && y <= child.getBottom()) {
                return child;
            }
        }
        return null;
    }

    private void ensureScrollHandler() {
        if (mScrollHandler == null) {
            mScrollHandler = getHandler();
        }
    }

    public DragDropController getDragDropController() {
        return mDragDropController;
    }

    @Override
    public void onDragStarted(int itemIndex, int x, int y, PhoneFavoriteTileView tileView) {
        if (mDragShadowOverlay == null) {
            return;
        }

        mDragShadowOverlay.clearAnimation();
        mDragShadowBitmap = createDraggedChildBitmap(tileView);
        if (mDragShadowBitmap == null) {
            return;
        }

        if (tileView instanceof PhoneFavoriteRegularRowView) {
            mDragShadowLeft = tileView.getParentRow().getLeft();
            mDragShadowTop = tileView.getParentRow().getTop();
        } else {
            // Square tile is relative to the contact tile,
            // and contact tile is relative to this list view.
            mDragShadowLeft = tileView.getLeft() + tileView.getParentRow().getLeft();
            mDragShadowTop = tileView.getTop() + tileView.getParentRow().getTop();
        }

        mDragShadowOverlay.setImageBitmap(mDragShadowBitmap);
        mDragShadowOverlay.setVisibility(VISIBLE);
        mDragShadowOverlay.setAlpha(DRAG_SHADOW_ALPHA);

        mDragShadowOverlay.setX(mDragShadowLeft);
        mDragShadowOverlay.setY(mDragShadowTop);

        // x and y passed in are the coordinates of where the user has touched down,
        // calculate the offset to the top left coordinate of the dragged child.  This
        // will be used for drawing the drag shadow.
        mTouchOffsetToChildLeft = x - mDragShadowLeft;
        mTouchOffsetToChildTop = y - mDragShadowTop;
    }

    @Override
    public void onDragHovered(int itemIndex, int x, int y) {
        // Update the drag shadow location.
        mDragShadowLeft = x - mTouchOffsetToChildLeft;
        mDragShadowTop = y - mTouchOffsetToChildTop;
        // Draw the drag shadow at its last known location if the drag shadow exists.
        if (mDragShadowOverlay != null) {
            mDragShadowOverlay.setX(mDragShadowLeft);
            mDragShadowOverlay.setY(mDragShadowTop);
        }
    }

    @Override
    public void onDragFinished(int x, int y) {
        // Update the drag shadow location.
        mDragShadowLeft = x - mTouchOffsetToChildLeft;
        mDragShadowTop = y - mTouchOffsetToChildTop;

        if (mDragShadowOverlay != null) {
            mDragShadowOverlay.clearAnimation();
            mDragShadowOverlay.animate().alpha(0.0f)
                    .setDuration(mAnimationDuration)
                    .setListener(mDragShadowOverAnimatorListener)
                    .start();
        }
    }

    @Override
    public void onDroppedOnRemove() {}

    private Bitmap createDraggedChildBitmap(View view) {
        view.setDrawingCacheEnabled(true);
        final Bitmap cache = view.getDrawingCache();

        Bitmap bitmap = null;
        if (cache != null) {
            try {
                bitmap = cache.copy(Bitmap.Config.ARGB_8888, false);
            } catch (final OutOfMemoryError e) {
                Log.w(LOG_TAG, "Failed to copy bitmap from Drawing cache", e);
                bitmap = null;
            }
        }

        view.destroyDrawingCache();
        view.setDrawingCacheEnabled(false);

        return bitmap;
    }
}
