/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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
package com.android.voicemail.impl.mail;

import android.content.Context;

import java.io.File;

/**
 * TempDirectory caches the directory used for caching file. It is set up during application
 * initialization.
 */
public class TempDirectory {
  private static File tempDirectory = null;

  public static void setTempDirectory(Context context) {
    tempDirectory = context.getCacheDir();
  }

  public static File getTempDirectory() {
    if (tempDirectory == null) {
      throw new RuntimeException(
          "TempDirectory not set.  "
              + "If in a unit test, call Email.setTempDirectory(context) in setUp().");
    }
    return tempDirectory;
  }
}
