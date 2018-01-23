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

import android.support.annotation.NonNull;
import com.google.common.util.concurrent.ListenableFuture;

/** Handles a Command from {@link CommandLineReceiver}. */
public interface Command {

  /**
   * Thrown when {@code args} in {@link #run(Arguments)} does not match the expected format. The
   * commandline will print {@code message} and {@link #getUsage()}.
   */
  class IllegalCommandLineArgumentException extends Exception {
    public IllegalCommandLineArgumentException(String message) {
      super(message);
    }
  }

  /** Describe the command when "help" is listing available commands. */
  @NonNull
  String getShortDescription();

  /**
   * Call when 'command --help' is called or when {@link IllegalCommandLineArgumentException} is
   * thrown to inform the user how should the command be used.
   */
  @NonNull
  String getUsage();

  ListenableFuture<String> run(Arguments args) throws IllegalCommandLineArgumentException;
}
