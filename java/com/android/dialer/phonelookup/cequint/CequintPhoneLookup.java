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

package com.android.dialer.phonelookup.cequint;

import android.content.Context;
import android.telecom.Call;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.CequintInfo;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import javax.inject.Inject;

/** PhoneLookup implementation for Cequint. */
public class CequintPhoneLookup implements PhoneLookup<CequintInfo> {

  @Inject
  CequintPhoneLookup() {}

  @Override
  public ListenableFuture<CequintInfo> lookup(Context appContext, Call call) {
    // TODO(a bug): Override the default implementation in the PhoneLookup interface
    // as a Cequint lookup requires info in the provided call.
    return Futures.immediateFuture(CequintInfo.getDefaultInstance());
  }

  @Override
  public ListenableFuture<CequintInfo> lookup(DialerPhoneNumber dialerPhoneNumber) {
    // TODO(a bug): Implement this method.
    return Futures.immediateFuture(CequintInfo.getDefaultInstance());
  }

  @Override
  public ListenableFuture<Boolean> isDirty(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    return Futures.immediateFuture(false);
  }

  @Override
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, CequintInfo>> getMostRecentInfo(
      ImmutableMap<DialerPhoneNumber, CequintInfo> existingInfoMap) {
    return Futures.immediateFuture(existingInfoMap);
  }

  @Override
  public void setSubMessage(PhoneLookupInfo.Builder destination, CequintInfo subMessage) {
    destination.setCequintInfo(subMessage);
  }

  @Override
  public CequintInfo getSubMessage(PhoneLookupInfo phoneLookupInfo) {
    return phoneLookupInfo.getCequintInfo();
  }

  @Override
  public ListenableFuture<Void> onSuccessfulBulkUpdate() {
    return Futures.immediateFuture(null);
  }

  @Override
  public void registerContentObservers() {
    // No content observers for Cequint info.
  }

  @Override
  public void unregisterContentObservers() {
    // No content observers for Cequint info.
  }

  @Override
  public ListenableFuture<Void> clearData() {
    return Futures.immediateFuture(null);
  }
}
