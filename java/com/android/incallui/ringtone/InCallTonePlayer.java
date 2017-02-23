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

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.incallui.Log;
import com.android.incallui.async.PausableExecutor;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Class responsible for playing in-call related tones in a background thread. This class only
 * allows one tone to be played at a time.
 */
public class InCallTonePlayer {

  public static final int TONE_CALL_WAITING = 4;

  public static final int VOLUME_RELATIVE_HIGH_PRIORITY = 80;

  @NonNull private final ToneGeneratorFactory mToneGeneratorFactory;
  @NonNull private final PausableExecutor mExecutor;
  private @Nullable CountDownLatch mNumPlayingTones;

  /**
   * Creates a new InCallTonePlayer.
   *
   * @param toneGeneratorFactory the {@link ToneGeneratorFactory} used to create {@link
   *     ToneGenerator}s.
   * @param executor the {@link PausableExecutor} used to play tones in a background thread.
   * @throws NullPointerException if audioModeProvider, toneGeneratorFactory, or executor are {@code
   *     null}.
   */
  public InCallTonePlayer(
      @NonNull ToneGeneratorFactory toneGeneratorFactory, @NonNull PausableExecutor executor) {
    mToneGeneratorFactory = Objects.requireNonNull(toneGeneratorFactory);
    mExecutor = Objects.requireNonNull(executor);
  }

  /** @return {@code true} if a tone is currently playing, {@code false} otherwise. */
  public boolean isPlayingTone() {
    return mNumPlayingTones != null && mNumPlayingTones.getCount() > 0;
  }

  /**
   * Plays the given tone in a background thread.
   *
   * @param tone the tone to play.
   * @throws IllegalStateException if a tone is already playing.
   * @throws IllegalArgumentException if the tone is invalid.
   */
  public void play(int tone) {
    if (isPlayingTone()) {
      throw new IllegalStateException("Tone already playing");
    }
    final ToneGeneratorInfo info = getToneGeneratorInfo(tone);
    mNumPlayingTones = new CountDownLatch(1);
    mExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            playOnBackgroundThread(info);
          }
        });
  }

  private ToneGeneratorInfo getToneGeneratorInfo(int tone) {
    switch (tone) {
      case TONE_CALL_WAITING:
        /*
         * DialerCall waiting tones play until they're stopped either by the user accepting or
         * declining the call so the tone length is set at what's effectively forever. The
         * tone is played at a high priority volume and through STREAM_VOICE_CALL since it's
         * call related and using that stream will route it through bluetooth devices
         * appropriately.
         */
        return new ToneGeneratorInfo(
            ToneGenerator.TONE_SUP_CALL_WAITING,
            VOLUME_RELATIVE_HIGH_PRIORITY,
            Integer.MAX_VALUE,
            AudioManager.STREAM_VOICE_CALL);
      default:
        throw new IllegalArgumentException("Bad tone: " + tone);
    }
  }

  private void playOnBackgroundThread(ToneGeneratorInfo info) {
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

  /** Stops playback of the current tone. */
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

    public ToneGeneratorInfo(int toneGeneratorType, int volume, int toneLengthMillis, int stream) {
      this.tone = toneGeneratorType;
      this.volume = volume;
      this.toneLengthMillis = toneLengthMillis;
      this.stream = stream;
    }

    @Override
    public String toString() {
      return "ToneGeneratorInfo{"
          + "toneLengthMillis="
          + toneLengthMillis
          + ", tone="
          + tone
          + ", volume="
          + volume
          + '}';
    }
  }
}
