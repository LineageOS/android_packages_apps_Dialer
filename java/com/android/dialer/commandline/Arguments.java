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

import android.support.annotation.Nullable;
import com.android.dialer.commandline.Command.IllegalCommandLineArgumentException;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.google.common.collect.UnmodifiableIterator;

/**
 * Parses command line arguments into optional flags (--foo, --key=value, --key value) and required
 * positionals (which must be passed in order). Flags must start with "--" and are always before
 * positionals. If flags are used "--" must be placed before positionals.
 *
 * <p>--flag will be interpreted as --flag=true, and --noflag as --flag=false
 *
 * <p>Grammar:<br>
 * dialer-cmd.py <cmd> <args><br>
 * <args> = (<flags> -- <positionals>) | <positionals><br>
 * <flags> = "no"?<name>(<separator><value>)?<br>
 * <separator> = " " | "="
 */
@AutoValue
public abstract class Arguments {

  public static final Arguments EMPTY =
      new AutoValue_Arguments(ImmutableMap.of(), ImmutableList.of());

  public abstract ImmutableMap<String, String> getFlags();

  public abstract ImmutableList<String> getPositionals();

  /**
   * Return the positional at {@code position}. Throw {@link IllegalCommandLineArgumentException} if
   * it is absent and reports to the user {@code name} is expected.
   */
  public String expectPositional(int position, String name)
      throws IllegalCommandLineArgumentException {
    if (getPositionals().size() <= position) {
      throw new IllegalCommandLineArgumentException(name + " expected");
    }
    return getPositionals().get(position);
  }

  public Boolean getBoolean(String flag, boolean defaultValue)
      throws IllegalCommandLineArgumentException {
    if (!getFlags().containsKey(flag)) {
      return defaultValue;
    }
    switch (getFlags().get(flag)) {
      case "true":
        return true;
      case "false":
        return false;
      default:
        throw new IllegalCommandLineArgumentException("boolean value expected for " + flag);
    }
  }

  public static Arguments parse(@Nullable String[] rawArguments)
      throws IllegalCommandLineArgumentException {
    if (rawArguments == null) {
      return EMPTY;
    }
    return parse(Iterators.forArray(rawArguments));
  }

  public static Arguments parse(Iterable<String> rawArguments)
      throws IllegalCommandLineArgumentException {
    return parse(Iterators.unmodifiableIterator(rawArguments.iterator()));
  }

  public static Arguments parse(UnmodifiableIterator<String> iterator)
      throws IllegalCommandLineArgumentException {
    PeekingIterator<String> peekingIterator = Iterators.peekingIterator(iterator);
    ImmutableMap<String, String> flags = parseFlags(peekingIterator);
    ImmutableList<String> positionals = parsePositionals(peekingIterator);

    return new AutoValue_Arguments(flags, positionals);
  }

  private static ImmutableMap<String, String> parseFlags(PeekingIterator<String> iterator)
      throws IllegalCommandLineArgumentException {
    ImmutableMap.Builder<String, String> flags = ImmutableMap.builder();
    if (!iterator.hasNext()) {
      return flags.build();
    }
    if (!iterator.peek().startsWith("--")) {
      return flags.build();
    }

    while (iterator.hasNext()) {
      String peek = iterator.peek();
      if (peek.equals("--")) {
        iterator.next();
        return flags.build();
      }
      if (peek.startsWith("--")) {
        String key = iterator.next().substring(2);
        String value;
        if (iterator.hasNext() && !iterator.peek().startsWith("--")) {
          value = iterator.next();
        } else if (key.contains("=")) {
          String[] entry = key.split("=", 2);
          key = entry[0];
          value = entry[1];
        } else if (key.startsWith("no")) {
          key = key.substring(2);
          value = "false";
        } else {
          value = "true";
        }
        flags.put(key, value);
      } else {
        throw new IllegalCommandLineArgumentException("flag or '--' expected");
      }
    }
    return flags.build();
  }

  private static ImmutableList<String> parsePositionals(PeekingIterator<String> iterator) {
    ImmutableList.Builder<String> positionals = ImmutableList.builder();
    positionals.addAll(iterator);
    return positionals.build();
  }
}
