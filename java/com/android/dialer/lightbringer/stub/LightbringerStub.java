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
import android.support.annotation.NonNull;
import com.android.dialer.common.Assert;
import com.android.dialer.lightbringer.Lightbringer;
import com.android.dialer.lightbringer.LightbringerListener;
import javax.inject.Inject;

public class LightbringerStub implements Lightbringer {

  @Inject
  public LightbringerStub() {}

  @Override
  public boolean isReachable(Context context, String number) {
    return false;
  }

  @Override
  public Intent getIntent(Context context, String number) {
    return null;
  }

  @Override
  public void registerListener(LightbringerListener listener) {}

  @Override
  public void unregisterListener(LightbringerListener listener) {}

  @Override
  public ComponentName getPhoneAccountComponentName(Context context) {
    return null;
  }

  @NonNull
  @Override
  public String getPackageName(@NonNull Context context) {
    throw Assert.createUnsupportedOperationFailException();
  }
}
