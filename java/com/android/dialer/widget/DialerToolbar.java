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
 * limitations under the License.
 */

package com.android.dialer.widget;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.widget.TextView;
import com.android.dialer.theme.base.ThemeComponent;

/** Toolbar widget for Dialer. */
public class DialerToolbar extends Toolbar {

  private final TextView title;
  private final BidiTextView subtitle;

  public DialerToolbar(Context context, @Nullable AttributeSet attributeSet) {
    super(context, attributeSet);
    inflate(context, R.layout.dialer_toolbar, this);
    title = (TextView) findViewById(R.id.title);
    subtitle = (BidiTextView) findViewById(R.id.subtitle);

    setElevation(getResources().getDimensionPixelSize(R.dimen.toolbar_elevation));
    setBackgroundColor(ThemeComponent.get(context).theme().getColorPrimary());
    setNavigationIcon(R.drawable.quantum_ic_close_white_24);
    setNavigationContentDescription(R.string.toolbar_close);
    setNavigationOnClickListener(v -> ((Activity) context).finish());
    setPaddingRelative(
        getPaddingStart(),
        getPaddingTop(),
        getResources().getDimensionPixelSize(R.dimen.toolbar_end_padding),
        getPaddingBottom());
  }

  @Override
  public void setTitle(@StringRes int id) {
    setTitle(getResources().getString(id));
  }

  @Override
  public void setTitle(CharSequence charSequence) {
    title.setText(charSequence);
  }

  @Override
  public void setSubtitle(@StringRes int id) {
    setSubtitle(getResources().getString(id));
  }

  @Override
  public void setSubtitle(CharSequence charSequence) {
    if (charSequence != null) {
      subtitle.setText(charSequence);
      subtitle.setVisibility(VISIBLE);
    }
  }
}
