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
package com.android.dialer.common.concurrent;

import android.os.AsyncTask;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.common.concurrent.Annotations.LightweightExecutor;
import com.android.dialer.common.concurrent.Annotations.NonUiParallel;
import com.android.dialer.common.concurrent.Annotations.NonUiSerial;
import com.android.dialer.common.concurrent.Annotations.Ui;
import com.android.dialer.common.concurrent.Annotations.UiParallel;
import com.android.dialer.common.concurrent.Annotations.UiSerial;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import javax.inject.Singleton;

/** Module which provides concurrency bindings. */
@Module
public abstract class DialerExecutorModule {

  @Binds
  abstract DialerExecutorFactory bindDialerExecutorFactory(
      DefaultDialerExecutorFactory defaultDialerExecutorFactory);

  @Provides
  @Singleton
  @Ui
  static ListeningExecutorService provideUiThreadExecutorService() {
    return new UiThreadExecutor();
  }

  @Provides
  @Singleton
  @NonUiParallel
  static ExecutorService provideNonUiThreadPool() {
    return Executors.newFixedThreadPool(
        5,
        new ThreadFactory() {
          @Override
          public Thread newThread(Runnable runnable) {
            LogUtil.i("DialerExecutorModule.newThread", "creating low priority thread");
            Thread thread = new Thread(runnable, "DialerExecutors-LowPriority");
            // Java thread priority 4 corresponds to Process.THREAD_PRIORITY_BACKGROUND (10)
            thread.setPriority(4);
            return thread;
          }
        });
  }

  @Provides
  @Singleton
  @NonUiSerial
  static ScheduledExecutorService provideNonUiSerialExecutorService() {
    return Executors.newSingleThreadScheduledExecutor(
        new ThreadFactory() {
          @Override
          public Thread newThread(Runnable runnable) {
            LogUtil.i("NonUiTaskBuilder.newThread", "creating serial thread");
            Thread thread = new Thread(runnable, "DialerExecutors-LowPriority-Serial");
            // Java thread priority 4 corresponds to Process.THREAD_PRIORITY_BACKGROUND (10)
            thread.setPriority(4);
            return thread;
          }
        });
  }

  @Provides
  @UiParallel
  static ExecutorService provideUiThreadPool() {
    return (ExecutorService) AsyncTask.THREAD_POOL_EXECUTOR;
  }

  @Provides
  @Singleton
  @UiSerial
  static ScheduledExecutorService provideUiSerialExecutorService() {
    return Executors.newSingleThreadScheduledExecutor(
        new ThreadFactory() {
          @Override
          public Thread newThread(Runnable runnable) {
            LogUtil.i("DialerExecutorModule.newThread", "creating serial thread");
            Thread thread = new Thread(runnable, "DialerExecutors-HighPriority-Serial");
            // Java thread priority 5 corresponds to Process.THREAD_PRIORITY_DEFAULT (0)
            thread.setPriority(5);
            return thread;
          }
        });
  }

  @Provides
  @Singleton
  @LightweightExecutor
  static ListeningExecutorService provideLightweightExecutor(@UiParallel ExecutorService delegate) {
    return MoreExecutors.listeningDecorator(delegate);
  }

  @Provides
  @Singleton
  @BackgroundExecutor
  static ListeningExecutorService provideBackgroundExecutor(
      @NonUiParallel ExecutorService delegate) {
    return MoreExecutors.listeningDecorator(delegate);
  }
}
