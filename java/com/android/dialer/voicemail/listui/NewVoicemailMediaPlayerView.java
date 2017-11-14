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
import android.content.Context;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.provider.VoicemailContract;
import android.support.annotation.VisibleForTesting;
import android.support.v4.util.Pair;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.SuccessListener;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.voicemail.model.VoicemailEntry;

/**
 * The view of the media player that is visible when a {@link NewVoicemailViewHolder} is expanded.
 */
public class NewVoicemailMediaPlayerView extends LinearLayout {

  private Button playButton;
  private Button speakerButton;
  private Button deleteButton;
  private TextView totalDurationView;
  private Uri voicemailUri;
  private FragmentManager fragmentManager;
  private MediaPlayer mediaPlayer;

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
    speakerButton = findViewById(R.id.speakerButton);
    deleteButton = findViewById(R.id.deleteButton);
    totalDurationView = findViewById(R.id.playback_seek_total_duration);
  }

  private void setupListenersForMediaPlayerButtons() {
    playButton.setOnClickListener(playButtonListener);
    speakerButton.setOnClickListener(speakerButtonListener);
    deleteButton.setOnClickListener(deleteButtonListener);
  }

  private final View.OnClickListener playButtonListener =
      view -> playVoicemailWhenAvailableLocally();

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
      if (cursor != null && cursor.moveToNext()) {
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
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnPreparedListener(onPreparedListener);
        mediaPlayer.setOnErrorListener(onErrorListener);
        mediaPlayer.setOnCompletionListener(onCompletionListener);

        mediaPlayer.reset();
        mediaPlayer.setDataSource(getContext(), uri);

        mediaPlayer.prepareAsync();
      } catch (Exception e) {
        LogUtil.e("NewVoicemailMediaPlayer.prepareMediaPlayer", "IOException " + e);
      }
    } else {
      // TODO(a bug): Add logic for downloading voicemail content from the server.
      LogUtil.i(
          "NewVoicemailMediaPlayer.prepareVoicemailForMediaPlayer", "need to download content");
    }
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

  private final View.OnClickListener deleteButtonListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          LogUtil.i(
              "NewVoicemailMediaPlayer.deleteButtonListener",
              "delete voicemailUri %s",
              voicemailUri.toString());
        }
      };

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  OnCompletionListener onCompletionListener =
      new OnCompletionListener() {

        @Override
        public void onCompletion(MediaPlayer mp) {
          LogUtil.i(
              "NewVoicemailMediaPlayer.onCompletionListener",
              "completed playing voicemailUri: %s",
              voicemailUri.toString());
        }
      };

  private final OnPreparedListener onPreparedListener =
      new OnPreparedListener() {

        @Override
        public void onPrepared(MediaPlayer mp) {
          LogUtil.i(
              "NewVoicemailMediaPlayer.onPreparedListener",
              "about to play voicemailUri: %s",
              voicemailUri.toString());
          mediaPlayer.start();
        }
      };

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  OnErrorListener onErrorListener =
      new OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
          LogUtil.i(
              "NewVoicemailMediaPlayer.onErrorListener",
              "error playing voicemailUri: %s",
              voicemailUri.toString());
          return false;
        }
      };

  void setFragmentManager(FragmentManager fragmentManager) {
    this.fragmentManager = fragmentManager;
  }

  void setVoicemailEntryValues(VoicemailEntry voicemailEntry) {
    Assert.isNotNull(voicemailEntry);
    Uri uri = Uri.parse(voicemailEntry.voicemailUri());
    Assert.isNotNull(uri);
    Assert.isNotNull(totalDurationView);

    voicemailUri = uri;
    totalDurationView.setText(
        VoicemailEntryText.getVoicemailDuration(getContext(), voicemailEntry));
  }
}
