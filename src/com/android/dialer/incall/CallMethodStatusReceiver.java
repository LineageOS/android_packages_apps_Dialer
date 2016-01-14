package com.android.dialer.incall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import com.android.dialer.DialtactsActivity;

public class CallMethodStatusReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle b = intent.getExtras();

        SharedPreferences preferences = context
                .getSharedPreferences(DialtactsActivity.SHARED_PREFS_NAME, Context.MODE_PRIVATE);

        if (b != null) {
            String pluginName = b.getString(com.cyanogen.ambient.incall.PluginStatusConstants.EXTRA_PLUGIN_COMPONENT);
            int pluginStatus = b.getInt(com.cyanogen.ambient.incall.PluginStatusConstants.EXTRA_PLUGIN_STATUS);
            if (pluginStatus == com.cyanogen.ambient.incall.InCallPluginStatus.ENABLED) {

                String lastProviderEnabled =
                        preferences.getString(InCallUtils.PREF_LAST_ENABLED_PROVIDER, null);

                // No provider was previously enabled, show coachmark
                if (lastProviderEnabled == null) {
                    preferences.edit()
                            .putBoolean(InCallUtils.PREF_SPINNER_COACHMARK_SHOW, true).apply();
                }

                preferences.edit()
                        .putString(InCallUtils.PREF_LAST_ENABLED_PROVIDER, pluginName).apply();
            }
        }
    }

}
