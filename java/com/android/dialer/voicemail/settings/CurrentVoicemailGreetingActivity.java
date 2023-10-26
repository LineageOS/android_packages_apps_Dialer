/*
 * Copyright (C) 2018 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.android.dialer.R;
import com.android.dialer.common.LogUtil;
import com.android.dialer.widget.DialerToolbar;

import java.io.IOException;
import java.util.Locale;

/** Activity to display current voicemail greeting and allow user to navigate to record a new one */
public class CurrentVoicemailGreetingActivity extends AppCompatActivity {
  public static final String VOICEMAIL_GREETING_FILEPATH_KEY = "canonVoicemailGreetingFilePathKey";

  private final ActivityResultLauncher<String> audioPermissionLauncher = registerForActivityResult(
          new ActivityResultContracts.RequestPermission(), granted -> {
            if (!granted) {
              LogUtil.w(
                      "CurrentVoicemailGreetingActivity.onRequestPermissionsResult",
                      "permissionToRecordAccepted = false.");
            }
          });

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
    playbackProgressLabel = findViewById(R.id.playback_progress_text_view);
    DialerToolbar currentVoicemailGreetingDialerToolbar = findViewById(R.id.toolbar);

    currentVoicemailGreetingDialerToolbar.setTitle(
        R.string.voicemail_change_greeting_preference_title);

    displayCurrentVoicemailGreetingStatus();
  }

  @Override
  public void onStart() {
    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);

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
