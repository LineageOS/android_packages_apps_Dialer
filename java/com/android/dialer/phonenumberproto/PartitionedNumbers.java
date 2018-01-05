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

package com.android.dialer.phonenumberproto;

import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.ArraySet;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.common.Assert;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.util.Map;
import java.util.Set;

/**
 * Divides a set of {@link DialerPhoneNumber DialerPhoneNumbers} by those that can be formatted to
 * E164 and those that cannot.
 */
public final class PartitionedNumbers {
  private final ImmutableMap<String, ImmutableSet<DialerPhoneNumber>>
      e164NumbersToDialerPhoneNumbers;
  private final ImmutableMap<String, ImmutableSet<DialerPhoneNumber>>
      unformattableNumbersToDialerPhoneNumbers;

  @WorkerThread
  public PartitionedNumbers(@NonNull ImmutableSet<DialerPhoneNumber> dialerPhoneNumbers) {
    Assert.isWorkerThread();
    DialerPhoneNumberUtil dialerPhoneNumberUtil =
        new DialerPhoneNumberUtil(PhoneNumberUtil.getInstance());
    Map<String, Set<DialerPhoneNumber>> e164MapBuilder = new ArrayMap<>();
    Map<String, Set<DialerPhoneNumber>> unformattableMapBuilder = new ArrayMap<>();

    for (DialerPhoneNumber dialerPhoneNumber : dialerPhoneNumbers) {
      Optional<String> e164 = dialerPhoneNumberUtil.formatToE164(dialerPhoneNumber);
      if (e164.isPresent()) {
        String validE164 = e164.get();
        Set<DialerPhoneNumber> currentNumbers = e164MapBuilder.get(validE164);
        if (currentNumbers == null) {
          currentNumbers = new ArraySet<>();
          e164MapBuilder.put(validE164, currentNumbers);
        }
        currentNumbers.add(dialerPhoneNumber);
      } else {
        String unformattableNumber = dialerPhoneNumber.getRawInput().getNumber();
        Set<DialerPhoneNumber> currentNumbers = unformattableMapBuilder.get(unformattableNumber);
        if (currentNumbers == null) {
          currentNumbers = new ArraySet<>();
          unformattableMapBuilder.put(unformattableNumber, currentNumbers);
        }
        currentNumbers.add(dialerPhoneNumber);
      }
    }

    e164NumbersToDialerPhoneNumbers = makeImmutable(e164MapBuilder);
    unformattableNumbersToDialerPhoneNumbers = makeImmutable(unformattableMapBuilder);
  }

  /** Returns the set of formatted number from the original DialerPhoneNumbers */
  @NonNull
  public ImmutableSet<String> unformattableNumbers() {
    return unformattableNumbersToDialerPhoneNumbers.keySet();
  }

  /** Returns the set of raw number that is unformattable from the original DialerPhoneNumbers */
  @NonNull
  public ImmutableSet<String> validE164Numbers() {
    return e164NumbersToDialerPhoneNumbers.keySet();
  }

  /**
   * Returns the corresponding set of original DialerPhoneNumber that maps to the e.164 number, or
   * an empty set if the number is not found.
   */
  @NonNull
  public ImmutableSet<DialerPhoneNumber> dialerPhoneNumbersForE164(String e164) {
    return Assert.isNotNull(e164NumbersToDialerPhoneNumbers.get(e164));
  }

  /**
   * Returns the corresponding set of original DialerPhoneNumber that maps to the unformattable
   * number returned by {@link #unformattableNumbers()}, or an empty set if the number is not found.
   */
  @NonNull
  public ImmutableSet<DialerPhoneNumber> dialerPhoneNumbersForUnformattable(
      String unformattableNumber) {
    return Assert.isNotNull(unformattableNumbersToDialerPhoneNumbers.get(unformattableNumber));
  }

  private static <K, V> ImmutableMap<K, ImmutableSet<V>> makeImmutable(
      Map<K, Set<V>> mutableMapOfSet) {
    ImmutableMap.Builder<K, ImmutableSet<V>> mapBuilder = ImmutableMap.builder();
    for (Map.Entry<K, Set<V>> entry : mutableMapOfSet.entrySet()) {
      mapBuilder.put(entry.getKey(), ImmutableSet.copyOf(entry.getValue()));
    }
    return mapBuilder.build();
  }
}
