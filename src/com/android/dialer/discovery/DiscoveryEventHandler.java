package com.android.dialer.discovery;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import com.android.dialer.DialtactsActivity;
import com.android.phone.common.ambient.AmbientConnection;
import com.android.phone.common.incall.utils.CallMethodUtils;
import com.android.phone.common.util.ImageUtils;

import com.cyanogen.ambient.common.api.AmbientApiClient;
import com.cyanogen.ambient.discovery.DiscoveryManagerServices;
import com.cyanogen.ambient.discovery.NudgeServices;
import com.cyanogen.ambient.discovery.nudge.NotificationNudge;
import com.cyanogen.ambient.discovery.nudge.Nudge;
import com.cyanogen.ambient.discovery.util.NudgeKey;
import com.cyanogen.ambient.discovery.results.NudgablePluginsResult;
import com.cyanogen.ambient.incall.InCallApi;
import com.cyanogen.ambient.incall.InCallServices;
import com.cyanogen.ambient.incall.results.InCallProviderInfoResult;
import com.cyanogen.ambient.incall.results.PluginStatusResult;
import com.cyanogen.ambient.incall.results.InstalledPluginsResult;

import com.cyanogen.ambient.plugin.PluginStatus;
import com.cyanogen.ambient.common.api.ResultCallback;


import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *  A small, lean, nudge'r. For loading data from mods that can provide nudges.
 *
 *  All Dialer nudge events route through here, and if found worthy, will send a nudge to
 *  the Discovery Nudge Service in ModCore.
 */
public class DiscoveryEventHandler {

    private static final String TAG = "DiscoveryEventHandler";

    private Context mContext;
    private AmbientApiClient mClient;
    private HashMap<String, Bundle> availableNudges = new HashMap<String, Bundle>();
    private List<ComponentName> plugins = new ArrayList<ComponentName>();

    // Key for this instance
    private static String mKey;

    public DiscoveryEventHandler(Context context) {
        mContext = context;
        mClient = AmbientConnection.CLIENT.get(context);
    }

    public void getNudgeProvidersWithKey(final String key) {
        getNudgeProvidersWithKey(key, false);
    }

    /* package */ void getNudgeProvidersWithKey(final String key, final boolean isTesting) {
        mKey = key;
        getAvailableNudgesForKey(key, isTesting);
        getInstalledPlugins();
    }

    private void getAvailableNudgesForKey(final String key, final boolean isTesting) {
        NudgeServices.NudgeApi.getAvailableNudgesForKey(mClient, key)
                .setResultCallback(new ResultCallback<NudgablePluginsResult>() {
                    @Override
                    public void onResult(NudgablePluginsResult plugins) {
                        Map nudgePlugins = plugins.components;
                        if (nudgePlugins == null || nudgePlugins.size() == 0) {
                            return;
                        }

                        for (Object entry : nudgePlugins.entrySet()) {
                            Map.Entry<ComponentName, Bundle> theEntry
                                    = (Map.Entry<ComponentName, Bundle>) entry;

                            Bundle b = theEntry.getValue();

                            if (!validateShouldShowNudge(key, b) && !isTesting) {
                                // Nudge not yet ready for this item.
                                continue;
                            }

                            availableNudges.put(theEntry.getKey().getPackageName(), b);
                        }

                        getStatusWhenReady();
                    }
                });
    }


    /**
     * Get installed plugins
     */
    private void getInstalledPlugins() {
        InCallServices.getInstance().getInstalledPlugins(mClient)
                .setResultCallback(new ResultCallback<InstalledPluginsResult>() {
                    @Override
                    public void onResult(InstalledPluginsResult installedPluginsResult) {
                        plugins = installedPluginsResult.components;

                        if (plugins == null || plugins.size() == 0) {
                            return;
                        }
                        getStatusWhenReady();
                    }
                });
    }

    /**
     * Get our plugin enabled status
     * @param cn
     */
    private void getCallMethodStatus(final ComponentName cn) {
        InCallServices.getInstance().getPluginStatus(mClient, cn)
            .setResultCallback(new ResultCallback<PluginStatusResult>() {
                @Override
                public void onResult(PluginStatusResult pluginStatusResult) {

                    boolean pluginIsApplicable = pluginStatusResult.status != PluginStatus.DISABLED
                            && pluginStatusResult.status != PluginStatus.UNAVAILABLE;

                    if (!pluginIsApplicable) {
                        plugins.remove(cn);
                        return;
                    }

                    getCallMethodIcon(cn);
                }
            });
    }

    private void getCallMethodIcon(final ComponentName cn) {
        InCallServices.getInstance().getProviderInfo(mClient, cn)
                .setResultCallback(new ResultCallback<InCallProviderInfoResult>() {
                    @Override
                    public void onResult(InCallProviderInfoResult providerInfo) {
                        if (providerInfo != null && providerInfo.inCallProviderInfo != null) {
                            try {
                                Resources pluginResources = mContext.getPackageManager()
                                        .getResourcesForApplication(cn.getPackageName());

                                Drawable d = pluginResources.getDrawable(
                                        providerInfo.inCallProviderInfo.getBrandIcon(), null);

                                createNudge(cn, ImageUtils.drawableToBitmap(d));
                            } catch (Resources.NotFoundException e) {
                                Log.e(TAG, "Unable to retrieve icon for plugin: " + cn);
                            } catch (PackageManager.NameNotFoundException e) {
                                Log.e(TAG, "Plugin isn't installed: " + cn);
                            }
                        }
                    }
        });
    }

    private void getStatusWhenReady() {
        if (plugins == null || plugins.size() == 0
                || availableNudges == null || availableNudges.size() == 0) {
            // Not ready or never will be, just bail man.
            return;
        }
        for (ComponentName cn : plugins) {
            if (availableNudges.containsKey(cn.getPackageName())) {
                getCallMethodStatus(cn);
            }
        }
    }

    private void createNudge(ComponentName component, Bitmap notificationIcon) {
        Bundle b = availableNudges.get(component.getPackageName());

        String title = b.getString(NudgeKey.NUDGE_PARAM_TITLE);
        String body = b.getString(NudgeKey.NOTIFICATION_PARAM_BODY);
        Parcelable[] actions = b.getParcelableArray(NudgeKey.NOTIFICATION_PARAM_NUDGE_ACTIONS);

        NotificationNudge nn = new NotificationNudge(component.getPackageName(),
                Nudge.Type.IMMEDIATE, title, body);

        for (Parcelable action : actions) {
            NotificationNudge.Button button = (NotificationNudge.Button) action;
            nn.addButton(button);
        }

        nn.setLargeIcon(notificationIcon);
        nn.setOnShowIntent(buildActionIntent(body,
                DiscoverySignalReceiver.DISCOVERY_NUDGE_SHOWN, component));
        nn.setContentIntent(buildActionIntent(body,
                DiscoverySignalReceiver.DISCOVERY_NUDGE_DISMISS, component));

        DiscoveryManagerServices.DiscoveryManagerApi.publishNudge(mClient, nn);
    }

    private PendingIntent buildActionIntent(String body, String action, ComponentName component) {
        Intent intent = new Intent(action);
        String nudgeID;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(body.getBytes());
            nudgeID = new String(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "No Algo, defaulting to unknown", e);
            nudgeID = "unknown";
        }
        intent.putExtra(DiscoverySignalReceiver.NUDGE_ID, nudgeID);
        intent.putExtra(DiscoverySignalReceiver.NUDGE_KEY, mKey);
        intent.putExtra(DiscoverySignalReceiver.NUDGE_COMPONENT, component.flattenToShortString());

        return PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_ONE_SHOT);
    }

    /**
     * Validates if a nudge should be shown.
     *
     * @param key the nudge key we're validating
     * @param b bundle with nudge data
     * @return true if the nudge is good to go, false if the nudge should not show.
     */
    private boolean validateShouldShowNudge(String key, Bundle b) {
        boolean checkCount;

        SharedPreferences preferences = mContext.getSharedPreferences(DialtactsActivity
                .SHARED_PREFS_NAME, Context.MODE_PRIVATE);

        int count = 0;

        // The count starts at 1 here because this is the first time we've seen this item.
        if (key.equals(NudgeKey.NOTIFICATION_INTERNATIONAL_CALL)) {
            count = preferences.getInt(CallMethodUtils.PREF_INTERNATIONAL_CALLS, 1);
        } else if (key.equals(NudgeKey.NOTIFICATION_WIFI_CALL)) {
            count = preferences.getInt(CallMethodUtils.PREF_WIFI_CALL, 1);
        }

        checkCount = (count == b.getInt(NudgeKey.NOTIFICATION_PARAM_EVENTS_FIRST_NUDGE, 0)) ||
                (count == b.getInt(NudgeKey.NOTIFICATION_PARAM_EVENTS_SECOND_NUDGE, 0));

        // return true if nudge should be shown
        return checkCount;
    }

}
