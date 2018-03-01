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
 * limitations under the License
 */

package com.android.dialer.voicemail.listui;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.strictmode.StrictModeUtils;
import java.io.IOException;

/** A wrapper around {@link MediaPlayer} */
public class NewVoicemailMediaPlayer {

  private final MediaPlayer mediaPlayer;
  private Uri voicemailLastPlayedOrPlayingUri;
  private Uri voicemailUriLastPreparedOrPreparingToPlay;

  private OnErrorListener newVoicemailMediaPlayerOnErrorListener;
  private OnPreparedListener newVoicemailMediaPlayerOnPreparedListener;
  private OnCompletionListener newVoicemailMediaPlayerOnCompletionListener;
  private Uri pausedUri;
  @Nullable private Uri voicemailRequestedToDownload;

  public NewVoicemailMediaPlayer(@NonNull MediaPlayer player) {
    mediaPlayer = Assert.isNotNull(player);
  }

  // TODO(uabdullah): Consider removing the StrictModeUtils.bypass (a bug)
  public void prepareMediaPlayerAndPlayVoicemailWhenReady(Context context, Uri uri)
      throws IOException {
    Assert.checkArgument(uri != null, "Media player cannot play a null uri");
    LogUtil.i(
        "NewVoicemailMediaPlayer",
        "trying to prepare playing voicemail uri: %s",
        String.valueOf(uri));
    try {
      reset();
      voicemailUriLastPreparedOrPreparingToPlay = uri;
      verifyListenersNotNull();
      LogUtil.i("NewVoicemailMediaPlayer", "setData source");
      StrictModeUtils.bypass(
          () -> {
            try {
              mediaPlayer.setDataSource(context, uri);
              setAudioManagerToNonSpeakerMode(context);
            } catch (IOException e) {
              LogUtil.i(
                  "NewVoicemailMediaPlayer",
                  "threw an Exception when setting datasource "
                      + e
                      + " for uri: "
                      + uri
                      + "for context : "
                      + context);
            }
          });
      LogUtil.i("NewVoicemailMediaPlayer", "prepare async");
      StrictModeUtils.bypass(() -> mediaPlayer.prepareAsync());
    } catch (IllegalStateException e) {
      LogUtil.i(
          "NewVoicemailMediaPlayer", "caught an IllegalStateException state exception : \n" + e);
    } catch (Exception e) {
      LogUtil.i(
          "NewVoicemailMediaPlayer",
          "threw an Exception " + e + " for uri: " + uri + "for context : " + context);
    }
  }

  /** We should never start playing voicemails from the speaker mode */
  private void setAudioManagerToNonSpeakerMode(Context context) {
    AudioManager audioManager = context.getSystemService(AudioManager.class);
    audioManager.setMode(AudioManager.STREAM_MUSIC);
    audioManager.setSpeakerphoneOn(false);
  }

  private void verifyListenersNotNull() {
    Assert.isNotNull(
        newVoicemailMediaPlayerOnErrorListener,
        "newVoicemailMediaPlayerOnErrorListener must be set before preparing to "
            + "play voicemails");
    Assert.isNotNull(
        newVoicemailMediaPlayerOnCompletionListener,
        "newVoicemailMediaPlayerOnCompletionListener must be set before preparing"
            + " to play voicemails");
    Assert.isNotNull(
        newVoicemailMediaPlayerOnPreparedListener,
        "newVoicemailMediaPlayerOnPreparedListener must be set before preparing to"
            + " play voicemails");
  }

  // Must be called from onPrepared
  public void start(Uri startPlayingVoicemailUri) {
    Assert.checkArgument(
        startPlayingVoicemailUri.equals(voicemailUriLastPreparedOrPreparingToPlay),
        "uri:%s was not prepared before calling start. Uri that is currently prepared: %s",
        startPlayingVoicemailUri,
        getLastPreparedOrPreparingToPlayVoicemailUri());

    mediaPlayer.start();
    voicemailLastPlayedOrPlayingUri = startPlayingVoicemailUri;
    pausedUri = null;
    voicemailRequestedToDownload = null;
  }

  public void reset() {
    LogUtil.enterBlock("NewVoicemailMediaPlayer.reset");
    mediaPlayer.reset();
    voicemailLastPlayedOrPlayingUri = null;
    voicemailUriLastPreparedOrPreparingToPlay = null;
    pausedUri = null;
    voicemailRequestedToDownload = null;
  }

  public void pauseMediaPlayer(Uri voicemailUri) {
    pausedUri = voicemailUri;
    Assert.checkArgument(
        voicemailUriLastPreparedOrPreparingToPlay.equals(voicemailLastPlayedOrPlayingUri),
        "last prepared and last playing should be the same");
    Assert.checkArgument(
        pausedUri.equals(voicemailLastPlayedOrPlayingUri),
        "only the last played uri can be paused");
    mediaPlayer.pause();
  }

  public void seekTo(int progress) {
    mediaPlayer.seekTo(progress);
  }

  public void setOnErrorListener(OnErrorListener onErrorListener) {
    mediaPlayer.setOnErrorListener(onErrorListener);
    newVoicemailMediaPlayerOnErrorListener = onErrorListener;
  }

  public void setOnPreparedListener(OnPreparedListener onPreparedListener) {
    mediaPlayer.setOnPreparedListener(onPreparedListener);
    newVoicemailMediaPlayerOnPreparedListener = onPreparedListener;
  }

  public void setOnCompletionListener(OnCompletionListener onCompletionListener) {
    mediaPlayer.setOnCompletionListener(onCompletionListener);
    newVoicemailMediaPlayerOnCompletionListener = onCompletionListener;
  }

  public void setVoicemailRequestedToDownload(@NonNull Uri uri) {
    Assert.isNotNull(uri, "cannot download a null voicemail");
    voicemailRequestedToDownload = uri;
  }

  /**
   * Note: In some cases it's possible mediaPlayer.isPlaying() can return true, but
   * mediaPlayer.getCurrentPosition() can be greater than mediaPlayer.getDuration(), after which
   * mediaPlayer.isPlaying() will be false. This is a weird corner case and adding the
   * mediaPlayer.getCurrentPosition() < mediaPlayer.getDuration() check here messes with the
   * mediaPlayer.start() (doesn't return mediaPlayer.isPlaying() to be true immediately).
   *
   * @return if the media plaer;
   */
  public boolean isPlaying() {
    return mediaPlayer.isPlaying();
  }

  public int getCurrentPosition() {
    return mediaPlayer.getCurrentPosition();
  }

  public Uri getLastPlayedOrPlayingVoicemailUri() {
    if (mediaPlayer.isPlaying()) {
      Assert.isNotNull(voicemailLastPlayedOrPlayingUri);
    }

    return voicemailLastPlayedOrPlayingUri == null ? Uri.EMPTY : voicemailLastPlayedOrPlayingUri;
  }

  /**
   * All the places that call this function, we expect the voicemail to have been prepared, but we
   * could get rid of the assert check in the future if needed.
   */
  public Uri getLastPreparedOrPreparingToPlayVoicemailUri() {
    return Assert.isNotNull(
        voicemailUriLastPreparedOrPreparingToPlay,
        "we expect whoever called this to have prepared a voicemail before calling this function");
  }

  public Uri getLastPausedVoicemailUri() {
    return pausedUri;
  }

  public MediaPlayer getMediaPlayer() {
    return mediaPlayer;
  }

  public int getDuration() {
    Assert.checkArgument(mediaPlayer != null);
    return mediaPlayer.getDuration();
  }

  /**
   * A null v/s non-value is important for the {@link NewVoicemailAdapter} to differentiate between
   * a underlying table change due to a voicemail being downloaded or something else (e.g delete).
   *
   * @return if there was a Uri that was requested to be downloaded from the server, null otherwise.
   */
  @Nullable
  public Uri getVoicemailRequestedToDownload() {
    return voicemailRequestedToDownload;
  }

  public boolean isPaused() {
    return pausedUri != null;
  }
}
