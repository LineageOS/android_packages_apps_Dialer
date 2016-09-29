/*
* Copyright (c) 2015, The Linux Foundation. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are
* met:
*     * Redistributions of source code must retain the above copyright
*      notice, this list of conditions and the following disclaimer.
*     * Redistributions in binary form must reproduce the above
*       copyright notice, this list of conditions and the following
*       disclaimer in the documentation and/or other materials provided
*      with the distribution.
*     * Neither the name of The Linux Foundation nor the names of its
*      contributors may be used to endorse or promote products derived
*       from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
* ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
* BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
* BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
* WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
* OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
* IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.android.dialer.util;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.CellInfo;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import com.android.dialer.R;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class WifiCallUtils {

    private static final String TAG = WifiCallUtils.class.getSimpleName();
    private static final int DELAYED_TIME = 5 * 1000;
    private static final int NOTIFICATION_WIFI_CALL_ID = 1;
    private WindowManager mWindowManager;
    private TextView mTextView;
    private boolean mViewRemoved = true;

    private static final String WIFI_CALL_READY = "wifi_call_ready";
    private static final String WIFI_CALL_TURNON = "wifi_call_turnon";
    private static final int WIFI_CALLING_DISABLED = 0;
    private static final int WIFI_CALLING_ENABLED = 1;

    public void addWifiCallReadyMarqueeMessage(Context context) {
        if (mViewRemoved && isWifiCallReadyEnabled(context)) {
            if (mWindowManager == null) mWindowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if(mTextView == null){
                mTextView = new TextView(context);
                Log.d(TAG, "mTextView is null, new mTextView = " + mTextView);
                mTextView.setText(
                        com.android.dialer.R.string.alert_call_over_wifi);
                mTextView.setSingleLine(true);
                mTextView.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
                mTextView.setMarqueeRepeatLimit(-1);
                mTextView.setFocusableInTouchMode(true);
            } else {
                Log.d(TAG, "mTextView is not null, mTextView = " + mTextView);
            }

            WindowManager.LayoutParams windowParam = new WindowManager.LayoutParams();
            windowParam.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            windowParam.format= 1;
            windowParam.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowParam.alpha = 1.0f;
            windowParam.x = 0;
            windowParam.y = -500;
            windowParam.height = WindowManager.LayoutParams.WRAP_CONTENT;
            windowParam.width = (mWindowManager.getDefaultDisplay().getWidth()
                    < mWindowManager.getDefaultDisplay().getHeight()
                    ? mWindowManager.getDefaultDisplay().getWidth()
                    : mWindowManager.getDefaultDisplay().getHeight()) - 64;
            mWindowManager.addView(mTextView, windowParam);
            mViewRemoved = false;
            Log.d(TAG, "addWifiCallReadyMarqueeMessage, mWindowManager:" + mWindowManager
                    + " addView, mTextView:" + mTextView
                    + " addWifiCallReadyMarqueeMessage, mViewRemoved = " + mViewRemoved);

            scheduleRemoveWifiCallReadyMarqueeMessageTimer();
        }
    }

    public void removeWifiCallReadyMarqueeMessage() {
        if (!mViewRemoved) {
            mWindowManager.removeView(mTextView);
            mViewRemoved = true;
            Log.d(TAG, "removeWifiCallReadyMarqueeMessage, mWindowManager:" + mWindowManager
                    + " removeView, mTextView:" + mTextView
                    + " removeWifiCallReadyMarqueeMessage, mViewRemoved = " + mViewRemoved);
        }
    }

    private void scheduleRemoveWifiCallReadyMarqueeMessageTimer() {
        // Schedule a timer, 5s later, remove the message
        new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "Handler is running");
                    removeWifiCallReadyMarqueeMessage();
                }
        }, DELAYED_TIME);
        Log.d(TAG, "schedule timerTask");
    }

    private static boolean isCellularNetworkAvailable(Context context) {
        boolean available = false;

        TelephonyManager tm = (TelephonyManager) context.
                getSystemService(Context.TELEPHONY_SERVICE);
        List<CellInfo> cellInfoList = tm.getAllCellInfo();

        if (cellInfoList != null) {
            for (CellInfo cellinfo : cellInfoList) {
                if (cellinfo.isRegistered()) {
                    available = true;
                }
            }
        }

        return available;
    }

    public static void showWifiCallDialog(final Context context) {
        String promptMessage = context.getString(com.android.dialer.R.string
                .alert_call_no_cellular_coverage) +"\n"+ context.getString(com.android.dialer.R
                        .string.alert_user_connect_to_wifi_for_call);
        AlertDialog.Builder diaBuilder = new AlertDialog.Builder(context);
        diaBuilder.setMessage(promptMessage);
        diaBuilder.setPositiveButton(com.android.internal.R.string.ok, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("com.qualcomm.qti.extsettings",
                        "com.qualcomm.qti.extsettings.wificall.WifiCallingEnhancedSettings");
                context.startActivity(intent);
            }
        });
        diaBuilder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
            }
        });
        diaBuilder.create().show();
    }

    public static boolean isWifiCallReadyEnabled(final Context context) {
        return (Settings.Global.getInt(context.getContentResolver(),
                WIFI_CALL_READY, WIFI_CALLING_DISABLED) == WIFI_CALLING_ENABLED);
    }

    public static boolean isWifiCallTurnOnEnabled(final Context context){
        return (Settings.Global.getInt(context.getContentResolver(),
                WIFI_CALL_TURNON, WIFI_CALLING_DISABLED) == WIFI_CALLING_ENABLED);
    }

    public static boolean shallShowWifiCallDialog(final Context context) {
        boolean wifiCallTurnOn = isWifiCallTurnOnEnabled(context);

        ConnectivityManager conManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiNetworkInfo = conManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        boolean wifiAvailableNotConnected =
                wifiNetworkInfo.isAvailable() && !wifiNetworkInfo.isConnected();

        return wifiCallTurnOn && wifiAvailableNotConnected && !isCellularNetworkAvailable(context);
    }

    public static void showWifiCallNotification(final Context context) {
        if (shallShowWifiCallDialog(context)) {
            final NotificationManager notiManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            Intent intent = new Intent();
            intent.setAction(android.provider.Settings.ACTION_WIFI_SETTINGS);
            PendingIntent pendingIntent =
                    PendingIntent.getActivity(
                            context, NOTIFICATION_WIFI_CALL_ID,
                            intent, PendingIntent.FLAG_UPDATE_CURRENT);

            Notification.Builder builder = new Notification.Builder(context);
            builder.setOngoing(false);
            builder.setWhen(0);
            builder.setContentIntent(pendingIntent);
            builder.setAutoCancel(true);
            builder.setSmallIcon(R.drawable.wifi_calling_on_notification);
            builder.setContentTitle(
                    context.getResources().getString(R.string.alert_user_connect_to_wifi_for_call));
            builder.setContentText(
                    context.getResources().getString(R.string.alert_user_connect_to_wifi_for_call));
            notiManager.notify(NOTIFICATION_WIFI_CALL_ID, builder.build());
            new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        notiManager.cancel(NOTIFICATION_WIFI_CALL_ID);
                    }
           }, 2000);
        }
    }
}
