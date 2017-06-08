/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.app.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import com.android.dialer.app.R;
import com.android.dialer.util.OrientationUtil;
import com.android.dialer.widget.EmptyContentView;

/** Empty content view to be shown when dialpad is visible. */
public class DialpadSearchEmptyContentView extends EmptyContentView {

  public DialpadSearchEmptyContentView(Context context) {
    super(context);
  }

  @Override
  protected void inflateLayout() {
    int orientation =
        OrientationUtil.isLandscape(getContext()) ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL;

    setOrientation(orientation);

    final LayoutInflater inflater =
        (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.empty_content_view_dialpad_search, this);
  }
}
