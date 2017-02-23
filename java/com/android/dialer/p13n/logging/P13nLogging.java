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

package com.android.dialer.p13n.logging;

import android.content.Context;
import android.support.annotation.NonNull;
import com.android.contacts.common.list.PhoneNumberListAdapter;
import com.android.dialer.common.Assert;

/** Single entry point for all logging for personalization. */
public final class P13nLogging {

  private static P13nLogger logger;

  private P13nLogging() {}

  @NonNull
  public static P13nLogger get(@NonNull Context context) {
    Assert.isNotNull(context);
    Assert.isMainThread();
    if (logger != null) {
      return logger;
    }

    Context application = context.getApplicationContext();
    if (application instanceof P13nLoggerFactory) {
      logger = ((P13nLoggerFactory) application).newP13nLogger(context);
    }

    if (logger == null) {
      logger =
          new P13nLogger() {
            @Override
            public void onSearchQuery(String query, PhoneNumberListAdapter adapter) {}

            @Override
            public void reset() {}
          };
    }
    return logger;
  }

  public static void setForTesting(@NonNull P13nLogger logger) {
    P13nLogging.logger = logger;
  }
}
