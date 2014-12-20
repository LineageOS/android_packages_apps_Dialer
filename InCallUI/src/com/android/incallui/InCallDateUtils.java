package com.android.incallui;

import android.content.res.Resources;

/**
 * Methods to parse time and date information in the InCallUi
 */
public class InCallDateUtils {
    public InCallDateUtils() {

    }

    /**
     * Return given duration in a human-friendly format. For example, "4
     * minutes 3 seconds" or "3 hours 1 second". Returns the hours, minutes and seconds in that
     * order if they exist.
     */
    public static String formatDetailedDuration(long millis) {
        int hours = 0;
        int minutes = 0;
        int seconds = 0;
        int elapsedSeconds = (int) (millis / 1000);
        if (elapsedSeconds >= 3600) {
            hours = elapsedSeconds / 3600;
            elapsedSeconds -= hours * 3600;
        }
        if (elapsedSeconds >= 60) {
            minutes = elapsedSeconds / 60;
            elapsedSeconds -= minutes * 60;
        }
        seconds = elapsedSeconds;

        final Resources res = Resources.getSystem();
        StringBuilder duration = new StringBuilder();
        if (hours > 0) {
            duration.append(res.getQuantityString(
                    com.android.internal.R.plurals.duration_hours, hours, hours));
        }
        if (minutes > 0) {
            if (hours > 0) {
                duration.append(' ');
            }
            duration.append(res.getQuantityString(
                    com.android.internal.R.plurals.duration_minutes, minutes, minutes));
        }
        if (seconds > 0) {
            if (hours > 0 || minutes > 0) {
                duration.append(' ');
            }
            duration.append(res.getQuantityString(
                    com.android.internal.R.plurals.duration_seconds, seconds, seconds));
        }
        return duration.toString();
    }

}
