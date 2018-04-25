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
import com.android.dialer.activecalls.ActiveCallsComponent;
import com.android.dialer.commandline.Arguments;
import com.android.dialer.commandline.Command;
import com.android.dialer.inject.ApplicationContext;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import javax.inject.Inject;

/** Manipulates {@link com.android.dialer.activecalls.ActiveCalls} */
public class ActiveCallsCommand implements Command {

  private final Context appContext;

  @Inject
  ActiveCallsCommand(@ApplicationContext Context appContext) {
    this.appContext = appContext;
  }

  @NonNull
  @Override
  public String getShortDescription() {
    return "manipulate active calls";
  }

  @NonNull
  @Override
  public String getUsage() {
    return "activecalls list";
  }

  @Override
  public ListenableFuture<String> run(Arguments args) throws IllegalCommandLineArgumentException {
    if (args.getPositionals().isEmpty()) {
      return Futures.immediateFuture(getUsage());
    }

    String command = args.getPositionals().get(0);

    switch (command) {
      case "list":
        return Futures.immediateFuture(
            ActiveCallsComponent.get(appContext).activeCalls().getActiveCalls().toString());
      default:
        throw new IllegalCommandLineArgumentException("unknown command " + command);
    }
  }
}
