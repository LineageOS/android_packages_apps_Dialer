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

import com.google.android.collect.Lists;

import com.android.services.telephony.common.AudioMode;

import java.util.List;


/**
 * Proxy class for getting and setting the audio mode.
 */
/* package */ class AudioModeProvider {

    private static AudioModeProvider sAudioModeProvider;
    private int mAudioMode = AudioMode.EARPIECE;
    private int mSupportedModes = AudioMode.ALL_MODES;
    private final List<AudioModeListener> mListeners = Lists.newArrayList();

    public static synchronized AudioModeProvider getInstance() {
        if (sAudioModeProvider == null) {
            sAudioModeProvider = new AudioModeProvider();
        }
        return sAudioModeProvider;
    }

    /**
     * Access only through getInstance()
     */
    private AudioModeProvider() {
    }

    public void onAudioModeChange(int newMode) {
        mAudioMode = newMode;

        for (AudioModeListener l : mListeners) {
            l.onAudioMode(mAudioMode);
        }
    }

    public void onSupportedAudioModeChange(int newModeMask) {
        mSupportedModes = newModeMask;

        for (AudioModeListener l : mListeners) {
            l.onSupportedAudioMode(mSupportedModes);
        }
    }

    public void addListener(AudioModeListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(AudioModeListener listener) {
        if (mListeners.contains(listener)) {
            mListeners.remove(listener);
        }
    }

    public int getSupportedModes() {
        return mSupportedModes;
    }

    public int getAudioMode() {
        return mAudioMode;
    }

    /* package */ interface AudioModeListener {
        void onAudioMode(int newMode);
        void onSupportedAudioMode(int modeMask);
    }
}
