package com.android.dialer.list;

import android.view.View;

import com.android.dialer.list.PhoneFavoritesTileAdapter.ContactTileRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that handles and combines drag events generated from multiple views, and then fires
 * off events to any OnDragDropListeners that have registered for callbacks.
 */
public class DragDropController {
    private List<OnDragDropListener> mOnDragDropListeners = new ArrayList<OnDragDropListener>();

    /**
     * @return True if the drag is started, false if the drag is cancelled for some reason.
     */
    boolean handleDragStarted(int x, int y, ContactTileRow tileRow) {
        final PhoneFavoriteTileView tileView =
                (PhoneFavoriteTileView) tileRow.getViewAtPosition(x, y);

        final int itemIndex = tileRow.getItemIndex(x, y);
        if (itemIndex != -1 && !mOnDragDropListeners.isEmpty()) {
            for (int i = 0; i < mOnDragDropListeners.size(); i++) {
                mOnDragDropListeners.get(i).onDragStarted(itemIndex, x, y, tileView);
            }
        }

        return true;
    }

    public void handleDragHovered(int x, int y, View view) {
        int itemIndex;
        if (!(view instanceof ContactTileRow)) {
            itemIndex = -1;
        } else {
            final ContactTileRow tile = (ContactTileRow) view;
            itemIndex = tile.getItemIndex(x, y);
        }
        for (int i = 0; i < mOnDragDropListeners.size(); i++) {
            mOnDragDropListeners.get(i).onDragHovered(itemIndex, x, y);
        }
    }

    public void handleDragFinished(int x, int y, boolean isRemoveView) {
        if (isRemoveView) {
            for (int i = 0; i < mOnDragDropListeners.size(); i++) {
                mOnDragDropListeners.get(i).onDroppedOnRemove();
            }
        }

        for (int i = 0; i < mOnDragDropListeners.size(); i++) {
            mOnDragDropListeners.get(i).onDragFinished(x, y);
        }
    }

    public void addOnDragDropListener(OnDragDropListener listener) {
        if (!mOnDragDropListeners.contains(listener)) {
            mOnDragDropListeners.add(listener);
        }
    }

    public void removeOnDragDropListener(OnDragDropListener listener) {
        if (mOnDragDropListeners.contains(listener)) {
            mOnDragDropListeners.remove(listener);
        }
    }

}