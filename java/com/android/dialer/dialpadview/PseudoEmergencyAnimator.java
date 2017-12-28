/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.dialer.dialpadview;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.os.Handler;
import android.os.Vibrator;
import android.view.View;

/** Animates the dial button on "emergency" phone numbers. */
public class PseudoEmergencyAnimator {

  static final String PSEUDO_EMERGENCY_NUMBER = "01189998819991197253";
  private static final int VIBRATE_LENGTH_MILLIS = 200;
  private static final int ITERATION_LENGTH_MILLIS = 1000;
  private static final int ANIMATION_ITERATION_COUNT = 6;
  private ViewProvider viewProvider;
  private ValueAnimator pseudoEmergencyColorAnimator;

  PseudoEmergencyAnimator(ViewProvider viewProvider) {
    this.viewProvider = viewProvider;
  }

  public void destroy() {
    end();
    viewProvider = null;
  }

  public void start() {
    if (pseudoEmergencyColorAnimator == null) {
      Integer colorFrom = Color.BLUE;
      Integer colorTo = Color.RED;
      pseudoEmergencyColorAnimator =
          ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);

      pseudoEmergencyColorAnimator.addUpdateListener(
          animator -> {
            try {
              int color = (int) animator.getAnimatedValue();
              ColorFilter colorFilter = new LightingColorFilter(Color.BLACK, color);

              if (viewProvider.getFab() != null) {
                viewProvider.getFab().getBackground().setColorFilter(colorFilter);
              }
            } catch (Exception e) {
              animator.cancel();
            }
          });

      pseudoEmergencyColorAnimator.addListener(
          new AnimatorListener() {
            @Override
            public void onAnimationCancel(Animator animation) {}

            @Override
            public void onAnimationRepeat(Animator animation) {
              try {
                vibrate(VIBRATE_LENGTH_MILLIS);
              } catch (Exception e) {
                animation.cancel();
              }
            }

            @Override
            public void onAnimationStart(Animator animation) {}

            @Override
            public void onAnimationEnd(Animator animation) {
              try {
                if (viewProvider.getFab() != null) {
                  viewProvider.getFab().getBackground().clearColorFilter();
                }

                new Handler()
                    .postDelayed(
                        () -> {
                          try {
                            vibrate(VIBRATE_LENGTH_MILLIS);
                          } catch (Exception e) {
                            // ignored
                          }
                        },
                        ITERATION_LENGTH_MILLIS);
              } catch (Exception e) {
                animation.cancel();
              }
            }
          });

      pseudoEmergencyColorAnimator.setDuration(VIBRATE_LENGTH_MILLIS);
      pseudoEmergencyColorAnimator.setRepeatMode(ValueAnimator.REVERSE);
      pseudoEmergencyColorAnimator.setRepeatCount(ANIMATION_ITERATION_COUNT);
    }
    if (!pseudoEmergencyColorAnimator.isStarted()) {
      pseudoEmergencyColorAnimator.start();
    }
  }

  public void end() {
    if (pseudoEmergencyColorAnimator != null && pseudoEmergencyColorAnimator.isStarted()) {
      pseudoEmergencyColorAnimator.end();
    }
  }

  private void vibrate(long milliseconds) {
    Context context = viewProvider.getContext();
    if (context != null) {
      Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
      if (vibrator != null) {
        vibrator.vibrate(milliseconds);
      }
    }
  }

  interface ViewProvider {

    View getFab();

    Context getContext();
  }
}
