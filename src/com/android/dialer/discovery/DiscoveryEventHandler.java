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
import com.android.phone.common.incall.CallMethodUtils;
import com.android.phone.common.util.ImageUtils;

import com.cyanogen.ambient.common.api.AmbientApiClient;
import com.cyanogen.ambient.discovery.DiscoveryManagerServices;
import com.cyanogen.ambient.discovery.NudgeServices;
import com.cyanogen.ambient.discovery.nudge.NotificationNudge;
import com.cyanogen.ambient.discovery.nudge.Nudge;
import com.cyanogen.ambient.discovery.util.NudgeKey;
import com.cyanogen.ambient.incall.InCallApi;
import com.cyanogen.ambient.incall.InCallServices;
import com.cyanogen.ambient.incall.results.InCallProviderInfoResult;
import com.cyanogen.ambient.incall.results.PluginStatusResult;
import com.cyanogen.ambient.plugin.PluginStatus;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiscoveryEventHandler {

    private static final String TAG = "DiscoveryEventHandler";
    private static final boolean DEBUG_STATUS = false;

    public static void getNudgeProvidersWithKey(final Context context, final String key) {
        getNudgeProvidersWithKey(context, key, false);
    }

    public static void getNudgeProvidersWithKey(final Context context, final String key, final
                                                boolean isTesting) {
        // Spin up a new thread to make the needed calls to ambient.
        new Thread(new Runnable() {
            @Override
            public void run() {
                AmbientApiClient client = AmbientConnection.CLIENT.get(context);
                ArrayList<NotificationNudge> nudges = getNudges(client, context, key,
                        isTesting);
                sendNudgeRequestToDiscovery(client, nudges);
            }
        }).start();
    }

    private static void sendNudgeRequestToDiscovery(AmbientApiClient client,
                                                   ArrayList<NotificationNudge> nudges) {

        for (NotificationNudge nn : nudges) {
            DiscoveryManagerServices.DiscoveryManagerApi.publishNudge(client, nn);
        }
    }

    private static ArrayList<NotificationNudge> getNudges(AmbientApiClient client, Context context,
                                                         String key, boolean isTesting) {

        Map nudgePlugins =
                NudgeServices.NudgeApi.getAvailableNudgesForKey(client, key).await().components;

        ArrayList<NotificationNudge> notificationNudges = new ArrayList<>();

        if (nudgePlugins == null) {
            return notificationNudges;
        }

        InCallApi api = InCallServices.getInstance();

        List<ComponentName> plugins = api.getInstalledPlugins(client).await().components;

        HashMap<String, Bundle> availableNudges = new HashMap<>();


        for (Object entry : nudgePlugins.entrySet()) {
            Map.Entry<ComponentName, Bundle> theEntry = (Map.Entry<ComponentName, Bundle>) entry;
            availableNudges.put(theEntry.getKey().getPackageName(), theEntry.getValue());
        }

        if (plugins != null && plugins.size() > 0) {

            for (ComponentName component : plugins) {

                if (availableNudges.containsKey(component.getPackageName())) {

                    PluginStatusResult statusResult =
                            api.getPluginStatus(client, component).await();

                    Bundle b = availableNudges.get(component.getPackageName());

                    if (validateShouldShowNudge(key, context, b) && !isTesting) {
                        // Nudge not yet ready for this item.
                        continue;
                    }

                    if (DEBUG_STATUS || (statusResult.status != PluginStatus.DISABLED &&
                                statusResult.status != PluginStatus.UNAVAILABLE)) {

                        Bitmap notificationIcon = null;

                        InCallProviderInfoResult providerInfo =
                                api.getProviderInfo(client, component).await();

                        if (providerInfo != null && providerInfo.inCallProviderInfo != null) {
                            try {
                                Resources pluginResources = context.getPackageManager()
                                        .getResourcesForApplication(component.getPackageName());

                                Drawable d = pluginResources.getDrawable(
                                        providerInfo.inCallProviderInfo.getBrandIcon(), null);

                                notificationIcon = ImageUtils.drawableToBitmap(d);
                            } catch (Resources.NotFoundException e) {
                                Log.e(TAG, "Unable to retrieve icon for plugin: " + component);
                            } catch (PackageManager.NameNotFoundException e) {
                                Log.e(TAG, "Plugin isn't installed: " + component);
                            }
                        }

                        NotificationNudge nn = new NotificationNudge(component.getPackageName(),
                                Nudge.Type.IMMEDIATE,
                                b.getString(NudgeKey.NUDGE_PARAM_TITLE),
                                b.getString(NudgeKey.NOTIFICATION_PARAM_BODY));

                        if (notificationIcon != null) {
                            nn.setLargeIcon(notificationIcon);
                        }

                        Parcelable[] actions =
                                b.getParcelableArray(NudgeKey.NOTIFICATION_PARAM_NUDGE_ACTIONS);

                        for (Parcelable action : actions) {
                            NotificationNudge.Button button = (NotificationNudge.Button) action;
                            nn.addButton(button);
                        }

                        Intent intent = new Intent(context, DiscoverySignalReceiver.class);
                        intent.setAction(DiscoverySignalReceiver.DISCOVERY_NUDGE_SHOWN);

                        String nudgeID;
                        try {
                            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
                            messageDigest.update(b.getString(NudgeKey.NOTIFICATION_PARAM_BODY)
                                    .getBytes());
                            nudgeID = new String(messageDigest.digest());
                        } catch (NoSuchAlgorithmException e) {
                            Log.e(TAG, "No Algo, defaulting to unknown", e);
                            nudgeID = "unkown";
                        }
                        intent.putExtra(DiscoverySignalReceiver.NUDGE_ID, nudgeID);

                        intent.putExtra(DiscoverySignalReceiver.NUDGE_KEY, key);

                        intent.putExtra(DiscoverySignalReceiver.NUDGE_COMPONENT,
                                component.flattenToShortString());

                        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

                        nn.setOnShowIntent(pendingIntent);

                        notificationNudges.add(nn);
                    }
                }
            }
        }
        return notificationNudges;
    }

    private static boolean validateShouldShowNudge(String key, Context c, Bundle b) {

        boolean checkCount = false;

        SharedPreferences preferences = c.getSharedPreferences(DialtactsActivity.SHARED_PREFS_NAME,
                        Context.MODE_PRIVATE);

        int count = 0;

        if (key.equals(NudgeKey.NOTIFICATION_INTERNATIONAL_CALL)) {
            count = preferences.getInt(CallMethodUtils.PREF_INTERNATIONAL_CALLS, 0);
        } else if (key.equals(NudgeKey.NOTIFICATION_WIFI_CALL)) {
            count = preferences.getInt(CallMethodUtils.PREF_WIFI_CALL, 0);
        }

        checkCount =
                count == b.getInt(NudgeKey.NOTIFICATION_PARAM_EVENTS_FIRST_NUDGE, 0) ||
                count == b.getInt(NudgeKey.NOTIFICATION_PARAM_EVENTS_SECOND_NUDGE, 0);

        // return true if nudge should be shown
        return checkCount;

    }

}
