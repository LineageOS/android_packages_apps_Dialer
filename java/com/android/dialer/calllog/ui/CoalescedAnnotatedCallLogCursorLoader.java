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

package com.android.dialer.calllog.ui;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.v4.content.CursorLoader;
import com.android.dialer.CallTypes;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.CoalescedAnnotatedCallLog;
import com.android.dialer.common.Assert;
import com.google.protobuf.InvalidProtocolBufferException;

/** CursorLoader for the coalesced annotated call log. */
final class CoalescedAnnotatedCallLogCursorLoader extends CursorLoader {

  /** Indexes for CoalescedAnnotatedCallLog.ALL_COLUMNS */
  private static final int ID = 0;

  private static final int TIMESTAMP = 1;
  private static final int NAME = 2;
  private static final int FORMATTED_NUMBER = 3;
  private static final int PHOTO_URI = 4;
  private static final int PHOTO_ID = 5;
  private static final int LOOKUP_URI = 6;
  private static final int NUMBER_TYPE_LABEL = 7;
  private static final int IS_READ = 8;
  private static final int NEW = 9;
  private static final int GEOCODED_LOCATION = 10;
  private static final int PHONE_ACCOUNT_LABEL = 11;
  private static final int PHONE_ACCOUNT_COLOR = 12;
  private static final int FEATURES = 13;
  private static final int IS_BUSINESS = 14;
  private static final int IS_VOICEMAIL = 15;
  private static final int NUMBER_CALLS = 16;
  private static final int CALL_TYPES = 17;

  /** Convenience class for accessing values using an abbreviated syntax. */
  static final class Row {
    private final Cursor cursor;

    Row(Cursor cursor) {
      this.cursor = cursor;
    }

    long id() {
      return cursor.getInt(ID);
    }

    long timestamp() {
      return cursor.getLong(TIMESTAMP);
    }

    String name() {
      return cursor.getString(NAME);
    }

    String formattedNumber() {
      return cursor.getString(FORMATTED_NUMBER);
    }

    String photoUri() {
      return cursor.getString(PHOTO_URI);
    }

    long photoId() {
      return cursor.getLong(PHOTO_ID);
    }

    String lookupUri() {
      return cursor.getString(LOOKUP_URI);
    }

    String numberTypeLabel() {
      return cursor.getString(NUMBER_TYPE_LABEL);
    }

    boolean isRead() {
      return cursor.getInt(IS_READ) == 1;
    }

    boolean isNew() {
      return cursor.getInt(NEW) == 1;
    }

    String geocodedLocation() {
      return cursor.getString(GEOCODED_LOCATION);
    }

    String phoneAccountLabel() {
      return cursor.getString(PHONE_ACCOUNT_LABEL);
    }

    @ColorInt
    int phoneAccountColor() {
      return cursor.getInt(PHONE_ACCOUNT_COLOR);
    }

    int features() {
      return cursor.getInt(FEATURES);
    }

    boolean isBusiness() {
      return cursor.getInt(IS_BUSINESS) == 1;
    }

    boolean isVoicemail() {
      return cursor.getInt(IS_VOICEMAIL) == 1;
    }

    int numberCalls() {
      return cursor.getInt(NUMBER_CALLS);
    }

    @NonNull
    CallTypes callTypes() {
      try {
        return CallTypes.parseFrom(cursor.getBlob(CALL_TYPES));
      } catch (InvalidProtocolBufferException e) {
        throw Assert.createAssertionFailException("Couldn't parse call types", e);
      }
    }
  }

  CoalescedAnnotatedCallLogCursorLoader(Context context) {
    // CoalescedAnnotatedCallLog requires that PROJECTION be ALL_COLUMNS and the following params be
    // null.
    super(
        context,
        CoalescedAnnotatedCallLog.CONTENT_URI,
        CoalescedAnnotatedCallLog.ALL_COLUMNS,
        null,
        null,
        null);
  }
}
