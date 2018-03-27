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

import com.google.common.util.concurrent.ListenableFuture;

/** Determines if new call log components are enabled. */
public interface CallLogConfig {

  /**
   * Updates the config values. This may kick off a lot of work so should be done infrequently, for
   * example by a scheduled job or broadcast receiver which rarely fires.
   */
  ListenableFuture<Void> update();

  boolean isNewCallLogFragmentEnabled();

  boolean isNewVoicemailFragmentEnabled();

  boolean isNewPeerEnabled();

  boolean isCallLogFrameworkEnabled();

  /** Schedules a job to periodically update the config. */
  void schedulePollingJob();
}
