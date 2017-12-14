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

  /** @return true if the Duo integration is enabled on this device. */
  boolean isEnabled(@NonNull Context context);

  /** @return true if Duo is installed on this device. */
  boolean isInstalled(@NonNull Context context);

  /**
   * @return true if Duo is installed and the user has gone through the set-up flow confirming their
   *     phone number.
   */
  boolean isActivated(@NonNull Context context);

  /** @return true if the parameter number is reachable on Duo. */
  @MainThread
  boolean isReachable(@NonNull Context context, @Nullable String number);

  /**
   * @return true if the number supports upgrading a voice call to a Duo video call. Returns {@code
   *     null} if result is unknown.
   */
  @MainThread
  Optional<Boolean> supportsUpgrade(@NonNull Context context, @Nullable String number);

  /** Starts a task to update the reachability of the parameter numbers asynchronously. */
  @MainThread
  void updateReachability(@NonNull Context context, @NonNull List<String> numbers);

  /**
   * Clears the current reachability data and starts a task to load the latest reachability data
   * asynchronously.
   */
  @MainThread
  void reloadReachability(@NonNull Context context);

  /**
   * @return an Intent to start a Duo video call with the parameter number. Must be started using
   *     startActivityForResult.
   */
  @MainThread
  Intent getIntent(@NonNull Context context, @NonNull String number);

  /** Requests upgrading the parameter ongoing call to a Duo video call. */
  @MainThread
  void requestUpgrade(@NonNull Context context, Call call);

  /** Registers a listener for reachability data changes. */
  @MainThread
  void registerListener(@NonNull DuoListener listener);

  /** Unregisters a listener for reachability data changes. */
  @MainThread
  void unregisterListener(@NonNull DuoListener listener);

  /** The string resource to use for outgoing Duo call entries in call details. */
  @StringRes
  @MainThread
  int getOutgoingCallTypeText();

  /** The string resource to use for incoming Duo call entries in call details. */
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
