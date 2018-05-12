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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.provider.CallLog.Calls;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import com.android.dialer.CoalescedIds;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.NumberAttributes;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.CoalescedAnnotatedCallLog;
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.calllog.datasources.DataSources;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.common.Assert;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.metrics.FutureTimer;
import com.android.dialer.metrics.Metrics;
import com.android.dialer.phonenumberproto.DialerPhoneNumberUtil;
import com.android.dialer.telecom.TelecomUtil;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;

/**
 * Coalesces rows in {@link AnnotatedCallLog} by combining adjacent rows.
 *
 * <p>Applies the logic that determines which adjacent rows should be coalesced, and then delegates
 * to each data source to determine how individual columns should be aggregated.
 */
public class Coalescer {

  // Indexes for CoalescedAnnotatedCallLog.ALL_COLUMNS
  private static final int ID = 0;
  private static final int TIMESTAMP = 1;
  private static final int NUMBER = 2;
  private static final int FORMATTED_NUMBER = 3;
  private static final int NUMBER_PRESENTATION = 4;
  private static final int IS_READ = 5;
  private static final int NEW = 6;
  private static final int GEOCODED_LOCATION = 7;
  private static final int PHONE_ACCOUNT_COMPONENT_NAME = 8;
  private static final int PHONE_ACCOUNT_ID = 9;
  private static final int FEATURES = 10;
  private static final int NUMBER_ATTRIBUTES = 11;
  private static final int IS_VOICEMAIL_CALL = 12;
  private static final int VOICEMAIL_CALL_TAG = 13;
  private static final int CALL_TYPE = 14;
  private static final int COALESCED_IDS = 15;

  private final DataSources dataSources;
  private final FutureTimer futureTimer;
  private final ListeningExecutorService backgroundExecutorService;

  @Inject
  Coalescer(
      @BackgroundExecutor ListeningExecutorService backgroundExecutorService,
      DataSources dataSources,
      FutureTimer futureTimer) {
    this.backgroundExecutorService = backgroundExecutorService;
    this.dataSources = dataSources;
    this.futureTimer = futureTimer;
  }

  /**
   * Given rows from {@link AnnotatedCallLog}, combine adjacent ones which should be collapsed for
   * display purposes.
   *
   * @param allAnnotatedCallLogRowsSortedByTimestampDesc {@link AnnotatedCallLog} rows sorted in
   *     descending order of timestamp.
   * @return a future of a {@link MatrixCursor} containing the {@link CoalescedAnnotatedCallLog}
   *     rows to display
   */
  public ListenableFuture<Cursor> coalesce(
      @NonNull Cursor allAnnotatedCallLogRowsSortedByTimestampDesc) {
    ListenableFuture<Cursor> coalescingFuture =
        backgroundExecutorService.submit(
            () -> coalesceInternal(Assert.isNotNull(allAnnotatedCallLogRowsSortedByTimestampDesc)));
    futureTimer.applyTiming(coalescingFuture, Metrics.NEW_CALL_LOG_COALESCE);
    return coalescingFuture;
  }

  /**
   * Reads the entire {@link AnnotatedCallLog} into memory from the provided cursor and then builds
   * and returns a new {@link MatrixCursor} of {@link CoalescedAnnotatedCallLog}, which is the
   * result of combining adjacent rows which should be collapsed for display purposes.
   *
   * @param allAnnotatedCallLogRowsSortedByTimestampDesc {@link AnnotatedCallLog} rows sorted in
   *     descending order of timestamp.
   * @return a new {@link MatrixCursor} containing the {@link CoalescedAnnotatedCallLog} rows to
   *     display
   */
  @WorkerThread
  @NonNull
  private Cursor coalesceInternal(Cursor allAnnotatedCallLogRowsSortedByTimestampDesc) {
    Assert.isWorkerThread();

    // Note: This method relies on rowsShouldBeCombined to determine which rows should be combined,
    // but delegates to data sources to actually aggregate column values.

    DialerPhoneNumberUtil dialerPhoneNumberUtil = new DialerPhoneNumberUtil();

    MatrixCursor allCoalescedRowsMatrixCursor =
        new MatrixCursor(
            CoalescedAnnotatedCallLog.ALL_COLUMNS,
            allAnnotatedCallLogRowsSortedByTimestampDesc.getCount());

    if (!allAnnotatedCallLogRowsSortedByTimestampDesc.moveToFirst()) {
      return allCoalescedRowsMatrixCursor;
    }

    int coalescedRowId = 0;
    List<ContentValues> currentRowGroup = new ArrayList<>();

    ContentValues firstRow = cursorRowToContentValues(allAnnotatedCallLogRowsSortedByTimestampDesc);
    currentRowGroup.add(firstRow);

    while (!currentRowGroup.isEmpty()) {
      // Group consecutive rows
      ContentValues firstRowInGroup = currentRowGroup.get(0);
      ContentValues currentRow = null;
      while (allAnnotatedCallLogRowsSortedByTimestampDesc.moveToNext()) {
        currentRow = cursorRowToContentValues(allAnnotatedCallLogRowsSortedByTimestampDesc);

        if (!rowsShouldBeCombined(dialerPhoneNumberUtil, firstRowInGroup, currentRow)) {
          break;
        }

        currentRowGroup.add(currentRow);
      }

      // Coalesce the group into a single row
      ContentValues coalescedRow = coalesceRowsForAllDataSources(currentRowGroup);
      coalescedRow.put(
          CoalescedAnnotatedCallLog.COALESCED_IDS, getCoalescedIds(currentRowGroup).toByteArray());
      addContentValuesToMatrixCursor(coalescedRow, allCoalescedRowsMatrixCursor, coalescedRowId++);

      // Clear the current group after the rows are coalesced.
      currentRowGroup.clear();

      // Add the first of the remaining rows to the current group.
      if (!allAnnotatedCallLogRowsSortedByTimestampDesc.isAfterLast()) {
        currentRowGroup.add(currentRow);
      }
    }

    return allCoalescedRowsMatrixCursor;
  }

  private static ContentValues cursorRowToContentValues(Cursor cursor) {
    ContentValues values = new ContentValues();
    String[] columns = cursor.getColumnNames();
    int length = columns.length;
    for (int i = 0; i < length; i++) {
      if (cursor.getType(i) == Cursor.FIELD_TYPE_BLOB) {
        values.put(columns[i], cursor.getBlob(i));
      } else {
        values.put(columns[i], cursor.getString(i));
      }
    }
    return values;
  }

  /**
   * @param row1 a row from {@link AnnotatedCallLog}
   * @param row2 a row from {@link AnnotatedCallLog}
   */
  private static boolean rowsShouldBeCombined(
      DialerPhoneNumberUtil dialerPhoneNumberUtil, ContentValues row1, ContentValues row2) {
    // Don't combine rows which don't use the same phone account.
    PhoneAccountHandle phoneAccount1 =
        TelecomUtil.composePhoneAccountHandle(
            row1.getAsString(AnnotatedCallLog.PHONE_ACCOUNT_COMPONENT_NAME),
            row1.getAsString(AnnotatedCallLog.PHONE_ACCOUNT_ID));
    PhoneAccountHandle phoneAccount2 =
        TelecomUtil.composePhoneAccountHandle(
            row2.getAsString(AnnotatedCallLog.PHONE_ACCOUNT_COMPONENT_NAME),
            row2.getAsString(AnnotatedCallLog.PHONE_ACCOUNT_ID));
    if (!Objects.equals(phoneAccount1, phoneAccount2)) {
      return false;
    }

    if (!row1.getAsInteger(AnnotatedCallLog.NUMBER_PRESENTATION)
        .equals(row2.getAsInteger(AnnotatedCallLog.NUMBER_PRESENTATION))) {
      return false;
    }

    if (!meetsCallFeatureCriteria(row1, row2)) {
      return false;
    }

    DialerPhoneNumber number1;
    DialerPhoneNumber number2;
    try {
      byte[] number1Bytes = row1.getAsByteArray(AnnotatedCallLog.NUMBER);
      byte[] number2Bytes = row2.getAsByteArray(AnnotatedCallLog.NUMBER);

      if (number1Bytes == null || number2Bytes == null) {
        // Empty numbers should not be combined.
        return false;
      }

      number1 = DialerPhoneNumber.parseFrom(number1Bytes);
      number2 = DialerPhoneNumber.parseFrom(number2Bytes);
    } catch (InvalidProtocolBufferException e) {
      throw Assert.createAssertionFailException("error parsing DialerPhoneNumber proto", e);
    }
    return dialerPhoneNumberUtil.isMatch(number1, number2);
  }

  /**
   * Returns true if column {@link AnnotatedCallLog#FEATURES} of the two given rows indicate that
   * they can be coalesced.
   */
  private static boolean meetsCallFeatureCriteria(ContentValues row1, ContentValues row2) {
    int row1Features = row1.getAsInteger(AnnotatedCallLog.FEATURES);
    int row2Features = row2.getAsInteger(AnnotatedCallLog.FEATURES);

    // A row with FEATURES_ASSISTED_DIALING should not be combined with one without it.
    if ((row1Features & TelephonyManagerCompat.FEATURES_ASSISTED_DIALING)
        != (row2Features & TelephonyManagerCompat.FEATURES_ASSISTED_DIALING)) {
      return false;
    }

    // A video call should not be combined with one that is not a video call.
    if ((row1Features & Calls.FEATURES_VIDEO) != (row2Features & Calls.FEATURES_VIDEO)) {
      return false;
    }

    // A RTT call should not be combined with one that is not a RTT call.
    if ((row1Features & Calls.FEATURES_RTT) != (row2Features & Calls.FEATURES_RTT)) {
      return false;
    }

    return true;
  }

  /**
   * Delegates to data sources to aggregate individual columns to create a new coalesced row.
   *
   * @param individualRows {@link AnnotatedCallLog} rows sorted by timestamp descending
   * @return a {@link CoalescedAnnotatedCallLog} row
   */
  private ContentValues coalesceRowsForAllDataSources(List<ContentValues> individualRows) {
    ContentValues coalescedValues = new ContentValues();
    for (CallLogDataSource dataSource : dataSources.getDataSourcesIncludingSystemCallLog()) {
      coalescedValues.putAll(dataSource.coalesce(individualRows));
    }
    return coalescedValues;
  }

  /**
   * Build a {@link CoalescedIds} proto that contains IDs of the rows in {@link AnnotatedCallLog}
   * that are coalesced into one row in {@link CoalescedAnnotatedCallLog}.
   *
   * @param individualRows {@link AnnotatedCallLog} rows sorted by timestamp descending
   * @return A {@link CoalescedIds} proto containing IDs of {@code individualRows}.
   */
  private CoalescedIds getCoalescedIds(List<ContentValues> individualRows) {
    CoalescedIds.Builder coalescedIds = CoalescedIds.newBuilder();

    for (ContentValues row : individualRows) {
      coalescedIds.addCoalescedId(Preconditions.checkNotNull(row.getAsLong(AnnotatedCallLog._ID)));
    }

    return coalescedIds.build();
  }

  /**
   * @param contentValues a {@link CoalescedAnnotatedCallLog} row
   * @param matrixCursor represents {@link CoalescedAnnotatedCallLog}
   */
  private static void addContentValuesToMatrixCursor(
      ContentValues contentValues, MatrixCursor matrixCursor, int rowId) {
    MatrixCursor.RowBuilder rowBuilder = matrixCursor.newRow();
    rowBuilder.add(CoalescedAnnotatedCallLog._ID, rowId);
    for (Map.Entry<String, Object> entry : contentValues.valueSet()) {
      rowBuilder.add(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Creates a new {@link CoalescedRow} based on the data at the provided cursor's current position.
   *
   * <p>The provided cursor should be one for {@link CoalescedAnnotatedCallLog}.
   */
  public static CoalescedRow toRow(Cursor coalescedAnnotatedCallLogCursor) {
    DialerPhoneNumber number;
    try {
      number = DialerPhoneNumber.parseFrom(coalescedAnnotatedCallLogCursor.getBlob(NUMBER));
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException("Couldn't parse DialerPhoneNumber bytes");
    }

    CoalescedIds coalescedIds;
    try {
      coalescedIds = CoalescedIds.parseFrom(coalescedAnnotatedCallLogCursor.getBlob(COALESCED_IDS));
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException("Couldn't parse CoalescedIds bytes");
    }

    NumberAttributes numberAttributes;
    try {
      numberAttributes =
          NumberAttributes.parseFrom(coalescedAnnotatedCallLogCursor.getBlob(NUMBER_ATTRIBUTES));
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException("Couldn't parse NumberAttributes bytes");
    }

    CoalescedRow.Builder coalescedRowBuilder =
        CoalescedRow.newBuilder()
            .setId(coalescedAnnotatedCallLogCursor.getLong(ID))
            .setTimestamp(coalescedAnnotatedCallLogCursor.getLong(TIMESTAMP))
            .setNumber(number)
            .setNumberPresentation(coalescedAnnotatedCallLogCursor.getInt(NUMBER_PRESENTATION))
            .setIsRead(coalescedAnnotatedCallLogCursor.getInt(IS_READ) == 1)
            .setIsNew(coalescedAnnotatedCallLogCursor.getInt(NEW) == 1)
            .setFeatures(coalescedAnnotatedCallLogCursor.getInt(FEATURES))
            .setCallType(coalescedAnnotatedCallLogCursor.getInt(CALL_TYPE))
            .setNumberAttributes(numberAttributes)
            .setIsVoicemailCall(coalescedAnnotatedCallLogCursor.getInt(IS_VOICEMAIL_CALL) == 1)
            .setCoalescedIds(coalescedIds);

    String formattedNumber = coalescedAnnotatedCallLogCursor.getString(FORMATTED_NUMBER);
    if (!TextUtils.isEmpty(formattedNumber)) {
      coalescedRowBuilder.setFormattedNumber(formattedNumber);
    }

    String geocodedLocation = coalescedAnnotatedCallLogCursor.getString(GEOCODED_LOCATION);
    if (!TextUtils.isEmpty(geocodedLocation)) {
      coalescedRowBuilder.setGeocodedLocation(geocodedLocation);
    }

    String phoneAccountComponentName =
        coalescedAnnotatedCallLogCursor.getString(PHONE_ACCOUNT_COMPONENT_NAME);
    if (!TextUtils.isEmpty(phoneAccountComponentName)) {
      coalescedRowBuilder.setPhoneAccountComponentName(
          coalescedAnnotatedCallLogCursor.getString(PHONE_ACCOUNT_COMPONENT_NAME));
    }

    String phoneAccountId = coalescedAnnotatedCallLogCursor.getString(PHONE_ACCOUNT_ID);
    if (!TextUtils.isEmpty(phoneAccountId)) {
      coalescedRowBuilder.setPhoneAccountId(phoneAccountId);
    }

    String voicemailCallTag = coalescedAnnotatedCallLogCursor.getString(VOICEMAIL_CALL_TAG);
    if (!TextUtils.isEmpty(voicemailCallTag)) {
      coalescedRowBuilder.setVoicemailCallTag(voicemailCallTag);
    }

    return coalescedRowBuilder.build();
  }

  /**
   * Returns the timestamp at the provided cursor's current position.
   *
   * <p>The provided cursor should be one for {@link CoalescedAnnotatedCallLog}.
   */
  public static long getTimestamp(Cursor coalescedAnnotatedCallLogCursor) {
    return coalescedAnnotatedCallLogCursor.getLong(TIMESTAMP);
  }
}
