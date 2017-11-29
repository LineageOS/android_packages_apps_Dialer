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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
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
        getWindow().addFlags(LayoutParams.FLAG_SHOW_WHEN_LOCKED);

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
