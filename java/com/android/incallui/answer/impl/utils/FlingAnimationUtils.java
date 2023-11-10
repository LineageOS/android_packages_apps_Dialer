/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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

package com.android.incallui.answer.impl.utils;

import android.animation.Animator;
import android.content.Context;
import android.view.ViewPropertyAnimator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/** Utility class to calculate general fling animation when the finger is released. */
public class FlingAnimationUtils {

  private static final float LINEAR_OUT_SLOW_IN_X2 = 0.35f;
  private static final float LINEAR_OUT_FASTER_IN_X2 = 0.5f;
  private static final float LINEAR_OUT_FASTER_IN_Y2_MIN = 0.4f;
  private static final float LINEAR_OUT_FASTER_IN_Y2_MAX = 0.5f;
  private static final float MIN_VELOCITY_DP_PER_SECOND = 250;
  private static final float HIGH_VELOCITY_DP_PER_SECOND = 3000;

  /** Fancy math. http://en.wikipedia.org/wiki/B%C3%A9zier_curve */
  private static final float LINEAR_OUT_SLOW_IN_START_GRADIENT = 1.0f / LINEAR_OUT_SLOW_IN_X2;

  private final Interpolator linearOutSlowIn;

  private final float minVelocityPxPerSecond;
  private final float maxLengthSeconds;
  private final float highVelocityPxPerSecond;

  private final AnimatorProperties animatorProperties = new AnimatorProperties();

  public FlingAnimationUtils(Context ctx, float maxLengthSeconds) {
    this.maxLengthSeconds = maxLengthSeconds;
    linearOutSlowIn = new PathInterpolator(0, 0, LINEAR_OUT_SLOW_IN_X2, 1);
    minVelocityPxPerSecond =
        MIN_VELOCITY_DP_PER_SECOND * ctx.getResources().getDisplayMetrics().density;
    highVelocityPxPerSecond =
        HIGH_VELOCITY_DP_PER_SECOND * ctx.getResources().getDisplayMetrics().density;
  }

  /**
   * Applies the interpolator and length to the animator, such that the fling animation is
   * consistent with the finger motion.
   *
   * @param animator the animator to apply
   * @param currValue the current value
   * @param endValue the end value of the animator
   * @param velocity the current velocity of the motion
   */
  public void apply(Animator animator, float currValue, float endValue, float velocity) {
    apply(animator, currValue, endValue, velocity, Math.abs(endValue - currValue));
  }

  /**
   * Applies the interpolator and length to the animator, such that the fling animation is
   * consistent with the finger motion.
   *
   * @param animator the animator to apply
   * @param currValue the current value
   * @param endValue the end value of the animator
   * @param velocity the current velocity of the motion
   */
  public void apply(
      ViewPropertyAnimator animator, float currValue, float endValue, float velocity) {
    apply(animator, currValue, endValue, velocity, Math.abs(endValue - currValue));
  }

  /**
   * Applies the interpolator and length to the animator, such that the fling animation is
   * consistent with the finger motion.
   *
   * @param animator the animator to apply
   * @param currValue the current value
   * @param endValue the end value of the animator
   * @param velocity the current velocity of the motion
   * @param maxDistance the maximum distance for this interaction; the maximum animation length gets
   *     multiplied by the ratio between the actual distance and this value
   */
  public void apply(
      Animator animator, float currValue, float endValue, float velocity, float maxDistance) {
    AnimatorProperties properties = getProperties(currValue, endValue, velocity, maxDistance);
    animator.setDuration(properties.duration);
    animator.setInterpolator(properties.interpolator);
  }

  /**
   * Applies the interpolator and length to the animator, such that the fling animation is
   * consistent with the finger motion.
   *
   * @param animator the animator to apply
   * @param currValue the current value
   * @param endValue the end value of the animator
   * @param velocity the current velocity of the motion
   * @param maxDistance the maximum distance for this interaction; the maximum animation length gets
   *     multiplied by the ratio between the actual distance and this value
   */
  public void apply(
      ViewPropertyAnimator animator,
      float currValue,
      float endValue,
      float velocity,
      float maxDistance) {
    AnimatorProperties properties = getProperties(currValue, endValue, velocity, maxDistance);
    animator.setDuration(properties.duration);
    animator.setInterpolator(properties.interpolator);
  }

  private AnimatorProperties getProperties(
      float currValue, float endValue, float velocity, float maxDistance) {
    float maxLengthSeconds =
        (float) (this.maxLengthSeconds * Math.sqrt(Math.abs(endValue - currValue) / maxDistance));
    float diff = Math.abs(endValue - currValue);
    float velAbs = Math.abs(velocity);
    float durationSeconds = LINEAR_OUT_SLOW_IN_START_GRADIENT * diff / velAbs;
    if (durationSeconds <= maxLengthSeconds) {
      animatorProperties.interpolator = linearOutSlowIn;
    } else if (velAbs >= minVelocityPxPerSecond) {

      // Cross fade between fast-out-slow-in and linear interpolator with current velocity.
      durationSeconds = maxLengthSeconds;
      VelocityInterpolator velocityInterpolator =
          new VelocityInterpolator(durationSeconds, velAbs, diff);
      animatorProperties.interpolator =
          new InterpolatorInterpolator(velocityInterpolator, linearOutSlowIn, linearOutSlowIn);
    } else {

      // Just use a normal interpolator which doesn't take the velocity into account.
      durationSeconds = maxLengthSeconds;
      animatorProperties.interpolator = Interpolators.FAST_OUT_SLOW_IN;
    }
    animatorProperties.duration = (long) (durationSeconds * 1000);
    return animatorProperties;
  }

  /**
   * Applies the interpolator and length to the animator, such that the fling animation is
   * consistent with the finger motion for the case when the animation is making something
   * disappear.
   *
   * @param animator the animator to apply
   * @param currValue the current value
   * @param endValue the end value of the animator
   * @param velocity the current velocity of the motion
   * @param maxDistance the maximum distance for this interaction; the maximum animation length gets
   *     multiplied by the ratio between the actual distance and this value
   */
  public void applyDismissing(
      Animator animator, float currValue, float endValue, float velocity, float maxDistance) {
    AnimatorProperties properties =
        getDismissingProperties(currValue, endValue, velocity, maxDistance);
    animator.setDuration(properties.duration);
    animator.setInterpolator(properties.interpolator);
  }

  /**
   * Applies the interpolator and length to the animator, such that the fling animation is
   * consistent with the finger motion for the case when the animation is making something
   * disappear.
   *
   * @param animator the animator to apply
   * @param currValue the current value
   * @param endValue the end value of the animator
   * @param velocity the current velocity of the motion
   * @param maxDistance the maximum distance for this interaction; the maximum animation length gets
   *     multiplied by the ratio between the actual distance and this value
   */
  public void applyDismissing(
      ViewPropertyAnimator animator,
      float currValue,
      float endValue,
      float velocity,
      float maxDistance) {
    AnimatorProperties properties =
        getDismissingProperties(currValue, endValue, velocity, maxDistance);
    animator.setDuration(properties.duration);
    animator.setInterpolator(properties.interpolator);
  }

  private AnimatorProperties getDismissingProperties(
      float currValue, float endValue, float velocity, float maxDistance) {
    float maxLengthSeconds =
        (float)
            (this.maxLengthSeconds * Math.pow(Math.abs(endValue - currValue) / maxDistance, 0.5f));
    float diff = Math.abs(endValue - currValue);
    float velAbs = Math.abs(velocity);
    float y2 = calculateLinearOutFasterInY2(velAbs);

    float startGradient = y2 / LINEAR_OUT_FASTER_IN_X2;
    Interpolator mLinearOutFasterIn = new PathInterpolator(0, 0, LINEAR_OUT_FASTER_IN_X2, y2);
    float durationSeconds = startGradient * diff / velAbs;
    if (durationSeconds <= maxLengthSeconds) {
      animatorProperties.interpolator = mLinearOutFasterIn;
    } else if (velAbs >= minVelocityPxPerSecond) {

      // Cross fade between linear-out-faster-in and linear interpolator with current
      // velocity.
      durationSeconds = maxLengthSeconds;
      VelocityInterpolator velocityInterpolator =
          new VelocityInterpolator(durationSeconds, velAbs, diff);
      InterpolatorInterpolator superInterpolator =
          new InterpolatorInterpolator(velocityInterpolator, mLinearOutFasterIn, linearOutSlowIn);
      animatorProperties.interpolator = superInterpolator;
    } else {

      // Just use a normal interpolator which doesn't take the velocity into account.
      durationSeconds = maxLengthSeconds;
      animatorProperties.interpolator = Interpolators.FAST_OUT_LINEAR_IN;
    }
    animatorProperties.duration = (long) (durationSeconds * 1000);
    return animatorProperties;
  }

  /**
   * Calculates the y2 control point for a linear-out-faster-in path interpolator depending on the
   * velocity. The faster the velocity, the more "linear" the interpolator gets.
   *
   * @param velocity the velocity of the gesture.
   * @return the y2 control point for a cubic bezier path interpolator
   */
  private float calculateLinearOutFasterInY2(float velocity) {
    float t =
        (velocity - minVelocityPxPerSecond) / (highVelocityPxPerSecond - minVelocityPxPerSecond);
    t = Math.max(0, Math.min(1, t));
    return (1 - t) * LINEAR_OUT_FASTER_IN_Y2_MIN + t * LINEAR_OUT_FASTER_IN_Y2_MAX;
  }

  /** @return the minimum velocity a gesture needs to have to be considered a fling */
  public float getMinVelocityPxPerSecond() {
    return minVelocityPxPerSecond;
  }

  /** An interpolator which interpolates two interpolators with an interpolator. */
  private static final class InterpolatorInterpolator implements Interpolator {

    private final Interpolator interpolator1;
    private final Interpolator interpolator2;
    private final Interpolator crossfader;

    InterpolatorInterpolator(
        Interpolator interpolator1, Interpolator interpolator2, Interpolator crossfader) {
      this.interpolator1 = interpolator1;
      this.interpolator2 = interpolator2;
      this.crossfader = crossfader;
    }

    @Override
    public float getInterpolation(float input) {
      float t = crossfader.getInterpolation(input);
      return (1 - t) * interpolator1.getInterpolation(input)
          + t * interpolator2.getInterpolation(input);
    }
  }

  /** An interpolator which interpolates with a fixed velocity. */
  private static final class VelocityInterpolator implements Interpolator {

    private final float durationSeconds;
    private final float velocity;
    private final float diff;

    private VelocityInterpolator(float durationSeconds, float velocity, float diff) {
      this.durationSeconds = durationSeconds;
      this.velocity = velocity;
      this.diff = diff;
    }

    @Override
    public float getInterpolation(float input) {
      float time = input * durationSeconds;
      return time * velocity / diff;
    }
  }

  private static class AnimatorProperties {

    Interpolator interpolator;
    long duration;
  }
}
