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

package com.android.dialer.phonelookup.spam;

import android.content.SharedPreferences;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.common.Assert;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.common.concurrent.Annotations.LightweightExecutor;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.SpamInfo;
import com.android.dialer.spam.Spam;
import com.android.dialer.spam.status.SpamStatus;
import com.android.dialer.storage.Unencrypted;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.Map.Entry;
import javax.inject.Inject;

/** PhoneLookup implementation for Spam info. */
public final class SpamPhoneLookup implements PhoneLookup<SpamInfo> {

  @VisibleForTesting
  static final String PREF_LAST_TIMESTAMP_PROCESSED = "spamPhoneLookupLastTimestampProcessed";

  private final ListeningExecutorService lightweightExecutorService;
  private final ListeningExecutorService backgroundExecutorService;
  private final SharedPreferences sharedPreferences;
  private final Spam spam;

  @Nullable private Long currentLastTimestampProcessed;

  @Inject
  SpamPhoneLookup(
      @BackgroundExecutor ListeningExecutorService backgroundExecutorService,
      @LightweightExecutor ListeningExecutorService lightweightExecutorService,
      @Unencrypted SharedPreferences sharedPreferences,
      Spam spam) {
    this.backgroundExecutorService = backgroundExecutorService;
    this.lightweightExecutorService = lightweightExecutorService;
    this.sharedPreferences = sharedPreferences;
    this.spam = spam;
  }

  @Override
  public ListenableFuture<SpamInfo> lookup(DialerPhoneNumber dialerPhoneNumber) {
    return Futures.transform(
        spam.batchCheckSpamStatus(ImmutableSet.of(dialerPhoneNumber)),
        spamStatusMap ->
            SpamInfo.newBuilder()
                .setIsSpam(Assert.isNotNull(spamStatusMap.get(dialerPhoneNumber)).isSpam())
                .build(),
        lightweightExecutorService);
  }

  @Override
  public ListenableFuture<Boolean> isDirty(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    ListenableFuture<Long> lastTimestampProcessedFuture =
        backgroundExecutorService.submit(
            () -> sharedPreferences.getLong(PREF_LAST_TIMESTAMP_PROCESSED, 0L));

    return Futures.transformAsync(
        lastTimestampProcessedFuture, spam::dataUpdatedSince, lightweightExecutorService);
  }

  @Override
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, SpamInfo>> getMostRecentInfo(
      ImmutableMap<DialerPhoneNumber, SpamInfo> existingInfoMap) {
    currentLastTimestampProcessed = null;

    ListenableFuture<ImmutableMap<DialerPhoneNumber, SpamStatus>> spamStatusMapFuture =
        spam.batchCheckSpamStatus(existingInfoMap.keySet());

    return Futures.transform(
        spamStatusMapFuture,
        spamStatusMap -> {
          ImmutableMap.Builder<DialerPhoneNumber, SpamInfo> mostRecentSpamInfo =
              new ImmutableMap.Builder<>();

          for (Entry<DialerPhoneNumber, SpamStatus> dialerPhoneNumberAndSpamStatus :
              spamStatusMap.entrySet()) {
            DialerPhoneNumber dialerPhoneNumber = dialerPhoneNumberAndSpamStatus.getKey();
            SpamStatus spamStatus = dialerPhoneNumberAndSpamStatus.getValue();
            mostRecentSpamInfo.put(
                dialerPhoneNumber, SpamInfo.newBuilder().setIsSpam(spamStatus.isSpam()).build());

            Optional<Long> timestampMillis = spamStatus.getTimestampMillis();
            if (timestampMillis.isPresent()) {
              currentLastTimestampProcessed =
                  currentLastTimestampProcessed == null
                      ? timestampMillis.get()
                      : Math.max(timestampMillis.get(), currentLastTimestampProcessed);
            }
          }

          // If currentLastTimestampProcessed is null, it means none of the numbers in
          // existingInfoMap has spam status in the underlying data source.
          // We should set currentLastTimestampProcessed to the current timestamp to avoid
          // triggering the bulk update flow repeatedly.
          if (currentLastTimestampProcessed == null) {
            currentLastTimestampProcessed = System.currentTimeMillis();
          }

          return mostRecentSpamInfo.build();
        },
        lightweightExecutorService);
  }

  @Override
  public SpamInfo getSubMessage(PhoneLookupInfo phoneLookupInfo) {
    return phoneLookupInfo.getSpamInfo();
  }

  @Override
  public void setSubMessage(PhoneLookupInfo.Builder destination, SpamInfo subMessage) {
    destination.setSpamInfo(subMessage);
  }

  @Override
  public ListenableFuture<Void> onSuccessfulBulkUpdate() {
    return backgroundExecutorService.submit(
        () -> {
          sharedPreferences
              .edit()
              .putLong(
                  PREF_LAST_TIMESTAMP_PROCESSED, Assert.isNotNull(currentLastTimestampProcessed))
              .apply();
          return null;
        });
  }

  @Override
  public void registerContentObservers() {
    // No content observer can be registered as Spam is not based on a content provider.
    // Each Spam implementation should be responsible for notifying any data changes.
  }

  @Override
  public void unregisterContentObservers() {}

  @Override
  public ListenableFuture<Void> clearData() {
    return backgroundExecutorService.submit(
        () -> {
          sharedPreferences.edit().remove(PREF_LAST_TIMESTAMP_PROCESSED).apply();
          return null;
        });
  }

  @Override
  public String getLoggingName() {
    return "SpamPhoneLookup";
  }
}
