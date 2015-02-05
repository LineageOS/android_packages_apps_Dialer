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
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.android.dialer.calllog.CallLogAdapter;
import com.android.dialer.calllog.CallLogNotificationsHelper;
import com.android.dialer.calllog.CallLogQueryHandler;
import com.android.dialer.list.SwipeHelper.OnItemGestureListener;

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

    private final Context mContext;

    private final DataSetObserver mObserver;

    private final CallLogQueryHandler mCallLogQueryHandler;

    private final OnItemGestureListener mCallLogOnItemSwipeListener =
            new OnItemGestureListener() {
        @Override
        public void onSwipe(View view) {
            mCallLogQueryHandler.markNewCallsAsOld();
            mCallLogQueryHandler.markNewVoicemailsAsOld();
            CallLogNotificationsHelper.removeMissedCallNotifications(mContext);
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
        public boolean onCallsFetched(Cursor combinedCursor) {
            mCallLogAdapter.invalidateCache();
            mCallLogAdapter.changeCursor(combinedCursor);
            mCallLogAdapter.notifyDataSetChanged();
            // Return true; took ownership of cursor
            return true;
        }
    };

    public ShortcutCardsAdapter(Context context,
            ListsFragment fragment,
            CallLogAdapter callLogAdapter) {
        final Resources resources = context.getResources();
        mContext = context;
        mFragment = fragment;

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
                null : wrapper.getChildAt(0), parent
        );
        wrapper.removeAllViews();
        wrapper.prepareChildView(view);
        wrapper.addView(view);
        wrapper.setVisibility(View.VISIBLE);
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
}
