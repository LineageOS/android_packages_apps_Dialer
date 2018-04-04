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

package com.android.incallui.rtt.protocol;

import android.support.v4.app.Fragment;
import com.android.dialer.rtt.RttTranscript;
import com.android.dialer.rtt.RttTranscriptMessage;
import com.android.incallui.incall.protocol.InCallScreen;
import java.util.List;

/** Interface for call RTT call module. */
public interface RttCallScreen extends InCallScreen {

  void onRttScreenStart();

  void onRttScreenStop();

  void onRemoteMessage(String message);

  void onRestoreRttChat(RttTranscript rttTranscript);

  List<RttTranscriptMessage> getRttTranscriptMessageList();

  Fragment getRttCallScreenFragment();

  String getCallId();
}
