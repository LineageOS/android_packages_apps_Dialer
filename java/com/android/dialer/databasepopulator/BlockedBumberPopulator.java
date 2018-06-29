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

package com.android.dialer.databasepopulator;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.os.RemoteException;
import android.provider.BlockedNumberContract;
import android.provider.BlockedNumberContract.BlockedNumbers;
import android.support.annotation.NonNull;
import com.android.dialer.common.Assert;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Populates the device database with blocked number entries. */
public class BlockedBumberPopulator {

  private static final List<ContentValues> values =
      Arrays.asList(
          createContentValuesWithNumber("123456789"), createContentValuesWithNumber("987654321"));

  public static void populateBlockedNumber(@NonNull Context context) {
    ArrayList<ContentProviderOperation> operations = new ArrayList<>();
    for (ContentValues value : values) {
      operations.add(
          ContentProviderOperation.newInsert(BlockedNumbers.CONTENT_URI)
              .withValues(value)
              .withYieldAllowed(true)
              .build());
    }
    try {
      context.getContentResolver().applyBatch(BlockedNumberContract.AUTHORITY, operations);
    } catch (RemoteException | OperationApplicationException e) {
      Assert.fail("error adding block number entries: " + e);
    }
  }

  public static void deleteBlockedNumbers(@NonNull Context context) {
    // clean BlockedNumbers db
    context.getContentResolver().delete(BlockedNumbers.CONTENT_URI, null, null);
  }

  private static ContentValues createContentValuesWithNumber(String number) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number);
    return contentValues;
  }
}
