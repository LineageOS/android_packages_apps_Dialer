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
 * limitations under the License
 */

package com.android.dialer.commandline.impl;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.support.annotation.NonNull;
import com.android.dialer.commandline.Arguments;
import com.android.dialer.commandline.Command;
import com.android.dialer.inject.ApplicationContext;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Locale;
import javax.inject.Inject;

/** Print the version name and code. */
public class Version implements Command {

  @NonNull
  @Override
  public String getShortDescription() {
    return "Print dialer version";
  }

  @NonNull
  @Override
  public String getUsage() {
    return "version";
  }

  private final Context appContext;

  @Inject
  Version(@ApplicationContext Context context) {
    this.appContext = context;
  }

  @Override
  public ListenableFuture<String> run(Arguments args) throws IllegalCommandLineArgumentException {
    try {
      PackageInfo info =
          appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0);
      return Futures.immediateFuture(
          String.format(Locale.US, "%s(%d)", info.versionName, info.versionCode));
    } catch (NameNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
