package com.android.dialer.incall;

import android.app.AlarmManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.phone.common.ambient.AmbientConnection;
import com.android.phone.common.incall.DialerDataSubscription;
import com.android.phone.common.incall.api.InCallQueries;
import com.cyanogen.ambient.analytics.AnalyticsServices;
import com.cyanogen.ambient.analytics.Event;

import java.util.HashMap;
import java.util.Map;

public class InCallMetricsHelper {

    private static final String TAG = InCallMetricsHelper.class.getSimpleName();
    private static final boolean DEBUG = false;

    public static final String CATEGORY_BASE = "dialer.incall.";
    public static final String METRICS_SHARED_PREFERENCES = "metrics_shared_preferences";
    public static final String DELIMIT = ":";

    // Positions in our shared preference keys
    private static final int POS_COMPONENT_NAME = 0;
    private static final int POS_CATEGORY_VALUE = 1;
    private static final int POS_EVENT_VALUE = 2;
    private static final int POS_PARAM_VALUE = 3;

    private static final int METRICS_JOB_ID = 1;

    public static final String PARAM_PROVIDER = "provider";

    // The TODO/Done flags denote which is convered by testing. These will be removed in the future.
    public enum Categories {
        CALLS("CALLS"), //DONE
        DISCOVERY_NUDGES("DISCOVERY_NUDGES"), // TODO
        AUTHENTICATION("AUTHENTICATION"), // TODO
        PROVIDER_STATE_CHANGE("PROVIDER_STATE_CHANGE"), //DONE
        INAPP_NUDGES("INAPP_NUDGES"),
        INAPP_SELECTIONS("INAPP_SELECTIONS");

        private String mValue;
        Categories(String s) {
            mValue = s;
        }
        public String value() {
            return mValue;
        }
    }

    public enum Events {
        NUDGE_EVENT_INTL("NUDGE_EVENT_INTL"), // TODO
        NUDGE_EVENT_WIFI("NUDGE_EVENT_WIFI"), // TODO
        NUDGE_EVENT_ROAMING("NUDGE_EVENT_ROAMING"), // TODO
        CALL_PROVIDER_VIDEO("CALL_PROVIDER_VIDEO"), //DONE
        CALL_PROVIDER_PSTN("CALL_PROVIDER_PSTN"), //DONE
        CALL_PROVIDER_VOICE("CALL_PROVIDER_VOICE"), //DONE
        CALL_SIM_PSTN("CALL_SIM_PSTN"), //DONE
        AUTH_LOGIN("AUTH_LOGIN"), // TODO
        AUTH_LOGOUT("AUTH_LOGOUT"), // TODO
        PROVIDER_HIDDEN("PROVIDER_HIDDEN"), //DONE
        PROVIDER_ENABLED("PROVIDER_ENABLED"), //DONE
        PROVIDER_DISABLED("PROVIDER_DISABLED"), //DONE
        PROVIDER_UNAVAILABLE("PROVIDER_UNAVAILABLE"), //DONE
        INAPP_NUDGE_DIALER_WIFI("INAPP_NUDGE_DIALER_WIFI"), // TODO
        INAPP_NUDGE_DIALER_ROAMING("INAPP_NUDGE_DIALER_ROAMING"), // TODO
        PROVIDER_SELECTED_SPINNER("PROVIDER_SELECTED_SPINNER");

        private String mValue;
        Events(String s) {
            mValue = s;
        }
        public String value() {
            return mValue;
        }
    }

    public enum Parameters {
        // NUDGES
        EVENT_ACCEPTANCE("EVENT_ACCEPTANCE"),
        EVENT_ATTEMPT_NUMBER("EVENT_ATTEMPT_NUMBER"),
        NUDGE_ID("NUDGE_ID"),
        NUDGE_COUNT("NUDGE_COUNT"),

        // GENERIC
        PROVIDER_NAME("PROVIDER_NAME"),
        ACTION_LOCATION("ACTION_LOCATION"),

        // CALLS
        OUTGOING_SUCCESS("OUTGOING_SUCCESS"),
        OUTGOING_FAIL("OUTGOING_FAIL"),
        OUTGOING_CONVERSIONS("OUTGOING_CONVERSIONS"),
        OUTGOING_TOTAL_DURATION("OUTGOING_TOTAL_DURATION"),

        // COUNTS
        COUNT_AUTOMATIC("COUNT_AUTOMATIC"),
        COUNT_MANUAL("COUNT_MANUAL"),
        COUNT("COUNT"),
        COUNT_INTERACTIONS("COUNT_INTERACTIONS"),
        COUNT_DISMISS("COUNT_DISMISS");

        private String mValue;
        Parameters(String s) {
            mValue = s;
        }
        public String value() {
            return mValue;
        }
    }

    private static InCallMetricsHelper sInstance;
    private Context mContext;

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
    public static void init(Context context) {
        InCallMetricsHelper helper = getInstance();
        helper.mContext = context;

        JobScheduler jobScheduler
                = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        if (jobScheduler != null) {
            boolean jobExists = false;
            for (JobInfo ji : jobScheduler.getAllPendingJobs()) {
                if (ji.getId() != METRICS_JOB_ID) {
                    // Job exists
                    jobExists = true;
                    break;
                }
            }
            if (!jobExists) {
                // We need a job to send our aggregated events to our metrics service every 24
                // hours.
                ComponentName jobComponent = new ComponentName(context,
                        InCallMetricsJob.class);

                JobInfo job = new JobInfo.Builder(METRICS_JOB_ID, jobComponent)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                        .setPersisted(true)
                        .setPeriodic(AlarmManager.INTERVAL_DAY)
                        .setBackoffCriteria(AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                                JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                        .build();
                jobScheduler.schedule(job);
            } else {
                if (DEBUG) Log.d(TAG, "InCall job " + 1 + " already exists");
            }
        } else {
            if (DEBUG) {
                Log.e(TAG, "Running on a device without JobScheduler." +
                        " InCall Metrics will fail to collect.");
            }
        }
    }

    /**
     * Prepares the event to send
     * @param category of the event
     * @param action that the event is about
     * @param params fields that are part of the event
     * @param cn associated with the plugin
     */
    public static void sendEvent(Context c, Categories category, Events action,
            HashMap<Parameters, Object> params, ComponentName cn) {
        Event.Builder event = new Event.Builder(CATEGORY_BASE + category.value(), action.value());
        if (params != null && params.size() > 0) {
            for (Parameters p : params.keySet()) {
                event.addField(p.value(), String.valueOf(params.get(p)));
            }
        }
        Event e = event.build();
        if (DEBUG) {
            Log.d(TAG, "Event: " + event.toString());
        }
        InCallQueries.shipAnalyticsToPlugin(DialerDataSubscription.get(c).mClient, cn, e);
        AnalyticsServices.AnalyticsApi.sendEvent(AmbientConnection.CLIENT.get(c), e);
    }

    /**
     * Stores events in SharedPreferences until we need to send them out.
     * @param cn
     * @param category
     * @param event
     * @param data
     */
    public static void storeEvent(ComponentName cn, Categories category, Events event,
            HashMap<Parameters, String> data) {
        storeEvent(cn, category, event, data, null);
    }

    @VisibleForTesting
    /* package */ static void storeEvent(ComponentName cn, Categories category, Events event,
            HashMap<Parameters, String> data, String location) {

        SharedPreferences sp = getInstance().mContext.getSharedPreferences(
                METRICS_SHARED_PREFERENCES, Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sp.edit();

        for (Parameters param : data.keySet()) {
            StringBuilder sb = new StringBuilder();
            sb.append(cn.flattenToShortString()); // Add ComponentName String
            sb.append(DELIMIT);
            sb.append(category.value()); // Add our category value
            sb.append(DELIMIT);
            sb.append(event.value()); // Add our event value
            sb.append(DELIMIT);
            sb.append(param.value()); // add our param value
            if (!TextUtils.isEmpty(location)) {
                sb.append(DELIMIT);
                sb.append(location); // add our location value
            }
            editor.putString(sb.toString(), data.get(param));
        }
        editor.apply();
    }

    /**
     * Increases the count of a parameter by one
     * @param hashmap containing the parameter
     * @param p parameter
     * @return new count of the item.
     */
    private static String increaseCount(HashMap<Parameters, String> hashmap, Parameters p) {
        if (hashmap.containsKey(p)) {
            return String.valueOf(Integer.valueOf(hashmap.get(p)) + 1);
        } else {
            return String.valueOf(1);
        }
    }

    /**
     * Get the SharedPreferences events and output a hashmap for the event's values.
     *
     * @param componentName ComponentName who created the event
     *
     * @return HashMap of our params and their values.
     */
    /* package*/ static HashMap<Parameters, String> getStoredEventParams(
            ComponentName componentName, Categories category, Events event) {

        SharedPreferences sp = getInstance().mContext.getSharedPreferences(
                METRICS_SHARED_PREFERENCES, Context.MODE_PRIVATE);

        StringBuilder sb = new StringBuilder();
        sb.append(componentName.flattenToShortString()); // Add ComponentName String
        sb.append(DELIMIT);
        sb.append(category.value()); // Add our category value
        sb.append(DELIMIT);
        sb.append(event.value()); // Add our event value
        sb.append(DELIMIT);

        HashMap<Parameters, String> eventMap = new HashMap<>();
        Map<String, ?> map = sp.getAll();

        for(Map.Entry<String,?> entry : map.entrySet()) {
            if (entry.getKey().startsWith(sb.toString())) {
                String[] keyParts = entry.getKey().split(DELIMIT);
                Parameters key = Parameters.valueOf(keyParts[POS_PARAM_VALUE]);
                eventMap.put(key, String.valueOf(entry.getValue()));
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
        if (cn == null) {
            // this is only null if we do not have a sim card.
            return;
        }
        HashMap<Parameters, String> metricsData = getStoredEventParams(cn, cat, event);
        metricsData.put(param, increaseCount(metricsData,param));
        storeEvent(cn, cat, event, metricsData);
    }

    /**
     * Prepares all our metrics for sending.
     */
    public static HashMap<String, Event.Builder> getEventsToSend(Context c) {
        SharedPreferences sp = c.getSharedPreferences(METRICS_SHARED_PREFERENCES,
                Context.MODE_PRIVATE);

        Map<String, ?> map = sp.getAll();

        HashMap<String, Event.Builder> unBuiltEvents = new HashMap<>();

        for(Map.Entry<String,?> entry : map.entrySet()){
            String[] keyParts = entry.getKey().split(DELIMIT);

            if (keyParts.length ==  POS_PARAM_VALUE + 1) {
                String componentString = keyParts[POS_COMPONENT_NAME];
                String eventCategory = keyParts[POS_CATEGORY_VALUE];
                String parameter = keyParts[POS_PARAM_VALUE];
                String eventAction = keyParts[POS_EVENT_VALUE];

                StringBuilder sb = new StringBuilder();
                sb.append(componentString); // Add ComponentName String
                sb.append(DELIMIT);
                sb.append(eventCategory); // Add our category value
                sb.append(DELIMIT);
                sb.append(eventAction); // Add our event value
                String eventKey = sb.toString();

                Event.Builder eventBuilder;
                if (unBuiltEvents.containsKey(eventKey)) {
                    eventBuilder = unBuiltEvents.get(eventKey);
                } else {
                    eventBuilder = new Event.Builder(CATEGORY_BASE + eventCategory, eventAction);
                    eventBuilder.addField(PARAM_PROVIDER, componentString);
                }

                eventBuilder.addField(parameter, String.valueOf(entry.getValue()));
                unBuiltEvents.put(eventKey, eventBuilder);
            }
        }
        return unBuiltEvents;
    }

    public static void clearEventData(Context c, String key) {
        SharedPreferences sp = c.getSharedPreferences(METRICS_SHARED_PREFERENCES,
                Context.MODE_PRIVATE);

        Map<String, ?> map = sp.getAll();
        SharedPreferences.Editor editor = sp.edit();

        for(Map.Entry<String,?> entry : map.entrySet()){
            String storedKey = entry.getKey();
            if (storedKey.startsWith(key)) {
                editor.remove(storedKey);
            }
        }
        editor.apply();
    }
}
