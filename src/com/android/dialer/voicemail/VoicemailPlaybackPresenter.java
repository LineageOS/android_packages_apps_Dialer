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

import android.content.Context;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.view.View;
import android.widget.SeekBar;

import com.android.dialer.R;
import com.android.dialer.util.AsyncTaskExecutor;
import com.android.ex.variablespeed.MediaPlayerProxy;
import com.android.ex.variablespeed.SingleThreadedMediaPlayerProxy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Contains the controlling logic for a voicemail playback ui.
 * <p>
 * Specifically right now this class is used to control the
 * {@link com.android.dialer.voicemail.VoicemailPlaybackFragment}.
 * <p>
 * This class is not thread safe. The thread policy for this class is
 * thread-confinement, all calls into this class from outside must be done from
 * the main ui thread.
 */
@NotThreadSafe
@VisibleForTesting
public class VoicemailPlaybackPresenter implements OnAudioFocusChangeListener {
    /** The stream used to playback voicemail. */
    private static final int PLAYBACK_STREAM = AudioManager.STREAM_VOICE_CALL;

    /** Contract describing the behaviour we need from the ui we are controlling. */
    public interface PlaybackView {
        Context getDataSourceContext();
        void runOnUiThread(Runnable runnable);
        void setStartStopListener(View.OnClickListener listener);
        void setPositionSeekListener(SeekBar.OnSeekBarChangeListener listener);
        void setSpeakerphoneListener(View.OnClickListener listener);
        void setIsBuffering();
        void setClipPosition(int clipPositionInMillis, int clipLengthInMillis);
        int getDesiredClipPosition();
        void playbackStarted();
        void playbackStopped();
        void playbackError(Exception e);
        boolean isSpeakerPhoneOn();
        void setSpeakerPhoneOn(boolean on);
        void finish();
        void setIsFetchingContent();
        void disableUiElements();
        void enableUiElements();
        void sendFetchVoicemailRequest(Uri voicemailUri);
        boolean queryHasContent(Uri voicemailUri);
        void setFetchContentTimeout();
        void registerContentObserver(Uri uri, ContentObserver observer);
        void unregisterContentObserver(ContentObserver observer);
        void enableProximitySensor();
        void disableProximitySensor();
        void setVolumeControlStream(int streamType);
    }

    /** The enumeration of {@link AsyncTask} objects we use in this class. */
    public enum Tasks {
        CHECK_FOR_CONTENT,
        CHECK_CONTENT_AFTER_CHANGE,
        PREPARE_MEDIA_PLAYER,
        RESET_PREPARE_START_MEDIA_PLAYER,
    }

    /** Update rate for the slider, 30fps. */
    private static final int SLIDER_UPDATE_PERIOD_MILLIS = 1000 / 30;
    /** Time our ui will wait for content to be fetched before reporting not available. */
    private static final long FETCH_CONTENT_TIMEOUT_MS = 20000;
    /**
     * If present in the saved instance bundle, we should not resume playback on
     * create.
     */
    private static final String IS_PLAYING_STATE_KEY = VoicemailPlaybackPresenter.class.getName()
            + ".IS_PLAYING_STATE_KEY";
    /**
     * If present in the saved instance bundle, indicates where to set the
     * playback slider.
     */
    private static final String CLIP_POSITION_KEY = VoicemailPlaybackPresenter.class.getName()
            + ".CLIP_POSITION_KEY";

    /**
     * The most recently calculated duration.
     * <p>
     * We cache this in a field since we don't want to keep requesting it from the player, as
     * this can easily lead to throwing {@link IllegalStateException} (any time the player is
     * released, it's illegal to ask for the duration).
     */
    private final AtomicInteger mDuration = new AtomicInteger(0);

    private final PlaybackView mView;
    private final MediaPlayerProxy mPlayer;
    private final PositionUpdater mPositionUpdater;

    /** Voicemail uri to play. */
    private final Uri mVoicemailUri;
    /** Start playing in onCreate iff this is true. */
    private final boolean mStartPlayingImmediately;
    /** Used to run async tasks that need to interact with the ui. */
    private final AsyncTaskExecutor mAsyncTaskExecutor;

    /**
     * Used to handle the result of a successful or time-out fetch result.
     * <p>
     * This variable is thread-contained, accessed only on the ui thread.
     */
    private FetchResultHandler mFetchResultHandler;
    private PowerManager.WakeLock mWakeLock;
    private AsyncTask<Void, ?, ?> mPrepareTask;
    private int mPosition;
    private boolean mPlaying;
    private AudioManager mAudioManager;

    public VoicemailPlaybackPresenter(PlaybackView view, MediaPlayerProxy player,
            Uri voicemailUri, ScheduledExecutorService executorService,
            boolean startPlayingImmediately, AsyncTaskExecutor asyncTaskExecutor,
            PowerManager.WakeLock wakeLock) {
        mView = view;
        mPlayer = player;
        mVoicemailUri = voicemailUri;
        mStartPlayingImmediately = startPlayingImmediately;
        mAsyncTaskExecutor = asyncTaskExecutor;
        mPositionUpdater = new PositionUpdater(executorService, SLIDER_UPDATE_PERIOD_MILLIS);
        mWakeLock = wakeLock;
    }

    public void onCreate(Bundle bundle) {
        mView.setVolumeControlStream(PLAYBACK_STREAM);
        checkThatWeHaveContent();
    }

    /**
     * Checks to see if we have content available for this voicemail.
     * <p>
     * This method will be called once, after the fragment has been created, before we know if the
     * voicemail we've been asked to play has any content available.
     * <p>
     * This method will notify the user through the ui that we are fetching the content, then check
     * to see if the content field in the db is set. If set, we proceed to
     * {@link #postSuccessfullyFetchedContent()} method. If not set, we will make a request to fetch
     * the content asynchronously via {@link #makeRequestForContent()}.
     */
    private void checkThatWeHaveContent() {
        mView.setIsFetchingContent();
        mAsyncTaskExecutor.submit(Tasks.CHECK_FOR_CONTENT, new AsyncTask<Void, Void, Boolean>() {
            @Override
            public Boolean doInBackground(Void... params) {
                return mView.queryHasContent(mVoicemailUri);
            }

            @Override
            public void onPostExecute(Boolean hasContent) {
                if (hasContent) {
                    postSuccessfullyFetchedContent();
                } else {
                    makeRequestForContent();
                }
            }
        });
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
     * proceed to {@link #postSuccessfullyFetchedContent()}. If the has_content field does not
     * become true within the allowed time, we will update the ui to reflect the fact that content
     * was not available.
     */
    private void makeRequestForContent() {
        Handler handler = new Handler();
        Preconditions.checkState(mFetchResultHandler == null, "mFetchResultHandler should be null");
        mFetchResultHandler = new FetchResultHandler(handler);
        mView.registerContentObserver(mVoicemailUri, mFetchResultHandler);
        handler.postDelayed(mFetchResultHandler.getTimeoutRunnable(), FETCH_CONTENT_TIMEOUT_MS);
        mView.sendFetchVoicemailRequest(mVoicemailUri);
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
                mView.unregisterContentObserver(FetchResultHandler.this);
                mView.setFetchContentTimeout();
            }
        }

        public void destroy() {
            if (mResultStillPending.getAndSet(false)) {
                mView.unregisterContentObserver(FetchResultHandler.this);
                mHandler.removeCallbacks(this);
            }
        }

        @Override
        public void onChange(boolean selfChange) {
            mAsyncTaskExecutor.submit(Tasks.CHECK_CONTENT_AFTER_CHANGE,
                    new AsyncTask<Void, Void, Boolean>() {
                @Override
                public Boolean doInBackground(Void... params) {
                    return mView.queryHasContent(mVoicemailUri);
                }

                @Override
                public void onPostExecute(Boolean hasContent) {
                    if (hasContent) {
                        if (mResultStillPending.getAndSet(false)) {
                            mView.unregisterContentObserver(FetchResultHandler.this);
                            postSuccessfullyFetchedContent();
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
     * content provider). This method will try to prepare the data source through the media player.
     * If preparing the media player works, we will call through to
     * {@link #postSuccessfulPrepareActions()}. If preparing the media player fails (perhaps the
     * file the content provider points to is actually missing, perhaps it is of an unknown file
     * format that we can't play, who knows) then we will show an error on the ui.
     */
    private void postSuccessfullyFetchedContent() {
        mView.setIsBuffering();
        mAsyncTaskExecutor.submit(Tasks.PREPARE_MEDIA_PLAYER,
                new AsyncTask<Void, Void, Exception>() {
                    @Override
                    public Exception doInBackground(Void... params) {
                        try {
                            mPlayer.reset();
                            mPlayer.setDataSource(mView.getDataSourceContext(), mVoicemailUri);
                            mPlayer.setAudioStreamType(PLAYBACK_STREAM);
                            mPlayer.prepare();
                            mDuration.set(mPlayer.getDuration());
                            return null;
                        } catch (Exception e) {
                            return e;
                        }
                    }

                    @Override
                    public void onPostExecute(Exception exception) {
                        if (exception == null) {
                            postSuccessfulPrepareActions();
                        } else {
                            mView.playbackError(exception);
                        }
                    }
                });
    }

    /**
     * Enables the ui, and optionally starts playback immediately.
     * <p>
     * This will be called once we have successfully prepared the media player, and will optionally
     * playback immediately.
     */
    private void postSuccessfulPrepareActions() {
        mView.enableUiElements();
        mView.setPositionSeekListener(new PlaybackPositionListener());
        mView.setStartStopListener(new StartStopButtonListener());
        mView.setSpeakerphoneListener(new SpeakerphoneListener());
        mPlayer.setOnErrorListener(new MediaPlayerErrorListener());
        mPlayer.setOnCompletionListener(new MediaPlayerCompletionListener());
        mView.setSpeakerPhoneOn(mView.isSpeakerPhoneOn());
        if (mPlaying) {
           resetPrepareStartPlaying(mPosition);
        } else {
           stopPlaybackAtPosition(mPosition, mDuration.get());
           if ((mPosition == 0) && (mStartPlayingImmediately)) {
               resetPrepareStartPlaying(0);
           }
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(CLIP_POSITION_KEY, mView.getDesiredClipPosition());
        outState.putBoolean(IS_PLAYING_STATE_KEY, mPlaying);
    }

    public void onRestoreInstanceState(Bundle inState) {
        int position = 0;
        boolean isPlaying = false;
        if (inState != null) {
            position = inState.getInt(CLIP_POSITION_KEY, 0);
            isPlaying = inState.getBoolean(IS_PLAYING_STATE_KEY, false);
        }
        setPositionAndPlayingStatus(position, isPlaying) ;
    }

    private void setPositionAndPlayingStatus(int position, boolean isPlaying) {
       mPosition = position;
       mPlaying = isPlaying;
    }

    public void onDestroy() {
        if (mPrepareTask != null) {
            mPrepareTask.cancel(false);
            mPrepareTask = null;
        }
        mPlayer.release();
        if (mFetchResultHandler != null) {
            mFetchResultHandler.destroy();
            mFetchResultHandler = null;
        }
        mPositionUpdater.stopUpdating();
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    private class MediaPlayerErrorListener implements MediaPlayer.OnErrorListener {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            mView.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleError(new IllegalStateException("MediaPlayer error listener invoked"));
                }
            });
            return true;
        }
    }

    private class MediaPlayerCompletionListener implements MediaPlayer.OnCompletionListener {
        @Override
        public void onCompletion(final MediaPlayer mp) {
            mView.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleCompletion(mp);
                }
            });
        }
    }

    private class AsyncPrepareTask extends AsyncTask<Void, Void, Exception> {
        private int mClipPositionInMillis;

        AsyncPrepareTask(int clipPositionInMillis) {
            mClipPositionInMillis = clipPositionInMillis;
        }

        @Override
        public Exception doInBackground(Void... params) {
            try {
                mPlayer.reset();
                mPlayer.setDataSource(mView.getDataSourceContext(), mVoicemailUri);
                mPlayer.setAudioStreamType(PLAYBACK_STREAM);
                mPlayer.prepare();
                return null;
            } catch (Exception e) {
                return e;
            }
        }

        @Override
        public void onPostExecute(Exception exception) {
            mPrepareTask = null;
            if (exception == null) {
                final int duration = mPlayer.getDuration();
                mDuration.set(duration);
                int startPosition =
                    constrain(mClipPositionInMillis, 0, duration);
                mPlayer.seekTo(startPosition);
                mView.setClipPosition(startPosition, duration);
                try {
                    // Grab audio focus here
                    int result = getAudioManager().requestAudioFocus(
                            VoicemailPlaybackPresenter.this,
                            PLAYBACK_STREAM,
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

                    if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        throw new RejectedExecutionException("Could not capture audio focus.");
                    }
                    // Can throw RejectedExecutionException
                    mPlayer.start();
                    setPositionAndPlayingStatus(mPlayer.getCurrentPosition(), true);
                    mView.playbackStarted();
                    if (!mWakeLock.isHeld()) {
                        mWakeLock.acquire();
                    }
                    // Only enable if we are not currently using the speaker phone.
                    if (!mView.isSpeakerPhoneOn()) {
                        mView.enableProximitySensor();
                    }
                    // Can throw RejectedExecutionException
                    mPositionUpdater.startUpdating(startPosition, duration);
                } catch (RejectedExecutionException e) {
                    handleError(e);
                }
            } else {
                handleError(exception);
            }
        }
    }

    private AudioManager getAudioManager() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager)
                    mView.getDataSourceContext().getSystemService(Context.AUDIO_SERVICE);
        }
        return mAudioManager;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        boolean lostFocus = focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS;
        // Note: the below logic is the same as in {@code StartStopButtonListener}.
        if (mPlayer.isPlaying() && lostFocus) {
            setPositionAndPlayingStatus(mPlayer.getCurrentPosition(), false);
            stopPlaybackAtPosition(mPlayer.getCurrentPosition(), mDuration.get());
        } else if (!mPlayer.isPlaying() && focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            setPositionAndPlayingStatus(mPosition, true);
            postSuccessfullyFetchedContent();
        }
    }


    private void resetPrepareStartPlaying(final int clipPositionInMillis) {
        if (mPrepareTask != null) {
            mPrepareTask.cancel(false);
            mPrepareTask = null;
        }
        mPrepareTask = mAsyncTaskExecutor.submit(Tasks.RESET_PREPARE_START_MEDIA_PLAYER,
                new AsyncPrepareTask(clipPositionInMillis));
    }

    private void handleError(Exception e) {
        mView.playbackError(e);
        mPositionUpdater.stopUpdating();
        mPlayer.release();
        setPositionAndPlayingStatus(0, false);
    }

    public void handleCompletion(MediaPlayer mediaPlayer) {
        stopPlaybackAtPosition(0, mDuration.get());
    }

    private void stopPlaybackAtPosition(int clipPosition, int duration) {
        getAudioManager().abandonAudioFocus(this);
        mPositionUpdater.stopUpdating();
        mView.playbackStopped();
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        // Always disable on stop.
        mView.disableProximitySensor();
        mView.setClipPosition(clipPosition, duration);
        if (mPlayer.isPlaying()) {
            mPlayer.pause();
        }
    }

    private class PlaybackPositionListener implements SeekBar.OnSeekBarChangeListener {
        private boolean mShouldResumePlaybackAfterSeeking;

        @Override
        public void onStartTrackingTouch(SeekBar arg0) {
            if (mPlayer.isPlaying()) {
                mShouldResumePlaybackAfterSeeking = true;
                stopPlaybackAtPosition(mPlayer.getCurrentPosition(), mDuration.get());
            } else {
                mShouldResumePlaybackAfterSeeking = false;
            }
        }

        @Override
        public void onStopTrackingTouch(SeekBar arg0) {
            if (mPlayer.isPlaying()) {
                setPositionAndPlayingStatus(mPlayer.getCurrentPosition(), false);
                stopPlaybackAtPosition(mPlayer.getCurrentPosition(), mDuration.get());
            } else {
                setPositionAndPlayingStatus(mView.getDesiredClipPosition(),
                        mShouldResumePlaybackAfterSeeking);
            }

            if (mShouldResumePlaybackAfterSeeking) {
                postSuccessfullyFetchedContent();
            }
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            mView.setClipPosition(seekBar.getProgress(), seekBar.getMax());
        }
    }

    private class SpeakerphoneListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            boolean previousState = mView.isSpeakerPhoneOn();
            mView.setSpeakerPhoneOn(!previousState);
            if (mPlayer.isPlaying() && previousState) {
                // If we are currently playing and we are disabling the speaker phone, enable the
                // sensor.
                mView.enableProximitySensor();
            } else {
                // If we are not currently playing, disable the sensor.
                mView.disableProximitySensor();
            }
        }
    }

    private class StartStopButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View arg0) {
            if (mPlayer.isPlaying()) {
                setPositionAndPlayingStatus(mPlayer.getCurrentPosition(), false);
                stopPlaybackAtPosition(mPlayer.getCurrentPosition(), mDuration.get());
            } else {
                setPositionAndPlayingStatus(mPosition, true);
                postSuccessfullyFetchedContent();
            }
        }
    }

    /**
     * Controls the animation of the playback slider.
     */
    @ThreadSafe
    private final class PositionUpdater implements Runnable {
        private final ScheduledExecutorService mExecutorService;
        private final int mPeriodMillis;
        private final Object mLock = new Object();
        @GuardedBy("mLock") private ScheduledFuture<?> mScheduledFuture;
        private final Runnable mSetClipPostitionRunnable = new Runnable() {
            @Override
            public void run() {
                int currentPosition = 0;
                synchronized (mLock) {
                    if (mScheduledFuture == null) {
                        // This task has been canceled. Just stop now.
                        return;
                    }
                    currentPosition = mPlayer.getCurrentPosition();
                }
                mView.setClipPosition(currentPosition, mDuration.get());
            }
        };

        public PositionUpdater(ScheduledExecutorService executorService, int periodMillis) {
            mExecutorService = executorService;
            mPeriodMillis = periodMillis;
        }

        @Override
        public void run() {
            mView.runOnUiThread(mSetClipPostitionRunnable);
        }

        public void startUpdating(int beginPosition, int endPosition) {
            synchronized (mLock) {
                if (mScheduledFuture != null) {
                    mScheduledFuture.cancel(false);
                    mScheduledFuture = null;
                }
                mScheduledFuture = mExecutorService.scheduleAtFixedRate(this, 0, mPeriodMillis,
                        TimeUnit.MILLISECONDS);
            }
        }

        public void stopUpdating() {
            synchronized (mLock) {
                if (mScheduledFuture != null) {
                    mScheduledFuture.cancel(false);
                    mScheduledFuture = null;
                }
            }
        }
    }

    public void onPause() {
        if (mPlayer.isPlaying()) {
            stopPlaybackAtPosition(mPlayer.getCurrentPosition(), mDuration.get());
        }
        if (mPrepareTask != null) {
            mPrepareTask.cancel(false);
            mPrepareTask = null;
        }
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    private static int constrain(int amount, int low, int high) {
        return amount < low ? low : (amount > high ? high : amount);
    }
}
