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

package com.android.dialershared.bubble;

import android.content.Context;
import android.content.res.Configuration;
import android.support.annotation.NonNull;
import android.view.KeyEvent;
import android.widget.FrameLayout;

/**
 * ViewGroup that handles some overlay window concerns. Allows back button and configuration change
 * events to be listened for via interfaces.
 */
public class WindowRoot extends FrameLayout {

  /** Callback for when the back button is pressed while this window is in focus */
  public interface OnBackPressedListener {
    boolean onBackPressed();
  }

  /** Callback for when the Configuration changes for this window */
  public interface OnConfigurationChangedListener {
    void onConfigurationChanged(Configuration newConfiguration);
  }

  private OnBackPressedListener backPressedListener;
  private OnConfigurationChangedListener configurationChangedListener;

  public WindowRoot(@NonNull Context context) {
    super(context);
  }

  public void setOnBackPressedListener(OnBackPressedListener listener) {
    backPressedListener = listener;
  }

  public void setOnConfigurationChangedListener(OnConfigurationChangedListener listener) {
    configurationChangedListener = listener;
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && backPressedListener != null) {
      if (event.getAction() == KeyEvent.ACTION_UP) {
        return backPressedListener.onBackPressed();
      }
      return true;
    }
    return super.dispatchKeyEvent(event);
  }

  @Override
  public void dispatchConfigurationChanged(Configuration newConfig) {
    super.dispatchConfigurationChanged(newConfig);
    if (configurationChangedListener != null) {
      configurationChangedListener.onConfigurationChanged(newConfig);
    }
  }
}
