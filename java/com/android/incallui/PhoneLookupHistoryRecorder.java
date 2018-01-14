/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.incallui;

import android.content.ContentValues;
import android.content.Context;
import android.support.annotation.Nullable;
import android.telecom.Call;
import com.android.dialer.buildtype.BuildType;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutors;
import com.android.dialer.phonelookup.PhoneLookupComponent;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.database.contract.PhoneLookupHistoryContract.PhoneLookupHistory;
import com.android.dialer.telecom.TelecomCallUtil;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Fetches the current {@link PhoneLookupInfo} for the provided call and writes it to the
 * PhoneLookupHistory.
 */
final class PhoneLookupHistoryRecorder {

  /**
   * If the new UI is enabled, fetches the current {@link PhoneLookupInfo} for the provided call and
   * writes it to the PhoneLookupHistory. Otherwise does nothing.
   */
  static void recordPhoneLookupInfo(Context appContext, Call call) {
    if (!(BuildType.get() == BuildType.BUGFOOD || LogUtil.isDebugEnabled())) {
      return;
    }
    ListenableFuture<PhoneLookupInfo> future =
        PhoneLookupComponent.get(appContext).phoneLookup().lookup(call);
    Futures.addCallback(
        future,
        new FutureCallback<PhoneLookupInfo>() {
          @Override
          public void onSuccess(@Nullable PhoneLookupInfo result) {
            Assert.checkArgument(result != null);
            Optional<String> normalizedNumber =
                TelecomCallUtil.getNormalizedNumber(appContext, call);
            if (!normalizedNumber.isPresent()) {
              LogUtil.w("PhoneLookupHistoryRecorder.onSuccess", "couldn't get a number");
              return;
            }
            ContentValues contentValues = new ContentValues();
            contentValues.put(PhoneLookupHistory.PHONE_LOOKUP_INFO, result.toByteArray());
            contentValues.put(PhoneLookupHistory.LAST_MODIFIED, System.currentTimeMillis());
            appContext
                .getContentResolver()
                .update(
                    PhoneLookupHistory.contentUriForNumber(normalizedNumber.get()),
                    contentValues,
                    null,
                    null);
          }

          @Override
          public void onFailure(Throwable t) {
            // TODO(zachh): Consider how to best handle this; take measures to repair call log?
            LogUtil.w(
                "PhoneLookupHistoryRecorder.onFailure", "could not write PhoneLookupHistory", t);
          }
        },
        DialerExecutors.getLowPriorityThreadPool(appContext));
  }
}
