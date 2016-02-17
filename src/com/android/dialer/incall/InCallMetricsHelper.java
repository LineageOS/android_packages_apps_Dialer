package com.android.dialer.incall;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import com.android.dialer.DialerApplication;
import com.android.dialer.incall.InCallMetricsReceiver;
import com.android.phone.common.ambient.AmbientConnection;
import com.android.phone.common.incall.CallMethodHelper;
import com.android.phone.common.incall.CallMethodInfo;
import com.cyanogen.ambient.analytics.AnalyticsServices;
import com.cyanogen.ambient.analytics.Event;

import java.util.HashMap;
import java.util.Map;

public class InCallMetricsHelper {

    private static final String TAG = InCallMetricsHelper.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final String CATEGORY_BASE = "dialernext.incall.";
    private static final String METRICS_SHARED_PREFERENCES = "metrics_shared_preferences";
    public static final String DELIMIT = ":";

    public enum Categories {
        CALLS("CALLS"),
        DISCOVERY_NUDGES("DISCOVERY_NUDGES"),
        AUTHENTICATION("AUTHENTICATION"),
        PROVIDER_STATE_CHANGE("PROVIDER_STATE_CHANGE"),
        INAPP_NUDGES("INAPP_NUDGES");

        private String mValue;
        Categories(String s) {
            mValue = s;
        }
        public String value() {
            return mValue;
        }
    }

    public enum Events {
        NUDGE_EVENT_INTL("NUDGE_EVENT_INTL"),
        NUDGE_EVENT_ROAMING("NUDGE_EVENT_ROAMING"),
        CALL_PROVIDER_VIDEO("CALL_PROVIDER_VIDEO"),
        CALL_PROVIDER_PSTN("CALL_PROVIDER_PSTN"),
        CALL_PROVIDER_VOICE("CALL_PROVIDER_VOICE"),
        CALL_SIM_PSTN("CALL_SIM_PSTN"),
        AUTH_LOGIN("AUTH_LOGIN"),
        AUTH_LOGOUT("AUTH_LOGOUT"),
        PROVIDER_HIDDEN("PROVIDER_HIDDEN"),
        PROVIDER_ENABLED("PROVIDER_ENABLED"),
        PROVIDER_DISABLED("PROVIDER_DISABLED"),
        PROVIDER_UNAVAILABLE("PROVIDER_UNAVAILABLE"),
        CONTACTS_MERGED("CONTACTS_MERGED"),
        INVITES_SENT("INVITES_SENT"),
        INAPP_NUDGE_CONTACTS_LOGIN("INAPP_NUDGE_CONTACTS_LOGIN"),
        INAPP_NUDGE_DIALER_WIFI("INAPP_NUDGE_DIALER_WIFI"),
        INAPP_NUDGE_DIALER_ROAMING("INAPP_NUDGE_DIALER_ROAMING"),
        INAPP_NUDGE_CONTACTS_INSTALL("INAPP_NUDGE_CONTACTS_INSTALL");

        private String mValue;
        Events(String s) {
            mValue = s;
        }
        public String value() {
            return mValue;
        }
    }

    public enum Parameters {
        EVENT_ACCEPTANCE("EVENT_ACCEPTANCE"),
        EVENT_ATTEMPT_NUMBER("EVENT_ATTEMPT_NUMBER"),
        NUDGE_ID("NUDGE_ID"),
        NUDGE_COUNT("NUDGE_COUNT"),
        PROVIDER_NAME("PROVIDER_NAME"),
        ACTION_LOCATION("ACTION_LOCATION"),
        OUTGOING_SUCCESS("OUTGOING_SUCCESS"),
        OUTGOING_FAIL("OUTGOING_FAIL"),
        OUTGOING_CONVERSIONS("OUTGOING_CONVERSIONS"),
        OUTGOING_TOTAL_DURATION("OUTGOING_TOTAL_DURATION"),
        COUNT_AUTOMATIC("COUNT_AUTOMATIC"),
        COUNT_MANUAL("COUNT_MANUAL"),
        COUNT("COUNT"),
        COUNT_INTERACTIONS("COUNT_INTERACTIONS");

        private String mValue;
        Parameters(String s) {
            mValue = s;
        }
        public String value() {
            return mValue;
        }
    }

    private static InCallMetricsHelper sInstance;
    private DialerApplication mContext;

    private InCallMetricsHelper() {}

    private static synchronized InCallMetricsHelper getInstance() {
        if (sInstance == null) {
            sInstance = new InCallMetricsHelper();
        }
        return sInstance;
    }

    /**
     * Start MetricsHelper instance.
     * @param context
     */
    public static void init(DialerApplication context) {
        InCallMetricsHelper helper = getInstance();
        helper.mContext = context;

        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, InCallMetricsReceiver.class);

        PendingIntent pi = PendingIntent.getService(context, 0, i, 0);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, 1000L, // every 24 hours
                AlarmManager.INTERVAL_DAY, pi);
    }

    /**
     * Sends all events
     * @param event
     */
    private void sendAmbientEvent(Event event) {
        if (DEBUG) {
            Log.d(TAG, "Event: " + event.toString());
        }
        AnalyticsServices.AnalyticsApi.sendEvent(AmbientConnection.CLIENT.get(mContext), event);
    }

    /**
     * Prepares the event to send
     * @param category of the event
     * @param action that the event is about
     * @param params fields that are part of the event
     * @param cn associated with the plugin
     */
    public static void sendEvent(Categories category, Events action,
                                 HashMap<Parameters, Object> params, ComponentName cn) {
        Event.Builder event = new Event.Builder(CATEGORY_BASE + category.value(), action.value());
        if (params != null && params.size() > 0) {
            for (Parameters p : params.keySet()) {
                event.addField(p.value(), String.valueOf(params.get(p)));
            }
        }
        Event e = event.build();
        CallMethodHelper.shipAnalyticsToPlugin(cn, e);
        getInstance().sendAmbientEvent(e);
    }

    /**
     * Stores events in SharedPreferences until we need to send them out.
     * @param cn
     * @param category
     * @param event
     * @param data
     */
    public static void storeEvent(ComponentName cn, Categories category, Events event,
                                  HashMap<Parameters, Object> data) {
        storeEvent(cn, category, event, data, null);
    }

    public static void storeEvent(ComponentName cn, Categories category, Events event,
                                  HashMap<Parameters, Object> data, String location) {

        SharedPreferences sp = getInstance().mContext.getSharedPreferences(
                METRICS_SHARED_PREFERENCES, Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sp.edit();

        for (Parameters param : data.keySet()) {
            Object o = data.get(param);
            String eventKey = cn.toShortString() + DELIMIT + category.value() + DELIMIT +
                    event.value() + DELIMIT + param.value();
            if (location != null) {
                eventKey += DELIMIT + location;
            }
            putEditor(editor, eventKey, o);
        }
        editor.apply();
    }

    /**
     * Increases the count of a parameter by one
     * @param hashmap containing the parameter
     * @param p parameter
     * @return new count of the item.
     */
    public static int increaseCount(HashMap<Parameters, Object> hashmap, Parameters p) {
        if (hashmap.containsKey(p)) {
            return (int)hashmap.get(p) + 1;
        } else {
            return 1;
        }
    }

    /**
     * Helper method for putting objects into a shared preference
     * @param e
     * @param key
     * @param value
     */
    private static void putEditor(SharedPreferences.Editor e, String key, Object value) {
        if (value instanceof Integer) {
            e.putInt(key, (int)value);
        } else if (value instanceof String) {
            e.putString(key, String.valueOf(value));
        } else if (value instanceof Boolean) {
            e.putBoolean(key, (boolean)value);
        } else if (value instanceof Double) {
            e.putLong(key, ((Double) value).longValue());
        } else if (value instanceof Long) {
            e.putLong(key, (long)value);
        }
    }

    /**
     * Get the sharedpreferences events and output a hashmap for the event's values.
     * @param cn
     * @param category
     * @param event
     * @return
     */
    public static HashMap<Parameters, Object> getStoredEventParams(ComponentName cn,
                                                                   Categories category,
                                                                   Events event) {

        SharedPreferences sp = getInstance().mContext.getSharedPreferences(
                METRICS_SHARED_PREFERENCES, Context.MODE_PRIVATE);

        String eventKey = cn.toShortString() + DELIMIT + category.value() + DELIMIT + event.value();

        HashMap<Parameters, Object> eventMap = new HashMap<>();
        Map<String, ?> map = sp.getAll();

        for(Map.Entry<String,?> entry : map.entrySet()) {
            if (entry.getKey().startsWith(eventKey)) {
                String[] keyParts = entry.getKey().split(DELIMIT);
                String key;
                if (keyParts.length > 4) {
                    key = keyParts[keyParts.length - 2];
                } else {
                    key = keyParts[keyParts.length - 1];
                }
                eventMap.put(Parameters.valueOf(key), entry.getValue());
            }
        }
        return eventMap;
    }

    /**
     * Helper method to increase the count of a specific parameter
     * @param cn
     * @param event
     * @param cat
     * @param param
     */
    public static void increaseCountOfMetric(ComponentName cn, Events event, Categories cat,
                                             Parameters param) {
        HashMap<Parameters, Object> metricsData = getStoredEventParams(cn, cat, event);
        metricsData.put(param, increaseCount(metricsData,param));
        storeEvent(cn, cat, event, metricsData);
    }

    public static void increaseCountOfMetric(CallMethodInfo cmi, Events event, Categories cat,
                                             Parameters param) {
        increaseCountOfMetric(cmi.mComponent, event, cat, param);
    }



    /**
     * Prepares all our metrics for sending.
     */
    public static void prepareToSend() {
        SharedPreferences sp = getInstance().mContext.getSharedPreferences(
                METRICS_SHARED_PREFERENCES, Context.MODE_PRIVATE);

        Map<String, ?> map = sp.getAll();

        HashMap<String, HashMap<Parameters, Object>> items = new HashMap<>();
        for(Map.Entry<String,?> entry : map.entrySet()){

            String[] keyParts = entry.getKey().split(DELIMIT);

            ComponentName component = ComponentName.unflattenFromString(keyParts[0]);
            Categories category = Categories.valueOf(keyParts[1]);
            Events event = Events.valueOf(keyParts[2]);
            Parameters parm = Parameters.valueOf(keyParts[3]);

            String eventKey = component.toShortString() + DELIMIT + category.value() + DELIMIT
                    + event.value();

            if (!items.containsKey(eventKey)) {
                items.put(eventKey, new HashMap<Parameters, Object>());
            }

            HashMap<Parameters, Object> params = items.get(eventKey);
            params.put(parm, entry.getValue());
            items.put(eventKey, params);
        }

        for (String key : items.keySet()) {
            String[] keyParts = key.split(DELIMIT);
            ComponentName component = ComponentName.unflattenFromString(keyParts[0]);
            Categories category = Categories.valueOf(keyParts[1]);
            Events event = Events.valueOf(keyParts[2]);
            HashMap<Parameters, Object> params = items.get(key);
            sendEvent(category, event, params, component);
        }
    }


}
