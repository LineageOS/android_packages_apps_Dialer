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
 * limitations under the License.
 */

package com.android.incallui.ringtone;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.provider.MediaStore.Audio;
import android.support.annotation.Nullable;

import com.android.contacts.common.testing.NeededForTesting;
import com.android.dialer.compat.CallAudioStateCompat;
import com.android.incallui.AudioModeProvider;
import com.android.incallui.Log;
import com.android.incallui.async.PausableExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Class responsible for playing in-call related tones in a background thread. This class only
 * allows one tone to be played at a time.
 */
@NeededForTesting
public class InCallTonePlayer {

    public static final int TONE_CALL_WAITING = 4;

    public static final int VOLUME_RELATIVE_HIGH_PRIORITY = 80;

    private final AudioModeProvider mAudioModeProvider;
    private final ToneGeneratorFactory mToneGeneratorFactory;
    private final PausableExecutor mExecutor;
    private @Nullable CountDownLatch mNumPlayingTones;

    /**
     * Creates a new InCallTonePlayer.
     *
     * @param audioModeProvider the {@link AudioModeProvider} used to determine through which stream
     * to play tones.
     * @param toneGeneratorFactory the {@link ToneGeneratorFactory} used to create
     * {@link ToneGenerator}s.
     * @param executor the {@link PausableExecutor} used to play tones in a background thread.
     * @throws NullPointerException if audioModeProvider, toneGeneratorFactory, or executor are
     * {@code null}.
     */
    @NeededForTesting
    public InCallTonePlayer(AudioModeProvider audioModeProvider,
            ToneGeneratorFactory toneGeneratorFactory, PausableExecutor executor) {
        mAudioModeProvider = Preconditions.checkNotNull(audioModeProvider);
        mToneGeneratorFactory = Preconditions.checkNotNull(toneGeneratorFactory);
        mExecutor = Preconditions.checkNotNull(executor);
    }

    /**
     * @return {@code true} if a tone is currently playing, {@code false} otherwise
     */
    @NeededForTesting
    public boolean isPlayingTone() {
        return mNumPlayingTones != null && mNumPlayingTones.getCount() > 0;
    }

    /**
     * Plays the given tone in a background thread.
     *
     * @param tone the tone to play.
     * @throws IllegalStateException if a tone is already playing
     * @throws IllegalArgumentException if the tone is invalid
     */
    @NeededForTesting
    public void play(int tone) {
        if (isPlayingTone()) {
            throw new IllegalStateException("Tone already playing");
        }
        final ToneGeneratorInfo info = getToneGeneratorInfo(tone);
        mNumPlayingTones = new CountDownLatch(1);
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                playOnBackgroundThread(info);
            }
        });
    }

    private ToneGeneratorInfo getToneGeneratorInfo(int tone) {
        int stream = getPlaybackStream();
        switch (tone) {
            case TONE_CALL_WAITING:
                return new ToneGeneratorInfo(ToneGenerator.TONE_SUP_CALL_WAITING,
                        VOLUME_RELATIVE_HIGH_PRIORITY,
                        Integer.MAX_VALUE,
                        stream);
            default:
                throw new IllegalArgumentException("Bad tone: " + tone);
        }
    }

    private int getPlaybackStream() {
        if (mAudioModeProvider.getAudioMode() == CallAudioStateCompat.ROUTE_BLUETOOTH) {
            // TODO (maxwelb): b/26932998 play through bluetooth
            // return AudioManager.STREAM_BLUETOOTH_SCO;
        }
        return AudioManager.STREAM_VOICE_CALL;
    }

    private void playOnBackgroundThread(ToneGeneratorInfo info) {
        // TODO (maxwelb): b/26936902 respect Do Not Disturb setting
        ToneGenerator toneGenerator = null;
        try {
            Log.v(this, "Starting tone " + info);
            toneGenerator = mToneGeneratorFactory.newInCallToneGenerator(info.stream, info.volume);
            toneGenerator.startTone(info.tone);
            /*
             * During tests, this will block until the tests call mExecutor.ackMilestone. This call
             * allows for synchronization to the point where the tone has started playing.
             */
            mExecutor.milestone();
            if (mNumPlayingTones != null) {
                mNumPlayingTones.await(info.toneLengthMillis, TimeUnit.MILLISECONDS);
                // Allows for synchronization to the point where the tone has completed playing.
                mExecutor.milestone();
            }
        } catch (InterruptedException e) {
            Log.w(this, "Interrupted while playing in-call tone.");
        } finally {
            if (toneGenerator != null) {
                toneGenerator.release();
            }
            if (mNumPlayingTones != null) {
                mNumPlayingTones.countDown();
            }
            // Allows for synchronization to the point where this background thread has cleaned up.
            mExecutor.milestone();
        }
    }

    /**
     * Stops playback of the current tone.
     */
    @NeededForTesting
    public void stop() {
        if (mNumPlayingTones != null) {
            mNumPlayingTones.countDown();
        }
    }

    private static class ToneGeneratorInfo {
        public final int tone;
        public final int volume;
        public final int toneLengthMillis;
        public final int stream;

        public ToneGeneratorInfo(int toneGeneratorType, int volume, int toneLengthMillis,
                int stream) {
            this.tone = toneGeneratorType;
            this.volume = volume;
            this.toneLengthMillis = toneLengthMillis;
            this.stream = stream;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("tone", tone)
                    .add("volume", volume)
                    .add("toneLengthMillis", toneLengthMillis).toString();
        }
    }
}
