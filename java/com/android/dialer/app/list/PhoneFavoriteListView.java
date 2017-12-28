/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.GridView;
import android.widget.ImageView;
import com.android.dialer.app.R;
import com.android.dialer.app.list.DragDropController.DragItemContainer;
import com.android.dialer.common.LogUtil;

/** Viewgroup that presents the user's speed dial contacts in a grid. */
public class PhoneFavoriteListView extends GridView
    implements OnDragDropListener, DragItemContainer {

  public static final String LOG_TAG = PhoneFavoriteListView.class.getSimpleName();
  final int[] locationOnScreen = new int[2];
  private static final long SCROLL_HANDLER_DELAY_MILLIS = 5;
  private static final int DRAG_SCROLL_PX_UNIT = 25;
  private static final float DRAG_SHADOW_ALPHA = 0.7f;
  /**
   * {@link #topScrollBound} and {@link bottomScrollBound} will be offseted to the top / bottom by
   * {@link #getHeight} * {@link #BOUND_GAP_RATIO} pixels.
   */
  private static final float BOUND_GAP_RATIO = 0.2f;

  private float touchSlop;
  private int topScrollBound;
  private int bottomScrollBound;
  private int lastDragY;
  private Handler scrollHandler;
  private final Runnable dragScroller =
      new Runnable() {
        @Override
        public void run() {
          if (lastDragY <= topScrollBound) {
            smoothScrollBy(-DRAG_SCROLL_PX_UNIT, (int) SCROLL_HANDLER_DELAY_MILLIS);
          } else if (lastDragY >= bottomScrollBound) {
            smoothScrollBy(DRAG_SCROLL_PX_UNIT, (int) SCROLL_HANDLER_DELAY_MILLIS);
          }
          scrollHandler.postDelayed(this, SCROLL_HANDLER_DELAY_MILLIS);
        }
      };
  private boolean isDragScrollerRunning = false;
  private int touchDownForDragStartY;
  private Bitmap dragShadowBitmap;
  private ImageView dragShadowOverlay;
  private final AnimatorListenerAdapter dragShadowOverAnimatorListener =
      new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
          if (dragShadowBitmap != null) {
            dragShadowBitmap.recycle();
            dragShadowBitmap = null;
          }
          dragShadowOverlay.setVisibility(GONE);
          dragShadowOverlay.setImageBitmap(null);
        }
      };
  private View dragShadowParent;
  private int animationDuration;
  // X and Y offsets inside the item from where the user grabbed to the
  // child's left coordinate. This is used to aid in the drawing of the drag shadow.
  private int touchOffsetToChildLeft;
  private int touchOffsetToChildTop;
  private int dragShadowLeft;
  private int dragShadowTop;
  private DragDropController dragDropController = new DragDropController(this);

  public PhoneFavoriteListView(Context context) {
    this(context, null);
  }

  public PhoneFavoriteListView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public PhoneFavoriteListView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    animationDuration = context.getResources().getInteger(R.integer.fade_duration);
    touchSlop = ViewConfiguration.get(context).getScaledPagingTouchSlop();
    dragDropController.addOnDragDropListener(this);
  }

  @Override
  protected void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    touchSlop = ViewConfiguration.get(getContext()).getScaledPagingTouchSlop();
  }

  /**
   * TODO: This is all swipe to remove code (nothing to do with drag to remove). This should be
   * cleaned up and removed once drag to remove becomes the only way to remove contacts.
   */
  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev) {
    if (ev.getAction() == MotionEvent.ACTION_DOWN) {
      touchDownForDragStartY = (int) ev.getY();
    }

    return super.onInterceptTouchEvent(ev);
  }

  @Override
  public boolean onDragEvent(DragEvent event) {
    final int action = event.getAction();
    final int eX = (int) event.getX();
    final int eY = (int) event.getY();
    switch (action) {
      case DragEvent.ACTION_DRAG_STARTED:
        {
          if (!PhoneFavoriteTileView.DRAG_PHONE_FAVORITE_TILE.equals(event.getLocalState())) {
            // Ignore any drag events that were not propagated by long pressing
            // on a {@link PhoneFavoriteTileView}
            return false;
          }
          if (!dragDropController.handleDragStarted(this, eX, eY)) {
            return false;
          }
          break;
        }
      case DragEvent.ACTION_DRAG_LOCATION:
        lastDragY = eY;
        dragDropController.handleDragHovered(this, eX, eY);
        // Kick off {@link #mScrollHandler} if it's not started yet.
        if (!isDragScrollerRunning
            &&
            // And if the distance traveled while dragging exceeds the touch slop
            (Math.abs(lastDragY - touchDownForDragStartY) >= 4 * touchSlop)) {
          isDragScrollerRunning = true;
          ensureScrollHandler();
          scrollHandler.postDelayed(dragScroller, SCROLL_HANDLER_DELAY_MILLIS);
        }
        break;
      case DragEvent.ACTION_DRAG_ENTERED:
        final int boundGap = (int) (getHeight() * BOUND_GAP_RATIO);
        topScrollBound = (getTop() + boundGap);
        bottomScrollBound = (getBottom() - boundGap);
        break;
      case DragEvent.ACTION_DRAG_EXITED:
      case DragEvent.ACTION_DRAG_ENDED:
      case DragEvent.ACTION_DROP:
        ensureScrollHandler();
        scrollHandler.removeCallbacks(dragScroller);
        isDragScrollerRunning = false;
        // Either a successful drop or it's ended with out drop.
        if (action == DragEvent.ACTION_DROP || action == DragEvent.ACTION_DRAG_ENDED) {
          dragDropController.handleDragFinished(eX, eY, false);
        }
        break;
      default:
        break;
    }
    // This ListView will consume the drag events on behalf of its children.
    return true;
  }

  public void setDragShadowOverlay(ImageView overlay) {
    dragShadowOverlay = overlay;
    dragShadowParent = (View) dragShadowOverlay.getParent();
  }

  /** Find the view under the pointer. */
  private View getViewAtPosition(int x, int y) {
    final int count = getChildCount();
    View child;
    for (int childIdx = 0; childIdx < count; childIdx++) {
      child = getChildAt(childIdx);
      if (y >= child.getTop()
          && y <= child.getBottom()
          && x >= child.getLeft()
          && x <= child.getRight()) {
        return child;
      }
    }
    return null;
  }

  private void ensureScrollHandler() {
    if (scrollHandler == null) {
      scrollHandler = getHandler();
    }
  }

  public DragDropController getDragDropController() {
    return dragDropController;
  }

  @Override
  public void onDragStarted(int x, int y, PhoneFavoriteSquareTileView tileView) {
    if (dragShadowOverlay == null) {
      return;
    }

    dragShadowOverlay.clearAnimation();
    dragShadowBitmap = createDraggedChildBitmap(tileView);
    if (dragShadowBitmap == null) {
      return;
    }

    tileView.getLocationOnScreen(locationOnScreen);
    dragShadowLeft = locationOnScreen[0];
    dragShadowTop = locationOnScreen[1];

    // x and y are the coordinates of the on-screen touch event. Using these
    // and the on-screen location of the tileView, calculate the difference between
    // the position of the user's finger and the position of the tileView. These will
    // be used to offset the location of the drag shadow so that it appears that the
    // tileView is positioned directly under the user's finger.
    touchOffsetToChildLeft = x - dragShadowLeft;
    touchOffsetToChildTop = y - dragShadowTop;

    dragShadowParent.getLocationOnScreen(locationOnScreen);
    dragShadowLeft -= locationOnScreen[0];
    dragShadowTop -= locationOnScreen[1];

    dragShadowOverlay.setImageBitmap(dragShadowBitmap);
    dragShadowOverlay.setVisibility(VISIBLE);
    dragShadowOverlay.setAlpha(DRAG_SHADOW_ALPHA);

    dragShadowOverlay.setX(dragShadowLeft);
    dragShadowOverlay.setY(dragShadowTop);
  }

  @Override
  public void onDragHovered(int x, int y, PhoneFavoriteSquareTileView tileView) {
    // Update the drag shadow location.
    dragShadowParent.getLocationOnScreen(locationOnScreen);
    dragShadowLeft = x - touchOffsetToChildLeft - locationOnScreen[0];
    dragShadowTop = y - touchOffsetToChildTop - locationOnScreen[1];
    // Draw the drag shadow at its last known location if the drag shadow exists.
    if (dragShadowOverlay != null) {
      dragShadowOverlay.setX(dragShadowLeft);
      dragShadowOverlay.setY(dragShadowTop);
    }
  }

  @Override
  public void onDragFinished(int x, int y) {
    if (dragShadowOverlay != null) {
      dragShadowOverlay.clearAnimation();
      dragShadowOverlay
          .animate()
          .alpha(0.0f)
          .setDuration(animationDuration)
          .setListener(dragShadowOverAnimatorListener)
          .start();
    }
  }

  @Override
  public void onDroppedOnRemove() {}

  private Bitmap createDraggedChildBitmap(View view) {
    view.setDrawingCacheEnabled(true);
    final Bitmap cache = view.getDrawingCache();

    Bitmap bitmap = null;
    if (cache != null) {
      try {
        bitmap = cache.copy(Bitmap.Config.ARGB_8888, false);
      } catch (final OutOfMemoryError e) {
        LogUtil.w(LOG_TAG, "Failed to copy bitmap from Drawing cache", e);
        bitmap = null;
      }
    }

    view.destroyDrawingCache();
    view.setDrawingCacheEnabled(false);

    return bitmap;
  }

  @Override
  public PhoneFavoriteSquareTileView getViewForLocation(int x, int y) {
    getLocationOnScreen(locationOnScreen);
    // Calculate the X and Y coordinates of the drag event relative to the view
    final int viewX = x - locationOnScreen[0];
    final int viewY = y - locationOnScreen[1];
    final View child = getViewAtPosition(viewX, viewY);

    if (!(child instanceof PhoneFavoriteSquareTileView)) {
      return null;
    }

    return (PhoneFavoriteSquareTileView) child;
  }
}
