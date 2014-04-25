/*
 * Copyright (C) 2011 Google Inc.
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
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;

import com.android.dialer.R;
import com.android.dialer.calllog.CallLogAdapter;
import com.android.dialer.calllog.CallLogNotificationsHelper;
import com.android.dialer.calllog.CallLogQueryHandler;
import com.android.dialer.list.SwipeHelper.OnItemGestureListener;
import com.android.dialer.list.SwipeHelper.SwipeHelperCallback;

/**
 * An adapter that displays call shortcuts from {@link com.android.dialer.calllog.CallLogAdapter}
 * in the form of cards.
 */
public class ShortcutCardsAdapter extends BaseAdapter {

    private class CustomDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            notifyDataSetChanged();
        }
    }

    private static final String TAG = ShortcutCardsAdapter.class.getSimpleName();

    private final CallLogAdapter mCallLogAdapter;
    private final ListsFragment mFragment;

    private final int mCallLogPadding;

    private final Context mContext;

    private final DataSetObserver mObserver;

    private final CallLogQueryHandler mCallLogQueryHandler;

    private final OnItemGestureListener mCallLogOnItemSwipeListener =
            new OnItemGestureListener() {
        @Override
        public void onSwipe(View view) {
            mCallLogQueryHandler.markNewCallsAsOld();
            mCallLogQueryHandler.markNewVoicemailsAsOld();
            CallLogNotificationsHelper.removeMissedCallNotifications();
            CallLogNotificationsHelper.updateVoicemailNotifications(mContext);
            mFragment.dismissShortcut(view);
        }

        @Override
        public void onTouch() {}

        @Override
        public boolean isSwipeEnabled() {
            return true;
        }
    };

    private final CallLogQueryHandler.Listener mCallLogQueryHandlerListener =
            new CallLogQueryHandler.Listener() {
        @Override
        public void onVoicemailStatusFetched(Cursor statusCursor) {}

        @Override
        public void onCallsFetched(Cursor combinedCursor) {
            mCallLogAdapter.invalidateCache();
            mCallLogAdapter.changeCursor(combinedCursor);
            mCallLogAdapter.notifyDataSetChanged();
        }
    };

    public ShortcutCardsAdapter(Context context,
            ListsFragment fragment,
            CallLogAdapter callLogAdapter) {
        final Resources resources = context.getResources();
        mContext = context;
        mFragment = fragment;
        mCallLogPadding = resources.getDimensionPixelSize(R.dimen.recent_call_log_item_padding);
        mCallLogAdapter = callLogAdapter;
        mObserver = new CustomDataSetObserver();
        mCallLogAdapter.registerDataSetObserver(mObserver);
        mCallLogQueryHandler = new CallLogQueryHandler(mContext.getContentResolver(),
                mCallLogQueryHandlerListener);
    }

    /**
     * Determines the number of items in the adapter.
     * mCallLogAdapter contains the item for the most recent caller.
     * mContactTileAdapter contains the starred contacts.
     * The +1 is to account for the presence of the favorites menu.
     *
     * @return Number of items in the adapter.
     */
    @Override
    public int getCount() {
        return mCallLogAdapter.getCount();
    }

    @Override
    public Object getItem(int position) {
        return mCallLogAdapter.getItem(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    /**
     * Determine the number of view types present.
     */
    @Override
    public int getViewTypeCount() {
        return mCallLogAdapter.getViewTypeCount();
    }

    @Override
    public int getItemViewType(int position) {
        return mCallLogAdapter.getItemViewType(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final SwipeableShortcutCard wrapper;
        if (convertView == null) {
            wrapper = new SwipeableShortcutCard(mContext);
            wrapper.setOnItemSwipeListener(mCallLogOnItemSwipeListener);
        } else {
            wrapper = (SwipeableShortcutCard) convertView;
        }

        // Special case wrapper view for the most recent call log item. This allows
        // us to create a card-like effect for the more recent call log item in
        // the PhoneFavoriteMergedAdapter, but keep the original look of the item in
        // the CallLogAdapter.
        final View view = mCallLogAdapter.getView(position, convertView == null ?
                null : wrapper.getChildAt(0), parent);
        wrapper.removeAllViews();
        final View callLogItem = view.findViewById(R.id.call_log_list_item);
        // Reset the internal call log item view if it is being recycled
        callLogItem.setTranslationX(0);
        callLogItem.setAlpha(1);
        wrapper.addView(view);
        return wrapper;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return mCallLogAdapter.areAllItemsEnabled();
    }

    @Override
    public boolean isEnabled(int position) {
        return mCallLogAdapter.isEnabled(position);
    }

    /**
     * The swipeable call log row.
     */
    class SwipeableShortcutCard extends FrameLayout implements SwipeHelperCallback {
        private SwipeHelper mSwipeHelper;
        private OnItemGestureListener mOnItemSwipeListener;

        public SwipeableShortcutCard(Context context) {
            super(context);
            final float densityScale = getResources().getDisplayMetrics().density;
            final float pagingTouchSlop = ViewConfiguration.get(context)
                    .getScaledPagingTouchSlop();
            mSwipeHelper = new SwipeHelper(context, SwipeHelper.X, this,
                    densityScale, pagingTouchSlop);
        }

        @Override
        public void addView(View view) {
            view.setBackgroundResource(R.drawable.ic_tile_for_recents_and_contact_tile);

            final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(mCallLogPadding, mCallLogPadding, mCallLogPadding, mCallLogPadding);
            view.setLayoutParams(params);
            view.setTranslationZ(getResources().getDimensionPixelSize(
                    R.dimen.recent_call_log_item_translation_z));

            super.addView(view);
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

        private float mPreviousTranslationZ = 0;
        private Rect mClipRect = new Rect();

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

            if (ratioHidden <= 0.01f) {
                viewToClip.setTranslationZ(mPreviousTranslationZ);
            } else if (viewToClip.getTranslationZ() != 0){
                mPreviousTranslationZ = viewToClip.getTranslationZ();
                viewToClip.setTranslationZ(0);
            }

            if (ratioHidden > 0.5f) {
                mClipRect.set(0, 0 , 0, 0);
            } else {
                int newLeft = (int) (ratioHidden * width);
                int newRight = (width - newLeft);
                int newTop = (int) (ratioHidden * height);
                int newBottom = (height - newTop);
                mClipRect.set(newLeft, newTop, newRight, newBottom);
            }
            viewToClip.setClipBounds(mClipRect);

            // If the view has any children, fade them out of view.
            final ViewGroup viewGroup = (ViewGroup) viewToClip;
            final int count = viewGroup.getChildCount();
            for (int i = 0; i < count; i++) {
                viewGroup.getChildAt(i).setAlpha(Math.max(0, 1 - 4 * ratioHidden));
            }
        }
    }
}
