/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.dialer.voicemail.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.widget.Button;
import com.android.dialer.voicemail.settings.RecordVoicemailGreetingActivity.ButtonState;

/** Custom Button View for Dialer voicemail greeting recording */
public class RecordButton extends Button {

  private final float trackWidth = getResources().getDimensionPixelSize(R.dimen.track_width);
  private final int centerIconRadius =
      getResources().getDimensionPixelSize(R.dimen.center_icon_radius);
  private final int secondaryTrackAlpha = 64;

  private float mainTrackFraction;
  private float secondaryTrackFraction;

  private Rect centerIconRect;
  private RectF bodyRect;

  private Drawable readyDrawable;
  private Drawable recordingDrawable;
  private Drawable recordedDrawable;
  private Drawable playingDrawable;
  private Drawable currentCenterDrawable;

  private Paint mainTrackPaint;
  private Paint secondaryTrackPaint;

  public RecordButton(Context context) {
    super(context);
    init();
  }

  public RecordButton(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public RecordButton(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init();
  }

  /**
   * Updates bounds for main and secondary tracks and the size of the center Drawable based on View
   * resizing
   */
  @Override
  protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    // Canvas.drawArc() method draws from center of stroke, so the trackWidth must be accounted for
    float viewRadius = Math.min(width, height) / 2f - trackWidth;
    float centerX = width / 2f;
    float centerY = viewRadius + trackWidth / 2f;

    bodyRect =
        new RectF(
            centerX - viewRadius, centerY - viewRadius, centerX + viewRadius, centerY + viewRadius);

    centerIconRect =
        new Rect(
            (int) centerX - centerIconRadius,
            (int) centerY - centerIconRadius,
            (int) centerX + centerIconRadius,
            (int) centerY + centerIconRadius);
  }

  private void init() {
    readyDrawable = ContextCompat.getDrawable(getContext(), R.drawable.start_recording_drawable);
    recordingDrawable = ContextCompat.getDrawable(getContext(), R.drawable.stop_recording_drawable);
    recordedDrawable = ContextCompat.getDrawable(getContext(), R.drawable.start_playback_drawable);
    playingDrawable = ContextCompat.getDrawable(getContext(), R.drawable.stop_playback_drawable);

    fixQuantumIconTint(Color.WHITE);

    mainTrackPaint = getBasePaint(R.color.dialer_call_green);
    secondaryTrackPaint = getBasePaint(R.color.dialer_call_green);
    secondaryTrackPaint.setAlpha(secondaryTrackAlpha);

    setState(RecordVoicemailGreetingActivity.RECORD_GREETING_INIT);
  }

  private void fixQuantumIconTint(int color) {
    Drawable playArrow = ((LayerDrawable) recordedDrawable).findDrawableByLayerId(R.id.play_icon);
    playArrow.mutate().setTint(color);
    ((LayerDrawable) recordedDrawable).setDrawableByLayerId(R.id.play_icon, playArrow);

    Drawable micIcon = ((LayerDrawable) readyDrawable).findDrawableByLayerId(R.id.record_icon);
    micIcon.mutate().setTint(color);
    ((LayerDrawable) readyDrawable).setDrawableByLayerId(R.id.record_icon, micIcon);
  }

  /** Returns Paint with base attributes for drawing the main and secondary tracks */
  private Paint getBasePaint(int id) {
    Paint paint = new Paint();
    paint.setAntiAlias(true);
    paint.setStrokeWidth(trackWidth);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStyle(Paint.Style.STROKE);
    paint.setColor(ContextCompat.getColor(getContext(), id));
    return paint;
  }

  /** Sets the fraction value of progress tracks, this will trigger a redraw of the button. */
  public void setTracks(float mainTrackFraction, float secondaryTrackFraction) {
    this.mainTrackFraction = mainTrackFraction;
    this.secondaryTrackFraction = secondaryTrackFraction;
    invalidate();
  }

  /**
   * Sets internal state of RecordButton. This will also trigger UI refresh of the button to reflect
   * the new state.
   */
  public void setState(@ButtonState int state) {
    switch (state) {
      case RecordVoicemailGreetingActivity.RECORD_GREETING_INIT:
        mainTrackPaint = getBasePaint(R.color.dialer_call_green);
        secondaryTrackPaint = getBasePaint(R.color.dialer_call_green);
        secondaryTrackPaint.setAlpha(secondaryTrackAlpha);
        currentCenterDrawable = readyDrawable;
        break;
      case RecordVoicemailGreetingActivity.RECORD_GREETING_PLAYING_BACK:
        mainTrackPaint = getBasePaint(R.color.google_blue_500);
        secondaryTrackPaint = getBasePaint(R.color.google_blue_50);
        currentCenterDrawable = playingDrawable;
        break;
      case RecordVoicemailGreetingActivity.RECORD_GREETING_RECORDED:
        mainTrackPaint = getBasePaint(R.color.google_blue_500);
        secondaryTrackPaint = getBasePaint(R.color.google_blue_50);
        currentCenterDrawable = recordedDrawable;
        break;
      case RecordVoicemailGreetingActivity.RECORD_GREETING_RECORDING:
        mainTrackPaint = getBasePaint(R.color.dialer_red);
        secondaryTrackPaint = getBasePaint(R.color.dialer_red);
        secondaryTrackPaint.setAlpha(secondaryTrackAlpha);
        currentCenterDrawable = recordingDrawable;
        break;
      default:
        throw new RuntimeException("Invalid button state");
    }
    refreshDrawableState();
    invalidate();
  }

  /**
   * Handles drawing the main and secondary track arcs and the center Drawable image based on track
   * fractions and the Button's current state
   */
  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    canvas.drawArc(bodyRect, -90, secondaryTrackFraction * 360, false, secondaryTrackPaint);
    canvas.drawArc(bodyRect, -90, mainTrackFraction * 360, false, mainTrackPaint);

    // TODO(marquelle) - Add pulse

    currentCenterDrawable.setBounds(centerIconRect);
    currentCenterDrawable.draw(canvas);
  }
}
