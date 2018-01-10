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
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.CoalescedIds;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.CoalescedAnnotatedCallLog;
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.calllog.datasources.DataSources;
import com.android.dialer.calllogutils.PhoneAccountUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.phonenumberproto.DialerPhoneNumberUtil;
import com.google.common.base.Preconditions;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;

/**
 * Coalesces call log rows by combining some adjacent rows.
 *
 * <p>Applies the logic that determines which adjacent rows should be coalesced, and then delegates
 * to each data source to determine how individual columns should be aggregated.
 */
public class Coalescer {
  private final DataSources dataSources;

  @Inject
  Coalescer(DataSources dataSources) {
    this.dataSources = dataSources;
  }

  /**
   * Reads the entire {@link AnnotatedCallLog} database into memory from the provided {@code
   * allAnnotatedCallLog} parameter and then builds and returns a new {@link MatrixCursor} which is
   * the result of combining adjacent rows which should be collapsed for display purposes.
   *
   * @param allAnnotatedCallLogRowsSortedByTimestampDesc all {@link AnnotatedCallLog} rows, sorted
   *     by timestamp descending
   * @return a new {@link MatrixCursor} containing the {@link CoalescedAnnotatedCallLog} rows to
   *     display
   */
  @WorkerThread
  @NonNull
  Cursor coalesce(@NonNull Cursor allAnnotatedCallLogRowsSortedByTimestampDesc) {
    Assert.isWorkerThread();

    // Note: This method relies on rowsShouldBeCombined to determine which rows should be combined,
    // but delegates to data sources to actually aggregate column values.

    DialerPhoneNumberUtil dialerPhoneNumberUtil =
        new DialerPhoneNumberUtil(PhoneNumberUtil.getInstance());

    MatrixCursor allCoalescedRowsMatrixCursor =
        new MatrixCursor(
            CoalescedAnnotatedCallLog.ALL_COLUMNS,
            Assert.isNotNull(allAnnotatedCallLogRowsSortedByTimestampDesc).getCount());

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
        PhoneAccountUtils.getAccount(
            row1.getAsString(AnnotatedCallLog.PHONE_ACCOUNT_COMPONENT_NAME),
            row1.getAsString(AnnotatedCallLog.PHONE_ACCOUNT_ID));
    PhoneAccountHandle phoneAccount2 =
        PhoneAccountUtils.getAccount(
            row2.getAsString(AnnotatedCallLog.PHONE_ACCOUNT_COMPONENT_NAME),
            row2.getAsString(AnnotatedCallLog.PHONE_ACCOUNT_ID));

    if (!Objects.equals(phoneAccount1, phoneAccount2)) {
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

    if (!number1.hasDialerInternalPhoneNumber() || !number2.hasDialerInternalPhoneNumber()) {
      // An empty number should not be combined with any other number.
      return false;
    }

    if (!meetsAssistedDialingCriteria(row1, row2)) {
      return false;
    }
    return dialerPhoneNumberUtil.isExactMatch(number1, number2);
  }

  /**
   * Returns a boolean indicating whether or not FEATURES_ASSISTED_DIALING is mutually exclusive
   * between two rows.
   */
  private static boolean meetsAssistedDialingCriteria(ContentValues row1, ContentValues row2) {
    int row1Assisted =
        row1.getAsInteger(AnnotatedCallLog.FEATURES)
            & TelephonyManagerCompat.FEATURES_ASSISTED_DIALING;
    int row2Assisted =
        row2.getAsInteger(AnnotatedCallLog.FEATURES)
            & TelephonyManagerCompat.FEATURES_ASSISTED_DIALING;

    // FEATURES_ASSISTED_DIALING should not be combined with calls that are
    // !FEATURES_ASSISTED_DIALING
    return row1Assisted == row2Assisted;
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
}
