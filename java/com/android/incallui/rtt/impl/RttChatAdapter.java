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
import android.support.annotation.MainThread;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Adapter class for holding RTT chat data. */
public class RttChatAdapter extends RecyclerView.Adapter<RttChatMessageViewHolder> {

  interface MessageListener {
    void newMessageAdded();
  }

  private final Context context;
  private final List<RttChatMessage> rttMessages = new ArrayList<>();
  private int lastIndexOfLocalMessage = -1;
  private int lastIndexOfRemoteMessage = -1;
  private final TypeBot typeBot;
  private final MessageListener messageListener;

  RttChatAdapter(Context context, MessageListener listener) {
    this.context = context;
    this.messageListener = listener;
    typeBot = new TypeBot(text -> ThreadUtil.postOnUiThread(() -> addRemoteMessage(text)));
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
    RttChatMessage[] newMessages = RttChatMessage.getRemoteRttChatMessage(rttChatMessage, newText);

    if (rttChatMessage == null) {
      lastIndexOfRemoteMessage = rttMessages.size();
      rttMessages.add(lastIndexOfRemoteMessage, newMessages[0]);
      rttMessages.addAll(Arrays.asList(newMessages).subList(1, newMessages.length));
      notifyItemRangeInserted(lastIndexOfRemoteMessage, newMessages.length);
      lastIndexOfRemoteMessage = rttMessages.size() - 1;
    } else {
      rttMessages.set(lastIndexOfRemoteMessage, newMessages[0]);
      int lastIndex = rttMessages.size();
      rttMessages.addAll(Arrays.asList(newMessages).subList(1, newMessages.length));

      notifyItemChanged(lastIndexOfRemoteMessage);
      notifyItemRangeInserted(lastIndex, newMessages.length);
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
      notifyItemChanged(lastIndexOfLocalMessage);
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
    startChatBot();
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

  private void startChatBot() {
    typeBot.scheduleMessage();
  }

  // TODO(wangqi): Move this out of this class once a bug is fixed.
  private static class TypeBot {
    interface Callback {
      void type(String text);
    }

    private static final String[] CANDIDATE_MESSAGES =
        new String[] {
          "To RTT or not to RTT, that is the question...",
          "Making TTY great again!",
          "I would be more comfortable with real \"Thyme\" chatting."
              + " I don't know how to end this pun",
          "お疲れ様でした",
          "The FCC has mandated that I respond... I will do so begrudgingly",
          "😂😂😂💯"
        };
    private final Random random = new Random();
    private final Callback callback;
    private final List<String> messageQueue = new ArrayList<>();
    private int currentTypingPosition = -1;
    private String currentTypingMessage = null;

    TypeBot(Callback callback) {
      this.callback = callback;
    }

    @MainThread
    public void scheduleMessage() {
      Assert.isMainThread();
      if (random.nextDouble() < 0.5) {
        return;
      }

      String text = CANDIDATE_MESSAGES[random.nextInt(CANDIDATE_MESSAGES.length)];
      messageQueue.add(text);
      typeMessage();
    }

    @MainThread
    private void typeMessage() {
      Assert.isMainThread();
      if (currentTypingPosition < 0 || currentTypingMessage == null) {
        if (messageQueue.size() <= 0) {
          return;
        }
        currentTypingMessage = messageQueue.remove(0);
        currentTypingPosition = 0;
      }
      if (currentTypingPosition < currentTypingMessage.length()) {
        int size = random.nextInt(currentTypingMessage.length() - currentTypingPosition + 1);
        callback.type(
            currentTypingMessage.substring(currentTypingPosition, currentTypingPosition + size));
        currentTypingPosition = currentTypingPosition + size;
        // Wait up to 2s between typing.
        ThreadUtil.postDelayedOnUiThread(this::typeMessage, 200 * random.nextInt(10));
      } else {
        callback.type(RttChatMessage.BUBBLE_BREAKER);
        currentTypingPosition = -1;
        currentTypingMessage = null;
        // Wait 1-11s between two messages.
        ThreadUtil.postDelayedOnUiThread(this::typeMessage, 1000 * (1 + random.nextInt(10)));
      }
    }
  }
}
