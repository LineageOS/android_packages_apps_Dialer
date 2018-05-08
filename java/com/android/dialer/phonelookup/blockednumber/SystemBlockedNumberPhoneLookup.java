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

package com.android.dialer.phonelookup.blockednumber;

import android.content.Context;
import android.database.Cursor;
import android.provider.BlockedNumberContract.BlockedNumbers;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.util.ArraySet;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.blocking.FilteredNumberCompat;
import com.android.dialer.calllog.observer.MarkDirtyObserver;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.common.database.Selection;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.BlockedState;
import com.android.dialer.phonelookup.PhoneLookupInfo.Builder;
import com.android.dialer.phonelookup.PhoneLookupInfo.SystemBlockedNumberInfo;
import com.android.dialer.phonenumberproto.PartitionedNumbers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.Set;
import javax.inject.Inject;

/**
 * Lookup blocked numbers in the system database. Requires N+ and migration from dialer database
 * completed (need user consent to move data into system).
 */
public class SystemBlockedNumberPhoneLookup implements PhoneLookup<SystemBlockedNumberInfo> {

  private final Context appContext;
  private final ListeningExecutorService executorService;
  private final MarkDirtyObserver markDirtyObserver;

  @Inject
  SystemBlockedNumberPhoneLookup(
      @ApplicationContext Context appContext,
      @BackgroundExecutor ListeningExecutorService executorService,
      MarkDirtyObserver markDirtyObserver) {
    this.appContext = appContext;
    this.executorService = executorService;
    this.markDirtyObserver = markDirtyObserver;
  }

  @Override
  public ListenableFuture<SystemBlockedNumberInfo> lookup(@NonNull DialerPhoneNumber number) {
    if (!FilteredNumberCompat.useNewFiltering(appContext)) {
      return Futures.immediateFuture(SystemBlockedNumberInfo.getDefaultInstance());
    }
    return executorService.submit(() -> queryNumbers(ImmutableSet.of(number)).get(number));
  }

  @Override
  public ListenableFuture<Boolean> isDirty(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    // Dirty state is recorded with PhoneLookupDataSource.markDirtyAndNotify(), which will force
    // rebuild with the CallLogFramework
    return Futures.immediateFuture(false);
  }

  @Override
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, SystemBlockedNumberInfo>>
      getMostRecentInfo(ImmutableMap<DialerPhoneNumber, SystemBlockedNumberInfo> existingInfoMap) {
    LogUtil.enterBlock("SystemBlockedNumberPhoneLookup.getMostRecentPhoneLookupInfo");
    if (!FilteredNumberCompat.useNewFiltering(appContext)) {
      return Futures.immediateFuture(existingInfoMap);
    }
    return executorService.submit(() -> queryNumbers(existingInfoMap.keySet()));
  }

  @WorkerThread
  private ImmutableMap<DialerPhoneNumber, SystemBlockedNumberInfo> queryNumbers(
      ImmutableSet<DialerPhoneNumber> numbers) {
    Assert.isWorkerThread();
    PartitionedNumbers partitionedNumbers = new PartitionedNumbers(numbers);

    Set<DialerPhoneNumber> blockedNumbers = new ArraySet<>();

    Selection normalizedSelection =
        Selection.column(BlockedNumbers.COLUMN_E164_NUMBER)
            .in(partitionedNumbers.validE164Numbers());
    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                BlockedNumbers.CONTENT_URI,
                new String[] {BlockedNumbers.COLUMN_E164_NUMBER},
                normalizedSelection.getSelection(),
                normalizedSelection.getSelectionArgs(),
                null)) {
      while (cursor != null && cursor.moveToNext()) {
        blockedNumbers.addAll(
            partitionedNumbers.dialerPhoneNumbersForValidE164(cursor.getString(0)));
      }
    }

    Selection rawSelection =
        Selection.column(BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
            .in(partitionedNumbers.invalidNumbers());
    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                BlockedNumbers.CONTENT_URI,
                new String[] {BlockedNumbers.COLUMN_ORIGINAL_NUMBER},
                rawSelection.getSelection(),
                rawSelection.getSelectionArgs(),
                null)) {
      while (cursor != null && cursor.moveToNext()) {
        blockedNumbers.addAll(partitionedNumbers.dialerPhoneNumbersForInvalid(cursor.getString(0)));
      }
    }

    ImmutableMap.Builder<DialerPhoneNumber, SystemBlockedNumberInfo> result =
        ImmutableMap.builder();

    for (DialerPhoneNumber number : numbers) {
      result.put(
          number,
          SystemBlockedNumberInfo.newBuilder()
              .setBlockedState(
                  blockedNumbers.contains(number) ? BlockedState.BLOCKED : BlockedState.NOT_BLOCKED)
              .build());
    }

    return result.build();
  }

  @Override
  public void setSubMessage(Builder phoneLookupInfo, SystemBlockedNumberInfo subMessage) {
    phoneLookupInfo.setSystemBlockedNumberInfo(subMessage);
  }

  @Override
  public SystemBlockedNumberInfo getSubMessage(PhoneLookupInfo phoneLookupInfo) {
    return phoneLookupInfo.getSystemBlockedNumberInfo();
  }

  @Override
  public ListenableFuture<Void> onSuccessfulBulkUpdate() {
    return Futures.immediateFuture(null);
  }

  @Override
  public void registerContentObservers() {
    appContext
        .getContentResolver()
        .registerContentObserver(
            BlockedNumbers.CONTENT_URI,
            true, // BlockedNumbers notifies on the item
            markDirtyObserver);
  }

  @Override
  public void unregisterContentObservers() {
    appContext.getContentResolver().unregisterContentObserver(markDirtyObserver);
  }

  @Override
  public ListenableFuture<Void> clearData() {
    return Futures.immediateFuture(null);
  }

  @Override
  public String getLoggingName() {
    return "SystemBlockedNumberPhoneLookup";
  }
}
