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

package com.android.dialer.phonelookup.cp2;

import android.content.Context;
import android.database.Cursor;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.common.concurrent.Annotations.LightweightExecutor;
import com.android.dialer.common.database.Selection;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.Cp2Info;
import com.android.dialer.phonelookup.database.contract.PhoneLookupHistoryContract.PhoneLookupHistory;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.function.Predicate;
import javax.inject.Inject;

/** Shared logic for handling missing permissions in CP2 lookups. */
final class MissingPermissionsOperations {

  private final Context appContext;
  private final ListeningExecutorService backgroundExecutor;
  private final ListeningExecutorService lightweightExecutor;

  @Inject
  MissingPermissionsOperations(
      @ApplicationContext Context appContext,
      @BackgroundExecutor ListeningExecutorService backgroundExecutor,
      @LightweightExecutor ListeningExecutorService lightweightExecutor) {
    this.appContext = appContext;
    this.backgroundExecutor = backgroundExecutor;
    this.lightweightExecutor = lightweightExecutor;
  }

  /**
   * Returns true if there is any CP2 data for the specified numbers in PhoneLookupHistory, because
   * that data needs to be cleared.
   *
   * <p>Note: This might be a little slow for users without contacts permissions, but we don't
   * expect this to often be the case. If necessary, a shared pref could be used to track the
   * permission state as an optimization.
   */
  ListenableFuture<Boolean> isDirtyForMissingPermissions(
      ImmutableSet<DialerPhoneNumber> phoneNumbers,
      Predicate<PhoneLookupInfo> phoneLookupInfoIsDirtyFn) {
    return backgroundExecutor.submit(
        () -> {
          // Note: This loses country info when number is not valid.
          String[] normalizedNumbers =
              phoneNumbers
                  .stream()
                  .map(DialerPhoneNumber::getNormalizedNumber)
                  .toArray(String[]::new);

          Selection selection =
              Selection.builder()
                  .and(Selection.column(PhoneLookupHistory.NORMALIZED_NUMBER).in(normalizedNumbers))
                  .build();

          try (Cursor cursor =
              appContext
                  .getContentResolver()
                  .query(
                      PhoneLookupHistory.CONTENT_URI,
                      new String[] {
                        PhoneLookupHistory.PHONE_LOOKUP_INFO,
                      },
                      selection.getSelection(),
                      selection.getSelectionArgs(),
                      null)) {

            if (cursor == null) {
              LogUtil.w("MissingPermissionsOperations.isDirtyForMissingPermissions", "null cursor");
              return false;
            }

            if (cursor.moveToFirst()) {
              int phoneLookupInfoColumn =
                  cursor.getColumnIndexOrThrow(PhoneLookupHistory.PHONE_LOOKUP_INFO);
              do {
                PhoneLookupInfo phoneLookupInfo;
                try {
                  phoneLookupInfo =
                      PhoneLookupInfo.parseFrom(cursor.getBlob(phoneLookupInfoColumn));
                } catch (InvalidProtocolBufferException e) {
                  throw new IllegalStateException(e);
                }
                if (phoneLookupInfoIsDirtyFn.test(phoneLookupInfo)) {
                  return true;
                }
              } while (cursor.moveToNext());
            }
          }
          return false;
        });
  }

  /** Clears all CP2 info because permissions are missing. */
  ListenableFuture<ImmutableMap<DialerPhoneNumber, Cp2Info>> getMostRecentInfoForMissingPermissions(
      ImmutableMap<DialerPhoneNumber, Cp2Info> existingInfoMap) {
    return lightweightExecutor.submit(
        () -> {
          ImmutableMap.Builder<DialerPhoneNumber, Cp2Info> clearedInfos = ImmutableMap.builder();
          for (DialerPhoneNumber number : existingInfoMap.keySet()) {
            clearedInfos.put(number, Cp2Info.getDefaultInstance());
          }
          return clearedInfos.build();
        });
  }
}
