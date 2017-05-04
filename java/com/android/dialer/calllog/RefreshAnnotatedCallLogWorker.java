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

package com.android.dialer.calllog;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.WorkerThread;
import com.android.dialer.calllog.database.AnnotatedCallLog;
import com.android.dialer.calllog.database.CallLogMutations;
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import javax.inject.Inject;

/**
 * Worker which brings the annotated call log up to date, if necessary.
 *
 * <p>Accepts a boolean which indicates if the dirty check should be skipped, and returns true if
 * the annotated call log was updated.
 */
public class RefreshAnnotatedCallLogWorker implements Worker<Boolean, Boolean> {

  private final Context appContext;
  private final DataSources dataSources;

  @Inject
  public RefreshAnnotatedCallLogWorker(Context appContext, DataSources dataSources) {
    this.appContext = appContext;
    this.dataSources = dataSources;
  }

  @Override
  public Boolean doInBackground(Boolean skipDirtyCheck) {
    LogUtil.enterBlock("RefreshAnnotatedCallLogWorker.doInBackgroundFallible");

    long startTime = System.currentTimeMillis();
    boolean annotatedCallLogUpdated = checkDirtyAndRebuildIfNecessary(appContext, skipDirtyCheck);
    LogUtil.i(
        "RefreshAnnotatedCallLogWorker.doInBackgroundFallible",
        "updated? %s, took %dms",
        annotatedCallLogUpdated,
        System.currentTimeMillis() - startTime);
    return annotatedCallLogUpdated;
  }

  @WorkerThread
  private boolean checkDirtyAndRebuildIfNecessary(Context appContext, boolean skipDirtyCheck) {
    Assert.isWorkerThread();

    long startTime = System.currentTimeMillis();

    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext);
    long lastRebuildTimeMillis =
        sharedPreferences.getLong(CallLogFramework.PREF_LAST_REBUILD_TIMESTAMP_MILLIS, 0);
    if (lastRebuildTimeMillis == 0) {
      LogUtil.i(
          "RefreshAnnotatedCallLogWorker.checkDirtyAndRebuildIfNecessary",
          "annotated call log has never been built, marking it dirty");
    }
    boolean forceRebuildPrefValue =
        sharedPreferences.getBoolean(CallLogFramework.PREF_FORCE_REBUILD, false);
    if (forceRebuildPrefValue) {
      LogUtil.i(
          "RefreshAnnotatedCallLogWorker.checkDirtyAndRebuildIfNecessary",
          "call log has been marked dirty");
    }

    boolean isDirty =
        lastRebuildTimeMillis == 0
            || skipDirtyCheck
            || forceRebuildPrefValue
            || isDirty(appContext);
    LogUtil.i(
        "RefreshAnnotatedCallLogWorker.checkDirtyAndRebuildIfNecessary",
        "isDirty took: %dms",
        System.currentTimeMillis() - startTime);
    if (isDirty) {
      startTime = System.currentTimeMillis();
      rebuild(appContext, lastRebuildTimeMillis);
      LogUtil.i(
          "RefreshAnnotatedCallLogWorker.checkDirtyAndRebuildIfNecessary",
          "rebuild took: %dms",
          System.currentTimeMillis() - startTime);
      return true; // Annotated call log was updated.
    }
    return false; // Annotated call log was not updated.
  }

  @WorkerThread
  private boolean isDirty(Context appContext) {
    Assert.isWorkerThread();

    for (CallLogDataSource dataSource : dataSources.getDataSourcesIncludingSystemCallLog()) {
      String dataSourceName = getName(dataSource);
      long startTime = System.currentTimeMillis();
      LogUtil.i("RefreshAnnotatedCallLogWorker.isDirty", "running isDirty for %s", dataSourceName);
      boolean isDirty = dataSource.isDirty(appContext);
      LogUtil.i(
          "RefreshAnnotatedCallLogWorker.isDirty",
          "%s.isDirty returned %b in %dms",
          dataSourceName,
          isDirty,
          System.currentTimeMillis() - startTime);
      if (isDirty) {
        return true;
      }
    }
    return false;
  }

  @TargetApi(Build.VERSION_CODES.M) // Uses try-with-resources
  @WorkerThread
  private void rebuild(Context appContext, long lastRebuildTimeMillis) {
    Assert.isWorkerThread();

    // TODO: Start a transaction?
    try (SQLiteDatabase database = AnnotatedCallLog.getWritableDatabase(appContext)) {

      CallLogMutations mutations = new CallLogMutations();

      // System call log data source must go first!
      CallLogDataSource systemCallLogDataSource = dataSources.getSystemCallLogDataSource();
      String dataSourceName = getName(systemCallLogDataSource);
      LogUtil.i("RefreshAnnotatedCallLogWorker.rebuild", "filling %s", dataSourceName);
      long startTime = System.currentTimeMillis();
      systemCallLogDataSource.fill(appContext, database, lastRebuildTimeMillis, mutations);
      LogUtil.i(
          "RefreshAnnotatedCallLogWorker.rebuild",
          "%s.fill took: %dms",
          dataSourceName,
          System.currentTimeMillis() - startTime);

      for (CallLogDataSource dataSource : dataSources.getDataSourcesExcludingSystemCallLog()) {
        dataSourceName = getName(dataSource);
        LogUtil.i("RefreshAnnotatedCallLogWorker.rebuild", "filling %s", dataSourceName);
        startTime = System.currentTimeMillis();
        dataSource.fill(appContext, database, lastRebuildTimeMillis, mutations);
        LogUtil.i(
            "CallLogFramework.rebuild",
            "%s.fill took: %dms",
            dataSourceName,
            System.currentTimeMillis() - startTime);
      }
      LogUtil.i("RefreshAnnotatedCallLogWorker.rebuild", "applying mutations to database");
      startTime = System.currentTimeMillis();
      mutations.applyToDatabase(database);
      LogUtil.i(
          "RefreshAnnotatedCallLogWorker.rebuild",
          "applyToDatabase took: %dms",
          System.currentTimeMillis() - startTime);
    }

    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext);
    sharedPreferences
        .edit()
        .putBoolean(CallLogFramework.PREF_FORCE_REBUILD, false)
        .putLong(CallLogFramework.PREF_LAST_REBUILD_TIMESTAMP_MILLIS, System.currentTimeMillis())
        .commit();
  }

  private static String getName(CallLogDataSource dataSource) {
    return dataSource.getClass().getSimpleName();
  }
}
