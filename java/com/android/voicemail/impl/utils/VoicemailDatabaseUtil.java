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

package com.android.voicemail.impl.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.VoicemailContract.Voicemails;
import android.telecom.PhoneAccountHandle;
import com.android.voicemail.impl.Voicemail;
import java.util.List;

public class VoicemailDatabaseUtil {

  /**
   * Inserts a new voicemail into the voicemail content provider.
   *
   * @param context The context of the app doing the inserting
   * @param voicemail Data to be inserted
   * @return {@link Uri} of the newly inserted {@link Voicemail}
   * @hide
   */
  public static Uri insert(Context context, Voicemail voicemail) {
    ContentResolver contentResolver = context.getContentResolver();
    ContentValues contentValues = getContentValues(voicemail);
    return contentResolver.insert(
        Voicemails.buildSourceUri(context.getPackageName()), contentValues);
  }

  /**
   * Inserts a list of voicemails into the voicemail content provider.
   *
   * @param context The context of the app doing the inserting
   * @param voicemails Data to be inserted
   * @return the number of voicemails inserted
   * @hide
   */
  public static int insert(Context context, List<Voicemail> voicemails) {
    for (Voicemail voicemail : voicemails) {
      insert(context, voicemail);
    }
    return voicemails.size();
  }

  /** Maps structured {@link Voicemail} to {@link ContentValues} in content provider. */
  private static ContentValues getContentValues(Voicemail voicemail) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(Voicemails.DATE, String.valueOf(voicemail.getTimestampMillis()));
    contentValues.put(Voicemails.NUMBER, voicemail.getNumber());
    contentValues.put(Voicemails.DURATION, String.valueOf(voicemail.getDuration()));
    contentValues.put(Voicemails.SOURCE_PACKAGE, voicemail.getSourcePackage());
    contentValues.put(Voicemails.SOURCE_DATA, voicemail.getSourceData());
    contentValues.put(Voicemails.IS_READ, voicemail.isRead() ? 1 : 0);
    contentValues.put(Voicemails.IS_OMTP_VOICEMAIL, 1);

    PhoneAccountHandle phoneAccount = voicemail.getPhoneAccount();
    if (phoneAccount != null) {
      contentValues.put(
          Voicemails.PHONE_ACCOUNT_COMPONENT_NAME,
          phoneAccount.getComponentName().flattenToString());
      contentValues.put(Voicemails.PHONE_ACCOUNT_ID, phoneAccount.getId());
    }

    if (voicemail.getTranscription() != null) {
      contentValues.put(Voicemails.TRANSCRIPTION, voicemail.getTranscription());
    }

    return contentValues;
  }
}
