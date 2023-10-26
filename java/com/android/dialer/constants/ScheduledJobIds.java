/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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
 * limitations under the License.
 */

package com.android.dialer.constants;

/**
 * Registry of scheduled job ids used by the dialer UID.
 *
 * <p>Any dialer jobs which use the android JobScheduler should register their IDs here, to avoid
 * the same ID accidentally being reused.
 *
 * <p>Do not change any existing IDs.
 */
public final class ScheduledJobIds {
  // This job refreshes dynamic launcher shortcuts.
  public static final int SHORTCUT_PERIODIC_JOB = 100;

  public static final int VVM_TASK_SCHEDULER_JOB = 200;
  public static final int VVM_STATUS_CHECK_JOB = 201;
  public static final int VVM_DEVICE_PROVISIONED_JOB = 202;
  public static final int VVM_NOTIFICATION_JOB = 205;
}
