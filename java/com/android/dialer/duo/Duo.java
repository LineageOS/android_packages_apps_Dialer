/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.dialer.duo;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.telecom.Call;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import java.util.List;

/** Interface for Duo video call integration. */
public interface Duo {

  boolean isEnabled(@NonNull Context context);

  /**
   * @return true if Duo is installed and the user has gone through the set-up flow confirming their
   *     phone number.
   */
  boolean isActivated(@NonNull Context context);

  @MainThread
  boolean isReachable(@NonNull Context context, @Nullable String number);

  /** @return {@code null} if result is unknown. */
  @MainThread
  Optional<Boolean> supportsUpgrade(@NonNull Context context, @Nullable String number);

  @MainThread
  void updateReachability(@NonNull Context context, @NonNull List<String> numbers);

  @MainThread
  Intent getIntent(@NonNull Context context, @NonNull String number);

  @MainThread
  void requestUpgrade(@NonNull Context context, Call call);

  @MainThread
  void registerListener(@NonNull DuoListener listener);

  @MainThread
  void unregisterListener(@NonNull DuoListener listener);

  @StringRes
  @MainThread
  int getOutgoingCallTypeText();

  @StringRes
  @MainThread
  int getIncomingCallTypeText();

  /** Reachability information for a number. */
  @AutoValue
  abstract class ReachabilityData {
    public abstract String number();

    public abstract boolean videoCallable();

    public abstract boolean supportsUpgrade();

    public static ReachabilityData create(
        String number, boolean videoCallable, boolean supportsUpgrade) {
      return new AutoValue_Duo_ReachabilityData(number, videoCallable, supportsUpgrade);
    }
  }
}
