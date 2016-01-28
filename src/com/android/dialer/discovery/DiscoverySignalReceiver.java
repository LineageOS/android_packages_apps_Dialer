package com.android.dialer.discovery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;

import android.util.Log;
import com.android.contacts.common.GeoUtil;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.discovery.DiscoveryService;
import com.android.phone.common.incall.CallMethodUtils;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonemetadata;
import com.google.i18n.phonenumbers.Phonenumber;

public class DiscoverySignalReceiver extends BroadcastReceiver {

    private static final String TAG = "DiscoverySignalReceiver";
    private static final boolean DEBUG_CONNECTIVITY = false;

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();

        switch (action) {
            case ConnectivityManager.CONNECTIVITY_ACTION:
                ConnectivityManager connManager = (ConnectivityManager)
                        context.getSystemService(Context.CONNECTIVITY_SERVICE);

                NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

                TelephonyManager tm =
                        (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

                if ((mWifi.isConnected() && tm.isNetworkRoaming()) || DEBUG_CONNECTIVITY) {
                    startServiceForConnectivityChanged(context);
                }
                break;
            case Intent.ACTION_NEW_OUTGOING_CALL:
                String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
                SharedPreferences preferences = context
                        .getSharedPreferences(DialtactsActivity.SHARED_PREFS_NAME,
                                Context.MODE_PRIVATE);
                int currentCount = preferences.getInt(CallMethodUtils.PREF_INTERNATIONAL_CALLS, 0);
                if (isMaybeInternationalNumber(context, phoneNumber)) {
                    preferences.edit().putInt(CallMethodUtils.PREF_INTERNATIONAL_CALLS,
                            ++currentCount).apply();
                    startServiceForInternationalCallMade(context);
                }
                break;
        }

    }

    public boolean isMaybeInternationalNumber(Context context, String number) {
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        String currentCountryIso = GeoUtil.getCurrentCountryIso(context);

        try {
            Phonenumber.PhoneNumber phoneNumberCalling = phoneUtil.parse(number, currentCountryIso);

            int callingToCode = phoneNumberCalling.getCountryCode();
            int callingFromCode = phoneUtil.getCountryCodeForRegion(currentCountryIso);

            if (callingToCode == 1) {
                if (phoneUtil.isNANPACountry(currentCountryIso)) {
                    // This is NANP country
                    // There is no way to reliably know what country we are calling to
                    // this even catches local numbers, we must return false to avoid the
                    // repercussions of false positives. The only way to know for sure is to build
                    // an area code list and keep that up to date.
                    return false;
                }
            }

            // if true, we are making an international call
            // if false, we are making a local call or a call between countries that share calling
            // codes
            return callingToCode != callingFromCode;

        } catch (NumberParseException e) {
            Log.e(TAG, "Phone number is invalid", e);
        }
        return false;
    }

    private void startServiceForInternationalCallMade(Context context) {
        Intent serviceIntent = getIntentForDiscoveryService(context);
        serviceIntent.setAction(Intent.ACTION_NEW_OUTGOING_CALL);
        context.startService(serviceIntent);
    }

    private void startServiceForConnectivityChanged(Context context) {
        Intent serviceIntent = getIntentForDiscoveryService(context);
        serviceIntent.setAction(ConnectivityManager.CONNECTIVITY_ACTION);
        context.startService(serviceIntent);
    }

    private Intent getIntentForDiscoveryService(Context context) {
        return new Intent(context, DiscoveryService.class);
    }

}
