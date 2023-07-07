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

package com.android.dialer.voicemailstatus;

import android.provider.VoicemailContract.Status;

/** The query for the call voicemail status table. */
public class VoicemailStatusQuery {

  // TODO(maxwelb): Column indices should be removed in favor of Cursor#getColumnIndex
  public static final int SOURCE_PACKAGE_INDEX = 0;
  public static final int SETTINGS_URI_INDEX = 1;
  public static final int VOICEMAIL_ACCESS_URI_INDEX = 2;
  public static final int CONFIGURATION_STATE_INDEX = 3;
  public static final int DATA_CHANNEL_STATE_INDEX = 4;
  public static final int NOTIFICATION_CHANNEL_STATE_INDEX = 5;
  public static final int QUOTA_OCCUPIED_INDEX = 6;
  public static final int QUOTA_TOTAL_INDEX = 7;
  public static final int PHONE_ACCOUNT_COMPONENT_NAME = 8;
  public static final int PHONE_ACCOUNT_ID = 9;
  public static final int SOURCE_TYPE_INDEX = 10;

  private static final String[] PROJECTION =
      new String[] {
        Status.SOURCE_PACKAGE, // 0
        Status.SETTINGS_URI, // 1
        Status.VOICEMAIL_ACCESS_URI, // 2
        Status.CONFIGURATION_STATE, // 3
        Status.DATA_CHANNEL_STATE, // 4
        Status.NOTIFICATION_CHANNEL_STATE, // 5
        Status.QUOTA_OCCUPIED, // 6
        Status.QUOTA_TOTAL, // 7
        Status.PHONE_ACCOUNT_COMPONENT_NAME, // 8
        Status.PHONE_ACCOUNT_ID, // 9
        Status.SOURCE_TYPE // 10
      };


  public static String[] getProjection() {
    return PROJECTION;
  }
}
