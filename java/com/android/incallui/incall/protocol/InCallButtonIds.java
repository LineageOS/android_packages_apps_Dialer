/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.incallui.incall.protocol;

import android.support.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Ids for buttons in the in call UI. */
@Retention(RetentionPolicy.SOURCE)
@IntDef({
  InCallButtonIds.BUTTON_AUDIO,
  InCallButtonIds.BUTTON_MUTE,
  InCallButtonIds.BUTTON_DIALPAD,
  InCallButtonIds.BUTTON_HOLD,
  InCallButtonIds.BUTTON_SWAP,
  InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO,
  InCallButtonIds.BUTTON_SWITCH_CAMERA,
  InCallButtonIds.BUTTON_DOWNGRADE_TO_AUDIO,
  InCallButtonIds.BUTTON_ADD_CALL,
  InCallButtonIds.BUTTON_MERGE,
  InCallButtonIds.BUTTON_PAUSE_VIDEO,
  InCallButtonIds.BUTTON_MANAGE_VIDEO_CONFERENCE,
  InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE,
  InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY,
  InCallButtonIds.BUTTON_COUNT,
})
public @interface InCallButtonIds {

  int BUTTON_AUDIO = 0;
  int BUTTON_MUTE = 1;
  int BUTTON_DIALPAD = 2;
  int BUTTON_HOLD = 3;
  int BUTTON_SWAP = 4;
  int BUTTON_UPGRADE_TO_VIDEO = 5;
  int BUTTON_SWITCH_CAMERA = 6;
  int BUTTON_DOWNGRADE_TO_AUDIO = 7;
  int BUTTON_ADD_CALL = 8;
  int BUTTON_MERGE = 9;
  int BUTTON_PAUSE_VIDEO = 10;
  int BUTTON_MANAGE_VIDEO_CONFERENCE = 11;
  int BUTTON_MANAGE_VOICE_CONFERENCE = 12;
  int BUTTON_SWITCH_TO_SECONDARY = 13;
  int BUTTON_COUNT = 14;
}
