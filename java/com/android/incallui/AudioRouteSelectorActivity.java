/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.telecom.CallAudioState;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.incallui.audiomode.AudioModeProvider;
import com.android.incallui.audioroute.AudioRouteSelectorDialogFragment;
import com.android.incallui.audioroute.AudioRouteSelectorDialogFragment.AudioRouteSelectorPresenter;
import com.android.incallui.call.CallList;
import com.android.incallui.call.CallList.Listener;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.TelecomAdapter;

/** Simple activity that just shows the audio route selector fragment */
public class AudioRouteSelectorActivity extends FragmentActivity
    implements AudioRouteSelectorPresenter, Listener {

  @Override
  protected void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);
    AudioRouteSelectorDialogFragment.newInstance(AudioModeProvider.getInstance().getAudioState())
        .show(getSupportFragmentManager(), AudioRouteSelectorDialogFragment.TAG);

    CallList.getInstance().addListener(this);
  }

  @Override
  public void onAudioRouteSelected(int audioRoute) {
    TelecomAdapter.getInstance().setAudioRoute(audioRoute);
    finish();

    // Log the select action with audio route and call
    DialerImpression.Type impressionType = null;
    if ((audioRoute & CallAudioState.ROUTE_WIRED_OR_EARPIECE) != 0) {
      impressionType = DialerImpression.Type.BUBBLE_V2_WIRED_OR_EARPIECE;
    } else if (audioRoute == CallAudioState.ROUTE_SPEAKER) {
      impressionType = DialerImpression.Type.BUBBLE_V2_SPEAKERPHONE;
    } else if (audioRoute == CallAudioState.ROUTE_BLUETOOTH) {
      impressionType = DialerImpression.Type.BUBBLE_V2_BLUETOOTH;
    }
    if (impressionType == null) {
      return;
    }

    DialerCall call = getCall();
    if (call != null) {
      Logger.get(this)
          .logCallImpression(impressionType, call.getUniqueCallId(), call.getTimeAddedMs());
    } else {
      Logger.get(this).logImpression(impressionType);
    }
  }

  @Override
  public void onAudioRouteSelectorDismiss() {
    finish();
  }

  @Override
  protected void onPause() {
    super.onPause();
    AudioRouteSelectorDialogFragment audioRouteSelectorDialogFragment =
        (AudioRouteSelectorDialogFragment)
            getSupportFragmentManager().findFragmentByTag(AudioRouteSelectorDialogFragment.TAG);
    // If Android back button is pressed, the fragment is dismissed and removed. If home button is
    // pressed, we have to manually dismiss the fragment here. The fragment is also removed when
    // dismissed.
    if (audioRouteSelectorDialogFragment != null) {
      audioRouteSelectorDialogFragment.dismiss();
    }
    // We don't expect the activity to resume, except for orientation change.
    if (!isChangingConfigurations()) {
      finish();
    }
  }

  @Override
  protected void onDestroy() {
    CallList.getInstance().removeListener(this);
    super.onDestroy();
  }

  private DialerCall getCall() {
    DialerCall dialerCall = CallList.getInstance().getOutgoingCall();
    if (dialerCall == null) {
      dialerCall = CallList.getInstance().getActiveOrBackgroundCall();
    }
    return dialerCall;
  }

  @Override
  public void onDisconnect(DialerCall call) {
    if (getCall() == null) {
      finish();
    }
  }

  @Override
  public void onIncomingCall(DialerCall call) {}

  @Override
  public void onUpgradeToVideo(DialerCall call) {}

  @Override
  public void onSessionModificationStateChange(DialerCall call) {}

  @Override
  public void onCallListChange(CallList callList) {}

  @Override
  public void onWiFiToLteHandover(DialerCall call) {}

  @Override
  public void onHandoverToWifiFailed(DialerCall call) {}

  @Override
  public void onInternationalCallOnWifi(@NonNull DialerCall call) {}
}
