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

package com.android.dialer.phonelookup.composite;

import android.support.annotation.NonNull;
import android.telecom.Call;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link PhoneLookup} which delegates to a configured set of {@link PhoneLookup PhoneLookups},
 * iterating, prioritizing, and coalescing data as necessary.
 */
public final class CompositePhoneLookup implements PhoneLookup {

  private final ImmutableList<PhoneLookup> phoneLookups;

  public CompositePhoneLookup(ImmutableList<PhoneLookup> phoneLookups) {
    this.phoneLookups = phoneLookups;
  }

  /**
   * Delegates to a set of dependent lookups to build a complete {@link PhoneLookupInfo}.
   *
   * <p>Note: If any of the dependent lookups fails, the returned future will also fail. If any of
   * the dependent lookups does not complete, the returned future will also not complete.
   */
  @Override
  public ListenableFuture<PhoneLookupInfo> lookup(@NonNull Call call) {
    List<ListenableFuture<PhoneLookupInfo>> futures = new ArrayList<>();
    for (PhoneLookup phoneLookup : phoneLookups) {
      futures.add(phoneLookup.lookup(call));
    }
    return Futures.transform(
        Futures.allAsList(futures),
        new Function<List<PhoneLookupInfo>, PhoneLookupInfo>() {
          @Override
          public PhoneLookupInfo apply(List<PhoneLookupInfo> infos) {
            PhoneLookupInfo.Builder mergedInfo = PhoneLookupInfo.newBuilder();
            for (PhoneLookupInfo info : infos) {
              mergedInfo.mergeFrom(info);
            }
            return mergedInfo.build();
          }
        },
        MoreExecutors.directExecutor());
  }

  @Override
  public ListenableFuture<Boolean> isDirty(
      ImmutableSet<DialerPhoneNumber> phoneNumbers, long lastModified) {
    return null;
  }

  @Override
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, PhoneLookupInfo>> bulkUpdate(
      ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> existingInfoMap, long lastModified) {
    return null;
  }
}
