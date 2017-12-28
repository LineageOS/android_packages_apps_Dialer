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
import com.android.dialer.common.concurrent.DialerExecutorComponent;
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

  private static VoicemailPlaybackPresenter instance;
  private static ScheduledExecutorService scheduledExecutorService;
  /**
   * The most recently cached duration. We cache this since we don't want to keep requesting it from
   * the player, as this can easily lead to throwing {@link IllegalStateException} (any time the
   * player is released, it's illegal to ask for the duration).
   */
  private final AtomicInteger duration = new AtomicInteger(0);

  protected Context context;
  private long rowId;
  protected Uri voicemailUri;
  protected MediaPlayer mediaPlayer;
  // Used to run async tasks that need to interact with the UI.
  protected AsyncTaskExecutor asyncTaskExecutor;
  private Activity activity;
  private PlaybackView view;
  private int position;
  private boolean isPlaying;
  // MediaPlayer crashes on some method calls if not prepared but does not have a method which
  // exposes its prepared state. Store this locally, so we can check and prevent crashes.
  private boolean isPrepared;
  private boolean isSpeakerphoneOn;

  private boolean shouldResumePlaybackAfterSeeking;
  /**
   * Used to handle the result of a successful or time-out fetch result.
   *
   * <p>This variable is thread-contained, accessed only on the ui thread.
   */
  private FetchResultHandler fetchResultHandler;

  private PowerManager.WakeLock proximityWakeLock;
  private VoicemailAudioManager voicemailAudioManager;
  private OnVoicemailDeletedListener onVoicemailDeletedListener;
  private View shareVoicemailButtonView;

  private DialerExecutor<Pair<Context, Uri>> shareVoicemailExecutor;

  /** Initialize variables which are activity-independent and state-independent. */
  protected VoicemailPlaybackPresenter(Activity activity) {
    Context context = activity.getApplicationContext();
    asyncTaskExecutor = AsyncTaskExecutors.createAsyncTaskExecutor();
    voicemailAudioManager = new VoicemailAudioManager(context, this);
    PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
      proximityWakeLock =
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
    if (instance == null) {
      instance = new VoicemailPlaybackPresenter(activity);
    }

    instance.init(activity, savedInstanceState);
    return instance;
  }

  private static synchronized ScheduledExecutorService getScheduledExecutorServiceInstance() {
    if (scheduledExecutorService == null) {
      scheduledExecutorService = Executors.newScheduledThreadPool(NUMBER_OF_THREADS_IN_POOL);
    }
    return scheduledExecutorService;
  }

  /** Update variables which are activity-dependent or state-dependent. */
  @MainThread
  protected void init(Activity activity, Bundle savedInstanceState) {
    Assert.isMainThread();
    this.activity = activity;
    context = activity;

    if (savedInstanceState != null) {
      // Restores playback state when activity is recreated, such as after rotation.
      voicemailUri = savedInstanceState.getParcelable(VOICEMAIL_URI_KEY);
      isPrepared = savedInstanceState.getBoolean(IS_PREPARED_KEY);
      position = savedInstanceState.getInt(CLIP_POSITION_KEY, 0);
      isPlaying = savedInstanceState.getBoolean(IS_PLAYING_STATE_KEY, false);
      isSpeakerphoneOn = savedInstanceState.getBoolean(IS_SPEAKERPHONE_ON_KEY, false);
    }

    if (mediaPlayer == null) {
      isPrepared = false;
      isPlaying = false;
    }

    if (this.activity != null) {
      if (isPlaying()) {
        this.activity.getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
      } else {
        this.activity.getWindow().clearFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
      }
      shareVoicemailExecutor =
          DialerExecutorComponent.get(context)
              .dialerExecutorFactory()
              .createUiTaskBuilder(
                  this.activity.getFragmentManager(), "shareVoicemail", new ShareVoicemailWorker())
              .onSuccess(
                  output -> {
                    if (output == null) {
                      LogUtil.e("VoicemailAsyncTaskUtil.shareVoicemail", "failed to get voicemail");
                      return;
                    }
                    context.startActivity(
                        Intent.createChooser(
                            getShareIntent(context, output.first, output.second),
                            context
                                .getResources()
                                .getText(R.string.call_log_action_share_voicemail)));
                  })
              .build();
    }
  }

  /** Must be invoked when the parent Activity is saving it state. */
  public void onSaveInstanceState(Bundle outState) {
    if (view != null) {
      outState.putParcelable(VOICEMAIL_URI_KEY, voicemailUri);
      outState.putBoolean(IS_PREPARED_KEY, isPrepared);
      outState.putInt(CLIP_POSITION_KEY, view.getDesiredClipPosition());
      outState.putBoolean(IS_PLAYING_STATE_KEY, isPlaying);
      outState.putBoolean(IS_SPEAKERPHONE_ON_KEY, isSpeakerphoneOn);
    }
  }

  /** Specify the view which this presenter controls and the voicemail to prepare to play. */
  public void setPlaybackView(
      PlaybackView view,
      long rowId,
      Uri voicemailUri,
      final boolean startPlayingImmediately,
      View shareVoicemailButtonView) {
    this.rowId = rowId;
    this.view = view;
    this.view.setPresenter(this, voicemailUri);
    this.view.onSpeakerphoneOn(isSpeakerphoneOn);
    this.shareVoicemailButtonView = shareVoicemailButtonView;
    showShareVoicemailButton(false);

    // Handles cases where the same entry is binded again when scrolling in list, or where
    // the MediaPlayer was retained after an orientation change.
    if (mediaPlayer != null && isPrepared && voicemailUri.equals(this.voicemailUri)) {
      // If the voicemail card was rebinded, we need to set the position to the appropriate
      // point. Since we retain the media player, we can just set it to the position of the
      // media player.
      position = mediaPlayer.getCurrentPosition();
      onPrepared(mediaPlayer);
      showShareVoicemailButton(true);
    } else {
      if (!voicemailUri.equals(this.voicemailUri)) {
        this.voicemailUri = voicemailUri;
        position = 0;
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
              if (this.view != null) {
                this.view.resetSeekBar();
                this.view.setClipPosition(0, duration.get());
              }
            }
          });

      if (startPlayingImmediately) {
        // Since setPlaybackView can get called during the view binding process, we don't
        // want to reset mIsPlaying to false if the user is currently playing the
        // voicemail and the view is rebound.
        isPlaying = startPlayingImmediately;
      }
    }
  }

  /** Reset the presenter for playback back to its original state. */
  public void resetAll() {
    pausePresenter(true);

    view = null;
    voicemailUri = null;
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
    if (mediaPlayer != null) {
      mediaPlayer.release();
      mediaPlayer = null;
    }

    disableProximitySensor(false /* waitForFarState */);

    isPrepared = false;
    isPlaying = false;

    if (reset) {
      // We want to reset the position whether or not the view is valid.
      position = 0;
    }

    if (view != null) {
      view.onPlaybackStopped();
      if (reset) {
        view.setClipPosition(0, duration.get());
      } else {
        position = view.getDesiredClipPosition();
      }
    }
  }

  /** Must be invoked when the parent activity is resumed. */
  public void onResume() {
    voicemailAudioManager.registerReceivers();
  }

  /** Must be invoked when the parent activity is paused. */
  public void onPause() {
    voicemailAudioManager.unregisterReceivers();

    if (activity != null && isPrepared && activity.isChangingConfigurations()) {
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
    activity = null;
    context = null;

    if (scheduledExecutorService != null) {
      scheduledExecutorService.shutdown();
      scheduledExecutorService = null;
    }

    if (fetchResultHandler != null) {
      fetchResultHandler.destroy();
      fetchResultHandler = null;
    }
  }

  /** Checks to see if we have content available for this voicemail. */
  protected void checkForContent(final OnContentCheckedListener callback) {
    asyncTaskExecutor.submit(
        Tasks.CHECK_FOR_CONTENT,
        new AsyncTask<Void, Void, Boolean>() {
          @Override
          public Boolean doInBackground(Void... params) {
            return queryHasContent(voicemailUri);
          }

          @Override
          public void onPostExecute(Boolean hasContent) {
            callback.onContentChecked(hasContent);
          }
        });
  }

  private boolean queryHasContent(Uri voicemailUri) {
    if (voicemailUri == null || context == null) {
      return false;
    }

    ContentResolver contentResolver = context.getContentResolver();
    Cursor cursor = contentResolver.query(voicemailUri, null, null, null, null);
    try {
      if (cursor != null && cursor.moveToNext()) {
        int duration = cursor.getInt(cursor.getColumnIndex(VoicemailContract.Voicemails.DURATION));
        // Convert database duration (seconds) into mDuration (milliseconds)
        this.duration.set(duration > 0 ? duration * 1000 : 0);
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
    if (context == null || voicemailUri == null) {
      return false;
    }

    FetchResultHandler tempFetchResultHandler =
        new FetchResultHandler(new Handler(), voicemailUri, code);

    switch (code) {
      default:
        if (fetchResultHandler != null) {
          fetchResultHandler.destroy();
        }
        view.setIsFetchingContent();
        fetchResultHandler = tempFetchResultHandler;
        break;
    }

    asyncTaskExecutor.submit(
        Tasks.SEND_FETCH_REQUEST,
        new AsyncTask<Void, Void, Void>() {

          @Override
          protected Void doInBackground(Void... voids) {
            try (Cursor cursor =
                context
                    .getContentResolver()
                    .query(
                        voicemailUri, new String[] {Voicemails.SOURCE_PACKAGE}, null, null, null)) {
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
              Intent intent = new Intent(VoicemailContract.ACTION_FETCH_VOICEMAIL, voicemailUri);
              intent.setPackage(sourcePackage);
              LogUtil.i(
                  "VoicemailPlaybackPresenter.requestContent",
                  "Sending ACTION_FETCH_VOICEMAIL to " + sourcePackage);
              context.sendBroadcast(intent);
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
    if (view == null || context == null) {
      return;
    }
    LogUtil.d("VoicemailPlaybackPresenter.prepareContent", null);

    // Release the previous media player, otherwise there may be failures.
    if (mediaPlayer != null) {
      mediaPlayer.release();
      mediaPlayer = null;
    }

    view.disableUiElements();
    isPrepared = false;

    if (context != null && TelecomUtil.isInManagedCall(context)) {
      handleError(new IllegalStateException("Cannot play voicemail when call is in progress"));
      return;
    }

    try {
      mediaPlayer = new MediaPlayer();
      mediaPlayer.setOnPreparedListener(this);
      mediaPlayer.setOnErrorListener(this);
      mediaPlayer.setOnCompletionListener(this);

      mediaPlayer.reset();
      mediaPlayer.setDataSource(context, voicemailUri);
      mediaPlayer.setAudioStreamType(VoicemailAudioManager.PLAYBACK_STREAM);
      mediaPlayer.prepareAsync();
    } catch (IOException e) {
      handleError(e);
    }
  }

  /**
   * Once the media player is prepared, enables the UI and adopts the appropriate playback state.
   */
  @Override
  public void onPrepared(MediaPlayer mp) {
    if (view == null || context == null) {
      return;
    }
    LogUtil.d("VoicemailPlaybackPresenter.onPrepared", null);
    isPrepared = true;

    duration.set(mediaPlayer.getDuration());

    LogUtil.d("VoicemailPlaybackPresenter.onPrepared", "mPosition=" + position);
    view.setClipPosition(position, duration.get());
    view.enableUiElements();
    view.setSuccess();
    if (!mp.isPlaying()) {
      mediaPlayer.seekTo(position);
    }

    if (isPlaying) {
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

    if (isPrepared) {
      mediaPlayer.release();
      mediaPlayer = null;
      isPrepared = false;
    }

    if (view != null) {
      view.onPlaybackError();
    }

    position = 0;
    isPlaying = false;
    showShareVoicemailButton(false);
  }

  /** After done playing the voicemail clip, reset the clip position to the start. */
  @Override
  public void onCompletion(MediaPlayer mediaPlayer) {
    pausePlayback();

    // Reset the seekbar position to the beginning.
    position = 0;
    if (view != null) {
      mediaPlayer.seekTo(0);
      view.setClipPosition(0, duration.get());
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
    if (isPlaying == gainedFocus) {
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
    if (view == null) {
      return;
    }

    if (!isPrepared) {
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
              isPlaying = requestContent(PLAYBACK_REQUEST);
            } else {
              showShareVoicemailButton(true);
              // Queue playing once the media play loaded the content.
              isPlaying = true;
              prepareContent();
            }
          });
      return;
    }

    isPlaying = true;

    activity.getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);

    if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
      // Clamp the start position between 0 and the duration.
      position = Math.max(0, Math.min(position, duration.get()));

      mediaPlayer.seekTo(position);

      try {
        // Grab audio focus.
        // Can throw RejectedExecutionException.
        voicemailAudioManager.requestAudioFocus();
        mediaPlayer.start();
        setSpeakerphoneOn(isSpeakerphoneOn);
        voicemailAudioManager.setSpeakerphoneOn(isSpeakerphoneOn);
      } catch (RejectedExecutionException e) {
        handleError(e);
      }
    }

    LogUtil.d("VoicemailPlaybackPresenter.resumePlayback", "resumed playback at %d.", position);
    view.onPlaybackStarted(duration.get(), getScheduledExecutorServiceInstance());
  }

  /** Pauses voicemail playback at the current position. Null-op if already paused. */
  public void pausePlayback() {
    pausePlayback(false);
  }

  private void pausePlayback(boolean keepFocus) {
    if (!isPrepared) {
      return;
    }

    isPlaying = false;

    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
      mediaPlayer.pause();
    }

    position = mediaPlayer == null ? 0 : mediaPlayer.getCurrentPosition();

    LogUtil.d("VoicemailPlaybackPresenter.pausePlayback", "paused playback at %d.", position);

    if (view != null) {
      view.onPlaybackStopped();
    }

    if (!keepFocus) {
      voicemailAudioManager.abandonAudioFocus();
    }
    if (activity != null) {
      activity.getWindow().clearFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    disableProximitySensor(true /* waitForFarState */);
  }

  /**
   * Pauses playback when the user starts seeking the position, and notes whether the voicemail is
   * playing to know whether to resume playback once the user selects a new position.
   */
  public void pausePlaybackForSeeking() {
    if (mediaPlayer != null) {
      shouldResumePlaybackAfterSeeking = mediaPlayer.isPlaying();
    }
    pausePlayback(true);
  }

  public void resumePlaybackAfterSeeking(int desiredPosition) {
    position = desiredPosition;
    if (shouldResumePlaybackAfterSeeking) {
      shouldResumePlaybackAfterSeeking = false;
      resumePlayback();
    }
  }

  /**
   * Seek to position. This is called when user manually seek the playback. It could be either by
   * touch or volume button while in talkback mode.
   */
  public void seek(int position) {
    this.position = position;
    mediaPlayer.seekTo(this.position);
  }

  private void enableProximitySensor() {
    if (proximityWakeLock == null
        || isSpeakerphoneOn
        || !isPrepared
        || mediaPlayer == null
        || !mediaPlayer.isPlaying()) {
      return;
    }

    if (!proximityWakeLock.isHeld()) {
      LogUtil.i(
          "VoicemailPlaybackPresenter.enableProximitySensor", "acquiring proximity wake lock");
      proximityWakeLock.acquire();
    } else {
      LogUtil.i(
          "VoicemailPlaybackPresenter.enableProximitySensor",
          "proximity wake lock already acquired");
    }
  }

  private void disableProximitySensor(boolean waitForFarState) {
    if (proximityWakeLock == null) {
      return;
    }
    if (proximityWakeLock.isHeld()) {
      LogUtil.i(
          "VoicemailPlaybackPresenter.disableProximitySensor", "releasing proximity wake lock");
      int flags = waitForFarState ? PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY : 0;
      proximityWakeLock.release(flags);
    } else {
      LogUtil.i(
          "VoicemailPlaybackPresenter.disableProximitySensor",
          "proximity wake lock already released");
    }
  }

  /** This is for use by UI interactions only. It simplifies UI logic. */
  public void toggleSpeakerphone() {
    voicemailAudioManager.setSpeakerphoneOn(!isSpeakerphoneOn);
    setSpeakerphoneOn(!isSpeakerphoneOn);
  }

  public void setOnVoicemailDeletedListener(OnVoicemailDeletedListener listener) {
    onVoicemailDeletedListener = listener;
  }

  public int getMediaPlayerPosition() {
    return isPrepared && mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
  }

  void onVoicemailDeleted(CallLogListItemViewHolder viewHolder) {
    if (onVoicemailDeletedListener != null) {
      onVoicemailDeletedListener.onVoicemailDeleted(viewHolder, voicemailUri);
    }
  }

  void onVoicemailDeleteUndo(int adapterPosition) {
    if (onVoicemailDeletedListener != null) {
      onVoicemailDeletedListener.onVoicemailDeleteUndo(rowId, adapterPosition, voicemailUri);
    }
  }

  void onVoicemailDeletedInDatabase() {
    if (onVoicemailDeletedListener != null) {
      onVoicemailDeletedListener.onVoicemailDeletedInDatabase(rowId, voicemailUri);
    }
  }

  @VisibleForTesting
  public boolean isPlaying() {
    return isPlaying;
  }

  @VisibleForTesting
  public boolean isSpeakerphoneOn() {
    return isSpeakerphoneOn;
  }

  /**
   * This method only handles app-level changes to the speakerphone. Audio layer changes should be
   * handled separately. This is so that the VoicemailAudioManager can trigger changes to the
   * presenter without the presenter triggering the audio manager and duplicating actions.
   */
  public void setSpeakerphoneOn(boolean on) {
    if (view == null) {
      return;
    }

    view.onSpeakerphoneOn(on);

    isSpeakerphoneOn = on;

    // This should run even if speakerphone is not being toggled because we may be switching
    // from earpiece to headphone and vise versa. Also upon initial setup the default audio
    // source is the earpiece, so we want to trigger the proximity sensor.
    if (isPlaying) {
      if (on || voicemailAudioManager.isWiredHeadsetPluggedIn()) {
        disableProximitySensor(false /* waitForFarState */);
      } else {
        enableProximitySensor();
      }
    }
  }

  @VisibleForTesting
  public void clearInstance() {
    instance = null;
  }

  private void showShareVoicemailButton(boolean show) {
    if (context == null) {
      return;
    }
    if (isShareVoicemailAllowed(context) && shareVoicemailButtonView != null) {
      if (show) {
        Logger.get(context).logImpression(DialerImpression.Type.VVM_SHARE_VISIBLE);
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
    shareVoicemailExecutor.executeParallel(new Pair<>(context, voicemailUri));
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

    private final Handler fetchResultHandler;
    private final Uri voicemailUri;
    private AtomicBoolean isWaitingForResult = new AtomicBoolean(true);

    public FetchResultHandler(Handler handler, Uri uri, int code) {
      super(handler);
      fetchResultHandler = handler;
      voicemailUri = uri;
      if (context != null) {
        if (PermissionsUtil.hasReadVoicemailPermissions(context)) {
          context.getContentResolver().registerContentObserver(voicemailUri, false, this);
        }
        fetchResultHandler.postDelayed(this, FETCH_CONTENT_TIMEOUT_MS);
      }
    }

    /** Stop waiting for content and notify UI if {@link FETCH_CONTENT_TIMEOUT_MS} has elapsed. */
    @Override
    public void run() {
      if (isWaitingForResult.getAndSet(false) && context != null) {
        context.getContentResolver().unregisterContentObserver(this);
        if (view != null) {
          view.setFetchContentTimeout();
        }
      }
    }

    public void destroy() {
      if (isWaitingForResult.getAndSet(false) && context != null) {
        context.getContentResolver().unregisterContentObserver(this);
        fetchResultHandler.removeCallbacks(this);
      }
    }

    @Override
    public void onChange(boolean selfChange) {
      asyncTaskExecutor.submit(
          Tasks.CHECK_CONTENT_AFTER_CHANGE,
          new AsyncTask<Void, Void, Boolean>() {

            @Override
            public Boolean doInBackground(Void... params) {
              return queryHasContent(voicemailUri);
            }

            @Override
            public void onPostExecute(Boolean hasContent) {
              if (hasContent && context != null && isWaitingForResult.getAndSet(false)) {
                context.getContentResolver().unregisterContentObserver(FetchResultHandler.this);
                showShareVoicemailButton(true);
                prepareContent();
              }
            }
          });
    }
  }
}
