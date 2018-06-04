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
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.blocking.Blocking;
import com.android.dialer.commandline.Arguments;
import com.android.dialer.commandline.Command;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.phonelookup.PhoneLookupComponent;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.consolidator.PhoneLookupInfoConsolidator;
import com.android.dialer.phonenumberproto.DialerPhoneNumberUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import javax.inject.Inject;

/** Block or unblock a number. */
public class BlockingCommand implements Command {

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
  BlockingCommand(
      @ApplicationContext Context context,
      @BackgroundExecutor ListeningExecutorService executorService) {
    this.appContext = context;
    this.executorService = executorService;
  }

  @Override
  public ListenableFuture<String> run(Arguments args) throws IllegalCommandLineArgumentException {
    if (args.getPositionals().isEmpty()) {
      return Futures.immediateFuture(getUsage());
    }

    String command = args.getPositionals().get(0);

    if ("block".equals(command)) {
      String number = args.getPositionals().get(1);
      return Futures.transform(
          Blocking.block(appContext, ImmutableList.of(number), null),
          (unused) -> "blocked " + number,
          MoreExecutors.directExecutor());
    }

    if ("unblock".equals(command)) {
      String number = args.getPositionals().get(1);
      return Futures.transform(
          Blocking.unblock(appContext, ImmutableList.of(number), null),
          (unused) -> "unblocked " + number,
          MoreExecutors.directExecutor());
    }

    if ("isblocked".equals(command)) {
      String number = args.getPositionals().get(1);
      ListenableFuture<DialerPhoneNumber> dialerPhoneNumberFuture =
          executorService.submit(() -> new DialerPhoneNumberUtil().parse(number, null));

      ListenableFuture<PhoneLookupInfo> lookupFuture =
          Futures.transformAsync(
              dialerPhoneNumberFuture,
              (dialerPhoneNumber) ->
                  PhoneLookupComponent.get(appContext)
                      .compositePhoneLookup()
                      .lookup(dialerPhoneNumber),
              executorService);

      return Futures.transform(
          lookupFuture,
          (info) -> new PhoneLookupInfoConsolidator(info).isBlocked() ? "true" : "false",
          MoreExecutors.directExecutor());
    }

    return Futures.immediateFuture(getUsage());
  }
}
