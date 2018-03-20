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
import android.text.TextUtils;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.NumberAttributes;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
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
        AnnotatedCallLog.TRANSCRIPTION_STATE,
        AnnotatedCallLog.PHONE_ACCOUNT_COMPONENT_NAME,
        AnnotatedCallLog.PHONE_ACCOUNT_ID,
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
  private static final int TRANSCRIPTION_STATE = 11;
  private static final int PHONE_ACCOUNT_COMPONENT_NAME = 12;
  private static final int PHONE_ACCOUNT_ID = 13;

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

    // Voicemail numbers should always be valid so the CP2 information should never be incomplete,
    // and there should be no need to query PhoneLookup at render time.
    Assert.checkArgument(
        !numberAttributes.getIsCp2InfoIncomplete(),
        "CP2 info incomplete for number: %s",
        LogUtil.sanitizePii(number.getNormalizedNumber()));

    VoicemailEntry.Builder voicemailEntryBuilder =
        VoicemailEntry.newBuilder()
            .setId(cursor.getInt(ID))
            .setTimestamp(cursor.getLong(TIMESTAMP))
            .setNumber(number)
            .setDuration(cursor.getLong(DURATION))
            .setCallType(cursor.getInt(CALL_TYPE))
            .setIsRead(cursor.getInt(IS_READ))
            .setNumberAttributes(numberAttributes)
            .setTranscriptionState(cursor.getInt(TRANSCRIPTION_STATE));

    String formattedNumber = cursor.getString(FORMATTED_NUMBER);
    if (!TextUtils.isEmpty(formattedNumber)) {
      voicemailEntryBuilder.setFormattedNumber(formattedNumber);
    }

    String geocodedLocation = cursor.getString(GEOCODED_LOCATION);
    if (!TextUtils.isEmpty(geocodedLocation)) {
      voicemailEntryBuilder.setGeocodedLocation(geocodedLocation);
    }

    String transcription = cursor.getString(TRANSCRIPTION);
    if (!TextUtils.isEmpty(transcription)) {
      voicemailEntryBuilder.setTranscription(transcription);
    }

    String voicemailUri = cursor.getString(VOICEMAIL_URI);
    if (!TextUtils.isEmpty(voicemailUri)) {
      voicemailEntryBuilder.setVoicemailUri(voicemailUri);
    }

    String phoneAccountComponentName = cursor.getString(PHONE_ACCOUNT_COMPONENT_NAME);
    if (!TextUtils.isEmpty(phoneAccountComponentName)) {
      voicemailEntryBuilder.setPhoneAccountComponentName(phoneAccountComponentName);
    }

    String phoneAccountId = cursor.getString(PHONE_ACCOUNT_ID);
    if (!TextUtils.isEmpty(phoneAccountId)) {
      voicemailEntryBuilder.setPhoneAccountId(phoneAccountId);
    }

    return voicemailEntryBuilder.build();
  }

  static long getTimestamp(Cursor cursor) {
    return cursor.getLong(TIMESTAMP);
  }
}
