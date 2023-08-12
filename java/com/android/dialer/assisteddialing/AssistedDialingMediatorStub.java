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

package com.android.dialer.assisteddialing;

import androidx.annotation.NonNull;

import java.util.Optional;

/** A stub assisted dialing implementation. */
public final class AssistedDialingMediatorStub implements AssistedDialingMediator {

  /** Always returns an empty Optional. */
  @Override
  public Optional<TransformationInfo> attemptAssistedDial(@NonNull String numberToTransform) {
    return Optional.empty();
  }

  /** Always returns an empty Optional. */
  @Override
  public Optional<String> userHomeCountryCode() {
    return Optional.empty();
  }

  @Override
  public boolean isPlatformEligible() {
    return false;
  }
}
