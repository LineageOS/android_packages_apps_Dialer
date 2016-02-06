package com.android.dialer.util;

import android.content.ContentValues;
import android.util.Log;
import com.android.dialer.DialerApplication;
import com.android.phone.common.ambient.AmbientConnection;
import com.cyanogen.ambient.analytics.AnalyticsApi;
import com.cyanogen.ambient.analytics.AnalyticsServices;
import com.cyanogen.ambient.analytics.Event;
import com.cyanogen.ambient.common.api.AmbientApiClient;

import java.util.AbstractMap;

public class MetricsHelper {

    private static final String TAG = MetricsHelper.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final String CATEGORY_BASE = "dialernext.callerinfo.";

    public enum Categories {
        PROVIDER_STATE_CHANGES("provider_state_change"),
        BLOCKED_CALLS("blocked_calls"),
        PROVIDER_PROVIDED_INFORMATION("provider_provided_information");

        private String mValue;
        Categories(String s) {
            mValue = CATEGORY_BASE + s;
        }
        public String value() {
            return mValue;
        }
    };

    public enum Actions {
        BLOCK_CALL("block_call"),
        BLOCK_SPAM_CALL("block_and_report_call"),
        ANSWERED_CALL("answered_call"),
        DECLINED_CALL("declined_call"),
        OPTED_IN("opted_in"),
        OPTED_OUT("opted_out"),
        PROVIDED_INFORMATION("provided_information"),
        PROVIDER_DISABLED("provider_disabled");

        private String mValue;
        Actions(String s) {
            mValue = s;
        }
        public String value() {
            return mValue;
        }
    };

    public enum State {
        FIRST_LAUNCH_DIALER("first_launch_dialer"),
        AFTER_CALL_ENDED("after_call_ended"),
        AFTER_FINAL_PROMPT("after_final_prompt"),
        INCALL_SCREEN("incall_screen"),
        CONTACT_CARD("contact_card"),
        INCALL_NOTIFICATION("incall_notification"),
        AFTER_OPTING_IN("after_opting_in"),
        AFTER_OPTING_OUT("after_opting_out"),
        CALL_LOG("call_log"),
        INCOMING_CALL("incoming_call"),
        OUTGOING_CALL("outgoing_call"),
        SETTINGS("settings");

        private String mValue;
        State(String s) {
            mValue = s;
        }
        public String value() {
            return mValue;
        }
    }

    public final static class Fields {
        public final static String PROVIDER_PACKAGE_NAME = "provider_package_name";
        public static final String STATE = "state";
    };

    public static final class Field {
        private final String mValue;
        private final String mKey;

        public Field(String key, String value) {
            mKey = key;
            mValue = value;
        }
    }

    private static MetricsHelper sInstance;

    private DialerApplication mContext;

    private MetricsHelper() {}

    private static synchronized MetricsHelper getInstance() {
        if (sInstance == null) {
            sInstance = new MetricsHelper();
        }
        return sInstance;
    }

    public static void init(DialerApplication context) {
        MetricsHelper helper = getInstance();
        helper.mContext = context;
    }

    private AmbientApiClient getClient() {
        if (mContext == null) {
            throw new IllegalStateException("initialize() not invoked");
        }

        return AmbientConnection.CLIENT.get(mContext);
    }

    private void sendAmbientEvent(Event event) {
        if (DEBUG) {
            Log.d(TAG, "Event: " + event.toString());
        }
        AnalyticsServices.AnalyticsApi.sendEvent(getClient(), event);
    }

    public static void sendEvent(Categories category, Actions action, State state) {
        sendEvent(category, action, state, (Field[]) null);
    }

    public static void sendEvent(Categories category, Actions action, State state, Field...
            fields) {
        Event.Builder event = new Event.Builder(category.value(), action.value());
        if (fields != null && fields.length > 0) {
            for (Field f : fields) {
                if (f != null) {
                    event.addField(f.mKey, f.mValue);
                }
            }
        }
        if (state != null) {
            event.addField(Fields.STATE, state.value());
        }
        getInstance().sendAmbientEvent(event.build());
    }
}
