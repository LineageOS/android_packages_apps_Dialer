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

import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.os.RemoteException;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.calllog.datasources.CallLogMutations;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map.Entry;
import javax.inject.Inject;

/** Applies {@link CallLogMutations} to the annotated call log. */
public class MutationApplier {

  @Inject
  MutationApplier() {}

  /** Applies the provided {@link CallLogMutations} to the annotated call log. */
  @WorkerThread
  public void applyToDatabase(CallLogMutations mutations, Context appContext)
      throws RemoteException, OperationApplicationException {
    Assert.isWorkerThread();

    if (mutations.isEmpty()) {
      return;
    }

    ArrayList<ContentProviderOperation> operations = new ArrayList<>();

    if (!mutations.getInserts().isEmpty()) {
      LogUtil.i(
          "CallLogMutations.applyToDatabase", "inserting %d rows", mutations.getInserts().size());
      for (Entry<Long, ContentValues> entry : mutations.getInserts().entrySet()) {
        long id = entry.getKey();
        ContentValues contentValues = entry.getValue();
        operations.add(
            ContentProviderOperation.newInsert(
                    ContentUris.withAppendedId(AnnotatedCallLog.CONTENT_URI, id))
                .withValues(contentValues)
                .build());
      }
    }

    if (!mutations.getUpdates().isEmpty()) {
      LogUtil.i(
          "CallLogMutations.applyToDatabase", "updating %d rows", mutations.getUpdates().size());
      for (Entry<Long, ContentValues> entry : mutations.getUpdates().entrySet()) {
        long id = entry.getKey();
        ContentValues contentValues = entry.getValue();
        operations.add(
            ContentProviderOperation.newUpdate(
                    ContentUris.withAppendedId(AnnotatedCallLog.CONTENT_URI, id))
                .withValues(contentValues)
                .build());
      }
    }

    if (!mutations.getDeletes().isEmpty()) {
      LogUtil.i(
          "CallLogMutations.applyToDatabase", "deleting %d rows", mutations.getDeletes().size());
      String[] questionMarks = new String[mutations.getDeletes().size()];
      Arrays.fill(questionMarks, "?");

      String whereClause =
          (AnnotatedCallLog._ID + " in (") + TextUtils.join(",", questionMarks) + ")";

      String[] whereArgs = new String[mutations.getDeletes().size()];
      int i = 0;
      for (long id : mutations.getDeletes()) {
        whereArgs[i++] = String.valueOf(id);
      }

      operations.add(
          ContentProviderOperation.newDelete(AnnotatedCallLog.CONTENT_URI)
              .withSelection(whereClause, whereArgs)
              .build());
    }

    appContext.getContentResolver().applyBatch(AnnotatedCallLogContract.AUTHORITY, operations);
  }
}
