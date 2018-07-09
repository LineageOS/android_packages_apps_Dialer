/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.dialer.binary.common;

import android.app.Application;
import android.os.Trace;
import android.support.annotation.NonNull;
import android.support.v4.os.BuildCompat;
import com.android.dialer.blocking.BlockedNumbersAutoMigrator;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.android.dialer.calllog.CallLogComponent;
import com.android.dialer.calllog.CallLogFramework;
import com.android.dialer.calllog.config.CallLogConfig;
import com.android.dialer.calllog.config.CallLogConfigComponent;
import com.android.dialer.callrecord.CallRecordingAutoMigrator;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.inject.HasRootComponent;
import com.android.dialer.notification.NotificationChannelManager;
import com.android.dialer.persistentlog.PersistentLogger;
import com.android.dialer.strictmode.StrictModeComponent;

/** A common application subclass for all Dialer build variants. */
public abstract class DialerApplication extends Application implements HasRootComponent {

  private volatile Object rootComponent;

  @Override
  public void onCreate() {
    Trace.beginSection("DialerApplication.onCreate");
    StrictModeComponent.get(this).getDialerStrictMode().onApplicationCreate(this);
    super.onCreate();
    new BlockedNumbersAutoMigrator(
            this.getApplicationContext(),
            new FilteredNumberAsyncQueryHandler(this),
            DialerExecutorComponent.get(this).dialerExecutorFactory())
        .asyncAutoMigrate();
    new CallRecordingAutoMigrator(
            this.getApplicationContext(),
            DialerExecutorComponent.get(this).dialerExecutorFactory())
        .asyncAutoMigrate();
    initializeAnnotatedCallLog();
    PersistentLogger.initialize(this);

    if (BuildCompat.isAtLeastO()) {
      NotificationChannelManager.initChannels(this);
    }
    Trace.endSection();
  }

  private void initializeAnnotatedCallLog() {
    CallLogConfig callLogConfig = CallLogConfigComponent.get(this).callLogConfig();
    callLogConfig.schedulePollingJob();

    if (callLogConfig.isCallLogFrameworkEnabled()) {
      CallLogFramework callLogFramework = CallLogComponent.get(this).callLogFramework();
      callLogFramework.registerContentObservers();
    } else {
      LogUtil.i("DialerApplication.initializeAnnotatedCallLog", "framework not enabled");
    }
  }

  /**
   * Returns a new instance of the root component for the application. Sub classes should define a
   * root component that extends all the sub components "HasComponent" intefaces. The component
   * should specify all modules that the application supports and provide stubs for the remainder.
   */
  @NonNull
  protected abstract Object buildRootComponent();

  /** Returns a cached instance of application's root component. */
  @Override
  @NonNull
  public final Object component() {
    Object result = rootComponent;
    if (result == null) {
      synchronized (this) {
        result = rootComponent;
        if (result == null) {
          rootComponent = result = buildRootComponent();
        }
      }
    }
    return result;
  }
}
