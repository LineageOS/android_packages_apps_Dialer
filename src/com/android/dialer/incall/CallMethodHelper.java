/*
 * Copyright (C) 2015 The CyanogenMod Project
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

package com.android.dialer.incall;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Handler;
import android.util.Log;
import com.android.dialer.DialerApplication;
import com.cyanogen.ambient.common.api.AmbientApiClient;
import com.cyanogen.ambient.common.api.Result;
import com.cyanogen.ambient.common.api.ResultCallback;
import com.cyanogen.ambient.analytics.Event;
import com.cyanogen.ambient.discovery.NudgeServices;
import com.cyanogen.ambient.discovery.results.BundleResult;
import com.cyanogen.ambient.discovery.util.NudgeKey;
import com.cyanogen.ambient.incall.InCallApi;
import com.cyanogen.ambient.incall.InCallPluginStatus;
import com.cyanogen.ambient.incall.InCallServices;
import com.cyanogen.ambient.incall.extension.CreditBalance;
import com.cyanogen.ambient.incall.extension.CreditInfo;
import com.cyanogen.ambient.incall.extension.GetCreditInfoResult;
import com.cyanogen.ambient.incall.extension.ICallCreditListener;
import com.cyanogen.ambient.incall.extension.StatusCodes;
import com.cyanogen.ambient.incall.results.AuthenticationStateResult;
import com.cyanogen.ambient.incall.results.GetCreditInfoResultResult;
import com.cyanogen.ambient.incall.results.InCallProviderInfoResult;
import com.cyanogen.ambient.incall.results.InstalledPluginsResult;

import com.android.dialer.incall.CallMethodInfo;
import com.android.dialer.R;
import com.cyanogen.ambient.incall.results.MimeTypeResult;
import com.cyanogen.ambient.incall.results.PendingIntentResult;
import com.cyanogen.ambient.incall.results.PluginStatusResult;
import com.cyanogen.ambient.incall.util.InCallProviderInfo;
import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.cyanogen.ambient.incall.util.InCallHelper.NO_COLOR;

/**
 *  Call Method Helper - In charge of keeping a running and updated hashmap of all InCallProviders
 *  currently installed.
 *
 *  Fragments and Activities can subscribe to changes with subscribe.
 *
 */
public class CallMethodHelper {

    private static CallMethodHelper sInstance;

    AmbientApiClient mClient;
    Context mContext;
    InCallApi mInCallApi;
    Handler mMainHandler;
    private static List<ComponentName> mInstalledPlugins;
    private static HashMap<ComponentName, CallMethodInfo> mCallMethodInfos = new HashMap<>();
    private static HashMap<ComponentName, ICallCreditListener> mCallCreditListeners = new
            HashMap<>();
    private static HashMap<String, CallMethodReceiver> mRegisteredClients = new HashMap<>();
    private static boolean dataHasBeenBroadcastPreviously = false;

    // To prevent multiple broadcasts and force us to wait for all items to be complete
    // this is the count of callbacks we should get for each item. Increase this if we add more.
    private static int EXPECTED_RESULT_CALLBACKS = 8;

    // To prevent multiple broadcasts and force us to wait for all items to be complete
    // this is the count of callbacks we should get for each item. Increase this if we add more.
    private static int EXPECTED_DYNAMIC_RESULT_CALLBACKS = 2;

    // Keeps track of the number of callbacks we have from AmbientCore. Reset this to 0
    // immediately after all callbacks are accounted for.
    private static int callbackCount = 0;

    private static final String TAG = CallMethodHelper.class.getSimpleName();
    private static final boolean DEBUG = true;

    public interface CallMethodReceiver {
        void onChanged(HashMap<ComponentName, CallMethodInfo> callMethodInfos);
    }

    /**
     * Broadcasts mCallMethodInfos to all registered clients on the Main thread.
     */
    private static void broadcast() {
        getInstance().mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (CallMethodReceiver client : mRegisteredClients.values()) {
                    client.onChanged(mCallMethodInfos);
                }
                if (DEBUG) {
                    for (CallMethodInfo cmi : mCallMethodInfos.values()) {
                        Log.v("BIRD", "Broadcast: " + cmi.mName);
                    }
                }
                dataHasBeenBroadcastPreviously = true;
                callbackCount = 0;
            }
        });
    }

    /**
     * Helper method for subscribed clients to remove any item that is not enabled from the hashmap
     * @param input HashMap returned from a broadcast
     * @param output HashMap with only enabled items
     */
    public static void removeDisabled(HashMap<ComponentName, CallMethodInfo> input,
                                      HashMap<ComponentName, CallMethodInfo> output) {

        for (Map.Entry<ComponentName, CallMethodInfo> entry : input.entrySet()) {
            ComponentName key = entry.getKey();
            CallMethodInfo value = entry.getValue();

            if (value.mStatus == InCallPluginStatus.ENABLED) {
                output.put(key, value);
            }
        }
    }

    /***
     * Registers the client, on register returns boolean if
     * callMethodInfo data is already collected and the initial broadcast has been sent.
     * @param id unique string for the client
     * @param cmr client receiver
     * @return boolean isempty
     */
    public static synchronized boolean subscribe(String id, CallMethodReceiver cmr) {
        mRegisteredClients.put(id, cmr);

        for (ComponentName callCreditProvider : mCallCreditListeners.keySet()) {
            if (mCallCreditListeners.get(callCreditProvider) == null) {
               /* CallCreditListenerImpl listener = CallCreditListenerImpl.getInstance
                        (callCreditProvider);
                getInstance().mInCallApi.addCreditListener(getInstance().mClient,
                        callCreditProvider, listener);
                mCallCreditListeners.put(callCreditProvider, listener); */
            }
        }

        return dataHasBeenBroadcastPreviously;
    }

    /**
     * Unsubscribes the client. All clients should unsubscribe when they are removed.
     * @param id of the client to remove
     */
    public static synchronized void unsubscribe(String id) {
        mRegisteredClients.remove(id);

        if (mRegisteredClients.size() == 0) {
            for (ComponentName callCreditProvider : mCallCreditListeners.keySet()) {
                if (mCallCreditListeners.get(callCreditProvider) != null) {
                    getInstance().mInCallApi.removeCreditListener(getInstance().mClient,
                            callCreditProvider, mCallCreditListeners.get(callCreditProvider));
                }
            }
        }
    }

    /**
     * Get a single instance of our call method helper. There should only be ever one instance.
     * @return
     */
    private static synchronized CallMethodHelper getInstance() {
        if (sInstance == null) {
            sInstance = new CallMethodHelper();
        }
        return sInstance;
    }

    /**
     * Start our Helper and kick off our first ModCore queries.
     * @param context
     */
    public static void init(DialerApplication context) {
        CallMethodHelper helper = getInstance();
        helper.mContext = context;
        helper.mClient = DialerApplication.ACLIENT.get(context);
        helper.mInCallApi = InCallServices.getInstance();
        helper.mMainHandler = new Handler(context.getMainLooper());
        refresh();
    }

    /**
     * *sip* ahhhh so refreshing
     */
    public static void refresh() {
        updateCallPlugins();
    }

    /**
     * Refresh just the possibly changing items
     *
     * This should only be called once dataHasBeenBroadcastPreviously is true.
     */
    public static void refreshDynamic() {
        for(ComponentName cn : mCallMethodInfos.keySet()) {
            getCreditInfo(cn, true);
            getCallMethodAuthenticated(cn, true);
        }
    }

    /**
     * This is helpful for items that don't want to subscribe to updates or for things that
     * need a quick CMI and have a component name.
     * @param cn Component name wanted.
     * @return specific call method when given a component name.
     */
    public static CallMethodInfo getCallMethod(ComponentName cn) {
        return mCallMethodInfos.get(cn);
    }

    /**
     * This is useful for items that subscribe after the initial broadcast has been sent out and
     * need to go get some data right away
     * @return the current HashMap of CMIs.
     */
    public static HashMap<ComponentName, CallMethodInfo> getAllCallMethods() {
        return mCallMethodInfos;
    }

    /**
     * A few items need a list of mime types in a comma delimited list. Since we are already
     * querying all the plugins. We can easily build this list ahead of time.
     *
     * Items that require this should subscribe and grab this updated list when needed.
     * @return string of all (not limited to enabled) mime types
     */
    public static String getAllMimeTypes() {
        String mimeTypes = "";

        List<String> mimeTypesList = new ArrayList<>();

        for (CallMethodInfo cmi : mCallMethodInfos.values()) {
            mimeTypesList.add(cmi.mMimeType);
        }

        if (!mimeTypesList.isEmpty()) {
            mimeTypes = Joiner.on(",").skipNulls().join(mimeTypesList);
        }
        return mimeTypes;
    }

    /**
     * A few items need a list of mime types in a comma delimited list. Since we are already
     * querying all the plugins. We can easily build this list ahead of time.
     *
     * Items that require this should subscribe and grab this updated list when needed.
     * @return string of enabled mime types
     */
    public static String getAllEnabledMimeTypes() {
        String mimeTypes = "";

        List<String> enabledMimeTypes = new ArrayList<>();

        for (CallMethodInfo cmi : mCallMethodInfos.values()) {
            if (cmi.mStatus == InCallPluginStatus.ENABLED) {
                enabledMimeTypes.add(cmi.mMimeType);
            }
        }

        if (!enabledMimeTypes.isEmpty()) {
            mimeTypes = Joiner.on(",").skipNulls().join(enabledMimeTypes);
        }
        return mimeTypes;
    }

    public static void updateCreditInfo(ComponentName name, GetCreditInfoResult gcir) {
        CallMethodInfo cmi = getCallMethodIfExists(name);
        if (cmi != null) {
            if (gcir == null || gcir.creditInfo == null) {
                // Build zero credit dummy if no result found.
                cmi.mProviderCreditInfo =
                        new CreditInfo(new CreditBalance(0, null), null);
            } else {
                cmi.mProviderCreditInfo = gcir.creditInfo;
            }

            // Since a CallMethodInfo object was updated here, we should let the subscribers know
            broadcast();
        }
    }

    /**
     * Broadcast to subscribers once we know we've gathered all our data. Do not do this until we
     * have everything we need for sure.
     *
     * This method is called after every callback from AmbientCore. We will keep track of all of
     * the callbacks, once we have accounted for all callbacks from all plugins, we can go ahead
     * and update subscribers.
     */
    private static void maybeBroadcastToSubscribers() {
        maybeBroadcastToSubscribers(false);
    }

    private static void maybeBroadcastToSubscribers(boolean broadcastDynamic) {
        int expectedCount;

        ++callbackCount;
        if (broadcastDynamic) {
            expectedCount = EXPECTED_DYNAMIC_RESULT_CALLBACKS;
        } else {
            expectedCount = EXPECTED_RESULT_CALLBACKS;
        }

        if (callbackCount == (expectedCount  * mInstalledPlugins.size()))  {
            // we are on the last item. broadcast updated hashmap
            broadcast();
        }
    }

    /**
     * In order to speed up the process we make calls for providers that may be invalid
     * To prevent this, make sure every resultcallback uses this before filling in the hashmap.
     * @param cn componentname
     * @return callmethodinfo if valid, otherwise null
     */
    public static CallMethodInfo getCallMethodIfExists(ComponentName cn) {
        if (mCallMethodInfos.containsKey(cn)) {
            return mCallMethodInfos.get(cn);
        } else {
            return null;
        }
    }

    /**
     * Prepare to query and fire off ModCore calls in all directions
     */
    private static void updateCallPlugins() {
        getInstance().mInCallApi.getInstalledPlugins(getInstance().mClient)
                .setResultCallback(new ResultCallback<InstalledPluginsResult>() {
            @Override
            public void onResult(InstalledPluginsResult installedPluginsResult) {
                // got installed components
                mInstalledPlugins = installedPluginsResult.components;

                mCallMethodInfos.clear();

                if (mInstalledPlugins.size() == 0) {
                    broadcast();
                }

                for (ComponentName cn : mInstalledPlugins) {
                    mCallMethodInfos.put(cn, new CallMethodInfo());
                    getCallMethodInfo(cn);
                    getCallMethodStatus(cn);
                    getCallMethodMimeType(cn);
                    getCallMethodAuthenticated(cn, false);
                    getSettingsIntent(cn);
                    getCreditInfo(cn, false);
                    getManageCreditsIntent(cn);
                    checkLowCreditConfig(cn);
                    // If you add any more callbacks, be sure to update EXPECTED_RESULT_CALLBACKS
                    // and EXPECTED_DYNAMIC_RESULT_CALLBACKS if the callback is dynamic
                    // with the proper count.
                }
            }
        });
    }

    /**
     * Get our basic CMI metadata
     * @param cn
     */
    private static void getCallMethodInfo(final ComponentName cn) {
        getInstance().mInCallApi.getProviderInfo(getInstance().mClient, cn)
                .setResultCallback(new ResultCallback<InCallProviderInfoResult>() {
            @Override
            public void onResult(InCallProviderInfoResult inCallProviderInfoResult) {

                InCallProviderInfo icpi = inCallProviderInfoResult.inCallProviderInfo;
                if (icpi == null) {
                    mCallMethodInfos.remove(cn);
                    return;
                }

                PackageManager packageManager =  getInstance().mContext.getPackageManager();

                Resources pluginResources = null;
                try {
                    pluginResources = packageManager.getResourcesForApplication(
                            cn.getPackageName());
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(TAG, "Plugin isn't installed: " + cn);
                    mCallMethodInfos.remove(cn);
                    return;
                }

                synchronized (mCallMethodInfos) {
                    CallMethodInfo cmi = getCallMethodIfExists(cn);

                    if (cmi == null) {
                        return;
                    }

                    try {
                        cmi.mBrandIcon = pluginResources.getDrawable(icpi.getBrandIcon(), null);
                        cmi.mBadgeIcon = pluginResources.getDrawable(icpi.getBadgeIcon(), null);
                        cmi.mLoginIcon = pluginResources.getDrawable(icpi.getLoginIcon(), null);
                        cmi.mActionOneIcon = pluginResources.getDrawable(icpi.getActionOneIcon(), null);
                        cmi.mActionTwoIcon = pluginResources.getDrawable(icpi.getActionTwoIcon(), null);
                    } catch (Resources.NotFoundException e) {
                        Log.e(TAG, "Resource Not found: " + cn);
                        mCallMethodInfos.remove(cn);
                        return;
                    }

                    cmi.mComponent = cn;
                    cmi.mName = icpi.getTitle();
                    cmi.mSummary = icpi.getSummary();
                    cmi.mSlotId = -1;
                    cmi.mSubId = -1;
                    cmi.mColor = NO_COLOR;
                    cmi.mSubscriptionButtonText = icpi.getSubscriptionButtonText();
                    cmi.mCreditButtonText = icpi.getCreditsButtonText();
                    cmi.mT9HintDescription = icpi.getT9HintDescription();
                    cmi.pluginResources = pluginResources;
                    cmi.mActionOneText = icpi.getActionOneTitle();
                    cmi.mActionTwoText = icpi.getActionTwoTitle();
                    cmi.mIsInCallProvider = true;

                    mCallMethodInfos.put(cn, cmi);
                    maybeBroadcastToSubscribers();
                }
            }
        });
    }

    /**
     * Get our plugin enabled status
     * @param cn
     */
    private static void getCallMethodStatus(final ComponentName cn) {
        getInstance().mInCallApi.getPluginStatus(getInstance().mClient, cn)
                .setResultCallback(new ResultCallback<PluginStatusResult>() {
            @Override
            public void onResult(PluginStatusResult pluginStatusResult) {
                synchronized (mCallMethodInfos) {
                    CallMethodInfo cmi = getCallMethodIfExists(cn);
                    if (cmi != null) {
                        cmi.mStatus = pluginStatusResult.status;
                        mCallMethodInfos.put(cn, cmi);
                        maybeBroadcastToSubscribers();
                    }
                }
            }
        });
    }

    /**
     * Send an event to the component
     * @param cn componentName to send the data to.
     */
    public static void shipAnalyticsToPlugin(final ComponentName cn, Event e) {
        if (cn == null) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "componentName: " + cn.toShortString());
            Log.d(TAG, "Event: " + e.toString());
        }
        getInstance().mInCallApi.sendAnalyticsEventToPlugin(getInstance().mClient, cn, e)
                .setResultCallback(new ResultCallback<Result>() {
            @Override
            public void onResult(Result result) {
                if (DEBUG) {
                    Log.v(TAG, "Event sent with result: " + result.getStatus().getStatusMessage());
                }
            }
        });
    }

    /**
     * Get the call method mime type
     * @param cn
     */
    private static void getCallMethodMimeType(final ComponentName cn) {
        getInstance().mInCallApi.getCallableMimeType(getInstance().mClient, cn)
                .setResultCallback(new ResultCallback<MimeTypeResult>() {
            @Override
            public void onResult(MimeTypeResult mimeTypeResult) {
                synchronized (mCallMethodInfos) {
                    CallMethodInfo cmi = getCallMethodIfExists(cn);
                    if (cmi != null) {
                        cmi.mMimeType = mimeTypeResult.mimeType;
                        mCallMethodInfos.put(cn, cmi);
                        maybeBroadcastToSubscribers();
                    }
                }
            }
        });
    }

    /**
     * Get the Authentication state of the callmethod
     * @param cn
     */
    private static void getCallMethodAuthenticated(final ComponentName cn,
                                                   final boolean dynamicRefresh) {
        getInstance().mInCallApi.getAuthenticationState(getInstance().mClient, cn)
                .setResultCallback(new ResultCallback<AuthenticationStateResult>() {
            @Override
            public void onResult(AuthenticationStateResult result) {
                synchronized (mCallMethodInfos) {
                    CallMethodInfo cmi = getCallMethodIfExists(cn);
                    if (cmi != null) {
                        cmi.mIsAuthenticated = result.result == StatusCodes.AuthenticationState
                                .LOGGED_IN;
                        mCallMethodInfos.put(cn, cmi);
                        maybeBroadcastToSubscribers(dynamicRefresh);
                    }
                }
            }
        });
    }

    /**
     * Get the settings intent for the callmethod
     * @param cn
     */
    private static void getSettingsIntent(final ComponentName cn) {
        getInstance().mInCallApi.getSettingsIntent(getInstance().mClient, cn)
                .setResultCallback(new ResultCallback<PendingIntentResult>() {
                    @Override
                    public void onResult(PendingIntentResult pendingIntentResult) {
                        synchronized (mCallMethodInfos) {
                            CallMethodInfo cmi = getCallMethodIfExists(cn);
                            if (cmi != null) {
                                cmi.mSettingsIntent = pendingIntentResult.intent;
                                mCallMethodInfos.put(cn, cmi);
                                maybeBroadcastToSubscribers();
                            }
                        }
                    }
                });
    }

    private static void getCreditInfo(final ComponentName cn,
                                      final boolean dynamicRefresh) {
        // Let's attach a listener so that we can continue to listen to any credit changes
        if (mCallCreditListeners.get(cn) == null) {
           /* CallCreditListenerImpl listener = CallCreditListenerImpl.getInstance(cn);
            getInstance().mInCallApi.addCreditListener(getInstance().mClient, cn, listener);
            mCallCreditListeners.put(cn, listener); */
        }
        getInstance().mInCallApi.getCreditInfo(getInstance().mClient, cn)
                .setResultCallback(new ResultCallback<GetCreditInfoResultResult>() {
                    @Override
                    public void onResult(GetCreditInfoResultResult getCreditInfoResultResult) {
                        CallMethodInfo cmi = getCallMethodIfExists(cn);
                        if (cmi != null) {
                            GetCreditInfoResult gcir = getCreditInfoResultResult.result;
                            if (gcir == null || gcir.creditInfo == null) {
                                // Build zero credit dummy if no result found.
                                cmi.mProviderCreditInfo =
                                        new CreditInfo(new CreditBalance(0, null), null);
                            } else {
                                cmi.mProviderCreditInfo = gcir.creditInfo;
                            }
                            mCallMethodInfos.put(cn, cmi);
                            maybeBroadcastToSubscribers(dynamicRefresh);
                        }
                    }
                });
    }

    private static void getManageCreditsIntent(final ComponentName cn) {
        getInstance().mInCallApi.getManageCreditsIntent(getInstance().mClient, cn)
                .setResultCallback(new ResultCallback<PendingIntentResult>() {
                    @Override
                    public void onResult(PendingIntentResult pendingIntentResult) {
                        CallMethodInfo cmi = getCallMethodIfExists(cn);
                        if (cmi != null) {
                            cmi.mManageCreditIntent = pendingIntentResult.intent;
                            mCallMethodInfos.put(cn, cmi);
                            maybeBroadcastToSubscribers();
                        }
                    }
                });
    }

    private static void checkLowCreditConfig(final ComponentName cn) {
        // find a nudge component if it exists for this package
        Intent nudgeIntent = new Intent("cyanogen.service.NUDGE_PROVIDER");
        nudgeIntent.setPackage(cn.getPackageName());
        List<ResolveInfo> resolved = getInstance().mContext.getPackageManager()
                .queryIntentServices(nudgeIntent, 0);
        if (resolved != null && !resolved.isEmpty()) {
            ResolveInfo result = resolved.get(0);
            ComponentName nudgeComponent = new ComponentName(result.serviceInfo.applicationInfo
                    .packageName, result.serviceInfo.name);
            collectLowCreditConfig(cn, nudgeComponent);
            return;
        }

        // if a nudge component doesn't exist, just finish here
        maybeBroadcastToSubscribers();
    }

    private static void collectLowCreditConfig(final ComponentName pluginComponent, final
                                               ComponentName nudgeComponent) {
        NudgeServices.NudgeApi.getConfigurationForKey(getInstance().mClient, nudgeComponent,
                NudgeKey.INCALL_CREDIT_NUDGE).setResultCallback(new ResultCallback<BundleResult>() {
            @Override
            public void onResult(BundleResult bundleResult) {
                CallMethodInfo cmi = getCallMethodIfExists(pluginComponent);
                if (cmi != null) {
                    if (bundleResult != null && bundleResult.bundle != null &&
                            bundleResult.bundle.containsKey(NudgeKey
                                    .INCALL_PARAM_CREDIT_WARN)) {
                        cmi.mCreditWarn = bundleResult.bundle.getFloat(NudgeKey
                                .INCALL_PARAM_CREDIT_WARN);
                        mCallMethodInfos.put(pluginComponent, cmi);
                    }
                    maybeBroadcastToSubscribers();
                }
            }
        });
    }
}
