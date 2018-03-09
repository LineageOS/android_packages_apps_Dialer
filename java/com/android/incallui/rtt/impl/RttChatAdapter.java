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
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.common.LogUtil;
import java.util.ArrayList;
import java.util.List;

/** Adapter class for holding RTT chat data. */
public class RttChatAdapter extends RecyclerView.Adapter<RttChatMessageViewHolder> {

  interface MessageListener {
    void newMessageAdded();
  }

  private static final String KEY_MESSAGE_DATA = "key_message_data";
  private static final String KEY_LAST_REMOTE_MESSAGE = "key_last_remote_message";
  private static final String KEY_LAST_LOCAL_MESSAGE = "key_last_local_message";

  private final Context context;
  private final List<RttChatMessage> rttMessages;
  private int lastIndexOfLocalMessage = -1;
  private int lastIndexOfRemoteMessage = -1;
  private final MessageListener messageListener;

  RttChatAdapter(Context context, MessageListener listener, @Nullable Bundle savedInstanceState) {
    this.context = context;
    this.messageListener = listener;
    if (savedInstanceState == null) {
      rttMessages = new ArrayList<>();
    } else {
      rttMessages = savedInstanceState.getParcelableArrayList(KEY_MESSAGE_DATA);
      lastIndexOfRemoteMessage = savedInstanceState.getInt(KEY_LAST_REMOTE_MESSAGE);
      lastIndexOfLocalMessage = savedInstanceState.getInt(KEY_LAST_LOCAL_MESSAGE);
    }
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
    rttChatMessageViewHolder.setMessage(rttMessages.get(i), isSameGroup);
  }

  @Override
  public int getItemCount() {
    return rttMessages.size();
  }

  private void updateCurrentRemoteMessage(String newText) {
    RttChatMessage rttChatMessage = null;
    if (lastIndexOfRemoteMessage >= 0) {
      rttChatMessage = rttMessages.get(lastIndexOfRemoteMessage);
    }
    List<RttChatMessage> newMessages =
        RttChatMessage.getRemoteRttChatMessage(rttChatMessage, newText);

    if (rttChatMessage == null) {
      lastIndexOfRemoteMessage = rttMessages.size();
      rttMessages.add(lastIndexOfRemoteMessage, newMessages.get(0));
      rttMessages.addAll(newMessages.subList(1, newMessages.size()));
      notifyItemRangeInserted(lastIndexOfRemoteMessage, newMessages.size());
      lastIndexOfRemoteMessage = rttMessages.size() - 1;
    } else {
      rttMessages.set(lastIndexOfRemoteMessage, newMessages.get(0));
      int lastIndex = rttMessages.size();
      rttMessages.addAll(newMessages.subList(1, newMessages.size()));

      notifyItemChanged(lastIndexOfRemoteMessage);
      notifyItemRangeInserted(lastIndex, newMessages.size());
    }
    if (rttMessages.get(lastIndexOfRemoteMessage).isFinished()) {
      lastIndexOfRemoteMessage = -1;
    }
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
        if (lastIndexOfRemoteMessage > lastIndexOfLocalMessage) {
          lastIndexOfRemoteMessage -= 1;
        }
        lastIndexOfLocalMessage = -1;
      } else {
        notifyItemChanged(lastIndexOfLocalMessage);
      }
    }
  }

  void addLocalMessage(String message) {
    LogUtil.enterBlock("RttChatAdapater.addLocalMessage");
    updateCurrentLocalMessage(message);
    if (messageListener != null) {
      messageListener.newMessageAdded();
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
    LogUtil.enterBlock("RttChatAdapater.addRemoteMessage");
    if (TextUtils.isEmpty(message)) {
      return;
    }
    updateCurrentRemoteMessage(message);
    if (messageListener != null) {
      messageListener.newMessageAdded();
    }
  }

  void onSaveInstanceState(@NonNull Bundle bundle) {
    bundle.putParcelableArrayList(KEY_MESSAGE_DATA, (ArrayList<RttChatMessage>) rttMessages);
    bundle.putInt(KEY_LAST_REMOTE_MESSAGE, lastIndexOfRemoteMessage);
    bundle.putInt(KEY_LAST_LOCAL_MESSAGE, lastIndexOfLocalMessage);
  }
}
