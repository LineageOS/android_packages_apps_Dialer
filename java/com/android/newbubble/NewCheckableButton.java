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

package com.android.newbubble;

import android.content.Context;
import android.content.res.ColorStateList;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v7.widget.AppCompatButton;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Checkable;

/**
 * A {@link android.widget.Button Button} that implements {@link Checkable} and propagates the
 * checkable state
 */
public class NewCheckableButton extends AppCompatButton implements Checkable {

  private boolean checked;

  public NewCheckableButton(Context context) {
    this(context, null);
  }

  public NewCheckableButton(Context context, AttributeSet attrs) {
    this(context, attrs, android.R.attr.imageButtonStyle);
  }

  public NewCheckableButton(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    ViewCompat.setAccessibilityDelegate(
        this,
        new AccessibilityDelegateCompat() {
          @Override
          public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(host, event);
            event.setChecked(isChecked());
          }

          @Override
          public void onInitializeAccessibilityNodeInfo(
              View host, AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setCheckable(true);
            info.setChecked(isChecked());
          }
        });
  }

  @Override
  public void setChecked(boolean checked) {
    if (this.checked != checked) {
      this.checked = checked;
      int newColor =
          checked
              ? getContext().getColor(R.color.bubble_button_color_blue)
              : getContext().getColor(R.color.bubble_button_color_grey);
      setTextColor(newColor);
      setCompoundDrawableTintList(ColorStateList.valueOf(newColor));
    }
  }

  @Override
  public boolean isChecked() {
    return checked;
  }

  @Override
  public void toggle() {
    setChecked(!checked);
  }
}
