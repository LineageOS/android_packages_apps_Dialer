/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.dialer.app;

import android.app.admin.DevicePolicyManager;

/**
 * Class containing the required identifiers to update device management resources.
 *
 * <p>See {@link DevicePolicyManager#getDrawable} and {@link DevicePolicyManager#getString}.
 */
public class DevicePolicyResources {

    private static final String PREFIX = "Dialer.";

    /**
     * The title of the in-call notification for an incoming work call.
     */
    public static final String NOTIFICATION_INCOMING_WORK_CALL_TITLE =
            PREFIX + "NOTIFICATION_INCOMING_WORK_CALL_TITLE";

    /**
     * The title of the in-call notification for an ongoing work call.
     */
    public static final String NOTIFICATION_ONGOING_WORK_CALL_TITLE =
            PREFIX + "NOTIFICATION_ONGOING_WORK_CALL_TITLE";

    /**
     * Missed call notification label, used when there's exactly one missed call from work
     * contact.
     */
    public static final String NOTIFICATION_MISSED_WORK_CALL_TITLE =
            PREFIX + "NOTIFICATION_MISSED_WORK_CALL_TITLE";

    /**
     * Label for notification indicating that call is being made over wifi.
     */
    public static final String NOTIFICATION_WIFI_WORK_CALL_LABEL =
            PREFIX + "NOTIFICATION_WIFI_WORK_CALL_LABEL";
}
