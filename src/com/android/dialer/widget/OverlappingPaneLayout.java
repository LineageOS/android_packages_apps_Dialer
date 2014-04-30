/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;

/**
 * A custom layout that aligns its child views vertically as two panes, and allows for the bottom
 * pane to be dragged upwards to overlap and hide the top pane. This layout is adapted from
 * {@link android.support.v4.widget.SlidingPaneLayout}.
 */
public class OverlappingPaneLayout extends ViewGroup {
    private static final String TAG = "SlidingPaneLayout";

    /**
     * Default size of the overhang for a pane in the open state.
     * At least this much of a sliding pane will remain visible.
     * This indicates that there is more content available and provides
     * a "physical" edge to grab to pull it closed.
     */
    private static final int DEFAULT_OVERHANG_SIZE = 32; // dp;

    /**
     * If no fade color is given by default it will fade to 80% gray.
     */
    private static final int DEFAULT_FADE_COLOR = 0xcccccccc;

    /**
     * Minimum velocity that will be detected as a fling
     */
    private static final int MIN_FLING_VELOCITY = 400; // dips per second

    /**
     * The size of the overhang in pixels.
     * This is the minimum section of the sliding panel that will
     * be visible in the open state to allow for a closing drag.
     */
    private final int mOverhangSize;

    /**
     * True if a panel can slide with the current measurements
     */
    private boolean mCanSlide;

    /**
     * The child view that can slide, if any.
     */
    private View mSlideableView;

    /**
     * The view that can be used to start the drag with.
     */
    private View mCapturableView;

    /**
     * How far the panel is offset from its closed position.
     * range [0, 1] where 0 = closed, 1 = open.
     */
    private float mSlideOffset;

    /**
     * How far in pixels the slideable panel may move.
     */
    private int mSlideRange;

    /**
     * A panel view is locked into internal scrolling or another condition that
     * is preventing a drag.
     */
    private boolean mIsUnableToDrag;

    private float mInitialMotionX;
    private float mInitialMotionY;

    private PanelSlideListener mPanelSlideListener;

    private final ViewDragHelper mDragHelper;

    /**
     * Stores whether or not the pane was open the last time it was slideable.
     * If open/close operations are invoked this state is modified. Used by
     * instance state save/restore.
     */
    private boolean mPreservedOpenState;
    private boolean mFirstLayout = true;

    private final Rect mTmpRect = new Rect();

    /**
     * Listener for monitoring events about sliding panes.
     */
    public interface PanelSlideListener {
        /**
         * Called when a sliding pane's position changes.
         * @param panel The child view that was moved
         * @param slideOffset The new offset of this sliding pane within its range, from 0-1
         */
        public void onPanelSlide(View panel, float slideOffset);
        /**
         * Called when a sliding pane becomes slid completely open. The pane may or may not
         * be interactive at this point depending on how much of the pane is visible.
         * @param panel The child view that was slid to an open position, revealing other panes
         */
        public void onPanelOpened(View panel);

        /**
         * Called when a sliding pane becomes slid completely closed. The pane is now guaranteed
         * to be interactive. It may now obscure other views in the layout.
         * @param panel The child view that was slid to a closed position
         */
        public void onPanelClosed(View panel);
    }

    public OverlappingPaneLayout(Context context) {
        this(context, null);
    }

    public OverlappingPaneLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OverlappingPaneLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final float density = context.getResources().getDisplayMetrics().density;
        mOverhangSize = (int) (DEFAULT_OVERHANG_SIZE * density + 0.5f);

        setWillNotDraw(false);

        ViewCompat.setAccessibilityDelegate(this, new AccessibilityDelegate());
        ViewCompat.setImportantForAccessibility(this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);

        mDragHelper = ViewDragHelper.create(this, 0.5f, new DragHelperCallback());
        mDragHelper.setMinVelocity(MIN_FLING_VELOCITY * density);
    }

    /**
     * Set the view that can be used to start dragging the sliding pane.
     */
    public void setCapturableView(View capturableView) {
        mCapturableView = capturableView;
    }

    public void setPanelSlideListener(PanelSlideListener listener) {
        mPanelSlideListener = listener;
    }

    void dispatchOnPanelSlide(View panel) {
        if (mPanelSlideListener != null) {
            mPanelSlideListener.onPanelSlide(panel, mSlideOffset);
        }
    }

    void dispatchOnPanelOpened(View panel) {
        if (mPanelSlideListener != null) {
            mPanelSlideListener.onPanelOpened(panel);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    void dispatchOnPanelClosed(View panel) {
        if (mPanelSlideListener != null) {
            mPanelSlideListener.onPanelClosed(panel);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    void updateObscuredViewsVisibility(View panel) {
        final int startBound = getPaddingTop();
        final int endBound = getHeight() - getPaddingBottom();

        final int leftBound = getPaddingLeft();
        final int rightBound = getWidth() - getPaddingRight();
        final int left;
        final int right;
        final int top;
        final int bottom;
        if (panel != null && viewIsOpaque(panel)) {
            left = panel.getLeft();
            right = panel.getRight();
            top = panel.getTop();
            bottom = panel.getBottom();
        } else {
            left = right = top = bottom = 0;
        }

        for (int i = 0, childCount = getChildCount(); i < childCount; i++) {
            final View child = getChildAt(i);

            if (child == panel) {
                // There are still more children above the panel but they won't be affected.
                break;
            }

            final int clampedChildLeft = Math.max(leftBound, child.getLeft());
            final int clampedChildRight = Math.min(rightBound, child.getRight());
            final int clampedChildTop = Math.max(startBound, child.getTop());
            final int clampedChildBottom = Math.min(endBound, child.getBottom());

            final int vis;
            if (clampedChildLeft >= left && clampedChildTop >= top &&
                    clampedChildRight <= right && clampedChildBottom <= bottom) {
                vis = INVISIBLE;
            } else {
                vis = VISIBLE;
            }
            child.setVisibility(vis);
        }
    }

    void setAllChildrenVisible() {
        for (int i = 0, childCount = getChildCount(); i < childCount; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == INVISIBLE) {
                child.setVisibility(VISIBLE);
            }
        }
    }

    private static boolean viewIsOpaque(View v) {
        if (ViewCompat.isOpaque(v)) return true;

        final Drawable bg = v.getBackground();
        if (bg != null) {
            return bg.getOpacity() == PixelFormat.OPAQUE;
        }
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mFirstLayout = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mFirstLayout = true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode != MeasureSpec.EXACTLY) {
            if (isInEditMode()) {
                // Don't crash the layout editor. Consume all of the space if specified
                // or pick a magic number from thin air otherwise.
                // TODO Better communication with tools of this bogus state.
                // It will crash on a real device.
                if (widthMode == MeasureSpec.AT_MOST) {
                    widthMode = MeasureSpec.EXACTLY;
                } else if (widthMode == MeasureSpec.UNSPECIFIED) {
                    widthMode = MeasureSpec.EXACTLY;
                    widthSize = 300;
                }
            } else {
                throw new IllegalStateException("Width must have an exact value or MATCH_PARENT");
            }
        } else if (heightMode == MeasureSpec.UNSPECIFIED) {
            if (isInEditMode()) {
                // Don't crash the layout editor. Pick a magic number from thin air instead.
                // TODO Better communication with tools of this bogus state.
                // It will crash on a real device.
                if (heightMode == MeasureSpec.UNSPECIFIED) {
                    heightMode = MeasureSpec.AT_MOST;
                    heightSize = 300;
                }
            } else {
                throw new IllegalStateException("Height must not be UNSPECIFIED");
            }
        }

        int layoutWidth = 0;
        int maxLayoutWidth = -1;
        switch (widthMode) {
            case MeasureSpec.EXACTLY:
                layoutWidth = maxLayoutWidth = widthSize - getPaddingLeft() - getPaddingRight();
                break;
            case MeasureSpec.AT_MOST:
                maxLayoutWidth = widthSize - getPaddingLeft() - getPaddingRight();
                break;
        }

        float weightSum = 0;
        boolean canSlide = false;
        final int heightAvailable = heightSize - getPaddingTop() - getPaddingBottom();
        int heightRemaining = heightAvailable;
        final int childCount = getChildCount();

        if (childCount > 2) {
            Log.e(TAG, "onMeasure: More than two child views are not supported.");
        }

        // We'll find the current one below.
        mSlideableView = null;

        // First pass. Measure based on child LayoutParams width/height.
        // Weight will incur a second pass.
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if (child.getVisibility() == GONE) {
                continue;
            }

            if (lp.weight > 0) {
                weightSum += lp.weight;

                // If we have no height, weight is the only contributor to the final size.
                // Measure this view on the weight pass only.
                if (lp.height == 0) continue;
            }

            int childHeightSpec;
            final int verticalMargin = lp.topMargin + lp.bottomMargin;
            if (lp.height == LayoutParams.WRAP_CONTENT) {
                childHeightSpec = MeasureSpec.makeMeasureSpec(heightAvailable - verticalMargin,
                        MeasureSpec.AT_MOST);
            } else if (lp.height == LayoutParams.MATCH_PARENT) {
                childHeightSpec = MeasureSpec.makeMeasureSpec(heightAvailable - verticalMargin,
                        MeasureSpec.EXACTLY);
            } else {
                childHeightSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
            }

            int childWidthSpec;
            if (lp.width == LayoutParams.WRAP_CONTENT) {
                childWidthSpec = MeasureSpec.makeMeasureSpec(maxLayoutWidth, MeasureSpec.AT_MOST);
            } else if (lp.width == LayoutParams.MATCH_PARENT) {
                childWidthSpec = MeasureSpec.makeMeasureSpec(maxLayoutWidth, MeasureSpec.EXACTLY);
            } else {
                childWidthSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
            }

            child.measure(childWidthSpec, childHeightSpec);
            final int childWidth = child.getMeasuredWidth();
            final int childHeight = child.getMeasuredHeight();

            if (widthMode == MeasureSpec.AT_MOST && childWidth > layoutWidth) {
                layoutWidth = Math.min(childWidth, maxLayoutWidth);
            }

            heightRemaining -= childHeight;
            canSlide |= lp.slideable = heightRemaining < 0;
            if (lp.slideable) {
                mSlideableView = child;
            }
        }

        // Resolve weight and make sure non-sliding panels are smaller than the full screen.
        if (canSlide || weightSum > 0) {
            final int fixedPanelHeightLimit = heightAvailable - mOverhangSize;

            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);

                if (child.getVisibility() == GONE) {
                    continue;
                }

                final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                if (child.getVisibility() == GONE) {
                    continue;
                }

                final boolean skippedFirstPass = lp.height == 0 && lp.weight > 0;
                final int measuredHeight = skippedFirstPass ? 0 : child.getMeasuredHeight();
                if (canSlide && child != mSlideableView) {
                    if (lp.height < 0 && (measuredHeight > fixedPanelHeightLimit || lp.weight > 0)) {
                        // Fixed panels in a sliding configuration should
                        // be clamped to the fixed panel limit.
                        final int childWidthSpec;
                        if (skippedFirstPass) {
                            // Do initial width measurement if we skipped measuring this view
                            // the first time around.
                            if (lp.width == LayoutParams.WRAP_CONTENT) {
                                childWidthSpec = MeasureSpec.makeMeasureSpec(maxLayoutWidth,
                                        MeasureSpec.AT_MOST);
                            } else if (lp.height == LayoutParams.MATCH_PARENT) {
                                childWidthSpec = MeasureSpec.makeMeasureSpec(maxLayoutWidth,
                                        MeasureSpec.EXACTLY);
                            } else {
                                childWidthSpec = MeasureSpec.makeMeasureSpec(lp.width,
                                        MeasureSpec.EXACTLY);
                            }
                        } else {
                            childWidthSpec = MeasureSpec.makeMeasureSpec(
                                    child.getMeasuredWidth(), MeasureSpec.EXACTLY);
                        }
                        final int childHeightSpec = MeasureSpec.makeMeasureSpec(
                                fixedPanelHeightLimit, MeasureSpec.EXACTLY);
                        child.measure(childWidthSpec, childHeightSpec);
                    }
                } else if (lp.weight > 0) {
                    int childWidthSpec;
                    if (lp.height == 0) {
                        // This was skipped the first time; figure out a real width spec.
                        if (lp.width == LayoutParams.WRAP_CONTENT) {
                            childWidthSpec = MeasureSpec.makeMeasureSpec(maxLayoutWidth,
                                    MeasureSpec.AT_MOST);
                        } else if (lp.width == LayoutParams.MATCH_PARENT) {
                            childWidthSpec = MeasureSpec.makeMeasureSpec(maxLayoutWidth,
                                    MeasureSpec.EXACTLY);
                        } else {
                            childWidthSpec = MeasureSpec.makeMeasureSpec(lp.width,
                                    MeasureSpec.EXACTLY);
                        }
                    } else {
                        childWidthSpec = MeasureSpec.makeMeasureSpec(
                                child.getMeasuredWidth(), MeasureSpec.EXACTLY);
                    }

                    if (canSlide) {
                        // Consume available space
                        final int verticalMargin = lp.topMargin + lp.bottomMargin;
                        final int newHeight = heightAvailable - verticalMargin;
                        final int childHeightSpec = MeasureSpec.makeMeasureSpec(
                                newHeight, MeasureSpec.EXACTLY);
                        if (measuredHeight != newHeight) {
                            child.measure(childWidthSpec, childHeightSpec);
                        }
                    } else {
                        // Distribute the extra width proportionally similar to LinearLayout
                        final int heightToDistribute = Math.max(0, heightRemaining);
                        final int addedHeight = (int) (lp.weight * heightToDistribute / weightSum);
                        final int childHeightSpec = MeasureSpec.makeMeasureSpec(
                                measuredHeight + addedHeight, MeasureSpec.EXACTLY);
                        child.measure(childWidthSpec, childHeightSpec);
                    }
                }
            }
        }

        final int measuredHeight = heightSize;
        final int measuredWidth = layoutWidth + getPaddingLeft() + getPaddingRight();

        setMeasuredDimension(measuredWidth, measuredHeight);
        mCanSlide = canSlide;

        if (mDragHelper.getViewDragState() != ViewDragHelper.STATE_IDLE && !canSlide) {
            // Cancel scrolling in progress, it's no longer relevant.
            mDragHelper.abort();
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mDragHelper.setEdgeTrackingEnabled(ViewDragHelper.EDGE_TOP);

        final int height = b - t;
        final int paddingTop = getPaddingTop();
        final int paddingBottom = getPaddingBottom();
        final int paddingLeft = getPaddingLeft();

        final int childCount = getChildCount();
        int yStart = paddingTop;
        int nextYStart = yStart;

        if (mFirstLayout) {
            mSlideOffset = mCanSlide && mPreservedOpenState ? 1.f : 0.f;
        }

        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            final int childHeight = child.getMeasuredHeight();

            if (lp.slideable) {
                final int margin = lp.topMargin + lp.bottomMargin;
                final int range = Math.min(nextYStart,
                        height - paddingBottom - mOverhangSize) - yStart - margin;
                mSlideRange = range;
                final int lpMargin = lp.topMargin;
                final int pos = (int) (range * mSlideOffset);
                yStart += pos + lpMargin;
                mSlideOffset = (float) pos / mSlideRange;
            } else {
                yStart = nextYStart;
            }

            final int childTop = yStart;
            final int childBottom = childTop + childHeight;
            final int childLeft = paddingLeft;
            final int childRight = childLeft + child.getMeasuredWidth();

            child.layout(childLeft, childTop, childRight, childBottom);

            nextYStart += child.getHeight();
        }

        if (mFirstLayout) {
            updateObscuredViewsVisibility(mSlideableView);
        }

        mFirstLayout = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Recalculate sliding panes and their details
        if (h != oldh) {
            mFirstLayout = true;
        }
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        if (!isInTouchMode() && !mCanSlide) {
            mPreservedOpenState = child == mSlideableView;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = MotionEventCompat.getActionMasked(ev);

        // Preserve the open state based on the last view that was touched.
        if (!mCanSlide && action == MotionEvent.ACTION_DOWN && getChildCount() > 1) {
            // After the first things will be slideable.
            final View secondChild = getChildAt(1);
            if (secondChild != null) {
                mPreservedOpenState = !mDragHelper.isViewUnder(secondChild,
                        (int) ev.getX(), (int) ev.getY());
            }
        }

        if (!mCanSlide || (mIsUnableToDrag && action != MotionEvent.ACTION_DOWN)) {
            mDragHelper.cancel();
            return super.onInterceptTouchEvent(ev);
        }

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            mDragHelper.cancel();
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                mIsUnableToDrag = false;
                final float x = ev.getX();
                final float y = ev.getY();
                mInitialMotionX = x;
                mInitialMotionY = y;

                break;
            }

            case MotionEvent.ACTION_MOVE: {
                final float x = ev.getX();
                final float y = ev.getY();
                final float adx = Math.abs(x - mInitialMotionX);
                final float ady = Math.abs(y - mInitialMotionY);
                final int slop = mDragHelper.getTouchSlop();
                if (ady > slop && adx > ady || !isCapturableViewUnder((int) x, (int) y)) {
                    mDragHelper.cancel();
                    mIsUnableToDrag = true;
                    return false;
                }
            }
        }

        final boolean interceptForDrag = mDragHelper.shouldInterceptTouchEvent(ev);

        return interceptForDrag;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mCanSlide) {
            return super.onTouchEvent(ev);
        }

        mDragHelper.processTouchEvent(ev);

        final int action = ev.getAction();
        boolean wantTouchEvents = true;

        switch (action & MotionEventCompat.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                mInitialMotionX = x;
                mInitialMotionY = y;
                break;
            }
        }

        return wantTouchEvents;
    }

    private boolean closePane(View pane, int initialVelocity) {
        if (mFirstLayout || smoothSlideTo(0.f, initialVelocity)) {
            mPreservedOpenState = false;
            return true;
        }
        return false;
    }

    private boolean openPane(View pane, int initialVelocity) {
        if (mFirstLayout || smoothSlideTo(1.f, initialVelocity)) {
            mPreservedOpenState = true;
            return true;
        }
        return false;
    }

    /**
     * Open the sliding pane if it is currently slideable. If first layout
     * has already completed this will animate.
     *
     * @return true if the pane was slideable and is now open/in the process of opening
     */
    public boolean openPane() {
        return openPane(mSlideableView, 0);
    }

    /**
     * Close the sliding pane if it is currently slideable. If first layout
     * has already completed this will animate.
     *
     * @return true if the pane was slideable and is now closed/in the process of closing
     */
    public boolean closePane() {
        return closePane(mSlideableView, 0);
    }

    /**
     * Check if the layout is completely open. It can be open either because the slider
     * itself is open revealing the left pane, or if all content fits without sliding.
     *
     * @return true if sliding panels are completely open
     */
    public boolean isOpen() {
        return !mCanSlide || mSlideOffset == 1;
    }

    /**
     * Check if the content in this layout cannot fully fit side by side and therefore
     * the content pane can be slid back and forth.
     *
     * @return true if content in this layout can be slid open and closed
     */
    public boolean isSlideable() {
        return mCanSlide;
    }

    private void onPanelDragged(int newTop) {
        if (mSlideableView == null) {
            // This can happen if we're aborting motion during layout because everything now fits.
            mSlideOffset = 0;
            return;
        }
        final LayoutParams lp = (LayoutParams) mSlideableView.getLayoutParams();

        final int lpMargin = lp.topMargin;
        final int topBound = getPaddingTop() + lpMargin;

        mSlideOffset = (float) (newTop - topBound) / mSlideRange;

        dispatchOnPanelSlide(mSlideableView);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        boolean result;
        final int save = canvas.save(Canvas.CLIP_SAVE_FLAG);

        if (mCanSlide && !lp.slideable && mSlideableView != null) {
            // Clip against the slider; no sense drawing what will immediately be covered.
            canvas.getClipBounds(mTmpRect);

            mTmpRect.bottom = Math.min(mTmpRect.bottom, mSlideableView.getTop());
            canvas.clipRect(mTmpRect);
        }

        if (Build.VERSION.SDK_INT >= 11) { // HC
            result = super.drawChild(canvas, child, drawingTime);
        } else {
            if (child.isDrawingCacheEnabled()) {
                child.setDrawingCacheEnabled(false);
            }
            result = super.drawChild(canvas, child, drawingTime);
        }

        canvas.restoreToCount(save);

        return result;
    }

    /**
     * Smoothly animate mDraggingPane to the target X position within its range.
     *
     * @param slideOffset position to animate to
     * @param velocity initial velocity in case of fling, or 0.
     */
    boolean smoothSlideTo(float slideOffset, int velocity) {
        if (!mCanSlide) {
            // Nothing to do.
            return false;
        }

        final LayoutParams lp = (LayoutParams) mSlideableView.getLayoutParams();

        int y;
        int topBound = getPaddingTop() + lp.topMargin;
        y = (int) (topBound + slideOffset * mSlideRange);

        if (mDragHelper.smoothSlideViewTo(mSlideableView, mSlideableView.getLeft(), y)) {
            setAllChildrenVisible();
            ViewCompat.postInvalidateOnAnimation(this);
            return true;
        }
        return false;
    }

    @Override
    public void computeScroll() {
        if (mDragHelper.continueSettling(true)) {
            if (!mCanSlide) {
                mDragHelper.abort();
                return;
            }

            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    private boolean isCapturableViewUnder(int x, int y) {
        View capturableView = mCapturableView != null ? mCapturableView : mSlideableView;
        if (capturableView == null) {
            return false;
        }
        int[] viewLocation = new int[2];
        capturableView.getLocationOnScreen(viewLocation);
        int[] parentLocation = new int[2];
        this.getLocationOnScreen(parentLocation);
        int screenX = parentLocation[0] + x;
        int screenY = parentLocation[1] + y;
        return screenX >= viewLocation[0]
                && screenX < viewLocation[0] + capturableView.getWidth()
                && screenY >= viewLocation[1]
                && screenY < viewLocation[1] + capturableView.getHeight();
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof MarginLayoutParams
                ? new LayoutParams((MarginLayoutParams) p)
                : new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams && super.checkLayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        SavedState ss = new SavedState(superState);
        ss.isOpen = isSlideable() ? isOpen() : mPreservedOpenState;

        return ss;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        if (ss.isOpen) {
            openPane();
        } else {
            closePane();
        }
        mPreservedOpenState = ss.isOpen;
    }

    private class DragHelperCallback extends ViewDragHelper.Callback {

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            if (mIsUnableToDrag) {
                return false;
            }

            return ((LayoutParams) child.getLayoutParams()).slideable;
        }

        @Override
        public void onViewDragStateChanged(int state) {
            if (mDragHelper.getViewDragState() == ViewDragHelper.STATE_IDLE) {
                if (mSlideOffset == 0) {
                    updateObscuredViewsVisibility(mSlideableView);
                    dispatchOnPanelClosed(mSlideableView);
                    mPreservedOpenState = false;
                } else {
                    dispatchOnPanelOpened(mSlideableView);
                    mPreservedOpenState = true;
                }
            }
        }

        @Override
        public void onViewCaptured(View capturedChild, int activePointerId) {
            // Make all child views visible in preparation for sliding things around
            setAllChildrenVisible();
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            onPanelDragged(top);
            invalidate();
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            final LayoutParams lp = (LayoutParams) releasedChild.getLayoutParams();

            int top = getPaddingTop() + lp.topMargin;
            if (yvel > 0 || (yvel == 0 && mSlideOffset > 0.5f)) {
                top += mSlideRange;
            }

            int left;
            mDragHelper.settleCapturedViewAt(releasedChild.getLeft(), top);
            invalidate();
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return mSlideRange;
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            // Make sure we never move views horizontally.
            return child.getLeft();
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            final LayoutParams lp = (LayoutParams) mSlideableView.getLayoutParams();

            final int newTop;
            int topBound = getPaddingTop() + lp.topMargin;
            int bottomBound = topBound + mSlideRange;
            newTop = Math.min(Math.max(top, topBound), bottomBound);

            return newTop;
        }

        @Override
        public void onEdgeDragStarted(int edgeFlags, int pointerId) {
            mDragHelper.captureChildView(mSlideableView, pointerId);
        }
    }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        private static final int[] ATTRS = new int[] {
            android.R.attr.layout_weight
        };

        /**
         * The weighted proportion of how much of the leftover space
         * this child should consume after measurement.
         */
        public float weight = 0;

        /**
         * True if this pane is the slideable pane in the layout.
         */
        boolean slideable;

        public LayoutParams() {
            super(FILL_PARENT, FILL_PARENT);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(android.view.ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(LayoutParams source) {
            super(source);
            this.weight = source.weight;
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            final TypedArray a = c.obtainStyledAttributes(attrs, ATTRS);
            this.weight = a.getFloat(0, 0);
            a.recycle();
        }

    }

    static class SavedState extends BaseSavedState {
        boolean isOpen;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            isOpen = in.readInt() != 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(isOpen ? 1 : 0);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    class AccessibilityDelegate extends AccessibilityDelegateCompat {
        private final Rect mTmpRect = new Rect();

        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
            final AccessibilityNodeInfoCompat superNode = AccessibilityNodeInfoCompat.obtain(info);
            super.onInitializeAccessibilityNodeInfo(host, superNode);
            copyNodeInfoNoChildren(info, superNode);
            superNode.recycle();

            info.setClassName(OverlappingPaneLayout.class.getName());
            info.setSource(host);

            final ViewParent parent = ViewCompat.getParentForAccessibility(host);
            if (parent instanceof View) {
                info.setParent((View) parent);
            }

            // This is a best-approximation of addChildrenForAccessibility()
            // that accounts for filtering.
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() == View.VISIBLE) {
                    // Force importance to "yes" since we can't read the value.
                    ViewCompat.setImportantForAccessibility(
                            child, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
                    info.addChild(child);
                }
            }
        }

        @Override
        public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(host, event);

            event.setClassName(OverlappingPaneLayout.class.getName());
        }

        /**
         * This should really be in AccessibilityNodeInfoCompat, but there unfortunately
         * seem to be a few elements that are not easily cloneable using the underlying API.
         * Leave it private here as it's not general-purpose useful.
         */
        private void copyNodeInfoNoChildren(AccessibilityNodeInfoCompat dest,
                AccessibilityNodeInfoCompat src) {
            final Rect rect = mTmpRect;

            src.getBoundsInParent(rect);
            dest.setBoundsInParent(rect);

            src.getBoundsInScreen(rect);
            dest.setBoundsInScreen(rect);

            dest.setVisibleToUser(src.isVisibleToUser());
            dest.setPackageName(src.getPackageName());
            dest.setClassName(src.getClassName());
            dest.setContentDescription(src.getContentDescription());

            dest.setEnabled(src.isEnabled());
            dest.setClickable(src.isClickable());
            dest.setFocusable(src.isFocusable());
            dest.setFocused(src.isFocused());
            dest.setAccessibilityFocused(src.isAccessibilityFocused());
            dest.setSelected(src.isSelected());
            dest.setLongClickable(src.isLongClickable());

            dest.addAction(src.getActions());

            dest.setMovementGranularities(src.getMovementGranularities());
        }
    }
}
