/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.calllog.config;

import android.annotation.SuppressLint;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.v4.os.UserManagerCompat;
import com.android.dialer.calllog.CallLogFramework;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.configprovider.ConfigProvider;
import com.android.dialer.constants.ScheduledJobIds;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.storage.Unencrypted;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/**
 * Determines if new call log components are enabled.
 *
 * <p>When the underlying flag values from the {@link ConfigProvider} changes, it is necessary to do
 * work such as registering/unregistering content observers, and this class is responsible for
 * coordinating that work.
 *
 * <p>New UI application components should use this class instead of reading flags directly from the
 * {@link ConfigProvider}.
 */
public final class CallLogConfigImpl implements CallLogConfig {

  private static final String NEW_CALL_LOG_FRAGMENT_ENABLED_PREF_KEY = "newCallLogFragmentEnabled";
  private static final String NEW_VOICEMAIL_FRAGMENT_ENABLED_PREF_KEY =
      "newVoicemailFragmentEnabled";
  private static final String NEW_PEER_ENABLED_PREF_KEY = "newPeerEnabled";
  private static final String NEW_CALL_LOG_FRAMEWORK_ENABLED_PREF_KEY =
      "newCallLogFrameworkEnabled";

  private final Context appContext;
  private final CallLogFramework callLogFramework;
  private final SharedPreferences sharedPreferences;
  private final ConfigProvider configProvider;
  private final ListeningExecutorService backgroundExecutor;

  @Inject
  public CallLogConfigImpl(
      @ApplicationContext Context appContext,
      CallLogFramework callLogFramework,
      @Unencrypted SharedPreferences sharedPreferences,
      ConfigProvider configProvider,
      @BackgroundExecutor ListeningExecutorService backgroundExecutor) {
    this.appContext = appContext;
    this.callLogFramework = callLogFramework;
    this.sharedPreferences = sharedPreferences;
    this.configProvider = configProvider;
    this.backgroundExecutor = backgroundExecutor;
  }

  @Override
  public ListenableFuture<Void> update() {
    boolean newCallLogFragmentEnabledInConfigProvider =
        configProvider.getBoolean("new_call_log_fragment_enabled", false);
    boolean newVoicemailFragmentEnabledInConfigProvider =
        configProvider.getBoolean("new_voicemail_fragment_enabled", false);
    boolean newPeerEnabledInConfigProvider = configProvider.getBoolean("nui_peer_enabled", false);

    boolean isCallLogFrameworkEnabled = isCallLogFrameworkEnabled();
    boolean callLogFrameworkShouldBeEnabled =
        newCallLogFragmentEnabledInConfigProvider
            || newVoicemailFragmentEnabledInConfigProvider
            || newPeerEnabledInConfigProvider;

    if (callLogFrameworkShouldBeEnabled && !isCallLogFrameworkEnabled) {
      return Futures.transform(
          callLogFramework.enable(),
          unused -> {
            // Reflect the flag changes only after the framework is enabled.
            sharedPreferences
                .edit()
                .putBoolean(
                    NEW_CALL_LOG_FRAGMENT_ENABLED_PREF_KEY,
                    newCallLogFragmentEnabledInConfigProvider)
                .putBoolean(
                    NEW_VOICEMAIL_FRAGMENT_ENABLED_PREF_KEY,
                    newVoicemailFragmentEnabledInConfigProvider)
                .putBoolean(NEW_PEER_ENABLED_PREF_KEY, newPeerEnabledInConfigProvider)
                .putBoolean(NEW_CALL_LOG_FRAMEWORK_ENABLED_PREF_KEY, true)
                .apply();
            return null;
          },
          backgroundExecutor);
    } else if (!callLogFrameworkShouldBeEnabled && isCallLogFrameworkEnabled) {
      // Reflect the flag changes before disabling the framework.
      ListenableFuture<Void> writeSharedPrefsFuture =
          backgroundExecutor.submit(
              () -> {
                sharedPreferences
                    .edit()
                    .putBoolean(NEW_CALL_LOG_FRAGMENT_ENABLED_PREF_KEY, false)
                    .putBoolean(NEW_VOICEMAIL_FRAGMENT_ENABLED_PREF_KEY, false)
                    .putBoolean(NEW_PEER_ENABLED_PREF_KEY, false)
                    .putBoolean(NEW_CALL_LOG_FRAMEWORK_ENABLED_PREF_KEY, false)
                    .apply();
                return null;
              });
      return Futures.transformAsync(
          writeSharedPrefsFuture,
          unused -> callLogFramework.disable(),
          MoreExecutors.directExecutor());
    } else {
      // We didn't need to enable/disable the framework, but we still need to update the
      // individual flags.
      return backgroundExecutor.submit(
          () -> {
            sharedPreferences
                .edit()
                .putBoolean(
                    NEW_CALL_LOG_FRAGMENT_ENABLED_PREF_KEY,
                    newCallLogFragmentEnabledInConfigProvider)
                .putBoolean(
                    NEW_VOICEMAIL_FRAGMENT_ENABLED_PREF_KEY,
                    newVoicemailFragmentEnabledInConfigProvider)
                .putBoolean(NEW_PEER_ENABLED_PREF_KEY, newPeerEnabledInConfigProvider)
                .apply();
            return null;
          });
    }
  }

  @Override
  public boolean isNewCallLogFragmentEnabled() {
    return sharedPreferences.getBoolean(NEW_CALL_LOG_FRAGMENT_ENABLED_PREF_KEY, false);
  }

  @Override
  public boolean isNewVoicemailFragmentEnabled() {
    return sharedPreferences.getBoolean(NEW_VOICEMAIL_FRAGMENT_ENABLED_PREF_KEY, false);
  }

  @Override
  public boolean isNewPeerEnabled() {
    return sharedPreferences.getBoolean(NEW_PEER_ENABLED_PREF_KEY, false);
  }

  /**
   * Returns true if the new call log framework is enabled, meaning that content observers are
   * firing and PhoneLookupHistory is being populated, etc.
   */
  @Override
  public boolean isCallLogFrameworkEnabled() {
    return sharedPreferences.getBoolean(NEW_CALL_LOG_FRAMEWORK_ENABLED_PREF_KEY, false);
  }

  @Override
  public void schedulePollingJob() {
    if (UserManagerCompat.isUserUnlocked(appContext)) {
      JobScheduler jobScheduler = Assert.isNotNull(appContext.getSystemService(JobScheduler.class));
      @SuppressLint("MissingPermission") // Dialer has RECEIVE_BOOT permission
      JobInfo jobInfo =
          new JobInfo.Builder(
                  ScheduledJobIds.CALL_LOG_CONFIG_POLLING_JOB,
                  new ComponentName(appContext, PollingJob.class))
              .setPeriodic(TimeUnit.HOURS.toMillis(24))
              .setPersisted(true)
              .setRequiresCharging(true)
              .setRequiresDeviceIdle(true)
              .build();
      LogUtil.i("CallLogConfigImpl.schedulePollingJob", "scheduling");
      jobScheduler.schedule(jobInfo);
    }
  }

  /**
   * Job which periodically force updates the {@link CallLogConfig}. This job is necessary to
   * support {@link ConfigProvider ConfigProviders} which do not provide a reliable mechanism for
   * listening to changes and calling {@link CallLogConfig#update()} directly, such as the {@link
   * com.android.dialer.configprovider.SharedPrefConfigProvider}.
   */
  public static final class PollingJob extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
      LogUtil.enterBlock("PollingJob.onStartJob");
      Futures.addCallback(
          CallLogConfigComponent.get(getApplicationContext()).callLogConfig().update(),
          new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
              jobFinished(params, false /* needsReschedule */);
            }

            @Override
            public void onFailure(Throwable throwable) {
              ThreadUtil.getUiThreadHandler()
                  .post(
                      () -> {
                        throw new RuntimeException(throwable);
                      });
              jobFinished(params, false /* needsReschedule */);
            }
          },
          MoreExecutors.directExecutor());
      return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
      return false;
    }
  }
}
