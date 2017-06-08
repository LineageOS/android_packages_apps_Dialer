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

package com.android.dialer.calllog.datasources.systemcalllog;

import android.Manifest.permission;
import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.ArraySet;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.calllog.datasources.CallLogMutations;
import com.android.dialer.calllog.datasources.util.RowCombiner;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.util.PermissionsUtil;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

/**
 * Responsible for defining the rows in the annotated call log and maintaining the columns in it
 * which are derived from the system call log.
 */
@SuppressWarnings("MissingPermission")
public class SystemCallLogDataSource implements CallLogDataSource {

  @VisibleForTesting
  static final String PREF_LAST_TIMESTAMP_PROCESSED = "systemCallLogLastTimestampProcessed";

  @Nullable private Long lastTimestampProcessed;

  @Inject
  public SystemCallLogDataSource() {}

  @MainThread
  @Override
  public void registerContentObservers(
      Context appContext, ContentObserverCallbacks contentObserverCallbacks) {
    Assert.isMainThread();

    LogUtil.enterBlock("SystemCallLogDataSource.registerContentObservers");

    if (!PermissionsUtil.hasCallLogReadPermissions(appContext)) {
      LogUtil.i("SystemCallLogDataSource.registerContentObservers", "no call log permissions");
      return;
    }

    appContext
        .getContentResolver()
        .registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true,
            new CallLogObserver(
                ThreadUtil.getUiThreadHandler(), appContext, contentObserverCallbacks));
  }

  @WorkerThread
  @Override
  public boolean isDirty(Context appContext) {
    Assert.isWorkerThread();

    /*
     * The system call log has a last updated timestamp, but deletes are physical (the "deleted"
     * column is unused). This means that we can't detect deletes without scanning the entire table,
     * which would be too slow. So, we just rely on content observers to trigger rebuilds when any
     * change is made to the system call log.
     */
    return false;
  }

  @WorkerThread
  @Override
  public void fill(Context appContext, CallLogMutations mutations) {
    Assert.isWorkerThread();

    lastTimestampProcessed = null;

    if (!PermissionsUtil.hasPermission(appContext, permission.READ_CALL_LOG)) {
      LogUtil.i("SystemCallLogDataSource.fill", "no call log permissions");
      return;
    }

    // This data source should always run first so the mutations should always be empty.
    Assert.checkArgument(mutations.isEmpty());

    Set<Long> annotatedCallLogIds = getAnnotatedCallLogIds(appContext);

    LogUtil.i(
        "SystemCallLogDataSource.fill",
        "found %d existing annotated call log ids",
        annotatedCallLogIds.size());

    handleInsertsAndUpdates(appContext, mutations, annotatedCallLogIds);
    handleDeletes(appContext, annotatedCallLogIds, mutations);
  }

  @WorkerThread
  @Override
  public void onSuccessfulFill(Context appContext) {
    // If a fill operation was a no-op, lastTimestampProcessed could still be null.
    if (lastTimestampProcessed != null) {
      PreferenceManager.getDefaultSharedPreferences(appContext)
          .edit()
          .putLong(PREF_LAST_TIMESTAMP_PROCESSED, lastTimestampProcessed)
          .commit();
    }
  }

  @Override
  public ContentValues coalesce(List<ContentValues> individualRowsSortedByTimestampDesc) {
    // TODO: Complete implementation.
    return new RowCombiner(individualRowsSortedByTimestampDesc)
        .useMostRecentLong(AnnotatedCallLog.TIMESTAMP)
        .combine();
  }

  @TargetApi(Build.VERSION_CODES.M) // Uses try-with-resources
  private void handleInsertsAndUpdates(
      Context appContext, CallLogMutations mutations, Set<Long> existingAnnotatedCallLogIds) {
    long previousTimestampProcessed =
        PreferenceManager.getDefaultSharedPreferences(appContext)
            .getLong(PREF_LAST_TIMESTAMP_PROCESSED, 0L);

    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                Calls.CONTENT_URI, // Excludes voicemail
                new String[] {Calls._ID, Calls.DATE, Calls.LAST_MODIFIED},
                Calls.LAST_MODIFIED + " > ?",
                new String[] {String.valueOf(previousTimestampProcessed)},
                Calls.LAST_MODIFIED + " DESC LIMIT 1000")) {

      if (cursor == null) {
        LogUtil.e("SystemCallLogDataSource.handleInsertsAndUpdates", "null cursor");
        return;
      }

      LogUtil.i(
          "SystemCallLogDataSource.handleInsertsAndUpdates",
          "found %d entries to insert/update",
          cursor.getCount());

      if (cursor.moveToFirst()) {
        int idColumn = cursor.getColumnIndexOrThrow(Calls._ID);
        int dateColumn = cursor.getColumnIndexOrThrow(Calls.DATE);
        int lastModifiedColumn = cursor.getColumnIndexOrThrow(Calls.LAST_MODIFIED);

        // The cursor orders by LAST_MODIFIED DESC, so the first result is the most recent timestamp
        // processed.
        lastTimestampProcessed = cursor.getLong(lastModifiedColumn);
        do {
          long id = cursor.getLong(idColumn);
          long date = cursor.getLong(dateColumn);

          ContentValues contentValues = new ContentValues();
          contentValues.put(AnnotatedCallLog.TIMESTAMP, date);

          if (existingAnnotatedCallLogIds.contains(id)) {
            mutations.update(id, contentValues);
          } else {
            mutations.insert(id, contentValues);
          }
        } while (cursor.moveToNext());
      } // else no new results, do nothing.
    }
  }

  private static void handleDeletes(
      Context appContext, Set<Long> existingAnnotatedCallLogIds, CallLogMutations mutations) {
    Set<Long> systemCallLogIds =
        getIdsFromSystemCallLogThatMatch(appContext, existingAnnotatedCallLogIds);
    LogUtil.i(
        "SystemCallLogDataSource.handleDeletes",
        "found %d entries in system call log",
        systemCallLogIds.size());
    Set<Long> idsInAnnotatedCallLogNoLongerInSystemCallLog = new ArraySet<>();
    idsInAnnotatedCallLogNoLongerInSystemCallLog.addAll(existingAnnotatedCallLogIds);
    idsInAnnotatedCallLogNoLongerInSystemCallLog.removeAll(systemCallLogIds);

    LogUtil.i(
        "SystemCallLogDataSource.handleDeletes",
        "found %d call log entries to remove",
        idsInAnnotatedCallLogNoLongerInSystemCallLog.size());

    for (long id : idsInAnnotatedCallLogNoLongerInSystemCallLog) {
      mutations.delete(id);
    }
  }

  @TargetApi(Build.VERSION_CODES.M) // Uses try-with-resources
  private static Set<Long> getAnnotatedCallLogIds(Context appContext) {
    ArraySet<Long> ids = new ArraySet<>();

    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                AnnotatedCallLog.CONTENT_URI,
                new String[] {AnnotatedCallLog._ID},
                null,
                null,
                null)) {

      if (cursor == null) {
        LogUtil.e("SystemCallLogDataSource.getAnnotatedCallLogIds", "null cursor");
        return ids;
      }

      if (cursor.moveToFirst()) {
        int idColumn = cursor.getColumnIndexOrThrow(AnnotatedCallLog._ID);
        do {
          ids.add(cursor.getLong(idColumn));
        } while (cursor.moveToNext());
      }
    }
    return ids;
  }

  @TargetApi(Build.VERSION_CODES.M) // Uses try-with-resources
  private static Set<Long> getIdsFromSystemCallLogThatMatch(
      Context appContext, Set<Long> matchingIds) {
    ArraySet<Long> ids = new ArraySet<>();

    String[] questionMarks = new String[matchingIds.size()];
    Arrays.fill(questionMarks, "?");
    String whereClause = (Calls._ID + " in (") + TextUtils.join(",", questionMarks) + ")";
    String[] whereArgs = new String[matchingIds.size()];
    int i = 0;
    for (long id : matchingIds) {
      whereArgs[i++] = String.valueOf(id);
    }

    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(Calls.CONTENT_URI, new String[] {Calls._ID}, whereClause, whereArgs, null)) {

      if (cursor == null) {
        LogUtil.e("SystemCallLogDataSource.getIdsFromSystemCallLog", "null cursor");
        return ids;
      }

      if (cursor.moveToFirst()) {
        int idColumn = cursor.getColumnIndexOrThrow(Calls._ID);
        do {
          ids.add(cursor.getLong(idColumn));
        } while (cursor.moveToNext());
      }
      return ids;
    }
  }

  private static class CallLogObserver extends ContentObserver {
    private final Context appContext;
    private final ContentObserverCallbacks contentObserverCallbacks;

    CallLogObserver(
        Handler handler, Context appContext, ContentObserverCallbacks contentObserverCallbacks) {
      super(handler);
      this.appContext = appContext;
      this.contentObserverCallbacks = contentObserverCallbacks;
    }

    @MainThread
    @Override
    public void onChange(boolean selfChange, Uri uri) {
      Assert.isMainThread();
      LogUtil.enterBlock("SystemCallLogDataSource.CallLogObserver.onChange");
      super.onChange(selfChange, uri);

      /*
       * The system call log has a last updated timestamp, but deletes are physical (the "deleted"
       * column is unused). This means that we can't detect deletes without scanning the entire
       * table, which would be too slow. So, we just rely on content observers to trigger rebuilds
       * when any change is made to the system call log.
       */
      contentObserverCallbacks.markDirtyAndNotify(appContext);
    }
  }
}
