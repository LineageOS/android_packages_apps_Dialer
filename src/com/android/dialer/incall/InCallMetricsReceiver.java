package com.android.dialer.incall;

import android.app.IntentService;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import com.android.dialer.util.TelecomUtil;
import com.cyanogen.ambient.incall.CallLogConstants;
import com.android.dialer.incall.InCallMetricsHelper;
import cyanogenmod.providers.CMSettings;

import com.android.internal.annotations.VisibleForTesting;
import com.cyanogen.ambient.incall.CallLogConstants;
import com.cyanogen.ambient.analytics.Event;
import com.android.dialer.incall.InCallMetricsHelper;
import cyanogenmod.providers.CMSettings;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

/**
 * InCallMetricsReceiver is an aggregation and shipping service that is fired
 * once every 24 hours to pass Metrics to ModCore's analytics service.
 *
 * InCallMetrics is responsible for retrieving call data from the call log as well
 * as events logged to shared preferences.
 *
 * InCall Metrics is used to send other data besides just InCallPlugin Metrics,
 * as we currently are aggregating sim calls for better UI/UX data;
 */
public class InCallMetricsReceiver extends IntentService {

    public InCallMetricsReceiver() {
        super(InCallMetricsReceiver.class.getSimpleName());
    }

    private static final String TAG = InCallMetricsHelper.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final String CALL_COUNT_SUCCESS = "call_count_success";
    private static final String CALL_COUNT_FAILURE = "call_count_failure";
    private static final String CALL_IS_PSTN = "call_is_pstn";
    private static final String ORIGIN = "origin";

    @Override
    protected void onHandleIntent(Intent intent) {
        // Get current time a day ago for call aggregation.
        Date d = new Date(System.currentTimeMillis());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(d);
        calendar.add(Calendar.DATE, -1);
        d.setTime(calendar.getTime().getTime());

        if (TelecomUtil.hasReadPhoneStatus(getApplicationContext())) {
            lookupCallsSince(d.getTime(), getContentResolver(), getApplicationContext());
        }

        // Send stored Incall Specific events
        if (CMSettings.Secure.getInt(getApplicationContext().getContentResolver(),
                CMSettings.Secure.STATS_COLLECTION, 1) != 0) {
            InCallMetricsHelper.prepareToSend(getApplicationContext());
        }
    }

    public InCallMetricsReceiver(String name) {
        super(name);
    }

    @VisibleForTesting
    /* package */ static void lookupCallsSince(long time,
            ContentResolver contentResolver, Context context) {

        Uri uri = CallLogConstants.CONTENT_ALL_URI.buildUpon().build();

        String[] projection = new String[] {
                CallLog.Calls.DURATION,
                CallLog.Calls.DATE,
                CallLogConstants.PLUGIN_PACKAGE_NAME,
                CallLog.Calls.NUMBER,
                ORIGIN
        };


        String where = CallLog.Calls.DATE + " >= ?";

        String[] args = new String[] {
                String.valueOf(time)
        };

        Cursor c = contentResolver.query(uri, projection, where, args, null);

        // Ensure that our ContentProvider was available to return a cursor
        if (c == null) {
            return;
        }

        HashMap<String, HashMap<String, Object>> keys = new HashMap<>();

        while (c.moveToNext()) {
            String pluginComponent =
                    c.getString(c.getColumnIndex(CallLogConstants.PLUGIN_PACKAGE_NAME));

            String callOrigin =
                    c.getString(c.getColumnIndex(ORIGIN));

            long callDuration =
                    c.getLong(c.getColumnIndex(CallLog.Calls.DURATION));

            boolean isPSTN =
                    PhoneNumberUtils.isGlobalPhoneNumber(
                            c.getString(
                                    c.getColumnIndex(CallLog.Calls.NUMBER)));

            if (callOrigin == null) {
                // mark this as an unknown location call;
                callOrigin = "unknown";
            }

            if (pluginComponent == null) {
                // TODO: figure out which sim the call was placed through
                pluginComponent = "sim";
            }
            pluginComponent += InCallMetricsHelper.DELIMIT + callOrigin;
            HashMap<String, Object> data;
            long call = 1;
            long calls_success = 0;
            long calls_failure = 0;
            if (keys.containsKey(pluginComponent)) {
                data = keys.get(pluginComponent);
                if (data.containsKey(CallLog.Calls.DURATION)) {
                    // Determine if the call was successfully connected to another device
                    // for a period of time. (includes: people, voicemails, machines)
                    // TODO: figure out how to fix false positive for calls that immediately end
                    // once they are started
                    if (callDuration > 0) {
                        // Call succeeded, increase by one
                        calls_success += call;
                        if (data.containsKey(CALL_COUNT_SUCCESS)) {
                            // add previous success to our current count;
                            calls_success += (long) data.get(CALL_COUNT_SUCCESS);
                        }
                        if (data.containsKey(CALL_COUNT_FAILURE)) {
                            // call success, keep old failure count w/o modification
                            calls_failure = (long) data.get(CALL_COUNT_FAILURE);
                        }
                    } else {
                        // Call failed to connect, increase by one
                        calls_failure += call;
                        if (data.containsKey(CALL_COUNT_SUCCESS)) {
                            // Not a successful call, keep old count
                            calls_success = (long) data.get(CALL_COUNT_SUCCESS);
                        }
                        if (data.containsKey(CALL_COUNT_FAILURE)) {
                            // add previous failures to our current count of one
                            calls_failure += (long) data.get(CALL_COUNT_FAILURE);
                        }
                    }
                    callDuration += (long) data.get(CallLog.Calls.DURATION);
                }
            } else {
                data = new HashMap<>();
                if (callDuration > 0) {
                    calls_success = call;
                } else {
                    calls_failure = call;
                }
            }
            data.put(CallLog.Calls.DURATION, callDuration);
            data.put(CALL_COUNT_SUCCESS, calls_success);
            data.put(CALL_COUNT_FAILURE, calls_failure);
            data.put(CALL_IS_PSTN, isPSTN);
            keys.put(pluginComponent, data);
        }

        c.close();

        for (String key : keys.keySet()) {
            // Shippit
            HashMap<String, Object> value = keys.get(key);
            String[] keySplit = key.split(InCallMetricsHelper.DELIMIT);
            String pluginComponent = keySplit[0];
            String callOrigin = keySplit[1];
            long callDuration = (Long)value.get(CallLog.Calls.DURATION);
            long callCountSuccess = (Long)value.get(CALL_COUNT_SUCCESS);
            long callCountFailure = (Long)value.get(CALL_COUNT_FAILURE);
            boolean isPSTN = (Boolean) value.get(CALL_IS_PSTN);

            if (DEBUG) {
                Log.v(TAG, "Method:" + pluginComponent
                        + " Origin:" + callOrigin
                        + " CountSuccess:" + callCountSuccess
                        + " CountFailure:" + callCountFailure
                        + " Duration:" + callDuration);
            }

            HashMap<InCallMetricsHelper.Parameters, Object> params = new HashMap<>();
            params.put(InCallMetricsHelper.Parameters.OUTGOING_SUCCESS, callCountSuccess);
            params.put(InCallMetricsHelper.Parameters.OUTGOING_FAIL, callCountFailure);
            params.put(InCallMetricsHelper.Parameters.OUTGOING_TOTAL_DURATION, callDuration);
            params.put(InCallMetricsHelper.Parameters.PROVIDER_NAME, pluginComponent);
            params.put(InCallMetricsHelper.Parameters.ACTION_LOCATION, callOrigin);

            InCallMetricsHelper.Events event;
            if (pluginComponent.startsWith("sim")) {
                event = InCallMetricsHelper.Events.CALL_SIM_PSTN;
            } else {
                if (isPSTN) {
                    event = InCallMetricsHelper.Events.CALL_PROVIDER_VOICE;
                } else {
                    event = InCallMetricsHelper.Events.CALL_PROVIDER_PSTN;
                }
            }

            InCallMetricsHelper.sendEvent(context, InCallMetricsHelper.Categories.CALLS,
                    event, params, ComponentName.unflattenFromString(pluginComponent));

        }
    }
}

