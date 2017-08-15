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

package com.android.dialer.lightbringer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telecom.Call;
import android.telecom.PhoneAccountHandle;

public interface Lightbringer {

  boolean isEnabled();

  @MainThread
  boolean isReachable(@NonNull Context context, @Nullable String number);

  @MainThread
  boolean supportsUpgrade(@NonNull Context context, @Nullable String number);

  @MainThread
  Intent getIntent(@NonNull Context context, @NonNull String number);

  @MainThread
  void requestUpgrade(Call call);

  @MainThread
  void registerListener(@NonNull LightbringerListener listener);

  @MainThread
  void unregisterListener(@NonNull LightbringerListener listener);

  @Nullable
  @MainThread
  ComponentName getPhoneAccountComponentName();

  @Nullable
  @MainThread
  PhoneAccountHandle getPhoneAccountHandle();

  @Nullable
  @MainThread
  String getPackageName();
}
