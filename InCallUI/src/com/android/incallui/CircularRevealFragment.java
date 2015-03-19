/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.graphics.Outline;
import android.graphics.Point;
import android.os.Bundle;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;

import com.android.contacts.common.util.MaterialColorMapUtils.MaterialPalette;

public class CircularRevealFragment extends Fragment {
    static final String TAG = "CircularRevealFragment";

    private Point mTouchPoint;
    private OnCircularRevealCompleteListener mListener;
    private boolean mAnimationStarted;

    interface OnCircularRevealCompleteListener {
        public void onCircularRevealComplete(FragmentManager fm);
    }

    public static void startCircularReveal(FragmentManager fm, Point touchPoint,
            OnCircularRevealCompleteListener listener) {
        if (fm.findFragmentByTag(TAG) == null) {
            fm.beginTransaction().add(R.id.main,
                    new CircularRevealFragment(touchPoint, listener), TAG)
                            .commitAllowingStateLoss();
        } else {
            Log.w(TAG, "An instance of CircularRevealFragment already exists");
        }
    }

    public static void endCircularReveal(FragmentManager fm) {
        final Fragment fragment = fm.findFragmentByTag(TAG);
        if (fragment != null) {
            fm.beginTransaction().remove(fragment).commitAllowingStateLoss();
        }
    }

    /**
     * Empty constructor used only by the {@link FragmentManager}.
     */
    public CircularRevealFragment() {}

    public CircularRevealFragment(Point touchPoint, OnCircularRevealCompleteListener listener) {
        mTouchPoint = touchPoint;
        mListener = listener;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mAnimationStarted) {
            // Only run the animation once for each instance of the fragment
            startOutgoingAnimation(InCallPresenter.getInstance().getThemeColors());
        }
        mAnimationStarted = true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.outgoing_call_animation, container, false);
    }

    public void startOutgoingAnimation(MaterialPalette palette) {
        final Activity activity = getActivity();
        if (activity == null) {
            Log.w(this, "Asked to do outgoing call animation when not attached");
            return;
        }

        final View view  = activity.getWindow().getDecorView();

        // The circle starts from an initial size of 0 so clip it such that it is invisible.
        // Otherwise the first frame is drawn with a fully opaque screen which causes jank. When
        // the animation later starts, this clip will be clobbered by the circular reveal clip.
        // See ViewAnimationUtils.createCircularReveal.
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                // Using (0, 0, 0, 0) will not work since the outline will simply be treated as
                // an empty outline.
                outline.setOval(-1, -1, 0, 0);
            }
        });
        view.setClipToOutline(true);

        if (palette != null) {
            view.findViewById(R.id.outgoing_call_animation_circle).setBackgroundColor(
                    palette.mPrimaryColor);
            activity.getWindow().setStatusBarColor(palette.mSecondaryColor);
        }

        view.getViewTreeObserver().addOnPreDrawListener(new OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                final ViewTreeObserver vto = view.getViewTreeObserver();
                if (vto.isAlive()) {
                    vto.removeOnPreDrawListener(this);
                }
                final Animator animator = getRevealAnimator(mTouchPoint);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setClipToOutline(false);
                        if (mListener != null) {
                            mListener.onCircularRevealComplete(getFragmentManager());
                        }
                    }
                });
                animator.start();
                return false;
            }
        });
    }

    private Animator getRevealAnimator(Point touchPoint) {
        final Activity activity = getActivity();
        final View view  = activity.getWindow().getDecorView();
        final Display display = activity.getWindowManager().getDefaultDisplay();
        final Point size = new Point();
        display.getSize(size);

        int startX = size.x / 2;
        int startY = size.y / 2;
        if (touchPoint != null) {
            startX = touchPoint.x;
            startY = touchPoint.y;
        }

        final Animator valueAnimator = ViewAnimationUtils.createCircularReveal(view,
                startX, startY, 0, Math.max(size.x, size.y));
        valueAnimator.setDuration(getResources().getInteger(R.integer.reveal_animation_duration));
        return valueAnimator;
    }
}
