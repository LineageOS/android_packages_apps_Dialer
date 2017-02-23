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

package com.android.dialer.common;

import android.content.Context;

/** Utility for dp to px conversion */
public class DpUtil {

  public static float pxToDp(Context context, float px) {
    return px / context.getResources().getDisplayMetrics().density;
  }

  public static float dpToPx(Context context, float dp) {
    return dp * context.getResources().getDisplayMetrics().density;
  }
}
