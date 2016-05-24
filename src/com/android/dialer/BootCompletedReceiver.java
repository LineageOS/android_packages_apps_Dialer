package com.android.dialer;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.TextUtils;
import com.cyanogen.ambient.callerinfo.extension.ICallerInfoPlugin;
import com.cyanogen.ambient.callerinfo.util.CallerInfoHelper;
import com.cyanogen.ambient.callerinfo.util.PluginStatus;
import com.cyanogen.ambient.common.CyanogenAmbientUtil;

import static com.cyanogen.ambient.callerinfo.util.SettingsConstants.PROVIDER_KEY_V2;
import static com.cyanogen.ambient.callerinfo.util.SettingsConstants.PROVIDER_STATUS;

/**
 * @author Rohit Yengisetty
 */
public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        // perform upgrade of lookup provider
        System.out.println("on receive - boot completed");
        if (CyanogenAmbientUtil.isCyanogenAmbientAvailable(context) ==
                CyanogenAmbientUtil.SUCCESS) {

            ComponentName provider = CallerInfoHelper.getActiveProviderPackage(context);

            if (TextUtils.isEmpty(provider.flattenToString())) {
                return;
            }

            final PendingResult pendingResult = goAsync();
            Intent pluginIntent = new Intent();
            pluginIntent.setComponent(provider);
            System.out.println("found old caller info provider setting : " + provider.toString());

            ServiceConnection serviceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    System.out.println("bound to caller info provider");
                    ICallerInfoPlugin plugin = ICallerInfoPlugin.Stub.asInterface(service);
                    try {

                        if (plugin.isAuthenticated().getObject()) {
                            System.out.println("plugin is authenticated");
                            // write to the new fields
                            String value = name.flattenToString();
                            Settings.Secure.putString(context.getContentResolver(), PROVIDER_KEY_V2, value);
                            Settings.Secure.putInt(context.getContentResolver(), PROVIDER_STATUS,
                                    PluginStatus.ACTIVE.ordinal());
                            System.out.println("updated to new settings strings");

                            // delete old setting
                            CallerInfoHelper.setActiveProvider(context, null);

                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    } finally {
                        pendingResult.finish();
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    pendingResult.finish();
                }
            };

            context.bindService(pluginIntent, serviceConnection, Context.BIND_AUTO_CREATE);

            // disable this component
            context.getPackageManager();

        }
    }
}
