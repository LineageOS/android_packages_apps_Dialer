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

package com.android.dialer.deeplink;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;

import com.android.phone.common.ambient.AmbientConnection;
import com.cyanogen.ambient.analytics.AnalyticsServices;
import com.cyanogen.ambient.common.ConnectionResult;
import com.cyanogen.ambient.common.CyanogenAmbientUtil;
import com.cyanogen.ambient.common.api.AmbientApiClient;
import com.cyanogen.ambient.common.api.PendingResult;
import com.cyanogen.ambient.common.api.ResultCallback;
import com.cyanogen.ambient.deeplink.DeepLink;
import com.cyanogen.ambient.deeplink.DeepLinkApi;
import com.cyanogen.ambient.deeplink.DeepLinkServices;
import com.cyanogen.ambient.deeplink.applicationtype.DeepLinkApplicationType;
import com.cyanogen.ambient.deeplink.linkcontent.DeepLinkContentType;
import com.cyanogen.ambient.deeplink.metrics.DeepLinkMetricsHelper;
import com.cyanogen.ambient.deeplink.metrics.DeepLinkMetricsHelper.Categories;
import com.cyanogen.ambient.deeplink.metrics.DeepLinkMetricsHelper.Events;
import com.cyanogen.ambient.deeplink.metrics.DeepLinkMetricsHelper.Parameters;

import java.util.HashMap;
import java.util.List;

public class DeepLinkIntegrationManager {

    public static DeepLinkIntegrationManager getInstance() {
        if (sInstance == null) {
            sInstance = new DeepLinkIntegrationManager();
        }
        return sInstance;
    }

    private String dummyNumber = "00000000";
    private long dummyTime = 0l;
    private static DeepLinkIntegrationManager sInstance;
    private AmbientApiClient mAmbientApiClient;
    private DeepLinkApi mApi;
    private volatile boolean mConnected = false;

    public void setUp(Context ctx) {
        if(ambientIsAvailable(ctx)) {
            mApi = (DeepLinkApi) DeepLinkServices.API;
            mAmbientApiClient = AmbientConnection.CLIENT.get(ctx);
        }
    }

    public PendingResult<DeepLink.DeepLinkResultList> getPreferredLinksFor(
            ResultCallback<DeepLink.DeepLinkResultList> callback, DeepLinkContentType category,
            Uri uri) {
        PendingResult<DeepLink.DeepLinkResultList> result = null;
        if (mAmbientApiClient.isConnected()) {
            result = mApi.getPreferredLinksForSingleItem(mAmbientApiClient,
                    DeepLinkApplicationType.NOTE, category, uri);
            result.setResultCallback(callback);
        }
        return result;
    }

    public PendingResult<DeepLink.DeepLinkResultList> getPreferredLinksForList(
            ResultCallback<DeepLink.DeepLinkResultList> callback, DeepLinkContentType category,
            List<Uri> uris) {
        PendingResult<DeepLink.DeepLinkResultList> result = null;
        if (mAmbientApiClient.isConnected()) {
            result = mApi.getPreferredLinksForList(mAmbientApiClient,
                    DeepLinkApplicationType.NOTE, category, uris);
            result.setResultCallback(callback);
        }
        return result;
    }

    public void getDefaultPlugin(ResultCallback<DeepLink.StringResultList> callback,
            DeepLinkContentType category) {
        PendingResult<DeepLink.StringResultList> result = null;
        if (mAmbientApiClient.isConnected()) {
            result = mApi.getDefaultProviderDisplayInformation(mAmbientApiClient,
                    DeepLinkApplicationType.NOTE, category,
                    DeepLinkIntegrationManager.generateCallUri(dummyNumber, dummyTime));
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

    public boolean ambientIsAvailable(Context ctx) {
        return CyanogenAmbientUtil.isCyanogenAmbientAvailable(ctx) == CyanogenAmbientUtil.SUCCESS;
    }

    public void sendEvent(Context ctx, Categories categories, Events event,
            HashMap<Parameters, Object> params) {
        if(mAmbientApiClient.isConnected()) {
            DeepLinkMetricsHelper.sendEvent(ctx, categories, event, params, mAmbientApiClient);
        }
    }

    private void sendEvent(Context ctx, DeepLink deepLink, ComponentName cn,
            Categories category, Events event) {

        HashMap<Parameters, Object>
                parameters = new HashMap<Parameters, Object>();

        parameters.put(Parameters.SOURCE, cn.flattenToString());
        parameters.put(Parameters.DESTINATION, deepLink.getPackageName());
        parameters.put(Parameters.CONTENT_TYPE,
                deepLink.getDeepLinkContentType());
        parameters.put(Parameters.DEST_APPLICATION_TYPE,
                deepLink.getApplicationType());
        parameters.put(Parameters.CONTENT_UID, deepLink.getUri().toString());
        sendEvent(ctx, category, event, parameters);
    }

    public void sendContentSentEvent(Context ctx, DeepLink deepLink, ComponentName cn) {
        sendEvent(ctx, deepLink, cn, Categories.USER_ACTIONS, Events.CONTENT_SENT);
    }

    public void sendOpeningExistingEvent(Context ctx, DeepLink deepLink, ComponentName cn) {
        sendEvent(ctx, deepLink, cn, Categories.USER_ACTIONS, Events.OPENING_EXISTING_LINK);
    }


    public void openDeepLinkPreferences(DeepLinkApplicationType deepLinkApplicationType) {
        if (mAmbientApiClient.isConnected()) {
            mApi.openDeepLinkPreferences(mAmbientApiClient, deepLinkApplicationType);
        }
    }

    public void isApplicationTypeEnabled(DeepLinkApplicationType deepLinkApplicationType,
            ResultCallback<DeepLink.BooleanResult> callback) {
        if (mAmbientApiClient.isConnected()) {
            PendingResult<DeepLink.BooleanResult> result = mApi.isApplicationTypeEnabled(
                    mAmbientApiClient, deepLinkApplicationType);
            result.setResultCallback(callback);
        }
    }
}
