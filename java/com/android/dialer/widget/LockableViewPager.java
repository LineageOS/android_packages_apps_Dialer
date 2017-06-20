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

package com.android.dialer.widget;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

/** {@link ViewPager} useful for disabled swiping between pages. */
public class LockableViewPager extends ViewPager {

  private boolean swipingLocked;

  public LockableViewPager(Context context, AttributeSet attributeSet) {
    super(context, attributeSet);
  }

  public void setSwipingLocked(boolean swipingLocked) {
    this.swipingLocked = swipingLocked;
  }

  public boolean isSwipingLocked() {
    return swipingLocked;
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent motionEvent) {
    return !swipingLocked && super.onInterceptTouchEvent(motionEvent);
  }

  @Override
  public boolean onTouchEvent(MotionEvent motionEvent) {
    return !swipingLocked && super.onTouchEvent(motionEvent);
  }
}
