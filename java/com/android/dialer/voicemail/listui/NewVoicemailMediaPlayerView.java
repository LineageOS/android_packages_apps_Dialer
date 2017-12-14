/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.dialer.voicemail.listui;

import android.app.FragmentManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.VoicemailContract;
import android.provider.VoicemailContract.Voicemails;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.SuccessListener;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.voicemail.listui.NewVoicemailViewHolder.NewVoicemailViewHolderListener;
import com.android.dialer.voicemail.model.VoicemailEntry;
import java.util.Locale;

/**
 * The view of the media player that is visible when a {@link NewVoicemailViewHolder} is expanded.
 */
public final class NewVoicemailMediaPlayerView extends LinearLayout {

  private ImageButton playButton;
  private ImageButton pauseButton;
  private ImageButton speakerButton;
  private ImageButton phoneButton;
  private ImageButton deleteButton;
  private TextView currentSeekBarPosition;
  private SeekBar seekBarView;
  private TextView totalDurationView;
  private TextView voicemailLoadingStatusView;
  private Uri voicemailUri;
  private FragmentManager fragmentManager;
  private NewVoicemailViewHolder newVoicemailViewHolder;
  private NewVoicemailMediaPlayer mediaPlayer;
  private NewVoicemailViewHolderListener newVoicemailViewHolderListener;

  public NewVoicemailMediaPlayerView(Context context, AttributeSet attrs) {
    super(context, attrs);
    LogUtil.enterBlock("NewVoicemailMediaPlayer");
    LayoutInflater inflater =
        (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.new_voicemail_media_player_layout, this);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    LogUtil.enterBlock("NewVoicemailMediaPlayer.onFinishInflate");
    initializeMediaPlayerButtonsAndViews();
    setupListenersForMediaPlayerButtons();
  }

  private void initializeMediaPlayerButtonsAndViews() {
    playButton = findViewById(R.id.playButton);
    pauseButton = findViewById(R.id.pauseButton);
    currentSeekBarPosition = findViewById(R.id.playback_position_text);
    seekBarView = findViewById(R.id.playback_seek);
    speakerButton = findViewById(R.id.speakerButton);
    phoneButton = findViewById(R.id.phoneButton);
    deleteButton = findViewById(R.id.deleteButton);
    totalDurationView = findViewById(R.id.playback_seek_total_duration);
    voicemailLoadingStatusView = findViewById(R.id.playback_state_text);
  }

  private void setupListenersForMediaPlayerButtons() {
    playButton.setOnClickListener(playButtonListener);
    pauseButton.setOnClickListener(pauseButtonListener);
    seekBarView.setOnSeekBarChangeListener(seekbarChangeListener);
    speakerButton.setOnClickListener(speakerButtonListener);
    phoneButton.setOnClickListener(phoneButtonListener);
    deleteButton.setOnClickListener(deleteButtonListener);
  }

  public void reset() {
    LogUtil.i("NewVoicemailMediaPlayer.reset", "the uri for this is " + voicemailUri);
    voicemailUri = null;
    voicemailLoadingStatusView.setVisibility(GONE);
  }

  /**
   * Can be called either when binding happens on the {@link NewVoicemailViewHolder} from {@link
   * NewVoicemailAdapter} or when a user expands a {@link NewVoicemailViewHolder}. During the
   * binding, since {@link NewVoicemailMediaPlayerView} is part of {@link NewVoicemailViewHolder},
   * we have to ensure that during the binding the values from the {@link NewVoicemailAdapter} are
   * also propogated down to the {@link NewVoicemailMediaPlayerView} via {@link
   * NewVoicemailViewHolder}. In the case of when the {@link NewVoicemailViewHolder} is expanded,
   * the most recent value and states from the {@link NewVoicemailAdapter} are set for the expanded
   * {@link NewVoicemailMediaPlayerView}.
   *
   * @param viewHolder
   * @param voicemailEntryFromAdapter are the voicemail related values from the {@link
   *     AnnotatedCallLog} converted into {@link VoicemailEntry} format.
   * @param fragmentManager
   * @param mp the media player passed down from the adapter
   * @param listener
   */
  void bindValuesFromAdapterOfExpandedViewHolderMediaPlayerView(
      NewVoicemailViewHolder viewHolder,
      @NonNull VoicemailEntry voicemailEntryFromAdapter,
      @NonNull FragmentManager fragmentManager,
      NewVoicemailMediaPlayer mp,
      NewVoicemailViewHolderListener listener) {

    Assert.isNotNull(voicemailEntryFromAdapter);
    Uri uri = Uri.parse(voicemailEntryFromAdapter.voicemailUri());
    Assert.isNotNull(viewHolder);
    Assert.isNotNull(uri);
    Assert.isNotNull(listener);
    Assert.isNotNull(totalDurationView);
    Assert.checkArgument(uri.equals(viewHolder.getViewHolderVoicemailUri()));

    LogUtil.i(
        "NewVoicemailMediaPlayerView.bindValuesFromAdapterOfExpandedViewHolderMediaPlayerView",
        "Updating the viewholder:%d mediaPlayerView with uri value:%s",
        viewHolder.getViewHolderId(),
        uri.toString());

    this.fragmentManager = fragmentManager;

    newVoicemailViewHolder = viewHolder;
    newVoicemailViewHolderListener = listener;
    mediaPlayer = mp;
    voicemailUri = uri;
    totalDurationView.setText(
        VoicemailEntryText.getVoicemailDuration(getContext(), voicemailEntryFromAdapter));
    // Not sure if these are needed, but it'll ensure that onInflate() has atleast happened.
    initializeMediaPlayerButtonsAndViews();
    setupListenersForMediaPlayerButtons();

    // During the binding we only send a request to the adapter to tell us what the
    // state of the media player should be and call that function.
    // This could be the paused state, or the playing state of the resume state.
    // Our job here is only to send the request upto the adapter and have it decide what we should
    // do.
    LogUtil.i(
        "NewVoicemailMediaPlayerView.bindValuesFromAdapterOfExpandedViewHolderMediaPlayerView",
        "Updating media player values for id:" + viewHolder.getViewHolderId());

    // During the binding make sure that the first time we just set the mediaplayer view
    // This does not take care of the constant update
    if (mp.isPlaying() && mp.getLastPlayedOrPlayingVoicemailUri().equals(voicemailUri)) {
      Assert.checkArgument(
          mp.getLastPlayedOrPlayingVoicemailUri()
              .equals(mp.getLastPreparedOrPreparingToPlayVoicemailUri()));
      LogUtil.i(
          "NewVoicemailMediaPlayerView.bindValuesFromAdapterOfExpandedViewHolderMediaPlayerView",
          "show playing state");
      playButton.setVisibility(GONE);
      pauseButton.setVisibility(VISIBLE);
      currentSeekBarPosition.setText(formatAsMinutesAndSeconds(mp.getCurrentPosition()));

      if (seekBarView.getMax() != mp.getDuration()) {
        seekBarView.setMax(mp.getDuration());
      }
      seekBarView.setProgress(mp.getCurrentPosition());

    } else if (mediaPlayer.isPaused() && mp.getLastPausedVoicemailUri().equals(voicemailUri)) {
      LogUtil.i(
          "NewVoicemailMediaPlayerView.bindValuesFromAdapterOfExpandedViewHolderMediaPlayerView",
          "show paused state");
      Assert.checkArgument(viewHolder.getViewHolderVoicemailUri().equals(voicemailUri));
      playButton.setVisibility(VISIBLE);
      pauseButton.setVisibility(GONE);
      currentSeekBarPosition.setText(formatAsMinutesAndSeconds(mp.getCurrentPosition()));
      if (seekBarView.getMax() != mp.getDuration()) {
        seekBarView.setMax(mp.getDuration());
      }
      seekBarView.setProgress(mp.getCurrentPosition());

    } else {
      LogUtil.i(
          "NewVoicemailMediaPlayerView.bindValuesFromAdapterOfExpandedViewHolderMediaPlayerView",
          "show reset state");
      playButton.setVisibility(VISIBLE);
      pauseButton.setVisibility(GONE);
      seekBarView.setProgress(0);
      seekBarView.setMax(100);
      currentSeekBarPosition.setText(formatAsMinutesAndSeconds(0));
    }
  }

  private final OnSeekBarChangeListener seekbarChangeListener =
      new OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBarfromProgress, int progress, boolean fromUser) {
          // TODO(uabdullah): Only for debugging purposes, to be removed.
          if (progress < 100) {
            LogUtil.i(
                "NewVoicemailMediaPlayer.seekbarChangeListener",
                "onProgressChanged, progress:%d, seekbarMax: %d, fromUser:%b",
                progress,
                seekBarfromProgress.getMax(),
                fromUser);
          }

          if (fromUser) {
            mediaPlayer.seekTo(progress);
            currentSeekBarPosition.setText(formatAsMinutesAndSeconds(progress));
          }
        }

        @Override
        // TODO(uabdullah): Handle this case
        public void onStartTrackingTouch(SeekBar seekBar) {
          LogUtil.i("NewVoicemailMediaPlayer.onStartTrackingTouch", "does nothing for now");
        }

        @Override
        // TODO(uabdullah): Handle this case
        public void onStopTrackingTouch(SeekBar seekBar) {
          LogUtil.i("NewVoicemailMediaPlayer.onStopTrackingTouch", "does nothing for now");
        }
      };

  private final View.OnClickListener pauseButtonListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          LogUtil.i(
              "NewVoicemailMediaPlayer.pauseButtonListener",
              "pauseMediaPlayerAndSetPausedStateOfViewHolder button for voicemailUri: %s",
              voicemailUri.toString());

          Assert.checkArgument(playButton.getVisibility() == GONE);
          Assert.checkArgument(mediaPlayer != null);
          Assert.checkArgument(
              mediaPlayer.getLastPlayedOrPlayingVoicemailUri().equals((voicemailUri)),
              "the voicemail being played is the only voicemail that should"
                  + " be paused. last played voicemail:%s, uri:%s",
              mediaPlayer.getLastPlayedOrPlayingVoicemailUri().toString(),
              voicemailUri.toString());
          Assert.checkArgument(
              newVoicemailViewHolder.getViewHolderVoicemailUri().equals(voicemailUri),
              "viewholder uri and mediaplayer view should be the same.");
          newVoicemailViewHolderListener.pauseViewHolder(newVoicemailViewHolder);
        }
      };

  /**
   * Attempts to imitate clicking the play button. This is useful for when we the user attempted to
   * play a voicemail, but the media player didn't start playing till the voicemail was downloaded
   * from the server. However once we have the voicemail downloaded, we want to start playing, so as
   * to make it seem like that this is a continuation of the users initial play button click.
   */
  public final void clickPlayButton() {
    playButtonListener.onClick(null);
  }

  private final View.OnClickListener playButtonListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          LogUtil.i(
              "NewVoicemailMediaPlayer.playButtonListener",
              "play button for voicemailUri: %s",
              String.valueOf(voicemailUri));

          if (mediaPlayer.getLastPausedVoicemailUri() != null
              && mediaPlayer
                  .getLastPausedVoicemailUri()
                  .toString()
                  .contentEquals(voicemailUri.toString())) {
            LogUtil.i(
                "NewVoicemailMediaPlayer.playButtonListener",
                "resume playing voicemailUri: %s",
                voicemailUri.toString());

            newVoicemailViewHolderListener.resumePausedViewHolder(newVoicemailViewHolder);

          } else {
            playVoicemailWhenAvailableLocally();
          }
        }
      };

  /**
   * Plays the voicemail when we are able to play the voicemail locally from the device. This
   * involves checking if the voicemail is available to play locally, if it is, then we setup the
   * Media Player to play the voicemail. If the voicemail is not available, then we need download
   * the voicemail from the voicemail server to the device, and then have the Media player play it.
   */
  private void playVoicemailWhenAvailableLocally() {
    LogUtil.enterBlock("playVoicemailWhenAvailableLocally");
    Worker<Pair<Context, Uri>, Pair<Boolean, Uri>> checkVoicemailHasContent =
        this::queryVoicemailHasContent;
    SuccessListener<Pair<Boolean, Uri>> checkVoicemailHasContentCallBack = this::prepareMediaPlayer;

    DialerExecutorComponent.get(getContext())
        .dialerExecutorFactory()
        .createUiTaskBuilder(fragmentManager, "lookup_voicemail_content", checkVoicemailHasContent)
        .onSuccess(checkVoicemailHasContentCallBack)
        .build()
        .executeSerial(new Pair<>(getContext(), voicemailUri));
  }

  private Pair<Boolean, Uri> queryVoicemailHasContent(Pair<Context, Uri> contextUriPair) {
    Context context = contextUriPair.first;
    Uri uri = contextUriPair.second;

    try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return new Pair<>(
            cursor.getInt(cursor.getColumnIndex(VoicemailContract.Voicemails.HAS_CONTENT)) == 1,
            uri);
      }
      return new Pair<>(false, uri);
    }
  }

  /**
   * If the voicemail is available to play locally, setup the media player to play it. Otherwise
   * send a request to download the voicemail and then play it.
   */
  private void prepareMediaPlayer(Pair<Boolean, Uri> booleanUriPair) {
    boolean voicemailAvailableLocally = booleanUriPair.first;
    Uri uri = booleanUriPair.second;
    LogUtil.i(
        "NewVoicemailMediaPlayer.prepareMediaPlayer",
        "voicemail available locally: %b for voicemailUri: %s",
        voicemailAvailableLocally,
        uri.toString());

    if (voicemailAvailableLocally) {
      try {
        Assert.checkArgument(mediaPlayer != null, "media player should not have been null");
        mediaPlayer.prepareMediaPlayerAndPlayVoicemailWhenReady(getContext(), uri);
      } catch (Exception e) {
        LogUtil.e(
            "NewVoicemailMediaPlayer.prepareMediaPlayer",
            "Exception when mediaPlayer.prepareMediaPlayerAndPlayVoicemailWhenReady"
                + "(getContext(), uri)\n"
                + e
                + "\n uri:"
                + uri
                + "context should not be null, its value is :"
                + getContext());
      }
    } else {
      LogUtil.i(
          "NewVoicemailMediaPlayer.prepareVoicemailForMediaPlayer", "need to download content");
      // Important to set since it allows the adapter to differentiate when to start playing the
      // voicemail, after it's downloaded.
      mediaPlayer.setVoicemailRequestedToDownload(uri);
      voicemailLoadingStatusView.setVisibility(VISIBLE);
      sendIntentToDownloadVoicemail(uri);
    }
  }

  private void sendIntentToDownloadVoicemail(Uri uri) {
    LogUtil.i("NewVoicemailMediaPlayer.sendIntentToDownloadVoicemail", "uri:%s", uri.toString());

    Worker<Pair<Context, Uri>, Pair<String, Uri>> getVoicemailSourcePackage =
        this::queryVoicemailSourcePackage;
    SuccessListener<Pair<String, Uri>> checkVoicemailHasSourcePackageCallBack = this::sendIntent;

    DialerExecutorComponent.get(getContext())
        .dialerExecutorFactory()
        .createUiTaskBuilder(fragmentManager, "lookup_voicemail_pkg", getVoicemailSourcePackage)
        .onSuccess(checkVoicemailHasSourcePackageCallBack)
        .build()
        .executeSerial(new Pair<>(getContext(), voicemailUri));
  }

  private void sendIntent(Pair<String, Uri> booleanUriPair) {
    String sourcePackage = booleanUriPair.first;
    Uri uri = booleanUriPair.second;
    LogUtil.i(
        "NewVoicemailMediaPlayer.sendIntent",
        "srcPkg:%s, uri:%s",
        sourcePackage,
        String.valueOf(uri));
    Intent intent = new Intent(VoicemailContract.ACTION_FETCH_VOICEMAIL, uri);
    intent.setPackage(sourcePackage);
    voicemailLoadingStatusView.setVisibility(VISIBLE);
    getContext().sendBroadcast(intent);
  }

  @Nullable
  private Pair<String, Uri> queryVoicemailSourcePackage(Pair<Context, Uri> contextUriPair) {
    LogUtil.enterBlock("NewVoicemailMediaPlayer.queryVoicemailSourcePackage");
    Context context = contextUriPair.first;
    Uri uri = contextUriPair.second;
    String sourcePackage;
    try (Cursor cursor =
        context
            .getContentResolver()
            .query(uri, new String[] {Voicemails.SOURCE_PACKAGE}, null, null, null)) {

      if (!hasContent(cursor)) {
        LogUtil.e(
            "NewVoicemailMediaPlayer.queryVoicemailSourcePackage",
            "uri: %s does not return a SOURCE_PACKAGE",
            uri.toString());
        sourcePackage = null;
      } else {
        sourcePackage = cursor.getString(0);
        LogUtil.i(
            "NewVoicemailMediaPlayer.queryVoicemailSourcePackage",
            "uri: %s has a SOURCE_PACKAGE: %s",
            uri.toString(),
            sourcePackage);
      }
      LogUtil.i(
          "NewVoicemailMediaPlayer.queryVoicemailSourcePackage",
          "uri: %s has a SOURCE_PACKAGE: %s",
          uri.toString(),
          sourcePackage);
    }
    return new Pair<>(sourcePackage, uri);
  }

  private boolean hasContent(Cursor cursor) {
    return cursor != null && cursor.moveToFirst();
  }

  private final View.OnClickListener speakerButtonListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          LogUtil.i(
              "NewVoicemailMediaPlayer.speakerButtonListener",
              "speaker request for voicemailUri: %s",
              voicemailUri.toString());
        }
      };

  private final View.OnClickListener phoneButtonListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          LogUtil.i(
              "NewVoicemailMediaPlayer.phoneButtonListener",
              "speaker request for voicemailUri: %s",
              voicemailUri.toString());
          ContentValues contentValues = new ContentValues();
          contentValues.put("has_content", 0);
          // TODO(uabdullah): It only sets the has_content to 0, to allow the annotated call log to
          // change, and refresh the fragment. This is used to demo and test the downloading of
          // voicemails from the server. This will be removed once we implement this listener.
          try {
            getContext().getContentResolver().update(voicemailUri, contentValues, "type = 4", null);
          } catch (Exception e) {
            LogUtil.i(
                "NewVoicemailMediaPlayer.deleteButtonListener",
                "update has content of voicemailUri %s caused an error: %s",
                voicemailUri.toString(),
                e.toString());
          }
        }
      };

  private final View.OnClickListener deleteButtonListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          LogUtil.i(
              "NewVoicemailMediaPlayer.deleteButtonListener",
              "delete voicemailUri %s",
              String.valueOf(voicemailUri));
          newVoicemailViewHolderListener.deleteViewHolder(
              getContext(), fragmentManager, newVoicemailViewHolder, voicemailUri);
        }
      };

  /**
   * This is only called to update the media player view of the seekbar, and the duration and the
   * play button. For constant updates the adapter should seek track. This is the state when a
   * voicemail is playing.
   */
  public void updateSeekBarDurationAndShowPlayButton(NewVoicemailMediaPlayer mp) {
    if (!mp.isPlaying()) {
      return;
    }

    playButton.setVisibility(GONE);
    pauseButton.setVisibility(VISIBLE);
    voicemailLoadingStatusView.setVisibility(GONE);

    Assert.checkArgument(
        mp.equals(mediaPlayer), "there should only be one instance of a media player");
    Assert.checkArgument(
        mediaPlayer.getLastPreparedOrPreparingToPlayVoicemailUri().equals(voicemailUri));
    Assert.checkArgument(mediaPlayer.getLastPlayedOrPlayingVoicemailUri().equals(voicemailUri));
    Assert.isNotNull(mediaPlayer, "media player should have been set on bind");
    Assert.checkArgument(mediaPlayer.isPlaying());
    Assert.checkArgument(mediaPlayer.getCurrentPosition() >= 0);
    Assert.checkArgument(mediaPlayer.getDuration() >= 0);
    Assert.checkArgument(playButton.getVisibility() == GONE);
    Assert.checkArgument(pauseButton.getVisibility() == VISIBLE);
    Assert.checkArgument(seekBarView.getVisibility() == VISIBLE);
    Assert.checkArgument(currentSeekBarPosition.getVisibility() == VISIBLE);

    currentSeekBarPosition.setText(formatAsMinutesAndSeconds(mediaPlayer.getCurrentPosition()));
    if (seekBarView.getMax() != mediaPlayer.getDuration()) {
      seekBarView.setMax(mediaPlayer.getDuration());
    }
    seekBarView.setProgress(mediaPlayer.getCurrentPosition());
  }

  /**
   * What the default state of an expanded media player view should look like.
   *
   * @param currentlyExpandedViewHolderOnScreen
   * @param mediaPlayer
   */
  public void setToResetState(
      NewVoicemailViewHolder currentlyExpandedViewHolderOnScreen,
      NewVoicemailMediaPlayer mediaPlayer) {
    LogUtil.i(
        "NewVoicemailMediaPlayer.setToResetState",
        "update the seekbar for viewholder id:%d, mediaplayer view uri:%s, play button "
            + "visible:%b, pause button visible:%b",
        currentlyExpandedViewHolderOnScreen.getViewHolderId(),
        String.valueOf(voicemailUri),
        playButton.getVisibility() == VISIBLE,
        pauseButton.getVisibility() == VISIBLE);

    if (playButton.getVisibility() == GONE) {
      playButton.setVisibility(VISIBLE);
      pauseButton.setVisibility(GONE);
    }

    Assert.checkArgument(playButton.getVisibility() == VISIBLE);
    Assert.checkArgument(pauseButton.getVisibility() == GONE);

    Assert.checkArgument(
        !mediaPlayer.isPlaying(),
        "when resetting an expanded " + "state, there should be no voicemail playing");

    Assert.checkArgument(
        mediaPlayer.getLastPlayedOrPlayingVoicemailUri().equals(Uri.EMPTY),
        "reset should have been called before updating its media player view");
    currentSeekBarPosition.setText(formatAsMinutesAndSeconds(0));
    seekBarView.setProgress(0);
    seekBarView.setMax(100);
  }

  public void setToPausedState(Uri toPausedState, NewVoicemailMediaPlayer mp) {
    LogUtil.i(
        "NewVoicemailMediaPlayer.setToPausedState",
        "toPausedState uri:%s, play button visible:%b, pause button visible:%b",
        toPausedState == null ? "null" : voicemailUri.toString(),
        playButton.getVisibility() == VISIBLE,
        pauseButton.getVisibility() == VISIBLE);

    playButton.setVisibility(VISIBLE);
    pauseButton.setVisibility(GONE);

    currentSeekBarPosition.setText(formatAsMinutesAndSeconds(mediaPlayer.getCurrentPosition()));
    if (seekBarView.getMax() != mediaPlayer.getDuration()) {
      seekBarView.setMax(mediaPlayer.getDuration());
    }
    seekBarView.setProgress(mediaPlayer.getCurrentPosition());

    Assert.checkArgument(voicemailUri.equals(toPausedState));
    Assert.checkArgument(!mp.isPlaying());
    Assert.checkArgument(
        mp.equals(mediaPlayer), "there should only be one instance of a media player");
    Assert.checkArgument(
        this.mediaPlayer.getLastPreparedOrPreparingToPlayVoicemailUri().equals(voicemailUri));
    Assert.checkArgument(
        this.mediaPlayer.getLastPlayedOrPlayingVoicemailUri().equals(voicemailUri));
    Assert.checkArgument(this.mediaPlayer.getLastPausedVoicemailUri().equals(voicemailUri));
    Assert.isNotNull(this.mediaPlayer, "media player should have been set on bind");
    Assert.checkArgument(this.mediaPlayer.getCurrentPosition() >= 0);
    Assert.checkArgument(this.mediaPlayer.getDuration() >= 0);
    Assert.checkArgument(playButton.getVisibility() == VISIBLE);
    Assert.checkArgument(pauseButton.getVisibility() == GONE);
    Assert.checkArgument(seekBarView.getVisibility() == VISIBLE);
    Assert.checkArgument(currentSeekBarPosition.getVisibility() == VISIBLE);
  }

  @NonNull
  public Uri getVoicemailUri() {
    return voicemailUri;
  }

  private String formatAsMinutesAndSeconds(int millis) {
    int seconds = millis / 1000;
    int minutes = seconds / 60;
    seconds -= minutes * 60;
    if (minutes > 99) {
      minutes = 99;
    }
    return String.format(Locale.US, "%02d:%02d", minutes, seconds);
  }
}
