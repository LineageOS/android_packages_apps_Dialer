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

import android.support.annotation.MainThread;
import android.util.ArrayMap;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.NumberAttributes;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.common.Assert;
import com.android.dialer.common.concurrent.Annotations.Ui;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.Cp2Info;
import com.android.dialer.phonelookup.cp2.Cp2LocalPhoneLookup;
import com.android.dialer.phonelookup.selector.PhoneLookupSelector;
import com.google.common.base.Optional;
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

  private final ListeningExecutorService uiExecutor;
  private final Cp2LocalPhoneLookup cp2LocalPhoneLookup;
  private final PhoneLookupSelector phoneLookupSelector;

  private final Map<DialerPhoneNumber, Cp2Info> cache = new ArrayMap<>();

  @Inject
  RealtimeRowProcessor(
      @Ui ListeningExecutorService uiExecutor,
      Cp2LocalPhoneLookup cp2LocalPhoneLookup,
      PhoneLookupSelector phoneLookupSelector) {
    this.uiExecutor = uiExecutor;
    this.cp2LocalPhoneLookup = cp2LocalPhoneLookup;
    this.phoneLookupSelector = phoneLookupSelector;
  }

  /**
   * Converts a {@link CoalescedRow} to a future which is the result of performing additional work
   * on the row. Returns {@link Optional#absent()} if no modifications were necessary.
   */
  @MainThread
  ListenableFuture<Optional<CoalescedRow>> applyRealtimeProcessing(final CoalescedRow row) {
    // Cp2LocalPhoneLookup can not always efficiently process all rows.
    if (!row.numberAttributes().getIsCp2InfoIncomplete()) {
      return Futures.immediateFuture(Optional.absent());
    }

    Cp2Info cachedCp2Info = cache.get(row.number());
    if (cachedCp2Info != null) {
      if (cachedCp2Info.equals(Cp2Info.getDefaultInstance())) {
        return Futures.immediateFuture(Optional.absent());
      }
      return Futures.immediateFuture(Optional.of(applyCp2LocalInfoToRow(cachedCp2Info, row)));
    }

    ListenableFuture<Cp2Info> cp2InfoFuture = cp2LocalPhoneLookup.lookupByNumber(row.number());
    return Futures.transform(
        cp2InfoFuture,
        cp2Info -> {
          cache.put(row.number(), cp2Info);
          if (!cp2Info.equals(Cp2Info.getDefaultInstance())) {
            return Optional.of(applyCp2LocalInfoToRow(cp2Info, row));
          }
          return Optional.absent();
        },
        uiExecutor /* ensures the cache is updated on a single thread */);
  }

  /** Clears the internal cache. */
  @MainThread
  public void clearCache() {
    Assert.isMainThread();
    cache.clear();
  }

  private CoalescedRow applyCp2LocalInfoToRow(Cp2Info cp2Info, CoalescedRow row) {
    PhoneLookupInfo phoneLookupInfo = PhoneLookupInfo.newBuilder().setCp2LocalInfo(cp2Info).build();
    // It is safe to overwrite any existing data because CP2 always has highest priority.
    return row.toBuilder()
        .setNumberAttributes(
            NumberAttributes.newBuilder()
                .setName(phoneLookupSelector.selectName(phoneLookupInfo))
                .setPhotoUri(phoneLookupSelector.selectPhotoUri(phoneLookupInfo))
                .setPhotoId(phoneLookupSelector.selectPhotoId(phoneLookupInfo))
                .setLookupUri(phoneLookupSelector.selectLookupUri(phoneLookupInfo))
                .setNumberTypeLabel(phoneLookupSelector.selectNumberLabel(phoneLookupInfo))
                .setIsBusiness(phoneLookupSelector.selectIsBusiness(phoneLookupInfo))
                .setIsVoicemail(phoneLookupSelector.selectIsVoicemail(phoneLookupInfo))
                .setCanReportAsInvalidNumber(
                    phoneLookupSelector.canReportAsInvalidNumber(phoneLookupInfo))
                .build())
        .build();
  }
}
