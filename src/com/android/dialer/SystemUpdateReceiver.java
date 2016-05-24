package com.android.dialer;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import com.cyanogen.ambient.common.CyanogenAmbientUtil;

/**
 * Receiver for system-updates.
 */
public class SystemUpdateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (CyanogenAmbientUtil.isCyanogenAmbientAvailable(context) ==
                CyanogenAmbientUtil.SUCCESS) {
            // Enable the receiver that performs the migration for CallerInfo related settings
            //
            // This component was disabled by default and only activated on system-updates.
            // We also need to ensure that any caller-info plugin update is installed before
            // proceeding w/ the settings migration, as the Service responsible for the settings
            // migration needs to bind to the plugin to query information additional
            // information needed to populate the settings correctly.
            ComponentName migrationReceiver = new ComponentName(context,
                    CallerInfoSettingsMigrationReceiver.class);
            PackageManager packageManager = context.getPackageManager();
            packageManager.setComponentEnabledSetting(migrationReceiver,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }

}
