package com.android.dialer;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import com.cyanogen.ambient.callerinfo.CallerInfoServices;
import com.cyanogen.ambient.callerinfo.extension.ICallerInfoPlugin;
import com.cyanogen.ambient.callerinfo.util.CallerInfoHelper;
import com.cyanogen.ambient.callerinfo.util.PluginStatus;
import com.cyanogen.ambient.common.CyanogenAmbientUtil;
import com.cyanogen.ambient.common.api.AmbientApiClient;

import static com.cyanogen.ambient.callerinfo.util.SettingsConstants.PROVIDER_KEY_V2;
import static com.cyanogen.ambient.callerinfo.util.SettingsConstants.PROVIDER_STATUS;

/**
 * @author Rohit Yengisetty
 */
public class PreBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // enable the upgrade component
        ComponentName upgradeComponent = new ComponentName(context, BootCompletedReceiver.class);
        PackageManager packageManager = context.getPackageManager();
        packageManager.setComponentEnabledSetting(upgradeComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        System.out.println("PreBootReceiver enabled upgrade component");
    }

}
