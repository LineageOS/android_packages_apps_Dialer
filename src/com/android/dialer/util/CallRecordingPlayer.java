/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.dialer.util;

import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.android.dialer.R;
import com.android.services.callrecorder.CallRecorderService;
import com.android.services.callrecorder.common.CallRecording;

import java.io.File;
import java.io.IOException;

/**
 * Simple playback for call recordings
 */
public class CallRecordingPlayer implements MediaPlayer.OnCompletionListener {
    private static final String TAG = "CallRecordingPlayer";

    private MediaPlayer mPlayer = null;
    private boolean mPlaying = false;
    private PlayButton mButton;

    public Button createPlaybackButton(Context context, CallRecording recording) {
        return new PlayButton(context, recording, this);
    }

    // button to toggle playback for a call recording
    private static class PlayButton extends Button implements View.OnClickListener {
        private boolean mPlaying = false;
        private CallRecording mRecording;
        private CallRecordingPlayer mPlayer;

        public PlayButton(Context context, CallRecording recording, CallRecordingPlayer player) {
            super(context);
            mRecording = recording;
            mPlayer = player;
            reset();
            setBackgroundColor(Color.TRANSPARENT);
            setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (!mPlaying) {
                mPlayer.play(mRecording, this);
                if (!mPlayer.isPlaying()) {
                    Toast.makeText(mContext, R.string.call_playback_error_message,
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                mPlayer.stop();
            }

            mPlaying = mPlayer.isPlaying();
            updateState();
        }

        private void updateState() {
            setText(mPlaying ? R.string.stop_call_playback : R.string.start_call_playback);
            setCompoundDrawablesRelativeWithIntrinsicBounds(mPlaying
                    ? R.drawable.ic_playback_stop_dk : R.drawable.ic_playback_dk,
                    0, 0, 0);
        }

        public void reset() {
            mPlaying = false;
            updateState();
        }
    }

    private void play(CallRecording recording, PlayButton button) {
        if (mPlayer != null) {
            // stop and cleanup current session first
            stop();
        }

        mButton = button;

        String filePath = recording.getFile().getAbsolutePath();

        mPlayer = new MediaPlayer();
        mPlayer.setOnCompletionListener(this);
        try {
            mPlayer.setDataSource(filePath);
            mPlayer.prepare();
        } catch (IOException e) {
            Log.w(TAG, "Error opening " + filePath, e);
            return;
        }

        try {
            mPlayer.start();
            mPlaying = true;
        } catch (IllegalStateException e) {
            Log.w(TAG, "Could not start player", e);
        }
    }

    public void stop() {
        if (mPlayer != null) {
            try {
                mPlayer.stop();
            } catch (IllegalStateException e) {
                Log.w(TAG, "Exception stopping player", e);
            }
            mPlayer.release();
            mPlayer = null;
            resetButton();
        }
        mPlaying = false;
    }

    private boolean isPlaying() {
        return mPlaying;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        resetButton();

        mPlayer.release();
        mPlayer = null;
        mPlaying = false;
    }

    private void resetButton() {
        if (mButton != null) {
            mButton.reset();
            mButton = null;
        }
    }
}
