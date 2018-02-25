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

import android.content.Context;
import android.support.annotation.MainThread;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.calllog.CallLogState;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.LightweightExecutor;
import com.android.dialer.common.concurrent.DialerFutures;
import com.android.dialer.metrics.FutureTimer;
import com.android.dialer.metrics.FutureTimer.LogCatMode;
import com.android.dialer.metrics.Metrics;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.Builder;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

/**
 * {@link PhoneLookup} which delegates to a configured set of {@link PhoneLookup PhoneLookups},
 * iterating, prioritizing, and coalescing data as necessary.
 *
 * <p>TODO(zachh): Consider renaming and moving this file since it does not implement PhoneLookup.
 */
public final class CompositePhoneLookup {

  private final ImmutableList<PhoneLookup> phoneLookups;
  private final FutureTimer futureTimer;
  private final CallLogState callLogState;
  private final ListeningExecutorService lightweightExecutorService;

  @VisibleForTesting
  @Inject
  public CompositePhoneLookup(
      ImmutableList<PhoneLookup> phoneLookups,
      FutureTimer futureTimer,
      CallLogState callLogState,
      @LightweightExecutor ListeningExecutorService lightweightExecutorService) {
    this.phoneLookups = phoneLookups;
    this.futureTimer = futureTimer;
    this.callLogState = callLogState;
    this.lightweightExecutorService = lightweightExecutorService;
  }

  /**
   * Delegates to a set of dependent lookups to build a complete {@link PhoneLookupInfo}.
   *
   * <p>Note: If any of the dependent lookups fails, the returned future will also fail. If any of
   * the dependent lookups does not complete, the returned future will also not complete.
   */
  @SuppressWarnings({"unchecked", "rawtype"})
  public ListenableFuture<PhoneLookupInfo> lookup(DialerPhoneNumber dialerPhoneNumber) {
    // TODO(zachh): Add short-circuiting logic so that this call is not blocked on low-priority
    // lookups finishing when a higher-priority one has already finished.
    List<ListenableFuture<?>> futures = new ArrayList<>();
    for (PhoneLookup<?> phoneLookup : phoneLookups) {
      ListenableFuture<?> lookupFuture = phoneLookup.lookup(dialerPhoneNumber);
      String eventName =
          String.format(Metrics.LOOKUP_TEMPLATE, phoneLookup.getClass().getSimpleName());
      futureTimer.applyTiming(lookupFuture, eventName);
      futures.add(lookupFuture);
    }
    ListenableFuture<PhoneLookupInfo> combinedFuture =
        Futures.transform(
            Futures.allAsList(futures),
            infos -> {
              Builder mergedInfo = PhoneLookupInfo.newBuilder();
              for (int i = 0; i < infos.size(); i++) {
                PhoneLookup phoneLookup = phoneLookups.get(i);
                phoneLookup.setSubMessage(mergedInfo, infos.get(i));
              }
              return mergedInfo.build();
            },
            lightweightExecutorService);
    String eventName =
        String.format(Metrics.LOOKUP_TEMPLATE, CompositePhoneLookup.class.getSimpleName());
    futureTimer.applyTiming(combinedFuture, eventName);
    return combinedFuture;
  }

  /**
   * Delegates to sub-lookups' {@link PhoneLookup#isDirty(ImmutableSet)} completing when the first
   * sub-lookup which returns true completes.
   */
  public ListenableFuture<Boolean> isDirty(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    List<ListenableFuture<Boolean>> futures = new ArrayList<>();
    for (PhoneLookup<?> phoneLookup : phoneLookups) {
      ListenableFuture<Boolean> isDirtyFuture = phoneLookup.isDirty(phoneNumbers);
      futures.add(isDirtyFuture);
      String eventName =
          String.format(Metrics.IS_DIRTY_TEMPLATE, phoneLookup.getClass().getSimpleName());
      futureTimer.applyTiming(isDirtyFuture, eventName, LogCatMode.LOG_VALUES);
    }
    // Executes all child lookups (possibly in parallel), completing when the first composite lookup
    // which returns "true" completes, and cancels the others.
    ListenableFuture<Boolean> firstMatching =
        DialerFutures.firstMatching(futures, Preconditions::checkNotNull, false /* defaultValue */);
    String eventName =
        String.format(Metrics.IS_DIRTY_TEMPLATE, CompositePhoneLookup.class.getSimpleName());
    futureTimer.applyTiming(firstMatching, eventName, LogCatMode.LOG_VALUES);
    return firstMatching;
  }

  /**
   * Delegates to a set of dependent lookups and combines results.
   *
   * <p>Note: If any of the dependent lookups fails, the returned future will also fail. If any of
   * the dependent lookups does not complete, the returned future will also not complete.
   */
  @SuppressWarnings("unchecked")
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, PhoneLookupInfo>> getMostRecentInfo(
      ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> existingInfoMap) {
    return Futures.transformAsync(
        callLogState.isBuilt(),
        isBuilt -> {
          List<ListenableFuture<ImmutableMap<DialerPhoneNumber, ?>>> futures = new ArrayList<>();
          for (PhoneLookup phoneLookup : phoneLookups) {
            futures.add(buildSubmapAndGetMostRecentInfo(existingInfoMap, phoneLookup, isBuilt));
          }
          ListenableFuture<ImmutableMap<DialerPhoneNumber, PhoneLookupInfo>> combinedFuture =
              Futures.transform(
                  Futures.allAsList(futures),
                  (allMaps) -> {
                    ImmutableMap.Builder<DialerPhoneNumber, PhoneLookupInfo> combinedMap =
                        ImmutableMap.builder();
                    for (DialerPhoneNumber dialerPhoneNumber : existingInfoMap.keySet()) {
                      PhoneLookupInfo.Builder combinedInfo = PhoneLookupInfo.newBuilder();
                      for (int i = 0; i < allMaps.size(); i++) {
                        ImmutableMap<DialerPhoneNumber, ?> map = allMaps.get(i);
                        Object subInfo = map.get(dialerPhoneNumber);
                        if (subInfo == null) {
                          throw new IllegalStateException(
                              "A sublookup didn't return an info for number: "
                                  + LogUtil.sanitizePhoneNumber(
                                      dialerPhoneNumber.getNormalizedNumber()));
                        }
                        phoneLookups.get(i).setSubMessage(combinedInfo, subInfo);
                      }
                      combinedMap.put(dialerPhoneNumber, combinedInfo.build());
                    }
                    return combinedMap.build();
                  },
                  lightweightExecutorService);
          String eventName = getMostRecentInfoEventName(this, isBuilt);
          futureTimer.applyTiming(combinedFuture, eventName);
          return combinedFuture;
        },
        MoreExecutors.directExecutor());
  }

  private <T> ListenableFuture<ImmutableMap<DialerPhoneNumber, T>> buildSubmapAndGetMostRecentInfo(
      ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> existingInfoMap,
      PhoneLookup<T> phoneLookup,
      boolean isBuilt) {
    Map<DialerPhoneNumber, T> submap =
        Maps.transformEntries(
            existingInfoMap,
            (dialerPhoneNumber, phoneLookupInfo) ->
                phoneLookup.getSubMessage(existingInfoMap.get(dialerPhoneNumber)));
    ListenableFuture<ImmutableMap<DialerPhoneNumber, T>> mostRecentInfoFuture =
        phoneLookup.getMostRecentInfo(ImmutableMap.copyOf(submap));
    String eventName = getMostRecentInfoEventName(phoneLookup, isBuilt);
    futureTimer.applyTiming(mostRecentInfoFuture, eventName);
    return mostRecentInfoFuture;
  }

  /** Delegates to sub-lookups' {@link PhoneLookup#onSuccessfulBulkUpdate()}. */
  public ListenableFuture<Void> onSuccessfulBulkUpdate() {
    return Futures.transformAsync(
        callLogState.isBuilt(),
        isBuilt -> {
          List<ListenableFuture<Void>> futures = new ArrayList<>();
          for (PhoneLookup<?> phoneLookup : phoneLookups) {
            ListenableFuture<Void> phoneLookupFuture = phoneLookup.onSuccessfulBulkUpdate();
            futures.add(phoneLookupFuture);
            String eventName = onSuccessfulBulkUpdatedEventName(phoneLookup, isBuilt);
            futureTimer.applyTiming(phoneLookupFuture, eventName);
          }
          ListenableFuture<Void> combinedFuture =
              Futures.transform(
                  Futures.allAsList(futures), unused -> null, lightweightExecutorService);
          String eventName = onSuccessfulBulkUpdatedEventName(this, isBuilt);
          futureTimer.applyTiming(combinedFuture, eventName);
          return combinedFuture;
        },
        MoreExecutors.directExecutor());
  }

  /** Delegates to sub-lookups' {@link PhoneLookup#registerContentObservers(Context)}. */
  @MainThread
  public void registerContentObservers(Context appContext) {
    for (PhoneLookup phoneLookup : phoneLookups) {
      phoneLookup.registerContentObservers(appContext);
    }
  }

  private static String getMostRecentInfoEventName(Object classNameSource, boolean isBuilt) {
    return String.format(
        !isBuilt
            ? Metrics.INITIAL_GET_MOST_RECENT_INFO_TEMPLATE
            : Metrics.GET_MOST_RECENT_INFO_TEMPLATE,
        classNameSource.getClass().getSimpleName());
  }

  private static String onSuccessfulBulkUpdatedEventName(Object classNameSource, boolean isBuilt) {
    return String.format(
        !isBuilt
            ? Metrics.INITIAL_ON_SUCCESSFUL_BULK_UPDATE_TEMPLATE
            : Metrics.ON_SUCCESSFUL_BULK_UPDATE_TEMPLATE,
        classNameSource.getClass().getSimpleName());
  }
}
