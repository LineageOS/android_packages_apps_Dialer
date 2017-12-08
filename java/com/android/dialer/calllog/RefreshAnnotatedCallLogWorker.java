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

import android.content.Context;
import android.content.SharedPreferences;
import com.android.dialer.calllog.database.CallLogDatabaseComponent;
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.calllog.datasources.CallLogMutations;
import com.android.dialer.calllog.datasources.DataSources;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.common.concurrent.Annotations.LightweightExecutor;
import com.android.dialer.common.concurrent.DialerFutureSerializer;
import com.android.dialer.common.concurrent.DialerFutures;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.storage.Unencrypted;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Brings the annotated call log up to date, if necessary. */
@Singleton
public class RefreshAnnotatedCallLogWorker {

  private final Context appContext;
  private final DataSources dataSources;
  private final SharedPreferences sharedPreferences;
  private final ListeningExecutorService backgroundExecutorService;
  private final ListeningExecutorService lightweightExecutorService;
  // Used to ensure that only one refresh flow runs at a time. (Note that
  // RefreshAnnotatedCallLogWorker is a @Singleton.)
  private final DialerFutureSerializer dialerFutureSerializer = new DialerFutureSerializer();

  @Inject
  RefreshAnnotatedCallLogWorker(
      @ApplicationContext Context appContext,
      DataSources dataSources,
      @Unencrypted SharedPreferences sharedPreferences,
      @BackgroundExecutor ListeningExecutorService backgroundExecutorService,
      @LightweightExecutor ListeningExecutorService lightweightExecutorService) {
    this.appContext = appContext;
    this.dataSources = dataSources;
    this.sharedPreferences = sharedPreferences;
    this.backgroundExecutorService = backgroundExecutorService;
    this.lightweightExecutorService = lightweightExecutorService;
  }

  /** Checks if the annotated call log is dirty and refreshes it if necessary. */
  public ListenableFuture<Void> refreshWithDirtyCheck() {
    return refresh(true);
  }

  /** Refreshes the annotated call log, bypassing dirty checks. */
  public ListenableFuture<Void> refreshWithoutDirtyCheck() {
    return refresh(false);
  }

  private ListenableFuture<Void> refresh(boolean checkDirty) {
    LogUtil.i("RefreshAnnotatedCallLogWorker.refresh", "submitting serialized refresh request");
    return dialerFutureSerializer.submitAsync(
        () -> checkDirtyAndRebuildIfNecessary(appContext, checkDirty), lightweightExecutorService);
  }

  private ListenableFuture<Void> checkDirtyAndRebuildIfNecessary(
      Context appContext, boolean checkDirty) {
    ListenableFuture<Boolean> forceRebuildFuture =
        backgroundExecutorService.submit(
            () -> {
              LogUtil.i(
                  "RefreshAnnotatedCallLogWorker.checkDirtyAndRebuildIfNecessary",
                  "starting refresh flow");
              if (!checkDirty) {
                return true;
              }
              // Default to true. If the pref doesn't exist, the annotated call log hasn't been
              // created and we just skip isDirty checks and force a rebuild.
              boolean forceRebuildPrefValue =
                  sharedPreferences.getBoolean(CallLogFramework.PREF_FORCE_REBUILD, true);
              if (forceRebuildPrefValue) {
                LogUtil.i(
                    "RefreshAnnotatedCallLogWorker.checkDirtyAndRebuildIfNecessary",
                    "annotated call log has been marked dirty or does not exist");
              }
              return forceRebuildPrefValue;
            });

    // After checking the "force rebuild" shared pref, conditionally call isDirty.
    ListenableFuture<Boolean> isDirtyFuture =
        Futures.transformAsync(
            forceRebuildFuture,
            forceRebuild ->
                Preconditions.checkNotNull(forceRebuild)
                    ? Futures.immediateFuture(true)
                    : isDirty(appContext),
            lightweightExecutorService);

    // After determining isDirty, conditionally call rebuild.
    return Futures.transformAsync(
        isDirtyFuture,
        isDirty ->
            Preconditions.checkNotNull(isDirty)
                ? rebuild(appContext)
                : Futures.immediateFuture(null),
        lightweightExecutorService);
  }

  private ListenableFuture<Boolean> isDirty(Context appContext) {
    List<ListenableFuture<Boolean>> isDirtyFutures = new ArrayList<>();
    for (CallLogDataSource dataSource : dataSources.getDataSourcesIncludingSystemCallLog()) {
      isDirtyFutures.add(dataSource.isDirty(appContext));
    }
    // Simultaneously invokes isDirty on all data sources, returning as soon as one returns true.
    return DialerFutures.firstMatching(isDirtyFutures, Preconditions::checkNotNull, false);
  }

  private ListenableFuture<Void> rebuild(Context appContext) {
    CallLogMutations mutations = new CallLogMutations();

    // Start by filling the data sources--the system call log data source must go first!
    CallLogDataSource systemCallLogDataSource = dataSources.getSystemCallLogDataSource();
    ListenableFuture<Void> fillFuture = systemCallLogDataSource.fill(appContext, mutations);

    // After the system call log data source is filled, call fill sequentially on each remaining
    // data source. This must be done sequentially because mutations are not threadsafe and are
    // passed from source to source.
    for (CallLogDataSource dataSource : dataSources.getDataSourcesExcludingSystemCallLog()) {
      fillFuture =
          Futures.transformAsync(
              fillFuture,
              unused -> dataSource.fill(appContext, mutations),
              lightweightExecutorService);
    }

    // After all data sources are filled, apply mutations (at this point "fillFuture" is the result
    // of filling the last data source).
    ListenableFuture<Void> applyMutationsFuture =
        Futures.transformAsync(
            fillFuture,
            unused ->
                CallLogDatabaseComponent.get(appContext)
                    .mutationApplier()
                    .applyToDatabase(mutations, appContext),
            lightweightExecutorService);

    // After mutations applied, call onSuccessfulFill for each data source (in parallel).
    ListenableFuture<List<Void>> onSuccessfulFillFuture =
        Futures.transformAsync(
            applyMutationsFuture,
            unused -> {
              List<ListenableFuture<Void>> onSuccessfulFillFutures = new ArrayList<>();
              for (CallLogDataSource dataSource :
                  dataSources.getDataSourcesIncludingSystemCallLog()) {
                onSuccessfulFillFutures.add(dataSource.onSuccessfulFill(appContext));
              }
              return Futures.allAsList(onSuccessfulFillFutures);
            },
            lightweightExecutorService);

    // After onSuccessfulFill is called for every data source, write the shared pref.
    return Futures.transform(
        onSuccessfulFillFuture,
        unused -> {
          sharedPreferences.edit().putBoolean(CallLogFramework.PREF_FORCE_REBUILD, false).apply();
          return null;
        },
        backgroundExecutorService);
  }
}
