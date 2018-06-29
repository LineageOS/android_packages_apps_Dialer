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

package com.android.incallui.speakeasy;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import com.android.incallui.call.DialerCall;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import javax.inject.Inject;

/** Default implementation of SpeakEasyCallManager. */
public class SpeakEasyCallManagerStub implements SpeakEasyCallManager {

  @Inject
  public SpeakEasyCallManagerStub() {}

  /** Returns an absent optional. */
  @Override
  @Nullable
  public Optional<Fragment> getSpeakEasyFragment(DialerCall call) {
    return Optional.empty();
  }

  /** Always inert in the stub. */
  @Override
  public void onCallRemoved(DialerCall call) {}

  @Override
  public ListenableFuture<Void> onNewIncomingCall(@NonNull DialerCall call) {
    return Futures.immediateFuture(null);
  }

  /** Always returns false. */
  @Override
  public boolean isAvailable(@NonNull Context unused) {
    return false;
  }

  /** Always returns a stub string. */
  @NonNull
  @Override
  public String getConfigProviderFlag() {
    return "not_yet_implmented";
  }
}
