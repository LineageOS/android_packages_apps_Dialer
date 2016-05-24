package com.android.dialer;

import android.app.IntentService;
import android.app.Service;
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

            Intent service = new Intent();
            service.setClass(context, UpgradeService.class);
            context.startService(service);

            // disable this component
            context.getPackageManager();
        }
    }

    public static class UpgradeService extends Service implements ServiceConnection {


        /**
         * Creates an IntentService.  Invoked by your subclass's constructor.
         *
         * @param name Used to name the worker thread, important only for debugging.
         */
//        public UpgradeService() {
//            super("Updagrade service");
//        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

//        @Override
//        protected void onHandleIntent(Intent intent) {
//            System.out.println("started upgrade service");
//
//            Intent pluginIntent = new Intent();
//            pluginIntent.setComponent(CallerInfoHelper.getActiveProviderPackage(this));
//            System.out.println("found old caller info provider setting : " +
//                    CallerInfoHelper.getActiveProviderPackage(this));
//
//            System.out.println("this : " + this);
//
//
//            bindService(pluginIntent, this, Context.BIND_AUTO_CREATE);
//
//        }


        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            System.out.println("started upgrade service");

            Intent pluginIntent = new Intent();
            pluginIntent.setComponent(CallerInfoHelper.getActiveProviderPackage(this));
            System.out.println("found old caller info provider setting : " +
                    CallerInfoHelper.getActiveProviderPackage(this));

            System.out.println("this : " + this);
            bindService(pluginIntent, this, Context.BIND_AUTO_CREATE);
            return super.onStartCommand(intent, flags, startId);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            System.out.println("bound to caller info provider");
            ICallerInfoPlugin plugin = ICallerInfoPlugin.Stub.asInterface(service);
            try {

                if (plugin.isAuthenticated().getObject()) {
                    System.out.println("plugin is authenticated");
                    // write to the new fields
                    String value = name.flattenToString();
                    Settings.Secure.putString(getContentResolver(), PROVIDER_KEY_V2, value);
                    Settings.Secure.putInt(getContentResolver(), PROVIDER_STATUS,
                            PluginStatus.ACTIVE.ordinal());
                    System.out.println("updated to new settings strings");

                    // delete old setting
                    CallerInfoHelper.setActiveProvider(this, null);

                } else {
                    System.out.println("plugin not authenticated");
                }

            } catch (RemoteException e) {
                e.printStackTrace();
            } finally {
                unbindService(this);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            System.out.println();
        }
    }
}
