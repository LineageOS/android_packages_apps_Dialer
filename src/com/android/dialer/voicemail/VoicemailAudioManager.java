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

package com.android.dialer.voicemail;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.util.Log;

import java.util.concurrent.RejectedExecutionException;

/**
 * This class manages all audio changes for voicemail playback.
 */
final class VoicemailAudioManager implements OnAudioFocusChangeListener {
    private static final String TAG = VoicemailAudioManager.class.getSimpleName();

    public static final int PLAYBACK_STREAM = AudioManager.STREAM_VOICE_CALL;

    private AudioManager mAudioManager;
    private VoicemailPlaybackPresenter mVoicemailPlaybackPresenter;

    public VoicemailAudioManager(Context context,
            VoicemailPlaybackPresenter voicemailPlaybackPresenter) {
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mVoicemailPlaybackPresenter = voicemailPlaybackPresenter;
    }

    public void requestAudioFocus() {
        int result = mAudioManager.requestAudioFocus(
                this,
                PLAYBACK_STREAM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            throw new RejectedExecutionException("Could not capture audio focus.");
        }
    }

    public void abandonAudioFocus() {
        mAudioManager.abandonAudioFocus(this);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        Log.d(TAG, "onAudioFocusChange: focusChange=" + focusChange);
        mVoicemailPlaybackPresenter.onAudioFocusChange(focusChange == AudioManager.AUDIOFOCUS_GAIN);
    }

    public void turnOnSpeaker(boolean on) {
        if (mAudioManager.isSpeakerphoneOn() != on) {
            Log.i(TAG, "turning speaker phone on: " + on);
            mAudioManager.setSpeakerphoneOn(on);
        }
    }

    public boolean isSpeakerphoneOn() {
        return mAudioManager.isSpeakerphoneOn();
    }
}