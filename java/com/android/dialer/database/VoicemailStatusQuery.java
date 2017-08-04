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

package com.android.dialer.database;

import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.VoicemailContract.Status;
import android.support.annotation.RequiresApi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The query for the call voicemail status table. */
public class VoicemailStatusQuery {

  // TODO: Column indices should be removed in favor of Cursor#getColumnIndex
  public static final int SOURCE_PACKAGE_INDEX = 0;
  public static final int SETTINGS_URI_INDEX = 1;
  public static final int VOICEMAIL_ACCESS_URI_INDEX = 2;
  public static final int CONFIGURATION_STATE_INDEX = 3;
  public static final int DATA_CHANNEL_STATE_INDEX = 4;
  public static final int NOTIFICATION_CHANNEL_STATE_INDEX = 5;

  @RequiresApi(VERSION_CODES.N)
  public static final int QUOTA_OCCUPIED_INDEX = 6;

  @RequiresApi(VERSION_CODES.N)
  public static final int QUOTA_TOTAL_INDEX = 7;

  @RequiresApi(VERSION_CODES.N_MR1)
  // The PHONE_ACCOUNT columns were added in M, but aren't queryable until N MR1
  public static final int PHONE_ACCOUNT_COMPONENT_NAME = 8;

  @RequiresApi(VERSION_CODES.N_MR1)
  public static final int PHONE_ACCOUNT_ID = 9;

  @RequiresApi(VERSION_CODES.N_MR1)
  public static final int SOURCE_TYPE_INDEX = 10;

  private static final String[] PROJECTION_M =
      new String[] {
        Status.SOURCE_PACKAGE, // 0
        Status.SETTINGS_URI, // 1
        Status.VOICEMAIL_ACCESS_URI, // 2
        Status.CONFIGURATION_STATE, // 3
        Status.DATA_CHANNEL_STATE, // 4
        Status.NOTIFICATION_CHANNEL_STATE // 5
      };

  @RequiresApi(VERSION_CODES.N)
  private static final String[] PROJECTION_N;

  @RequiresApi(VERSION_CODES.N_MR1)
  private static final String[] PROJECTION_NMR1;

  static {
    List<String> projectionList = new ArrayList<>(Arrays.asList(PROJECTION_M));
    projectionList.add(Status.QUOTA_OCCUPIED); // 6
    projectionList.add(Status.QUOTA_TOTAL); // 7
    PROJECTION_N = projectionList.toArray(new String[projectionList.size()]);

    projectionList.add(Status.PHONE_ACCOUNT_COMPONENT_NAME); // 8
    projectionList.add(Status.PHONE_ACCOUNT_ID); // 9
    projectionList.add(Status.SOURCE_TYPE); // 10
    PROJECTION_NMR1 = projectionList.toArray(new String[projectionList.size()]);
  }

  public static String[] getProjection() {
    if (VERSION.SDK_INT >= VERSION_CODES.N_MR1) {
      return PROJECTION_NMR1;
    }
    if (VERSION.SDK_INT >= VERSION_CODES.N) {
      return PROJECTION_N;
    }
    return PROJECTION_M;
  }
}
