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

package com.android.dialer.precall.impl;

import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.WindowManager.LayoutParams;

/** A transparent activity to host dialogs for {@link PreCallCoordinatorImpl} */
public class PreCallActivity extends Activity {

  private PreCallCoordinatorImpl preCallCoordinator;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    preCallCoordinator = new PreCallCoordinatorImpl(this);
    preCallCoordinator.onCreate(getIntent(), savedInstanceState);
    if (getSystemService(KeyguardManager.class).isKeyguardLocked()) {
      // Note:
      //
      // Flag LayoutParams.FLAG_TURN_SCREEN_ON was deprecated in O_MR1, but calling the new API
      // setTurnScreenOn(true) doesn't give us the expected behavior.
      //
      // Calling setTurnScreenOn(true) alone doesn't turn on the screen when the device is locked.
      // We must also call KeyguardManager#requestDismissKeyguard, which will bring up the lock
      // screen for the user to enter their credentials.
      //
      // If the Keyguard is not secure or the device is currently in a trusted state, calling
      // requestDismissKeyguard will immediately dismiss the Keyguard without any user interaction.
      // However, the lock screen will still pop up before it quickly disappears.
      //
      // If the Keyguard is secure and the device is not in a trusted state, the device will show
      // the lock screen and wait for the user's credentials.
      //
      // Therefore, to avoid showing the lock screen, we will continue using the deprecated flag in
      // O_MR1 and later Android versions.
      //
      // Flag LayoutParams.FLAG_SHOW_WHEN_LOCKED was also deprecated in O_MR1, and the new API
      // setShowWhenLocked(boolean) works. However, as the purpose of the two new APIs is to prevent
      // an unintentional double life-cycle event, only using one is ineffective.
      //
      // Therefore, to simplify code and make testing easier, we will also keep using
      // LayoutParams.FLAG_SHOW_WHEN_LOCKED.
      getWindow().addFlags(LayoutParams.FLAG_SHOW_WHEN_LOCKED | LayoutParams.FLAG_TURN_SCREEN_ON);
    }
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    preCallCoordinator.onRestoreInstanceState(savedInstanceState);
  }

  @Override
  public void onResume() {
    super.onResume();
    preCallCoordinator.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
    preCallCoordinator.onPause();
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    preCallCoordinator.onSaveInstanceState(outState);
  }
}
