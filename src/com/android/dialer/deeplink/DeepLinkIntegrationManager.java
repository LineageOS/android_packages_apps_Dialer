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
import android.util.ArrayMap;

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

    ArrayMap<Object, PendingResult<DeepLink.BooleanResult>> mPendingResultArrayMap =
            new ArrayMap<Object, PendingResult<DeepLink.BooleanResult>>();

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
    private boolean mShouldQueueRequests = false;

    /**
     * Initialize the API.
     * @param ctx   Context to use for connecting to the API.
     */
    public void setUp(Context ctx) {
        if (ambientIsAvailable(ctx)) {
            mShouldQueueRequests = true;
            mApi = (DeepLinkApi) DeepLinkServices.API;
            mAmbientApiClient = AmbientConnection.CLIENT.get(ctx);
        }
    }

    /**
     * Get the API's preferred Links for the given Uri.
     * @param callback  code to execute when the API has completed its work.
     * @param category  DeepLinkContentType to query for
     * @param uri       The content Uri to ask the API about.
     * @return  Pending result which represents the future completion of the request.
     */
    public PendingResult<DeepLink.DeepLinkResultList> getPreferredLinksFor(
            ResultCallback<DeepLink.DeepLinkResultList> callback, DeepLinkContentType category,
            Uri uri) {
        PendingResult<DeepLink.DeepLinkResultList> result = null;
        if (mShouldQueueRequests) {
            result = mApi.getPreferredLinksForSingleItem(mAmbientApiClient,
                    DeepLinkApplicationType.NOTE, category, uri);
            result.setResultCallback(callback);
        }
        return result;
    }

    /**
     * For the given list of Uri's, find the preferred link for each system supported
     * ApplicationType
     *
     * @param callback  code to execute when the API has finished its work.
     * @param category  DeepLinKContentType to query for (calls, emails etc..)
     * @param uris      List of Uri's the search for a preferred set of links against.
     * @return  Pending result which represents the future completion of the request.
     */
    public PendingResult<DeepLink.DeepLinkResultList> getPreferredLinksForList(
            ResultCallback<DeepLink.DeepLinkResultList> callback, DeepLinkContentType category,
            List<Uri> uris) {
        PendingResult<DeepLink.DeepLinkResultList> result = null;
        if (mShouldQueueRequests) {
            result = mApi.getPreferredLinksForList(mAmbientApiClient,
                    DeepLinkApplicationType.NOTE, category, uris);
            result.setResultCallback(callback);
        }
        return result;
    }

    /**
     * Gets all links for the given authority, across all installed plugins..
     * @param callback  Code to execute upon completion.
     * @param category  DeepLinkContentType to query for API defaults.
     */

    public PendingResult<DeepLink.DeepLinkResultList> getLinksForAuthority(
            ResultCallback<DeepLink.DeepLinkResultList> callback, DeepLinkContentType category,
            String authority) {
        PendingResult<DeepLink.DeepLinkResultList> result = null;
        if (mShouldQueueRequests) {
            result = mApi.getLinksForAuthority(mAmbientApiClient,
                    DeepLinkApplicationType.NOTE, category, authority);
            result.setResultCallback(callback);
        }
        return result;
    }

    /**
     * Get's the default plugin information for display.
     * @param callback  Code to execute upon completion.
     * @param category  DeepLinkContentType to query for API defaults.
     */
    public void getDefaultPlugin(ResultCallback<DeepLink.StringResultList> callback,
            DeepLinkContentType category) {
        PendingResult<DeepLink.StringResultList> result = null;
        if (mShouldQueueRequests) {
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

    /**
     * Checks to ensure ambient is installed and working correctly.
     * @param ctx   context to query against
     * @return      true if ambient is available, false otherwise.
     */
    public boolean ambientIsAvailable(Context ctx) {
        return CyanogenAmbientUtil.isCyanogenAmbientAvailable(ctx) == CyanogenAmbientUtil.SUCCESS;
    }

    /**
     * Send a metrics event of your own design.
     * @param ctx           context to execute against
     * @param categories    Event Categories
     * @param event         Event to report
     * @param params        Specific parameters to include in this event, such as source and
     *                      destination.
     */
    public void sendEvent(Context ctx, Categories categories, Events event,
            HashMap<Parameters, Object> params) {
        if (mShouldQueueRequests) {
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

    /**
     * View a given note in the application in which it was taken.  Also logs metrics events for
     * viewing the note.
     *
     * @param ctx      Context to log metrics against and to start the activity against.
     * @param deepLink The DeepLink for the content to view
     * @param cn       The ComponentName to log as the generator of the metrics event.
     */
    public void viewNote(Context ctx, DeepLink deepLink, ComponentName cn) {
        if (deepLink != null && deepLink.getAlreadyHasContent()) {
            sendOpeningExistingEvent(ctx, deepLink, cn);
            ctx.startActivity(deepLink.createViewIntent());
        }
    }

    /**
     * Report metrics events for creating a new DeepLink between applications.
     *
     * @param ctx       Context to execute against
     * @param deepLink  DeepLink being created
     * @param cn        ComponentName of the package/class which the event originated within.
     */
    public void sendContentSentEvent(Context ctx, DeepLink deepLink, ComponentName cn) {
        sendEvent(ctx, deepLink, cn, Categories.USER_ACTIONS, Events.CONTENT_SENT);
    }

    /**
     * Report metrics events for opening a previously created DeepLink.
     *
     * @param ctx       Context to execute against
     * @param deepLink  DeepLink being opened
     * @param cn        ComponentName of the package/class which the event originated within.
     */
    public void sendOpeningExistingEvent(Context ctx, DeepLink deepLink, ComponentName cn) {
        sendEvent(ctx, deepLink, cn, Categories.USER_ACTIONS, Events.OPENING_EXISTING_LINK);
    }

    /**
     * Opens a preference activity for the DeepLinkApi to allow users to enable/disable
     * functionality.
     *
     * @param deepLinkApplicationType   DepeLinkApplicationType to open preferences for.
     */
    public void openDeepLinkPreferences(DeepLinkApplicationType deepLinkApplicationType) {
        if (mShouldQueueRequests) {
            mApi.openDeepLinkPreferences(mAmbientApiClient, deepLinkApplicationType);
        }
    }

    /**
     * Returns true if the given DeepLinkApplicationType is enabled, false if not.
     *
     * @param deepLinkApplicationType   DeepLinkApplicationType to check the status of
     * @param callback                  Code to execute upon completion of the query.
     */
    public void isApplicationTypeEnabled(DeepLinkApplicationType deepLinkApplicationType,
            ResultCallback<DeepLink.BooleanResult> callback) {
        if (mShouldQueueRequests) {
            completeEnabledRequest(callback);
            PendingResult<DeepLink.BooleanResult> request =
                    mApi.isApplicationTypeEnabled(mAmbientApiClient, deepLinkApplicationType);
            request.setResultCallback(callback);
            mPendingResultArrayMap.put(callback, request);
        }
    }

    /**
     * Takes a ResultCallback<?>.toString() as an argument, cancels any pending requests which have
     * the argument set as the callback method, removes them from the list of callbacks to track.
     *
     * The callbacks are tracked from the moment a query is executed against the API and must be
     * cleaned up when they are completed, or when the application context becomes invalid.
     *
     * @param toCancel key to use for removing objects in PendingResultArrayMap.
     */
    public void completeEnabledRequest(Object toCancel) {
        if (mPendingResultArrayMap.containsKey(toCancel)) {
            mPendingResultArrayMap.get(toCancel).cancel();
            mPendingResultArrayMap.remove(toCancel);
        }
    }
}
