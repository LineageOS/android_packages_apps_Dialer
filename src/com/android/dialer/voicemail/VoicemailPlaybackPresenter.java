/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.app.Activity;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.VoicemailContract;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;

import com.android.dialer.R;
import com.android.dialer.util.AsyncTaskExecutor;
import com.android.dialer.util.AsyncTaskExecutors;

import com.android.common.io.MoreCloseables;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Contains the controlling logic for a voicemail playback UI.
 * <p>
 * This controls a single {@link com.android.dialer.voicemail.VoicemailPlaybackLayout}. A single
 * instance can be reused for different such layouts, using {@link #setVoicemailPlaybackView}.
 * <p>
 * This class is not thread safe. The thread policy for this class is thread-confinement, all calls
 * into this class from outside must be done from the main UI thread.
 */
@NotThreadSafe
@VisibleForTesting
public class VoicemailPlaybackPresenter
        implements OnAudioFocusChangeListener, MediaPlayer.OnPreparedListener,
                MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    private static final String TAG = VoicemailPlaybackPresenter.class.getSimpleName();

    /** Contract describing the behaviour we need from the ui we are controlling. */
    public interface PlaybackView {
        int getDesiredClipPosition();
        void disableUiElements();
        void enableUiElements();
        void onPlaybackError(Exception e);
        void onPlaybackStarted(MediaPlayer mediaPlayer, ScheduledExecutorService executorService);
        void onPlaybackStopped();
        void onSpeakerphoneOn(boolean on);
        void setClipPosition(int clipPositionInMillis, int clipLengthInMillis);
        void setFetchContentTimeout();
        void setIsBuffering();
        void setIsFetchingContent();
        void setPresenter(VoicemailPlaybackPresenter presenter);
    }

    /** The enumeration of {@link AsyncTask} objects we use in this class. */
    public enum Tasks {
        CHECK_FOR_CONTENT,
        CHECK_CONTENT_AFTER_CHANGE,
    }

    private static final String[] HAS_CONTENT_PROJECTION = new String[] {
        VoicemailContract.Voicemails.HAS_CONTENT,
    };

    private static final int PLAYBACK_STREAM = AudioManager.STREAM_VOICE_CALL;
    private static final int NUMBER_OF_THREADS_IN_POOL = 2;
    // Time to wait for content to be fetched before timing out.
    private static final long FETCH_CONTENT_TIMEOUT_MS = 20000;

    private static final String VOICEMAIL_URI_KEY =
            VoicemailPlaybackPresenter.class.getName() + ".VOICEMAIL_URI";
    private static final String IS_PREPARED_KEY =
            VoicemailPlaybackPresenter.class.getName() + ".IS_PREPARED";
    // If present in the saved instance bundle, we should not resume playback on create.
    private static final String IS_PLAYING_STATE_KEY =
            VoicemailPlaybackPresenter.class.getName() + ".IS_PLAYING_STATE_KEY";
    // If present in the saved instance bundle, indicates where to set the playback slider.
    private static final String CLIP_POSITION_KEY =
            VoicemailPlaybackPresenter.class.getName() + ".CLIP_POSITION_KEY";

    /**
     * The most recently cached duration. We cache this since we don't want to keep requesting it
     * from the player, as this can easily lead to throwing {@link IllegalStateException} (any time
     * the player is released, it's illegal to ask for the duration).
     */
    private final AtomicInteger mDuration = new AtomicInteger(0);

    private Context mContext;
    private PlaybackView mView;
    private static MediaPlayer mMediaPlayer;

    private Uri mVoicemailUri;
    private int mPosition;
    private boolean mIsPrepared;
    private boolean mIsPlaying;

    private boolean mShouldResumePlaybackAfterSeeking;

    // Used to run async tasks that need to interact with the UI.
    private final AsyncTaskExecutor mAsyncTaskExecutor;
    private static ScheduledExecutorService mScheduledExecutorService;
    /**
     * Used to handle the result of a successful or time-out fetch result.
     * <p>
     * This variable is thread-contained, accessed only on the ui thread.
     */
    private FetchResultHandler mFetchResultHandler;
    private PowerManager.WakeLock mProximityWakeLock;
    private AudioManager mAudioManager;

    public VoicemailPlaybackPresenter(Activity activity, Bundle savedInstanceState) {
        mContext = activity;
        mAsyncTaskExecutor = AsyncTaskExecutors.createAsyncTaskExecutor();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        if (savedInstanceState != null) {
            // Restores playback state when activity is recreated, such as after rotation.
            mVoicemailUri = (Uri) savedInstanceState.getParcelable(VOICEMAIL_URI_KEY);
            mIsPrepared = savedInstanceState.getBoolean(IS_PREPARED_KEY);
            mPosition = savedInstanceState.getInt(CLIP_POSITION_KEY, 0);
            mIsPlaying = savedInstanceState.getBoolean(IS_PLAYING_STATE_KEY, false);
        }

        PowerManager powerManager =
                (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            mProximityWakeLock = powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG);
        }

        // mMediaPlayer is static to enable seamless playback during rotation. If we do not create
        // a new MediaPlayer, we still need to update listeners to the current Presenter instance.
        if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer();
            mIsPrepared = false;
        }
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setOnErrorListener(this);
        mMediaPlayer.setOnCompletionListener(this);

        activity.setVolumeControlStream(PLAYBACK_STREAM);
    }

    /**
     * Specify the view which this presenter controls and the voicemail for playback.
     */
    public void setPlaybackView(
            PlaybackView view, Uri voicemailUri, boolean startPlayingImmediately) {
        mView = view;
        mView.setPresenter(this);

        mView.onSpeakerphoneOn(isSpeakerphoneOn());

        if (mVoicemailUri != null && mVoicemailUri.equals(voicemailUri)) {
            // Handles rotation case where playback view is set for the same voicemail.
            if (mIsPrepared) {
                onPrepared(mMediaPlayer);
            } else {
                checkForContent();
            }
        } else {
            mVoicemailUri = voicemailUri;
            mPosition = 0;
            mIsPlaying = startPlayingImmediately;
            checkForContent();
        }
    }

    public void onPause(boolean isFinishing) {
        // Do not pause for orientation changes.
        if (mMediaPlayer.isPlaying() && isFinishing) {
            pausePlayback();
        }

        disableProximitySensor(false /* waitForFarState */);
    }

    public void onDestroy(boolean isFinishing) {
        // Do not release for orientation changes.
        if (mIsPrepared && isFinishing) {
            mMediaPlayer.release();
            mIsPrepared = false;
        }

        if (mScheduledExecutorService != null) {
            mScheduledExecutorService.shutdown();
            mScheduledExecutorService = null;
        }

        if (mFetchResultHandler != null) {
            mFetchResultHandler.destroy();
            mFetchResultHandler = null;
        }

        disableProximitySensor(false /* waitForFarState */);
    }

    public void onSaveInstanceState(Bundle outState) {
        if (mView != null) {
            outState.putParcelable(VOICEMAIL_URI_KEY, mVoicemailUri);
            outState.putBoolean(IS_PREPARED_KEY, mIsPrepared);
            outState.putInt(CLIP_POSITION_KEY, mView.getDesiredClipPosition());
            outState.putBoolean(IS_PLAYING_STATE_KEY, mIsPlaying);
        }
    }

    /**
     * Checks to see if we have content available for this voicemail.
     * <p>
     * This method will be called once, after the fragment has been created, before we know if the
     * voicemail we've been asked to play has any content available.
     * <p>
     * Notify the user that we are fetching the content, then check to see if the content field in
     * the DB is set. If set, we proceed to {@link #prepareToPlayContent()} method. If not set, make
     * a request to fetch the content asynchronously via {@link #requestContent()}.
     */
    private void checkForContent() {
        mView.setIsFetchingContent();
        mAsyncTaskExecutor.submit(Tasks.CHECK_FOR_CONTENT, new AsyncTask<Void, Void, Boolean>() {
            @Override
            public Boolean doInBackground(Void... params) {
                return queryHasContent(mVoicemailUri);
            }

            @Override
            public void onPostExecute(Boolean hasContent) {
                if (hasContent) {
                    prepareToPlayContent();
                } else {
                    requestContent();
                }
            }
        });
    }

    private boolean queryHasContent(Uri voicemailUri) {
        ContentResolver contentResolver = mContext.getContentResolver();
        Cursor cursor = contentResolver.query(
                voicemailUri, HAS_CONTENT_PROJECTION, null, null, null);
        try {
            if (cursor != null && cursor.moveToNext()) {
                return cursor.getInt(cursor.getColumnIndexOrThrow(
                        VoicemailContract.Voicemails.HAS_CONTENT)) == 1;
            }
        } finally {
            MoreCloseables.closeQuietly(cursor);
        }
        return false;
    }

    /**
     * Makes a broadcast request to ask that a voicemail source fetch this content.
     * <p>
     * This method <b>must be called on the ui thread</b>.
     * <p>
     * This method will be called when we realise that we don't have content for this voicemail. It
     * will trigger a broadcast to request that the content be downloaded. It will add a listener to
     * the content resolver so that it will be notified when the has_content field changes. It will
     * also set a timer. If the has_content field changes to true within the allowed time, we will
     * proceed to {@link #prepareToPlayContent()}. If the has_content field does not
     * become true within the allowed time, we will update the ui to reflect the fact that content
     * was not available.
     */
    private void requestContent() {
        Preconditions.checkState(mFetchResultHandler == null, "mFetchResultHandler should be null");

        Handler handler = new Handler();
        mFetchResultHandler = new FetchResultHandler(handler);
        mContext.getContentResolver().registerContentObserver(
                mVoicemailUri, false, mFetchResultHandler);
        handler.postDelayed(mFetchResultHandler.getTimeoutRunnable(), FETCH_CONTENT_TIMEOUT_MS);

        // Send voicemail fetch request.
        Intent intent = new Intent(VoicemailContract.ACTION_FETCH_VOICEMAIL, mVoicemailUri);
        mContext.sendBroadcast(intent);
    }

    @ThreadSafe
    private class FetchResultHandler extends ContentObserver implements Runnable {
        private AtomicBoolean mResultStillPending = new AtomicBoolean(true);
        private final Handler mHandler;

        public FetchResultHandler(Handler handler) {
            super(handler);
            mHandler = handler;
        }

        public Runnable getTimeoutRunnable() {
            return this;
        }

        @Override
        public void run() {
            if (mResultStillPending.getAndSet(false)) {
                mContext.getContentResolver().unregisterContentObserver(FetchResultHandler.this);
                mView.setFetchContentTimeout();
            }
        }

        public void destroy() {
            if (mResultStillPending.getAndSet(false)) {
                mContext.getContentResolver().unregisterContentObserver(FetchResultHandler.this);
                mHandler.removeCallbacks(this);
            }
        }

        @Override
        public void onChange(boolean selfChange) {
            mAsyncTaskExecutor.submit(Tasks.CHECK_CONTENT_AFTER_CHANGE,
                    new AsyncTask<Void, Void, Boolean>() {
                @Override
                public Boolean doInBackground(Void... params) {
                    return queryHasContent(mVoicemailUri);
                }

                @Override
                public void onPostExecute(Boolean hasContent) {
                    if (hasContent) {
                        if (mResultStillPending.getAndSet(false)) {
                            mContext.getContentResolver().unregisterContentObserver(
                                    FetchResultHandler.this);
                            prepareToPlayContent();
                        }
                    }
                }
            });
        }
    }

    /**
     * Prepares the voicemail content for playback.
     * <p>
     * This method will be called once we know that our voicemail has content (according to the
     * content provider). this method asynchronously tries to prepare the data source through the
     * media player. If preparation is successful, the media player will {@link #onPrepared()},
     * and it will call {@link #onError()} otherwise.
     */
    private void prepareToPlayContent() {
        mIsPrepared = false;
        mView.setIsBuffering();

        try {
            mMediaPlayer.reset();
            mMediaPlayer.setDataSource(mContext, mVoicemailUri);
            mMediaPlayer.setAudioStreamType(PLAYBACK_STREAM);
            mMediaPlayer.prepareAsync();
        } catch (IOException e) {
            handleError(e);
        }
    }

    /**
     * Once the media player is prepared, enables the UI and adopts the appropriate playback state.
     */
    @Override
    public void onPrepared(MediaPlayer mp) {
        mIsPrepared = true;
        mDuration.set(mMediaPlayer.getDuration());

        mView.enableUiElements();
        mView.setClipPosition(mPosition, mDuration.get());

        if (mIsPlaying) {
            resumePlayback();
        } else {
            pausePlayback();
        }
    }

    /**
     * Invoked if preparing the media player fails, for example, if file is missing or the voicemail
     * is an unknown file format that can't be played.
     */
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        handleError(new IllegalStateException("MediaPlayer error listener invoked"));
        return true;
    }

    private void handleError(Exception e) {
        if (mIsPrepared) {
            mMediaPlayer.release();
            mIsPrepared = false;
        }

        mView.onPlaybackError(e);

        mPosition = 0;
        mIsPlaying = false;
    }

    /**
     * After done playing the voicemail clip, reset the clip position to the start.
     */
    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        pausePlayback();

        // Reset the seekbar position to the beginning.
        mPosition = 0;
        mView.setClipPosition(0, mDuration.get());
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        boolean lostFocus = focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS;
        if (mMediaPlayer.isPlaying() && lostFocus) {
            pausePlayback();
        } else if (!mMediaPlayer.isPlaying() && focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            resumePlayback();
        }
    }

    /**
     * Resumes voicemail playback at the clip position stored by the presenter. Null-op if already
     * playing.
     */
    public void resumePlayback() {
        mIsPlaying = true;

        if (!mMediaPlayer.isPlaying()) {
            // Clamp the start position between 0 and the duration.
            mPosition = Math.max(0, Math.min(mPosition, mDuration.get()));
            mMediaPlayer.seekTo(mPosition);

            try {
                // Grab audio focus here
                int result = mAudioManager.requestAudioFocus(
                        VoicemailPlaybackPresenter.this,
                        PLAYBACK_STREAM,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

                if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    throw new RejectedExecutionException("Could not capture audio focus.");
                }

                // Can throw RejectedExecutionException
                mMediaPlayer.start();
            } catch (RejectedExecutionException e) {
                handleError(e);
            }
        }

        enableProximitySensor();
        mView.onPlaybackStarted(mMediaPlayer, getScheduledExecutorServiceInstance());
    }

    /**
     * Pauses voicemail playback at the current position. Null-op if already paused.
     */
    public void pausePlayback() {
        mPosition = mMediaPlayer.getCurrentPosition();
        mIsPlaying = false;

        if (mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
        }

        mAudioManager.abandonAudioFocus(this);
        mView.onPlaybackStopped();

        // Always disable the proximity sensor on stop.
        disableProximitySensor(true /* waitForFarState */);
    }

    /**
     * Pauses playback when the user starts seeking the position, and notes whether the voicemail is
     * playing to know whether to resume playback once the user selects a new position.
     */
    public void pausePlaybackForSeeking() {
        mShouldResumePlaybackAfterSeeking = mMediaPlayer.isPlaying();
        pausePlayback();
    }

    public void resumePlaybackAfterSeeking(int desiredPosition) {
        mPosition = desiredPosition;
        if (mShouldResumePlaybackAfterSeeking) {
            mShouldResumePlaybackAfterSeeking = false;
            resumePlayback();
        }
    }

    private void enableProximitySensor() {
        if (mProximityWakeLock == null || isSpeakerphoneOn() || !mMediaPlayer.isPlaying()) {
            return;
        }

        if (!mProximityWakeLock.isHeld()) {
            Log.i(TAG, "Acquiring proximity wake lock");
            mProximityWakeLock.acquire();
        } else {
            Log.i(TAG, "Proximity wake lock already acquired");
        }
    }

    private void disableProximitySensor(boolean waitForFarState) {
        if (mProximityWakeLock == null) {
            return;
        }
        if (mProximityWakeLock.isHeld()) {
            Log.i(TAG, "Releasing proximity wake lock");
            int flags = waitForFarState ? PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY : 0;
            mProximityWakeLock.release(flags);
        } else {
            Log.i(TAG, "Proximity wake lock already released");
        }
    }

    public void setSpeakerphoneOn(boolean on) {
        mAudioManager.setSpeakerphoneOn(on);
        if (on) {
            disableProximitySensor(false /* waitForFarState */);
        } else {
            enableProximitySensor();
        }
    }

    public boolean isSpeakerphoneOn() {
        return mAudioManager.isSpeakerphoneOn();
    }

    public Uri getVoicemailUri() {
        return mVoicemailUri;
    }

    private static synchronized ScheduledExecutorService getScheduledExecutorServiceInstance() {
        if (mScheduledExecutorService == null) {
            mScheduledExecutorService = Executors.newScheduledThreadPool(NUMBER_OF_THREADS_IN_POOL);
        }
        return mScheduledExecutorService;
    }

}
