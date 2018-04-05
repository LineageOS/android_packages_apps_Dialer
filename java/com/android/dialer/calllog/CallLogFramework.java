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
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.calllog.datasources.DataSources;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.Ui;
import com.android.dialer.inject.ApplicationContext;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Coordinates work across {@link DataSources}.
 *
 * <p>All methods should be called on the main thread.
 */
@Singleton
public final class CallLogFramework {

  private final Context appContext;
  private final DataSources dataSources;
  private final AnnotatedCallLogMigrator annotatedCallLogMigrator;
  private final ListeningExecutorService uiExecutor;
  private final CallLogState callLogState;

  @Inject
  CallLogFramework(
      @ApplicationContext Context appContext,
      DataSources dataSources,
      AnnotatedCallLogMigrator annotatedCallLogMigrator,
      @Ui ListeningExecutorService uiExecutor,
      CallLogState callLogState) {
    this.appContext = appContext;
    this.dataSources = dataSources;
    this.annotatedCallLogMigrator = annotatedCallLogMigrator;
    this.uiExecutor = uiExecutor;
    this.callLogState = callLogState;
  }

  /** Registers the content observers for all data sources. */
  public void registerContentObservers() {
    LogUtil.enterBlock("CallLogFramework.registerContentObservers");
    for (CallLogDataSource dataSource : dataSources.getDataSourcesIncludingSystemCallLog()) {
      dataSource.registerContentObservers();
    }
  }

  /** Enables the framework. */
  public ListenableFuture<Void> enable() {
    registerContentObservers();
    return annotatedCallLogMigrator.migrate();
  }

  /** Disables the framework. */
  public ListenableFuture<Void> disable() {
    return Futures.transform(
        Futures.allAsList(disableDataSources(), annotatedCallLogMigrator.clearData()),
        unused -> null,
        MoreExecutors.directExecutor());
  }

  private ListenableFuture<Void> disableDataSources() {
    LogUtil.enterBlock("CallLogFramework.disableDataSources");

    for (CallLogDataSource dataSource : dataSources.getDataSourcesIncludingSystemCallLog()) {
      dataSource.unregisterContentObservers();
    }

    callLogState.clearData();

    // Clear data only after all content observers have been disabled.
    List<ListenableFuture<Void>> allFutures = new ArrayList<>();
    for (CallLogDataSource dataSource : dataSources.getDataSourcesIncludingSystemCallLog()) {
      allFutures.add(dataSource.clearData());
    }

    return Futures.transform(
        Futures.allAsList(allFutures),
        unused -> {
          // Send a broadcast to the OldMainActivityPeer to remove the NewCallLogFragment and
          // NewVoicemailFragment if it is currently attached. If this is not done, user interaction
          // with the fragment could cause call log framework state to be unexpectedly written. For
          // example scrolling could cause the AnnotatedCallLog to be read (which would trigger
          // database creation).
          LocalBroadcastManager.getInstance(appContext)
              .sendBroadcastSync(new Intent("disableCallLogFramework"));
          return null;
        },
        uiExecutor);
  }
}
