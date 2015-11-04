/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @file
 * @brief Call method and VoLTE related utilities for Dialer
 */

package com.android.dialer.dialpad;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.VoLteServiceState;
import android.util.Log;
import com.android.ims.ImsManager;

import static com.android.dialer.DialtactsActivity.DEBUG;

import java.util.ArrayList;
import java.util.List;

/**
 * Collection of utilities that glue the Dialer and Telphony
 * layers together.
 */
public class VolteUtils {
    private static final String TAG = VolteUtils.class.getSimpleName();
    private static final boolean FAKE_CALL_METHOD = false;       // For testing

    private Context mContext;
    private DialpadFragment mDialpad;
    private CallMethodInfo mCallMethod;

    private PhoneStateListener mPhoneStateListener = null;

    public VolteUtils(DialpadFragment dialpad, Context ctx) {
        mDialpad = dialpad;
        mContext = ctx;
    }

    public void setCallMethod(CallMethodInfo callMethod) {
        mCallMethod = callMethod;
        trackVolteState(callMethod);
        mDialpad.setVolteInUse(getVolteInUse());
    }

    public TelephonyManager getTelephonyManager() {
        return (TelephonyManager)
            mDialpad.getActivity().getSystemService(Context.TELEPHONY_SERVICE);
    }

    public TelecomManager getTelecomManager() {
        return (TelecomManager)
            mDialpad.getActivity().getSystemService(Context.TELECOM_SERVICE);
    }

    /**
     * @return true if VoLTE is in use.
     */
    public boolean getVolteAvailable() {
        if (FAKE_CALL_METHOD && mCallMethod != null &&
            mCallMethod.mName.equals("Fake call method")) return false;
        return ImsManager.isVolteEnabledByPlatform(mContext) &&
               ImsManager.isVolteProvisionedOnDevice(mContext);
    }

    /**
     * @return true if VoLTE is in use.
     */
    public boolean getVolteInUse() {
        return getVolteAvailable() &&
               ImsManager.isEnhanced4gLteModeSettingEnabledByUser(mContext);
    }

    /**
     * Return the CallMethodInfo that corresponds to the default sim for
     * outgoing calls. This could change, so don't cache the output of this
     * function.
     */
    public CallMethodInfo getDefaultSimInfo() {
        PhoneAccountHandle handle = getTelecomManager().
                getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL);
        return phoneToCallMethod(handle);
    }

    /**
     * Return a list of all sim cards.
     */
    public List<CallMethodInfo> getSimInfoList() {
        final TelecomManager telecomMgr = getTelecomManager();
        final List<PhoneAccountHandle> accountHandles = telecomMgr.getCallCapablePhoneAccounts();
        ArrayList<CallMethodInfo> callMethodInfoList = new ArrayList<CallMethodInfo>();
        for (PhoneAccountHandle accountHandle : accountHandles) {
            CallMethodInfo callMethodInfo = phoneToCallMethod(accountHandle);
            callMethodInfoList.add(callMethodInfo);
        }
        if (FAKE_CALL_METHOD && callMethodInfoList.size() < 2) {
            CallMethodInfo callMethodInfo = new CallMethodInfo();
            callMethodInfo.mName = "Fake call method";
            callMethodInfo.mSubId = 2;
            callMethodInfo.mSlotId = 1;
            callMethodInfo.mColor = 0xff000080;
            callMethodInfo.mIcon = null;
            callMethodInfoList.add(callMethodInfo);
        }
        return callMethodInfoList;
    }

    private CallMethodInfo phoneToCallMethod(PhoneAccountHandle handle) {
        PhoneAccount phoneAccount = getTelecomManager().getPhoneAccount(handle);
        CallMethodInfo callMethodInfo = new CallMethodInfo();
        callMethodInfo.mName = phoneAccount.getLabel().toString();
        callMethodInfo.mSubId = Integer.parseInt(handle.getId());
        callMethodInfo.mSlotId = SubscriptionManager.getSlotId(callMethodInfo.mSubId);
        callMethodInfo.mColor = getPhoneAccountColor(phoneAccount);
        callMethodInfo.mIcon = null;        // TODO: load in background
        return callMethodInfo;
    }

    /**
     * Based on CallMethodInfo from DialerNext; this class provides
     * enough data to support the call_method_volte UI.
     */
    public static class CallMethodInfo {
        public String mId;
        public String mName;
        public int mSlotId;
        public int mSubId;
        public int mColor;
        public Drawable mIcon;
        public String toString() {
          return String.format(
            "<CallMethodInfo slot %d: mId=%d subId=%d, name=%s>",
              mSlotId, mId, mSubId, mName);
        }
    }

    /**
     * Set up or remove a listener for changes in VoLTE. Client should call
     * this any time the call method changes.
     */
    private void trackVolteState(CallMethodInfo callMethodInfo) {
        TelephonyManager mgr = getTelephonyManager();
        if (mPhoneStateListener != null) {
            mgr.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
            mPhoneStateListener = null;
        }
        if (!getVolteAvailable()) return;
        SubscriptionManager smgr = SubscriptionManager.from(mContext);
        android.telephony.SubscriptionInfo info =
            smgr.getActiveSubscriptionInfoForSimSlotIndex(callMethodInfo.mSlotId);
        if (info == null) {
            mDialpad.setVolteInUse(false);
            return;
        }
        mPhoneStateListener = new MobilePhoneStateListener(info.getSubscriptionId());
        int eventMask = PhoneStateListener.LISTEN_VOLTE_STATE;
        mgr.listen(mPhoneStateListener, eventMask);
    }

    private class MobilePhoneStateListener extends PhoneStateListener {
        public MobilePhoneStateListener(int subId) {
            super(subId);
        }
        @Override
        public void onVoLteServiceStateChanged(VoLteServiceState stateInfo) {
            if (DEBUG) Log.d(TAG, "onVoLteServiceStateChanged: state=" + stateInfo);
            // Tests show that this always returns IMS_REGISTERED, even when the user
            // is turning VoLTE *off*. I don't trust it, so I just re-query it every time.
            mDialpad.setVolteInUse(getVolteInUse());
            /*
            switch (stateInfo.getSrvccState()) {
            case VoLteServiceState.IMS_REGISTERED:
                mDialpad.setVolteInUse(true);
                break;
            case VoLteServiceState.IMS_UNREGISTERED:
                mDialpad.setVolteInUse(false);
                break;
            default: break;
            }
            */
        }
    }

    private static int getPhoneAccountColor(PhoneAccount phoneAccount) {
        int highlightColor = phoneAccount.getHighlightColor();
        int iconTint = phoneAccount.getIconTint();
        int accountColor = phoneAccount.getColor();

        if (iconTint != PhoneAccount.NO_ICON_TINT) {
            return iconTint;
        } else if (highlightColor != PhoneAccount.NO_HIGHLIGHT_COLOR) {
            return highlightColor;
        } else if (accountColor != PhoneAccount.NO_COLOR) {
            return accountColor;
        } else {
            return PhoneAccount.NO_ICON_TINT;
        }
    }
}
