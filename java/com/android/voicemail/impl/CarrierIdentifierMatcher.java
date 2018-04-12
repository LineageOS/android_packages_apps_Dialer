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

package com.android.voicemail.impl;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;

/**
 * Matches a {@link CarrierIdentifier}. Full equality check on CarrierIdentifiers is often unfit
 * because non-MVNO carriers usually just specify the {@link CarrierIdentifier#mccMnc()} while their
 * {@link CarrierIdentifier#gid1()} could be anything. This matcher ignore fields that are not
 * specified in the matcher.
 */
@AutoValue
public abstract class CarrierIdentifierMatcher {

  public abstract String mccMnc();

  public abstract Optional<String> gid1();

  public static Builder builder() {
    return new AutoValue_CarrierIdentifierMatcher.Builder();
  }

  /** Builder for the matcher */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setMccMnc(String mccMnc);

    public abstract Builder setGid1(String gid1);

    public abstract CarrierIdentifierMatcher build();
  }

  public boolean matches(CarrierIdentifier carrierIdentifier) {
    if (!mccMnc().equals(carrierIdentifier.mccMnc())) {
      return false;
    }
    if (gid1().isPresent()) {
      if (!gid1().get().equalsIgnoreCase(carrierIdentifier.gid1())) {
        return false;
      }
    }
    return true;
  }
}
