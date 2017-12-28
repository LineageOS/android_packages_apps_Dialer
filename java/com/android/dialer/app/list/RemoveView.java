/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.app.list;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.dialer.app.R;

public class RemoveView extends FrameLayout {

  DragDropController dragDropController;
  TextView removeText;
  ImageView removeIcon;
  int unhighlightedColor;
  int highlightedColor;
  Drawable removeDrawable;

  public RemoveView(Context context) {
    super(context);
  }

  public RemoveView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public RemoveView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  @Override
  protected void onFinishInflate() {
    removeText = (TextView) findViewById(R.id.remove_view_text);
    removeIcon = (ImageView) findViewById(R.id.remove_view_icon);
    final Resources r = getResources();
    unhighlightedColor = r.getColor(R.color.remove_text_color);
    highlightedColor = r.getColor(R.color.remove_highlighted_text_color);
    removeDrawable = r.getDrawable(R.drawable.ic_remove);
  }

  public void setDragDropController(DragDropController controller) {
    dragDropController = controller;
  }

  @Override
  public boolean onDragEvent(DragEvent event) {
    final int action = event.getAction();
    switch (action) {
      case DragEvent.ACTION_DRAG_ENTERED:
        // TODO: This is temporary solution and should be removed once accessibility for
        // drag and drop is supported by framework(a bug).
        sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT);
        setAppearanceHighlighted();
        break;
      case DragEvent.ACTION_DRAG_EXITED:
        setAppearanceNormal();
        break;
      case DragEvent.ACTION_DRAG_LOCATION:
        if (dragDropController != null) {
          dragDropController.handleDragHovered(this, (int) event.getX(), (int) event.getY());
        }
        break;
      case DragEvent.ACTION_DROP:
        sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT);
        if (dragDropController != null) {
          dragDropController.handleDragFinished((int) event.getX(), (int) event.getY(), true);
        }
        setAppearanceNormal();
        break;
    }
    return true;
  }

  private void setAppearanceNormal() {
    removeText.setTextColor(unhighlightedColor);
    removeIcon.setColorFilter(unhighlightedColor);
    invalidate();
  }

  private void setAppearanceHighlighted() {
    removeText.setTextColor(highlightedColor);
    removeIcon.setColorFilter(highlightedColor);
    invalidate();
  }
}
