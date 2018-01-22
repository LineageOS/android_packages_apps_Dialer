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

import android.content.Context;
import android.support.annotation.MainThread;
import android.util.ArrayMap;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.NumberAttributes;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.common.Assert;
import com.android.dialer.common.concurrent.Annotations.Ui;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.consolidator.PhoneLookupInfoConsolidator;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.Map;
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
 */
public final class RealtimeRowProcessor {

  private final Context appContext;
  private final PhoneLookup<PhoneLookupInfo> phoneLookup;
  private final ListeningExecutorService uiExecutor;

  private final Map<DialerPhoneNumber, PhoneLookupInfo> cache = new ArrayMap<>();

  @Inject
  RealtimeRowProcessor(
      @ApplicationContext Context appContext,
      @Ui ListeningExecutorService uiExecutor,
      PhoneLookup<PhoneLookupInfo> phoneLookup) {
    this.appContext = appContext;
    this.uiExecutor = uiExecutor;
    this.phoneLookup = phoneLookup;
  }

  /**
   * Converts a {@link CoalescedRow} to a future which is the result of performing additional work
   * on the row. May simply return the original row if no modifications were necessary.
   */
  @MainThread
  ListenableFuture<CoalescedRow> applyRealtimeProcessing(final CoalescedRow row) {
    // Cp2LocalPhoneLookup can not always efficiently process all rows.
    if (!row.numberAttributes().getIsCp2InfoIncomplete()) {
      return Futures.immediateFuture(row);
    }

    PhoneLookupInfo cachedPhoneLookupInfo = cache.get(row.number());
    if (cachedPhoneLookupInfo != null) {
      return Futures.immediateFuture(applyPhoneLookupInfoToRow(cachedPhoneLookupInfo, row));
    }

    ListenableFuture<PhoneLookupInfo> phoneLookupInfoFuture = phoneLookup.lookup(row.number());
    return Futures.transform(
        phoneLookupInfoFuture,
        phoneLookupInfo -> {
          cache.put(row.number(), phoneLookupInfo);
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

  private CoalescedRow applyPhoneLookupInfoToRow(
      PhoneLookupInfo phoneLookupInfo, CoalescedRow row) {
    PhoneLookupInfoConsolidator phoneLookupInfoConsolidator =
        new PhoneLookupInfoConsolidator(appContext, phoneLookupInfo);
    return row.toBuilder()
        .setNumberAttributes(
            // TODO(zachh): Put this in a common location.
            NumberAttributes.newBuilder()
                .setName(phoneLookupInfoConsolidator.getName())
                .setPhotoUri(phoneLookupInfoConsolidator.getPhotoUri())
                .setPhotoId(phoneLookupInfoConsolidator.getPhotoId())
                .setLookupUri(phoneLookupInfoConsolidator.getLookupUri())
                .setNumberTypeLabel(phoneLookupInfoConsolidator.getNumberLabel())
                .setIsBusiness(phoneLookupInfoConsolidator.isBusiness())
                .setIsVoicemail(phoneLookupInfoConsolidator.isVoicemail())
                .setCanReportAsInvalidNumber(phoneLookupInfoConsolidator.canReportAsInvalidNumber())
                .build())
        .build();
  }
}
