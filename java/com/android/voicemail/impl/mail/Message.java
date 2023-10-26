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

import androidx.annotation.Nullable;

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

  protected String uid;

  private HashSet<String> flags = null;

  protected Date internalDate;

  public String getUid() {
    return uid;
  }

  public void setUid(String uid) {
    this.uid = uid;
  }

  public abstract String getSubject() throws MessagingException;

  public abstract void setSubject(String subject) throws MessagingException;

  public void setInternalDate(Date internalDate) {
    this.internalDate = internalDate;
  }

  public abstract Date getSentDate() throws MessagingException;

  @Nullable
  public abstract Long getDuration() throws MessagingException;

  public abstract Address[] getFrom() throws MessagingException;

  public abstract void setFrom(Address from) throws MessagingException;

  // Always use these instead of getHeader("Message-ID") or setHeader("Message-ID");
  public abstract void setMessageId(String messageId) throws MessagingException;

  public abstract String getMessageId() throws MessagingException;

  @Override
  public boolean isMimeType(String mimeType) throws MessagingException {
    return getContentType().startsWith(mimeType);
  }

  private HashSet<String> getFlagSet() {
    if (flags == null) {
      flags = new HashSet<>();
    }
    return flags;
  }

  /*
   * TODO Refactor Flags at some point to be able to store user defined flags.
   */
  public String[] getFlags() {
    return getFlagSet().toArray(new String[] {});
  }

  public void setFlag(String flag, boolean set) throws MessagingException { }

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

  @Override
  public String toString() {
    return getClass().getSimpleName() + ':' + uid;
  }
}
