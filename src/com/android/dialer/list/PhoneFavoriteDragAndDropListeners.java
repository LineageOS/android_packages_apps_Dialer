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

import android.content.ClipData;
import android.util.Log;
import android.view.DragEvent;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnDragListener;

import com.android.dialer.list.PhoneFavoritesTileAdapter.ContactTileRow;

/**
 * Implements the OnLongClickListener and OnDragListener for phone's favorite tiles and rows.
 */
public class PhoneFavoriteDragAndDropListeners {

    private static final String TAG = PhoneFavoriteDragAndDropListeners.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final float FLING_HEIGHT_PORTION = 1.f / 4.f;
    private static final float FLING_WIDTH_PORTION = 1.f / 6.f;

    public static class PhoneFavoriteGestureListener extends SimpleOnGestureListener {
        private static final float FLING_VELOCITY_MINIMUM = 5.0f;
        private float mFlingHorizontalThreshold;
        private float mFlingVerticalThreshold;
        private final PhoneFavoriteTileView mView;

        public PhoneFavoriteGestureListener(View view) {
            super();
            mView = (PhoneFavoriteTileView) view;
        }

        @Override
        public void onLongPress(MotionEvent event) {
            final ClipData data = ClipData.newPlainText("", "");
            final View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(mView);
            mView.setPressed(false);
            if (mView instanceof PhoneFavoriteRegularRowView) {
                // If the view is regular row, start drag the row view.
                // TODO: move the padding so we can start drag the original view.
                mView.getParentRow().startDrag(data, shadowBuilder, null, 0);
            } else {
                // If the view is a tile view, start drag the tile.
                mView.startDrag(data, shadowBuilder, null, 0);
            }
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            final float x1 = e1.getX();
            final float x2 = e2.getX();
            // Temporarily disables parents from getting this event so the listview does not scroll.
            mView.getParent().requestDisallowInterceptTouchEvent(true);
            mView.setScrollOffset(x2 - x1);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            // Sets fling trigger threshold.
            mFlingVerticalThreshold = (float) mView.getHeight() * FLING_HEIGHT_PORTION;
            mFlingHorizontalThreshold = (float) mView.getWidth() * FLING_WIDTH_PORTION;
            final float x1 = e1.getX();
            final float x2 = e2.getX();
            final float y1 = e1.getY();
            final float y2 = e2.getY();

            mView.setPressed(false);

            if (Math.abs(y1 - y2) < mFlingVerticalThreshold &&
                    Math.abs(x2 - x1) > mFlingHorizontalThreshold &&
                    Math.abs(velocityX) > FLING_VELOCITY_MINIMUM) {
                // If fling is triggered successfully, end the scroll and setup removal dialogue.
                final int removeIndex = mView.getParentRow().getItemIndex(x1, y1);
                mView.setScrollEnd(false);
                mView.setupRemoveDialogue();
                mView.getParentRow().getTileAdapter().setPotentialRemoveEntryIndex(removeIndex);

                return true;
            } else {
                mView.setScrollEnd(true);
                return false;
            }
        }

        @Override
        public boolean onDown(MotionEvent e) {
            mView.setPressed(true);
            // Signals that the view will accept further events.
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            mView.performClick();
            return true;
        }
    }

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

        @Override
        public boolean onDrag(View v, DragEvent event) {
            // Handles drag events.
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    break;
                case DragEvent.ACTION_DRAG_ENTERED:
                    break;
                case DragEvent.ACTION_DRAG_EXITED:
                    break;
                case DragEvent.ACTION_DROP:
                    // Gets the location of the drag with respect to the whole Dialer view.
                    mX = event.getX() + v.getLeft();
                    mY = event.getY() + v.getTop();

                    // Indicates a drag has finished.
                    if (mTileAdapter != null && mContactTileRow != null) {
                        mTileAdapter.setInDragging(false);

                        // Finds out at which position of the list the Contact is being dropped.
                        final int dropIndex = mContactTileRow.getItemIndex(mX, mY);
                        if (DEBUG) {
                            Log.v(TAG, "Stop dragging " + String.valueOf(dropIndex));
                        }

                        // Adds the dragged contact to the drop position.
                        mTileAdapter.dropContactEntry(dropIndex);
                    }
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    break;
                case DragEvent.ACTION_DRAG_LOCATION:
                    // Gets the current drag location with respect to the whole Dialer view.
                    mX = event.getX() + v.getLeft();
                    mY = event.getY() + v.getTop();
                    if (DEBUG) {
                        Log.v(TAG, String.valueOf(mX) + "; " + String.valueOf(mY));
                    }

                    if (mTileAdapter != null && mContactTileRow != null) {
                        // If there is no drag in process, initializes the drag.
                        if (!mTileAdapter.getInDragging()) {
                            // Finds out which item is being dragged.
                            final int dragIndex = mContactTileRow.getItemIndex(mX, mY);
                            if (DEBUG) {
                                Log.v(TAG, "Start dragging " + String.valueOf(dragIndex));
                            }

                            // Indicates a drag has started.
                            mTileAdapter.setInDragging(true);

                            // Temporarily pops out the Contact entry.
                            mTileAdapter.popContactEntry(dragIndex);
                        }
                    }
                    break;
                default:
                    break;
            }
            return true;
        }
    }
}
