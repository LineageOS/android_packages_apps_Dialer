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
package com.android.dialer.calllog.datasources.util;

import android.content.ContentValues;
import com.android.dialer.common.Assert;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/** Convenience class for aggregating row values. */
public class RowCombiner {
  private final List<ContentValues> individualRowsSortedByTimestampDesc;
  private final ContentValues combinedRow = new ContentValues();

  public RowCombiner(List<ContentValues> individualRowsSortedByTimestampDesc) {
    Assert.checkArgument(!individualRowsSortedByTimestampDesc.isEmpty());
    this.individualRowsSortedByTimestampDesc = individualRowsSortedByTimestampDesc;
  }

  /** Use the most recent value for the specified column. */
  public RowCombiner useMostRecentInt(String columnName) {
    combinedRow.put(
        columnName, individualRowsSortedByTimestampDesc.get(0).getAsInteger(columnName));
    return this;
  }

  /** Use the most recent value for the specified column. */
  public RowCombiner useMostRecentLong(String columnName) {
    combinedRow.put(columnName, individualRowsSortedByTimestampDesc.get(0).getAsLong(columnName));
    return this;
  }

  /** Use the most recent value for the specified column. */
  public RowCombiner useMostRecentString(String columnName) {
    combinedRow.put(columnName, individualRowsSortedByTimestampDesc.get(0).getAsString(columnName));
    return this;
  }

  public RowCombiner useMostRecentBlob(String columnName) {
    combinedRow.put(
        columnName, individualRowsSortedByTimestampDesc.get(0).getAsByteArray(columnName));
    return this;
  }

  /** Asserts that all column values for the given column name are the same, and uses it. */
  public RowCombiner useSingleValueString(String columnName) {
    Iterator<ContentValues> iterator = individualRowsSortedByTimestampDesc.iterator();
    String singleValue = iterator.next().getAsString(columnName);
    while (iterator.hasNext()) {
      String current = iterator.next().getAsString(columnName);
      Assert.checkState(Objects.equals(singleValue, current), "Values different for " + columnName);
    }
    combinedRow.put(columnName, singleValue);
    return this;
  }

  /** Asserts that all column values for the given column name are the same, and uses it. */
  public RowCombiner useSingleValueLong(String columnName) {
    Iterator<ContentValues> iterator = individualRowsSortedByTimestampDesc.iterator();
    Long singleValue = iterator.next().getAsLong(columnName);
    while (iterator.hasNext()) {
      Long current = iterator.next().getAsLong(columnName);
      Assert.checkState(Objects.equals(singleValue, current), "Values different for " + columnName);
    }
    combinedRow.put(columnName, singleValue);
    return this;
  }

  /** Performs a bitwise OR on the specified column and yields the result. */
  public RowCombiner bitwiseOr(String columnName) {
    int combinedValue = 0;
    for (ContentValues val : individualRowsSortedByTimestampDesc) {
      combinedValue |= val.getAsInteger(columnName);
    }
    combinedRow.put(columnName, combinedValue);
    return this;
  }

  public ContentValues combine() {
    return combinedRow;
  }
}
