package com.android.dialer.discovery;

import android.content.ComponentName;
import com.android.dialer.incall.InCallMetricsHelper;
import com.google.common.base.Joiner;

/**
 * Helper object for serializing and tracking recorded nudge data.
 */
public class NudgeItem {

    public String mKey;
    String mId;
    long mShownTime;
    public ComponentName mComponent;

    public static final String TIME = "time";
    public static final String NUDGE_ENABLED_PLUGIN = "nudgeEnabledPlugin";
    public static final int RECENT_TIME = 24;

    public static final int COMPONENT = 0;
    public static final int KEY = 1;
    public static final int ID = 2;
    public static final int DATA = 3;

    public NudgeItem(ComponentName componentName, String key, String id, long showntime) {
        this.mComponent = componentName;
        this.mKey = key;
        this.mId = id;
        this.mShownTime = showntime;
    }

    public static NudgeItem createNudgeItemFromArray(String[] array, Object showTime) {
        if (array.length == 4) {
            ComponentName cn = ComponentName.unflattenFromString(array[COMPONENT]);
            if (showTime instanceof Long) {
                return new NudgeItem(cn, array[KEY], array[ID], (Long) showTime);
            }
        }
        return null;
    }

    /**
     * All of our SharedPrefs keys start with this data.
     * @return
     */
    private String[] getBaseKey() {
        String[] baseKey = new String[4];
        baseKey[COMPONENT] = this.mComponent.flattenToShortString();
        baseKey[KEY] = this.mKey;
        baseKey[ID] = this.mId;
        return baseKey;
    }

    /**
     * Get timeKey
     */
    public String getTimeKey() {
        String[] key = getBaseKey();
        key[DATA] = TIME;
        return Joiner.on(InCallMetricsHelper.DELIMIT).join(key);
    }

    public String getWinKey() {
        String[] key = getBaseKey();
        key[DATA] = NUDGE_ENABLED_PLUGIN;
        return Joiner.on(InCallMetricsHelper.DELIMIT).join(key);
    }

    /**
     * Get countKey
     */
    public String getCountKey() {
        String[] key = getBaseKey();
        key[DATA] = InCallMetricsHelper.Parameters.COUNT.value();
        return Joiner.on(InCallMetricsHelper.DELIMIT).join(key);
    }

    public static String getKeyType(String[] array) {
        return array[DATA];
    }

    public boolean belongsTo(ComponentName cn) {
        return this.mComponent.equals(cn);
    }

    // nudge was shown less than 24 hours ago and now
    // the plugin is enabled. Consider this recent.
    public boolean isRecent() {
        double hoursSince = (this.mShownTime - System.currentTimeMillis()) / 1000 / 60 / 60;
        return hoursSince <= RECENT_TIME;
    }
}
