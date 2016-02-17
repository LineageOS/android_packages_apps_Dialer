package com.android.dialer.incall;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.android.dialer.DialtactsActivity;
import com.android.dialer.discovery.NudgeItem;
import com.android.phone.common.incall.CallMethodHelper;
import com.android.phone.common.incall.CallMethodUtils;
import com.android.dialer.incall.InCallMetricsHelper;
import com.android.dialer.discovery.DiscoverySignalReceiver;
import com.cyanogen.ambient.discovery.util.NudgeKey;
import com.cyanogen.ambient.plugin.PluginStatus;
import com.cyanogen.ambient.plugin.PluginStatusConstants;

import java.util.Map;

public class CallMethodStatusReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle b = intent.getExtras();

        CallMethodHelper.refresh();

        SharedPreferences preferences = context
                .getSharedPreferences(DialtactsActivity.SHARED_PREFS_NAME, Context.MODE_PRIVATE);

        if (b != null) {
            String pluginName = b.getString(PluginStatusConstants.EXTRA_PLUGIN_COMPONENT);
            int pluginStatus = b.getInt(PluginStatusConstants.EXTRA_PLUGIN_STATUS);
            int previousStatus = b.getInt(PluginStatusConstants.EXTRA_PLUGIN_PREVIOUS_STATUS);

            InCallMetricsHelper.Events event = null;
            switch (pluginStatus) {
                case PluginStatus.ENABLED:
                    event = InCallMetricsHelper.Events.PROVIDER_ENABLED;
                    String lastProviderEnabled =
                            preferences.getString(CallMethodUtils.PREF_LAST_ENABLED_PROVIDER, null);

                    SharedPreferences.Editor e = preferences.edit();

                    // No provider was previously enabled, show coachmark
                    if (lastProviderEnabled == null) {
                        e.putBoolean(CallMethodUtils.PREF_SPINNER_COACHMARK_SHOW, true).apply();
                    }

                    e.putString(CallMethodUtils.PREF_LAST_ENABLED_PROVIDER, pluginName).apply();

                    if (previousStatus == PluginStatus.HIDDEN) {
                        checkNudgePluginStatus(context, pluginName);
                    }
                    break;
                case PluginStatus.HIDDEN:
                    event = InCallMetricsHelper.Events.PROVIDER_HIDDEN;
                    break;
                case PluginStatus.UNAVAILABLE:
                    event = InCallMetricsHelper.Events.PROVIDER_UNAVAILABLE;
                    break;
                case PluginStatus.DISABLED:
                    event = InCallMetricsHelper.Events.PROVIDER_DISABLED;
                    break;
            }

            if (event != null) {
                InCallMetricsHelper.increaseCountOfMetric(
                        ComponentName.unflattenFromString(pluginName),
                        event, InCallMetricsHelper.Categories.PROVIDER_STATE_CHANGE,
                        InCallMetricsHelper.Parameters.COUNT_INTERACTIONS);
            }
        }
    }

    private void checkNudgePluginStatus(Context context, String pluginName) {
        SharedPreferences sp = context.getSharedPreferences(
                DiscoverySignalReceiver.NUDGE_SHARED_PREF, Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sp.edit();

        Map<String, ?> prefs = sp.getAll();

        for (Map.Entry<String, ?> entry : prefs.entrySet()) {
            String[] keyParts = entry.getKey().split(InCallMetricsHelper.DELIMIT);
            ComponentName enabledPlugin = ComponentName.unflattenFromString(pluginName);
            NudgeItem ni = NudgeItem.createNudgeItemFromArray(keyParts, entry.getValue());
            if (ni != null) {
                if (ni.belongsTo(enabledPlugin)) {
                    switch(ni.mKey) {
                        case NudgeKey.NOTIFICATION_INTERNATIONAL_CALL:
                        case NudgeKey.NOTIFICATION_ROAMING:
                            if (NudgeItem.getKeyType(keyParts).equals(NudgeItem.TIME)) {
                                if (ni.isRecent()) {
                                    editor.putBoolean(ni.getWinKey(), true);
                                    editor.apply();
                                }
                            }
                            break;
                    }
                }
            }
        }
    }
}
