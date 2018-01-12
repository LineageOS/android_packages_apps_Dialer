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
import android.database.Cursor;
import android.provider.CallLog.Calls;
import android.support.v4.content.CursorLoader;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.NumberAttributes;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.voicemail.model.VoicemailEntry;
import com.google.protobuf.InvalidProtocolBufferException;

/** CursorLoader for the annotated call log (voicemails only). */
final class VoicemailCursorLoader extends CursorLoader {

  // When adding columns be sure to update {@link #VoicemailCursorLoader.toVoicemailEntry}.
  public static final String[] VOICEMAIL_COLUMNS =
      new String[] {
        AnnotatedCallLog._ID,
        AnnotatedCallLog.TIMESTAMP,
        AnnotatedCallLog.NUMBER,
        AnnotatedCallLog.FORMATTED_NUMBER,
        AnnotatedCallLog.DURATION,
        AnnotatedCallLog.GEOCODED_LOCATION,
        AnnotatedCallLog.CALL_TYPE,
        AnnotatedCallLog.TRANSCRIPTION,
        AnnotatedCallLog.VOICEMAIL_URI,
        AnnotatedCallLog.IS_READ,
        AnnotatedCallLog.NUMBER_ATTRIBUTES,
      };

  // Indexes for VOICEMAIL_COLUMNS
  private static final int ID = 0;
  private static final int TIMESTAMP = 1;
  private static final int NUMBER = 2;
  private static final int FORMATTED_NUMBER = 3;
  private static final int DURATION = 4;
  private static final int GEOCODED_LOCATION = 5;
  private static final int CALL_TYPE = 6;
  private static final int TRANSCRIPTION = 7;
  private static final int VOICEMAIL_URI = 8;
  private static final int IS_READ = 9;
  private static final int NUMBER_ATTRIBUTES = 10;

  // TODO(zachh): Optimize indexes
  VoicemailCursorLoader(Context context) {
    super(
        context,
        AnnotatedCallLog.CONTENT_URI,
        VOICEMAIL_COLUMNS,
        AnnotatedCallLog.CALL_TYPE + " = ?",
        new String[] {Integer.toString(Calls.VOICEMAIL_TYPE)},
        AnnotatedCallLog.TIMESTAMP + " DESC");
  }

  /** Creates a new {@link VoicemailEntry} from the provided cursor using the current position. */
  static VoicemailEntry toVoicemailEntry(Cursor cursor) {
    DialerPhoneNumber number;
    try {
      number = DialerPhoneNumber.parseFrom(cursor.getBlob(NUMBER));
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException("Couldn't parse DialerPhoneNumber bytes");
    }
    NumberAttributes numberAttributes;
    try {
      numberAttributes = NumberAttributes.parseFrom(cursor.getBlob(NUMBER_ATTRIBUTES));
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException("Couldn't parse NumberAttributes bytes");
    }

    return VoicemailEntry.builder()
        .setId(cursor.getInt(ID))
        .setTimestamp(cursor.getLong(TIMESTAMP))
        .setNumber(number)
        .setFormattedNumber(cursor.getString(FORMATTED_NUMBER))
        .setDuration(cursor.getLong(DURATION))
        .setTranscription(cursor.getString(TRANSCRIPTION))
        .setVoicemailUri(cursor.getString(VOICEMAIL_URI))
        .setGeocodedLocation(cursor.getString(GEOCODED_LOCATION))
        .setCallType(cursor.getInt(CALL_TYPE))
        .setIsRead(cursor.getInt(IS_READ))
        .setNumberAttributes(numberAttributes)
        .build();
  }

  static long getTimestamp(Cursor cursor) {
    return cursor.getLong(TIMESTAMP);
  }
}
