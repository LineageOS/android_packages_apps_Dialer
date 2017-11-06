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

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import com.android.dialer.common.LogUtil;

/**
 * The view of the media player that is visible when a {@link NewVoicemailViewHolder} is expanded.
 */
public class NewVoicemailMediaPlayer extends LinearLayout {

  private Button playButton;
  private Button speakerButton;
  private Button deleteButton;

  public NewVoicemailMediaPlayer(Context context, AttributeSet attrs) {
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
    initializeMediaPlayerButtons();
    setupListenersForMediaPlayerButtons();
  }

  private void initializeMediaPlayerButtons() {
    playButton = findViewById(R.id.playButton);
    speakerButton = findViewById(R.id.speakerButton);
    deleteButton = findViewById(R.id.deleteButton);
  }

  private void setupListenersForMediaPlayerButtons() {
    playButton.setOnClickListener(playButtonListener);
    speakerButton.setOnClickListener(speakerButtonListener);
    deleteButton.setOnClickListener(deleteButtonListener);
  }

  private final View.OnClickListener playButtonListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          LogUtil.i("NewVoicemailMediaPlayer.playButtonListener", "onClick");
        }
      };

  private final View.OnClickListener speakerButtonListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          LogUtil.i("NewVoicemailMediaPlayer.speakerButtonListener", "onClick");
        }
      };

  private final View.OnClickListener deleteButtonListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          LogUtil.i("NewVoicemailMediaPlayer.deleteButtonListener", "onClick");
        }
      };
}
