/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.spam;

import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.logging.ContactLookupResult;
import com.android.dialer.logging.ContactSource;
import com.android.dialer.logging.ReportingLocation;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import javax.inject.Inject;

/** Default implementation of Spam. */
public class SpamStub implements Spam {

  private final ListeningExecutorService backgroundExecutorService;

  @Inject
  public SpamStub(@BackgroundExecutor ListeningExecutorService backgroundExecutorService) {
    this.backgroundExecutorService = backgroundExecutorService;
  }

  @Override
  public boolean isSpamEnabled() {
    return false;
  }

  @Override
  public boolean isSpamNotificationEnabled() {
    return false;
  }

  @Override
  public boolean isDialogEnabledForSpamNotification() {
    return false;
  }

  @Override
  public boolean isDialogReportSpamCheckedByDefault() {
    return false;
  }

  @Override
  public int percentOfSpamNotificationsToShow() {
    return 0;
  }

  @Override
  public int percentOfNonSpamNotificationsToShow() {
    return 0;
  }

  @Override
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, SpamStatus>> batchCheckSpamStatus(
      ImmutableSet<DialerPhoneNumber> dialerPhoneNumbers) {
    return backgroundExecutorService.submit(
        () -> {
          ImmutableMap.Builder<DialerPhoneNumber, SpamStatus> resultBuilder =
              new ImmutableMap.Builder<>();
          for (DialerPhoneNumber dialerPhoneNumber : dialerPhoneNumbers) {
            resultBuilder.put(
                dialerPhoneNumber,
                new SpamStatus() {
                  @Override
                  public boolean isSpam() {
                    return false;
                  }

                  @Override
                  public Optional<Long> getTimestampMillis() {
                    return Optional.absent();
                  }
                });
          }
          return resultBuilder.build();
        });
  }

  @Override
  public void checkSpamStatus(String number, String countryIso, Listener listener) {
    listener.onComplete(false);
  }

  @Override
  public void checkUserMarkedNonSpamStatus(String number, String countryIso, Listener listener) {
    listener.onComplete(false);
  }

  @Override
  public void checkUserMarkedSpamStatus(String number, String countryIso, Listener listener) {
    listener.onComplete(false);
  }

  @Override
  public void checkGlobalSpamListStatus(String number, String countryIso, Listener listener) {
    listener.onComplete(false);
  }

  @Override
  public boolean checkSpamStatusSynchronous(String number, String countryIso) {
    return false;
  }

  @Override
  public ListenableFuture<Boolean> dataUpdatedSince(long timestampMillis) {
    return Futures.immediateFuture(false);
  }

  @Override
  public void reportSpamFromAfterCallNotification(
      String number,
      String countryIso,
      int callType,
      ReportingLocation.Type from,
      ContactLookupResult.Type contactLookupResultType) {}

  @Override
  public void reportSpamFromCallHistory(
      String number,
      String countryIso,
      int callType,
      ReportingLocation.Type from,
      ContactSource.Type contactSourceType) {}

  @Override
  public void reportNotSpamFromAfterCallNotification(
      String number,
      String countryIso,
      int callType,
      ReportingLocation.Type from,
      ContactLookupResult.Type contactLookupResultType) {}

  @Override
  public void reportNotSpamFromCallHistory(
      String number,
      String countryIso,
      int callType,
      ReportingLocation.Type from,
      ContactSource.Type contactSourceType) {}
}
