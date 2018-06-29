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
package com.android.dialer.calllog.database;

import android.database.Cursor;
import android.database.StaleDataException;
import android.provider.CallLog.Calls;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import com.android.dialer.CoalescedIds;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.NumberAttributes;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.common.Assert;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.metrics.FutureTimer;
import com.android.dialer.metrics.Metrics;
import com.android.dialer.phonenumberproto.DialerPhoneNumberUtil;
import com.android.dialer.telecom.TelecomUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Objects;
import javax.inject.Inject;

/** Combines adjacent rows in {@link AnnotatedCallLog}. */
public class Coalescer {

  private final FutureTimer futureTimer;
  private final ListeningExecutorService backgroundExecutorService;

  @Inject
  Coalescer(
      @BackgroundExecutor ListeningExecutorService backgroundExecutorService,
      FutureTimer futureTimer) {
    this.backgroundExecutorService = backgroundExecutorService;
    this.futureTimer = futureTimer;
  }

  /**
   * Given rows from {@link AnnotatedCallLog}, combine adjacent ones which should be collapsed for
   * display purposes.
   *
   * @param allAnnotatedCallLogRowsSortedByTimestampDesc {@link AnnotatedCallLog} rows sorted in
   *     descending order of timestamp.
   * @return a future of a list of {@link CoalescedRow coalesced rows}, which will be used to
   *     display call log entries.
   */
  public ListenableFuture<ImmutableList<CoalescedRow>> coalesce(
      @NonNull Cursor allAnnotatedCallLogRowsSortedByTimestampDesc) {
    ListenableFuture<ImmutableList<CoalescedRow>> coalescingFuture =
        backgroundExecutorService.submit(
            () -> coalesceInternal(Assert.isNotNull(allAnnotatedCallLogRowsSortedByTimestampDesc)));
    futureTimer.applyTiming(coalescingFuture, Metrics.NEW_CALL_LOG_COALESCE);
    return coalescingFuture;
  }

  /**
   * Reads the entire {@link AnnotatedCallLog} into memory from the provided cursor and then builds
   * and returns a list of {@link CoalescedRow coalesced rows}, which is the result of combining
   * adjacent rows which should be collapsed for display purposes.
   *
   * @param allAnnotatedCallLogRowsSortedByTimestampDesc {@link AnnotatedCallLog} rows sorted in
   *     descending order of timestamp.
   * @return a list of {@link CoalescedRow coalesced rows}, which will be used to display call log
   *     entries.
   */
  @WorkerThread
  @NonNull
  private ImmutableList<CoalescedRow> coalesceInternal(
      Cursor allAnnotatedCallLogRowsSortedByTimestampDesc) throws ExpectedCoalescerException {
    Assert.isWorkerThread();

    ImmutableList.Builder<CoalescedRow> coalescedRowListBuilder = new ImmutableList.Builder<>();

    try {
      if (!allAnnotatedCallLogRowsSortedByTimestampDesc.moveToFirst()) {
        return ImmutableList.of();
      }

      RowCombiner rowCombiner = new RowCombiner(allAnnotatedCallLogRowsSortedByTimestampDesc);
      rowCombiner.startNewGroup();

      long coalescedRowId = 0;
      do {
        boolean isRowMerged = rowCombiner.mergeRow(allAnnotatedCallLogRowsSortedByTimestampDesc);

        if (isRowMerged) {
          allAnnotatedCallLogRowsSortedByTimestampDesc.moveToNext();
        }

        if (!isRowMerged || allAnnotatedCallLogRowsSortedByTimestampDesc.isAfterLast()) {
          coalescedRowListBuilder.add(
              rowCombiner.combine().toBuilder().setId(coalescedRowId++).build());
          rowCombiner.startNewGroup();
        }
      } while (!allAnnotatedCallLogRowsSortedByTimestampDesc.isAfterLast());

      return coalescedRowListBuilder.build();

    } catch (Exception exception) {
      // Coalescing can fail if cursor "allAnnotatedCallLogRowsSortedByTimestampDesc" is closed by
      // its loader while the work is still in progress.
      //
      // This can happen when the loader restarts and finishes loading data before the coalescing
      // work is completed.
      //
      // This kind of failure doesn't have to crash the app as coalescing will be restarted on the
      // latest data obtained by the loader. Therefore, we inspect the exception here and throw an
      // ExpectedCoalescerException if it is the case described above.
      //
      // The type of expected exception depends on whether AbstractWindowedCursor#checkPosition() is
      // called when the cursor is closed.
      //   (1) If it is called before the cursor is closed, we will get IllegalStateException thrown
      //       by SQLiteClosable when it attempts to acquire a reference to the database.
      //   (2) Otherwise, we will get StaleDataException thrown by AbstractWindowedCursor's
      //       checkPosition() method.
      //
      // Note that it would be more accurate to inspect the stack trace to locate the origin of the
      // exception. However, according to the documentation on Throwable#getStackTrace, "some
      // virtual machines may, under some circumstances, omit one or more stack frames from the
      // stack trace". "In the extreme case, a virtual machine that has no stack trace information
      // concerning this throwable is permitted to return a zero-length array from this method."
      // Therefore, the best we can do is to inspect the message in the exception.
      // TODO(linyuh): try to avoid the expected failure.
      String message = exception.getMessage();
      if (message != null
          && ((exception instanceof StaleDataException
                  && message.startsWith("Attempting to access a closed CursorWindow"))
              || (exception instanceof IllegalStateException
                  && message.startsWith("attempt to re-open an already-closed object")))) {
        throw new ExpectedCoalescerException(exception);
      }

      throw exception;
    }
  }

  /** Combines rows from {@link AnnotatedCallLog} into a {@link CoalescedRow}. */
  private static final class RowCombiner {
    private final CoalescedRow.Builder coalescedRowBuilder = CoalescedRow.newBuilder();
    private final CoalescedIds.Builder coalescedIdsBuilder = CoalescedIds.newBuilder();

    // Indexes for columns in AnnotatedCallLog
    private final int idColumn;
    private final int timestampColumn;
    private final int numberColumn;
    private final int formattedNumberColumn;
    private final int numberPresentationColumn;
    private final int isReadColumn;
    private final int isNewColumn;
    private final int geocodedLocationColumn;
    private final int phoneAccountComponentNameColumn;
    private final int phoneAccountIdColumn;
    private final int featuresColumn;
    private final int numberAttributesColumn;
    private final int isVoicemailCallColumn;
    private final int voicemailCallTagColumn;
    private final int callTypeColumn;

    // DialerPhoneNumberUtil will be created lazily as its instantiation is expensive.
    private DialerPhoneNumberUtil dialerPhoneNumberUtil = null;

    RowCombiner(Cursor annotatedCallLogRow) {
      idColumn = annotatedCallLogRow.getColumnIndexOrThrow(AnnotatedCallLog._ID);
      timestampColumn = annotatedCallLogRow.getColumnIndexOrThrow(AnnotatedCallLog.TIMESTAMP);
      numberColumn = annotatedCallLogRow.getColumnIndexOrThrow(AnnotatedCallLog.NUMBER);
      formattedNumberColumn =
          annotatedCallLogRow.getColumnIndexOrThrow(AnnotatedCallLog.FORMATTED_NUMBER);
      numberPresentationColumn =
          annotatedCallLogRow.getColumnIndexOrThrow(AnnotatedCallLog.NUMBER_PRESENTATION);
      isReadColumn = annotatedCallLogRow.getColumnIndexOrThrow(AnnotatedCallLog.IS_READ);
      isNewColumn = annotatedCallLogRow.getColumnIndexOrThrow(AnnotatedCallLog.NEW);
      geocodedLocationColumn =
          annotatedCallLogRow.getColumnIndexOrThrow(AnnotatedCallLog.GEOCODED_LOCATION);
      phoneAccountComponentNameColumn =
          annotatedCallLogRow.getColumnIndexOrThrow(AnnotatedCallLog.PHONE_ACCOUNT_COMPONENT_NAME);
      phoneAccountIdColumn =
          annotatedCallLogRow.getColumnIndexOrThrow(AnnotatedCallLog.PHONE_ACCOUNT_ID);
      featuresColumn = annotatedCallLogRow.getColumnIndexOrThrow(AnnotatedCallLog.FEATURES);
      numberAttributesColumn =
          annotatedCallLogRow.getColumnIndexOrThrow(AnnotatedCallLog.NUMBER_ATTRIBUTES);
      isVoicemailCallColumn =
          annotatedCallLogRow.getColumnIndexOrThrow(AnnotatedCallLog.IS_VOICEMAIL_CALL);
      voicemailCallTagColumn =
          annotatedCallLogRow.getColumnIndexOrThrow(AnnotatedCallLog.VOICEMAIL_CALL_TAG);
      callTypeColumn = annotatedCallLogRow.getColumnIndexOrThrow(AnnotatedCallLog.CALL_TYPE);
    }

    /**
     * Prepares {@link RowCombiner} for building a new group of rows by clearing information on all
     * previously merged rows.
     */
    void startNewGroup() {
      coalescedRowBuilder.clear();
      coalescedIdsBuilder.clear();
    }

    /**
     * Merge the given {@link AnnotatedCallLog} row into the current group.
     *
     * @return true if the given row is merged.
     */
    boolean mergeRow(Cursor annotatedCallLogRow) {
      Assert.checkArgument(annotatedCallLogRow.getInt(callTypeColumn) != Calls.VOICEMAIL_TYPE);

      if (!canMergeRow(annotatedCallLogRow)) {
        return false;
      }

      // Set fields that don't use the most recent value.
      //
      // Currently there is only one such field: "features".
      // If any call in a group includes a feature (like Wifi/HD), consider the group to have
      // the feature.
      coalescedRowBuilder.setFeatures(
          coalescedRowBuilder.getFeatures() | annotatedCallLogRow.getInt(featuresColumn));

      // Set fields that use the most recent value.
      // Rows passed to Coalescer are already sorted in descending order of timestamp. If the
      // coalesced ID list is not empty, it means RowCombiner has merged the most recent row in a
      // group and there is no need to continue as we only set fields that use the most recent value
      // from this point forward.
      if (!coalescedIdsBuilder.getCoalescedIdList().isEmpty()) {
        coalescedIdsBuilder.addCoalescedId(annotatedCallLogRow.getInt(idColumn));
        return true;
      }

      coalescedRowBuilder
          .setTimestamp(annotatedCallLogRow.getLong(timestampColumn))
          .setNumberPresentation(annotatedCallLogRow.getInt(numberPresentationColumn))
          .setIsRead(annotatedCallLogRow.getInt(isReadColumn) == 1)
          .setIsNew(annotatedCallLogRow.getInt(isNewColumn) == 1)
          .setIsVoicemailCall(annotatedCallLogRow.getInt(isVoicemailCallColumn) == 1)
          .setCallType(annotatedCallLogRow.getInt(callTypeColumn));

      // Two different DialerPhoneNumbers could be combined if they are different but considered
      // to be a match by libphonenumber; in this case we arbitrarily select the most recent one.
      try {
        coalescedRowBuilder.setNumber(
            DialerPhoneNumber.parseFrom(annotatedCallLogRow.getBlob(numberColumn)));
      } catch (InvalidProtocolBufferException e) {
        throw Assert.createAssertionFailException("Unable to parse DialerPhoneNumber bytes", e);
      }

      String formattedNumber = annotatedCallLogRow.getString(formattedNumberColumn);
      if (!TextUtils.isEmpty(formattedNumber)) {
        coalescedRowBuilder.setFormattedNumber(formattedNumber);
      }

      String geocodedLocation = annotatedCallLogRow.getString(geocodedLocationColumn);
      if (!TextUtils.isEmpty(geocodedLocation)) {
        coalescedRowBuilder.setGeocodedLocation(geocodedLocation);
      }

      String phoneAccountComponentName =
          annotatedCallLogRow.getString(phoneAccountComponentNameColumn);
      if (!TextUtils.isEmpty(phoneAccountComponentName)) {
        coalescedRowBuilder.setPhoneAccountComponentName(phoneAccountComponentName);
      }

      String phoneAccountId = annotatedCallLogRow.getString(phoneAccountIdColumn);
      if (!TextUtils.isEmpty(phoneAccountId)) {
        coalescedRowBuilder.setPhoneAccountId(phoneAccountId);
      }

      try {
        coalescedRowBuilder.setNumberAttributes(
            NumberAttributes.parseFrom(annotatedCallLogRow.getBlob(numberAttributesColumn)));
      } catch (InvalidProtocolBufferException e) {
        throw Assert.createAssertionFailException("Unable to parse NumberAttributes bytes", e);
      }

      String voicemailCallTag = annotatedCallLogRow.getString(voicemailCallTagColumn);
      if (!TextUtils.isEmpty(voicemailCallTag)) {
        coalescedRowBuilder.setVoicemailCallTag(voicemailCallTag);
      }

      coalescedIdsBuilder.addCoalescedId(annotatedCallLogRow.getInt(idColumn));
      return true;
    }

    /** Builds a {@link CoalescedRow} based on all rows merged into the current group. */
    CoalescedRow combine() {
      return coalescedRowBuilder.setCoalescedIds(coalescedIdsBuilder.build()).build();
    }

    /**
     * Returns true if the given {@link AnnotatedCallLog} row can be merged into the current group.
     */
    private boolean canMergeRow(Cursor annotatedCallLogRow) {
      return coalescedIdsBuilder.getCoalescedIdList().isEmpty()
          || (samePhoneAccount(annotatedCallLogRow)
              && sameNumberPresentation(annotatedCallLogRow)
              && meetsCallFeatureCriteria(annotatedCallLogRow)
              && meetsDialerPhoneNumberCriteria(annotatedCallLogRow));
    }

    private boolean samePhoneAccount(Cursor annotatedCallLogRow) {
      PhoneAccountHandle groupPhoneAccountHandle =
          TelecomUtil.composePhoneAccountHandle(
              coalescedRowBuilder.getPhoneAccountComponentName(),
              coalescedRowBuilder.getPhoneAccountId());
      PhoneAccountHandle rowPhoneAccountHandle =
          TelecomUtil.composePhoneAccountHandle(
              annotatedCallLogRow.getString(phoneAccountComponentNameColumn),
              annotatedCallLogRow.getString(phoneAccountIdColumn));

      return Objects.equals(groupPhoneAccountHandle, rowPhoneAccountHandle);
    }

    private boolean sameNumberPresentation(Cursor annotatedCallLogRow) {
      return coalescedRowBuilder.getNumberPresentation()
          == annotatedCallLogRow.getInt(numberPresentationColumn);
    }

    private boolean meetsCallFeatureCriteria(Cursor annotatedCallLogRow) {
      int groupFeatures = coalescedRowBuilder.getFeatures();
      int rowFeatures = annotatedCallLogRow.getInt(featuresColumn);

      // A row with FEATURES_ASSISTED_DIALING should not be combined with one without it.
      if ((groupFeatures & TelephonyManagerCompat.FEATURES_ASSISTED_DIALING)
          != (rowFeatures & TelephonyManagerCompat.FEATURES_ASSISTED_DIALING)) {
        return false;
      }

      // A video call should not be combined with one that is not a video call.
      if ((groupFeatures & Calls.FEATURES_VIDEO) != (rowFeatures & Calls.FEATURES_VIDEO)) {
        return false;
      }

      // A RTT call should not be combined with one that is not a RTT call.
      if ((groupFeatures & Calls.FEATURES_RTT) != (rowFeatures & Calls.FEATURES_RTT)) {
        return false;
      }

      return true;
    }

    private boolean meetsDialerPhoneNumberCriteria(Cursor annotatedCallLogRow) {
      DialerPhoneNumber groupPhoneNumber = coalescedRowBuilder.getNumber();

      DialerPhoneNumber rowPhoneNumber;
      try {
        byte[] rowPhoneNumberBytes = annotatedCallLogRow.getBlob(numberColumn);
        if (rowPhoneNumberBytes == null) {
          return false; // Empty numbers should not be combined.
        }
        rowPhoneNumber = DialerPhoneNumber.parseFrom(rowPhoneNumberBytes);
      } catch (InvalidProtocolBufferException e) {
        throw Assert.createAssertionFailException("Unable to parse DialerPhoneNumber bytes", e);
      }

      if (dialerPhoneNumberUtil == null) {
        dialerPhoneNumberUtil = new DialerPhoneNumberUtil();
      }

      return dialerPhoneNumberUtil.isMatch(groupPhoneNumber, rowPhoneNumber);
    }
  }

  /** A checked exception thrown when expected failure happens when coalescing is in progress. */
  public static final class ExpectedCoalescerException extends Exception {
    ExpectedCoalescerException(Throwable throwable) {
      super("Expected coalescing exception", throwable);
    }
  }
}
