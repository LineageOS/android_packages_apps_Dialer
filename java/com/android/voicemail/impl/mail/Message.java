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
 * limitations under the License.
 */
package com.android.voicemail.impl.mail;

import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import java.util.Date;
import java.util.HashSet;

public abstract class Message implements Part, Body {
  public static final Message[] EMPTY_ARRAY = new Message[0];

  public static final String RECIPIENT_TYPE_TO = "to";
  public static final String RECIPIENT_TYPE_CC = "cc";
  public static final String RECIPIENT_TYPE_BCC = "bcc";

  public enum RecipientType {
    TO,
    CC,
    BCC,
  }

  protected String mUid;

  private HashSet<String> mFlags = null;

  protected Date mInternalDate;

  public String getUid() {
    return mUid;
  }

  public void setUid(String uid) {
    this.mUid = uid;
  }

  public abstract String getSubject() throws MessagingException;

  public abstract void setSubject(String subject) throws MessagingException;

  public Date getInternalDate() {
    return mInternalDate;
  }

  public void setInternalDate(Date internalDate) {
    this.mInternalDate = internalDate;
  }

  public abstract Date getReceivedDate() throws MessagingException;

  public abstract Date getSentDate() throws MessagingException;

  public abstract void setSentDate(Date sentDate) throws MessagingException;

  @Nullable
  public abstract Long getDuration() throws MessagingException;

  public abstract Address[] getRecipients(String type) throws MessagingException;

  public abstract void setRecipients(String type, Address[] addresses) throws MessagingException;

  public void setRecipient(String type, Address address) throws MessagingException {
    setRecipients(type, new Address[] {address});
  }

  public abstract Address[] getFrom() throws MessagingException;

  public abstract void setFrom(Address from) throws MessagingException;

  public abstract Address[] getReplyTo() throws MessagingException;

  public abstract void setReplyTo(Address[] from) throws MessagingException;

  // Always use these instead of getHeader("Message-ID") or setHeader("Message-ID");
  public abstract void setMessageId(String messageId) throws MessagingException;

  public abstract String getMessageId() throws MessagingException;

  @Override
  public boolean isMimeType(String mimeType) throws MessagingException {
    return getContentType().startsWith(mimeType);
  }

  private HashSet<String> getFlagSet() {
    if (mFlags == null) {
      mFlags = new HashSet<String>();
    }
    return mFlags;
  }

  /*
   * TODO Refactor Flags at some point to be able to store user defined flags.
   */
  public String[] getFlags() {
    return getFlagSet().toArray(new String[] {});
  }

  /**
   * Set/clear a flag directly, without involving overrides of {@link #setFlag} in subclasses. Only
   * used for testing.
   */
  @VisibleForTesting
  private final void setFlagDirectlyForTest(String flag, boolean set) throws MessagingException {
    if (set) {
      getFlagSet().add(flag);
    } else {
      getFlagSet().remove(flag);
    }
  }

  public void setFlag(String flag, boolean set) throws MessagingException {
    setFlagDirectlyForTest(flag, set);
  }

  /**
   * This method calls setFlag(String, boolean)
   *
   * @param flags
   * @param set
   */
  public void setFlags(String[] flags, boolean set) throws MessagingException {
    for (String flag : flags) {
      setFlag(flag, set);
    }
  }

  public boolean isSet(String flag) {
    return getFlagSet().contains(flag);
  }

  public abstract void saveChanges() throws MessagingException;

  @Override
  public String toString() {
    return getClass().getSimpleName() + ':' + mUid;
  }
}
