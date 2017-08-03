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

package com.android.dialer.lightbringer.stub;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telecom.Call;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.common.Assert;
import com.android.dialer.lightbringer.Lightbringer;
import com.android.dialer.lightbringer.LightbringerListener;
import javax.inject.Inject;

public class LightbringerStub implements Lightbringer {

  @Inject
  public LightbringerStub() {}

  @Override
  public boolean isEnabled() {
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
  public boolean supportsUpgrade(@NonNull Context context, @Nullable String number) {
    Assert.isMainThread();
    Assert.isNotNull(context);
    return false;
  }

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
  public void requestUpgrade(Call call) {
    Assert.isMainThread();
    Assert.isNotNull(call);
  }

  @MainThread
  @Override
  public void registerListener(LightbringerListener listener) {
    Assert.isMainThread();
    Assert.isNotNull(listener);
  }

  @MainThread
  @Override
  public void unregisterListener(LightbringerListener listener) {
    Assert.isMainThread();
    Assert.isNotNull(listener);
  }

  @Nullable
  @Override
  public ComponentName getPhoneAccountComponentName() {
    return null;
  }

  @Nullable
  @Override
  public PhoneAccountHandle getPhoneAccountHandle() {
    return null;
  }

  @Nullable
  @Override
  public String getPackageName() {
    return null;
  }
}
