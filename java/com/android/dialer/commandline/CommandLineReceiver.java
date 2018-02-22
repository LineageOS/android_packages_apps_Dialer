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

package com.android.dialer.commandline;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import com.android.dialer.buildtype.BuildType;
import com.android.dialer.buildtype.BuildType.Type;
import com.android.dialer.commandline.Command.IllegalCommandLineArgumentException;
import com.android.dialer.common.LogUtil;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * Receives broadcasts to the component from adb shell. Must be on bugfood or have debug logging
 * enabled.
 */
public class CommandLineReceiver extends BroadcastReceiver {

  public static final String COMMAND = "command";
  public static final String ARGS = "args";
  public static final String TAG = "tag";

  @Override
  public void onReceive(Context context, Intent intent) {
    String outputTag = intent.getStringExtra(TAG);
    if (outputTag == null) {
      LogUtil.e("CommandLineReceiver", "missing tag");
      return;
    }
    if (!LogUtil.isDebugEnabled() && BuildType.get() != Type.BUGFOOD) {
      LogUtil.i(outputTag, "DISABLED");
      return;
    }
    Command command =
        CommandLineComponent.get(context)
            .commandSupplier()
            .get()
            .get(intent.getStringExtra(COMMAND));
    try {
      if (command == null) {
        LogUtil.i(outputTag, "unknown command " + intent.getStringExtra(COMMAND));
        return;
      }

      Arguments args = Arguments.parse(intent.getStringArrayExtra(ARGS));

      if (args.getBoolean("help", false)) {
        LogUtil.i(outputTag, "usage:\n" + command.getUsage());
        return;
      }
      Futures.addCallback(
          command.run(args),
          new FutureCallback<String>() {
            @Override
            public void onSuccess(String response) {
              if (TextUtils.isEmpty(response)) {
                LogUtil.i(outputTag, "EMPTY");
              } else {
                LogUtil.i(outputTag, response);
              }
            }

            @Override
            public void onFailure(Throwable throwable) {
              if (throwable instanceof IllegalCommandLineArgumentException) {
                LogUtil.e(outputTag, throwable.getMessage() + "\n\nusage:\n" + command.getUsage());
              }
              LogUtil.e(outputTag, "error running command future", throwable);
            }
          },
          MoreExecutors.directExecutor());
    } catch (IllegalCommandLineArgumentException e) {
      LogUtil.e(outputTag, e.getMessage() + "\n\nusage:\n" + command.getUsage());
    } catch (Throwable throwable) {
      LogUtil.e(outputTag, "error running command", throwable);
    }
  }
}
