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

package com.android.dialer.calllog.datasources.phonelookup;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.support.annotation.MainThread;
import android.support.annotation.WorkerThread;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.calllog.datasources.CallLogMutations;
import com.android.dialer.common.LogUtil;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.storage.Unencrypted;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;

/**
 * Responsible for maintaining the columns in the annotated call log which are derived from phone
 * numbers.
 */
public final class PhoneLookupDataSource implements CallLogDataSource {
  private static final String PREF_LAST_TIMESTAMP_PROCESSED = "phoneLookupLastTimestampProcessed";

  private final PhoneLookup phoneLookup;
  private final SharedPreferences sharedPreferences;

  @Inject
  PhoneLookupDataSource(PhoneLookup phoneLookup, @Unencrypted SharedPreferences sharedPreferences) {
    this.phoneLookup = phoneLookup;
    this.sharedPreferences = sharedPreferences;
  }

  @WorkerThread
  @Override
  public boolean isDirty(Context appContext) {
    ImmutableSet<DialerPhoneNumber> uniqueDialerPhoneNumbers =
        queryDistinctDialerPhoneNumbersFromAnnotatedCallLog(appContext);

    long lastTimestampProcessedSharedPrefValue =
        sharedPreferences.getLong(PREF_LAST_TIMESTAMP_PROCESSED, 0L);
    try {
      // TODO(zachh): Would be good to rework call log architecture to properly use futures.
      // TODO(zachh): Consider how individual lookups should behave wrt timeouts/exceptions and
      // handle appropriately here.
      return phoneLookup
          .isDirty(uniqueDialerPhoneNumbers, lastTimestampProcessedSharedPrefValue)
          .get();
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException(e);
    }
  }

  @WorkerThread
  @Override
  public void fill(Context appContext, CallLogMutations mutations) {
    // TODO(zachh): Implementation.
  }

  @WorkerThread
  @Override
  public void onSuccessfulFill(Context appContext) {
    // TODO(zachh): Implementation.
  }

  @WorkerThread
  @Override
  public ContentValues coalesce(List<ContentValues> individualRowsSortedByTimestampDesc) {
    // TODO(zachh): Implementation.
    return new ContentValues();
  }

  @MainThread
  @Override
  public void registerContentObservers(
      Context appContext, ContentObserverCallbacks contentObserverCallbacks) {
    // No content observers required for this data source.
  }

  private static ImmutableSet<DialerPhoneNumber>
      queryDistinctDialerPhoneNumbersFromAnnotatedCallLog(Context appContext) {
    ImmutableSet.Builder<DialerPhoneNumber> numbers = ImmutableSet.builder();

    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                AnnotatedCallLog.DISTINCT_NUMBERS_CONTENT_URI,
                new String[] {AnnotatedCallLog.NUMBER},
                null,
                null,
                null)) {

      if (cursor == null) {
        LogUtil.e(
            "PhoneLookupDataSource.queryDistinctDialerPhoneNumbersFromAnnotatedCallLog",
            "null cursor");
        return numbers.build();
      }

      if (cursor.moveToFirst()) {
        int numberColumn = cursor.getColumnIndexOrThrow(AnnotatedCallLog.NUMBER);
        do {
          byte[] blob = cursor.getBlob(numberColumn);
          if (blob == null) {
            // Not all [incoming] calls have associated phone numbers.
            continue;
          }
          try {
            numbers.add(DialerPhoneNumber.parseFrom(blob));
          } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(e);
          }
        } while (cursor.moveToNext());
      }
    }
    return numbers.build();
  }
}
