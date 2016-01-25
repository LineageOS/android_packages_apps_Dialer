package com.android.dialer.incall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import com.android.dialer.DialtactsActivity;
import com.android.phone.common.incall.CallMethodUtils;
import com.cyanogen.ambient.plugin.PluginStatus;
import com.cyanogen.ambient.plugin.PluginStatusConstants;

public class CallMethodStatusReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle b = intent.getExtras();

        SharedPreferences preferences = context
                .getSharedPreferences(DialtactsActivity.SHARED_PREFS_NAME, Context.MODE_PRIVATE);

        if (b != null) {
            String pluginName = b.getString(PluginStatusConstants.EXTRA_PLUGIN_COMPONENT);
            int pluginStatus = b.getInt(PluginStatusConstants.EXTRA_PLUGIN_STATUS);
            if (pluginStatus == PluginStatus.ENABLED) {

                String lastProviderEnabled =
                        preferences.getString(CallMethodUtils.PREF_LAST_ENABLED_PROVIDER, null);

                // No provider was previously enabled, show coachmark
                if (lastProviderEnabled == null) {
                    preferences.edit()
                            .putBoolean(CallMethodUtils.PREF_SPINNER_COACHMARK_SHOW, true).apply();
                }

                preferences.edit()
                        .putString(CallMethodUtils.PREF_LAST_ENABLED_PROVIDER, pluginName).apply();
            }
        }
    }

}
