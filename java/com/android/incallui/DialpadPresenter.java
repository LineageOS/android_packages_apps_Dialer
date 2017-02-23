/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.incallui;

import android.telephony.PhoneNumberUtils;
import com.android.incallui.DialpadPresenter.DialpadUi;
import com.android.incallui.baseui.Presenter;
import com.android.incallui.baseui.Ui;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.TelecomAdapter;

/** Logic for call buttons. */
public class DialpadPresenter extends Presenter<DialpadUi>
    implements InCallPresenter.InCallStateListener {

  private DialerCall mCall;

  @Override
  public void onUiReady(DialpadUi ui) {
    super.onUiReady(ui);
    InCallPresenter.getInstance().addListener(this);
    mCall = CallList.getInstance().getOutgoingOrActive();
  }

  @Override
  public void onUiUnready(DialpadUi ui) {
    super.onUiUnready(ui);
    InCallPresenter.getInstance().removeListener(this);
  }

  @Override
  public void onStateChange(
      InCallPresenter.InCallState oldState,
      InCallPresenter.InCallState newState,
      CallList callList) {
    mCall = callList.getOutgoingOrActive();
    Log.d(this, "DialpadPresenter mCall = " + mCall);
  }

  /**
   * Processes the specified digit as a DTMF key, by playing the appropriate DTMF tone, and
   * appending the digit to the EditText field that displays the DTMF digits sent so far.
   */
  public final void processDtmf(char c) {
    Log.d(this, "Processing dtmf key " + c);
    // if it is a valid key, then update the display and send the dtmf tone.
    if (PhoneNumberUtils.is12Key(c) && mCall != null) {
      Log.d(this, "updating display and sending dtmf tone for '" + c + "'");

      // Append this key to the "digits" widget.
      DialpadUi dialpadUi = getUi();
      if (dialpadUi != null) {
        dialpadUi.appendDigitsToField(c);
      }
      // Plays the tone through Telecom.
      TelecomAdapter.getInstance().playDtmfTone(mCall.getId(), c);
    } else {
      Log.d(this, "ignoring dtmf request for '" + c + "'");
    }
  }

  /** Stops the local tone based on the phone type. */
  public void stopDtmf() {
    if (mCall != null) {
      Log.d(this, "stopping remote tone");
      TelecomAdapter.getInstance().stopDtmfTone(mCall.getId());
    }
  }

  public interface DialpadUi extends Ui {

    void setVisible(boolean on);

    void appendDigitsToField(char digit);
  }
}
