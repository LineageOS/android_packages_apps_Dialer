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

package com.android.dialer.phonelookup.emergency;

import android.content.Context;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.EmergencyInfo;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import javax.inject.Inject;

/**
 * PhoneLookup implementation for checking if a number is an emergency number.
 *
 * <p>The check has to be done in a PhoneLookup as it involves detecting the user's location and
 * obtaining SIM info, which are expensive operations. Doing it in the main thread will make the UI
 * super janky.
 */
public class EmergencyPhoneLookup implements PhoneLookup<EmergencyInfo> {

  private final Context appContext;
  private final ListeningExecutorService backgroundExecutorService;

  @Inject
  EmergencyPhoneLookup(
      @ApplicationContext Context appContext,
      @BackgroundExecutor ListeningExecutorService backgroundExecutorService) {
    this.appContext = appContext;
    this.backgroundExecutorService = backgroundExecutorService;
  }

  @Override
  public ListenableFuture<EmergencyInfo> lookup(DialerPhoneNumber dialerPhoneNumber) {
    return backgroundExecutorService.submit(
        () ->
            EmergencyInfo.newBuilder()
                .setIsEmergencyNumber(
                    PhoneNumberHelper.isLocalEmergencyNumber(
                        appContext, dialerPhoneNumber.getNormalizedNumber()))
                .build());
  }

  @Override
  public ListenableFuture<Boolean> isDirty(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    return Futures.immediateFuture(false);
  }

  @Override
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, EmergencyInfo>> getMostRecentInfo(
      ImmutableMap<DialerPhoneNumber, EmergencyInfo> existingInfoMap) {
    // We can update EmergencyInfo for all numbers in the provided map, but the negative impact on
    // performance is intolerable as checking a single number involves detecting the user's location
    // and obtaining SIM info, which will take more than 100ms (see
    // android.telephony.PhoneNumberUtils#isLocalEmergencyNumber(Context, int, String) for details).
    //
    // As emergency numbers won't change in a country, the only case we will miss is that
    //   (1) a number is an emergency number in country A but not in country B,
    //   (2) a user has an emergency call entry when they are in country A, and
    //   (3) they travel from A to B,
    // which is a rare event.
    //
    // We can update the implementation if telecom supports batch check in the future.
    return Futures.immediateFuture(existingInfoMap);
  }

  @Override
  public void setSubMessage(PhoneLookupInfo.Builder destination, EmergencyInfo subMessage) {
    destination.setEmergencyInfo(subMessage);
  }

  @Override
  public EmergencyInfo getSubMessage(PhoneLookupInfo phoneLookupInfo) {
    return phoneLookupInfo.getEmergencyInfo();
  }

  @Override
  public ListenableFuture<Void> onSuccessfulBulkUpdate() {
    return Futures.immediateFuture(null);
  }

  @Override
  public void registerContentObservers() {
    // No content observer to register.
  }

  @Override
  public void unregisterContentObservers() {
    // Nothing to be done as no content observer is registered.
  }

  @Override
  public ListenableFuture<Void> clearData() {
    return Futures.immediateFuture(null);
  }

  @Override
  public String getLoggingName() {
    return "EmergencyPhoneLookup";
  }
}
