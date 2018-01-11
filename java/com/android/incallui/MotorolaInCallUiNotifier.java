/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * This file is derived in part from code issued under the following license.
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
 *
 */
package com.android.incallui;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.common.LogUtil;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.InCallUiListener;
import com.android.incallui.call.CallList;

/**
 * Responsible for broadcasting the Intent INCOMING_CALL_VISIBILITY_CHANGED so other processes could
 * know when the incoming call activity is started or finished.
 */
public class MotorolaInCallUiNotifier implements InCallUiListener, InCallStateListener {

  @VisibleForTesting static final String EXTRA_VISIBLE_KEY = "visible";

  @VisibleForTesting
  static final String ACTION_INCOMING_CALL_VISIBILITY_CHANGED =
      "com.motorola.incallui.action.INCOMING_CALL_VISIBILITY_CHANGED";

  @VisibleForTesting
  static final String PERMISSION_INCOMING_CALL_VISIBILITY_CHANGED =
      "com.motorola.incallui.permission.INCOMING_CALL_VISIBILITY_CHANGED";

  private final Context context;

  MotorolaInCallUiNotifier(Context context) {
    this.context = context;
  }

  @Override
  public void onUiShowing(boolean showing) {
    if (showing && CallList.getInstance().getIncomingCall() != null) {
      sendInCallUiBroadcast(true);
    }
  }

  @Override
  public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
    if (oldState != null
        && oldState.isConnectingOrConnected()
        && newState == InCallState.NO_CALLS) {
      sendInCallUiBroadcast(false);
    }
  }

  private void sendInCallUiBroadcast(boolean visible) {
    LogUtil.d(
        "MotorolaInCallUiNotifier.sendInCallUiBroadcast",
        "Send InCallUi Broadcast, visible: " + visible);
    Intent intent = new Intent();
    intent.putExtra(EXTRA_VISIBLE_KEY, visible);
    intent.setAction(ACTION_INCOMING_CALL_VISIBILITY_CHANGED);
    context.sendBroadcast(intent, PERMISSION_INCOMING_CALL_VISIBILITY_CHANGED);
  }
}
