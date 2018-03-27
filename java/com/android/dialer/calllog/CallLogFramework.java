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

import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.calllog.datasources.DataSources;
import com.android.dialer.common.LogUtil;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
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

  private final DataSources dataSources;
  private final AnnotatedCallLogMigrator annotatedCallLogMigrator;

  @Inject
  CallLogFramework(DataSources dataSources, AnnotatedCallLogMigrator annotatedCallLogMigrator) {
    this.dataSources = dataSources;
    this.annotatedCallLogMigrator = annotatedCallLogMigrator;
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

    // Clear data only after all content observers have been disabled.
    List<ListenableFuture<Void>> allFutures = new ArrayList<>();
    for (CallLogDataSource dataSource : dataSources.getDataSourcesIncludingSystemCallLog()) {
      allFutures.add(dataSource.clearData());
    }
    return Futures.transform(
        Futures.allAsList(allFutures), unused -> null, MoreExecutors.directExecutor());
  }
}
