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

package com.android.dialer.app.voicemail;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.CallLog;
import android.provider.VoicemailContract;
import android.provider.VoicemailContract.Voicemails;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.Pair;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.webkit.MimeTypeMap;
import com.android.common.io.MoreCloseables;
import com.android.dialer.app.R;
import com.android.dialer.app.calllog.CallLogListItemViewHolder;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.AsyncTaskExecutor;
import com.android.dialer.common.concurrent.AsyncTaskExecutors;
import com.android.dialer.common.concurrent.DialerExecutor;
import com.android.dialer.common.concurrent.DialerExecutors;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.constants.Constants;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.phonenumbercache.CallLogQuery;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.PermissionsUtil;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Contains the controlling logic for a voicemail playback in the call log. It is closely coupled to
 * assumptions about the behaviors and lifecycle of the call log, in particular in the {@link
 * CallLogFragment} and {@link CallLogAdapter}.
 *
 * <p>This controls a single {@link com.android.dialer.app.voicemail.VoicemailPlaybackLayout}. A
 * single instance can be reused for different such layouts, using {@link #setPlaybackView}. This is
 * to facilitate reuse across different voicemail call log entries.
 *
 * <p>This class is not thread safe. The thread policy for this class is thread-confinement, all
 * calls into this class from outside must be done from the main UI thread.
 */
@NotThreadSafe
@TargetApi(VERSION_CODES.M)
public class VoicemailPlaybackPresenter
    implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener {

  public static final int PLAYBACK_REQUEST = 0;
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
  private static final String IS_SPEAKERPHONE_ON_KEY =
      VoicemailPlaybackPresenter.class.getName() + ".IS_SPEAKER_PHONE_ON";
  private static final String VOICEMAIL_SHARE_FILE_NAME_DATE_FORMAT = "MM-dd-yy_hhmmaa";
  private static final String CONFIG_SHARE_VOICEMAIL_ALLOWED = "share_voicemail_allowed";

  private static VoicemailPlaybackPresenter sInstance;
  private static ScheduledExecutorService mScheduledExecutorService;
  /**
   * The most recently cached duration. We cache this since we don't want to keep requesting it from
   * the player, as this can easily lead to throwing {@link IllegalStateException} (any time the
   * player is released, it's illegal to ask for the duration).
   */
  private final AtomicInteger mDuration = new AtomicInteger(0);

  protected Context mContext;
  private long mRowId;
  protected Uri mVoicemailUri;
  protected MediaPlayer mMediaPlayer;
  // Used to run async tasks that need to interact with the UI.
  protected AsyncTaskExecutor mAsyncTaskExecutor;
  private Activity mActivity;
  private PlaybackView mView;
  private int mPosition;
  private boolean mIsPlaying;
  // MediaPlayer crashes on some method calls if not prepared but does not have a method which
  // exposes its prepared state. Store this locally, so we can check and prevent crashes.
  private boolean mIsPrepared;
  private boolean mIsSpeakerphoneOn;

  private boolean mShouldResumePlaybackAfterSeeking;
  /**
   * Used to handle the result of a successful or time-out fetch result.
   *
   * <p>This variable is thread-contained, accessed only on the ui thread.
   */
  private FetchResultHandler mFetchResultHandler;

  private PowerManager.WakeLock mProximityWakeLock;
  private VoicemailAudioManager mVoicemailAudioManager;
  private OnVoicemailDeletedListener mOnVoicemailDeletedListener;
  private View shareVoicemailButtonView;

  private DialerExecutor<Pair<Context, Uri>> shareVoicemailExecutor;

  /** Initialize variables which are activity-independent and state-independent. */
  protected VoicemailPlaybackPresenter(Activity activity) {
    Context context = activity.getApplicationContext();
    mAsyncTaskExecutor = AsyncTaskExecutors.createAsyncTaskExecutor();
    mVoicemailAudioManager = new VoicemailAudioManager(context, this);
    PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
      mProximityWakeLock =
          powerManager.newWakeLock(
              PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "VoicemailPlaybackPresenter");
    }
  }

  /**
   * Obtain singleton instance of this class. Use a single instance to provide a consistent listener
   * to the AudioManager when requesting and abandoning audio focus.
   *
   * <p>Otherwise, after rotation the previous listener will still be active but a new listener will
   * be provided to calls to the AudioManager, which is bad. For example, abandoning audio focus
   * with the new listeners results in an AUDIO_FOCUS_GAIN callback to the previous listener, which
   * is the opposite of the intended behavior.
   */
  @MainThread
  public static VoicemailPlaybackPresenter getInstance(
      Activity activity, Bundle savedInstanceState) {
    if (sInstance == null) {
      sInstance = new VoicemailPlaybackPresenter(activity);
    }

    sInstance.init(activity, savedInstanceState);
    return sInstance;
  }

  private static synchronized ScheduledExecutorService getScheduledExecutorServiceInstance() {
    if (mScheduledExecutorService == null) {
      mScheduledExecutorService = Executors.newScheduledThreadPool(NUMBER_OF_THREADS_IN_POOL);
    }
    return mScheduledExecutorService;
  }

  /** Update variables which are activity-dependent or state-dependent. */
  @MainThread
  protected void init(Activity activity, Bundle savedInstanceState) {
    Assert.isMainThread();
    mActivity = activity;
    mContext = activity;

    if (savedInstanceState != null) {
      // Restores playback state when activity is recreated, such as after rotation.
      mVoicemailUri = savedInstanceState.getParcelable(VOICEMAIL_URI_KEY);
      mIsPrepared = savedInstanceState.getBoolean(IS_PREPARED_KEY);
      mPosition = savedInstanceState.getInt(CLIP_POSITION_KEY, 0);
      mIsPlaying = savedInstanceState.getBoolean(IS_PLAYING_STATE_KEY, false);
      mIsSpeakerphoneOn = savedInstanceState.getBoolean(IS_SPEAKERPHONE_ON_KEY, false);
    }

    if (mMediaPlayer == null) {
      mIsPrepared = false;
      mIsPlaying = false;
    }

    if (mActivity != null) {
      if (isPlaying()) {
        mActivity.getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
      } else {
        mActivity.getWindow().clearFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
      }
      shareVoicemailExecutor =
          DialerExecutors.createUiTaskBuilder(
                  mActivity.getFragmentManager(), "test", new ShareVoicemailWorker())
              .onSuccess(
                  output -> {
                    if (output == null) {
                      LogUtil.e("VoicemailAsyncTaskUtil.shareVoicemail", "failed to get voicemail");
                      return;
                    }
                    mContext.startActivity(
                        Intent.createChooser(
                            getShareIntent(mContext, output.first, output.second),
                            mContext
                                .getResources()
                                .getText(R.string.call_log_action_share_voicemail)));
                  })
              .build();
    }
  }

  /** Must be invoked when the parent Activity is saving it state. */
  public void onSaveInstanceState(Bundle outState) {
    if (mView != null) {
      outState.putParcelable(VOICEMAIL_URI_KEY, mVoicemailUri);
      outState.putBoolean(IS_PREPARED_KEY, mIsPrepared);
      outState.putInt(CLIP_POSITION_KEY, mView.getDesiredClipPosition());
      outState.putBoolean(IS_PLAYING_STATE_KEY, mIsPlaying);
      outState.putBoolean(IS_SPEAKERPHONE_ON_KEY, mIsSpeakerphoneOn);
    }
  }

  /** Specify the view which this presenter controls and the voicemail to prepare to play. */
  public void setPlaybackView(
      PlaybackView view,
      long rowId,
      Uri voicemailUri,
      final boolean startPlayingImmediately,
      View shareVoicemailButtonView) {
    mRowId = rowId;
    mView = view;
    mView.setPresenter(this, voicemailUri);
    mView.onSpeakerphoneOn(mIsSpeakerphoneOn);
    this.shareVoicemailButtonView = shareVoicemailButtonView;
    showShareVoicemailButton(false);

    // Handles cases where the same entry is binded again when scrolling in list, or where
    // the MediaPlayer was retained after an orientation change.
    if (mMediaPlayer != null && mIsPrepared && voicemailUri.equals(mVoicemailUri)) {
      // If the voicemail card was rebinded, we need to set the position to the appropriate
      // point. Since we retain the media player, we can just set it to the position of the
      // media player.
      mPosition = mMediaPlayer.getCurrentPosition();
      onPrepared(mMediaPlayer);
      showShareVoicemailButton(true);
    } else {
      if (!voicemailUri.equals(mVoicemailUri)) {
        mVoicemailUri = voicemailUri;
        mPosition = 0;
      }
      /*
       * Check to see if the content field in the DB is set. If set, we proceed to
       * prepareContent() method. We get the duration of the voicemail from the query and set
       * it if the content is not available.
       */
      checkForContent(
          hasContent -> {
            if (hasContent) {
              showShareVoicemailButton(true);
              prepareContent();
            } else {
              if (startPlayingImmediately) {
                requestContent(PLAYBACK_REQUEST);
              }
              if (mView != null) {
                mView.resetSeekBar();
                mView.setClipPosition(0, mDuration.get());
              }
            }
          });

      if (startPlayingImmediately) {
        // Since setPlaybackView can get called during the view binding process, we don't
        // want to reset mIsPlaying to false if the user is currently playing the
        // voicemail and the view is rebound.
        mIsPlaying = startPlayingImmediately;
      }
    }
  }

  /** Reset the presenter for playback back to its original state. */
  public void resetAll() {
    pausePresenter(true);

    mView = null;
    mVoicemailUri = null;
  }

  /**
   * When navigating away from voicemail playback, we need to release the media player, pause the UI
   * and save the position.
   *
   * @param reset {@code true} if we want to reset the position of the playback, {@code false} if we
   *     want to retain the current position (in case we return to the voicemail).
   */
  public void pausePresenter(boolean reset) {
    pausePlayback();
    if (mMediaPlayer != null) {
      mMediaPlayer.release();
      mMediaPlayer = null;
    }

    disableProximitySensor(false /* waitForFarState */);

    mIsPrepared = false;
    mIsPlaying = false;

    if (reset) {
      // We want to reset the position whether or not the view is valid.
      mPosition = 0;
    }

    if (mView != null) {
      mView.onPlaybackStopped();
      if (reset) {
        mView.setClipPosition(0, mDuration.get());
      } else {
        mPosition = mView.getDesiredClipPosition();
      }
    }
  }

  /** Must be invoked when the parent activity is resumed. */
  public void onResume() {
    mVoicemailAudioManager.registerReceivers();
  }

  /** Must be invoked when the parent activity is paused. */
  public void onPause() {
    mVoicemailAudioManager.unregisterReceivers();

    if (mActivity != null && mIsPrepared && mActivity.isChangingConfigurations()) {
      // If an configuration change triggers the pause, retain the MediaPlayer.
      LogUtil.d("VoicemailPlaybackPresenter.onPause", "configuration changed.");
      return;
    }

    // Release the media player, otherwise there may be failures.
    pausePresenter(false);
  }

  /** Must be invoked when the parent activity is destroyed. */
  public void onDestroy() {
    // Clear references to avoid leaks from the singleton instance.
    mActivity = null;
    mContext = null;

    if (mScheduledExecutorService != null) {
      mScheduledExecutorService.shutdown();
      mScheduledExecutorService = null;
    }

    if (mFetchResultHandler != null) {
      mFetchResultHandler.destroy();
      mFetchResultHandler = null;
    }
  }

  /** Checks to see if we have content available for this voicemail. */
  protected void checkForContent(final OnContentCheckedListener callback) {
    mAsyncTaskExecutor.submit(
        Tasks.CHECK_FOR_CONTENT,
        new AsyncTask<Void, Void, Boolean>() {
          @Override
          public Boolean doInBackground(Void... params) {
            return queryHasContent(mVoicemailUri);
          }

          @Override
          public void onPostExecute(Boolean hasContent) {
            callback.onContentChecked(hasContent);
          }
        });
  }

  private boolean queryHasContent(Uri voicemailUri) {
    if (voicemailUri == null || mContext == null) {
      return false;
    }

    ContentResolver contentResolver = mContext.getContentResolver();
    Cursor cursor = contentResolver.query(voicemailUri, null, null, null, null);
    try {
      if (cursor != null && cursor.moveToNext()) {
        int duration = cursor.getInt(cursor.getColumnIndex(VoicemailContract.Voicemails.DURATION));
        // Convert database duration (seconds) into mDuration (milliseconds)
        mDuration.set(duration > 0 ? duration * 1000 : 0);
        return cursor.getInt(cursor.getColumnIndex(VoicemailContract.Voicemails.HAS_CONTENT)) == 1;
      }
    } finally {
      MoreCloseables.closeQuietly(cursor);
    }
    return false;
  }

  /**
   * Makes a broadcast request to ask that a voicemail source fetch this content.
   *
   * <p>This method <b>must be called on the ui thread</b>.
   *
   * <p>This method will be called when we realise that we don't have content for this voicemail. It
   * will trigger a broadcast to request that the content be downloaded. It will add a listener to
   * the content resolver so that it will be notified when the has_content field changes. It will
   * also set a timer. If the has_content field changes to true within the allowed time, we will
   * proceed to {@link #prepareContent()}. If the has_content field does not become true within the
   * allowed time, we will update the ui to reflect the fact that content was not available.
   *
   * @return whether issued request to fetch content
   */
  protected boolean requestContent(int code) {
    if (mContext == null || mVoicemailUri == null) {
      return false;
    }

    FetchResultHandler tempFetchResultHandler =
        new FetchResultHandler(new Handler(), mVoicemailUri, code);

    switch (code) {
      default:
        if (mFetchResultHandler != null) {
          mFetchResultHandler.destroy();
        }
        mView.setIsFetchingContent();
        mFetchResultHandler = tempFetchResultHandler;
        break;
    }

    mAsyncTaskExecutor.submit(
        Tasks.SEND_FETCH_REQUEST,
        new AsyncTask<Void, Void, Void>() {

          @Override
          protected Void doInBackground(Void... voids) {
            try (Cursor cursor =
                mContext
                    .getContentResolver()
                    .query(
                        mVoicemailUri,
                        new String[] {Voicemails.SOURCE_PACKAGE},
                        null,
                        null,
                        null)) {
              String sourcePackage;
              if (!hasContent(cursor)) {
                LogUtil.e(
                    "VoicemailPlaybackPresenter.requestContent",
                    "mVoicemailUri does not return a SOURCE_PACKAGE");
                sourcePackage = null;
              } else {
                sourcePackage = cursor.getString(0);
              }
              // Send voicemail fetch request.
              Intent intent = new Intent(VoicemailContract.ACTION_FETCH_VOICEMAIL, mVoicemailUri);
              intent.setPackage(sourcePackage);
              LogUtil.i(
                  "VoicemailPlaybackPresenter.requestContent",
                  "Sending ACTION_FETCH_VOICEMAIL to " + sourcePackage);
              mContext.sendBroadcast(intent);
            }
            return null;
          }
        });
    return true;
  }

  /**
   * Prepares the voicemail content for playback.
   *
   * <p>This method will be called once we know that our voicemail has content (according to the
   * content provider). this method asynchronously tries to prepare the data source through the
   * media player. If preparation is successful, the media player will {@link #onPrepared()}, and it
   * will call {@link #onError()} otherwise.
   */
  protected void prepareContent() {
    if (mView == null || mContext == null) {
      return;
    }
    LogUtil.d("VoicemailPlaybackPresenter.prepareContent", null);

    // Release the previous media player, otherwise there may be failures.
    if (mMediaPlayer != null) {
      mMediaPlayer.release();
      mMediaPlayer = null;
    }

    mView.disableUiElements();
    mIsPrepared = false;

    if (mContext != null && TelecomUtil.isInCall(mContext)) {
      handleError(new IllegalStateException("Cannot play voicemail when call is in progress"));
      return;
    }

    try {
      mMediaPlayer = new MediaPlayer();
      mMediaPlayer.setOnPreparedListener(this);
      mMediaPlayer.setOnErrorListener(this);
      mMediaPlayer.setOnCompletionListener(this);

      mMediaPlayer.reset();
      mMediaPlayer.setDataSource(mContext, mVoicemailUri);
      mMediaPlayer.setAudioStreamType(VoicemailAudioManager.PLAYBACK_STREAM);
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
    if (mView == null || mContext == null) {
      return;
    }
    LogUtil.d("VoicemailPlaybackPresenter.onPrepared", null);
    mIsPrepared = true;

    mDuration.set(mMediaPlayer.getDuration());

    LogUtil.d("VoicemailPlaybackPresenter.onPrepared", "mPosition=" + mPosition);
    mView.setClipPosition(mPosition, mDuration.get());
    mView.enableUiElements();
    mView.setSuccess();
    if (!mp.isPlaying()) {
      mMediaPlayer.seekTo(mPosition);
    }

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
    handleError(new IllegalStateException("MediaPlayer error listener invoked: " + extra));
    return true;
  }

  protected void handleError(Exception e) {
    LogUtil.e("VoicemailPlaybackPresenter.handlerError", "could not play voicemail", e);

    if (mIsPrepared) {
      mMediaPlayer.release();
      mMediaPlayer = null;
      mIsPrepared = false;
    }

    if (mView != null) {
      mView.onPlaybackError();
    }

    mPosition = 0;
    mIsPlaying = false;
    showShareVoicemailButton(false);
  }

  /** After done playing the voicemail clip, reset the clip position to the start. */
  @Override
  public void onCompletion(MediaPlayer mediaPlayer) {
    pausePlayback();

    // Reset the seekbar position to the beginning.
    mPosition = 0;
    if (mView != null) {
      mediaPlayer.seekTo(0);
      mView.setClipPosition(0, mDuration.get());
    }
  }

  /**
   * Only play voicemail when audio focus is granted. When it is lost (usually by another
   * application requesting focus), pause playback. Audio focus gain/lost only triggers the focus is
   * requested. Audio focus is requested when the user pressed play and abandoned when the user
   * pressed pause or the audio has finished. Losing focus should not abandon focus as the voicemail
   * should resume once the focus is returned.
   *
   * @param gainedFocus {@code true} if the audio focus was gained, {@code} false otherwise.
   */
  public void onAudioFocusChange(boolean gainedFocus) {
    if (mIsPlaying == gainedFocus) {
      // Nothing new here, just exit.
      return;
    }

    if (gainedFocus) {
      resumePlayback();
    } else {
      pausePlayback(true);
    }
  }

  /**
   * Resumes voicemail playback at the clip position stored by the presenter. Null-op if already
   * playing.
   */
  public void resumePlayback() {
    if (mView == null) {
      return;
    }

    if (!mIsPrepared) {
      /*
       * Check content before requesting content to avoid duplicated requests. It is possible
       * that the UI doesn't know content has arrived if the fetch took too long causing a
       * timeout, but succeeded.
       */
      checkForContent(
          hasContent -> {
            if (!hasContent) {
              // No local content, download from server. Queue playing if the request was
              // issued,
              mIsPlaying = requestContent(PLAYBACK_REQUEST);
            } else {
              showShareVoicemailButton(true);
              // Queue playing once the media play loaded the content.
              mIsPlaying = true;
              prepareContent();
            }
          });
      return;
    }

    mIsPlaying = true;

    mActivity.getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);

    if (mMediaPlayer != null && !mMediaPlayer.isPlaying()) {
      // Clamp the start position between 0 and the duration.
      mPosition = Math.max(0, Math.min(mPosition, mDuration.get()));

      mMediaPlayer.seekTo(mPosition);

      try {
        // Grab audio focus.
        // Can throw RejectedExecutionException.
        mVoicemailAudioManager.requestAudioFocus();
        mMediaPlayer.start();
        setSpeakerphoneOn(mIsSpeakerphoneOn);
        mVoicemailAudioManager.setSpeakerphoneOn(mIsSpeakerphoneOn);
      } catch (RejectedExecutionException e) {
        handleError(e);
      }
    }

    LogUtil.d("VoicemailPlaybackPresenter.resumePlayback", "resumed playback at %d.", mPosition);
    mView.onPlaybackStarted(mDuration.get(), getScheduledExecutorServiceInstance());
  }

  /** Pauses voicemail playback at the current position. Null-op if already paused. */
  public void pausePlayback() {
    pausePlayback(false);
  }

  private void pausePlayback(boolean keepFocus) {
    if (!mIsPrepared) {
      return;
    }

    mIsPlaying = false;

    if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
      mMediaPlayer.pause();
    }

    mPosition = mMediaPlayer == null ? 0 : mMediaPlayer.getCurrentPosition();

    LogUtil.d("VoicemailPlaybackPresenter.pausePlayback", "paused playback at %d.", mPosition);

    if (mView != null) {
      mView.onPlaybackStopped();
    }

    if (!keepFocus) {
      mVoicemailAudioManager.abandonAudioFocus();
    }
    if (mActivity != null) {
      mActivity.getWindow().clearFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    disableProximitySensor(true /* waitForFarState */);
  }

  /**
   * Pauses playback when the user starts seeking the position, and notes whether the voicemail is
   * playing to know whether to resume playback once the user selects a new position.
   */
  public void pausePlaybackForSeeking() {
    if (mMediaPlayer != null) {
      mShouldResumePlaybackAfterSeeking = mMediaPlayer.isPlaying();
    }
    pausePlayback(true);
  }

  public void resumePlaybackAfterSeeking(int desiredPosition) {
    mPosition = desiredPosition;
    if (mShouldResumePlaybackAfterSeeking) {
      mShouldResumePlaybackAfterSeeking = false;
      resumePlayback();
    }
  }

  /**
   * Seek to position. This is called when user manually seek the playback. It could be either by
   * touch or volume button while in talkback mode.
   */
  public void seek(int position) {
    mPosition = position;
    mMediaPlayer.seekTo(mPosition);
  }

  private void enableProximitySensor() {
    if (mProximityWakeLock == null
        || mIsSpeakerphoneOn
        || !mIsPrepared
        || mMediaPlayer == null
        || !mMediaPlayer.isPlaying()) {
      return;
    }

    if (!mProximityWakeLock.isHeld()) {
      LogUtil.i(
          "VoicemailPlaybackPresenter.enableProximitySensor", "acquiring proximity wake lock");
      mProximityWakeLock.acquire();
    } else {
      LogUtil.i(
          "VoicemailPlaybackPresenter.enableProximitySensor",
          "proximity wake lock already acquired");
    }
  }

  private void disableProximitySensor(boolean waitForFarState) {
    if (mProximityWakeLock == null) {
      return;
    }
    if (mProximityWakeLock.isHeld()) {
      LogUtil.i(
          "VoicemailPlaybackPresenter.disableProximitySensor", "releasing proximity wake lock");
      int flags = waitForFarState ? PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY : 0;
      mProximityWakeLock.release(flags);
    } else {
      LogUtil.i(
          "VoicemailPlaybackPresenter.disableProximitySensor",
          "proximity wake lock already released");
    }
  }

  /** This is for use by UI interactions only. It simplifies UI logic. */
  public void toggleSpeakerphone() {
    mVoicemailAudioManager.setSpeakerphoneOn(!mIsSpeakerphoneOn);
    setSpeakerphoneOn(!mIsSpeakerphoneOn);
  }

  public void setOnVoicemailDeletedListener(OnVoicemailDeletedListener listener) {
    mOnVoicemailDeletedListener = listener;
  }

  public int getMediaPlayerPosition() {
    return mIsPrepared && mMediaPlayer != null ? mMediaPlayer.getCurrentPosition() : 0;
  }

  void onVoicemailDeleted(CallLogListItemViewHolder viewHolder) {
    if (mOnVoicemailDeletedListener != null) {
      mOnVoicemailDeletedListener.onVoicemailDeleted(viewHolder, mVoicemailUri);
    }
  }

  void onVoicemailDeleteUndo(int adapterPosition) {
    if (mOnVoicemailDeletedListener != null) {
      mOnVoicemailDeletedListener.onVoicemailDeleteUndo(mRowId, adapterPosition, mVoicemailUri);
    }
  }

  void onVoicemailDeletedInDatabase() {
    if (mOnVoicemailDeletedListener != null) {
      mOnVoicemailDeletedListener.onVoicemailDeletedInDatabase(mRowId, mVoicemailUri);
    }
  }

  @VisibleForTesting
  public boolean isPlaying() {
    return mIsPlaying;
  }

  @VisibleForTesting
  public boolean isSpeakerphoneOn() {
    return mIsSpeakerphoneOn;
  }

  /**
   * This method only handles app-level changes to the speakerphone. Audio layer changes should be
   * handled separately. This is so that the VoicemailAudioManager can trigger changes to the
   * presenter without the presenter triggering the audio manager and duplicating actions.
   */
  public void setSpeakerphoneOn(boolean on) {
    if (mView == null) {
      return;
    }

    mView.onSpeakerphoneOn(on);

    mIsSpeakerphoneOn = on;

    // This should run even if speakerphone is not being toggled because we may be switching
    // from earpiece to headphone and vise versa. Also upon initial setup the default audio
    // source is the earpiece, so we want to trigger the proximity sensor.
    if (mIsPlaying) {
      if (on || mVoicemailAudioManager.isWiredHeadsetPluggedIn()) {
        disableProximitySensor(false /* waitForFarState */);
      } else {
        enableProximitySensor();
      }
    }
  }

  @VisibleForTesting
  public void clearInstance() {
    sInstance = null;
  }

  private void showShareVoicemailButton(boolean show) {
    if (mContext == null) {
      return;
    }
    if (isShareVoicemailAllowed(mContext) && shareVoicemailButtonView != null) {
      if (show) {
        Logger.get(mContext).logImpression(DialerImpression.Type.VVM_SHARE_VISIBLE);
      }
      LogUtil.d("VoicemailPlaybackPresenter.showShareVoicemailButton", "show: %b", show);
      shareVoicemailButtonView.setVisibility(show ? View.VISIBLE : View.GONE);
    }
  }

  private static boolean isShareVoicemailAllowed(Context context) {
    return ConfigProviderBindings.get(context).getBoolean(CONFIG_SHARE_VOICEMAIL_ALLOWED, true);
  }

  private static class ShareVoicemailWorker
      implements DialerExecutor.Worker<Pair<Context, Uri>, Pair<Uri, String>> {

    @Nullable
    @Override
    public Pair<Uri, String> doInBackground(Pair<Context, Uri> input) {
      Context context = input.first;
      Uri voicemailUri = input.second;
      ContentResolver contentResolver = context.getContentResolver();
      try (Cursor callLogInfo = getCallLogInfoCursor(contentResolver, voicemailUri);
          Cursor contentInfo = getContentInfoCursor(contentResolver, voicemailUri)) {

        if (hasContent(callLogInfo) && hasContent(contentInfo)) {
          String cachedName = callLogInfo.getString(CallLogQuery.CACHED_NAME);
          String number = contentInfo.getString(contentInfo.getColumnIndex(Voicemails.NUMBER));
          long date = contentInfo.getLong(contentInfo.getColumnIndex(Voicemails.DATE));
          String mimeType = contentInfo.getString(contentInfo.getColumnIndex(Voicemails.MIME_TYPE));
          String transcription =
              contentInfo.getString(contentInfo.getColumnIndex(Voicemails.TRANSCRIPTION));

          // Copy voicemail content to a new file.
          // Please see reference in third_party/java_src/android_app/dialer/java/com/android/
          // dialer/app/res/xml/file_paths.xml for correct cache directory name.
          File parentDir = new File(context.getCacheDir(), "my_cache");
          if (!parentDir.exists()) {
            parentDir.mkdirs();
          }
          File temporaryVoicemailFile =
              new File(parentDir, getFileName(cachedName, number, mimeType, date));

          try (InputStream inputStream = contentResolver.openInputStream(voicemailUri);
              OutputStream outputStream =
                  contentResolver.openOutputStream(Uri.fromFile(temporaryVoicemailFile))) {
            if (inputStream != null && outputStream != null) {
              ByteStreams.copy(inputStream, outputStream);
              return new Pair<>(
                  FileProvider.getUriForFile(
                      context, Constants.get().getFileProviderAuthority(), temporaryVoicemailFile),
                  transcription);
            }
          } catch (IOException e) {
            LogUtil.e(
                "VoicemailAsyncTaskUtil.shareVoicemail",
                "failed to copy voicemail content to new file: ",
                e);
          }
          return null;
        }
      }
      return null;
    }
  }

  /**
   * Share voicemail to be opened by user selected apps. This method will collect information, copy
   * voicemail to a temporary file in background and launch a chooser intent to share it.
   */
  public void shareVoicemail() {
    shareVoicemailExecutor.executeParallel(new Pair<>(mContext, mVoicemailUri));
  }

  private static String getFileName(String cachedName, String number, String mimeType, long date) {
    String callerName = TextUtils.isEmpty(cachedName) ? number : cachedName;
    SimpleDateFormat simpleDateFormat =
        new SimpleDateFormat(VOICEMAIL_SHARE_FILE_NAME_DATE_FORMAT, Locale.getDefault());

    String fileExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);

    return callerName
        + "_"
        + simpleDateFormat.format(new Date(date))
        + (TextUtils.isEmpty(fileExtension) ? "" : "." + fileExtension);
  }

  private static Intent getShareIntent(
      Context context, Uri voicemailFileUri, String transcription) {
    Intent shareIntent = new Intent();
    if (TextUtils.isEmpty(transcription)) {
      shareIntent.setAction(Intent.ACTION_SEND);
      shareIntent.putExtra(Intent.EXTRA_STREAM, voicemailFileUri);
      shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      shareIntent.setType(context.getContentResolver().getType(voicemailFileUri));
    } else {
      shareIntent.setAction(Intent.ACTION_SEND);
      shareIntent.putExtra(Intent.EXTRA_STREAM, voicemailFileUri);
      shareIntent.putExtra(Intent.EXTRA_TEXT, transcription);
      shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      shareIntent.setType("*/*");
    }

    return shareIntent;
  }

  private static boolean hasContent(@Nullable Cursor cursor) {
    return cursor != null && cursor.moveToFirst();
  }

  @Nullable
  private static Cursor getCallLogInfoCursor(ContentResolver contentResolver, Uri voicemailUri) {
    return contentResolver.query(
        ContentUris.withAppendedId(
            CallLog.Calls.CONTENT_URI_WITH_VOICEMAIL, ContentUris.parseId(voicemailUri)),
        CallLogQuery.getProjection(),
        null,
        null,
        null);
  }

  @Nullable
  private static Cursor getContentInfoCursor(ContentResolver contentResolver, Uri voicemailUri) {
    return contentResolver.query(
        voicemailUri,
        new String[] {
          Voicemails._ID,
          Voicemails.NUMBER,
          Voicemails.DATE,
          Voicemails.MIME_TYPE,
          Voicemails.TRANSCRIPTION,
        },
        null,
        null,
        null);
  }

  /** The enumeration of {@link AsyncTask} objects we use in this class. */
  public enum Tasks {
    CHECK_FOR_CONTENT,
    CHECK_CONTENT_AFTER_CHANGE,
    SHARE_VOICEMAIL,
    SEND_FETCH_REQUEST
  }

  /** Contract describing the behaviour we need from the ui we are controlling. */
  public interface PlaybackView {

    int getDesiredClipPosition();

    void disableUiElements();

    void enableUiElements();

    void onPlaybackError();

    void onPlaybackStarted(int duration, ScheduledExecutorService executorService);

    void onPlaybackStopped();

    void onSpeakerphoneOn(boolean on);

    void setClipPosition(int clipPositionInMillis, int clipLengthInMillis);

    void setSuccess();

    void setFetchContentTimeout();

    void setIsFetchingContent();

    void setPresenter(VoicemailPlaybackPresenter presenter, Uri voicemailUri);

    void resetSeekBar();
  }

  public interface OnVoicemailDeletedListener {

    void onVoicemailDeleted(CallLogListItemViewHolder viewHolder, Uri uri);

    void onVoicemailDeleteUndo(long rowId, int adaptorPosition, Uri uri);

    void onVoicemailDeletedInDatabase(long rowId, Uri uri);
  }

  protected interface OnContentCheckedListener {

    void onContentChecked(boolean hasContent);
  }

  @ThreadSafe
  private class FetchResultHandler extends ContentObserver implements Runnable {

    private final Handler mFetchResultHandler;
    private final Uri mVoicemailUri;
    private AtomicBoolean mIsWaitingForResult = new AtomicBoolean(true);

    public FetchResultHandler(Handler handler, Uri uri, int code) {
      super(handler);
      mFetchResultHandler = handler;
      mVoicemailUri = uri;
      if (mContext != null) {
        if (PermissionsUtil.hasReadVoicemailPermissions(mContext)) {
          mContext.getContentResolver().registerContentObserver(mVoicemailUri, false, this);
        }
        mFetchResultHandler.postDelayed(this, FETCH_CONTENT_TIMEOUT_MS);
      }
    }

    /** Stop waiting for content and notify UI if {@link FETCH_CONTENT_TIMEOUT_MS} has elapsed. */
    @Override
    public void run() {
      if (mIsWaitingForResult.getAndSet(false) && mContext != null) {
        mContext.getContentResolver().unregisterContentObserver(this);
        if (mView != null) {
          mView.setFetchContentTimeout();
        }
      }
    }

    public void destroy() {
      if (mIsWaitingForResult.getAndSet(false) && mContext != null) {
        mContext.getContentResolver().unregisterContentObserver(this);
        mFetchResultHandler.removeCallbacks(this);
      }
    }

    @Override
    public void onChange(boolean selfChange) {
      mAsyncTaskExecutor.submit(
          Tasks.CHECK_CONTENT_AFTER_CHANGE,
          new AsyncTask<Void, Void, Boolean>() {

            @Override
            public Boolean doInBackground(Void... params) {
              return queryHasContent(mVoicemailUri);
            }

            @Override
            public void onPostExecute(Boolean hasContent) {
              if (hasContent && mContext != null && mIsWaitingForResult.getAndSet(false)) {
                mContext.getContentResolver().unregisterContentObserver(FetchResultHandler.this);
                showShareVoicemailButton(true);
                prepareContent();
              }
            }
          });
    }
  }
}
