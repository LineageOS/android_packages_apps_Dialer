/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.incallui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.ViewPropertyAnimator;
import android.widget.ImageView;

/**
 * Utilities for Animation.
 */
public class InCallAnimationUtils {
    private static final String LOG_TAG = InCallAnimationUtils.class.getSimpleName();
    /**
     * Turn on when you're interested in fading animation. Intentionally untied from other debug
     * settings.
     */
    private static final boolean FADE_DBG = false;

    /**
     * Duration for animations in msec, which can be used with
     * {@link ViewPropertyAnimator#setDuration(long)} for example.
     */
    public static final int ANIMATION_DURATION = 250;

    private InCallAnimationUtils() {
    }

    /**
     * Drawable achieving cross-fade, just like TransitionDrawable. We can have
     * call-backs via animator object (see also {@link CrossFadeDrawable#getAnimator()}).
     */
    private static class CrossFadeDrawable extends LayerDrawable {
        private final ObjectAnimator mAnimator;

        public CrossFadeDrawable(Drawable[] layers) {
            super(layers);
            mAnimator = ObjectAnimator.ofInt(this, "crossFadeAlpha", 0xff, 0);
        }

        private int mCrossFadeAlpha;

        /**
         * This will be used from ObjectAnimator.
         * Note: this method is protected by proguard.flags so that it won't be removed
         * automatically.
         */
        @SuppressWarnings("unused")
        public void setCrossFadeAlpha(int alpha) {
            mCrossFadeAlpha = alpha;
            invalidateSelf();
        }

        public ObjectAnimator getAnimator() {
            return mAnimator;
        }

        @Override
        public void draw(Canvas canvas) {
            Drawable first = getDrawable(0);
            Drawable second = getDrawable(1);

            if (mCrossFadeAlpha > 0) {
                first.setAlpha(mCrossFadeAlpha);
                first.draw(canvas);
                first.setAlpha(255);
            }

            if (mCrossFadeAlpha < 0xff) {
                second.setAlpha(0xff - mCrossFadeAlpha);
                second.draw(canvas);
                second.setAlpha(0xff);
            }
        }
    }

    private static CrossFadeDrawable newCrossFadeDrawable(Drawable first, Drawable second) {
        Drawable[] layers = new Drawable[2];
        layers[0] = first;
        layers[1] = second;
        return new CrossFadeDrawable(layers);
    }

    /**
     * Starts cross-fade animation using TransitionDrawable. Nothing will happen if "from" and "to"
     * are the same.
     */
    public static void startCrossFade(
            final ImageView imageView, final Drawable from, final Drawable to) {
        // We skip the cross-fade when those two Drawables are equal, or they are BitmapDrawables
        // pointing to the same Bitmap.
        final boolean drawableIsEqual = (from != null && to != null && from.equals(to));
        final boolean hasFromImage = ((from instanceof BitmapDrawable) &&
                ((BitmapDrawable) from).getBitmap() != null);
        final boolean hasToImage = ((to instanceof BitmapDrawable) &&
                ((BitmapDrawable) to).getBitmap() != null);
        final boolean areSameImage = drawableIsEqual || (hasFromImage && hasToImage &&
                ((BitmapDrawable) from).getBitmap().equals(((BitmapDrawable) to).getBitmap()));

        if (!areSameImage) {
            if (FADE_DBG) {
                log("Start cross-fade animation for " + imageView
                        + "(" + Integer.toHexString(from.hashCode()) + " -> "
                        + Integer.toHexString(to.hashCode()) + ")");
            }

            CrossFadeDrawable crossFadeDrawable = newCrossFadeDrawable(from, to);
            ObjectAnimator animator = crossFadeDrawable.getAnimator();
            imageView.setImageDrawable(crossFadeDrawable);
            animator.setDuration(ANIMATION_DURATION);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (FADE_DBG) {
                        log("cross-fade animation start ("
                                + Integer.toHexString(from.hashCode()) + " -> "
                                + Integer.toHexString(to.hashCode()) + ")");
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (FADE_DBG) {
                        log("cross-fade animation ended ("
                                + Integer.toHexString(from.hashCode()) + " -> "
                                + Integer.toHexString(to.hashCode()) + ")");
                    }
                    animation.removeAllListeners();
                    // Workaround for issue 6300562; this will force the drawable to the
                    // resultant one regardless of animation glitch.
                    imageView.setImageDrawable(to);
                }
            });
            animator.start();

            /* We could use TransitionDrawable here, but it may cause some weird animation in
             * some corner cases. See issue 6300562
             * TODO: decide which to be used in the long run. TransitionDrawable is old but system
             * one. Ours uses new animation framework and thus have callback (great for testing),
             * while no framework support for the exact class.

            Drawable[] layers = new Drawable[2];
            layers[0] = from;
            layers[1] = to;
            TransitionDrawable transitionDrawable = new TransitionDrawable(layers);
            imageView.setImageDrawable(transitionDrawable);
            transitionDrawable.startTransition(ANIMATION_DURATION); */
            imageView.setTag(to);
        } else if (!hasFromImage && hasToImage) {
            imageView.setImageDrawable(to);
            imageView.setTag(to);
        } else {
            if (FADE_DBG) {
                log("*Not* start cross-fade. " + imageView);
            }
        }
    }

    // Debugging / testing code

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}