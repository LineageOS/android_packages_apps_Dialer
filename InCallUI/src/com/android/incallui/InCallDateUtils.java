package com.android.incallui;

import android.icu.text.MeasureFormat;
import android.icu.text.MeasureFormat.FormatWidth;
import android.icu.util.Measure;
import android.icu.util.MeasureUnit;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Methods to parse time and date information in the InCallUi
 */
public class InCallDateUtils {

    /**
     * Return given duration in a human-friendly format. For example, "4 minutes 3 seconds" or
     * "3 hours 1 second". Returns the hours, minutes and seconds in that order if they exist.
     */
    public static String formatDuration(long millis) {
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

        final ArrayList<Measure> measures = new ArrayList<Measure>();
        if (hours > 0) {
            measures.add(new Measure(hours, MeasureUnit.HOUR));
        }
        if (minutes > 0) {
            measures.add(new Measure(minutes, MeasureUnit.MINUTE));
        }
        if (seconds > 0) {
            measures.add(new Measure(seconds, MeasureUnit.SECOND));
        }

        if (measures.isEmpty()) {
            return "";
        } else {
            return MeasureFormat.getInstance(Locale.getDefault(), FormatWidth.WIDE)
                    .formatMeasures(measures.toArray(new Measure[measures.size()]));
        }
    }
}
