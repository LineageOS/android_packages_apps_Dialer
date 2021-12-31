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
import android.content.Intent;
import android.support.annotation.NonNull;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import com.android.dialer.buildtype.BuildType;
import com.android.dialer.buildtype.BuildType.Type;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.commandline.Arguments;
import com.android.dialer.commandline.Command;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.precall.PreCall;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import javax.inject.Inject;

/** Make calls. Requires bugfood build. */
public class CallCommand implements Command {

  private final Context appContext;

  @Inject
  CallCommand(@ApplicationContext Context appContext) {
    this.appContext = appContext;
  }

  @NonNull
  @Override
  public String getShortDescription() {
    return "make a call";
  }

  @NonNull
  @Override
  public String getUsage() {
    return "call [flags --] number\n"
        + "\nuse 'voicemail' to call voicemail"
        + "\n\nflags:"
        + "\n--direct send intent to telecom instead of pre call";
  }

  @Override
  @SuppressWarnings("missingPermission")
  public ListenableFuture<String> run(Arguments args) throws IllegalCommandLineArgumentException {
    if (BuildType.get() != Type.BUGFOOD) {
      throw new SecurityException("Bugfood only command");
    }
    String number = args.expectPositional(0, "number");
    TelecomManager telecomManager = appContext.getSystemService(TelecomManager.class);
    PhoneAccountHandle phoneAccountHandle =
        telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL);
    CallIntentBuilder callIntentBuilder;
    if ("voicemail".equals(number)) {
      callIntentBuilder =
          CallIntentBuilder.forVoicemail(CallInitiationType.Type.DIALPAD);
    } else {
      callIntentBuilder = new CallIntentBuilder(number, CallInitiationType.Type.DIALPAD);
    }
    if (args.getBoolean("direct", false)) {
      Intent intent = callIntentBuilder.build();
      appContext
          .getSystemService(TelecomManager.class)
          .placeCall(intent.getData(), intent.getExtras());
    } else {
      Intent intent = PreCall.getIntent(appContext, callIntentBuilder);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      appContext.startActivity(intent);
    }
    return Futures.immediateFuture("Calling " + number);
  }
}
