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

package com.android.dialer.phonelookup.cnap;

import android.content.Context;
import android.database.Cursor;
import android.telecom.Call;
import android.text.TextUtils;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.common.database.Selection;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.CnapInfo;
import com.android.dialer.phonelookup.database.contract.PhoneLookupHistoryContract.PhoneLookupHistory;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.protobuf.InvalidProtocolBufferException;
import javax.inject.Inject;

/** PhoneLookup implementation for CNAP info. */
public final class CnapPhoneLookup implements PhoneLookup<CnapInfo> {

  private final Context appContext;
  private final ListeningExecutorService backgroundExecutorService;

  @Inject
  CnapPhoneLookup(
      @ApplicationContext Context appContext,
      @BackgroundExecutor ListeningExecutorService backgroundExecutorService) {
    this.appContext = appContext;
    this.backgroundExecutorService = backgroundExecutorService;
  }

  /**
   * Override the default implementation in {@link PhoneLookup#lookup(Context, Call)} as CNAP info
   * is in the provided {@link Call}.
   */
  @Override
  public ListenableFuture<CnapInfo> lookup(Context appContext, Call call) {
    String callerDisplayName = call.getDetails().getCallerDisplayName();
    return Futures.immediateFuture(
        TextUtils.isEmpty(callerDisplayName)
            ? CnapInfo.getDefaultInstance()
            : CnapInfo.newBuilder().setName(callerDisplayName).build());
  }

  /**
   * CNAP info cannot be retrieved when all we have is a number. The best we can do is returning the
   * existing info in {@link PhoneLookupHistory}.
   */
  @Override
  public ListenableFuture<CnapInfo> lookup(DialerPhoneNumber dialerPhoneNumber) {
    return backgroundExecutorService.submit(
        () -> {
          Selection selection =
              Selection.builder()
                  .and(
                      Selection.column(PhoneLookupHistory.NORMALIZED_NUMBER)
                          .is("=", dialerPhoneNumber.getNormalizedNumber()))
                  .build();

          try (Cursor cursor =
              appContext
                  .getContentResolver()
                  .query(
                      PhoneLookupHistory.CONTENT_URI,
                      new String[] {PhoneLookupHistory.PHONE_LOOKUP_INFO},
                      selection.getSelection(),
                      selection.getSelectionArgs(),
                      /* sortOrder = */ null)) {
            if (cursor == null) {
              LogUtil.e("CnapPhoneLookup.lookup", "null cursor");
              return CnapInfo.getDefaultInstance();
            }

            if (!cursor.moveToFirst()) {
              LogUtil.i("CnapPhoneLookup.lookup", "empty cursor");
              return CnapInfo.getDefaultInstance();
            }

            // At ths point, we expect only one row in the cursor as
            // PhoneLookupHistory.NORMALIZED_NUMBER is the primary key of table PhoneLookupHistory.
            Assert.checkState(cursor.getCount() == 1);

            int phoneLookupInfoColumn =
                cursor.getColumnIndexOrThrow(PhoneLookupHistory.PHONE_LOOKUP_INFO);
            PhoneLookupInfo phoneLookupInfo;
            try {
              phoneLookupInfo = PhoneLookupInfo.parseFrom(cursor.getBlob(phoneLookupInfoColumn));
            } catch (InvalidProtocolBufferException e) {
              throw new IllegalStateException(e);
            }

            return phoneLookupInfo.getCnapInfo();
          }
        });
  }

  @Override
  public ListenableFuture<Boolean> isDirty(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    return Futures.immediateFuture(false);
  }

  @Override
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, CnapInfo>> getMostRecentInfo(
      ImmutableMap<DialerPhoneNumber, CnapInfo> existingInfoMap) {
    return Futures.immediateFuture(existingInfoMap);
  }

  @Override
  public void setSubMessage(PhoneLookupInfo.Builder destination, CnapInfo subMessage) {
    destination.setCnapInfo(subMessage);
  }

  @Override
  public CnapInfo getSubMessage(PhoneLookupInfo phoneLookupInfo) {
    return phoneLookupInfo.getCnapInfo();
  }

  @Override
  public ListenableFuture<Void> onSuccessfulBulkUpdate() {
    return Futures.immediateFuture(null);
  }

  @Override
  public void registerContentObservers() {
    // No content observers for CNAP info.
  }

  @Override
  public void unregisterContentObservers() {
    // No content observers for CNAP info.
  }

  @Override
  public ListenableFuture<Void> clearData() {
    return Futures.immediateFuture(null);
  }

  @Override
  public String getLoggingName() {
    return "CnapPhoneLookup";
  }
}
