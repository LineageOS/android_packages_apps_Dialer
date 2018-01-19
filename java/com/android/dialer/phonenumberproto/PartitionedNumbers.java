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
import android.telephony.PhoneNumberUtils;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.common.Assert;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.util.Map;
import java.util.Set;

/**
 * Divides a set of {@link DialerPhoneNumber DialerPhoneNumbers} according to those that are valid
 * according to libphonenumber, and those that are not.
 */
public final class PartitionedNumbers {
  private final ImmutableMap<String, ImmutableSet<DialerPhoneNumber>>
      e164NumbersToDialerPhoneNumbers;
  private final ImmutableMap<String, ImmutableSet<DialerPhoneNumber>>
      invalidNumbersToDialerPhoneNumbers;

  @WorkerThread
  public PartitionedNumbers(@NonNull ImmutableSet<DialerPhoneNumber> dialerPhoneNumbers) {
    Assert.isWorkerThread();
    DialerPhoneNumberUtil dialerPhoneNumberUtil =
        new DialerPhoneNumberUtil(PhoneNumberUtil.getInstance());
    Map<String, Set<DialerPhoneNumber>> e164MapBuilder = new ArrayMap<>();
    Map<String, Set<DialerPhoneNumber>> invalidMapBuilder = new ArrayMap<>();

    for (DialerPhoneNumber dialerPhoneNumber : dialerPhoneNumbers) {
      Optional<String> optValidE164 = dialerPhoneNumberUtil.formatToValidE164(dialerPhoneNumber);
      /*
       * Numbers with post-dial digits are considered valid and can be converted to E164, but their
       * post dial digits are lost in the process. Similarly, if a contact's number has a post-dial
       * digits, the normalized version of it stored in the contacts database does not include the
       * post dial digits.
       *
       * A number with post-dial digits should not match a contact whose number does not have
       * post-dial digits, which means that we cannot normalize such numbers for use in bulk lookup.
       * Treat them as invalid which will cause them to be processed individually using
       * ContactsContract.PHONE_LOOKUP.
       */
      if (optValidE164.isPresent() && !hasPostDialDigits(dialerPhoneNumber)) {
        String validE164 = optValidE164.get();
        Set<DialerPhoneNumber> currentNumbers = e164MapBuilder.get(validE164);
        if (currentNumbers == null) {
          currentNumbers = new ArraySet<>();
          e164MapBuilder.put(validE164, currentNumbers);
        }
        currentNumbers.add(dialerPhoneNumber);
      } else {
        String invalidNumber = dialerPhoneNumber.getRawInput().getNumber();
        Set<DialerPhoneNumber> currentNumbers = invalidMapBuilder.get(invalidNumber);
        if (currentNumbers == null) {
          currentNumbers = new ArraySet<>();
          invalidMapBuilder.put(invalidNumber, currentNumbers);
        }
        currentNumbers.add(dialerPhoneNumber);
      }
    }

    e164NumbersToDialerPhoneNumbers = makeImmutable(e164MapBuilder);
    invalidNumbersToDialerPhoneNumbers = makeImmutable(invalidMapBuilder);
  }

  private boolean hasPostDialDigits(DialerPhoneNumber dialerPhoneNumber) {
    return !PhoneNumberUtils.extractPostDialPortion(dialerPhoneNumber.getRawInput().getNumber())
        .isEmpty();
  }

  /** Returns the set of invalid numbers from the original DialerPhoneNumbers */
  @NonNull
  public ImmutableSet<String> invalidNumbers() {
    return invalidNumbersToDialerPhoneNumbers.keySet();
  }

  /** Returns the set of valid, E164 formatted numbers from the original DialerPhoneNumbers */
  @NonNull
  public ImmutableSet<String> validE164Numbers() {
    return e164NumbersToDialerPhoneNumbers.keySet();
  }

  /**
   * Returns the corresponding set of original DialerPhoneNumbers that map to the valid E164 number
   * from {@link #validE164Numbers()}.
   *
   * @throws NullPointerException if there are no numbers found
   */
  @NonNull
  public ImmutableSet<DialerPhoneNumber> dialerPhoneNumbersForValidE164(String validE164) {
    return Assert.isNotNull(e164NumbersToDialerPhoneNumbers.get(validE164));
  }

  /**
   * Returns the corresponding set of original DialerPhoneNumbers that map to the invalid number
   * from {@link #invalidNumbers()}.
   *
   * @throws NullPointerException if there are no numbers found
   */
  @NonNull
  public ImmutableSet<DialerPhoneNumber> dialerPhoneNumbersForInvalid(String invalidNumber) {
    return Assert.isNotNull(invalidNumbersToDialerPhoneNumbers.get(invalidNumber));
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
