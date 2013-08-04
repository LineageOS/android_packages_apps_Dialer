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
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;

import com.android.dialer.R;
import com.android.dialer.calllog.CallLogAdapter;

/**
 * An adapter that combines items from {@link com.android.contacts.common.list.ContactTileAdapter}
 * and {@link com.android.dialer.calllog.CallLogAdapter} into a single list.
 */
public class NewPhoneFavoriteMergedAdapter extends BaseAdapter {

    private class CustomDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            notifyDataSetChanged();
        }
    }

    private static final String TAG = NewPhoneFavoriteMergedAdapter.class.getSimpleName();

    private final PhoneFavoritesTileAdapter mContactTileAdapter;
    private final CallLogAdapter mCallLogAdapter;
    private final View mLoadingView;
    private final View mShowAllContactsButton;

    private final int mCallLogPadding;

    private final Context mContext;

    private final DataSetObserver mObserver;

    public NewPhoneFavoriteMergedAdapter(Context context,
            PhoneFavoritesTileAdapter contactTileAdapter,
            View accountFilterHeaderContainer,
            CallLogAdapter callLogAdapter,
            View loadingView,
            View showAllContactsButton) {
        final Resources resources = context.getResources();
        mContext = context;
        mCallLogPadding = resources.getDimensionPixelSize(R.dimen.recent_call_log_item_padding);
        mContactTileAdapter = contactTileAdapter;
        mCallLogAdapter = callLogAdapter;

        mObserver = new CustomDataSetObserver();
        mContactTileAdapter.registerDataSetObserver(mObserver);
        mLoadingView = loadingView;
        mShowAllContactsButton = showAllContactsButton;
    }

    @Override
    public boolean isEmpty() {
        // This adapter will always contain at least the all contacts button
        return false;
    }

    @Override
    public int getCount() {
        return mContactTileAdapter.getCount() + mCallLogAdapter.getCount() + 1;
    }

    @Override
    public Object getItem(int position) {
        final int callLogAdapterCount = mCallLogAdapter.getCount();

        if (callLogAdapterCount > 0) {
            if (position < callLogAdapterCount) {
                return mCallLogAdapter.getItem(position);
            }
            // Set position to the position of the actual favorite contact in the favorites adapter
            position = getAdjustedFavoritePosition(position, callLogAdapterCount);
        }
        return mContactTileAdapter.getItem(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return (mContactTileAdapter.getViewTypeCount() + mCallLogAdapter.getViewTypeCount() + 1);
    }

    @Override
    public int getItemViewType(int position) {
        final int callLogAdapterCount = mCallLogAdapter.getCount();

        if (position < callLogAdapterCount) {
            // View type of the call log adapter is the last view type of the contact tile adapter
            // + 1
            return mContactTileAdapter.getViewTypeCount();
        } else if (position < getCount() - 1) {
            return mContactTileAdapter.getItemViewType(
                    getAdjustedFavoritePosition(position, callLogAdapterCount));
        } else {
            // View type of the show all contact button is the last view type of the contact tile
            // adapter + 2
            return mContactTileAdapter.getViewTypeCount() + 1;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final int callLogAdapterCount = mCallLogAdapter.getCount();

        if (position == getCount() - 1) {
            return mShowAllContactsButton;
        }

        if (callLogAdapterCount > 0) {
            if (position == 0) {
                final FrameLayout wrapper;
                if (convertView == null) {
                    wrapper = new FrameLayout(mContext);
                } else {
                    wrapper = (FrameLayout) convertView;
                }

                // Special case wrapper view for the most recent call log item. This allows
                // us to create a card-like effect for the more recent call log item in
                // the PhoneFavoriteMergedAdapter, but keep the original look of the item in
                // the CallLogAdapter.
                final View view = mCallLogAdapter.getView(position, convertView == null ?
                        null : wrapper.getChildAt(0), parent);
                wrapper.removeAllViews();
                view.setBackgroundResource(R.drawable.dialer_recent_card_bg);

                final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT);

                params.setMarginsRelative(mCallLogPadding, mCallLogPadding, mCallLogPadding,
                        mCallLogPadding);
                view.setLayoutParams(params);
                wrapper.addView(view);

                return wrapper;
            }
            // Set position to the position of the actual favorite contact in the
            // favorites adapter
            position = getAdjustedFavoritePosition(position, callLogAdapterCount);
        }

        // Favorites section
        final View view = mContactTileAdapter.getView(position, convertView, parent);
        if (position >= mContactTileAdapter.getMaxTiledRows()) {
            final FrameLayout frameLayout = (FrameLayout) view;
            final View child = frameLayout.getChildAt(0);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            child.setLayoutParams(params);
        }
        return view;
    }

    @Override
    public boolean areAllItemsEnabled() {
        // If "all" section is being loaded we'll show mLoadingView, which is not enabled.
        // Otherwise check the all the other components in the ListView and return appropriate
        // result.
        return mCallLogAdapter.areAllItemsEnabled() && mContactTileAdapter.areAllItemsEnabled();
    }

    @Override
    public boolean isEnabled(int position) {
        final int callLogAdapterCount = mCallLogAdapter.getCount();
        if (position < callLogAdapterCount) {
            return mCallLogAdapter.isEnabled(position);
        } else { // For favorites section
            return mContactTileAdapter.isEnabled(
                    getAdjustedFavoritePosition(position, callLogAdapterCount));
        }
    }

    private int getAdjustedFavoritePosition(int position, int callLogAdapterCount) {
        return position - callLogAdapterCount;
    }
}
