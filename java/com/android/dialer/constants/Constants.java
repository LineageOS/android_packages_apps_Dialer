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

package com.android.dialer.constants;

import androidx.annotation.NonNull;

/**
 * Utility to access constants
 */
public class Constants {
  private static Constants instance;
  private static boolean didInitializeInstance;

  @NonNull
  public static synchronized Constants get() {
    if (!didInitializeInstance) {
      didInitializeInstance = true;
      instance = new Constants();
    }
    return instance;
  }

  @NonNull
  public String getFileProviderAuthority() {
    return "com.android.dialer.files";
  }

  @NonNull
  public String getAnnotatedCallLogProviderAuthority() {
    return "com.android.dialer.annotatedcalllog";
  }

  @NonNull
  public String getPhoneLookupHistoryProviderAuthority() {
    return "com.android.dialer.phonelookuphistory";
  }

  @NonNull
  public String getPreferredSimFallbackProviderAuthority() {
    return "com.android.dialer.preferredsimfallback";
  }

  protected Constants() {}
}
