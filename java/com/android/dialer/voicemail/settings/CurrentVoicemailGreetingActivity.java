/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.voicemail.settings;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TextView;
import com.android.dialer.common.LogUtil;
import com.android.dialer.widget.DialerToolbar;
import java.io.IOException;
import java.util.Locale;

/** Activity to display current voicemail greeting and allow user to navigate to record a new one */
public class CurrentVoicemailGreetingActivity extends Activity {
  public static final String VOICEMAIL_GREETING_FILEPATH_KEY = "canonVoicemailGreetingFilePathKey";

  private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

  private boolean permissionToRecordAccepted = false;

  private ImageButton changeGreetingButton;
  private ImageButton playButton;

  private DialerToolbar currentVoicemailGreetingDialerToolbar;

  private int greetingDuration = -1;

  private MediaPlayer mediaPlayer;

  private TextView playbackProgressLabel;
  private View playbackDisplay;

  private String voicemailGreetingFilePath = "";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_current_voicemail_greeting);

    playbackDisplay = findViewById(R.id.current_voicemail_greeting_recording_display);
    playbackProgressLabel = (TextView) findViewById(R.id.playback_progress_text_view);
    currentVoicemailGreetingDialerToolbar = (DialerToolbar) findViewById(R.id.toolbar);

    currentVoicemailGreetingDialerToolbar.setTitle(
        R.string.voicemail_change_greeting_preference_title);

    changeGreetingButton = (ImageButton) findViewById(R.id.change_greeting_button);
    changeGreetingButton.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            // TODO(sabowitz): Implement this in CL child beta01.
          }
        });

    playButton = (ImageButton) findViewById(R.id.play_button);
    playButton.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            // TODO(sabowitz): Finish implementing this in CL child beta02.
          }
        });

    displayCurrentVoicemailGreetingStatus();
  }

  @Override
  public void onStart() {
    ActivityCompat.requestPermissions(
        this, new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);

    if (isGreetingRecorded()) {
      mediaPlayer = new MediaPlayer();
      try {
        mediaPlayer.setDataSource(voicemailGreetingFilePath);
        mediaPlayer.prepare();
      } catch (IOException e) {
        LogUtil.e("CurrentVoicemailGreetingActivity.onStart", "mediaPlayer setup failed.");
      }
    }
    super.onStart();
  }

  @Override
  public void onPause() {
    if (isGreetingRecorded()) {
      if (mediaPlayer.isPlaying()) {
        mediaPlayer.release();
        mediaPlayer = null;
      }
    }
    super.onPause();
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
      permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
    }
    if (!permissionToRecordAccepted) {
      LogUtil.w(
          "CurrentVoicemailGreetingActivity.onRequestPermissionsResult",
          "permissionToRecordAccepted = false.");
      // TODO(sabowitz): Implement error dialog logic in a child CL.
    }
  }

  private boolean isGreetingRecorded() {
    Intent intent = getIntent();
    if (intent.hasExtra(VOICEMAIL_GREETING_FILEPATH_KEY)) {
      String filePathProxy = intent.getStringExtra(VOICEMAIL_GREETING_FILEPATH_KEY);
      if (filePathProxy == null || filePathProxy.length() == 0) {
        return false;
      }
      if (mediaPlayer == null) {
        mediaPlayer = new MediaPlayer();
      }
      try {
        mediaPlayer.setDataSource(filePathProxy);
        int durationProxy = mediaPlayer.getDuration();
        greetingDuration = durationProxy;
        voicemailGreetingFilePath = filePathProxy;
        mediaPlayer = null;
        return true;
      } catch (IOException e) {
        LogUtil.e("CurrentVoicemailGreetingActivity.isGreetingRecorded", "bad filepath.");
        mediaPlayer = null;
        return false;
      }
    }
    return false;
  }

  private void displayCurrentVoicemailGreetingStatus() {
    if (isGreetingRecorded()) {
      String durationLabel = String.format(Locale.US, "00:%d", greetingDuration);
      playbackProgressLabel.setText(durationLabel);
    } else {
      playbackDisplay.setVisibility(View.GONE);
    }
  }
}
