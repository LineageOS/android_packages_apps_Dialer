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
import android.database.Cursor;
import android.database.DataSetObserver;
import android.view.View;

import com.android.dialer.calllog.CallLogAdapter;
import com.android.dialer.calllog.CallLogNotificationsHelper;
import com.android.dialer.calllog.CallLogQueryHandler;
import com.android.dialer.list.SwipeHelper.OnItemGestureListener;

/**
 * Handles the logic that displays call shortcuts from
 * {@link com.android.dialer.calllog.CallLogAdapter} in the form of cards.
 */
public class ShortcutCardsManager {

    private class CustomDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            updateShortcutCard();
        }
    }

    private static final String TAG = ShortcutCardsManager.class.getSimpleName();

    // The position of the shortcut card within the CallLogAdapter
    private static final int SHORTCUT_CARD_INDEX = 0;

    private final CallLogAdapter mCallLogAdapter;
    private final ListsFragment mFragment;
    private final SwipeableShortcutCard mShortcutCard;

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

    public ShortcutCardsManager(Context context,
            ListsFragment fragment,
            CallLogAdapter callLogAdapter,
            SwipeableShortcutCard shortcutCard) {
        mContext = context;
        mFragment = fragment;
        mShortcutCard = shortcutCard;

        mCallLogAdapter = callLogAdapter;
        mObserver = new CustomDataSetObserver();
        mCallLogAdapter.registerDataSetObserver(mObserver);
        mCallLogQueryHandler = new CallLogQueryHandler(mContext.getContentResolver(),
                mCallLogQueryHandlerListener);
        mShortcutCard.setOnItemSwipeListener(mCallLogOnItemSwipeListener);
    }

    /**
     * Updates the contents of the shortcut card with the view provided by the
     * {@link CallLogAdapter}.
     */
    private void updateShortcutCard() {
        final int count = mCallLogAdapter.getCount();
        final View convertView = mShortcutCard.getChildAt(SHORTCUT_CARD_INDEX);
        if (count <= SHORTCUT_CARD_INDEX) {
            if (convertView != null) {
                convertView.setVisibility(View.GONE);
            }
        } else {
            mShortcutCard.setVisibility(View.VISIBLE);
            final View view = mCallLogAdapter.getView(SHORTCUT_CARD_INDEX, convertView,
                    mShortcutCard);
            mShortcutCard.removeAllViews();
            mShortcutCard.prepareChildView(view);
            mShortcutCard.addView(view);
            view.setVisibility(View.VISIBLE);
        }
    }
}
