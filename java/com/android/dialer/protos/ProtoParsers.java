/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.dialer.protos;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import com.android.dialer.common.Assert;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

/** Useful methods for using Protocol Buffers with Android. */
public final class ProtoParsers {

  private ProtoParsers() {}

  /** Retrieve a proto from a Bundle which was not created within the current executable/version. */
  @SuppressWarnings("unchecked") // We want to eventually optimize away parser classes, so cast
  public static <T extends MessageLite> T get(Bundle bundle, String key, T defaultInstance)
      throws InvalidProtocolBufferException {
    byte[] bytes = bundle.getByteArray(key);
    return (T) mergeFrom(bytes, defaultInstance.getDefaultInstanceForType());
  }

  /**
   * Retrieve a proto from a trusted bundle which was created within the current executable/version.
   *
   * @throws RuntimeException if the proto cannot be parsed
   */
  public static <T extends MessageLite> T getTrusted(Bundle bundle, String key, T defaultInstance) {
    try {
      return get(bundle, key, defaultInstance);
    } catch (InvalidProtocolBufferException e) {
      throw Assert.createIllegalStateFailException(e.toString());
    }
  }

  /**
   * Retrieve a proto from a trusted bundle which was created within the current executable/version.
   *
   * @throws RuntimeException if the proto cannot be parsed
   */
  public static <T extends MessageLite> T getTrusted(Intent intent, String key, T defaultInstance) {
    return getTrusted(intent.getExtras(), key, defaultInstance);
  }

  /**
   * Stores a proto in a Bundle, for later retrieval by {@link #get(Bundle, String, MessageLite)} or
   * {@link #getFromInstanceState(Bundle, String, MessageLite)}.
   */
  public static void put(
      @NonNull Bundle bundle, @NonNull String key, @NonNull MessageLite message) {
    Assert.checkState(message != null);
    bundle.putByteArray(key, message.toByteArray());
  }

  /**
   * Stores a proto in an Intent, for later retrieval by {@link #get(Bundle, String, MessageLite)}.
   * Needs separate method because Intent has similar to but different API than Bundle.
   */
  public static void put(@NonNull Intent intent, @NonNull String key, MessageLite message) {
    Assert.checkState(message != null);
    intent.putExtra(key, message.toByteArray());
  }

  /** Parses a proto, throwing parser errors as runtime exceptions. */
  @SuppressWarnings("unchecked") // We want to eventually optimize away parser classes
  private static <T extends MessageLite> T mergeFrom(byte[] bytes, T defaultInstance) {
    try {
      return (T) defaultInstance.toBuilder().mergeFrom(bytes).build();
    } catch (InvalidProtocolBufferException e) {
      throw Assert.createIllegalStateFailException(e.toString());
    }
  }
}
