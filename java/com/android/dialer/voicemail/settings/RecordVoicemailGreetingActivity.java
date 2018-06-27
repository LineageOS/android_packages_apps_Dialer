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

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Activity for recording a new voicemail greeting */
public class RecordVoicemailGreetingActivity extends Activity implements OnClickListener {

  /** Possible states of RecordButton and RecordVoicemailGreetingActivity */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    RECORD_GREETING_INIT,
    RECORD_GREETING_RECORDING,
    RECORD_GREETING_RECORDED,
    RECORD_GREETING_PLAYING_BACK
  })
  public @interface ButtonState {}

  public static final int RECORD_GREETING_INIT = 1;
  public static final int RECORD_GREETING_RECORDING = 2;
  public static final int RECORD_GREETING_RECORDED = 3;
  public static final int RECORD_GREETING_PLAYING_BACK = 4;
  public static final int MAX_GREETING_DURATION_MS = 45000;

  private int currentState;
  private int duration;
  private RecordButton recordButton;
  private Button saveButton;
  private Button redoButton;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_record_voicemail_greeting);

    recordButton = findViewById(R.id.record_button);
    saveButton = findViewById(R.id.save_button);
    redoButton = findViewById(R.id.redo_button);

    duration = 0;
    setState(RECORD_GREETING_INIT);
    recordButton.setOnClickListener(this);
  }

  @Override
  public void onClick(View v) {
    if (v == recordButton) {
      switch (currentState) {
        case RECORD_GREETING_INIT:
          setState(RECORD_GREETING_RECORDING);
          break;
        case RECORD_GREETING_RECORDED:
          setState(RECORD_GREETING_PLAYING_BACK);
          break;
        case RECORD_GREETING_RECORDING:
        case RECORD_GREETING_PLAYING_BACK:
          setState(RECORD_GREETING_RECORDED);
          break;
        default:
          break;
      }
    }
  }

  private void setState(@ButtonState int state) {
    currentState = state;

    switch (state) {
      case RECORD_GREETING_INIT:
        recordButton.setState(state);
        recordButton.setTracks(0, 0);
        setSaveRedoButtonsEnabled(false);
        break;
      case RECORD_GREETING_PLAYING_BACK:
      case RECORD_GREETING_RECORDED:
        recordButton.setState(state);
        recordButton.setTracks(0, (float) duration / MAX_GREETING_DURATION_MS);
        setSaveRedoButtonsEnabled(true);
        break;
      case RECORD_GREETING_RECORDING:
        recordButton.setState(state);
        recordButton.setTracks(0, 1f);
        setSaveRedoButtonsEnabled(false);
        break;
      default:
        break;
    }
  }

  /** Enables/Disables save and redo buttons in the layout */
  private void setSaveRedoButtonsEnabled(boolean enabled) {
    if (enabled) {
      saveButton.setVisibility(View.VISIBLE);
      redoButton.setVisibility(View.VISIBLE);
    } else {
      saveButton.setVisibility(View.GONE);
      redoButton.setVisibility(View.GONE);
    }
  }
}
