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

/** Convenience class for aggregating row values. */
public class RowCombiner {
  private final List<ContentValues> individualRowsSortedByTimestampDesc;
  private final ContentValues combinedRow = new ContentValues();

  public RowCombiner(List<ContentValues> individualRowsSortedByTimestampDesc) {
    Assert.checkArgument(!individualRowsSortedByTimestampDesc.isEmpty());
    this.individualRowsSortedByTimestampDesc = individualRowsSortedByTimestampDesc;
  }

  /** Use the most recent value for the specified column. */
  public RowCombiner useMostRecentLong(String columnName) {
    combinedRow.put(columnName, individualRowsSortedByTimestampDesc.get(0).getAsLong(columnName));
    return this;
  }

  /** Asserts that all column values for the given column name are the same, and uses it. */
  public RowCombiner useSingleValueString(String columnName) {
    Iterator<ContentValues> iterator = individualRowsSortedByTimestampDesc.iterator();
    String singleValue = iterator.next().getAsString(columnName);
    while (iterator.hasNext()) {
      Assert.checkState(iterator.next().getAsString(columnName).equals(singleValue));
    }
    combinedRow.put(columnName, singleValue);
    return this;
  }

  public ContentValues combine() {
    return combinedRow;
  }
}
