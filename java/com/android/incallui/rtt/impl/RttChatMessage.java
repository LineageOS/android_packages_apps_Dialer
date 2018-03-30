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

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import com.android.dialer.common.Assert;
import com.android.incallui.rtt.protocol.Constants;
import com.google.common.base.Splitter;
import java.util.Iterator;
import java.util.List;

/** Message class that holds one RTT chat content. */
final class RttChatMessage implements Parcelable {

  private static final Splitter SPLITTER = Splitter.on(Constants.BUBBLE_BREAKER);

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

  void unfinish() {
    isFinished = false;
  }

  public void append(String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\b' && content.length() > 0 && content.charAt(content.length() - 1) != '\b') {
        content.deleteCharAt(content.length() - 1);
      } else {
        content.append(c);
      }
    }
  }

  public String getContent() {
    return content.toString();
  }

  /**
   * Computes delta change of two string.
   *
   * <p>e.g. "hello world" -> "hello" : "\b\b\b\b\b\b"
   *
   * <p>"hello world" -> "hello mom!" : "\b\b\b\b\bmom!"
   *
   * <p>"hello world" -> "hello d" : "\b\b\b\b\bd"
   *
   * <p>"hello world" -> "hello new world" : "\b\b\b\b\bnew world"
   */
  static String computeChangedString(String oldMessage, String newMesssage) {
    StringBuilder modify = new StringBuilder();
    int indexChangeStart = 0;
    while (indexChangeStart < oldMessage.length()
        && indexChangeStart < newMesssage.length()
        && oldMessage.charAt(indexChangeStart) == newMesssage.charAt(indexChangeStart)) {
      indexChangeStart++;
    }
    for (int i = indexChangeStart; i < oldMessage.length(); i++) {
      modify.append('\b');
    }
    for (int i = indexChangeStart; i < newMesssage.length(); i++) {
      modify.append(newMesssage.charAt(i));
    }
    return modify.toString();
  }

  /** Update list of {@code RttChatMessage} based on given remote text. */
  static void updateRemoteRttChatMessage(List<RttChatMessage> messageList, @NonNull String text) {
    Assert.isNotNull(messageList);
    Iterator<String> splitText = SPLITTER.split(text).iterator();

    while (splitText.hasNext()) {
      String singleMessageContent = splitText.next();
      RttChatMessage message;
      int index = getLastIndexUnfinishedRemoteMessage(messageList);
      if (index < 0) {
        message = new RttChatMessage();
        message.append(singleMessageContent);
        message.isRemote = true;
        if (splitText.hasNext()) {
          message.finish();
        }
        if (message.content.length() != 0) {
          messageList.add(message);
        }
      } else {
        message = messageList.get(index);
        message.append(singleMessageContent);
        if (splitText.hasNext()) {
          message.finish();
        }
        if (message.content.length() == 0) {
          messageList.remove(index);
        }
      }
      StringBuilder content = message.content;
      // Delete previous messages.
      while (content.length() > 0 && content.charAt(0) == '\b') {
        messageList.remove(message);
        content.delete(0, 1);
        int previous = getLastIndexRemoteMessage(messageList);
        // There are more backspaces than existing characters.
        if (previous < 0) {
          while (content.length() > 0 && content.charAt(0) == '\b') {
            content.deleteCharAt(0);
          }
          // Add message if there are still characters after backspaces.
          if (content.length() > 0) {
            message = new RttChatMessage();
            message.append(content.toString());
            message.isRemote = true;
            if (splitText.hasNext()) {
              message.finish();
            }
            messageList.add(message);
          }
          break;
        }
        message = messageList.get(previous);
        message.unfinish();
        message.append(content.toString());
        content = message.content;
      }
    }
    if (text.endsWith(Constants.BUBBLE_BREAKER)) {
      int lastIndexRemoteMessage = getLastIndexRemoteMessage(messageList);
      messageList.get(lastIndexRemoteMessage).finish();
    }
  }

  private static int getLastIndexUnfinishedRemoteMessage(List<RttChatMessage> messageList) {
    int i = messageList.size() - 1;
    while (i >= 0 && (!messageList.get(i).isRemote || messageList.get(i).isFinished)) {
      i--;
    }
    return i;
  }

  static int getLastIndexRemoteMessage(List<RttChatMessage> messageList) {
    int i = messageList.size() - 1;
    while (i >= 0 && !messageList.get(i).isRemote) {
      i--;
    }
    return i;
  }

  static int getLastIndexLocalMessage(List<RttChatMessage> messageList) {
    int i = messageList.size() - 1;
    while (i >= 0 && messageList.get(i).isRemote) {
      i--;
    }
    return i;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(getContent());
    boolean[] values = new boolean[2];
    values[0] = isRemote;
    values[1] = isFinished;
    dest.writeBooleanArray(values);
  }

  public static final Parcelable.Creator<RttChatMessage> CREATOR =
      new Parcelable.Creator<RttChatMessage>() {
        @Override
        public RttChatMessage createFromParcel(Parcel in) {
          return new RttChatMessage(in);
        }

        @Override
        public RttChatMessage[] newArray(int size) {
          return new RttChatMessage[size];
        }
      };

  private RttChatMessage(Parcel in) {
    content.append(in.readString());
    boolean[] values = new boolean[2];
    in.readBooleanArray(values);
    isRemote = values[0];
    isFinished = values[1];
  }

  RttChatMessage() {}
}
