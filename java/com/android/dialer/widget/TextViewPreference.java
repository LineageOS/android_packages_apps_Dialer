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

package com.android.dialer.widget;

import android.content.Context;
import android.preference.Preference;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

/**
 * Provides a {@link TextView} inside a preference. Useful for displaying static text which may
 * contain hyperlinks.
 */
public class TextViewPreference extends Preference {

  /**
   * The resource ID of the text to be populated in the {@link TextView} when a resource ID is used.
   */
  private int textResourceId = 0;

  /** The text to be populated in the {@link TextView} when a {@link CharSequence} is used. */
  private CharSequence text;

  /** The {@link TextView} containing the text. */
  private TextView textView;

  /**
   * Instantiates the {@link TextViewPreference} instance.
   *
   * @param context The Context this is associated with, through which it can access the current
   *     theme, resources, etc.
   * @param attrs The attributes of the XML tag that is inflating the preference.
   * @param defStyleAttr An attribute in the current theme that contains a reference to a style
   *     resource that supplies default values for the view. Can be 0 to not look for defaults.
   * @param defStyleRes A resource identifier of a style resource that supplies default values for
   *     the view, used only if defStyleAttr is 0 or can not be found in the theme. Can be 0 to not
   *     look for defaults.
   */
  public TextViewPreference(
      Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);

    setLayoutResource(R.layout.text_view_preference);
  }

  /**
   * Instantiates the {@link TextViewPreference} instance.
   *
   * @param context The Context this is associated with, through which it can access the current
   *     theme, resources, etc.
   * @param attrs The attributes of the XML tag that is inflating the preference.
   * @param defStyleAttr An attribute in the current theme that contains a reference to a style
   *     resource that supplies default values for the view. Can be 0 to not look for defaults.
   */
  public TextViewPreference(Context context, AttributeSet attrs, int defStyleAttr) {
    this(context, attrs, defStyleAttr, 0);
  }

  /**
   * Instantiates the {@link TextViewPreference} instance.
   *
   * @param context The Context this is associated with, through which it can access the current
   *     theme, resources, etc.
   * @param attrs The attributes of the XML tag that is inflating the preference.
   */
  public TextViewPreference(Context context, AttributeSet attrs) {
    this(context, attrs, android.R.attr.preferenceStyle, 0);
  }

  /**
   * Instantiates the {@link TextViewPreference} instance.
   *
   * @param context The Context this is associated with, through which it can access the current
   *     theme, resources, etc.
   */
  public TextViewPreference(Context context) {
    super(context, null);

    setLayoutResource(R.layout.text_view_preference);
  }

  /**
   * Handles binding the preference.
   *
   * @param view The view.
   */
  @Override
  protected void onBindView(View view) {
    super.onBindView(view);
    textView = (TextView) view.findViewById(R.id.text);
    if (textResourceId != 0) {
      setTitle(textResourceId);
    } else if (text != null) {
      setTitle(text);
    } else if (getTitleRes() != 0) {
      setTitle(getTitleRes());
    }
  }

  /**
   * Sets the preference title from a {@link CharSequence}.
   *
   * @param text The text.
   */
  @Override
  public void setTitle(CharSequence text) {
    textResourceId = 0;
    this.text = text;
    if (textView == null) {
      return;
    }

    textView.setMovementMethod(LinkMovementMethod.getInstance());
    textView.setText(text);
  }

  /**
   * Sets the preference title from a resource id.
   *
   * @param textResId The string resource Id.
   */
  @Override
  public void setTitle(int textResId) {
    textResourceId = textResId;
    setTitle(getContext().getString(textResId));
  }
}
