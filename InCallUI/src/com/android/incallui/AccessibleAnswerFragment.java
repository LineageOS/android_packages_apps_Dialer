/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.incallui;

import android.os.Bundle;
import android.telecom.VideoProfile;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.android.dialer.R;

/**
 * AnswerFragment to use when touch exploration is enabled in accessibility.
 */
public class AccessibleAnswerFragment extends AnswerFragment {

    private static final String TAG = AccessibleAnswerFragment.class.getSimpleName();
    private static final int SWIPE_THRESHOLD = 100;

    private View mAnswer;
    private View mDecline;
    private View mText;

    private TouchListener mTouchListener;
    private GestureDetector mGestureDetector;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ViewGroup group = (ViewGroup) inflater.inflate(R.layout.accessible_answer_fragment,
                container, false);

        mTouchListener = new TouchListener();
        mGestureDetector = new GestureDetector(getContext(), new SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                    float velocityY) {
                return AccessibleAnswerFragment.this.onFling(e1, e2, velocityX, velocityX);
            }
        });

        mAnswer = group.findViewById(R.id.accessible_answer_fragment_answer);
        mAnswer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Answer Button Clicked");
                onAnswer(VideoProfile.STATE_AUDIO_ONLY, getContext());
            }
        });
        mDecline = group.findViewById(R.id.accessible_answer_fragment_decline);
        mDecline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Decline Button Clicked");
                onDecline(getContext());
            }
        });

        mText = group.findViewById(R.id.accessible_answer_fragment_text);
        mText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Text Button Clicked");
                onText();
            }
        });
        return group;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Intercept all touch events for full screen swiping gesture.
        InCallActivity activity = (InCallActivity) getActivity();
        activity.setDispatchTouchEventListener(mTouchListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        InCallActivity activity = (InCallActivity) getActivity();
        activity.setDispatchTouchEventListener(null);
    }

    private class TouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return mGestureDetector.onTouchEvent(event);
        }
    }

    private boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
            float velocityY) {
        if (hasPendingDialogs()) {
            return false;
        }

        float diffY = e2.getY() - e1.getY();
        float diffX = e2.getX() - e1.getX();
        if (Math.abs(diffX) > Math.abs(diffY)) {
            if (Math.abs(diffX) > SWIPE_THRESHOLD) {
                if (diffX > 0) {
                    onSwipeRight();
                } else {
                    onSwipeLeft();
                }
            }
            return true;
        } else if (Math.abs(diffY) > SWIPE_THRESHOLD) {
            if (diffY > 0) {
                onSwipeDown();
            } else {
                onSwipeUp();
            }
            return true;
        }

        return false;
    }

    private void onSwipeUp() {
        Log.d(TAG, "onSwipeUp");
        onText();
    }

    private void onSwipeDown() {
        Log.d(TAG, "onSwipeDown");
    }

    private void onSwipeLeft() {
        Log.d(TAG, "onSwipeLeft");
        onDecline(getContext());
    }

    private void onSwipeRight() {
        Log.d(TAG, "onSwipeRight");
        onAnswer(VideoProfile.STATE_AUDIO_ONLY, getContext());
    }
}
