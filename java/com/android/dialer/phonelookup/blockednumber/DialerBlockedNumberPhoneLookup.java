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
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.telecom.Call;
import android.util.ArraySet;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.common.database.Selection;
import com.android.dialer.database.FilteredNumberContract.FilteredNumber;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberColumns;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberTypes;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.BlockedState;
import com.android.dialer.phonelookup.PhoneLookupInfo.DialerBlockedNumberInfo;
import com.android.dialer.phonenumberproto.DialerPhoneNumberUtil;
import com.android.dialer.phonenumberproto.PartitionedNumbers;
import com.android.dialer.telecom.TelecomCallUtil;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.util.Set;
import javax.inject.Inject;

/**
 * Lookup blocked numbers in the dialer internal database. This is used when the system database is
 * not yet available.
 */
public final class DialerBlockedNumberPhoneLookup implements PhoneLookup<DialerBlockedNumberInfo> {

  private final Context appContext;
  private final ListeningExecutorService executorService;

  @Inject
  DialerBlockedNumberPhoneLookup(
      @ApplicationContext Context appContext,
      @BackgroundExecutor ListeningExecutorService executorService) {
    this.appContext = appContext;
    this.executorService = executorService;
  }

  @Override
  public ListenableFuture<DialerBlockedNumberInfo> lookup(@NonNull Call call) {
    return executorService.submit(
        () -> {
          DialerPhoneNumberUtil dialerPhoneNumberUtil =
              new DialerPhoneNumberUtil(PhoneNumberUtil.getInstance());

          DialerPhoneNumber number =
              dialerPhoneNumberUtil.parse(
                  TelecomCallUtil.getNumber(call),
                  TelecomCallUtil.getCountryCode(appContext, call).orNull());
          return queryNumbers(ImmutableSet.of(number)).get(number);
        });
  }

  @Override
  public ListenableFuture<Boolean> isDirty(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    // Dirty state is recorded with PhoneLookupDataSource.markDirtyAndNotify(), which will force
    // rebuild with the CallLogFramework
    return Futures.immediateFuture(false);
  }

  @Override
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, DialerBlockedNumberInfo>>
      getMostRecentInfo(ImmutableMap<DialerPhoneNumber, DialerBlockedNumberInfo> existingInfoMap) {
    LogUtil.enterBlock("DialerBlockedNumberPhoneLookup.getMostRecentPhoneLookupInfo");
    return executorService.submit(() -> queryNumbers(existingInfoMap.keySet()));
  }

  @Override
  public void setSubMessage(
      PhoneLookupInfo.Builder phoneLookupInfo, DialerBlockedNumberInfo subMessage) {
    phoneLookupInfo.setDialerBlockedNumberInfo(subMessage);
  }

  @Override
  public DialerBlockedNumberInfo getSubMessage(PhoneLookupInfo phoneLookupInfo) {
    return phoneLookupInfo.getDialerBlockedNumberInfo();
  }

  @Override
  public ListenableFuture<Void> onSuccessfulBulkUpdate() {
    return Futures.immediateFuture(null);
  }

  @WorkerThread
  private ImmutableMap<DialerPhoneNumber, DialerBlockedNumberInfo> queryNumbers(
      ImmutableSet<DialerPhoneNumber> numbers) {
    Assert.isWorkerThread();
    PartitionedNumbers partitionedNumbers = new PartitionedNumbers(numbers);

    Set<DialerPhoneNumber> blockedNumbers = new ArraySet<>();

    Selection normalizedSelection =
        Selection.column(FilteredNumberColumns.NORMALIZED_NUMBER)
            .in(partitionedNumbers.validE164Numbers());
    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                FilteredNumber.CONTENT_URI,
                new String[] {FilteredNumberColumns.NORMALIZED_NUMBER, FilteredNumberColumns.TYPE},
                normalizedSelection.getSelection(),
                normalizedSelection.getSelectionArgs(),
                null)) {
      while (cursor != null && cursor.moveToNext()) {
        if (cursor.getInt(1) == FilteredNumberTypes.BLOCKED_NUMBER) {
          blockedNumbers.addAll(
              partitionedNumbers.dialerPhoneNumbersForValidE164(cursor.getString(0)));
        }
      }
    }

    Selection rawSelection =
        Selection.column(FilteredNumberColumns.NUMBER)
            .in(
                partitionedNumbers
                    .invalidNumbers()
                    .toArray(new String[partitionedNumbers.invalidNumbers().size()]));
    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                FilteredNumber.CONTENT_URI,
                new String[] {FilteredNumberColumns.NUMBER, FilteredNumberColumns.TYPE},
                rawSelection.getSelection(),
                rawSelection.getSelectionArgs(),
                null)) {
      while (cursor != null && cursor.moveToNext()) {
        if (cursor.getInt(1) == FilteredNumberTypes.BLOCKED_NUMBER) {
          blockedNumbers.addAll(
              partitionedNumbers.dialerPhoneNumbersForInvalid(cursor.getString(0)));
        }
      }
    }

    ImmutableMap.Builder<DialerPhoneNumber, DialerBlockedNumberInfo> result =
        ImmutableMap.builder();

    for (DialerPhoneNumber number : numbers) {
      result.put(
          number,
          DialerBlockedNumberInfo.newBuilder()
              .setBlockedState(
                  blockedNumbers.contains(number) ? BlockedState.BLOCKED : BlockedState.NOT_BLOCKED)
              .build());
    }

    return result.build();
  }

  @Override
  public void registerContentObservers(
      Context appContext, ContentObserverCallbacks contentObserverCallbacks) {
    appContext
        .getContentResolver()
        .registerContentObserver(
            FilteredNumber.CONTENT_URI,
            true, // FilteredNumberProvider notifies on the item
            new FilteredNumberObserver(appContext, contentObserverCallbacks));
  }

  private static class FilteredNumberObserver extends ContentObserver {
    private final Context appContext;
    private final ContentObserverCallbacks contentObserverCallbacks;

    FilteredNumberObserver(Context appContext, ContentObserverCallbacks contentObserverCallbacks) {
      super(null);
      this.appContext = appContext;
      this.contentObserverCallbacks = contentObserverCallbacks;
    }

    @MainThread
    @Override
    @SuppressWarnings("FutureReturnValueIgnored") // never throws.
    public void onChange(boolean selfChange, Uri uri) {
      Assert.isMainThread();
      LogUtil.enterBlock("DialerBlockedNumberPhoneLookup.FilteredNumberObserver.onChange");
      contentObserverCallbacks.markDirtyAndNotify(appContext);
    }
  }
}
