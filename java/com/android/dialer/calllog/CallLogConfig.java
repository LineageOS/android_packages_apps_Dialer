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

package com.android.dialer.calllog;

import android.content.SharedPreferences;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.configprovider.ConfigProvider;
import com.android.dialer.storage.Unencrypted;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
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
public final class CallLogConfig {

  private static final String NEW_CALL_LOG_FRAGMENT_ENABLED_PREF_KEY = "newCallLogFragmentEnabled";
  private static final String NEW_VOICEMAIL_FRAGMENT_ENABLED_PREF_KEY =
      "newVoicemailFragmentEnabled";
  private static final String NEW_PEER_ENABLED_PREF_KEY = "newPeerEnabled";
  private static final String NEW_CALL_LOG_FRAMEWORK_ENABLED_PREF_KEY =
      "newCallLogFrameworkEnabled";

  private final SharedPreferences sharedPreferences;
  private final ConfigProvider configProvider;
  private final ListeningExecutorService backgroundExecutor;

  @Inject
  public CallLogConfig(
      @Unencrypted SharedPreferences sharedPreferences,
      ConfigProvider configProvider,
      @BackgroundExecutor ListeningExecutorService backgroundExecutor) {
    this.sharedPreferences = sharedPreferences;
    this.configProvider = configProvider;
    this.backgroundExecutor = backgroundExecutor;
  }

  /**
   * Updates the config values. This may kick off a lot of work so should be done infrequently, for
   * example by a scheduled job or broadcast receiver which rarely fires.
   */
  public ListenableFuture<Void> update() {
    return backgroundExecutor.submit(
        () -> {
          boolean newCallLogFragmentEnabledInConfigProvider =
              configProvider.getBoolean("new_call_log_fragment_enabled", false);
          boolean newVoicemailFragmentEnabledInConfigProvider =
              configProvider.getBoolean("new_voicemail_fragment_enabled", false);
          boolean newPeerEnabledInConfigProvider =
              configProvider.getBoolean("nui_peer_enabled", false);

          boolean isCallLogFrameworkEnabled = isCallLogFrameworkEnabled();
          boolean callLogFrameworkShouldBeEnabled =
              newCallLogFragmentEnabledInConfigProvider
                  || newVoicemailFragmentEnabledInConfigProvider
                  || newPeerEnabledInConfigProvider;

          if (callLogFrameworkShouldBeEnabled && !isCallLogFrameworkEnabled) {
            enableFramework();

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

          } else if (!callLogFrameworkShouldBeEnabled && isCallLogFrameworkEnabled) {
            // Reflect the flag changes before disabling the framework.
            sharedPreferences
                .edit()
                .putBoolean(NEW_CALL_LOG_FRAGMENT_ENABLED_PREF_KEY, false)
                .putBoolean(NEW_VOICEMAIL_FRAGMENT_ENABLED_PREF_KEY, false)
                .putBoolean(NEW_PEER_ENABLED_PREF_KEY, false)
                .putBoolean(NEW_CALL_LOG_FRAMEWORK_ENABLED_PREF_KEY, false)
                .apply();

            disableFramework();
          } else {
            // We didn't need to enable/disable the framework, but we still need to update the
            // individual flags.
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
          }
          return null;
        });
  }

  private void enableFramework() {
    // TODO(zachh): Register content observers, etc.
  }

  private void disableFramework() {
    // TODO(zachh): Unregister content observers, delete databases, etc.
  }

  public boolean isNewCallLogFragmentEnabled() {
    return sharedPreferences.getBoolean(NEW_CALL_LOG_FRAGMENT_ENABLED_PREF_KEY, false);
  }

  public boolean isNewVoicemailFragmentEnabled() {
    return sharedPreferences.getBoolean(NEW_VOICEMAIL_FRAGMENT_ENABLED_PREF_KEY, false);
  }

  public boolean isNewPeerEnabled() {
    return sharedPreferences.getBoolean(NEW_PEER_ENABLED_PREF_KEY, false);
  }

  /**
   * Returns true if the new call log framework is enabled, meaning that content observers are
   * firing and PhoneLookupHistory is being populated, etc.
   */
  public boolean isCallLogFrameworkEnabled() {
    return sharedPreferences.getBoolean(NEW_CALL_LOG_FRAMEWORK_ENABLED_PREF_KEY, false);
  }
}
