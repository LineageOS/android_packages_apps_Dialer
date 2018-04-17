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
 * limitations under the License.
 */

package com.android.incallui;

import android.annotation.TargetApi;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.telecom.Call.RttCall;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.rtt.RttTranscript;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.rtt.protocol.RttCallScreen;
import com.android.incallui.rtt.protocol.RttCallScreenDelegate;
import java.io.IOException;

/**
 * Logic related to the {@link RttCallScreen} and for managing changes to the RTT calling surfaces
 * based on other user interface events and incoming events.
 */
@TargetApi(28)
public class RttCallPresenter implements RttCallScreenDelegate, InCallStateListener {

  private RttCallScreen rttCallScreen;
  private RttCall rttCall;
  private HandlerThread handlerThread;
  private RemoteMessageHandler remoteMessageHandler;

  @Override
  public void initRttCallScreenDelegate(RttCallScreen rttCallScreen) {
    this.rttCallScreen = rttCallScreen;
  }

  @Override
  public void onLocalMessage(String message) {
    if (rttCall == null) {
      LogUtil.w("RttCallPresenter.onLocalMessage", "Rtt Call is not started yet");
      return;
    }
    remoteMessageHandler.writeMessage(message);
  }

  @Override
  public void onRttCallScreenUiReady() {
    LogUtil.enterBlock("RttCallPresenter.onRttCallScreenUiReady");
    InCallPresenter.getInstance().addListener(this);
    startListenOnRemoteMessage();
    DialerCall call = CallList.getInstance().getCallById(rttCallScreen.getCallId());
    if (call != null) {
      rttCallScreen.onRestoreRttChat(call.getRttTranscript());
    }
  }

  @Override
  public void onSaveRttTranscript() {
    LogUtil.enterBlock("RttCallPresenter.onSaveRttTranscript");
    DialerCall call = CallList.getInstance().getCallById(rttCallScreen.getCallId());
    if (call != null) {
      saveTranscript(call);
    }
  }

  @Override
  public void onRttCallScreenUiUnready() {
    LogUtil.enterBlock("RttCallPresenter.onRttCallScreenUiUnready");
    InCallPresenter.getInstance().removeListener(this);
    stopListenOnRemoteMessage();
    onSaveRttTranscript();
  }

  private void saveTranscript(DialerCall dialerCall) {
    LogUtil.enterBlock("RttCallPresenter.saveTranscript");
    RttTranscript.Builder builder = RttTranscript.newBuilder();
    builder

        .setId(String.valueOf(dialerCall.getCreationTimeMillis()))

        .setTimestamp(dialerCall.getCreationTimeMillis())
        .setNumber(dialerCall.getNumber())
        .addAllMessages(rttCallScreen.getRttTranscriptMessageList());
    dialerCall.setRttTranscript(builder.build());
  }

  @Override
  public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
    LogUtil.enterBlock("RttCallPresenter.onStateChange");
    if (newState == InCallState.INCALL) {
      startListenOnRemoteMessage();
    }
  }

  private void startListenOnRemoteMessage() {
    DialerCall call = CallList.getInstance().getCallById(rttCallScreen.getCallId());
    if (call == null) {
      LogUtil.i("RttCallPresenter.startListenOnRemoteMessage", "call does not exist");
      return;
    }
    rttCall = call.getRttCall();
    if (rttCall == null) {
      LogUtil.i("RttCallPresenter.startListenOnRemoteMessage", "RTT Call is not started yet");
      return;
    }
    if (handlerThread != null && handlerThread.isAlive()) {
      LogUtil.i("RttCallPresenter.startListenOnRemoteMessage", "already running");
      return;
    }
    handlerThread = new HandlerThread("RttCallRemoteMessageHandler");
    handlerThread.start();
    remoteMessageHandler =
        new RemoteMessageHandler(handlerThread.getLooper(), rttCall, rttCallScreen);
    remoteMessageHandler.start();
  }

  private void stopListenOnRemoteMessage() {
    if (handlerThread != null && handlerThread.isAlive()) {
      handlerThread.quit();
    }
  }

  private static class RemoteMessageHandler extends Handler {
    private static final int START = 1;
    private static final int READ_MESSAGE = 2;
    private static final int WRITE_MESSAGE = 3;

    private final RttCall rttCall;
    private final RttCallScreen rttCallScreen;

    RemoteMessageHandler(Looper looper, RttCall rttCall, RttCallScreen rttCallScreen) {
      super(looper);
      this.rttCall = rttCall;
      this.rttCallScreen = rttCallScreen;
    }

    @Override
    public void handleMessage(android.os.Message msg) {
      switch (msg.what) {
        case START:
          sendEmptyMessage(READ_MESSAGE);
          break;
        case READ_MESSAGE:
          try {
            final String message = rttCall.readImmediately();
            if (message != null) {
              ThreadUtil.postOnUiThread(() -> rttCallScreen.onRemoteMessage(message));
            }
          } catch (IOException e) {
            LogUtil.e("RttCallPresenter.RemoteMessageHandler.handleMessage", "read message", e);
          }
          sendEmptyMessageDelayed(READ_MESSAGE, 200);
          break;
        case WRITE_MESSAGE:
          try {
            rttCall.write((String) msg.obj);
          } catch (IOException e) {
            LogUtil.e("RttCallPresenter.RemoteMessageHandler.handleMessage", "write message", e);
          }
          break;
        default: // fall out
      }
    }

    void start() {
      sendEmptyMessage(START);
    }

    void writeMessage(String message) {
      sendMessage(obtainMessage(WRITE_MESSAGE, message));
    }
  }
}
