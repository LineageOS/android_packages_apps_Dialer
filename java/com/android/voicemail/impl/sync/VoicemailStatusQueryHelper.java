/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.voicemail.impl.sync;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.VoicemailContract;
import android.provider.VoicemailContract.Status;
import android.telecom.PhoneAccountHandle;

/** Construct queries to interact with the voicemail status table. */
public class VoicemailStatusQueryHelper {

  static final String[] PROJECTION =
      new String[] {
        Status._ID, // 0
        Status.CONFIGURATION_STATE, // 1
        Status.NOTIFICATION_CHANNEL_STATE, // 2
        Status.SOURCE_PACKAGE // 3
      };

  public static final int _ID = 0;
  public static final int CONFIGURATION_STATE = 1;
  public static final int NOTIFICATION_CHANNEL_STATE = 2;
  public static final int SOURCE_PACKAGE = 3;

  private Context mContext;
  private ContentResolver mContentResolver;
  private Uri mSourceUri;

  public VoicemailStatusQueryHelper(Context context) {
    mContext = context;
    mContentResolver = context.getContentResolver();
    mSourceUri = VoicemailContract.Status.buildSourceUri(mContext.getPackageName());
  }

  /**
   * Check if the configuration state for the voicemail source is "ok", meaning that the source is
   * set up.
   *
   * @param phoneAccount The phone account for the voicemail source to check.
   * @return {@code true} if the voicemail source is configured, {@code} false otherwise, including
   *     if the voicemail source is not registered in the table.
   */
  public boolean isVoicemailSourceConfigured(PhoneAccountHandle phoneAccount) {
    return isFieldEqualTo(phoneAccount, CONFIGURATION_STATE, Status.CONFIGURATION_STATE_OK);
  }

  /**
   * Check if the notifications channel of a voicemail source is active. That is, when a new
   * voicemail is available, if the server able to notify the device.
   *
   * @return {@code true} if notifications channel is active, {@code false} otherwise.
   */
  public boolean isNotificationsChannelActive(PhoneAccountHandle phoneAccount) {
    return isFieldEqualTo(
        phoneAccount, NOTIFICATION_CHANNEL_STATE, Status.NOTIFICATION_CHANNEL_STATE_OK);
  }

  /**
   * Check if a field for an entry in the status table is equal to a specific value.
   *
   * @param phoneAccount The phone account of the voicemail source to query for.
   * @param columnIndex The column index of the field in the returned query.
   * @param value The value to compare against.
   * @return {@code true} if the stored value is equal to the provided value. {@code false}
   *     otherwise.
   */
  private boolean isFieldEqualTo(PhoneAccountHandle phoneAccount, int columnIndex, int value) {
    Cursor cursor = null;
    if (phoneAccount != null) {
      String phoneAccountComponentName = phoneAccount.getComponentName().flattenToString();
      String phoneAccountId = phoneAccount.getId();
      if (phoneAccountComponentName == null || phoneAccountId == null) {
        return false;
      }
      try {
        String whereClause =
            Status.PHONE_ACCOUNT_COMPONENT_NAME
                + "=? AND "
                + Status.PHONE_ACCOUNT_ID
                + "=? AND "
                + Status.SOURCE_PACKAGE
                + "=?";
        String[] whereArgs = {phoneAccountComponentName, phoneAccountId, mContext.getPackageName()};
        cursor = mContentResolver.query(mSourceUri, PROJECTION, whereClause, whereArgs, null);
        if (cursor != null && cursor.moveToFirst()) {
          return cursor.getInt(columnIndex) == value;
        }
      } finally {
        if (cursor != null) {
          cursor.close();
        }
      }
    }
    return false;
  }
}
