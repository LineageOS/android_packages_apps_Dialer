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

package com.android.dialer.common.preference;

import static android.support.v4.content.ContextCompat.startActivity;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import com.android.dialer.common.Assert;

/**
 * Utility to allow the summary of a {@link SwitchPreference} to be clicked and opened via a browser
 * to the specified {@link urlToOpen} attribute while maintaining all other aspects of a {@link
 * SwitchPreference}.
 *
 * <p>Example usage:
 *
 * <pre>
 *   <com.android.dialer.common.preference.SwitchPreferenceWithClickableSummary
 *          android:dependency="...."
 *          android:key="...."
 *          android:title="...."
 *          app:urlToOpen="...."/>
 * </pre>
 */
public class SwitchPreferenceWithClickableSummary extends SwitchPreference {
  private final String urlToOpen;

  public SwitchPreferenceWithClickableSummary(
      Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    TypedArray typedArray =
        context.obtainStyledAttributes(attrs, R.styleable.SwitchPreferenceWithClickableSummary);
    urlToOpen =
        String.valueOf(
            typedArray.getText(R.styleable.SwitchPreferenceWithClickableSummary_urlToOpen));
  }

  public SwitchPreferenceWithClickableSummary(
      Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr, defStyleAttr);
    TypedArray typedArray =
        context.obtainStyledAttributes(attrs, R.styleable.SwitchPreferenceWithClickableSummary);
    urlToOpen =
        String.valueOf(
            typedArray.getText(R.styleable.SwitchPreferenceWithClickableSummary_urlToOpen));
  }

  public SwitchPreferenceWithClickableSummary(Context context, AttributeSet attrs) {
    super(context, attrs);
    TypedArray typedArray =
        context.obtainStyledAttributes(attrs, R.styleable.SwitchPreferenceWithClickableSummary);
    urlToOpen =
        String.valueOf(
            typedArray.getText(R.styleable.SwitchPreferenceWithClickableSummary_urlToOpen));
  }

  public SwitchPreferenceWithClickableSummary(Context context) {
    this(context, null);
  }

  @Override
  protected View onCreateView(ViewGroup parent) {
    return super.onCreateView(parent);
  }

  @Override
  protected void onBindView(View view) {
    super.onBindView(view);
    Assert.checkArgument(
        urlToOpen != null,
        "must have a urlToOpen attribute when using SwitchPreferenceWithClickableSummary");
    view.findViewById(android.R.id.summary)
        .setOnClickListener(
            new OnClickListener() {
              @Override
              public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen));
                startActivity(view.getContext(), intent, null);
              }
            });
  }
}
