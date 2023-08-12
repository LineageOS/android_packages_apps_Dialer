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

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;

/**
 * Activity that contains an alert dialog with OK and Cancel buttons to allow user to Accept or
 * Reject the WAIT inserted as part of the Dial string.
 */
public class PostCharDialogActivity extends AppCompatActivity implements CallList.Listener {

  public static final String EXTRA_CALL_ID = "extra_call_id";
  public static final String EXTRA_POST_DIAL_STRING = "extra_post_dial_string";
  private static final String TAG_INTERNATIONAL_CALL_ON_WIFI = "tag_international_call_on_wifi";

  private String callId;

  @Override
  protected void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);

    callId = getIntent().getStringExtra(EXTRA_CALL_ID);
    String postDialString = getIntent().getStringExtra(EXTRA_POST_DIAL_STRING);
    if (callId == null || postDialString == null) {
      finish();
      return;
    }

    PostCharDialogFragment fragment = new PostCharDialogFragment(callId, postDialString);
    fragment.show(getSupportFragmentManager(), TAG_INTERNATIONAL_CALL_ON_WIFI);

    CallList.getInstance().addListener(this);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    CallList.getInstance().removeListener(this);
  }

  @Override
  protected void onPause() {
    super.onPause();
    // We don't expect the activity to resume, except for orientation change.
    if (!isChangingConfigurations()) {
      finish();
    }
  }

  @Override
  public void onDisconnect(DialerCall call) {
    if (callId.equals(call.getId())) {
      finish();
    }
  }

  @Override
  public void onIncomingCall(DialerCall call) {}

  @Override
  public void onUpgradeToVideo(DialerCall call) {}

  @Override
  public void onUpgradeToRtt(DialerCall call, int rttRequestId) {}

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
