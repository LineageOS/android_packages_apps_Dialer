package com.android.dialer.list;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.dialer.R;

public class RemoveView extends LinearLayout {

    DragDropController mDragDropController;
    TextView mRemoveText;
    ImageView mRemoveIcon;
    int mUnhighlightedColor;
    int mHighlightedColor;
    Drawable mRemoveDrawable;
    Drawable mRemoveHighlightedDrawable;

    public RemoveView(Context context) {
      super(context);
    }

    public RemoveView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public RemoveView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        mRemoveText = (TextView) findViewById(R.id.remove_view_text);
        mRemoveIcon = (ImageView) findViewById(R.id.remove_view_icon);
        final Resources r = getResources();
        mUnhighlightedColor = r.getColor(R.color.remove_text_color);
        mHighlightedColor = r.getColor(R.color.remove_highlighted_text_color);
        mRemoveDrawable = r.getDrawable(R.drawable.ic_remove);
        mRemoveHighlightedDrawable = r.getDrawable(R.drawable.ic_remove_highlight);
    }

    public void setDragDropController(DragDropController controller) {
        mDragDropController = controller;
    }

    @Override
    public boolean dispatchDragEvent(DragEvent event) {
      final int action = event.getAction();
      switch (action) {
        case DragEvent.ACTION_DRAG_ENTERED:
            setAppearanceHighlighted();
            break;
        case DragEvent.ACTION_DRAG_EXITED:
            setAppearanceNormal();
            break;
        case DragEvent.ACTION_DRAG_LOCATION:
            if (mDragDropController != null) {
                mDragDropController.handleDragHovered((int) event.getX(),
                        // the true y-coordinate of the event with respect to the listview is
                        // offset by the height of the remove view
                        (int) event.getY() - getHeight(), null);
            }
            break;
        case DragEvent.ACTION_DROP:
            if (mDragDropController != null) {
                mDragDropController.handleDragFinished((int) event.getX(), (int) event.getY(), true);
            }
            setAppearanceNormal();
            break;
      }
      return true;
    }

    private void setAppearanceNormal() {
        mRemoveText.setTextColor(mUnhighlightedColor);
        mRemoveIcon.setImageDrawable(mRemoveDrawable);
        invalidate();
    }

    private void setAppearanceHighlighted() {
        mRemoveText.setTextColor(mHighlightedColor);
        mRemoveIcon.setImageDrawable(mRemoveHighlightedDrawable);
        invalidate();
    }
}
