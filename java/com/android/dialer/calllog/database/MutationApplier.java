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
import android.text.TextUtils;

import androidx.annotation.WorkerThread;

import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.calllog.datasources.CallLogMutations;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import javax.inject.Inject;

/** Applies {@link CallLogMutations} to the annotated call log. */
public class MutationApplier {

  private final ListeningExecutorService backgroundExecutorService;

  @Inject
  public MutationApplier(@BackgroundExecutor ListeningExecutorService backgroundExecutorService) {
    this.backgroundExecutorService = backgroundExecutorService;
  }

  /** Applies the provided {@link CallLogMutations} to the annotated call log. */
  public ListenableFuture<Void> applyToDatabase(CallLogMutations mutations, Context appContext) {
    if (mutations.isEmpty()) {
      return Futures.immediateFuture(null);
    }
    return backgroundExecutorService.submit(
        () -> {
          applyToDatabaseInternal(mutations, appContext);
          return null;
        });
  }

  @WorkerThread
  private void applyToDatabaseInternal(CallLogMutations mutations, Context appContext)
      throws RemoteException, OperationApplicationException {
    Assert.isWorkerThread();

    ArrayList<ContentProviderOperation> operations = new ArrayList<>();

    if (!mutations.getInserts().isEmpty()) {
      LogUtil.i(
          "MutationApplier.applyToDatabase", "inserting %d rows", mutations.getInserts().size());
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
          "MutationApplier.applyToDatabase", "updating %d rows", mutations.getUpdates().size());
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
          "MutationApplier.applyToDatabase", "deleting %d rows", mutations.getDeletes().size());

      // Batch the deletes into chunks of 999, the maximum size for SQLite selection args.
      Iterable<List<Long>> batches = Iterables.partition(mutations.getDeletes(), 999);
      for (List<Long> idsInBatch : batches) {
        String[] questionMarks = new String[idsInBatch.size()];
        Arrays.fill(questionMarks, "?");

        String whereClause =
            (AnnotatedCallLog._ID + " in (") + TextUtils.join(",", questionMarks) + ")";

        String[] whereArgs = new String[idsInBatch.size()];
        int i = 0;
        for (long id : idsInBatch) {
          whereArgs[i++] = String.valueOf(id);
        }

        operations.add(
            ContentProviderOperation.newDelete(AnnotatedCallLog.CONTENT_URI)
                .withSelection(whereClause, whereArgs)
                .build());
      }
    }

    appContext.getContentResolver().applyBatch(AnnotatedCallLogContract.AUTHORITY, operations);
  }
}
