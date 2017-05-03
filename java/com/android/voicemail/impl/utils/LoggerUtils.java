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

package com.android.voicemail.impl.utils;

import android.content.Context;
import android.support.annotation.AnyThread;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;

/** Common utility method when using {@link Logger} */
public class LoggerUtils {

  /** Posts the impression logging to the main thread so it will be thread safe. */
  @AnyThread
  public static void logImpressionOnMainThread(Context context, DialerImpression.Type impression) {
    ThreadUtil.postOnUiThread(() -> Logger.get(context).logImpression(impression));
  }
}
