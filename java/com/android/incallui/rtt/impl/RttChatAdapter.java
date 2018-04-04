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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.common.LogUtil;
import com.android.dialer.rtt.RttTranscript;
import com.android.dialer.rtt.RttTranscriptMessage;
import java.util.ArrayList;
import java.util.List;

/** Adapter class for holding RTT chat data. */
public class RttChatAdapter extends RecyclerView.Adapter<RttChatMessageViewHolder> {

  private Drawable avatarDrawable;

  interface MessageListener {
    void onUpdateRemoteMessage(int position);

    void onUpdateLocalMessage(int position);
  }

  private final Context context;
  private List<RttChatMessage> rttMessages = new ArrayList<>();
  private int lastIndexOfLocalMessage = -1;
  private final MessageListener messageListener;

  RttChatAdapter(Context context, MessageListener listener) {
    this.context = context;
    this.messageListener = listener;
  }

  @Override
  public RttChatMessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    LayoutInflater layoutInflater = LayoutInflater.from(context);
    View view = layoutInflater.inflate(R.layout.rtt_chat_list_item, parent, false);
    return new RttChatMessageViewHolder(view);
  }

  @Override
  public int getItemViewType(int position) {
    return super.getItemViewType(position);
  }

  @Override
  public void onBindViewHolder(RttChatMessageViewHolder rttChatMessageViewHolder, int i) {
    boolean isSameGroup = false;
    if (i > 0) {
      isSameGroup = rttMessages.get(i).isRemote == rttMessages.get(i - 1).isRemote;
    }
    rttChatMessageViewHolder.setMessage(rttMessages.get(i), isSameGroup, avatarDrawable);
  }

  @Override
  public int getItemCount() {
    return rttMessages.size();
  }

  private void updateCurrentLocalMessage(String newMessage) {
    RttChatMessage rttChatMessage = null;
    if (lastIndexOfLocalMessage >= 0) {
      rttChatMessage = rttMessages.get(lastIndexOfLocalMessage);
    }
    if (rttChatMessage == null || rttChatMessage.isFinished()) {
      rttChatMessage = new RttChatMessage();
      rttChatMessage.append(newMessage);
      rttMessages.add(rttChatMessage);
      lastIndexOfLocalMessage = rttMessages.size() - 1;
      notifyItemInserted(lastIndexOfLocalMessage);
    } else {
      rttChatMessage.append(newMessage);
      // Clear empty message bubble.
      if (TextUtils.isEmpty(rttChatMessage.getContent())) {
        rttMessages.remove(lastIndexOfLocalMessage);
        notifyItemRemoved(lastIndexOfLocalMessage);
        lastIndexOfLocalMessage = -1;
      } else {
        notifyItemChanged(lastIndexOfLocalMessage);
      }
    }
  }

  private void updateCurrentRemoteMessage(String newMessage) {
    RttChatMessage.updateRemoteRttChatMessage(rttMessages, newMessage);
    lastIndexOfLocalMessage = RttChatMessage.getLastIndexLocalMessage(rttMessages);
    notifyDataSetChanged();
  }

  void addLocalMessage(String message) {
    updateCurrentLocalMessage(message);
    if (messageListener != null) {
      messageListener.onUpdateLocalMessage(lastIndexOfLocalMessage);
    }
  }

  void submitLocalMessage() {
    LogUtil.enterBlock("RttChatAdapater.submitLocalMessage");
    rttMessages.get(lastIndexOfLocalMessage).finish();
    notifyItemChanged(lastIndexOfLocalMessage);
    lastIndexOfLocalMessage = -1;
  }

  String computeChangeOfLocalMessage(String newMessage) {
    RttChatMessage rttChatMessage = null;
    if (lastIndexOfLocalMessage >= 0) {
      rttChatMessage = rttMessages.get(lastIndexOfLocalMessage);
    }
    if (rttChatMessage == null || rttChatMessage.isFinished()) {
      return newMessage;
    } else {
      return RttChatMessage.computeChangedString(rttChatMessage.getContent(), newMessage);
    }
  }

  void addRemoteMessage(String message) {
    if (TextUtils.isEmpty(message)) {
      return;
    }
    updateCurrentRemoteMessage(message);
    if (messageListener != null) {
      messageListener.onUpdateRemoteMessage(RttChatMessage.getLastIndexRemoteMessage(rttMessages));
    }
  }

  /**
   * Retrieve last local message and update the index. This is used when deleting to previous
   * message bubble.
   */
  @Nullable
  String retrieveLastLocalMessage() {
    lastIndexOfLocalMessage = RttChatMessage.getLastIndexLocalMessage(rttMessages);
    if (lastIndexOfLocalMessage >= 0) {
      RttChatMessage rttChatMessage = rttMessages.get(lastIndexOfLocalMessage);
      rttChatMessage.unfinish();
      return rttChatMessage.getContent();
    } else {
      return null;
    }
  }

  void setAvatarDrawable(Drawable drawable) {
    avatarDrawable = drawable;
  }

  /**
   * Restores RTT chat history from {@code RttTranscript}.
   *
   * @param rttTranscript transcript saved previously.
   * @return last unfinished local message, return null if there is no current editing local
   *     message.
   */
  @Nullable
  String onRestoreRttChat(RttTranscript rttTranscript) {
    LogUtil.enterBlock("RttChatAdapater.onRestoreRttChat");
    rttMessages = RttChatMessage.fromTranscript(rttTranscript);
    lastIndexOfLocalMessage = RttChatMessage.getLastIndexLocalMessage(rttMessages);
    notifyDataSetChanged();
    if (lastIndexOfLocalMessage < 0) {
      return null;
    }
    RttChatMessage message = rttMessages.get(lastIndexOfLocalMessage);
    if (!message.isFinished()) {
      return message.getContent();
    } else {
      return null;
    }
  }

  List<RttTranscriptMessage> getRttTranscriptMessageList() {
    return RttChatMessage.toTranscriptMessageList(rttMessages);
  }
}
