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

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.android.incallui.widget.multiwaveview.GlowPadView;

/**
 *
 */
public class GlowPadWrapper extends GlowPadView implements GlowPadView.OnTriggerListener {

    private static final String TAG = GlowPadWrapper.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    // Parameters for the GlowPadView "ping" animation; see triggerPing().
    private static final int PING_MESSAGE_WHAT = 101;
    private static final boolean ENABLE_PING_AUTO_REPEAT = true;
    private static final long PING_REPEAT_DELAY_MS = 1200;

    private final Handler mPingHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case PING_MESSAGE_WHAT:
                    triggerPing();
                    break;
            }
        }
    };

    private AnswerListener mAnswerListener;
    private boolean mPingEnabled = true;

    public GlowPadWrapper(Context context) {
        super(context);
    }

    public GlowPadWrapper(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        logD("onFinishInflate()");
        super.onFinishInflate();
        setOnTriggerListener(this);
        startPing();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        logD("Visibility changed " + visibility);
        super.onWindowVisibilityChanged(visibility);
        switch (visibility) {
            case View.VISIBLE:
                startPing();
                break;
            case View.INVISIBLE:
            case View.GONE:
                stopPing();
                break;
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        logD("onSaveInstanceState()");
        // TODO: evaluate this versus stopping during fragment onPause/onResume
        stopPing();
        return super.onSaveInstanceState();
    }

    public void startPing() {
        mPingEnabled = true;
        triggerPing();
    }

    public void stopPing() {
        mPingEnabled = false;
    }

    private void triggerPing() {
        if (mPingEnabled) {
            ping();

            if (ENABLE_PING_AUTO_REPEAT) {
                mPingHandler.sendEmptyMessageDelayed(PING_MESSAGE_WHAT, PING_REPEAT_DELAY_MS);
            }
        }
    }

    @Override
    public void onGrabbed(View v, int handle) {
        logD("onGrabbed()");
        stopPing();
    }

    @Override
    public void onReleased(View v, int handle) {
        logD("onReleased()");
        startPing();
    }

    @Override
    public void onTrigger(View v, int target) {
        logD("onTrigger()");
        final int resId = getResourceIdForTarget(target);
        switch (resId) {
            case R.drawable.ic_lockscreen_answer:
                mAnswerListener.onAnswer();
                break;
            case R.drawable.ic_lockscreen_decline:
                mAnswerListener.onDecline();
                break;
            case R.drawable.ic_lockscreen_text:
                mAnswerListener.onText();
                break;
            default:
                // Code should never reach here.
                Log.e(TAG, "Trigger detected on unhandled resource. Skipping.");
        }
    }

    @Override
    public void onGrabbedStateChange(View v, int handle) {

    }

    @Override
    public void onFinishFinalAnimation() {

    }

    public void setAnswerListener(AnswerListener listener) {
        mAnswerListener = listener;
    }

    public interface AnswerListener {
        void onAnswer();
        void onDecline();
        void onText();
    }

    private void logD(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }
}
