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

package com.android.dialer.callcomposer.camera.camerafocus;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.LinearInterpolator;
import android.view.animation.Transformation;
import java.util.ArrayList;
import java.util.List;

/** Used to draw and render the pie item focus indicator. */
public class PieRenderer extends OverlayRenderer implements FocusIndicator {
  // Sometimes continuous autofocus starts and stops several times quickly.
  // These states are used to make sure the animation is run for at least some
  // time.
  private volatile int state;
  private ScaleAnimation animation = new ScaleAnimation();
  private static final int STATE_IDLE = 0;
  private static final int STATE_FOCUSING = 1;
  private static final int STATE_FINISHING = 2;
  private static final int STATE_PIE = 8;

  private Runnable disappear = new Disappear();
  private Animation.AnimationListener endAction = new EndAction();
  private static final int SCALING_UP_TIME = 600;
  private static final int SCALING_DOWN_TIME = 100;
  private static final int DISAPPEAR_TIMEOUT = 200;
  private static final int DIAL_HORIZONTAL = 157;

  private static final long PIE_FADE_IN_DURATION = 200;
  private static final long PIE_XFADE_DURATION = 200;
  private static final long PIE_SELECT_FADE_DURATION = 300;

  private static final int MSG_OPEN = 0;
  private static final int MSG_CLOSE = 1;
  private static final float PIE_SWEEP = (float) (Math.PI * 2 / 3);
  // geometry
  private Point center;
  private int radius;
  private int radiusInc;

  // the detection if touch is inside a slice is offset
  // inbounds by this amount to allow the selection to show before the
  // finger covers it
  private int touchOffset;

  private List<PieItem> items;

  private PieItem openItem;

  private Paint selectedPaint;
  private Paint subPaint;

  // touch handling
  private PieItem currentItem;

  private Paint focusPaint;
  private int successColor;
  private int failColor;
  private int circleSize;
  private int focusX;
  private int focusY;
  private int centerX;
  private int centerY;

  private int dialAngle;
  private RectF circle;
  private RectF dial;
  private Point point1;
  private Point point2;
  private int startAnimationAngle;
  private boolean focused;
  private int innerOffset;
  private int outerStroke;
  private int innerStroke;
  private boolean tapMode;
  private boolean blockFocus;
  private int touchSlopSquared;
  private Point down;
  private boolean opening;
  private LinearAnimation xFade;
  private LinearAnimation fadeIn;
  private volatile boolean focusCancelled;

  private Handler handler =
      new Handler() {
        @Override
        public void handleMessage(Message msg) {
          switch (msg.what) {
            case MSG_OPEN:
              if (listener != null) {
                listener.onPieOpened(center.x, center.y);
              }
              break;
            case MSG_CLOSE:
              if (listener != null) {
                listener.onPieClosed();
              }
              break;
          }
        }
      };

  private PieListener listener;

  /** Listener for the pie item to communicate back to the renderer. */
  public interface PieListener {
    void onPieOpened(int centerX, int centerY);

    void onPieClosed();
  }

  public void setPieListener(PieListener pl) {
    listener = pl;
  }

  public PieRenderer(Context context) {
    init(context);
  }

  private void init(Context ctx) {
    setVisible(false);
    items = new ArrayList<PieItem>();
    Resources res = ctx.getResources();
    radius = res.getDimensionPixelSize(R.dimen.pie_radius_start);
    circleSize = radius - res.getDimensionPixelSize(R.dimen.focus_radius_offset);
    radiusInc = res.getDimensionPixelSize(R.dimen.pie_radius_increment);
    touchOffset = res.getDimensionPixelSize(R.dimen.pie_touch_offset);
    center = new Point(0, 0);
    selectedPaint = new Paint();
    selectedPaint.setColor(Color.argb(255, 51, 181, 229));
    selectedPaint.setAntiAlias(true);
    subPaint = new Paint();
    subPaint.setAntiAlias(true);
    subPaint.setColor(Color.argb(200, 250, 230, 128));
    focusPaint = new Paint();
    focusPaint.setAntiAlias(true);
    focusPaint.setColor(Color.WHITE);
    focusPaint.setStyle(Paint.Style.STROKE);
    successColor = Color.GREEN;
    failColor = Color.RED;
    circle = new RectF();
    dial = new RectF();
    point1 = new Point();
    point2 = new Point();
    innerOffset = res.getDimensionPixelSize(R.dimen.focus_inner_offset);
    outerStroke = res.getDimensionPixelSize(R.dimen.focus_outer_stroke);
    innerStroke = res.getDimensionPixelSize(R.dimen.focus_inner_stroke);
    state = STATE_IDLE;
    blockFocus = false;
    touchSlopSquared = ViewConfiguration.get(ctx).getScaledTouchSlop();
    touchSlopSquared = touchSlopSquared * touchSlopSquared;
    down = new Point();
  }

  public boolean showsItems() {
    return tapMode;
  }

  public void addItem(PieItem item) {
    // add the item to the pie itself
    items.add(item);
  }

  public void removeItem(PieItem item) {
    items.remove(item);
  }

  public void clearItems() {
    items.clear();
  }

  public void showInCenter() {
    if ((state == STATE_PIE) && isVisible()) {
      tapMode = false;
      show(false);
    } else {
      if (state != STATE_IDLE) {
        cancelFocus();
      }
      state = STATE_PIE;
      setCenter(centerX, centerY);
      tapMode = true;
      show(true);
    }
  }

  public void hide() {
    show(false);
  }

  /**
   * guaranteed has center set
   *
   * @param show
   */
  private void show(boolean show) {
    if (show) {
      state = STATE_PIE;
      // ensure clean state
      currentItem = null;
      openItem = null;
      for (PieItem item : items) {
        item.setSelected(false);
      }
      layoutPie();
      fadeIn();
    } else {
      state = STATE_IDLE;
      tapMode = false;
      if (xFade != null) {
        xFade.cancel();
      }
    }
    setVisible(show);
    handler.sendEmptyMessage(show ? MSG_OPEN : MSG_CLOSE);
  }

  private void fadeIn() {
    fadeIn = new LinearAnimation(0, 1);
    fadeIn.setDuration(PIE_FADE_IN_DURATION);
    fadeIn.setAnimationListener(
        new AnimationListener() {
          @Override
          public void onAnimationStart(Animation animation) {}

          @Override
          public void onAnimationEnd(Animation animation) {
            fadeIn = null;
          }

          @Override
          public void onAnimationRepeat(Animation animation) {}
        });
    fadeIn.startNow();
    overlay.startAnimation(fadeIn);
  }

  public void setCenter(int x, int y) {
    center.x = x;
    center.y = y;
    // when using the pie menu, align the focus ring
    alignFocus(x, y);
  }

  private void layoutPie() {
    int rgap = 2;
    int inner = radius + rgap;
    int outer = radius + radiusInc - rgap;
    int gap = 1;
    layoutItems(items, (float) (Math.PI / 2), inner, outer, gap);
  }

  private void layoutItems(List<PieItem> items, float centerAngle, int inner, int outer, int gap) {
    float emptyangle = PIE_SWEEP / 16;
    float sweep = (PIE_SWEEP - 2 * emptyangle) / items.size();
    float angle = centerAngle - PIE_SWEEP / 2 + emptyangle + sweep / 2;
    // check if we have custom geometry
    // first item we find triggers custom sweep for all
    // this allows us to re-use the path
    for (PieItem item : items) {
      if (item.getCenter() >= 0) {
        sweep = item.getSweep();
        break;
      }
    }
    Path path = makeSlice(getDegrees(0) - gap, getDegrees(sweep) + gap, outer, inner, center);
    for (PieItem item : items) {
      // shared between items
      item.setPath(path);
      if (item.getCenter() >= 0) {
        angle = item.getCenter();
      }
      int w = item.getIntrinsicWidth();
      int h = item.getIntrinsicHeight();
      // move views to outer border
      int r = inner + (outer - inner) * 2 / 3;
      int x = (int) (r * Math.cos(angle));
      int y = center.y - (int) (r * Math.sin(angle)) - h / 2;
      x = center.x + x - w / 2;
      item.setBounds(x, y, x + w, y + h);
      float itemstart = angle - sweep / 2;
      item.setGeometry(itemstart, sweep, inner, outer);
      if (item.hasItems()) {
        layoutItems(item.getItems(), angle, inner, outer + radiusInc / 2, gap);
      }
      angle += sweep;
    }
  }

  private Path makeSlice(float start, float end, int outer, int inner, Point center) {
    RectF bb = new RectF(center.x - outer, center.y - outer, center.x + outer, center.y + outer);
    RectF bbi = new RectF(center.x - inner, center.y - inner, center.x + inner, center.y + inner);
    Path path = new Path();
    path.arcTo(bb, start, end - start, true);
    path.arcTo(bbi, end, start - end);
    path.close();
    return path;
  }

  /**
   * converts a
   *
   * @param angle from 0..PI to Android degrees (clockwise starting at 3 o'clock)
   * @return skia angle
   */
  private float getDegrees(double angle) {
    return (float) (360 - 180 * angle / Math.PI);
  }

  private void startFadeOut() {
    overlay
        .animate()
        .alpha(0)
        .setListener(
            new AnimatorListenerAdapter() {
              @Override
              public void onAnimationEnd(Animator animation) {
                deselect();
                show(false);
                overlay.setAlpha(1);
                super.onAnimationEnd(animation);
              }
            })
        .setDuration(PIE_SELECT_FADE_DURATION);
  }

  @Override
  public void onDraw(Canvas canvas) {
    float alpha = 1;
    if (xFade != null) {
      alpha = xFade.getValue();
    } else if (fadeIn != null) {
      alpha = fadeIn.getValue();
    }
    int state = canvas.save();
    if (fadeIn != null) {
      float sf = 0.9f + alpha * 0.1f;
      canvas.scale(sf, sf, center.x, center.y);
    }
    drawFocus(canvas);
    if (this.state == STATE_FINISHING) {
      canvas.restoreToCount(state);
      return;
    }
    if ((openItem == null) || (xFade != null)) {
      // draw base menu
      for (PieItem item : items) {
        drawItem(canvas, item, alpha);
      }
    }
    if (openItem != null) {
      for (PieItem inner : openItem.getItems()) {
        drawItem(canvas, inner, (xFade != null) ? (1 - 0.5f * alpha) : 1);
      }
    }
    canvas.restoreToCount(state);
  }

  private void drawItem(Canvas canvas, PieItem item, float alpha) {
    if (this.state == STATE_PIE) {
      if (item.getPath() != null) {
        if (item.isSelected()) {
          Paint p = selectedPaint;
          int state = canvas.save();
          float r = getDegrees(item.getStartAngle());
          canvas.rotate(r, center.x, center.y);
          canvas.drawPath(item.getPath(), p);
          canvas.restoreToCount(state);
        }
        alpha = alpha * (item.isEnabled() ? 1 : 0.3f);
        // draw the item view
        item.setAlpha(alpha);
        item.draw(canvas);
      }
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent evt) {
    float x = evt.getX();
    float y = evt.getY();
    int action = evt.getActionMasked();
    PointF polar = getPolar(x, y, !(tapMode));
    if (MotionEvent.ACTION_DOWN == action) {
      down.x = (int) evt.getX();
      down.y = (int) evt.getY();
      opening = false;
      if (tapMode) {
        PieItem item = findItem(polar);
        if ((item != null) && (currentItem != item)) {
          state = STATE_PIE;
          onEnter(item);
        }
      } else {
        setCenter((int) x, (int) y);
        show(true);
      }
      return true;
    } else if (MotionEvent.ACTION_UP == action) {
      if (isVisible()) {
        PieItem item = currentItem;
        if (tapMode) {
          item = findItem(polar);
          if (item != null && opening) {
            opening = false;
            return true;
          }
        }
        if (item == null) {
          tapMode = false;
          show(false);
        } else if (!opening && !item.hasItems()) {
          item.performClick();
          startFadeOut();
          tapMode = false;
        }
        return true;
      }
    } else if (MotionEvent.ACTION_CANCEL == action) {
      if (isVisible() || tapMode) {
        show(false);
      }
      deselect();
      return false;
    } else if (MotionEvent.ACTION_MOVE == action) {
      if (polar.y < radius) {
        if (openItem != null) {
          openItem = null;
        } else {
          deselect();
        }
        return false;
      }
      PieItem item = findItem(polar);
      boolean moved = hasMoved(evt);
      if ((item != null) && (currentItem != item) && (!opening || moved)) {
        // only select if we didn't just open or have moved past slop
        opening = false;
        if (moved) {
          // switch back to swipe mode
          tapMode = false;
        }
        onEnter(item);
      }
    }
    return false;
  }

  private boolean hasMoved(MotionEvent e) {
    return touchSlopSquared
        < (e.getX() - down.x) * (e.getX() - down.x) + (e.getY() - down.y) * (e.getY() - down.y);
  }

  /**
   * enter a slice for a view updates model only
   *
   * @param item
   */
  private void onEnter(PieItem item) {
    if (currentItem != null) {
      currentItem.setSelected(false);
    }
    if (item != null && item.isEnabled()) {
      item.setSelected(true);
      currentItem = item;
      if ((currentItem != openItem) && currentItem.hasItems()) {
        openCurrentItem();
      }
    } else {
      currentItem = null;
    }
  }

  private void deselect() {
    if (currentItem != null) {
      currentItem.setSelected(false);
    }
    if (openItem != null) {
      openItem = null;
    }
    currentItem = null;
  }

  private void openCurrentItem() {
    if ((currentItem != null) && currentItem.hasItems()) {
      currentItem.setSelected(false);
      openItem = currentItem;
      opening = true;
      xFade = new LinearAnimation(1, 0);
      xFade.setDuration(PIE_XFADE_DURATION);
      xFade.setAnimationListener(
          new AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
              xFade = null;
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
          });
      xFade.startNow();
      overlay.startAnimation(xFade);
    }
  }

  private PointF getPolar(float x, float y, boolean useOffset) {
    PointF res = new PointF();
    // get angle and radius from x/y
    res.x = (float) Math.PI / 2;
    x = x - center.x;
    y = center.y - y;
    res.y = (float) Math.sqrt(x * x + y * y);
    if (x != 0) {
      res.x = (float) Math.atan2(y, x);
      if (res.x < 0) {
        res.x = (float) (2 * Math.PI + res.x);
      }
    }
    res.y = res.y + (useOffset ? touchOffset : 0);
    return res;
  }

  /**
   * @param polar x: angle, y: dist
   * @return the item at angle/dist or null
   */
  private PieItem findItem(PointF polar) {
    // find the matching item:
    List<PieItem> items = (openItem != null) ? openItem.getItems() : this.items;
    for (PieItem item : items) {
      if (inside(polar, item)) {
        return item;
      }
    }
    return null;
  }

  private boolean inside(PointF polar, PieItem item) {
    return (item.getInnerRadius() < polar.y)
        && (item.getStartAngle() < polar.x)
        && (item.getStartAngle() + item.getSweep() > polar.x)
        && (!tapMode || (item.getOuterRadius() > polar.y));
  }

  @Override
  public boolean handlesTouch() {
    return true;
  }

  // focus specific code

  public void setBlockFocus(boolean blocked) {
    blockFocus = blocked;
    if (blocked) {
      clear();
    }
  }

  public void setFocus(int x, int y) {
    focusX = x;
    focusY = y;
    setCircle(focusX, focusY);
  }

  public void alignFocus(int x, int y) {
    overlay.removeCallbacks(disappear);
    animation.cancel();
    animation.reset();
    focusX = x;
    focusY = y;
    dialAngle = DIAL_HORIZONTAL;
    setCircle(x, y);
    focused = false;
  }

  public int getSize() {
    return 2 * circleSize;
  }

  private int getRandomRange() {
    return (int) (-60 + 120 * Math.random());
  }

  @Override
  public void layout(int l, int t, int r, int b) {
    super.layout(l, t, r, b);
    centerX = (r - l) / 2;
    centerY = (b - t) / 2;
    focusX = centerX;
    focusY = centerY;
    setCircle(focusX, focusY);
    if (isVisible() && state == STATE_PIE) {
      setCenter(centerX, centerY);
      layoutPie();
    }
  }

  private void setCircle(int cx, int cy) {
    circle.set(cx - circleSize, cy - circleSize, cx + circleSize, cy + circleSize);
    dial.set(
        cx - circleSize + innerOffset,
        cy - circleSize + innerOffset,
        cx + circleSize - innerOffset,
        cy + circleSize - innerOffset);
  }

  public void drawFocus(Canvas canvas) {
    if (blockFocus) {
      return;
    }
    focusPaint.setStrokeWidth(outerStroke);
    canvas.drawCircle((float) focusX, (float) focusY, (float) circleSize, focusPaint);
    if (state == STATE_PIE) {
      return;
    }
    int color = focusPaint.getColor();
    if (state == STATE_FINISHING) {
      focusPaint.setColor(focused ? successColor : failColor);
    }
    focusPaint.setStrokeWidth(innerStroke);
    drawLine(canvas, dialAngle, focusPaint);
    drawLine(canvas, dialAngle + 45, focusPaint);
    drawLine(canvas, dialAngle + 180, focusPaint);
    drawLine(canvas, dialAngle + 225, focusPaint);
    canvas.save();
    // rotate the arc instead of its offset to better use framework's shape caching
    canvas.rotate(dialAngle, focusX, focusY);
    canvas.drawArc(dial, 0, 45, false, focusPaint);
    canvas.drawArc(dial, 180, 45, false, focusPaint);
    canvas.restore();
    focusPaint.setColor(color);
  }

  private void drawLine(Canvas canvas, int angle, Paint p) {
    convertCart(angle, circleSize - innerOffset, point1);
    convertCart(angle, circleSize - innerOffset + innerOffset / 3, point2);
    canvas.drawLine(point1.x + focusX, point1.y + focusY, point2.x + focusX, point2.y + focusY, p);
  }

  private static void convertCart(int angle, int radius, Point out) {
    double a = 2 * Math.PI * (angle % 360) / 360;
    out.x = (int) (radius * Math.cos(a) + 0.5);
    out.y = (int) (radius * Math.sin(a) + 0.5);
  }

  @Override
  public void showStart() {
    if (state == STATE_PIE) {
      return;
    }
    cancelFocus();
    startAnimationAngle = 67;
    int range = getRandomRange();
    startAnimation(SCALING_UP_TIME, false, startAnimationAngle, startAnimationAngle + range);
    state = STATE_FOCUSING;
  }

  @Override
  public void showSuccess(boolean timeout) {
    if (state == STATE_FOCUSING) {
      startAnimation(SCALING_DOWN_TIME, timeout, startAnimationAngle);
      state = STATE_FINISHING;
      focused = true;
    }
  }

  @Override
  public void showFail(boolean timeout) {
    if (state == STATE_FOCUSING) {
      startAnimation(SCALING_DOWN_TIME, timeout, startAnimationAngle);
      state = STATE_FINISHING;
      focused = false;
    }
  }

  private void cancelFocus() {
    focusCancelled = true;
    overlay.removeCallbacks(disappear);
    if (animation != null) {
      animation.cancel();
    }
    focusCancelled = false;
    focused = false;
    state = STATE_IDLE;
  }

  @Override
  public void clear() {
    if (state == STATE_PIE) {
      return;
    }
    cancelFocus();
    overlay.post(disappear);
  }

  private void startAnimation(long duration, boolean timeout, float toScale) {
    startAnimation(duration, timeout, dialAngle, toScale);
  }

  private void startAnimation(long duration, boolean timeout, float fromScale, float toScale) {
    setVisible(true);
    animation.reset();
    animation.setDuration(duration);
    animation.setScale(fromScale, toScale);
    animation.setAnimationListener(timeout ? endAction : null);
    overlay.startAnimation(animation);
    update();
  }

  private class EndAction implements Animation.AnimationListener {
    @Override
    public void onAnimationEnd(Animation animation) {
      // Keep the focus indicator for some time.
      if (!focusCancelled) {
        overlay.postDelayed(disappear, DISAPPEAR_TIMEOUT);
      }
    }

    @Override
    public void onAnimationRepeat(Animation animation) {}

    @Override
    public void onAnimationStart(Animation animation) {}
  }

  private class Disappear implements Runnable {
    @Override
    public void run() {
      if (state == STATE_PIE) {
        return;
      }
      setVisible(false);
      focusX = centerX;
      focusY = centerY;
      state = STATE_IDLE;
      setCircle(focusX, focusY);
      focused = false;
    }
  }

  private class ScaleAnimation extends Animation {
    private float from = 1f;
    private float to = 1f;

    public ScaleAnimation() {
      setFillAfter(true);
    }

    public void setScale(float from, float to) {
      this.from = from;
      this.to = to;
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
      dialAngle = (int) (from + (to - from) * interpolatedTime);
    }
  }

  private static class LinearAnimation extends Animation {
    private float from;
    private float to;
    private float value;

    public LinearAnimation(float from, float to) {
      setFillAfter(true);
      setInterpolator(new LinearInterpolator());
      this.from = from;
      this.to = to;
    }

    public float getValue() {
      return value;
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
      value = (from + (to - from) * interpolatedTime);
    }
  }
}
