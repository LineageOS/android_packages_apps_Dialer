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
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import com.android.incallui.audiomode.AudioModeProvider;
import com.android.incallui.audioroute.AudioRouteSelectorDialogFragment;
import com.android.incallui.audioroute.AudioRouteSelectorDialogFragment.AudioRouteSelectorPresenter;
import com.android.incallui.call.TelecomAdapter;

/** Simple activity that just shows the audio route selector fragment */
public class AudioRouteSelectorActivity extends FragmentActivity
    implements AudioRouteSelectorPresenter {

  @Override
  protected void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);
    AudioRouteSelectorDialogFragment.newInstance(AudioModeProvider.getInstance().getAudioState())
        .show(getSupportFragmentManager(), null);
  }

  @Override
  public void onAudioRouteSelected(int audioRoute) {
    TelecomAdapter.getInstance().setAudioRoute(audioRoute);
  }

  @Override
  public void onAudioRouteSelectorDismiss() {
    finish();
  }
}
