/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.graphics.Rect;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;
import android.view.View.OnDragListener;

import com.android.dialer.list.PhoneFavoritesTileAdapter.ContactTileRow;

/**
 * Implements the OnLongClickListener and OnDragListener for phone's favorite tiles and rows.
 */
public class PhoneFavoriteDragAndDropListeners {

    private static final String TAG = PhoneFavoriteDragAndDropListeners.class.getSimpleName();
    private static final boolean DEBUG = false;

    /**
     * Implements the OnDragListener to handle drag events.
     */
    public static class PhoneFavoriteDragListener implements OnDragListener {
        /** Location of the drag event. */
        private float mX = 0;
        private float mY = 0;
        private final ContactTileRow mContactTileRow;
        private final PhoneFavoritesTileAdapter mTileAdapter;

        public PhoneFavoriteDragListener(ContactTileRow contactTileRow,
                PhoneFavoritesTileAdapter tileAdapter) {
            super();
            mContactTileRow = contactTileRow;
            mTileAdapter = tileAdapter;
        }

        /**
         * @return The item index in {@link #mTileAdapter} for the given {@link DragEvent}.
         *     Returns -1 if {@link #mTileAdapter} is not in dragging or index can not be found.
         */
        private int getDragItemIndex(DragEvent event) {
            int itemIndex = -1;
            if (mTileAdapter != null && mContactTileRow != null
                    && !mTileAdapter.getInDragging()) {
                mX = event.getX();
                mY = event.getY();
                if (DEBUG) {
                    Log.v(TAG, String.valueOf(mX) + "; " + String.valueOf(mY));
                }

                final int[] rowLocation = new int[2];
                mContactTileRow.getLocationOnScreen(rowLocation);

                final Rect locationRect = new Rect(rowLocation[0], rowLocation[1],
                        rowLocation[0] + mContactTileRow.getWidth(),
                        rowLocation[1] + mContactTileRow.getHeight());

                if (locationRect.contains((int) mX, (int) mY)) {
                    // Finds out which item is being dragged.
                    // Computes relative coordinates as we get absolute coordinates.
                    itemIndex = mContactTileRow.getItemIndex(
                            mX - rowLocation[0], mY - rowLocation[1]);
                    if (DEBUG) {
                        Log.v(TAG, "Start dragging " + String.valueOf(itemIndex));
                    }
                }
            }
            return itemIndex;
        }

        @Override
        public boolean onDrag(View v, DragEvent event) {
            if (DEBUG) {
                Log.v(TAG, event.toString());
            }
            // Handles drag events.
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    final int itemIndex = getDragItemIndex(event);
                    if (itemIndex != -1) {
                        // Indicates a drag has started.
                        mTileAdapter.setInDragging(true);

                        // Temporarily pops out the Contact entry.
                        mTileAdapter.popContactEntry(itemIndex);
                    }
                    break;
                case DragEvent.ACTION_DRAG_ENTERED:
                    break;
                case DragEvent.ACTION_DRAG_EXITED:
                    break;
                case DragEvent.ACTION_DROP:
                    // Indicates a drag has finished.
                    if (mTileAdapter != null && mContactTileRow != null) {
                        mTileAdapter.setInDragging(false);
                        // The drop to position has been reported to the adapter
                        // via {@link DragEvent#ACTION_DRAG_LOCATION} events in ListView.
                        mTileAdapter.handleDrop();
                    }
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    if (mTileAdapter != null && mTileAdapter.getInDragging()) {
                        // If the drag and drop ends when the drop happens outside of any rows,
                        // we will end the drag here and put the item back to where it was dragged
                        // from before.
                        mTileAdapter.setInDragging(false);
                        mTileAdapter.handleDrop();
                    }
                    break;
                case DragEvent.ACTION_DRAG_LOCATION:
                    break;
                default:
                    break;
            }
            return true;
        }
    }
}
