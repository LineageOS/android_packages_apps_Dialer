/**
 * Copyright (c) 2015-2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution.
 * * Neither the name of The Linux Foundation nor the names of its
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **/
package com.android.dialer.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.incallui.Log;

import org.codeaurora.presenceserv.IPresenceService;
import org.codeaurora.presenceserv.IPresenceServiceCB;

/**
 * General presnece service utility methods for the Dialer.
 */
public class PresenceHelper {

    private static final String TAG = "PresenceHelper";
    private static volatile IPresenceService mService;
    private static boolean mIsBound;

    private static ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "PresenceService connected");
            mService = IPresenceService.Stub.asInterface(service);
            try {
                mService.registerCallback(mCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "PresenceService registerCallback error " + e);
            }
        }
        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "PresenceService disconnected");
            mService = null;
        }
    };

    private static IPresenceServiceCB mCallback = new IPresenceServiceCB.Stub() {

        public void setIMSEnabledCB() {
            Log.d(TAG, "PresenceService setIMSEnabled callback");
        }

    };

    public static void bindService(Context context) {
        Log.d(TAG, "PresenceService BindService ");
        Intent intent = new Intent(IPresenceService.class.getName());
        intent.setClassName("com.qualcomm.qti.presenceserv",
                "com.qualcomm.qti.presenceserv.PresenceService");
        mIsBound = context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    public static void unbindService(Context context) {
        Log.d(TAG, "PresenceService unbindService");
        if (mService == null) {
            Log.d(TAG, "PresenceService unbindService: mService is null");
            return;
        }
        try {
            mService.unregisterCallback(mCallback);
        } catch (RemoteException e) {
            Log.e(TAG, "PresenceService unregister error " + e);
        }
        if (mIsBound) {
            Log.d(TAG, "PresenceService unbind");
            context.unbindService(mConnection);
            mIsBound = false;
        }
    }

    public static boolean isBound() {
        return mIsBound;
    }

    public static boolean startAvailabilityFetch(String number){
        Log.d(TAG, "startAvailabilityFetch   number " + number);
        if (mService == null) {
             Log.d(TAG, "startAvailabilityFetch mService is null");
             return false;
        }
        try {
            return mService.invokeAvailabilityFetch(number);
        } catch (Exception e) {
            Log.d(TAG, "getVTCapOfContact ERROR " + e);
        }
        return false;
    }

    public static boolean getVTCapability(String number) {
        Log.d(TAG, "getVTCapability   number " + number);
        if (null == mService) {
            Log.d(TAG, "getVTCapability mService is null");
            return false;
        }
        try {
            return mService.hasVTCapability(number);
        } catch (Exception e) {
            Log.d(TAG, "getVTCapability ERROR " + e);
        }
        return false;
    }
}
