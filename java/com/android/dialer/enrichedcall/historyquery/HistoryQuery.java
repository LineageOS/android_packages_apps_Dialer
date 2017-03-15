package com.android.dialer.enrichedcall.historyquery;

import android.support.annotation.NonNull;
import com.android.dialer.common.LogUtil;
import com.google.auto.value.AutoValue;

/**
 * Data object representing the pieces of information required to query for historical enriched call
 * data.
 */
@AutoValue
public abstract class HistoryQuery {

  @NonNull
  public static HistoryQuery create(@NonNull String number, long callStartTime, long callEndTime) {
    return new AutoValue_HistoryQuery(number, callStartTime, callEndTime);
  }

  public abstract String getNumber();

  public abstract long getCallStartTimestamp();

  public abstract long getCallEndTimestamp();

  @Override
  public String toString() {
    return String.format(
        "HistoryQuery{number: %s, callStartTimestamp: %d, callEndTimestamp: %d}",
        LogUtil.sanitizePhoneNumber(getNumber()), getCallStartTimestamp(), getCallEndTimestamp());
  }
}
