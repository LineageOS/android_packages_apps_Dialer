/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.incallui.rtt.impl;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextWatcher;
import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Message class that holds one RTT chat content. */
final class RttChatMessage {

  static final String BUBBLE_BREAKER = "\n\n";
  private static final Splitter SPLITTER = Splitter.on(BUBBLE_BREAKER);

  boolean isRemote;
  public boolean hasAvatar;
  private final StringBuilder content = new StringBuilder();
  private boolean isFinished;

  public boolean isFinished() {
    return isFinished;
  }

  public void finish() {
    isFinished = true;
  }

  public void append(String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c != '\b') {
        content.append(c);
      } else if (content.length() > 0) {
        content.deleteCharAt(content.length() - 1);
      }
    }
  }

  public String getContent() {
    return content.toString();
  }

  /**
   * Generates delta change to a text.
   *
   * <p>This is used to track text change of input. See more details in {@link
   * TextWatcher#onTextChanged}
   *
   * <p>e.g. "hello world" -> "hello" : "\b\b\b\b\b\b"
   *
   * <p>"hello world" -> "hello mom!" : "\b\b\b\b\bmom!"
   *
   * <p>"hello world" -> "hello d" : "\b\b\b\b\bd"
   *
   * <p>"hello world" -> "hello new world" : "\b\b\b\b\bnew world"
   */
  static String getChangedString(CharSequence s, int start, int before, int count) {
    StringBuilder modify = new StringBuilder();
    if (before > count) {
      int deleteStart = start + count;
      int deleted = before - count;
      int numberUnModifiedCharsAfterDeleted = s.length() - start - count;
      char c = '\b';
      for (int i = 0; i < deleted + numberUnModifiedCharsAfterDeleted; i++) {
        modify.append(c);
      }
      modify.append(s, deleteStart, s.length());
    } else {
      int insertStart = start + before;
      int numberUnModifiedCharsAfterInserted = s.length() - start - count;
      char c = '\b';
      for (int i = 0; i < numberUnModifiedCharsAfterInserted; i++) {
        modify.append(c);
      }
      modify.append(s, insertStart, s.length());
    }
    return modify.toString();
  }

  /** Convert remote input text into an array of {@code RttChatMessage}. */
  static RttChatMessage[] getRemoteRttChatMessage(
      @Nullable RttChatMessage currentMessage, @NonNull String text) {
    Iterator<String> splitText = SPLITTER.split(text).iterator();
    List<RttChatMessage> messageList = new ArrayList<>();

    String firstMessageContent = splitText.next();
    RttChatMessage firstMessage = currentMessage;
    if (firstMessage == null) {
      firstMessage = new RttChatMessage();
      firstMessage.isRemote = true;
    }
    firstMessage.append(firstMessageContent);
    if (splitText.hasNext() || text.endsWith(BUBBLE_BREAKER)) {
      firstMessage.finish();
    }
    messageList.add(firstMessage);

    while (splitText.hasNext()) {
      String singleMessageContent = splitText.next();
      if (singleMessageContent.isEmpty()) {
        continue;
      }
      RttChatMessage message = new RttChatMessage();
      message.append(singleMessageContent);
      message.isRemote = true;
      if (splitText.hasNext()) {
        message.finish();
      }
      messageList.add(message);
    }

    return messageList.toArray(new RttChatMessage[0]);
  }
}
