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

package com.android.dialer.simulator.stub;

import com.android.dialer.simulator.SimulatorEnrichedCall;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import javax.inject.Inject;

/** Stub implementation of {@link SimulatorEnrichedCall}. */
public class SimulatorEnrichedCallStub implements SimulatorEnrichedCall {

  @Inject
  public SimulatorEnrichedCallStub() {}

  @Override
  public ListenableFuture<Void> setupIncomingEnrichedCall(String number) {
    return Futures.immediateFuture(null);
  }

  @Override
  public ListenableFuture<Void> setupOutgoingEnrichedCall(String number) {
    return Futures.immediateFuture(null);
  }

  @Override
  public void onEnrichedCallStateChanged() {}
}
