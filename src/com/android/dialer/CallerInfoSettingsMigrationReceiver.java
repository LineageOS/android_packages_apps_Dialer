package com.android.dialer;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
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
 * A one-shot receiver that kicks off the task for performing the migration for any relevant
 * CallerInfo related settings. If the user had previously enabled a CallerInfo Provider, those
 * settings will be carried over to the new schema.
 *
 * The settings migration is necessitated because of the revisions in
 * {@link com.cyanogen.ambient.callerinfo.CallerInfoApi}. For a detailed briefing on the new
 * settings, refer to {@link com.cyanogen.ambient.callerinfo.util.SettingsListener.CallerInfoSettings}
 */
public class CallerInfoSettingsMigrationReceiver extends BroadcastReceiver {

    private static void disableReceiver(Context context) {
        ComponentName migrationReceiver = new ComponentName(context,
                CallerInfoSettingsMigrationReceiver.class);
        context.getPackageManager().setComponentEnabledSetting(migrationReceiver,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (CyanogenAmbientUtil.isCyanogenAmbientAvailable(context) ==
                CyanogenAmbientUtil.SUCCESS) {
            ComponentName provider = CallerInfoHelper.getActiveProviderPackage(context);
            if (provider == null || TextUtils.isEmpty(provider.flattenToString())) {
                // there isn't an active provider to migrate to the new settings
                // disable receiver
                disableReceiver(context);
                return;
            }

            // start the service responsible for performing the migration task
            Intent migrationService = new Intent();
            migrationService.setClass(context, CallerInfoSettingsMigrationService.class);
            context.startService(migrationService);
        }
    }

    /**
     * The Service doing the actual task of settings migration.
     */
    public static class CallerInfoSettingsMigrationService extends Service
            implements ServiceConnection {

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            Intent pluginIntent = new Intent();
            pluginIntent.setComponent(CallerInfoHelper.getActiveProviderPackage(this));
            bindService(pluginIntent, this, Context.BIND_AUTO_CREATE);
            return Service.START_REDELIVER_INTENT;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ICallerInfoPlugin plugin = ICallerInfoPlugin.Stub.asInterface(service);
            try {
                if (plugin.isAuthenticated().getObject()) {
                    // write the active plugin to the new settings field
                    String value = name.flattenToString();
                    Settings.Secure.putString(getContentResolver(), PROVIDER_KEY_V2, value);
                    Settings.Secure.putInt(getContentResolver(), PROVIDER_STATUS,
                            PluginStatus.ACTIVE.ordinal());

                    // delete old setting
                    CallerInfoHelper.setActiveProvider(this, null);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            } finally {
                performCleanup();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            performCleanup();
        }

        private void performCleanup() {
            // disable migration receiver
            CallerInfoSettingsMigrationReceiver.disableReceiver(this);

            // unbind from the caller-info plugin
            unbindService(this);

            // stop this service
            stopSelf();
        }
    }
}
