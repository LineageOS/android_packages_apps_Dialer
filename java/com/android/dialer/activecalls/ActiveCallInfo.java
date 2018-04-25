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

package com.android.dialer.activecalls;

import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;

/** Info of an active call */
@AutoValue
@SuppressWarnings("Guava")
public abstract class ActiveCallInfo {

  /** The {@link PhoneAccountHandle} the call is made with */
  public abstract Optional<PhoneAccountHandle> phoneAccountHandle();

  public static Builder builder() {
    return new AutoValue_ActiveCallInfo.Builder();
  }

  /** Builder for {@link ActiveCallInfo}. Only In Call UI should create ActiveCallInfo */
  @AutoValue.Builder
  public abstract static class Builder {

    public Builder setPhoneAccountHandle(@Nullable PhoneAccountHandle phoneAccountHandle) {
      return setPhoneAccountHandle(Optional.fromNullable(phoneAccountHandle));
    }

    public abstract Builder setPhoneAccountHandle(Optional<PhoneAccountHandle> phoneAccountHandle);

    public abstract ActiveCallInfo build();
  }
}
