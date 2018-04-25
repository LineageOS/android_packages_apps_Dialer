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

package com.android.dialer.activecalls.impl;

import android.support.annotation.MainThread;
import com.android.dialer.activecalls.ActiveCallInfo;
import com.android.dialer.activecalls.ActiveCalls;
import com.android.dialer.common.Assert;
import com.google.common.collect.ImmutableList;
import javax.inject.Inject;

/** Implementation of {@link ActiveCalls} */
public class ActiveCallsImpl implements ActiveCalls {

  ImmutableList<ActiveCallInfo> activeCalls = ImmutableList.of();

  @Inject
  ActiveCallsImpl() {}

  @Override
  public ImmutableList<ActiveCallInfo> getActiveCalls() {
    return activeCalls;
  }

  @Override
  @MainThread
  public void setActiveCalls(ImmutableList<ActiveCallInfo> activeCalls) {
    Assert.isMainThread();
    this.activeCalls = Assert.isNotNull(activeCalls);
  }
}
