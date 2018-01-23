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

import com.android.dialer.commandline.impl.Blocking;
import com.android.dialer.commandline.impl.Echo;
import com.android.dialer.commandline.impl.Help;
import com.android.dialer.commandline.impl.Version;
import com.android.dialer.function.Supplier;
import com.google.common.collect.ImmutableMap;
import dagger.Module;
import dagger.Provides;
import javax.inject.Inject;

/** Provides {@link Command} */
@Module
public abstract class CommandLineModule {

  @Provides
  static Supplier<ImmutableMap<String, Command>> provideCommandSupplier(
      AospCommandInjector aospCommandInjector) {

    return aospCommandInjector.inject(CommandSupplier.builder()).build();
  }

  /** Injects standard commands to the builder */
  public static class AospCommandInjector {
    private final Help help;
    private final Version version;
    private final Echo echo;
    private final Blocking blocking;

    @Inject
    AospCommandInjector(Help help, Version version, Echo echo, Blocking blocking) {
      this.help = help;
      this.version = version;
      this.echo = echo;
      this.blocking = blocking;
    }

    public CommandSupplier.Builder inject(CommandSupplier.Builder builder) {
      builder.addCommand("help", help);
      builder.addCommand("version", version);
      builder.addCommand("echo", echo);
      builder.addCommand("blocking", blocking);
      return builder;
    }
  }
}
