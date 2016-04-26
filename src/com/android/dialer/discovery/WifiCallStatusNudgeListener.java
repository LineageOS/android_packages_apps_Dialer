package com.android.dialer.discovery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneStateListener;
import android.telephony.PreciseCallState;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.dialer.DialtactsActivity;
import com.android.phone.common.incall.utils.CallMethodUtils;
import com.cyanogen.ambient.discovery.util.NudgeKey;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Listener to monitor and count calls that have wifi connected the entire time.
 */
public class WifiCallStatusNudgeListener {

    private final static String TAG = WifiCallStatusNudgeListener.class.getSimpleName();
    private final static boolean DEBUG = false;

    private final static AtomicBoolean mReceiverRegistered = new AtomicBoolean(false);
    private final static int WIFI_STATE_DISABLED = 0;
    private final static int PRECISE_CALL_STATE_IDLE = 1;
    private final static int PRECISE_CALL_STATE_DIALING = 2;

    private static Context mContext;

    private static BroadcastReceiver mWifiListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN);
            if (DEBUG) Log.v(TAG, "Got wifi state: " + state);
            if (state == WifiManager.WIFI_STATE_DISABLED) {
                Message phoneStateMessage
                        = mPhoneWifiStateHandler.obtainMessage(WIFI_STATE_DISABLED);
                phoneStateMessage.sendToTarget();
            }
        }
    };

    private static Handler mPhoneWifiStateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case PRECISE_CALL_STATE_DIALING:

                    ConnectivityManager connManager = (ConnectivityManager) mContext
                            .getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo mWifi
                            = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

                    // If wifi is connected when we first get the OFFHOOK state, start a receiver to
                    // watch for wifi connection slips during the call.
                    if (mWifi.isConnected()) {
                        startReceiver();
                    } else {
                        callOnWifiFailure();
                    }
                    break;
                case PRECISE_CALL_STATE_IDLE:
                    synchronized (mReceiverRegistered) {
                        if (mReceiverRegistered.get()) {
                            // We lasted the whole call
                            stopReceiver();
                            callOnWifiSuccess();
                        } else {
                            callOnWifiFailure();
                        }
                    }
                    break;
                case WIFI_STATE_DISABLED:
                    stopReceiver();
                    break;
            }
            super.handleMessage(msg);
        }
    };

    private static PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onPreciseCallStateChanged(PreciseCallState callState) {
            int ringingState = callState.getForegroundCallState();
            if (DEBUG) Log.v(TAG, "ringing state: " + ringingState);
            Message phoneStateMessage = null;
            switch (ringingState) {
                case PreciseCallState.PRECISE_CALL_STATE_DIALING:
                    phoneStateMessage
                            = mPhoneWifiStateHandler.obtainMessage(PRECISE_CALL_STATE_DIALING);
                    break;
                case PreciseCallState.PRECISE_CALL_STATE_IDLE:
                case PreciseCallState.PRECISE_CALL_STATE_DISCONNECTED:
                    phoneStateMessage
                            = mPhoneWifiStateHandler.obtainMessage(PRECISE_CALL_STATE_IDLE);
                    break;
            }
            if (phoneStateMessage != null) {
                phoneStateMessage.sendToTarget();
            }
        }
    };

    public static void init(Context c) {
        mContext = c;
        mReceiverRegistered.set(false);

        TelephonyManager telephony
                = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        telephony.listen(mPhoneStateListener, PhoneStateListener.LISTEN_PRECISE_CALL_STATE);
    }

    private static void startReceiver() {
        synchronized (mReceiverRegistered) {
            if (DEBUG) Log.v(TAG, "Receiver starting...");
            IntentFilter intFilter = new IntentFilter();
            intFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            mContext.registerReceiver(mWifiListener, intFilter);
            mReceiverRegistered.set(true);
        }
    }

    private static void stopReceiver() {
        synchronized (mReceiverRegistered) {
            if (DEBUG) Log.v(TAG, "Receiver stopping...");
            mContext.unregisterReceiver(mWifiListener);
            mReceiverRegistered.set(false);
        }
    }

    private static void callOnWifiSuccess() {
        if (DEBUG) Log.v(TAG, "call was made with wifi connected the whole time");

        SharedPreferences preferences = mContext
                .getSharedPreferences(DialtactsActivity.SHARED_PREFS_NAME, Context.MODE_PRIVATE);

        int currentCount = preferences.getInt(CallMethodUtils.PREF_WIFI_CALL, 0);
        preferences.edit().putInt(CallMethodUtils.PREF_WIFI_CALL, ++currentCount).apply();

        new DiscoveryEventHandler(mContext).getNudgeProvidersWithKey(
                NudgeKey.NOTIFICATION_WIFI_CALL);
    }

    private static void callOnWifiFailure() {
        if (DEBUG) Log.v(TAG, "call was made with wifi not connected at some point");
    }
}
