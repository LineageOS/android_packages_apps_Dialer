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

package com.android.dialer;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Trace;

import android.util.Log;
import com.android.contacts.common.extensions.ExtensionsFactory;
import com.android.contacts.commonbind.analytics.AnalyticsUtil;
import com.android.dialer.incall.CallMethodHelper;
import com.cyanogen.ambient.analytics.AnalyticsServices;
import com.cyanogen.ambient.callerinfo.CallerInfoServices;
import com.cyanogen.ambient.common.ConnectionResult;
import com.cyanogen.ambient.common.api.AmbientApiClient;
import com.cyanogen.ambient.discovery.DiscoveryManagerServices;
import com.cyanogen.ambient.discovery.NudgeServices;
import com.cyanogen.ambient.incall.InCallServices;
import com.android.dialer.util.SingletonHolder;

public class DialerApplication extends Application {

    private static final String TAG = "DialerApplication";

    public static final SingletonHolder<AmbientApiClient, Context> ACLIENT =
            new SingletonHolder<AmbientApiClient, Context>() {
                private static final String TAG = "Dialer.AmbientSingletonHolder";

                @Override
                protected AmbientApiClient create(Context context) {
                    AmbientApiClient client = new AmbientApiClient.Builder(context)
                            .addApi(AnalyticsServices.API)
                            .addApi(InCallServices.API)
                            .addApi(CallerInfoServices.API)
                            .addApi(NudgeServices.API)
                            .addApi(DiscoveryManagerServices.API)
                            .build();

                    client.registerConnectionFailedListener(
                            new AmbientApiClient.OnConnectionFailedListener() {
                                @Override
                                public void onConnectionFailed(ConnectionResult result) {
                                    Log.w(TAG, "Ambient connection failed: " + result);
                                }
                            });
                    client.registerDisconnectionListener(
                            new AmbientApiClient.OnDisconnectionListener() {
                                @Override
                                public void onDisconnection() {
                                    Log.d(TAG, "Ambient connection disconnected");
                                }
                            });
                    client.registerConnectionCallbacks(
                            new AmbientApiClient.ConnectionCallbacks() {
                                @Override
                                public void onConnected(Bundle connectionHint) {
                                    Log.d(TAG, "Ambient connection established");
                                }

                                @Override
                                public void onConnectionSuspended(int cause) {
                                    Log.d(TAG, "Ambient connection suspended");
                                }
                            });
                    client.connect();
                    return client;
                }
            };

    @Override
    public void onCreate() {
        Trace.beginSection(TAG + " onCreate");
        super.onCreate();
        Trace.beginSection(TAG + " ExtensionsFactory initialization");
        ExtensionsFactory.init(getApplicationContext());
        Trace.endSection();
        Trace.beginSection(TAG + " Analytics initialization");
        AnalyticsUtil.initialize(this);
        Trace.endSection();
        Trace.endSection();

        CallMethodHelper.init(this);
    }
}
