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
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.annotation.WorkerThread;
import com.android.dialer.calllog.database.CallLogDatabaseComponent;
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.calllog.datasources.CallLogMutations;
import com.android.dialer.calllog.datasources.DataSources;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.inject.ApplicationContext;
import javax.inject.Inject;

/**
 * Worker which brings the annotated call log up to date, if necessary.
 *
 * <p>Accepts a boolean which indicates if the dirty check should be skipped.
 */
public class RefreshAnnotatedCallLogWorker implements Worker<Boolean, Void> {

  private final Context appContext;
  private final DataSources dataSources;

  @Inject
  RefreshAnnotatedCallLogWorker(@ApplicationContext Context appContext, DataSources dataSources) {
    this.appContext = appContext;
    this.dataSources = dataSources;
  }

  @Override
  public Void doInBackground(Boolean skipDirtyCheck)
      throws RemoteException, OperationApplicationException {
    LogUtil.enterBlock("RefreshAnnotatedCallLogWorker.doInBackground");

    long startTime = System.currentTimeMillis();
    checkDirtyAndRebuildIfNecessary(appContext, skipDirtyCheck);
    LogUtil.i(
        "RefreshAnnotatedCallLogWorker.doInBackground",
        "took %dms",
        System.currentTimeMillis() - startTime);
    return null;
  }

  @WorkerThread
  private void checkDirtyAndRebuildIfNecessary(Context appContext, boolean skipDirtyCheck)
      throws RemoteException, OperationApplicationException {
    Assert.isWorkerThread();

    long startTime = System.currentTimeMillis();

    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext);
    // Default to true. If the pref doesn't exist, the annotated call log hasn't been created and
    // we just skip isDirty checks and force a rebuild.
    boolean forceRebuildPrefValue =
        sharedPreferences.getBoolean(CallLogFramework.PREF_FORCE_REBUILD, true);
    if (forceRebuildPrefValue) {
      LogUtil.i(
          "RefreshAnnotatedCallLogWorker.checkDirtyAndRebuildIfNecessary",
          "annotated call log has been marked dirty or does not exist");
    }

    boolean isDirty = skipDirtyCheck || forceRebuildPrefValue || isDirty(appContext);

    LogUtil.i(
        "RefreshAnnotatedCallLogWorker.checkDirtyAndRebuildIfNecessary",
        "isDirty took: %dms",
        System.currentTimeMillis() - startTime);
    if (isDirty) {
      startTime = System.currentTimeMillis();
      rebuild(appContext);
      LogUtil.i(
          "RefreshAnnotatedCallLogWorker.checkDirtyAndRebuildIfNecessary",
          "rebuild took: %dms",
          System.currentTimeMillis() - startTime);
    }
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
  private void rebuild(Context appContext) throws RemoteException, OperationApplicationException {
    Assert.isWorkerThread();

    CallLogMutations mutations = new CallLogMutations();

    // System call log data source must go first!
    CallLogDataSource systemCallLogDataSource = dataSources.getSystemCallLogDataSource();
    String dataSourceName = getName(systemCallLogDataSource);
    LogUtil.i("RefreshAnnotatedCallLogWorker.rebuild", "filling %s", dataSourceName);
    long startTime = System.currentTimeMillis();
    systemCallLogDataSource.fill(appContext, mutations);
    LogUtil.i(
        "RefreshAnnotatedCallLogWorker.rebuild",
        "%s.fill took: %dms",
        dataSourceName,
        System.currentTimeMillis() - startTime);

    for (CallLogDataSource dataSource : dataSources.getDataSourcesExcludingSystemCallLog()) {
      dataSourceName = getName(dataSource);
      LogUtil.i("RefreshAnnotatedCallLogWorker.rebuild", "filling %s", dataSourceName);
      startTime = System.currentTimeMillis();
      dataSource.fill(appContext, mutations);
      LogUtil.i(
          "CallLogFramework.rebuild",
          "%s.fill took: %dms",
          dataSourceName,
          System.currentTimeMillis() - startTime);
    }
    LogUtil.i("RefreshAnnotatedCallLogWorker.rebuild", "applying mutations to database");
    startTime = System.currentTimeMillis();
    CallLogDatabaseComponent.get(appContext)
        .mutationApplier()
        .applyToDatabase(mutations, appContext);
    LogUtil.i(
        "RefreshAnnotatedCallLogWorker.rebuild",
        "applyToDatabase took: %dms",
        System.currentTimeMillis() - startTime);

    for (CallLogDataSource dataSource : dataSources.getDataSourcesIncludingSystemCallLog()) {
      dataSourceName = getName(dataSource);
      LogUtil.i("RefreshAnnotatedCallLogWorker.rebuild", "onSuccessfulFill'ing %s", dataSourceName);
      startTime = System.currentTimeMillis();
      dataSource.onSuccessfulFill(appContext);
      LogUtil.i(
          "CallLogFramework.rebuild",
          "%s.onSuccessfulFill took: %dms",
          dataSourceName,
          System.currentTimeMillis() - startTime);
    }

    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext);
    sharedPreferences.edit().putBoolean(CallLogFramework.PREF_FORCE_REBUILD, false).apply();
  }

  private static String getName(CallLogDataSource dataSource) {
    return dataSource.getClass().getSimpleName();
  }
}
