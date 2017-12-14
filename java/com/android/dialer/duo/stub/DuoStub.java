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

package com.android.dialer.duo.stub;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.telecom.Call;
import com.android.dialer.common.Assert;
import com.android.dialer.duo.Duo;
import com.android.dialer.duo.DuoListener;
import com.google.common.base.Optional;
import java.util.List;
import javax.inject.Inject;

public class DuoStub implements Duo {

  @Inject
  public DuoStub() {}

  @Override
  public boolean isEnabled(@NonNull Context context) {
    return false;
  }

  @Override
  public boolean isInstalled(@NonNull Context context) {
    return false;
  }

  @Override
  public boolean isActivated(@NonNull Context context) {
    return false;
  }

  @MainThread
  @Override
  public boolean isReachable(@NonNull Context context, @Nullable String number) {
    Assert.isMainThread();
    Assert.isNotNull(context);
    return false;
  }

  @MainThread
  @Override
  public Optional<Boolean> supportsUpgrade(@NonNull Context context, @Nullable String number) {
    Assert.isMainThread();
    Assert.isNotNull(context);
    return Optional.of(false);
  }

  @Override
  public void updateReachability(@NonNull Context context, @NonNull List<String> numbers) {
    Assert.isMainThread();
    Assert.isNotNull(context);
    Assert.isNotNull(numbers);
  }

  @Override
  public void reloadReachability(@NonNull Context context) {}

  @MainThread
  @Override
  public Intent getIntent(@NonNull Context context, @NonNull String number) {
    Assert.isMainThread();
    Assert.isNotNull(context);
    Assert.isNotNull(number);
    return null;
  }

  @MainThread
  @Override
  public void requestUpgrade(@NonNull Context context, Call call) {
    Assert.isMainThread();
    Assert.isNotNull(call);
  }

  @MainThread
  @Override
  public void registerListener(DuoListener listener) {
    Assert.isMainThread();
    Assert.isNotNull(listener);
  }

  @MainThread
  @Override
  public void unregisterListener(DuoListener listener) {
    Assert.isMainThread();
    Assert.isNotNull(listener);
  }

  @StringRes
  @Override
  public int getOutgoingCallTypeText() {
    return -1;
  }

  @StringRes
  @Override
  public int getIncomingCallTypeText() {
    return -1;
  }
}
