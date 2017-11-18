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
 * limitations under the License
 */
package com.android.dialer.phonelookup.testing;

import android.support.annotation.NonNull;
import android.telecom.Call;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

/** Fake implementation of {@link PhoneLookup} used for unit tests. */
@AutoValue
public abstract class FakePhoneLookup implements PhoneLookup {

  abstract PhoneLookupInfo lookupResult();

  abstract ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> bulkUpdateResult();

  abstract boolean isDirtyResult();

  public static Builder builder() {
    return new AutoValue_FakePhoneLookup.Builder()
        .setLookupResult(PhoneLookupInfo.getDefaultInstance())
        .setBulkUpdateResult(ImmutableMap.of())
        .setIsDirtyResult(false);
  }

  /** Builder. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setLookupResult(PhoneLookupInfo phoneLookupInfo);

    public abstract Builder setBulkUpdateResult(
        ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> map);

    public abstract Builder setIsDirtyResult(boolean isDirty);

    public abstract FakePhoneLookup build();
  }

  @Override
  public ListenableFuture<PhoneLookupInfo> lookup(@NonNull Call call) {
    SettableFuture<PhoneLookupInfo> future = SettableFuture.create();
    future.set(lookupResult());
    return future;
  }

  @Override
  public ListenableFuture<Boolean> isDirty(
      ImmutableSet<DialerPhoneNumber> phoneNumbers, long lastModified) {
    SettableFuture<Boolean> future = SettableFuture.create();
    future.set(isDirtyResult());
    return future;
  }

  @Override
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, PhoneLookupInfo>> bulkUpdate(
      ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> existingInfoMap, long lastModified) {
    SettableFuture<ImmutableMap<DialerPhoneNumber, PhoneLookupInfo>> future =
        SettableFuture.create();
    future.set(bulkUpdateResult());
    return future;
  }
}
