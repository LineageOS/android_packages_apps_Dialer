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
import android.support.annotation.WorkerThread;
import com.android.dialer.calllog.database.CallLogDatabaseComponent;
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.calllog.datasources.CallLogMutations;
import com.android.dialer.calllog.datasources.DataSources;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.UiSerial;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.storage.Unencrypted;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/** Brings the annotated call log up to date, if necessary. */
public class RefreshAnnotatedCallLogWorker {

  /*
   * This is a reasonable time that it might take between related call log writes, that also
   * shouldn't slow down single-writes too much. For example, when populating the database using
   * the simulator, using this value results in ~6 refresh cycles (on a release build) to write 120
   * call log entries.
   */
  private static final long WAIT_MILLIS = 100L;

  private final Context appContext;
  private final DataSources dataSources;
  private final SharedPreferences sharedPreferences;
  private final ListeningScheduledExecutorService listeningScheduledExecutorService;
  private ListenableScheduledFuture<Void> scheduledFuture;

  @Inject
  RefreshAnnotatedCallLogWorker(
      @ApplicationContext Context appContext,
      DataSources dataSources,
      @Unencrypted SharedPreferences sharedPreferences,
      @UiSerial ScheduledExecutorService serialUiExecutorService) {
    this.appContext = appContext;
    this.dataSources = dataSources;
    this.sharedPreferences = sharedPreferences;
    this.listeningScheduledExecutorService =
        MoreExecutors.listeningDecorator(serialUiExecutorService);
  }

  /** Checks if the annotated call log is dirty and refreshes it if necessary. */
  public ListenableScheduledFuture<Void> refreshWithDirtyCheck() {
    return refresh(true);
  }

  /** Refreshes the annotated call log, bypassing dirty checks. */
  public ListenableScheduledFuture<Void> refreshWithoutDirtyCheck() {
    return refresh(false);
  }

  private ListenableScheduledFuture<Void> refresh(boolean checkDirty) {
    if (scheduledFuture != null) {
      LogUtil.i("RefreshAnnotatedCallLogWorker.refresh", "cancelling waiting task");
      scheduledFuture.cancel(false /* mayInterrupt */);
    }
    scheduledFuture =
        listeningScheduledExecutorService.schedule(
            () -> doInBackground(checkDirty), WAIT_MILLIS, TimeUnit.MILLISECONDS);
    return scheduledFuture;
  }

  @WorkerThread
  private Void doInBackground(boolean checkDirty)
      throws RemoteException, OperationApplicationException {
    LogUtil.enterBlock("RefreshAnnotatedCallLogWorker.doInBackground");

    long startTime = System.currentTimeMillis();
    checkDirtyAndRebuildIfNecessary(appContext, checkDirty);
    LogUtil.i(
        "RefreshAnnotatedCallLogWorker.doInBackground",
        "took %dms",
        System.currentTimeMillis() - startTime);
    return null;
  }

  @WorkerThread
  private void checkDirtyAndRebuildIfNecessary(Context appContext, boolean checkDirty)
      throws RemoteException, OperationApplicationException {
    Assert.isWorkerThread();

    long startTime = System.currentTimeMillis();

    // Default to true. If the pref doesn't exist, the annotated call log hasn't been created and
    // we just skip isDirty checks and force a rebuild.
    boolean forceRebuildPrefValue =
        sharedPreferences.getBoolean(CallLogFramework.PREF_FORCE_REBUILD, true);
    if (forceRebuildPrefValue) {
      LogUtil.i(
          "RefreshAnnotatedCallLogWorker.checkDirtyAndRebuildIfNecessary",
          "annotated call log has been marked dirty or does not exist");
    }

    boolean isDirty = !checkDirty || forceRebuildPrefValue || isDirty(appContext);

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
    sharedPreferences.edit().putBoolean(CallLogFramework.PREF_FORCE_REBUILD, false).apply();
  }

  private static String getName(CallLogDataSource dataSource) {
    return dataSource.getClass().getSimpleName();
  }
}
