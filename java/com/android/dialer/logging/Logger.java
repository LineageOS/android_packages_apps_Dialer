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

package com.android.dialer.logging;

import android.content.Context;
import java.util.Objects;

/** Single entry point for all logging/analytics-related work for all user interactions. */
public class Logger {

  private static LoggingBindings loggingBindings;

  private Logger() {}

  public static LoggingBindings get(Context context) {
    Objects.requireNonNull(context);
    if (loggingBindings != null) {
      return loggingBindings;
    }

    Context application = context.getApplicationContext();
    if (application instanceof LoggingBindingsFactory) {
      loggingBindings = ((LoggingBindingsFactory) application).newLoggingBindings();
    }

    if (loggingBindings == null) {
      loggingBindings = new LoggingBindingsStub();
    }
    return loggingBindings;
  }

  public static void setForTesting(LoggingBindings loggingBindings) {
    Logger.loggingBindings = loggingBindings;
  }
}
