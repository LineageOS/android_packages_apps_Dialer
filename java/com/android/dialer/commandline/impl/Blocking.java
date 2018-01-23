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
import android.support.annotation.NonNull;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.android.dialer.commandline.Arguments;
import com.android.dialer.commandline.Command;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.inject.ApplicationContext;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import javax.inject.Inject;

/** Block or unblock a number. */
public class Blocking implements Command {

  @NonNull
  @Override
  public String getShortDescription() {
    return "block or unblock numbers";
  }

  @NonNull
  @Override
  public String getUsage() {
    return "blocking block|unblock|isblocked number\n\n" + "number should be e.164 formatted";
  }

  private final Context appContext;
  private final ListeningExecutorService executorService;

  @Inject
  Blocking(
      @ApplicationContext Context context,
      @BackgroundExecutor ListeningExecutorService executorService) {
    this.appContext = context;
    this.executorService = executorService;
  }

  @Override
  public ListenableFuture<String> run(Arguments args) throws IllegalCommandLineArgumentException {
    // AsyncQueryHandler must be created on a thread with looper.
    // TODO(a bug): Use blocking version
    FilteredNumberAsyncQueryHandler asyncQueryHandler =
        new FilteredNumberAsyncQueryHandler(appContext);
    return executorService.submit(() -> doInBackground(args, asyncQueryHandler));
  }

  private String doInBackground(Arguments args, FilteredNumberAsyncQueryHandler asyncQueryHandler) {
    if (args.getPositionals().isEmpty()) {
      return getUsage();
    }

    String command = args.getPositionals().get(0);

    if ("block".equals(command)) {
      String number = args.getPositionals().get(1);
      asyncQueryHandler.blockNumber((unused) -> {}, number, null);
      return "blocked " + number;
    }

    if ("unblock".equals(command)) {
      String number = args.getPositionals().get(1);
      Integer id = asyncQueryHandler.getBlockedIdSynchronous(number, null);
      if (id == null) {
        return number + " is not blocked";
      }
      asyncQueryHandler.unblock((unusedRows, unusedValues) -> {}, id);
      return "unblocked " + number;
    }

    if ("isblocked".equals(command)) {
      String number = args.getPositionals().get(1);
      Integer id = asyncQueryHandler.getBlockedIdSynchronous(number, null);
      return id == null ? "false" : "true";
    }

    return getUsage();
  }
}
