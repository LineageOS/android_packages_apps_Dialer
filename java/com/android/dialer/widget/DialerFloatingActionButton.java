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

package com.android.dialer.widget;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.util.AttributeSet;
import com.android.dialer.common.Assert;

/**
 * Since {@link FloatingActionButton} is possibly the worst widget supported by the framework, we
 * need this class to work around several of it's bugs.
 *
 * <p>Current fixes:
 *
 * <ul>
 *   <li>Being able to trigger click events twice.
 *   <li>Banning setVisibility since 9 times out of 10, it just causes bad state.
 * </ul>
 *
 * Planned fixes:
 *
 * <ul>
 *   <li>Animating on first show/hide
 *   <li>Being able to call show/hide rapidly and being in the proper state
 *   <li>Having a proper 48x48 touch target in mini mode
 * </ul>
 */
public class DialerFloatingActionButton extends FloatingActionButton {

  public DialerFloatingActionButton(Context context, AttributeSet attributeSet) {
    super(context, attributeSet);
  }

  @Override
  public void show() {
    super.show();
    setClickable(true);
  }

  @Override
  public void show(@Nullable OnVisibilityChangedListener onVisibilityChangedListener) {
    super.show(onVisibilityChangedListener);
    setClickable(true);
  }

  @Override
  public void hide() {
    super.hide();
    setClickable(false);
  }

  @Override
  public void hide(@Nullable OnVisibilityChangedListener onVisibilityChangedListener) {
    super.hide(onVisibilityChangedListener);
    setClickable(false);
  }

  @Override
  public void setVisibility(int i) {
    throw Assert.createUnsupportedOperationFailException(
        "Do not call setVisibility, call show/hide instead");
  }
}
