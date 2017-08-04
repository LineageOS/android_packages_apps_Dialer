/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.database.Cursor;
import android.provider.VoicemailContract.Status;
import com.android.dialer.database.VoicemailStatusQuery;

/**
 * Utility used by the call log UI to determine what user message, if any, related to voicemail
 * source status needs to be shown. The messages are returned in the order of importance.
 *
 * <p>This class interacts with the voicemail content provider to fetch statuses of all the
 * registered voicemail sources and determines if any status message needs to be shown. The user of
 * this class must observe/listen to provider changes and invoke this class to check if any message
 * needs to be shown.
 */
public final class VoicemailStatusHelper {

  private VoicemailStatusHelper() {}

  /**
   * Returns the number of active voicemail sources installed.
   *
   * <p>The number of sources is counted by querying the voicemail status table.
   *
   * @param cursor The caller is responsible for the life cycle of the cursor and resetting the
   *     position
   */
  public static int getNumberActivityVoicemailSources(Cursor cursor) {
    int count = 0;
    if (!cursor.moveToFirst()) {
      return 0;
    }
    do {
      if (isVoicemailSourceActive(cursor)) {
        ++count;
      }
    } while (cursor.moveToNext());
    return count;
  }

  /**
   * Returns whether the source status in the cursor corresponds to an active source. A source is
   * active if its' configuration state is not NOT_CONFIGURED. For most voicemail sources, only OK
   * and NOT_CONFIGURED are used. The OMTP visual voicemail client has the same behavior pre-NMR1.
   * NMR1 visual voicemail will only set it to NOT_CONFIGURED when it is deactivated. As soon as
   * activation is attempted, it will transition into CONFIGURING then into OK or other error state,
   * NOT_CONFIGURED is never set through an error.
   */
  private static boolean isVoicemailSourceActive(Cursor cursor) {
    return cursor.getString(VoicemailStatusQuery.SOURCE_PACKAGE_INDEX) != null
        // getInt() returns 0 when null
        && !cursor.isNull(VoicemailStatusQuery.CONFIGURATION_STATE_INDEX)
        && cursor.getInt(VoicemailStatusQuery.CONFIGURATION_STATE_INDEX)
            != Status.CONFIGURATION_STATE_NOT_CONFIGURED;
  }
}
