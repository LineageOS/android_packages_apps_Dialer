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

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.support.annotation.MainThread;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.calllog.datasources.CallLogMutations;
import com.android.dialer.calllog.datasources.util.RowCombiner;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.common.concurrent.Annotations.LightweightExecutor;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupSelector;
import com.android.dialer.phonelookup.database.contract.PhoneLookupHistoryContract;
import com.android.dialer.phonelookup.database.contract.PhoneLookupHistoryContract.PhoneLookupHistory;
import com.android.dialer.phonenumberproto.DialerPhoneNumberUtil;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.inject.Inject;

/**
 * Responsible for maintaining the columns in the annotated call log which are derived from phone
 * numbers.
 */
public final class PhoneLookupDataSource implements CallLogDataSource {

  private final PhoneLookup<PhoneLookupInfo> phoneLookup;
  private final ListeningExecutorService backgroundExecutorService;
  private final ListeningExecutorService lightweightExecutorService;

  /**
   * Keyed by normalized number (the primary key for PhoneLookupHistory).
   *
   * <p>This is state saved between the {@link #fill(Context, CallLogMutations)} and {@link
   * #onSuccessfulFill(Context)} operations.
   */
  private final Map<String, PhoneLookupInfo> phoneLookupHistoryRowsToUpdate = new ArrayMap<>();

  /**
   * Normalized numbers (the primary key for PhoneLookupHistory) which should be deleted from
   * PhoneLookupHistory.
   *
   * <p>This is state saved between the {@link #fill(Context, CallLogMutations)} and {@link
   * #onSuccessfulFill(Context)} operations.
   */
  private final Set<String> phoneLookupHistoryRowsToDelete = new ArraySet<>();

  @Inject
  PhoneLookupDataSource(
      PhoneLookup<PhoneLookupInfo> phoneLookup,
      @BackgroundExecutor ListeningExecutorService backgroundExecutorService,
      @LightweightExecutor ListeningExecutorService lightweightExecutorService) {
    this.phoneLookup = phoneLookup;
    this.backgroundExecutorService = backgroundExecutorService;
    this.lightweightExecutorService = lightweightExecutorService;
  }

  @Override
  public ListenableFuture<Boolean> isDirty(Context appContext) {
    ListenableFuture<ImmutableSet<DialerPhoneNumber>> phoneNumbers =
        backgroundExecutorService.submit(
            () -> queryDistinctDialerPhoneNumbersFromAnnotatedCallLog(appContext));
    return Futures.transformAsync(phoneNumbers, phoneLookup::isDirty, lightweightExecutorService);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method uses the following algorithm:
   *
   * <ul>
   *   <li>Finds the phone numbers of interest by taking the union of the distinct
   *       DialerPhoneNumbers from the AnnotatedCallLog and the pending inserts provided in {@code
   *       mutations}
   *   <li>Uses them to fetch the current information from PhoneLookupHistory, in order to construct
   *       a map from DialerPhoneNumber to PhoneLookupInfo
   *       <ul>
   *         <li>If no PhoneLookupInfo is found (e.g. app data was cleared?) an empty value is used.
   *       </ul>
   *   <li>Looks through the provided set of mutations
   *   <li>For inserts, uses the contents of PhoneLookupHistory to populate the fields of the
   *       provided mutations. (Note that at this point, data may not be fully up-to-date, but the
   *       next steps will take care of that.)
   *   <li>Uses all of the numbers from AnnotatedCallLog to invoke (composite) {@link
   *       PhoneLookup#getMostRecentInfo(ImmutableMap)}
   *   <li>Looks through the results of getMostRecentInfo
   *       <ul>
   *         <li>For each number, checks if the original PhoneLookupInfo differs from the new one
   *         <li>If so, it applies the update to the mutations and (in onSuccessfulFill) writes the
   *             new value back to the PhoneLookupHistory.
   *       </ul>
   * </ul>
   */
  @Override
  public ListenableFuture<Void> fill(Context appContext, CallLogMutations mutations) {
    // Clear state saved since the last call to fill. This is necessary in case fill is called but
    // onSuccessfulFill is not called during a previous flow.
    phoneLookupHistoryRowsToUpdate.clear();
    phoneLookupHistoryRowsToDelete.clear();

    // First query information from annotated call log (and include pending inserts).
    ListenableFuture<Map<DialerPhoneNumber, Set<Long>>> annotatedCallLogIdsByNumberFuture =
        backgroundExecutorService.submit(
            () -> collectIdAndNumberFromAnnotatedCallLogAndPendingInserts(appContext, mutations));

    // Use it to create the original info map.
    ListenableFuture<ImmutableMap<DialerPhoneNumber, PhoneLookupInfo>> originalInfoMapFuture =
        Futures.transform(
            annotatedCallLogIdsByNumberFuture,
            annotatedCallLogIdsByNumber ->
                queryPhoneLookupHistoryForNumbers(appContext, annotatedCallLogIdsByNumber.keySet()),
            backgroundExecutorService);

    // Use the original info map to generate the updated info map by delegating to phoneLookup.
    ListenableFuture<ImmutableMap<DialerPhoneNumber, PhoneLookupInfo>> updatedInfoMapFuture =
        Futures.transformAsync(
            originalInfoMapFuture, phoneLookup::getMostRecentInfo, lightweightExecutorService);

    // This is the computation that will use the result of all of the above.
    Callable<ImmutableMap<Long, PhoneLookupInfo>> computeRowsToUpdate =
        () -> {
          // These get() calls are safe because we are using whenAllSucceed below.
          Map<DialerPhoneNumber, Set<Long>> annotatedCallLogIdsByNumber =
              annotatedCallLogIdsByNumberFuture.get();
          ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> originalInfoMap =
              originalInfoMapFuture.get();
          ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> updatedInfoMap =
              updatedInfoMapFuture.get();

          // First populate the insert mutations
          ImmutableMap.Builder<Long, PhoneLookupInfo>
              originalPhoneLookupHistoryDataByAnnotatedCallLogId = ImmutableMap.builder();
          for (Entry<DialerPhoneNumber, PhoneLookupInfo> entry : originalInfoMap.entrySet()) {
            DialerPhoneNumber dialerPhoneNumber = entry.getKey();
            PhoneLookupInfo phoneLookupInfo = entry.getValue();
            for (Long id : annotatedCallLogIdsByNumber.get(dialerPhoneNumber)) {
              originalPhoneLookupHistoryDataByAnnotatedCallLogId.put(id, phoneLookupInfo);
            }
          }
          populateInserts(originalPhoneLookupHistoryDataByAnnotatedCallLogId.build(), mutations);

          // Compute and save the PhoneLookupHistory rows which can be deleted in onSuccessfulFill.
          DialerPhoneNumberUtil dialerPhoneNumberUtil =
              new DialerPhoneNumberUtil(PhoneNumberUtil.getInstance());
          phoneLookupHistoryRowsToDelete.addAll(
              computePhoneLookupHistoryRowsToDelete(
                  annotatedCallLogIdsByNumber, mutations, dialerPhoneNumberUtil));

          // Now compute the rows to update.
          ImmutableMap.Builder<Long, PhoneLookupInfo> rowsToUpdate = ImmutableMap.builder();
          for (Entry<DialerPhoneNumber, PhoneLookupInfo> entry : updatedInfoMap.entrySet()) {
            DialerPhoneNumber dialerPhoneNumber = entry.getKey();
            PhoneLookupInfo upToDateInfo = entry.getValue();
            if (!originalInfoMap.get(dialerPhoneNumber).equals(upToDateInfo)) {
              for (Long id : annotatedCallLogIdsByNumber.get(dialerPhoneNumber)) {
                rowsToUpdate.put(id, upToDateInfo);
              }
              // Also save the updated information so that it can be written to PhoneLookupHistory
              // in onSuccessfulFill.
              String normalizedNumber = dialerPhoneNumberUtil.normalizeNumber(dialerPhoneNumber);
              phoneLookupHistoryRowsToUpdate.put(normalizedNumber, upToDateInfo);
            }
          }
          return rowsToUpdate.build();
        };

    ListenableFuture<ImmutableMap<Long, PhoneLookupInfo>> rowsToUpdateFuture =
        Futures.whenAllSucceed(
                annotatedCallLogIdsByNumberFuture, updatedInfoMapFuture, originalInfoMapFuture)
            .call(
                computeRowsToUpdate,
                backgroundExecutorService /* PhoneNumberUtil may do disk IO */);

    // Finally update the mutations with the computed rows.
    return Futures.transform(
        rowsToUpdateFuture,
        rowsToUpdate -> {
          updateMutations(rowsToUpdate, mutations);
          return null;
        },
        lightweightExecutorService);
  }

  @Override
  public ListenableFuture<Void> onSuccessfulFill(Context appContext) {
    // First update and/or delete the appropriate rows in PhoneLookupHistory.
    ListenableFuture<Void> writePhoneLookupHistory =
        backgroundExecutorService.submit(() -> writePhoneLookupHistory(appContext));

    // If that succeeds, delegate to the composite PhoneLookup to notify all PhoneLookups that both
    // the AnnotatedCallLog and PhoneLookupHistory have been successfully updated.
    return Futures.transformAsync(
        writePhoneLookupHistory,
        unused -> phoneLookup.onSuccessfulBulkUpdate(),
        lightweightExecutorService);
  }

  @WorkerThread
  private Void writePhoneLookupHistory(Context appContext)
      throws RemoteException, OperationApplicationException {
    ArrayList<ContentProviderOperation> operations = new ArrayList<>();
    long currentTimestamp = System.currentTimeMillis();
    for (Entry<String, PhoneLookupInfo> entry : phoneLookupHistoryRowsToUpdate.entrySet()) {
      String normalizedNumber = entry.getKey();
      PhoneLookupInfo phoneLookupInfo = entry.getValue();
      ContentValues contentValues = new ContentValues();
      contentValues.put(PhoneLookupHistory.PHONE_LOOKUP_INFO, phoneLookupInfo.toByteArray());
      contentValues.put(PhoneLookupHistory.LAST_MODIFIED, currentTimestamp);
      operations.add(
          ContentProviderOperation.newUpdate(numberUri(normalizedNumber))
              .withValues(contentValues)
              .build());
    }
    for (String normalizedNumber : phoneLookupHistoryRowsToDelete) {
      operations.add(ContentProviderOperation.newDelete(numberUri(normalizedNumber)).build());
    }
    appContext.getContentResolver().applyBatch(PhoneLookupHistoryContract.AUTHORITY, operations);
    return null;
  }

  @WorkerThread
  @Override
  public ContentValues coalesce(List<ContentValues> individualRowsSortedByTimestampDesc) {
    return new RowCombiner(individualRowsSortedByTimestampDesc)
        .useMostRecentString(AnnotatedCallLog.NAME)
        .useMostRecentString(AnnotatedCallLog.NUMBER_TYPE_LABEL)
        .useMostRecentString(AnnotatedCallLog.PHOTO_URI)
        .useMostRecentLong(AnnotatedCallLog.PHOTO_ID)
        .useMostRecentString(AnnotatedCallLog.LOOKUP_URI)
        .combine();
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

  private Map<DialerPhoneNumber, Set<Long>> collectIdAndNumberFromAnnotatedCallLogAndPendingInserts(
      Context appContext, CallLogMutations mutations) {
    Map<DialerPhoneNumber, Set<Long>> idsByNumber = new ArrayMap<>();
    // First add any pending inserts to the map.
    for (Entry<Long, ContentValues> entry : mutations.getInserts().entrySet()) {
      long id = entry.getKey();
      ContentValues insertedContentValues = entry.getValue();
      DialerPhoneNumber dialerPhoneNumber;
      try {
        dialerPhoneNumber =
            DialerPhoneNumber.parseFrom(
                insertedContentValues.getAsByteArray(AnnotatedCallLog.NUMBER));
      } catch (InvalidProtocolBufferException e) {
        throw new IllegalStateException(e);
      }
      Set<Long> ids = idsByNumber.get(dialerPhoneNumber);
      if (ids == null) {
        ids = new ArraySet<>();
        idsByNumber.put(dialerPhoneNumber, ids);
      }
      ids.add(id);
    }

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
        LogUtil.e(
            "PhoneLookupDataSource.collectIdAndNumberFromAnnotatedCallLogAndPendingInserts",
            "null cursor");
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
        Maps.asMap(uniqueDialerPhoneNumbers, dialerPhoneNumberUtil::normalizeNumber);

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
        updateContentValues(contentValues, phoneLookupInfo);
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
        updateContentValues(contentValuesToInsert, phoneLookupInfo);
        continue;
      }
      ContentValues contentValuesToUpdate = mutations.getUpdates().get(id);
      if (contentValuesToUpdate != null) {
        updateContentValues(contentValuesToUpdate, phoneLookupInfo);
        continue;
      }
      // Else this row is not already scheduled for insert or update and we need to schedule it.
      ContentValues contentValues = new ContentValues();
      updateContentValues(contentValues, phoneLookupInfo);
      mutations.getUpdates().put(id, contentValues);
    }
  }

  private Set<String> computePhoneLookupHistoryRowsToDelete(
      Map<DialerPhoneNumber, Set<Long>> annotatedCallLogIdsByNumber,
      CallLogMutations mutations,
      DialerPhoneNumberUtil dialerPhoneNumberUtil) {
    if (mutations.getDeletes().isEmpty()) {
      return ImmutableSet.of();
    }
    // First convert the dialer phone numbers to normalized numbers; we need to combine entries
    // because different DialerPhoneNumbers can map to the same normalized number.
    Map<String, Set<Long>> idsByNormalizedNumber = new ArrayMap<>();
    for (Entry<DialerPhoneNumber, Set<Long>> entry : annotatedCallLogIdsByNumber.entrySet()) {
      DialerPhoneNumber dialerPhoneNumber = entry.getKey();
      Set<Long> idsForDialerPhoneNumber = entry.getValue();
      String normalizedNumber = dialerPhoneNumberUtil.normalizeNumber(dialerPhoneNumber);
      Set<Long> idsForNormalizedNumber = idsByNormalizedNumber.get(normalizedNumber);
      if (idsForNormalizedNumber == null) {
        idsForNormalizedNumber = new ArraySet<>();
        idsByNormalizedNumber.put(normalizedNumber, idsForNormalizedNumber);
      }
      idsForNormalizedNumber.addAll(idsForDialerPhoneNumber);
    }
    // Now look through and remove all IDs that were scheduled for delete; after doing that, if
    // there are no remaining IDs left for a normalized number, the number can be deleted from
    // PhoneLookupHistory.
    Set<String> normalizedNumbersToDelete = new ArraySet<>();
    for (Entry<String, Set<Long>> entry : idsByNormalizedNumber.entrySet()) {
      String normalizedNumber = entry.getKey();
      Set<Long> idsForNormalizedNumber = entry.getValue();
      idsForNormalizedNumber.removeAll(mutations.getDeletes());
      if (idsForNormalizedNumber.isEmpty()) {
        normalizedNumbersToDelete.add(normalizedNumber);
      }
    }
    return normalizedNumbersToDelete;
  }

  private static void updateContentValues(
      ContentValues contentValues, PhoneLookupInfo phoneLookupInfo) {
    contentValues.put(AnnotatedCallLog.NAME, PhoneLookupSelector.selectName(phoneLookupInfo));
    contentValues.put(
        AnnotatedCallLog.PHOTO_URI, PhoneLookupSelector.selectPhotoUri(phoneLookupInfo));
    contentValues.put(
        AnnotatedCallLog.PHOTO_ID, PhoneLookupSelector.selectPhotoId(phoneLookupInfo));
    contentValues.put(
        AnnotatedCallLog.LOOKUP_URI, PhoneLookupSelector.selectLookupUri(phoneLookupInfo));
    contentValues.put(
        AnnotatedCallLog.NUMBER_TYPE_LABEL, PhoneLookupSelector.selectNumberLabel(phoneLookupInfo));
  }

  private static Uri numberUri(String number) {
    return PhoneLookupHistory.CONTENT_URI.buildUpon().appendEncodedPath(number).build();
  }
}
