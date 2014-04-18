package com.android.dialer.list;

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
    boolean handleDragStarted(int x, int y, PhoneFavoriteSquareTileView tileView) {
        if (tileView == null) {
            return false;
        }
        if (tileView != null && !mOnDragDropListeners.isEmpty()) {
            for (int i = 0; i < mOnDragDropListeners.size(); i++) {
                mOnDragDropListeners.get(i).onDragStarted(x, y, tileView);
            }
        }

        return true;
    }

    public void handleDragHovered(int x, int y, PhoneFavoriteSquareTileView view) {
        for (int i = 0; i < mOnDragDropListeners.size(); i++) {
            mOnDragDropListeners.get(i).onDragHovered(x, y, view);
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