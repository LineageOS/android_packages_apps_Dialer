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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.VisibleForTesting;
import android.support.design.widget.Snackbar;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import com.android.dialer.app.R;
import com.android.dialer.app.calllog.CallLogAsyncTaskUtil;
import com.android.dialer.app.calllog.CallLogListItemViewHolder;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Displays and plays a single voicemail. See {@link VoicemailPlaybackPresenter} for details on the
 * voicemail playback implementation.
 *
 * <p>This class is not thread-safe, it is thread-confined. All calls to all public methods on this
 * class are expected to come from the main ui thread.
 */
@NotThreadSafe
public class VoicemailPlaybackLayout extends LinearLayout
    implements VoicemailPlaybackPresenter.PlaybackView,
        CallLogAsyncTaskUtil.CallLogAsyncTaskListener {

  private static final String TAG = VoicemailPlaybackLayout.class.getSimpleName();
  private static final int VOICEMAIL_DELETE_DELAY_MS = 3000;

  private Context context;
  private CallLogListItemViewHolder viewHolder;
  private VoicemailPlaybackPresenter presenter;
  /** Click listener to toggle speakerphone. */
  private final View.OnClickListener speakerphoneListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          if (presenter != null) {
            presenter.toggleSpeakerphone();
          }
        }
      };

  private Uri voicemailUri;
  private final View.OnClickListener deleteButtonListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          Logger.get(context).logImpression(DialerImpression.Type.VOICEMAIL_DELETE_ENTRY);
          if (presenter == null) {
            return;
          }

          // When the undo button is pressed, the viewHolder we have is no longer valid because when
          // we hide the view it is binded to something else, and the layout is not updated for
          // hidden items. copy the adapter position so we can update the view upon undo.
          // TODO(twyen): refactor this so the view holder will always be valid.
          final int adapterPosition = viewHolder.getAdapterPosition();

          presenter.pausePlayback();
          presenter.onVoicemailDeleted(viewHolder);

          final Uri deleteUri = voicemailUri;
          final Runnable deleteCallback =
              new Runnable() {
                @Override
                public void run() {
                  if (Objects.equals(deleteUri, voicemailUri)) {
                    CallLogAsyncTaskUtil.deleteVoicemail(
                        context, deleteUri, VoicemailPlaybackLayout.this);
                  }
                }
              };

          final Handler handler = new Handler();
          // Add a little buffer time in case the user clicked "undo" at the end of the delay
          // window.
          handler.postDelayed(deleteCallback, VOICEMAIL_DELETE_DELAY_MS + 50);

          Snackbar.make(
                  VoicemailPlaybackLayout.this,
                  R.string.snackbar_voicemail_deleted,
                  Snackbar.LENGTH_LONG)
              .setDuration(VOICEMAIL_DELETE_DELAY_MS)
              .setAction(
                  R.string.snackbar_voicemail_deleted_undo,
                  new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                      presenter.onVoicemailDeleteUndo(adapterPosition);
                      handler.removeCallbacks(deleteCallback);
                    }
                  })
              .setActionTextColor(
                  context.getResources().getColor(R.color.dialer_snackbar_action_text_color))
              .show();
        }
      };
  private boolean isPlaying = false;
  /** Click listener to play or pause voicemail playback. */
  private final View.OnClickListener startStopButtonListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          if (presenter == null) {
            return;
          }

          if (isPlaying) {
            presenter.pausePlayback();
          } else {
            Logger.get(context)
                .logImpression(DialerImpression.Type.VOICEMAIL_PLAY_AUDIO_AFTER_EXPANDING_ENTRY);
            presenter.resumePlayback();
          }
        }
      };

  private SeekBar playbackSeek;
  private ImageButton startStopButton;
  private ImageButton playbackSpeakerphone;
  private ImageButton deleteButton;
  private TextView stateText;
  private TextView positionText;
  private TextView totalDurationText;
  /** Handle state changes when the user manipulates the seek bar. */
  private final OnSeekBarChangeListener seekBarChangeListener =
      new OnSeekBarChangeListener() {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
          if (presenter != null) {
            presenter.pausePlaybackForSeeking();
          }
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
          if (presenter != null) {
            presenter.resumePlaybackAfterSeeking(seekBar.getProgress());
          }
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
          setClipPosition(progress, seekBar.getMax());
          // Update the seek position if user manually changed it. This makes sure position gets
          // updated when user use volume button to seek playback in talkback mode.
          if (fromUser) {
            presenter.seek(progress);
          }
        }
      };

  private PositionUpdater positionUpdater;
  private Drawable voicemailSeekHandleEnabled;
  private Drawable voicemailSeekHandleDisabled;

  public VoicemailPlaybackLayout(Context context) {
    this(context, null);
  }

  public VoicemailPlaybackLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
    LayoutInflater inflater =
        (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.voicemail_playback_layout, this);
  }

  public void setViewHolder(CallLogListItemViewHolder mViewHolder) {
    this.viewHolder = mViewHolder;
  }

  @Override
  public void setPresenter(VoicemailPlaybackPresenter presenter, Uri voicemailUri) {
    this.presenter = presenter;
    this.voicemailUri = voicemailUri;
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    playbackSeek = (SeekBar) findViewById(R.id.playback_seek);
    startStopButton = (ImageButton) findViewById(R.id.playback_start_stop);
    playbackSpeakerphone = (ImageButton) findViewById(R.id.playback_speakerphone);
    deleteButton = (ImageButton) findViewById(R.id.delete_voicemail);

    stateText = (TextView) findViewById(R.id.playback_state_text);
    stateText.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
    positionText = (TextView) findViewById(R.id.playback_position_text);
    totalDurationText = (TextView) findViewById(R.id.total_duration_text);

    playbackSeek.setOnSeekBarChangeListener(seekBarChangeListener);
    startStopButton.setOnClickListener(startStopButtonListener);
    playbackSpeakerphone.setOnClickListener(speakerphoneListener);
    deleteButton.setOnClickListener(deleteButtonListener);

    positionText.setText(formatAsMinutesAndSeconds(0));
    totalDurationText.setText(formatAsMinutesAndSeconds(0));

    voicemailSeekHandleEnabled =
        getResources().getDrawable(R.drawable.ic_voicemail_seek_handle, context.getTheme());
    voicemailSeekHandleDisabled =
        getResources()
            .getDrawable(R.drawable.ic_voicemail_seek_handle_disabled, context.getTheme());
  }

  @Override
  public void onPlaybackStarted(int duration, ScheduledExecutorService executorService) {
    isPlaying = true;

    startStopButton.setImageResource(R.drawable.ic_pause);

    if (positionUpdater != null) {
      positionUpdater.stopUpdating();
      positionUpdater = null;
    }
    positionUpdater = new PositionUpdater(duration, executorService);
    positionUpdater.startUpdating();
  }

  @Override
  public void onPlaybackStopped() {
    isPlaying = false;

    startStopButton.setImageResource(R.drawable.ic_play_arrow);

    if (positionUpdater != null) {
      positionUpdater.stopUpdating();
      positionUpdater = null;
    }
  }

  @Override
  public void onPlaybackError() {
    if (positionUpdater != null) {
      positionUpdater.stopUpdating();
    }

    disableUiElements();
    stateText.setText(getString(R.string.voicemail_playback_error));
  }

  @Override
  public void onSpeakerphoneOn(boolean on) {
    if (on) {
      playbackSpeakerphone.setImageResource(R.drawable.quantum_ic_volume_up_white_24);
      // Speaker is now on, tapping button will turn it off.
      playbackSpeakerphone.setContentDescription(context.getString(R.string.voicemail_speaker_off));
    } else {
      playbackSpeakerphone.setImageResource(R.drawable.quantum_ic_volume_down_white_24);
      // Speaker is now off, tapping button will turn it on.
      playbackSpeakerphone.setContentDescription(context.getString(R.string.voicemail_speaker_on));
    }
  }

  @Override
  public void setClipPosition(int positionMs, int durationMs) {
    int seekBarPositionMs = Math.max(0, positionMs);
    int seekBarMax = Math.max(seekBarPositionMs, durationMs);
    if (playbackSeek.getMax() != seekBarMax) {
      playbackSeek.setMax(seekBarMax);
    }

    playbackSeek.setProgress(seekBarPositionMs);

    positionText.setText(formatAsMinutesAndSeconds(seekBarPositionMs));
    totalDurationText.setText(formatAsMinutesAndSeconds(durationMs));
  }

  @Override
  public void setSuccess() {
    stateText.setText(null);
  }

  @Override
  public void setIsFetchingContent() {
    disableUiElements();
    stateText.setText(getString(R.string.voicemail_fetching_content));
  }

  @Override
  public void setFetchContentTimeout() {
    startStopButton.setEnabled(true);
    stateText.setText(getString(R.string.voicemail_fetching_timout));
  }

  @Override
  public int getDesiredClipPosition() {
    return playbackSeek.getProgress();
  }

  @Override
  public void disableUiElements() {
    startStopButton.setEnabled(false);
    resetSeekBar();
  }

  @Override
  public void enableUiElements() {
    deleteButton.setEnabled(true);
    startStopButton.setEnabled(true);
    playbackSeek.setEnabled(true);
    playbackSeek.setThumb(voicemailSeekHandleEnabled);
  }

  @Override
  public void resetSeekBar() {
    playbackSeek.setProgress(0);
    playbackSeek.setEnabled(false);
    playbackSeek.setThumb(voicemailSeekHandleDisabled);
  }

  @Override
  public void onDeleteVoicemail() {
    presenter.onVoicemailDeletedInDatabase();
  }

  private String getString(int resId) {
    return context.getString(resId);
  }

  /**
   * Formats a number of milliseconds as something that looks like {@code 00:05}.
   *
   * <p>We always use four digits, two for minutes two for seconds. In the very unlikely event that
   * the voicemail duration exceeds 99 minutes, the display is capped at 99 minutes.
   */
  private String formatAsMinutesAndSeconds(int millis) {
    int seconds = millis / 1000;
    int minutes = seconds / 60;
    seconds -= minutes * 60;
    if (minutes > 99) {
      minutes = 99;
    }
    return String.format("%02d:%02d", minutes, seconds);
  }

  @VisibleForTesting
  public String getStateText() {
    return stateText.getText().toString();
  }

  /** Controls the animation of the playback slider. */
  @ThreadSafe
  private final class PositionUpdater implements Runnable {

    /** Update rate for the slider, 30fps. */
    private static final int SLIDER_UPDATE_PERIOD_MILLIS = 1000 / 30;

    private final ScheduledExecutorService executorService;
    private final Object lock = new Object();
    private int durationMs;

    @GuardedBy("lock")
    private ScheduledFuture<?> scheduledFuture;

    private Runnable updateClipPositionRunnable =
        new Runnable() {
          @Override
          public void run() {
            int currentPositionMs = 0;
            synchronized (lock) {
              if (scheduledFuture == null || presenter == null) {
                // This task has been canceled. Just stop now.
                return;
              }
              currentPositionMs = presenter.getMediaPlayerPosition();
            }
            setClipPosition(currentPositionMs, durationMs);
          }
        };

    public PositionUpdater(int durationMs, ScheduledExecutorService executorService) {
      this.durationMs = durationMs;
      this.executorService = executorService;
    }

    @Override
    public void run() {
      post(updateClipPositionRunnable);
    }

    public void startUpdating() {
      synchronized (lock) {
        cancelPendingRunnables();
        scheduledFuture =
            executorService.scheduleAtFixedRate(
                this, 0, SLIDER_UPDATE_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
      }
    }

    public void stopUpdating() {
      synchronized (lock) {
        cancelPendingRunnables();
      }
    }

    @GuardedBy("lock")
    private void cancelPendingRunnables() {
      if (scheduledFuture != null) {
        scheduledFuture.cancel(true);
        scheduledFuture = null;
      }
      removeCallbacks(updateClipPositionRunnable);
    }
  }
}
