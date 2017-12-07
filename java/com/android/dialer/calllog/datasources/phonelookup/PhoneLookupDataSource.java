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
import android.database.Cursor;
import android.support.annotation.MainThread;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.calllog.datasources.CallLogMutations;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupSelector;
import com.android.dialer.phonelookup.database.contract.PhoneLookupHistoryContract.PhoneLookupHistory;
import com.android.dialer.phonenumberproto.DialerPhoneNumberUtil;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;

/**
 * Responsible for maintaining the columns in the annotated call log which are derived from phone
 * numbers.
 */
public final class PhoneLookupDataSource implements CallLogDataSource {

  private final PhoneLookup phoneLookup;
  private final ListeningExecutorService backgroundExecutorService;

  @Inject
  PhoneLookupDataSource(
      PhoneLookup phoneLookup,
      @BackgroundExecutor ListeningExecutorService backgroundExecutorService) {
    this.phoneLookup = phoneLookup;
    this.backgroundExecutorService = backgroundExecutorService;
  }

  @Override
  public ListenableFuture<Boolean> isDirty(Context appContext) {
    return backgroundExecutorService.submit(() -> isDirtyInternal(appContext));
  }

  @Override
  public ListenableFuture<Void> fill(Context appContext, CallLogMutations mutations) {
    return backgroundExecutorService.submit(() -> fillInternal(appContext, mutations));
  }

  @Override
  public ListenableFuture<Void> onSuccessfulFill(Context appContext) {
    return backgroundExecutorService.submit(this::onSuccessfulFillInternal);
  }

  @WorkerThread
  private boolean isDirtyInternal(Context appContext) {
    ImmutableSet<DialerPhoneNumber> uniqueDialerPhoneNumbers =
        queryDistinctDialerPhoneNumbersFromAnnotatedCallLog(appContext);

    try {
      // TODO(zachh): Would be good to rework call log architecture to properly use futures.
      // TODO(zachh): Consider how individual lookups should behave wrt timeouts/exceptions and
      // handle appropriately here.
      return phoneLookup.isDirty(uniqueDialerPhoneNumbers).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method uses the following algorithm:
   *
   * <ul>
   *   <li>Selects the distinct DialerPhoneNumbers from the AnnotatedCallLog
   *   <li>Uses them to fetch the current information from PhoneLookupHistory, in order to construct
   *       a map from DialerPhoneNumber to PhoneLookupInfo
   *       <ul>
   *         <li>If no PhoneLookupInfo is found (e.g. app data was cleared?) an empty value is used.
   *       </ul>
   *   <li>Looks through the provided set of mutations
   *   <li>For inserts, uses the contents of PhoneLookupHistory to populate the fields of the
   *       provided mutations. (Note that at this point, data may not be fully up-to-date, but the
   *       next steps will take care of that.)
   *   <li>Uses all of the numbers from AnnotatedCallLog to invoke CompositePhoneLookup:bulkUpdate
   *   <li>Looks through the results of bulkUpdate
   *       <ul>
   *         <li>For each number, checks if the original PhoneLookupInfo differs from the new one
   *         <li>If so, it applies the update to the mutations and (in onSuccessfulFill) writes the
   *             new value back to the PhoneLookupHistory.
   *       </ul>
   * </ul>
   */
  @WorkerThread
  private Void fillInternal(Context appContext, CallLogMutations mutations) {
    Map<DialerPhoneNumber, Set<Long>> annotatedCallLogIdsByNumber =
        queryIdAndNumberFromAnnotatedCallLog(appContext);
    ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> originalPhoneLookupInfosByNumber =
        queryPhoneLookupHistoryForNumbers(appContext, annotatedCallLogIdsByNumber.keySet());
    ImmutableMap.Builder<Long, PhoneLookupInfo> originalPhoneLookupHistoryDataByAnnotatedCallLogId =
        ImmutableMap.builder();
    for (Entry<DialerPhoneNumber, PhoneLookupInfo> entry :
        originalPhoneLookupInfosByNumber.entrySet()) {
      DialerPhoneNumber dialerPhoneNumber = entry.getKey();
      PhoneLookupInfo phoneLookupInfo = entry.getValue();
      for (Long id : annotatedCallLogIdsByNumber.get(dialerPhoneNumber)) {
        originalPhoneLookupHistoryDataByAnnotatedCallLogId.put(id, phoneLookupInfo);
      }
    }
    populateInserts(originalPhoneLookupHistoryDataByAnnotatedCallLogId.build(), mutations);

    ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> updatedInfoMap;
    try {
      updatedInfoMap = phoneLookup.bulkUpdate(originalPhoneLookupInfosByNumber).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException(e);
    }
    ImmutableMap.Builder<Long, PhoneLookupInfo> rowsToUpdate = ImmutableMap.builder();
    for (Entry<DialerPhoneNumber, PhoneLookupInfo> entry : updatedInfoMap.entrySet()) {
      DialerPhoneNumber dialerPhoneNumber = entry.getKey();
      PhoneLookupInfo upToDateInfo = entry.getValue();
      if (!originalPhoneLookupInfosByNumber.get(dialerPhoneNumber).equals(upToDateInfo)) {
        for (Long id : annotatedCallLogIdsByNumber.get(dialerPhoneNumber)) {
          rowsToUpdate.put(id, upToDateInfo);
        }
      }
    }
    updateMutations(rowsToUpdate.build(), mutations);
    return null;
  }

  @WorkerThread
  private Void onSuccessfulFillInternal() {
    // TODO(zachh): Update PhoneLookupHistory.
    return null;
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

  private Map<DialerPhoneNumber, Set<Long>> queryIdAndNumberFromAnnotatedCallLog(
      Context appContext) {
    Map<DialerPhoneNumber, Set<Long>> idsByNumber = new ArrayMap<>();

    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                AnnotatedCallLog.CONTENT_URI,
                new String[] {AnnotatedCallLog._ID, AnnotatedCallLog.NUMBER},
                null,
                null,
                null)) {

      if (cursor == null) {
        LogUtil.e("PhoneLookupDataSource.queryIdAndNumberFromAnnotatedCallLog", "null cursor");
        return ImmutableMap.of();
      }

      if (cursor.moveToFirst()) {
        int idColumn = cursor.getColumnIndexOrThrow(AnnotatedCallLog._ID);
        int numberColumn = cursor.getColumnIndexOrThrow(AnnotatedCallLog.NUMBER);
        do {
          long id = cursor.getLong(idColumn);
          byte[] blob = cursor.getBlob(numberColumn);
          if (blob == null) {
            // Not all [incoming] calls have associated phone numbers.
            continue;
          }
          DialerPhoneNumber dialerPhoneNumber;
          try {
            dialerPhoneNumber = DialerPhoneNumber.parseFrom(blob);
          } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(e);
          }
          Set<Long> ids = idsByNumber.get(dialerPhoneNumber);
          if (ids == null) {
            ids = new ArraySet<>();
            idsByNumber.put(dialerPhoneNumber, ids);
          }
          ids.add(id);
        } while (cursor.moveToNext());
      }
    }
    return idsByNumber;
  }

  private ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> queryPhoneLookupHistoryForNumbers(
      Context appContext, Set<DialerPhoneNumber> uniqueDialerPhoneNumbers) {
    DialerPhoneNumberUtil dialerPhoneNumberUtil =
        new DialerPhoneNumberUtil(PhoneNumberUtil.getInstance());
    Map<DialerPhoneNumber, String> dialerPhoneNumberToNormalizedNumbers =
        Maps.asMap(uniqueDialerPhoneNumbers, dialerPhoneNumberUtil::formatToE164);

    // Convert values to a set to remove any duplicates that are the result of two
    // DialerPhoneNumbers mapping to the same normalized number.
    String[] normalizedNumbers =
        dialerPhoneNumberToNormalizedNumbers.values().toArray(new String[] {});
    String[] questionMarks = new String[normalizedNumbers.length];
    Arrays.fill(questionMarks, "?");
    String selection =
        PhoneLookupHistory.NORMALIZED_NUMBER + " in (" + TextUtils.join(",", questionMarks) + ")";

    Map<String, PhoneLookupInfo> normalizedNumberToInfoMap = new ArrayMap<>();
    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                PhoneLookupHistory.CONTENT_URI,
                new String[] {
                  PhoneLookupHistory.NORMALIZED_NUMBER, PhoneLookupHistory.PHONE_LOOKUP_INFO,
                },
                selection,
                normalizedNumbers,
                null)) {

      if (cursor == null) {
        LogUtil.e("PhoneLookupDataSource.queryPhoneLookupHistoryForNumbers", "null cursor");
        return ImmutableMap.of();
      }

      if (cursor.moveToFirst()) {
        int normalizedNumberColumn =
            cursor.getColumnIndexOrThrow(PhoneLookupHistory.NORMALIZED_NUMBER);
        int phoneLookupInfoColumn =
            cursor.getColumnIndexOrThrow(PhoneLookupHistory.PHONE_LOOKUP_INFO);
        do {
          String normalizedNumber = cursor.getString(normalizedNumberColumn);
          PhoneLookupInfo phoneLookupInfo;
          try {
            phoneLookupInfo = PhoneLookupInfo.parseFrom(cursor.getBlob(phoneLookupInfoColumn));
          } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(e);
          }
          normalizedNumberToInfoMap.put(normalizedNumber, phoneLookupInfo);
        } while (cursor.moveToNext());
      }
    }

    // We have the required information in normalizedNumberToInfoMap but it's keyed by normalized
    // number instead of DialerPhoneNumber. Build and return a new map keyed by DialerPhoneNumber.
    return ImmutableMap.copyOf(
        Maps.asMap(
            uniqueDialerPhoneNumbers,
            (dialerPhoneNumber) -> {
              String normalizedNumber = dialerPhoneNumberToNormalizedNumbers.get(dialerPhoneNumber);
              PhoneLookupInfo phoneLookupInfo = normalizedNumberToInfoMap.get(normalizedNumber);
              // If data is cleared or for other reasons, the PhoneLookupHistory may not contain an
              // entry for a number. Just use an empty value for that case.
              return phoneLookupInfo == null
                  ? PhoneLookupInfo.getDefaultInstance()
                  : phoneLookupInfo;
            }));
  }

  private static void populateInserts(
      ImmutableMap<Long, PhoneLookupInfo> existingInfo, CallLogMutations mutations) {
    for (Entry<Long, ContentValues> entry : mutations.getInserts().entrySet()) {
      long id = entry.getKey();
      ContentValues contentValues = entry.getValue();
      PhoneLookupInfo phoneLookupInfo = existingInfo.get(id);
      // Existing info might be missing if data was cleared or for other reasons.
      if (phoneLookupInfo != null) {
        contentValues.put(AnnotatedCallLog.NAME, selectName(phoneLookupInfo));
      }
    }
  }

  private static void updateMutations(
      ImmutableMap<Long, PhoneLookupInfo> updatesToApply, CallLogMutations mutations) {
    for (Entry<Long, PhoneLookupInfo> entry : updatesToApply.entrySet()) {
      long id = entry.getKey();
      PhoneLookupInfo phoneLookupInfo = entry.getValue();
      ContentValues contentValuesToInsert = mutations.getInserts().get(id);
      if (contentValuesToInsert != null) {
        /*
         * This is a confusing case. Consider:
         *
         * 1) An incoming call from "Bob" arrives; "Bob" is written to PhoneLookupHistory.
         * 2) User changes Bob's name to "Robert".
         * 3) User opens call log, and this code is invoked with the inserted call as a mutation.
         *
         * In populateInserts, we retrieved "Bob" from PhoneLookupHistory and wrote it to the insert
         * mutation, which is wrong. We need to actually ask the phone lookups for the most up to
         * date information ("Robert"), and update the "insert" mutation again.
         *
         * Having understood this, you may wonder why populateInserts() is needed at all--excellent
         * question! Consider:
         *
         * 1) An incoming call from number 123 ("Bob") arrives at time T1; "Bob" is written to
         * PhoneLookupHistory.
         * 2) User opens call log at time T2 and "Bob" is written to it, and everything is fine; the
         * call log can be considered accurate as of T2.
         * 3) An incoming call from number 456 ("John") arrives at time T3. Let's say the contact
         * info for John was last modified at time T0.
         * 4) Now imagine that populateInserts() didn't exist; the phone lookup will ask for any
         * information for phone number 456 which has changed since T2--but "John" hasn't changed
         * since then so no contact information would be found.
         *
         * The populateInserts() method avoids this problem by always first populating inserted
         * mutations from PhoneLookupHistory; in this case "John" would be copied during
         * populateInserts() and there wouldn't be further updates needed here.
         */
        contentValuesToInsert.put(AnnotatedCallLog.NAME, selectName(phoneLookupInfo));
        continue;
      }
      ContentValues contentValuesToUpdate = mutations.getUpdates().get(id);
      if (contentValuesToUpdate != null) {
        contentValuesToUpdate.put(AnnotatedCallLog.NAME, selectName(phoneLookupInfo));
        continue;
      }
      // Else this row is not already scheduled for insert or update and we need to schedule it.
      ContentValues contentValues = new ContentValues();
      contentValues.put(AnnotatedCallLog.NAME, selectName(phoneLookupInfo));
      mutations.getUpdates().put(id, contentValues);
    }
  }

  private static String selectName(PhoneLookupInfo phoneLookupInfo) {
    return PhoneLookupSelector.selectName(phoneLookupInfo);
  }
}
