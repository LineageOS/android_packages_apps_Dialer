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

  private Context mContext;
  private CallLogListItemViewHolder mViewHolder;
  private VoicemailPlaybackPresenter mPresenter;
  /** Click listener to toggle speakerphone. */
  private final View.OnClickListener mSpeakerphoneListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          if (mPresenter != null) {
            mPresenter.toggleSpeakerphone();
          }
        }
      };

  private Uri mVoicemailUri;
  private final View.OnClickListener mDeleteButtonListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          Logger.get(mContext).logImpression(DialerImpression.Type.VOICEMAIL_DELETE_ENTRY);
          if (mPresenter == null) {
            return;
          }

          // When the undo button is pressed, the viewHolder we have is no longer valid because when
          // we hide the view it is binded to something else, and the layout is not updated for
          // hidden items. copy the adapter position so we can update the view upon undo.
          // TODO: refactor this so the view holder will always be valid.
          final int adapterPosition = mViewHolder.getAdapterPosition();

          mPresenter.pausePlayback();
          mPresenter.onVoicemailDeleted(mViewHolder);

          final Uri deleteUri = mVoicemailUri;
          final Runnable deleteCallback =
              new Runnable() {
                @Override
                public void run() {
                  if (Objects.equals(deleteUri, mVoicemailUri)) {
                    CallLogAsyncTaskUtil.deleteVoicemail(
                        mContext, deleteUri, VoicemailPlaybackLayout.this);
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
                      mPresenter.onVoicemailDeleteUndo(adapterPosition);
                      handler.removeCallbacks(deleteCallback);
                    }
                  })
              .setActionTextColor(
                  mContext.getResources().getColor(R.color.dialer_snackbar_action_text_color))
              .show();
        }
      };
  private boolean mIsPlaying = false;
  /** Click listener to play or pause voicemail playback. */
  private final View.OnClickListener mStartStopButtonListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          if (mPresenter == null) {
            return;
          }

          if (mIsPlaying) {
            mPresenter.pausePlayback();
          } else {
            Logger.get(mContext)
                .logImpression(DialerImpression.Type.VOICEMAIL_PLAY_AUDIO_AFTER_EXPANDING_ENTRY);
            mPresenter.resumePlayback();
          }
        }
      };

  private SeekBar mPlaybackSeek;
  private ImageButton mStartStopButton;
  private ImageButton mPlaybackSpeakerphone;
  private ImageButton mDeleteButton;
  private TextView mStateText;
  private TextView mPositionText;
  private TextView mTotalDurationText;
  /** Handle state changes when the user manipulates the seek bar. */
  private final OnSeekBarChangeListener mSeekBarChangeListener =
      new OnSeekBarChangeListener() {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
          if (mPresenter != null) {
            mPresenter.pausePlaybackForSeeking();
          }
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
          if (mPresenter != null) {
            mPresenter.resumePlaybackAfterSeeking(seekBar.getProgress());
          }
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
          setClipPosition(progress, seekBar.getMax());
          // Update the seek position if user manually changed it. This makes sure position gets
          // updated when user use volume button to seek playback in talkback mode.
          if (fromUser) {
            mPresenter.seek(progress);
          }
        }
      };

  private PositionUpdater mPositionUpdater;
  private Drawable mVoicemailSeekHandleEnabled;
  private Drawable mVoicemailSeekHandleDisabled;

  public VoicemailPlaybackLayout(Context context) {
    this(context, null);
  }

  public VoicemailPlaybackLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
    mContext = context;
    LayoutInflater inflater =
        (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.voicemail_playback_layout, this);
  }

  public void setViewHolder(CallLogListItemViewHolder mViewHolder) {
    this.mViewHolder = mViewHolder;
  }

  @Override
  public void setPresenter(VoicemailPlaybackPresenter presenter, Uri voicemailUri) {
    mPresenter = presenter;
    mVoicemailUri = voicemailUri;
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    mPlaybackSeek = (SeekBar) findViewById(R.id.playback_seek);
    mStartStopButton = (ImageButton) findViewById(R.id.playback_start_stop);
    mPlaybackSpeakerphone = (ImageButton) findViewById(R.id.playback_speakerphone);
    mDeleteButton = (ImageButton) findViewById(R.id.delete_voicemail);

    mStateText = (TextView) findViewById(R.id.playback_state_text);
    mStateText.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
    mPositionText = (TextView) findViewById(R.id.playback_position_text);
    mTotalDurationText = (TextView) findViewById(R.id.total_duration_text);

    mPlaybackSeek.setOnSeekBarChangeListener(mSeekBarChangeListener);
    mStartStopButton.setOnClickListener(mStartStopButtonListener);
    mPlaybackSpeakerphone.setOnClickListener(mSpeakerphoneListener);
    mDeleteButton.setOnClickListener(mDeleteButtonListener);

    mPositionText.setText(formatAsMinutesAndSeconds(0));
    mTotalDurationText.setText(formatAsMinutesAndSeconds(0));

    mVoicemailSeekHandleEnabled =
        getResources().getDrawable(R.drawable.ic_voicemail_seek_handle, mContext.getTheme());
    mVoicemailSeekHandleDisabled =
        getResources()
            .getDrawable(R.drawable.ic_voicemail_seek_handle_disabled, mContext.getTheme());
  }

  @Override
  public void onPlaybackStarted(int duration, ScheduledExecutorService executorService) {
    mIsPlaying = true;

    mStartStopButton.setImageResource(R.drawable.ic_pause);

    if (mPositionUpdater != null) {
      mPositionUpdater.stopUpdating();
      mPositionUpdater = null;
    }
    mPositionUpdater = new PositionUpdater(duration, executorService);
    mPositionUpdater.startUpdating();
  }

  @Override
  public void onPlaybackStopped() {
    mIsPlaying = false;

    mStartStopButton.setImageResource(R.drawable.ic_play_arrow);

    if (mPositionUpdater != null) {
      mPositionUpdater.stopUpdating();
      mPositionUpdater = null;
    }
  }

  @Override
  public void onPlaybackError() {
    if (mPositionUpdater != null) {
      mPositionUpdater.stopUpdating();
    }

    disableUiElements();
    mStateText.setText(getString(R.string.voicemail_playback_error));
  }

  @Override
  public void onSpeakerphoneOn(boolean on) {
    if (on) {
      mPlaybackSpeakerphone.setImageResource(R.drawable.quantum_ic_volume_up_white_24);
      // Speaker is now on, tapping button will turn it off.
      mPlaybackSpeakerphone.setContentDescription(
          mContext.getString(R.string.voicemail_speaker_off));
    } else {
      mPlaybackSpeakerphone.setImageResource(R.drawable.quantum_ic_volume_down_white_24);
      // Speaker is now off, tapping button will turn it on.
      mPlaybackSpeakerphone.setContentDescription(
          mContext.getString(R.string.voicemail_speaker_on));
    }
  }

  @Override
  public void setClipPosition(int positionMs, int durationMs) {
    int seekBarPositionMs = Math.max(0, positionMs);
    int seekBarMax = Math.max(seekBarPositionMs, durationMs);
    if (mPlaybackSeek.getMax() != seekBarMax) {
      mPlaybackSeek.setMax(seekBarMax);
    }

    mPlaybackSeek.setProgress(seekBarPositionMs);

    mPositionText.setText(formatAsMinutesAndSeconds(seekBarPositionMs));
    mTotalDurationText.setText(formatAsMinutesAndSeconds(durationMs));
  }

  @Override
  public void setSuccess() {
    mStateText.setText(null);
  }

  @Override
  public void setIsFetchingContent() {
    disableUiElements();
    mStateText.setText(getString(R.string.voicemail_fetching_content));
  }

  @Override
  public void setFetchContentTimeout() {
    mStartStopButton.setEnabled(true);
    mStateText.setText(getString(R.string.voicemail_fetching_timout));
  }

  @Override
  public int getDesiredClipPosition() {
    return mPlaybackSeek.getProgress();
  }

  @Override
  public void disableUiElements() {
    mStartStopButton.setEnabled(false);
    resetSeekBar();
  }

  @Override
  public void enableUiElements() {
    mDeleteButton.setEnabled(true);
    mStartStopButton.setEnabled(true);
    mPlaybackSeek.setEnabled(true);
    mPlaybackSeek.setThumb(mVoicemailSeekHandleEnabled);
  }

  @Override
  public void resetSeekBar() {
    mPlaybackSeek.setProgress(0);
    mPlaybackSeek.setEnabled(false);
    mPlaybackSeek.setThumb(mVoicemailSeekHandleDisabled);
  }

  @Override
  public void onDeleteVoicemail() {
    mPresenter.onVoicemailDeletedInDatabase();
  }

  private String getString(int resId) {
    return mContext.getString(resId);
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
    return mStateText.getText().toString();
  }

  /** Controls the animation of the playback slider. */
  @ThreadSafe
  private final class PositionUpdater implements Runnable {

    /** Update rate for the slider, 30fps. */
    private static final int SLIDER_UPDATE_PERIOD_MILLIS = 1000 / 30;

    private final ScheduledExecutorService mExecutorService;
    private final Object mLock = new Object();
    private int mDurationMs;

    @GuardedBy("mLock")
    private ScheduledFuture<?> mScheduledFuture;

    private Runnable mUpdateClipPositionRunnable =
        new Runnable() {
          @Override
          public void run() {
            int currentPositionMs = 0;
            synchronized (mLock) {
              if (mScheduledFuture == null || mPresenter == null) {
                // This task has been canceled. Just stop now.
                return;
              }
              currentPositionMs = mPresenter.getMediaPlayerPosition();
            }
            setClipPosition(currentPositionMs, mDurationMs);
          }
        };

    public PositionUpdater(int durationMs, ScheduledExecutorService executorService) {
      mDurationMs = durationMs;
      mExecutorService = executorService;
    }

    @Override
    public void run() {
      post(mUpdateClipPositionRunnable);
    }

    public void startUpdating() {
      synchronized (mLock) {
        cancelPendingRunnables();
        mScheduledFuture =
            mExecutorService.scheduleAtFixedRate(
                this, 0, SLIDER_UPDATE_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
      }
    }

    public void stopUpdating() {
      synchronized (mLock) {
        cancelPendingRunnables();
      }
    }

    @GuardedBy("mLock")
    private void cancelPendingRunnables() {
      if (mScheduledFuture != null) {
        mScheduledFuture.cancel(true);
        mScheduledFuture = null;
      }
      removeCallbacks(mUpdateClipPositionRunnable);
    }
  }
}
