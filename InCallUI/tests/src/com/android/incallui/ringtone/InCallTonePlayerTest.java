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
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.incallui.async.PausableExecutor;
import com.android.incallui.async.SingleProdThreadExecutor;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@SmallTest
public class InCallTonePlayerTest extends AndroidTestCase {

    @Mock private ToneGeneratorFactory mToneGeneratorFactory;
    @Mock private ToneGenerator mToneGenerator;
    private InCallTonePlayer mInCallTonePlayer;

    /*
     * InCallTonePlayer milestones:
     * 1) After tone starts playing
     * 2) After tone finishes waiting (could have timed out)
     * 3) After cleaning up state to allow new tone to play
     */
    private PausableExecutor mExecutor;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        Mockito.when(mToneGeneratorFactory.newInCallToneGenerator(Mockito.anyInt(),
                Mockito.anyInt())).thenReturn(mToneGenerator);
        mExecutor = new SingleProdThreadExecutor();
        mInCallTonePlayer = new InCallTonePlayer(mToneGeneratorFactory, mExecutor);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        // Stop any playing so the InCallTonePlayer isn't stuck waiting for the tone to complete
        mInCallTonePlayer.stop();
        // Ack all milestones to ensure that the prod thread doesn't block forever
        mExecutor.ackAllMilestonesForTesting();
    }

    public void testIsPlayingTone_False() {
        assertFalse(mInCallTonePlayer.isPlayingTone());
    }

    public void testIsPlayingTone_True() throws InterruptedException {
        mInCallTonePlayer.play(InCallTonePlayer.TONE_CALL_WAITING);
        mExecutor.awaitMilestoneForTesting();

        assertTrue(mInCallTonePlayer.isPlayingTone());
    }

    public void testPlay_InvalidTone() {
        try {
            mInCallTonePlayer.play(Integer.MIN_VALUE);
            fail();
        } catch (IllegalArgumentException e) {}
    }

    public void testPlay_CurrentlyPlaying() throws InterruptedException {
        mInCallTonePlayer.play(InCallTonePlayer.TONE_CALL_WAITING);
        mExecutor.awaitMilestoneForTesting();
        try {
            mInCallTonePlayer.play(InCallTonePlayer.TONE_CALL_WAITING);
            fail();
        } catch (IllegalStateException e) {}
    }

    public void testPlay_VoiceCallStream() throws InterruptedException {
        mInCallTonePlayer.play(InCallTonePlayer.TONE_CALL_WAITING);
        mExecutor.awaitMilestoneForTesting();
        Mockito.verify(mToneGeneratorFactory).newInCallToneGenerator(AudioManager.STREAM_VOICE_CALL,
                InCallTonePlayer.VOLUME_RELATIVE_HIGH_PRIORITY);
    }

    public void testPlay_Single() throws InterruptedException {
        mInCallTonePlayer.play(InCallTonePlayer.TONE_CALL_WAITING);
        mExecutor.awaitMilestoneForTesting();
        mExecutor.ackMilestoneForTesting();
        mInCallTonePlayer.stop();
        mExecutor.ackMilestoneForTesting();
        mExecutor.awaitMilestoneForTesting();
        mExecutor.ackMilestoneForTesting();

        Mockito.verify(mToneGenerator).startTone(ToneGenerator.TONE_SUP_CALL_WAITING);
    }

    public void testPlay_Consecutive() throws InterruptedException {
        mInCallTonePlayer.play(InCallTonePlayer.TONE_CALL_WAITING);
        mExecutor.awaitMilestoneForTesting();
        mExecutor.ackMilestoneForTesting();
        // Prevent waiting forever
        mInCallTonePlayer.stop();
        mExecutor.ackMilestoneForTesting();
        mExecutor.awaitMilestoneForTesting();
        mExecutor.ackMilestoneForTesting();

        mInCallTonePlayer.play(InCallTonePlayer.TONE_CALL_WAITING);
        mExecutor.awaitMilestoneForTesting();
        mExecutor.ackMilestoneForTesting();
        mInCallTonePlayer.stop();
        mExecutor.ackMilestoneForTesting();
        mExecutor.awaitMilestoneForTesting();
        mExecutor.ackMilestoneForTesting();

        Mockito.verify(mToneGenerator, Mockito.times(2))
                .startTone(ToneGenerator.TONE_SUP_CALL_WAITING);
    }

    public void testStop_NotPlaying() {
        // No crash
        mInCallTonePlayer.stop();
    }

    public void testStop() throws InterruptedException {
        mInCallTonePlayer.play(InCallTonePlayer.TONE_CALL_WAITING);
        mExecutor.awaitMilestoneForTesting();

        mInCallTonePlayer.stop();
        mExecutor.ackMilestoneForTesting();
        mExecutor.awaitMilestoneForTesting();

        assertFalse(mInCallTonePlayer.isPlayingTone());
    }
}
