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

package com.android.dialer.simulator.impl;

import android.annotation.TargetApi;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.telecom.Connection.RttTextStream;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.incallui.rtt.protocol.Constants;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Chat bot to generate remote RTT chat messages. */
@TargetApi(28)
class RttChatBot {

  interface Callback {
    void type(String text);
  }

  private static final int START_SENDING = 1;
  private static final int SEND_MESSAGE = 2;

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

  private final MessageHandler messageHandler;
  private final HandlerThread handlerThread;

  RttChatBot(RttTextStream rttTextStream) {
    handlerThread = new HandlerThread("RttChatBot");
    handlerThread.start();
    messageHandler = new MessageHandler(handlerThread.getLooper(), rttTextStream);
  }

  @MainThread
  void start() {
    Assert.isMainThread();
    LogUtil.enterBlock("RttChatBot.start");
    messageHandler.sendEmptyMessage(START_SENDING);
  }

  @MainThread
  void stop() {
    Assert.isMainThread();
    LogUtil.enterBlock("RttChatBot.stop");
    if (handlerThread != null && handlerThread.isAlive()) {
      handlerThread.quit();
    }
  }

  private static class MessageHandler extends Handler {
    private final RttTextStream rttTextStream;
    private final Random random = new Random();
    private final List<String> messageQueue = new ArrayList<>();
    private int currentTypingPosition = -1;
    private String currentTypingMessage = null;

    MessageHandler(Looper looper, RttTextStream rttTextStream) {
      super(looper);
      this.rttTextStream = rttTextStream;
    }

    @Override
    public void handleMessage(android.os.Message msg) {
      switch (msg.what) {
        case START_SENDING:
          sendMessage(obtainMessage(SEND_MESSAGE, nextTyping()));
          break;
        case SEND_MESSAGE:
          String message = (String) msg.obj;
          try {
            rttTextStream.write(message);
          } catch (IOException e) {
            LogUtil.e("RttChatBot.MessageHandler", "write message", e);
          }
          if (Constants.BUBBLE_BREAKER.equals(message)) {
            // Wait 1-11s between two messages.
            sendMessageDelayed(
                obtainMessage(SEND_MESSAGE, nextTyping()), 1000 * (1 + random.nextInt(10)));
          } else {
            // Wait up to 2s between typing.
            sendMessageDelayed(obtainMessage(SEND_MESSAGE, nextTyping()), 200 * random.nextInt(10));
          }
          break;
        default: // fall out
      }
    }

    private String nextTyping() {
      if (currentTypingPosition < 0 || currentTypingMessage == null) {
        if (messageQueue.isEmpty()) {
          String text = CANDIDATE_MESSAGES[random.nextInt(CANDIDATE_MESSAGES.length)];
          messageQueue.add(text);
        }
        currentTypingMessage = messageQueue.remove(0);
        currentTypingPosition = 0;
      }
      if (currentTypingPosition < currentTypingMessage.length()) {
        int size = random.nextInt(currentTypingMessage.length() - currentTypingPosition + 1);
        String messageToType =
            currentTypingMessage.substring(currentTypingPosition, currentTypingPosition + size);
        currentTypingPosition = currentTypingPosition + size;
        return messageToType;
      } else {
        currentTypingPosition = -1;
        currentTypingMessage = null;
        return Constants.BUBBLE_BREAKER;
      }
    }
  }
}
