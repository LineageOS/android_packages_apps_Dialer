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
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.calllog.datasources.DataSources;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProviderBindings;
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

  @Inject
  CallLogFramework(DataSources dataSources) {
    this.dataSources = dataSources;
  }

  /** Registers the content observers for all data sources. */
  public void registerContentObservers(Context appContext) {
    LogUtil.enterBlock("CallLogFramework.registerContentObservers");

    // This is the same condition used in MainImpl#isNewUiEnabled. It means that bugfood/debug
    // users will have "new call log" content observers firing. These observers usually do simple
    // things like writing shared preferences.
    // TODO(zachh): Find a way to access Main#isNewUiEnabled without creating a circular dependency.
    if (ConfigProviderBindings.get(appContext).getBoolean("is_nui_shortcut_enabled", false)) {
      for (CallLogDataSource dataSource : dataSources.getDataSourcesIncludingSystemCallLog()) {
        dataSource.registerContentObservers(appContext);
      }
    } else {
      LogUtil.i("CallLogFramework.registerContentObservers", "not registering content observers");
    }
  }
}
