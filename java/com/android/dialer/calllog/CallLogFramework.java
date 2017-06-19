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
import android.preference.PreferenceManager;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.calllog.datasources.DataSources;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProviderBindings;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Coordinates work across CallLog data sources to detect if the annotated call log is out of date
 * ("dirty") and update it if necessary.
 *
 * <p>All methods should be called on the main thread.
 */
@Singleton
public final class CallLogFramework implements CallLogDataSource.ContentObserverCallbacks {

  static final String PREF_FORCE_REBUILD = "callLogFrameworkForceRebuild";

  private final DataSources dataSources;

  @Nullable private CallLogUi ui;

  @Inject
  CallLogFramework(DataSources dataSources) {
    this.dataSources = dataSources;
  }

  public boolean isNewCallLogEnabled(Context context) {
    return ConfigProviderBindings.get(context).getBoolean("enable_new_call_log_tab", false);
  }

  /** Registers the content observers for all data sources. */
  public void registerContentObservers(Context appContext) {
    LogUtil.enterBlock("CallLogFramework.registerContentObservers");

    if (!isNewCallLogEnabled(appContext)) {
      LogUtil.i("CallLogFramework.registerContentObservers", "new call log not enabled");
      return;
    }

    for (CallLogDataSource dataSource : dataSources.getDataSourcesIncludingSystemCallLog()) {
      dataSource.registerContentObservers(appContext, this);
    }
  }

  /**
   * Attach a UI component to the framework so that it may be notified of changes to the annotated
   * call log.
   */
  public void attachUi(CallLogUi ui) {
    LogUtil.enterBlock("CallLogFramework.attachUi");
    this.ui = ui;
  }

  /**
   * Detaches the UI from the framework. This should be called when the UI is hidden or destroyed
   * and no longer needs to be notified of changes to the annotated call log.
   */
  public void detachUi() {
    LogUtil.enterBlock("CallLogFramework.detachUi");
    this.ui = null;
  }

  /**
   * Marks the call log as dirty and notifies any attached UI components. If there are no UI
   * components currently attached, this is an efficient operation since it is just writing a shared
   * pref.
   *
   * <p>We don't want to actually force a rebuild when there is no UI running because we don't want
   * to be constantly rebuilding the database when the device is sitting on a desk and receiving a
   * lot of calls, for example.
   */
  @Override
  @MainThread
  public void markDirtyAndNotify(Context appContext) {
    Assert.isMainThread();
    LogUtil.enterBlock("CallLogFramework.markDirtyAndNotify");

    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext);
    sharedPreferences.edit().putBoolean(PREF_FORCE_REBUILD, true).apply();

    if (ui != null) {
      ui.invalidateUi();
    }
  }

  /** Callbacks invoked on listening UI components. */
  public interface CallLogUi {

    /** Notifies the call log UI that the annotated call log is out of date. */
    @MainThread
    void invalidateUi();
  }
}
