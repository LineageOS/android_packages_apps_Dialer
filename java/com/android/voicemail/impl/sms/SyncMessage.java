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
package com.android.voicemail.impl.sms;

import android.os.Bundle;
import android.support.annotation.Nullable;
import com.android.voicemail.impl.NeededForTesting;
import com.android.voicemail.impl.OmtpConstants;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Structured data representation of an OMTP SYNC message.
 *
 * <p>Getters will return null if the field was not set in the message body or it could not be
 * parsed.
 */
public class SyncMessage {
  // Sync event that triggered this message.
  private final String mSyncTriggerEvent;
  // Total number of new messages on the server.
  private final int mNewMessageCount;
  // UID of the new message.
  private final String mMessageId;
  // Length of the message.
  private final int mMessageLength;
  // Content type (voice, video, fax...) of the new message.
  private final String mContentType;
  // Sender of the new message.
  private final String mSender;
  // Timestamp (in millis) of the new message.
  private final long mMsgTimeMillis;

  @Override
  public String toString() {
    return "SyncMessage [mSyncTriggerEvent="
        + mSyncTriggerEvent
        + ", mNewMessageCount="
        + mNewMessageCount
        + ", mMessageId="
        + mMessageId
        + ", mMessageLength="
        + mMessageLength
        + ", mContentType="
        + mContentType
        + ", mSender="
        + mSender
        + ", mMsgTimeMillis="
        + mMsgTimeMillis
        + "]";
  }

  public SyncMessage(Bundle wrappedData) {
    mSyncTriggerEvent = getString(wrappedData, OmtpConstants.SYNC_TRIGGER_EVENT);
    mMessageId = getString(wrappedData, OmtpConstants.MESSAGE_UID);
    mMessageLength = getInt(wrappedData, OmtpConstants.MESSAGE_LENGTH);
    mContentType = getString(wrappedData, OmtpConstants.CONTENT_TYPE);
    mSender = getString(wrappedData, OmtpConstants.SENDER);
    mNewMessageCount = getInt(wrappedData, OmtpConstants.NUM_MESSAGE_COUNT);
    mMsgTimeMillis = parseTime(wrappedData.getString(OmtpConstants.TIME));
  }

  private static long parseTime(@Nullable String value) {
    if (value == null) {
      return 0L;
    }
    try {
      return new SimpleDateFormat(OmtpConstants.DATE_TIME_FORMAT, Locale.US).parse(value).getTime();
    } catch (ParseException e) {
      return 0L;
    }
  }
  /**
   * @return the event that triggered the sync message. This is a mandatory field and must always be
   *     set.
   */
  public String getSyncTriggerEvent() {
    return mSyncTriggerEvent;
  }

  /** @return the number of new messages stored on the voicemail server. */
  @NeededForTesting
  public int getNewMessageCount() {
    return mNewMessageCount;
  }

  /**
   * @return the message ID of the new message.
   *     <p>Expected to be set only for {@link OmtpConstants#NEW_MESSAGE}
   */
  public String getId() {
    return mMessageId;
  }

  /**
   * @return the content type of the new message.
   *     <p>Expected to be set only for {@link OmtpConstants#NEW_MESSAGE}
   */
  @NeededForTesting
  public String getContentType() {
    return mContentType;
  }

  /**
   * @return the message length of the new message.
   *     <p>Expected to be set only for {@link OmtpConstants#NEW_MESSAGE}
   */
  public int getLength() {
    return mMessageLength;
  }

  /**
   * @return the sender's phone number of the new message specified as MSISDN.
   *     <p>Expected to be set only for {@link OmtpConstants#NEW_MESSAGE}
   */
  public String getSender() {
    return mSender;
  }

  /**
   * @return the timestamp as milliseconds for the new message.
   *     <p>Expected to be set only for {@link OmtpConstants#NEW_MESSAGE}
   */
  public long getTimestampMillis() {
    return mMsgTimeMillis;
  }

  private static int getInt(Bundle wrappedData, String key) {
    String value = wrappedData.getString(key);
    if (value == null) {
      return 0;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static String getString(Bundle wrappedData, String key) {
    String value = wrappedData.getString(key);
    if (value == null) {
      return "";
    }
    return value;
  }
}
