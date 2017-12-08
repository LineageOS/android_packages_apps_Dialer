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
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.LightweightExecutor;
import com.android.dialer.common.concurrent.DialerFutures;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/**
 * {@link PhoneLookup} which delegates to a configured set of {@link PhoneLookup PhoneLookups},
 * iterating, prioritizing, and coalescing data as necessary.
 */
public final class CompositePhoneLookup implements PhoneLookup {

  private final ImmutableList<PhoneLookup> phoneLookups;
  private final ListeningExecutorService lightweightExecutorService;

  @Inject
  CompositePhoneLookup(
      ImmutableList<PhoneLookup> phoneLookups,
      @LightweightExecutor ListeningExecutorService lightweightExecutorService) {
    this.phoneLookups = phoneLookups;
    this.lightweightExecutorService = lightweightExecutorService;
  }

  /**
   * Delegates to a set of dependent lookups to build a complete {@link PhoneLookupInfo}.
   *
   * <p>Note: If any of the dependent lookups fails, the returned future will also fail. If any of
   * the dependent lookups does not complete, the returned future will also not complete.
   */
  @Override
  public ListenableFuture<PhoneLookupInfo> lookup(@NonNull Call call) {
    // TODO(zachh): Add short-circuiting logic so that this call is not blocked on low-priority
    // lookups finishing when a higher-priority one has already finished.
    List<ListenableFuture<PhoneLookupInfo>> futures = new ArrayList<>();
    for (PhoneLookup phoneLookup : phoneLookups) {
      futures.add(phoneLookup.lookup(call));
    }
    return Futures.transform(
        Futures.allAsList(futures),
        infos -> {
          PhoneLookupInfo.Builder mergedInfo = PhoneLookupInfo.newBuilder();
          for (PhoneLookupInfo info : infos) {
            mergedInfo.mergeFrom(info);
          }
          return mergedInfo.build();
        },
        lightweightExecutorService);
  }

  @Override
  public ListenableFuture<Boolean> isDirty(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    List<ListenableFuture<Boolean>> futures = new ArrayList<>();
    for (PhoneLookup phoneLookup : phoneLookups) {
      futures.add(phoneLookup.isDirty(phoneNumbers));
    }
    // Executes all child lookups (possibly in parallel), completing when the first composite lookup
    // which returns "true" completes, and cancels the others.
    return DialerFutures.firstMatching(
        futures, Preconditions::checkNotNull, false /* defaultValue */);
  }

  /**
   * Delegates to a set of dependent lookups and combines results.
   *
   * <p>Note: If any of the dependent lookups fails, the returned future will also fail. If any of
   * the dependent lookups does not complete, the returned future will also not complete.
   */
  @Override
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, PhoneLookupInfo>>
      getMostRecentPhoneLookupInfo(
          ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> existingInfoMap) {
    List<ListenableFuture<ImmutableMap<DialerPhoneNumber, PhoneLookupInfo>>> futures =
        new ArrayList<>();
    for (PhoneLookup phoneLookup : phoneLookups) {
      futures.add(phoneLookup.getMostRecentPhoneLookupInfo(existingInfoMap));
    }
    return Futures.transform(
        Futures.allAsList(futures),
        (allMaps) -> {
          ImmutableMap.Builder<DialerPhoneNumber, PhoneLookupInfo> combinedMap =
              ImmutableMap.builder();
          for (DialerPhoneNumber dialerPhoneNumber : existingInfoMap.keySet()) {
            PhoneLookupInfo.Builder combinedInfo = PhoneLookupInfo.newBuilder();
            for (ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> map : allMaps) {
              PhoneLookupInfo subInfo = map.get(dialerPhoneNumber);
              if (subInfo == null) {
                throw new IllegalStateException(
                    "A sublookup didn't return an info for number: "
                        + LogUtil.sanitizePhoneNumber(dialerPhoneNumber.getRawInput().getNumber()));
              }
              combinedInfo.mergeFrom(subInfo);
            }
            combinedMap.put(dialerPhoneNumber, combinedInfo.build());
          }
          return combinedMap.build();
        },
        lightweightExecutorService);
  }

  @Override
  public ListenableFuture<Void> onSuccessfulBulkUpdate() {
    List<ListenableFuture<Void>> futures = new ArrayList<>();
    for (PhoneLookup phoneLookup : phoneLookups) {
      futures.add(phoneLookup.onSuccessfulBulkUpdate());
    }
    return Futures.transform(
        Futures.allAsList(futures), unused -> null, lightweightExecutorService);
  }
}
