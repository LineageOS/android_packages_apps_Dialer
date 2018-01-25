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

import android.content.Context;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.SpamInfo;
import com.android.dialer.spam.Spam;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import javax.inject.Inject;

/** PhoneLookup implementation for Spam info. */
public final class SpamPhoneLookup implements PhoneLookup<SpamInfo> {

  private final Spam spam;

  @Inject
  SpamPhoneLookup(Spam spam) {
    this.spam = spam;
  }

  @Override
  public ListenableFuture<SpamInfo> lookup(DialerPhoneNumber dialerPhoneNumber) {
    // TODO(a bug): Use Spam to look up spam info.
    return Futures.immediateFuture(SpamInfo.getDefaultInstance());
  }

  @Override
  public ListenableFuture<Boolean> isDirty(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    // TODO(a bug): Use Spam to check if its underlying data have been updated.
    return Futures.immediateFuture(false);
  }

  @Override
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, SpamInfo>> getMostRecentInfo(
      ImmutableMap<DialerPhoneNumber, SpamInfo> existingInfoMap) {
    // TODO(a bug): Use Spam to retrieve the most recent spam info.
    return Futures.immediateFuture(existingInfoMap);
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
    // TODO(a bug): Properly implement this method.
    return Futures.immediateFuture(null);
  }

  @Override
  public void registerContentObservers(
      Context appContext, ContentObserverCallbacks contentObserverCallbacks) {
    // No content observer needed for spam info
  }
}
