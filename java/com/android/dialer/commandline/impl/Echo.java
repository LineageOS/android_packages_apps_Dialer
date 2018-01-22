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

import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import com.android.dialer.commandline.Arguments;
import com.android.dialer.commandline.Command;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import javax.inject.Inject;

/** Print arguments. */
public class Echo implements Command {

  @NonNull
  @Override
  public String getShortDescription() {
    return "@hide Print all arguments.";
  }

  @NonNull
  @Override
  public String getUsage() {
    return "echo [arguments...]";
  }

  @VisibleForTesting
  @Inject
  public Echo() {}

  @Override
  public ListenableFuture<String> run(Arguments args) throws IllegalCommandLineArgumentException {
    return Futures.immediateFuture(TextUtils.join(" ", args.getPositionals()));
  }
}
