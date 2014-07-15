/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.dialer.cmstats;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

import com.android.dialer.R;

public class DialerStats {

    private static final String ANALYTIC_INTENT = "com.cyngn.cmstats.action.SEND_ANALYTIC_EVENT";
    private static final String ANALYTIC_PERMISSION = "com.cyngn.cmstats.RECEIVE_ANALYTICS";

    public static final String TRACKING_ID = "tracking_id";

    public static final class Fields {
        public static final String EVENT_CATEGORY = "category";
        public static final String EVENT_ACTION = "action";
        public static final String EVENT_LABEL = "label";
        public static final String EVENT_VALUE = "value";
    }

    public static void sendEvent(Context context, String category, String action,
                                 String label, String value) {

        if (!StatsUtils.isStatsPackageInstalled(context)
                || !StatsUtils.isStatsCollectionEnabled(context)) {
            return;
        }

        // Create new intent
        Intent intent = new Intent();
        intent.setAction(ANALYTIC_INTENT);

        // add tracking id
        intent.putExtra(TRACKING_ID, context.getResources().getString(R.string.ga_trackingId));
        // append
        intent.putExtra(Fields.EVENT_CATEGORY, category);
        intent.putExtra(Fields.EVENT_ACTION, action);

        // check if exist
        if (label != null) {
            intent.putExtra(Fields.EVENT_LABEL, label);
        }

        if (value != null) {
            intent.putExtra(Fields.EVENT_VALUE, value);
        }

        // broadcast for internal package
        context.sendBroadcastAsUser(intent, new UserHandle(UserHandle.USER_CURRENT), ANALYTIC_PERMISSION);
    }

    public static void sendEvent(Context context, String category, String action) {
        sendEvent(context, category, action, null, null);
    }
}
