/*
 * Copyright (C) 2016 The Android Open Source Project
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

/** Utility class for {@link InCallButtonIds}. */
public class InCallButtonIdsExtension {

  /**
   * Converts the given {@link InCallButtonIds} to a human readable string.
   *
   * @param id the id to convert.
   * @return the human readable string.
   */
  public static String toString(@InCallButtonIds int id) {
    if (id == InCallButtonIds.BUTTON_AUDIO) {
      return "AUDIO";
    } else if (id == InCallButtonIds.BUTTON_MUTE) {
      return "MUTE";
    } else if (id == InCallButtonIds.BUTTON_DIALPAD) {
      return "DIALPAD";
    } else if (id == InCallButtonIds.BUTTON_HOLD) {
      return "HOLD";
    } else if (id == InCallButtonIds.BUTTON_SWAP) {
      return "SWAP";
    } else if (id == InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO) {
      return "UPGRADE_TO_VIDEO";
    } else if (id == InCallButtonIds.BUTTON_DOWNGRADE_TO_AUDIO) {
      return "DOWNGRADE_TO_AUDIO";
    } else if (id == InCallButtonIds.BUTTON_SWITCH_CAMERA) {
      return "SWITCH_CAMERA";
    } else if (id == InCallButtonIds.BUTTON_ADD_CALL) {
      return "ADD_CALL";
    } else if (id == InCallButtonIds.BUTTON_MERGE) {
      return "MERGE";
    } else if (id == InCallButtonIds.BUTTON_PAUSE_VIDEO) {
      return "PAUSE_VIDEO";
    } else if (id == InCallButtonIds.BUTTON_MANAGE_VIDEO_CONFERENCE) {
      return "MANAGE_VIDEO_CONFERENCE";
    } else if (id == InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE) {
      return "MANAGE_VOICE_CONFERENCE";
    } else if (id == InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY) {
      return "SWITCH_TO_SECONDARY";
    } else {
      return "INVALID_BUTTON: " + id;
    }
  }
}
