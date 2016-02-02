/*
 * Copyright (C) 2016 The CyanogenMod Project
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

package com.android.dialer;

import android.net.Uri;
import android.provider.CallLog;
import android.content.Context;

import com.cyanogen.ambient.common.api.AmbientApiClient;
import com.cyanogen.ambient.common.api.PendingResult;
import com.cyanogen.ambient.common.api.ResultCallback;
import com.cyanogen.ambient.common.ConnectionResult;
import com.cyanogen.ambient.deeplink.DeepLink;
import com.cyanogen.ambient.deeplink.DeepLinkApi;
import com.cyanogen.ambient.deeplink.DeepLinkServices;
import com.cyanogen.ambient.deeplink.linkcontent.DeepLinkContentType;

import java.util.List;

public class DeepLinkIntegrationManager {

    public static DeepLinkIntegrationManager getInstance() {
        if (sInstance == null) {
            sInstance = new DeepLinkIntegrationManager();
        }
        return sInstance;
    }

    private static DeepLinkIntegrationManager sInstance;
    private AmbientApiClient mAmbientApiClient;
    private DeepLinkApi mApi;
    private boolean mConnected = false;

    public void setUp(Context ctx) {
        mApi = (DeepLinkApi) DeepLinkServices.API;
        mAmbientApiClient = new AmbientApiClient.Builder(ctx).addApi(DeepLinkServices.API).build();

        mAmbientApiClient.registerConnectionFailedListener(
                new AmbientApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult connectionResult) {
                        mConnected = false;
                    }
                });

        mAmbientApiClient.registerDisconnectionListener(
                new AmbientApiClient.OnDisconnectionListener() {
                    @Override
                    public void onDisconnection() {
                        mConnected = false;
                    }
                });

        mAmbientApiClient.registerConnectionCallbacks(new AmbientApiClient.ConnectionCallbacks() {
            @Override
            public void onConnected(android.os.Bundle bundle) {
                mConnected = true;
            }

            @Override
            public void onConnectionSuspended(int i) {
                mConnected = false;
            }
        });
        mAmbientApiClient.connect();
    }

    public void getPreferredLinksFor(
            ResultCallback<DeepLink.DeepLinkResultList> callback,
            DeepLinkContentType category,
            Uri uri) {
        if (mConnected) {
            PendingResult<DeepLink.DeepLinkResultList>
                    result = mApi.getPreferredLinksForSingleItem(mAmbientApiClient, category, uri);
            result.setResultCallback(callback);
        }
    }

    public void getPreferredLinksForList(
            ResultCallback<DeepLink.DeepLinkResultList> callback,
            DeepLinkContentType category,
            List<Uri> uris) {
        if (mConnected) {
            PendingResult<DeepLink.DeepLinkResultList>
                    result = mApi.getPreferredLinksForList(mAmbientApiClient, category, uris);
            result.setResultCallback(callback);
        }
    }

    /**
     * Generate a uri which will identify the call for a given number and timestamp
     *
     * @param number - the phone number dialed
     * @param time   - the time the call occured
     * @return Uri identifying the call.
     */
    public static Uri generateCallUri(String number, long time) {
        return Uri.parse(CallLog.AUTHORITY + "." + number + "." + time);
    }
}
