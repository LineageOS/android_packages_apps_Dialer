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
 * limitations under the License.
 */

package com.android.dialer.calllog.ui;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.support.annotation.MainThread;
import android.support.annotation.VisibleForTesting;
import android.util.ArrayMap;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.calllogutils.NumberAttributesBuilder;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.common.concurrent.Annotations.Ui;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.composite.CompositePhoneLookup;
import com.android.dialer.phonelookup.database.contract.PhoneLookupHistoryContract;
import com.android.dialer.phonelookup.database.contract.PhoneLookupHistoryContract.PhoneLookupHistory;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/**
 * Does work necessary to update a {@link CoalescedRow} when it is requested to be displayed.
 *
 * <p>In most cases this is a no-op as most AnnotatedCallLog rows can be displayed immediately
 * as-is. However, there are certain times that a row from the AnnotatedCallLog cannot be displayed
 * without further work being performed.
 *
 * <p>For example, when there are many invalid numbers in the call log, we cannot efficiently update
 * the CP2 information for all of them at once, and so information for those rows must be retrieved
 * at display time.
 *
 * <p>This class also updates {@link PhoneLookupHistory} with the results that it fetches.
 */
public final class RealtimeRowProcessor {

  /*
   * The time to wait between writing batches of records to PhoneLookupHistory.
   */
  @VisibleForTesting static final long BATCH_WAIT_MILLIS = TimeUnit.SECONDS.toMillis(3);

  private final Context appContext;
  private final CompositePhoneLookup compositePhoneLookup;
  private final ListeningExecutorService uiExecutor;
  private final ListeningExecutorService backgroundExecutor;

  private final Map<DialerPhoneNumber, PhoneLookupInfo> cache = new ArrayMap<>();

  private final Map<DialerPhoneNumber, PhoneLookupInfo> queuedPhoneLookupHistoryWrites =
      new LinkedHashMap<>(); // Keep the order so the most recent looked up value always wins
  private final Runnable writePhoneLookupHistoryRunnable = this::writePhoneLookupHistory;

  @Inject
  RealtimeRowProcessor(
      @ApplicationContext Context appContext,
      @Ui ListeningExecutorService uiExecutor,
      @BackgroundExecutor ListeningExecutorService backgroundExecutor,
      CompositePhoneLookup compositePhoneLookup) {
    this.appContext = appContext;
    this.uiExecutor = uiExecutor;
    this.backgroundExecutor = backgroundExecutor;
    this.compositePhoneLookup = compositePhoneLookup;
  }

  /**
   * Converts a {@link CoalescedRow} to a future which is the result of performing additional work
   * on the row. May simply return the original row if no modifications were necessary.
   */
  @MainThread
  ListenableFuture<CoalescedRow> applyRealtimeProcessing(final CoalescedRow row) {
    // Cp2DefaultDirectoryPhoneLookup can not always efficiently process all rows.
    if (!row.getNumberAttributes().getIsCp2InfoIncomplete()) {
      return Futures.immediateFuture(row);
    }

    PhoneLookupInfo cachedPhoneLookupInfo = cache.get(row.getNumber());
    if (cachedPhoneLookupInfo != null) {
      return Futures.immediateFuture(applyPhoneLookupInfoToRow(cachedPhoneLookupInfo, row));
    }

    ListenableFuture<PhoneLookupInfo> phoneLookupInfoFuture =
        compositePhoneLookup.lookup(row.getNumber());
    return Futures.transform(
        phoneLookupInfoFuture,
        phoneLookupInfo -> {
          queuePhoneLookupHistoryWrite(row.getNumber(), phoneLookupInfo);
          cache.put(row.getNumber(), phoneLookupInfo);
          return applyPhoneLookupInfoToRow(phoneLookupInfo, row);
        },
        uiExecutor /* ensures the cache is updated on a single thread */);
  }

  /** Clears the internal cache. */
  @MainThread
  public void clearCache() {
    Assert.isMainThread();
    cache.clear();
  }

  @MainThread
  private void queuePhoneLookupHistoryWrite(
      DialerPhoneNumber dialerPhoneNumber, PhoneLookupInfo phoneLookupInfo) {
    Assert.isMainThread();
    queuedPhoneLookupHistoryWrites.put(dialerPhoneNumber, phoneLookupInfo);
    ThreadUtil.getUiThreadHandler().removeCallbacks(writePhoneLookupHistoryRunnable);
    ThreadUtil.getUiThreadHandler().postDelayed(writePhoneLookupHistoryRunnable, BATCH_WAIT_MILLIS);
  }

  @MainThread
  private void writePhoneLookupHistory() {
    Assert.isMainThread();

    // Copy the batch to a new collection that be safely processed on a background thread.
    ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> currentBatch =
        ImmutableMap.copyOf(queuedPhoneLookupHistoryWrites);

    // Clear the queue, handing responsibility for its items to the background task.
    queuedPhoneLookupHistoryWrites.clear();

    // Returns the number of rows updated.
    ListenableFuture<Integer> applyBatchFuture =
        backgroundExecutor.submit(
            () -> {
              ArrayList<ContentProviderOperation> operations = new ArrayList<>();
              long currentTimestamp = System.currentTimeMillis();
              for (Entry<DialerPhoneNumber, PhoneLookupInfo> entry : currentBatch.entrySet()) {
                DialerPhoneNumber dialerPhoneNumber = entry.getKey();
                PhoneLookupInfo phoneLookupInfo = entry.getValue();

                // Note: Multiple DialerPhoneNumbers can map to the same normalized number but we
                // just write them all and the value for the last one will arbitrarily win.
                // Note: This loses country info when number is not valid.
                String normalizedNumber = dialerPhoneNumber.getNormalizedNumber();

                ContentValues contentValues = new ContentValues();
                contentValues.put(
                    PhoneLookupHistory.PHONE_LOOKUP_INFO, phoneLookupInfo.toByteArray());
                contentValues.put(PhoneLookupHistory.LAST_MODIFIED, currentTimestamp);
                operations.add(
                    ContentProviderOperation.newUpdate(
                            PhoneLookupHistory.contentUriForNumber(normalizedNumber))
                        .withValues(contentValues)
                        .build());
              }
              return Assert.isNotNull(
                      appContext
                          .getContentResolver()
                          .applyBatch(PhoneLookupHistoryContract.AUTHORITY, operations))
                  .length;
            });

    Futures.addCallback(
        applyBatchFuture,
        new FutureCallback<Integer>() {
          @Override
          public void onSuccess(Integer rowsAffected) {
            LogUtil.i(
                "RealtimeRowProcessor.onSuccess",
                "wrote %d rows to PhoneLookupHistory",
                rowsAffected);
          }

          @Override
          public void onFailure(Throwable throwable) {
            throw new RuntimeException(throwable);
          }
        },
        uiExecutor);
  }

  private CoalescedRow applyPhoneLookupInfoToRow(
      PhoneLookupInfo phoneLookupInfo, CoalescedRow row) {
    // Force the "cp2_info_incomplete" value to the original value so that it is not used when
    // comparing the original row to the updated row.
    // TODO(linyuh): Improve the comparison instead.
    return row.toBuilder()
        .setNumberAttributes(
            NumberAttributesBuilder.fromPhoneLookupInfo(phoneLookupInfo)
                .setIsCp2InfoIncomplete(row.getNumberAttributes().getIsCp2InfoIncomplete())
                .build())
        .build();
  }
}
