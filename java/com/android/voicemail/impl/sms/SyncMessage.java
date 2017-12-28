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
  private final String syncTriggerEvent;
  // Total number of new messages on the server.
  private final int newMessageCount;
  // UID of the new message.
  private final String messageId;
  // Length of the message.
  private final int messageLength;
  // Content type (voice, video, fax...) of the new message.
  private final String contentType;
  // Sender of the new message.
  private final String sender;
  // Timestamp (in millis) of the new message.
  private final long msgTimeMillis;

  @Override
  public String toString() {
    return "SyncMessage [mSyncTriggerEvent="
        + syncTriggerEvent
        + ", mNewMessageCount="
        + newMessageCount
        + ", mMessageId="
        + messageId
        + ", mMessageLength="
        + messageLength
        + ", mContentType="
        + contentType
        + ", mSender="
        + sender
        + ", mMsgTimeMillis="
        + msgTimeMillis
        + "]";
  }

  public SyncMessage(Bundle wrappedData) {
    syncTriggerEvent = getString(wrappedData, OmtpConstants.SYNC_TRIGGER_EVENT);
    messageId = getString(wrappedData, OmtpConstants.MESSAGE_UID);
    messageLength = getInt(wrappedData, OmtpConstants.MESSAGE_LENGTH);
    contentType = getString(wrappedData, OmtpConstants.CONTENT_TYPE);
    sender = getString(wrappedData, OmtpConstants.SENDER);
    newMessageCount = getInt(wrappedData, OmtpConstants.NUM_MESSAGE_COUNT);
    msgTimeMillis = parseTime(wrappedData.getString(OmtpConstants.TIME));
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
    return syncTriggerEvent;
  }

  /** @return the number of new messages stored on the voicemail server. */
  @NeededForTesting
  public int getNewMessageCount() {
    return newMessageCount;
  }

  /**
   * @return the message ID of the new message.
   *     <p>Expected to be set only for {@link OmtpConstants#NEW_MESSAGE}
   */
  public String getId() {
    return messageId;
  }

  /**
   * @return the content type of the new message.
   *     <p>Expected to be set only for {@link OmtpConstants#NEW_MESSAGE}
   */
  @NeededForTesting
  public String getContentType() {
    return contentType;
  }

  /**
   * @return the message length of the new message.
   *     <p>Expected to be set only for {@link OmtpConstants#NEW_MESSAGE}
   */
  public int getLength() {
    return messageLength;
  }

  /**
   * @return the sender's phone number of the new message specified as MSISDN.
   *     <p>Expected to be set only for {@link OmtpConstants#NEW_MESSAGE}
   */
  public String getSender() {
    return sender;
  }

  /**
   * @return the timestamp as milliseconds for the new message.
   *     <p>Expected to be set only for {@link OmtpConstants#NEW_MESSAGE}
   */
  public long getTimestampMillis() {
    return msgTimeMillis;
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
