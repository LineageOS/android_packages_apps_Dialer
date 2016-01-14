package com.android.dialer.incall;

import android.content.Context;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.android.dialer.incall.CallMethodInfo;
import com.android.dialer.R;

import java.util.ArrayList;
import java.util.List;

import static com.cyanogen.ambient.incall.util.InCallHelper.NO_COLOR;

/**
 * Basic Utils for call method modifications
 */
public class CallMethodUtils {

    public final static String PREF_SPINNER_COACHMARK_SHOW = "pref_spinner_coachmark_shown";
    public final static String PREF_LAST_ENABLED_PROVIDER = "pref_last_enabled_provider";
    public final static String PREF_INTERNATIONAL_CALLS = "pref_international_calls";

    /**
     * Return whether the card in the given slot is activated
     */
    public static boolean isIccCardActivated(int slot) {
        TelephonyManager tm = TelephonyManager.getDefault();
        final int simState = tm.getSimState(slot);
        return (simState != TelephonyManager.SIM_STATE_ABSENT)
                && (simState != TelephonyManager.SIM_STATE_UNKNOWN);
    }

    public static List<CallMethodInfo> getSimInfoList(Context context) {
        final TelecomManager telecomMgr =
                (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        final List<PhoneAccountHandle> accountHandles = telecomMgr.getCallCapablePhoneAccounts();
        ArrayList<CallMethodInfo> callMethodInfoList = new ArrayList<CallMethodInfo>();
        for (PhoneAccountHandle accountHandle : accountHandles) {
            PhoneAccount phoneAccount = telecomMgr.getPhoneAccount(accountHandle);
            CallMethodInfo callMethodInfo = new CallMethodInfo();
            callMethodInfo.mComponent = accountHandle.getComponentName();
            callMethodInfo.mId = accountHandle.getId();
            callMethodInfo.mUserHandle = accountHandle.getUserHandle();
            callMethodInfo.mColor = getPhoneAccountColor(phoneAccount);
            callMethodInfo.mSubId = Integer.parseInt(accountHandle.getId());
            callMethodInfo.mSlotId = SubscriptionManager.getSlotId(callMethodInfo.mSubId);
            callMethodInfo.mName =
                    getPhoneAccountName(context, phoneAccount, callMethodInfo.mSlotId);
            callMethodInfo.mIsInCallProvider = false;
            if (isIccCardActivated(callMethodInfo.mSlotId)) {
                callMethodInfoList.add(callMethodInfo);
            }
        }
        return callMethodInfoList;
    }

    private static String getPhoneAccountName(Context context, PhoneAccount phoneAccount,
                                              int slotId) {
        if (phoneAccount == null) {
            return context.getString(R.string.call_method_spinner_item_unknown_sim, slotId + 1);
        }
        return phoneAccount.getLabel().toString();
    }

    private static int getPhoneAccountColor(PhoneAccount phoneAccount) {
        if (phoneAccount == null) {
            return NO_COLOR;
        }

        int highlightColor = phoneAccount.getHighlightColor();

        if (highlightColor != PhoneAccount.NO_HIGHLIGHT_COLOR) {
            return highlightColor;
        } else {
            return NO_COLOR;
        }
    }

    public static CallMethodInfo getDefaultDataSimInfo(Context context) {
        SubscriptionManager subMgr = SubscriptionManager.from(context);
        SubscriptionInfo subInfo = subMgr.getActiveSubscriptionInfo(
                SubscriptionManager.getDefaultSubId());

        if (subInfo == null) return null;

        CallMethodInfo callMethodInfo = new CallMethodInfo();
        callMethodInfo.mSubId = subInfo.getSubscriptionId();
        callMethodInfo.mColor = subInfo.getIconTint();
        callMethodInfo.mName = subInfo.getDisplayName().toString();
        callMethodInfo.mSlotId = SubscriptionManager.getSlotId(callMethodInfo.mSubId);

        final TelecomManager telecomMgr =
                (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        final List<PhoneAccountHandle> accountHandles = telecomMgr.getCallCapablePhoneAccounts();
        for (PhoneAccountHandle accountHandle : accountHandles) {
            if (callMethodInfo.mSubId == Integer.parseInt(accountHandle.getId())) {
                callMethodInfo.mComponent = accountHandle.getComponentName();
                break;
            }
        }

        return callMethodInfo;
    }
}
