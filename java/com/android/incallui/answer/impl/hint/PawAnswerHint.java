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
 * limitations under the License
 */

package com.android.incallui.answer.impl.hint;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.DimenRes;
import android.support.annotation.NonNull;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.dialer.common.Assert;

/**
 * An Answer hint that animates a {@link Drawable} payload with animation similar to {@link
 * DotAnswerHint}.
 */
public final class PawAnswerHint implements AnswerHint {

  private static final long FADE_IN_DELAY_SCALE_MILLIS = 380;
  private static final long FADE_IN_DURATION_SCALE_MILLIS = 200;
  private static final long FADE_IN_DELAY_ALPHA_MILLIS = 340;
  private static final long FADE_IN_DURATION_ALPHA_MILLIS = 50;

  private static final long SWIPE_UP_DURATION_ALPHA_MILLIS = 500;

  private static final long FADE_OUT_DELAY_SCALE_SMALL_MILLIS = 90;
  private static final long FADE_OUT_DURATION_SCALE_MILLIS = 100;
  private static final long FADE_OUT_DELAY_ALPHA_MILLIS = 130;
  private static final long FADE_OUT_DURATION_ALPHA_MILLIS = 170;

  private static final float IMAGE_SCALE = 1.5f;
  private static final float FADE_SCALE = 2.0f;

  private final Context context;
  private final Drawable payload;
  private final long puckUpDurationMillis;
  private final long puckUpDelayMillis;

  private View puck;
  private View payloadView;
  private View answerHintContainer;
  private AnimatorSet answerGestureHintAnim;

  public PawAnswerHint(
      @NonNull Context context,
      @NonNull Drawable payload,
      long puckUpDurationMillis,
      long puckUpDelayMillis) {
    this.context = Assert.isNotNull(context);
    this.payload = Assert.isNotNull(payload);
    this.puckUpDurationMillis = puckUpDurationMillis;
    this.puckUpDelayMillis = puckUpDelayMillis;
  }

  @Override
  public void onCreateView(
      LayoutInflater inflater, ViewGroup container, View puck, TextView hintText) {
    this.puck = puck;
    View view = inflater.inflate(R.layout.paw_hint, container, true);
    answerHintContainer = view.findViewById(R.id.answer_hint_container);
    payloadView = view.findViewById(R.id.paw_image);
    hintText.setTextSize(
        TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(R.dimen.hint_text_size));
    ((ImageView) payloadView).setImageDrawable(payload);
  }

  @Override
  public void onBounceStart() {
    if (answerGestureHintAnim == null) {
      answerGestureHintAnim = new AnimatorSet();
      int[] puckLocation = new int[2];
      puck.getLocationInWindow(puckLocation);
      answerHintContainer.setY(puckLocation[1] + getDimension(R.dimen.hint_initial_offset));

      Animator fadeIn = createFadeIn();

      Animator swipeUp =
          ObjectAnimator.ofFloat(
              answerHintContainer,
              View.TRANSLATION_Y,
              puckLocation[1] - getDimension(R.dimen.hint_offset));
      swipeUp.setInterpolator(new FastOutSlowInInterpolator());
      swipeUp.setDuration(SWIPE_UP_DURATION_ALPHA_MILLIS);

      Animator fadeOut = createFadeOut();

      answerGestureHintAnim.play(fadeIn).after(puckUpDelayMillis);
      answerGestureHintAnim.play(swipeUp).after(fadeIn);
      // The fade out should start fading the alpha just as the puck is dropping. Scaling will start
      // a bit earlier.
      answerGestureHintAnim
          .play(fadeOut)
          .after(puckUpDelayMillis + puckUpDurationMillis - FADE_OUT_DELAY_ALPHA_MILLIS);

      fadeIn.addListener(
          new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
              super.onAnimationStart(animation);
              payloadView.setAlpha(0);
              payloadView.setScaleX(1);
              payloadView.setScaleY(1);
              answerHintContainer.setY(puckLocation[1] + getDimension(R.dimen.hint_initial_offset));
              answerHintContainer.setVisibility(View.VISIBLE);
            }
          });
    }

    answerGestureHintAnim.start();
  }

  private Animator createFadeIn() {
    AnimatorSet set = new AnimatorSet();
    set.play(createFadeInScaleAndAlpha(payloadView));
    return set;
  }

  private static Animator createFadeInScaleAndAlpha(View target) {
    Animator scale =
        createUniformScaleAnimator(
            target,
            FADE_SCALE,
            IMAGE_SCALE,
            FADE_IN_DURATION_SCALE_MILLIS,
            FADE_IN_DELAY_SCALE_MILLIS,
            new LinearInterpolator());
    Animator alpha =
        createAlphaAnimator(
            target,
            0f,
            1.0f,
            FADE_IN_DURATION_ALPHA_MILLIS,
            FADE_IN_DELAY_ALPHA_MILLIS,
            new LinearInterpolator());
    AnimatorSet set = new AnimatorSet();
    set.play(scale).with(alpha);
    return set;
  }

  private Animator createFadeOut() {
    AnimatorSet set = new AnimatorSet();
    set.play(createFadeOutScaleAndAlpha(payloadView, FADE_OUT_DELAY_SCALE_SMALL_MILLIS));
    return set;
  }

  private static Animator createFadeOutScaleAndAlpha(View target, long scaleDelay) {
    Animator scale =
        createUniformScaleAnimator(
            target,
            IMAGE_SCALE,
            FADE_SCALE,
            FADE_OUT_DURATION_SCALE_MILLIS,
            scaleDelay,
            new LinearInterpolator());
    Animator alpha =
        createAlphaAnimator(
            target,
            1.0f,
            0.0f,
            FADE_OUT_DURATION_ALPHA_MILLIS,
            FADE_OUT_DELAY_ALPHA_MILLIS,
            new LinearInterpolator());
    AnimatorSet set = new AnimatorSet();
    set.play(scale).with(alpha);
    return set;
  }

  @Override
  public void onBounceEnd() {
    if (answerGestureHintAnim != null) {
      answerGestureHintAnim.end();
      answerGestureHintAnim = null;
      answerHintContainer.setVisibility(View.GONE);
    }
  }

  @Override
  public void onAnswered() {
    // Do nothing
  }

  private float getDimension(@DimenRes int id) {
    return context.getResources().getDimension(id);
  }

  private static Animator createUniformScaleAnimator(
      View target,
      float scaleBegin,
      float scaleEnd,
      long duration,
      long delay,
      Interpolator interpolator) {
    Animator scaleX = ObjectAnimator.ofFloat(target, View.SCALE_X, scaleBegin, scaleEnd);
    Animator scaleY = ObjectAnimator.ofFloat(target, View.SCALE_Y, scaleBegin, scaleEnd);
    scaleX.setDuration(duration);
    scaleY.setDuration(duration);
    scaleX.setInterpolator(interpolator);
    scaleY.setInterpolator(interpolator);
    AnimatorSet set = new AnimatorSet();
    set.play(scaleX).with(scaleY).after(delay);
    return set;
  }

  private static Animator createAlphaAnimator(
      View target, float begin, float end, long duration, long delay, Interpolator interpolator) {
    Animator alpha = ObjectAnimator.ofFloat(target, View.ALPHA, begin, end);
    alpha.setDuration(duration);
    alpha.setInterpolator(interpolator);
    alpha.setStartDelay(delay);
    return alpha;
  }
}
