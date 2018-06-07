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
import com.android.dialer.calllog.constants.SharedPrefKeys;
import com.android.dialer.calllog.database.MutationApplier;
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.calllog.datasources.CallLogMutations;
import com.android.dialer.calllog.datasources.DataSources;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.common.concurrent.Annotations.LightweightExecutor;
import com.android.dialer.common.concurrent.DefaultFutureCallback;
import com.android.dialer.common.concurrent.DialerFutureSerializer;
import com.android.dialer.common.concurrent.DialerFutures;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.metrics.FutureTimer;
import com.android.dialer.metrics.FutureTimer.LogCatMode;
import com.android.dialer.metrics.Metrics;
import com.android.dialer.storage.Unencrypted;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
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
  private final MutationApplier mutationApplier;
  private final FutureTimer futureTimer;
  private final CallLogState callLogState;
  private final CallLogCacheUpdater callLogCacheUpdater;
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
      MutationApplier mutationApplier,
      FutureTimer futureTimer,
      CallLogState callLogState,
      CallLogCacheUpdater callLogCacheUpdater,
      @BackgroundExecutor ListeningExecutorService backgroundExecutorService,
      @LightweightExecutor ListeningExecutorService lightweightExecutorService) {
    this.appContext = appContext;
    this.dataSources = dataSources;
    this.sharedPreferences = sharedPreferences;
    this.mutationApplier = mutationApplier;
    this.futureTimer = futureTimer;
    this.callLogState = callLogState;
    this.callLogCacheUpdater = callLogCacheUpdater;
    this.backgroundExecutorService = backgroundExecutorService;
    this.lightweightExecutorService = lightweightExecutorService;
  }

  /** Result of refreshing the annotated call log. */
  public enum RefreshResult {
    NOT_DIRTY,
    REBUILT_BUT_NO_CHANGES_NEEDED,
    REBUILT_AND_CHANGES_NEEDED
  }

  /** Checks if the annotated call log is dirty and refreshes it if necessary. */
  ListenableFuture<RefreshResult> refreshWithDirtyCheck() {
    return refresh(true);
  }

  /** Refreshes the annotated call log, bypassing dirty checks. */
  ListenableFuture<RefreshResult> refreshWithoutDirtyCheck() {
    return refresh(false);
  }

  private ListenableFuture<RefreshResult> refresh(boolean checkDirty) {
    LogUtil.i("RefreshAnnotatedCallLogWorker.refresh", "submitting serialized refresh request");
    return dialerFutureSerializer.submitAsync(
        () -> checkDirtyAndRebuildIfNecessary(checkDirty), lightweightExecutorService);
  }

  private ListenableFuture<RefreshResult> checkDirtyAndRebuildIfNecessary(boolean checkDirty) {
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
                  sharedPreferences.getBoolean(SharedPrefKeys.FORCE_REBUILD, true);
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
                    : isDirty(),
            lightweightExecutorService);

    // After determining isDirty, conditionally call rebuild.
    return Futures.transformAsync(
        isDirtyFuture,
        isDirty -> {
          LogUtil.v(
              "RefreshAnnotatedCallLogWorker.checkDirtyAndRebuildIfNecessary",
              "isDirty: %b",
              Preconditions.checkNotNull(isDirty));
          if (isDirty) {
            return Futures.transformAsync(
                callLogState.isBuilt(), this::rebuild, MoreExecutors.directExecutor());
          }
          return Futures.immediateFuture(RefreshResult.NOT_DIRTY);
        },
        lightweightExecutorService);
  }

  private ListenableFuture<Boolean> isDirty() {
    List<ListenableFuture<Boolean>> isDirtyFutures = new ArrayList<>();
    for (CallLogDataSource dataSource : dataSources.getDataSourcesIncludingSystemCallLog()) {
      ListenableFuture<Boolean> dataSourceDirty = dataSource.isDirty();
      isDirtyFutures.add(dataSourceDirty);
      String eventName = String.format(Metrics.IS_DIRTY_TEMPLATE, dataSource.getLoggingName());
      futureTimer.applyTiming(dataSourceDirty, eventName, LogCatMode.LOG_VALUES);
    }
    // Simultaneously invokes isDirty on all data sources, returning as soon as one returns true.
    ListenableFuture<Boolean> isDirtyFuture =
        DialerFutures.firstMatching(isDirtyFutures, Preconditions::checkNotNull, false);
    futureTimer.applyTiming(isDirtyFuture, Metrics.IS_DIRTY_EVENT_NAME, LogCatMode.LOG_VALUES);
    return isDirtyFuture;
  }

  private ListenableFuture<RefreshResult> rebuild(boolean isBuilt) {
    CallLogMutations mutations = new CallLogMutations();

    // Start by filling the data sources--the system call log data source must go first!
    CallLogDataSource systemCallLogDataSource = dataSources.getSystemCallLogDataSource();
    ListenableFuture<Void> fillFuture = systemCallLogDataSource.fill(mutations);
    String systemEventName = eventNameForFill(systemCallLogDataSource, isBuilt);
    futureTimer.applyTiming(fillFuture, systemEventName);

    // After the system call log data source is filled, call fill sequentially on each remaining
    // data source. This must be done sequentially because mutations are not threadsafe and are
    // passed from source to source.
    for (CallLogDataSource dataSource : dataSources.getDataSourcesExcludingSystemCallLog()) {
      fillFuture =
          Futures.transformAsync(
              fillFuture,
              unused -> {
                ListenableFuture<Void> dataSourceFuture = dataSource.fill(mutations);
                String eventName = eventNameForFill(dataSource, isBuilt);
                futureTimer.applyTiming(dataSourceFuture, eventName);
                return dataSourceFuture;
              },
              lightweightExecutorService);
    }

    futureTimer.applyTiming(fillFuture, eventNameForOverallFill(isBuilt));

    // After all data sources are filled, apply mutations (at this point "fillFuture" is the result
    // of filling the last data source).
    ListenableFuture<Void> applyMutationsFuture =
        Futures.transformAsync(
            fillFuture,
            unused -> {
              ListenableFuture<Void> mutationApplierFuture =
                  mutationApplier.applyToDatabase(mutations, appContext);
              futureTimer.applyTiming(mutationApplierFuture, eventNameForApplyMutations(isBuilt));
              return mutationApplierFuture;
            },
            lightweightExecutorService);

    Futures.addCallback(
        Futures.transformAsync(
            applyMutationsFuture,
            unused -> callLogCacheUpdater.updateCache(mutations),
            MoreExecutors.directExecutor()),
        new DefaultFutureCallback<>(),
        MoreExecutors.directExecutor());

    // After mutations applied, call onSuccessfulFill for each data source (in parallel).
    ListenableFuture<List<Void>> onSuccessfulFillFuture =
        Futures.transformAsync(
            applyMutationsFuture,
            unused -> {
              List<ListenableFuture<Void>> onSuccessfulFillFutures = new ArrayList<>();
              for (CallLogDataSource dataSource :
                  dataSources.getDataSourcesIncludingSystemCallLog()) {
                ListenableFuture<Void> dataSourceFuture = dataSource.onSuccessfulFill();
                onSuccessfulFillFutures.add(dataSourceFuture);
                String eventName = eventNameForOnSuccessfulFill(dataSource, isBuilt);
                futureTimer.applyTiming(dataSourceFuture, eventName);
              }
              ListenableFuture<List<Void>> allFutures = Futures.allAsList(onSuccessfulFillFutures);
              futureTimer.applyTiming(allFutures, eventNameForOverallOnSuccessfulFill(isBuilt));
              return allFutures;
            },
            lightweightExecutorService);

    // After onSuccessfulFill is called for every data source, write the shared pref.
    return Futures.transform(
        onSuccessfulFillFuture,
        unused -> {
          sharedPreferences.edit().putBoolean(SharedPrefKeys.FORCE_REBUILD, false).apply();
          callLogState.markBuilt();
          return mutations.isEmpty()
              ? RefreshResult.REBUILT_BUT_NO_CHANGES_NEEDED
              : RefreshResult.REBUILT_AND_CHANGES_NEEDED;
        },
        backgroundExecutorService);
  }

  private static String eventNameForFill(CallLogDataSource dataSource, boolean isBuilt) {
    return String.format(
        !isBuilt ? Metrics.INITIAL_FILL_TEMPLATE : Metrics.FILL_TEMPLATE,
        dataSource.getLoggingName());
  }

  private static String eventNameForOverallFill(boolean isBuilt) {
    return !isBuilt ? Metrics.INITIAL_FILL_EVENT_NAME : Metrics.FILL_EVENT_NAME;
  }

  private static String eventNameForOnSuccessfulFill(
      CallLogDataSource dataSource, boolean isBuilt) {
    return String.format(
        !isBuilt
            ? Metrics.INITIAL_ON_SUCCESSFUL_FILL_TEMPLATE
            : Metrics.ON_SUCCESSFUL_FILL_TEMPLATE,
        dataSource.getLoggingName());
  }

  private static String eventNameForOverallOnSuccessfulFill(boolean isBuilt) {
    return !isBuilt
        ? Metrics.INITIAL_ON_SUCCESSFUL_FILL_EVENT_NAME
        : Metrics.ON_SUCCESSFUL_FILL_EVENT_NAME;
  }

  private static String eventNameForApplyMutations(boolean isBuilt) {
    return !isBuilt
        ? Metrics.INITIAL_APPLY_MUTATIONS_EVENT_NAME
        : Metrics.APPLY_MUTATIONS_EVENT_NAME;
  }
}
