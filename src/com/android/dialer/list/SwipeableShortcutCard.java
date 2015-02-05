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
package com.android.dialer.list;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.dialer.R;
import com.android.dialer.list.SwipeHelper.OnItemGestureListener;
import com.android.dialer.list.SwipeHelper.SwipeHelperCallback;

/**
 * The swipeable call log row.
 */
public class SwipeableShortcutCard extends FrameLayout implements SwipeHelperCallback {

    private static final float CLIP_CARD_BARELY_HIDDEN_RATIO = 0.001f;
    private static final float CLIP_CARD_MOSTLY_HIDDEN_RATIO = 0.9f;
    // Fade out 5x faster than the hidden ratio.
    private static final float CLIP_CARD_OPACITY_RATIO = 5f;

    final int mCallLogMarginHorizontal;
    final int mCallLogMarginTop;
    final int mCallLogMarginBottom;
    final int mCallLogPaddingStart;
    final int mCallLogPaddingTop;
    final int mCallLogPaddingBottom;
    final int mShortCardBackgroundColor;

    private SwipeHelper mSwipeHelper;
    private OnItemGestureListener mOnItemSwipeListener;

    private float mPreviousTranslationZ = 0;
    private Rect mClipRect = new Rect();

    public SwipeableShortcutCard(Context context) {
        super(context);
        final Resources resources = getResources();
        final float densityScale =resources.getDisplayMetrics().density;
        final float pagingTouchSlop = ViewConfiguration.get(context)
                .getScaledPagingTouchSlop();
        mSwipeHelper = new SwipeHelper(context, SwipeHelper.X, this,
                densityScale, pagingTouchSlop);

        mCallLogMarginHorizontal =
                resources.getDimensionPixelSize(R.dimen.recent_call_log_item_margin_horizontal);
        mCallLogMarginTop =
                resources.getDimensionPixelSize(R.dimen.recent_call_log_item_margin_top);
        mCallLogMarginBottom =
                resources.getDimensionPixelSize(R.dimen.recent_call_log_item_margin_bottom);
        mCallLogPaddingStart =
                resources.getDimensionPixelSize(R.dimen.recent_call_log_item_padding_start);
        mCallLogPaddingTop =
                resources.getDimensionPixelSize(R.dimen.recent_call_log_item_padding_top);
        mCallLogPaddingBottom =
                resources.getDimensionPixelSize(R.dimen.recent_call_log_item_padding_bottom);
        mShortCardBackgroundColor =
                resources.getColor(R.color.call_log_expanded_background_color, null);
    }

    void prepareChildView(View view) {
        // Override CallLogAdapter's accessibility behavior; don't expand the shortcut card.
        view.setAccessibilityDelegate(null);
        view.setBackgroundResource(R.drawable.rounded_corner_bg);

        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(mCallLogMarginHorizontal, mCallLogMarginTop, mCallLogMarginHorizontal,
                mCallLogMarginBottom);
        view.setLayoutParams(params);

        LinearLayout actionView =
                (LinearLayout) view.findViewById(R.id.primary_action_view);
        actionView.setPaddingRelative(mCallLogPaddingStart, mCallLogPaddingTop,
                actionView.getPaddingEnd(), mCallLogPaddingBottom);

        // TODO: Set content description including type/location and time information.
        TextView nameView = (TextView) actionView.findViewById(R.id.name);

        actionView.setContentDescription(
                TextUtils.expandTemplate(
                        getResources().getString(R.string.description_call_back_action),
                        nameView.getText()));

        mPreviousTranslationZ = getResources().getDimensionPixelSize(
                R.dimen.recent_call_log_item_translation_z);
        view.setTranslationZ(mPreviousTranslationZ);

        final ViewGroup callLogItem = (ViewGroup) view.findViewById(R.id.call_log_list_item);
        // Reset the internal call log item view if it is being recycled
        callLogItem.setTranslationX(0);
        callLogItem.setTranslationY(0);
        callLogItem.setAlpha(1);
        callLogItem.setClipBounds(null);
        setChildrenOpacity(callLogItem, 1.0f);

        callLogItem.findViewById(R.id.call_log_row)
                .setBackgroundColor(mShortCardBackgroundColor);

        callLogItem.findViewById(R.id.call_indicator_icon).setVisibility(View.VISIBLE);
    }

    @Override
    public View getChildAtPosition(MotionEvent ev) {
        return getChildCount() > 0 ? getChildAt(0) : null;
    }

    @Override
    public View getChildContentView(View v) {
        return v.findViewById(R.id.call_log_list_item);
    }

    @Override
    public void onScroll() {}

    @Override
    public boolean canChildBeDismissed(View v) {
        return true;
    }

    @Override
    public void onBeginDrag(View v) {
        // We do this so the underlying ScrollView knows that it won't get
        // the chance to intercept events anymore
        requestDisallowInterceptTouchEvent(true);
    }

    @Override
    public void onChildDismissed(View v) {
        if (v != null && mOnItemSwipeListener != null) {
            mOnItemSwipeListener.onSwipe(v);
        }
    }

    @Override
    public void onDragCancelled(View v) {}

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mSwipeHelper != null) {
            return mSwipeHelper.onInterceptTouchEvent(ev) || super.onInterceptTouchEvent(ev);
        } else {
            return super.onInterceptTouchEvent(ev);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mSwipeHelper != null) {
            return mSwipeHelper.onTouchEvent(ev) || super.onTouchEvent(ev);
        } else {
            return super.onTouchEvent(ev);
        }
    }

    public void setOnItemSwipeListener(OnItemGestureListener listener) {
        mOnItemSwipeListener = listener;
    }

    /**
     * Clips the card by a specified amount.
     *
     * @param ratioHidden A float indicating how much of each edge of the card should be
     *         clipped. If 0, the entire card is displayed. If 0.5f, each edge is hidden
     *         entirely, thus obscuring the entire card.
     */
    public void clipCard(float ratioHidden) {
        final View viewToClip = getChildAt(0);
        if (viewToClip == null) {
            return;
        }
        int width = viewToClip.getWidth();
        int height = viewToClip.getHeight();

        if (ratioHidden <= CLIP_CARD_BARELY_HIDDEN_RATIO) {
            viewToClip.setTranslationZ(mPreviousTranslationZ);
        } else if (viewToClip.getTranslationZ() != 0){
            mPreviousTranslationZ = viewToClip.getTranslationZ();
            viewToClip.setTranslationZ(0);
        }

        if (ratioHidden > CLIP_CARD_MOSTLY_HIDDEN_RATIO) {
            mClipRect.set(0, 0 , 0, 0);
            setVisibility(View.INVISIBLE);
        } else {
            setVisibility(View.VISIBLE);
            int newTop = (int) (ratioHidden * height);
            mClipRect.set(0, newTop, width, height);

            // Since the pane will be overlapping with the action bar, apply a vertical offset
            // to top align the clipped card in the viewable area;
            viewToClip.setTranslationY(-newTop);
        }
        viewToClip.setClipBounds(mClipRect);

        // If the view has any children, fade them out of view.
        final ViewGroup viewGroup = (ViewGroup) viewToClip;
        setChildrenOpacity(
                viewGroup, Math.max(0, 1 - (CLIP_CARD_OPACITY_RATIO  * ratioHidden)));
    }

    private void setChildrenOpacity(ViewGroup viewGroup, float alpha) {
        final int count = viewGroup.getChildCount();
        for (int i = 0; i < count; i++) {
            viewGroup.getChildAt(i).setAlpha(alpha);
        }
    }
}