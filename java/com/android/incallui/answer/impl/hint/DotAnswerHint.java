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
import android.support.annotation.DimenRes;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

/** An Answer hint that uses a green swiping dot. */
public class DotAnswerHint implements AnswerHint {

  private static final float ANSWER_HINT_SMALL_ALPHA = 0.8f;
  private static final float ANSWER_HINT_MID_ALPHA = 0.5f;
  private static final float ANSWER_HINT_LARGE_ALPHA = 0.2f;

  private static final long FADE_IN_DELAY_SCALE_MILLIS = 380;
  private static final long FADE_IN_DURATION_SCALE_MILLIS = 200;
  private static final long FADE_IN_DELAY_ALPHA_MILLIS = 340;
  private static final long FADE_IN_DURATION_ALPHA_MILLIS = 50;

  private static final long SWIPE_UP_DURATION_ALPHA_MILLIS = 500;

  private static final long FADE_OUT_DELAY_SCALE_SMALL_MILLIS = 90;
  private static final long FADE_OUT_DELAY_SCALE_MID_MILLIS = 70;
  private static final long FADE_OUT_DELAY_SCALE_LARGE_MILLIS = 10;
  private static final long FADE_OUT_DURATION_SCALE_MILLIS = 100;
  private static final long FADE_OUT_DELAY_ALPHA_MILLIS = 130;
  private static final long FADE_OUT_DURATION_ALPHA_MILLIS = 170;

  private final Context context;
  private final long puckUpDurationMillis;
  private final long puckUpDelayMillis;

  private View puck;

  private View answerHintSmall;
  private View answerHintMid;
  private View answerHintLarge;
  private View answerHintContainer;
  private AnimatorSet answerGestureHintAnim;

  public DotAnswerHint(Context context, long puckUpDurationMillis, long puckUpDelayMillis) {
    this.context = context;
    this.puckUpDurationMillis = puckUpDurationMillis;
    this.puckUpDelayMillis = puckUpDelayMillis;
  }

  @Override
  public void onCreateView(
      LayoutInflater inflater, ViewGroup container, View puck, TextView hintText) {
    this.puck = puck;
    View view = inflater.inflate(R.layout.dot_hint, container, true);
    answerHintContainer = view.findViewById(R.id.answer_hint_container);
    answerHintSmall = view.findViewById(R.id.answer_hint_small);
    answerHintMid = view.findViewById(R.id.answer_hint_mid);
    answerHintLarge = view.findViewById(R.id.answer_hint_large);
    hintText.setTextSize(
        TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(R.dimen.hint_text_size));
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
              answerHintSmall.setAlpha(0);
              answerHintSmall.setScaleX(1);
              answerHintSmall.setScaleY(1);
              answerHintMid.setAlpha(0);
              answerHintMid.setScaleX(1);
              answerHintMid.setScaleY(1);
              answerHintLarge.setAlpha(0);
              answerHintLarge.setScaleX(1);
              answerHintLarge.setScaleY(1);
              answerHintContainer.setY(puckLocation[1] + getDimension(R.dimen.hint_initial_offset));
              answerHintContainer.setVisibility(View.VISIBLE);
            }
          });
    }

    answerGestureHintAnim.start();
  }

  private Animator createFadeIn() {
    AnimatorSet set = new AnimatorSet();
    set.play(
            createFadeInScaleAndAlpha(
                answerHintSmall,
                R.dimen.hint_small_begin_size,
                R.dimen.hint_small_end_size,
                ANSWER_HINT_SMALL_ALPHA))
        .with(
            createFadeInScaleAndAlpha(
                answerHintMid,
                R.dimen.hint_mid_begin_size,
                R.dimen.hint_mid_end_size,
                ANSWER_HINT_MID_ALPHA))
        .with(
            createFadeInScaleAndAlpha(
                answerHintLarge,
                R.dimen.hint_large_begin_size,
                R.dimen.hint_large_end_size,
                ANSWER_HINT_LARGE_ALPHA));
    return set;
  }

  private Animator createFadeInScaleAndAlpha(
      View target, @DimenRes int beginSize, @DimenRes int endSize, float endAlpha) {
    Animator scale =
        createUniformScaleAnimator(
            target,
            getDimension(beginSize),
            getDimension(beginSize),
            getDimension(endSize),
            FADE_IN_DURATION_SCALE_MILLIS,
            FADE_IN_DELAY_SCALE_MILLIS,
            new LinearInterpolator());
    Animator alpha =
        createAlphaAnimator(
            target,
            0f,
            endAlpha,
            FADE_IN_DURATION_ALPHA_MILLIS,
            FADE_IN_DELAY_ALPHA_MILLIS,
            new LinearInterpolator());
    AnimatorSet set = new AnimatorSet();
    set.play(scale).with(alpha);
    return set;
  }

  private Animator createFadeOut() {
    AnimatorSet set = new AnimatorSet();
    set.play(
            createFadeOutScaleAndAlpha(
                answerHintSmall,
                R.dimen.hint_small_begin_size,
                R.dimen.hint_small_end_size,
                FADE_OUT_DELAY_SCALE_SMALL_MILLIS,
                ANSWER_HINT_SMALL_ALPHA))
        .with(
            createFadeOutScaleAndAlpha(
                answerHintMid,
                R.dimen.hint_mid_begin_size,
                R.dimen.hint_mid_end_size,
                FADE_OUT_DELAY_SCALE_MID_MILLIS,
                ANSWER_HINT_MID_ALPHA))
        .with(
            createFadeOutScaleAndAlpha(
                answerHintLarge,
                R.dimen.hint_large_begin_size,
                R.dimen.hint_large_end_size,
                FADE_OUT_DELAY_SCALE_LARGE_MILLIS,
                ANSWER_HINT_LARGE_ALPHA));
    return set;
  }

  private Animator createFadeOutScaleAndAlpha(
      View target,
      @DimenRes int beginSize,
      @DimenRes int endSize,
      long scaleDelay,
      float endAlpha) {
    Animator scale =
        createUniformScaleAnimator(
            target,
            getDimension(beginSize),
            getDimension(endSize),
            getDimension(beginSize),
            FADE_OUT_DURATION_SCALE_MILLIS,
            scaleDelay,
            new LinearInterpolator());
    Animator alpha =
        createAlphaAnimator(
            target,
            endAlpha,
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
    AnswerHintFactory.increaseAnsweredCount(context);
  }

  private float getDimension(@DimenRes int id) {
    return context.getResources().getDimension(id);
  }

  private static Animator createUniformScaleAnimator(
      View target,
      float original,
      float begin,
      float end,
      long duration,
      long delay,
      Interpolator interpolator) {
    float scaleBegin = begin / original;
    float scaleEnd = end / original;
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
