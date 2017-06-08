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
import android.database.DatabaseUtils;
import android.database.MatrixCursor;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.CoalescedAnnotatedCallLog;
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.calllog.datasources.DataSources;
import com.android.dialer.common.Assert;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

/**
 * Coalesces call log rows by combining some adjacent rows.
 *
 * <p>Applies the business which logic which determines which adjacent rows should be coalasced, and
 * then delegates to each data source to determine how individual columns should be aggregated.
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

    MatrixCursor allCoalescedRowsMatrixCursor =
        new MatrixCursor(
            CoalescedAnnotatedCallLog.ALL_COLUMNS,
            Assert.isNotNull(allAnnotatedCallLogRowsSortedByTimestampDesc).getCount());

    if (allAnnotatedCallLogRowsSortedByTimestampDesc.moveToFirst()) {
      int coalescedRowId = 0;

      List<ContentValues> currentRowGroup = new ArrayList<>();

      do {
        ContentValues currentRow = new ContentValues();
        DatabaseUtils.cursorRowToContentValues(
            allAnnotatedCallLogRowsSortedByTimestampDesc, currentRow);

        if (currentRowGroup.isEmpty()) {
          currentRowGroup.add(currentRow);
          continue;
        }

        ContentValues previousRow = currentRowGroup.get(currentRowGroup.size() - 1);

        if (!rowsShouldBeCombined(previousRow, currentRow)) {
          ContentValues coalescedRow = coalesceRowsForAllDataSources(currentRowGroup);
          coalescedRow.put(CoalescedAnnotatedCallLog.NUMBER_CALLS, currentRowGroup.size());
          addContentValuesToMatrixCursor(
              coalescedRow, allCoalescedRowsMatrixCursor, coalescedRowId++);
          currentRowGroup.clear();
        }
        currentRowGroup.add(currentRow);
      } while (allAnnotatedCallLogRowsSortedByTimestampDesc.moveToNext());

      // Deal with leftover rows.
      ContentValues coalescedRow = coalesceRowsForAllDataSources(currentRowGroup);
      coalescedRow.put(CoalescedAnnotatedCallLog.NUMBER_CALLS, currentRowGroup.size());
      addContentValuesToMatrixCursor(coalescedRow, allCoalescedRowsMatrixCursor, coalescedRowId);
    }
    return allCoalescedRowsMatrixCursor;
  }

  /**
   * @param row1 a row from {@link AnnotatedCallLog}
   * @param row2 a row from {@link AnnotatedCallLog}
   */
  private static boolean rowsShouldBeCombined(ContentValues row1, ContentValues row2) {
    // TODO: Real implementation.
    return row1.get(AnnotatedCallLog.TIMESTAMP).equals(row2.get(AnnotatedCallLog.TIMESTAMP));
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
