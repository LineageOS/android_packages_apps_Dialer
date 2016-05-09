/*
 * Copyright (C) 2016 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.dialer.callerinfo;

import android.app.IntentService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.android.dialer.util.MetricsHelper;
import com.android.phone.common.ambient.AmbientConnection;
import com.cyanogen.ambient.callerinfo.CallerInfoServices;
import com.cyanogen.ambient.common.api.AmbientApiClient;

public class ProviderActivationService extends IntentService {
    public static final String INTENT_EXTRA_PACKAGE = "package";
    public static final String INTENT_EXTRA_COMPONENT = "component";
    public static final String INTENT_EXTRA_SUCCESS = "success";
    public static final String INTENT_EXTRA_METRIC_REASON = "metric_reason";

    public static final int REASON_FIRST_LAUNCH_DIALER = 0;
    public static final int REASON_INCOMING_CALL_FIRST_PROMPT = 1;
    public static final int REASON_INCOMING_CALL_FINAL_PROMPT = 2;
    public static final int REASON_DIALER_SETTINGS = 3;

    private static final String TAG = ProviderActivationService.class.getSimpleName();

    public ProviderActivationService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        boolean success = intent.getBooleanExtra(INTENT_EXTRA_SUCCESS, false);
        sendMetrics(intent, success);

        if (success) {
            connectToProvider(getApplicationContext(), intent);
        }
    }

    private static void connectToProvider(final Context context, Intent intent) {
        final ComponentName provider = intent.getParcelableExtra(INTENT_EXTRA_COMPONENT);
        AmbientApiClient client = AmbientConnection.CLIENT.get(context);
        CallerInfoServices.CallerInfoApi.enablePlugin(client, provider);
    }

    private static void sendMetrics(Intent intent, boolean success) {
        int reason = intent.getIntExtra(INTENT_EXTRA_METRIC_REASON, -1);
        MetricsHelper.Field field = new MetricsHelper.Field(
                MetricsHelper.Fields.PROVIDER_PACKAGE_NAME,
                intent.getStringExtra(INTENT_EXTRA_PACKAGE));
        MetricsHelper.Actions action = success ?
                MetricsHelper.Actions.OPTED_IN : MetricsHelper.Actions.OPTED_OUT;
        MetricsHelper.State state = null;
        switch (reason) {
            case REASON_FIRST_LAUNCH_DIALER:
                state = MetricsHelper.State.FIRST_LAUNCH_DIALER;
                break;
            case REASON_INCOMING_CALL_FIRST_PROMPT:
                state = MetricsHelper.State.AFTER_CALL_ENDED;
                break;
            case REASON_INCOMING_CALL_FINAL_PROMPT:
                state = MetricsHelper.State.AFTER_FINAL_PROMPT;
                break;
            case REASON_DIALER_SETTINGS:
                state = MetricsHelper.State.SETTINGS;
                break;
        }

        MetricsHelper.sendEvent(MetricsHelper.Categories.PROVIDER_STATE_CHANGES,
                action, state, field);
    }
}