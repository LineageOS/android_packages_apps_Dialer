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

package com.android.dialer.app.list;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

/** Class that handles enabling/disabling swiping between @{ViewPagerTabs}. */
public class DialerViewPager extends ViewPager {

  private boolean enableSwipingPages;

  public DialerViewPager(Context context, AttributeSet attributeSet) {
    super(context, attributeSet);
    enableSwipingPages = true;
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent event) {
    if (enableSwipingPages) {
      return super.onInterceptTouchEvent(event);
    }

    return false;
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (enableSwipingPages) {
      return super.onTouchEvent(event);
    }

    return false;
  }

  public void setEnableSwipingPages(boolean enabled) {
    enableSwipingPages = enabled;
  }
}
