/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.dialer.calllog.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import com.android.dialer.calllog.database.Coalescer;
import com.android.dialer.calllogutils.CallLogDates;
import com.android.dialer.common.Assert;
import com.android.dialer.duo.Duo;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.logging.Logger;
import com.android.dialer.promotion.RttPromotion;
import com.android.dialer.storage.StorageComponent;
import com.android.dialer.time.Clock;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.TimeUnit;

/** {@link RecyclerView.Adapter} for the new call log fragment. */
final class NewCallLogAdapter extends RecyclerView.Adapter<ViewHolder> {

  @VisibleForTesting
  static final String SHARED_PREF_KEY_DUO_DISCLOSURE_DISMISSED = "duo_disclosure_dismissed";

  private static final String SHARED_PREF_KEY_DUO_DISCLOSURE_FIRST_VIEW_TIME_MILLIS =
      "duo_disclosure_first_viewed_time_ms";

  /** IntDef for the different types of rows that can be shown in the call log. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    RowType.DUO_DISCLOSURE_CARD,
    RowType.HEADER_TODAY,
    RowType.HEADER_YESTERDAY,
    RowType.HEADER_OLDER,
    RowType.CALL_LOG_ENTRY
  })
  @interface RowType {
    /** The Duo disclosure card. */
    int DUO_DISCLOSURE_CARD = 1;

    /** Header that displays "Today". */
    int HEADER_TODAY = 2;

    /** Header that displays "Yesterday". */
    int HEADER_YESTERDAY = 3;

    /** Header that displays "Older". */
    int HEADER_OLDER = 4;

    /** A row representing a call log entry (which could represent one or more calls). */
    int CALL_LOG_ENTRY = 5;
  }

  private final Clock clock;
  private final Activity activity;
  private final RealtimeRowProcessor realtimeRowProcessor;
  private final PopCounts popCounts = new PopCounts();
  private final SharedPreferences sharedPref;
  private final OnScrollListenerForRecordingDuoDisclosureFirstViewTime
      onScrollListenerForRecordingDuoDisclosureFirstViewTime;

  private Cursor cursor;

  /** Position of the Duo disclosure card. Null when it should not be displayed. */
  @Nullable private Integer duoDisclosureCardPosition;

  /** Position of the "Today" header. Null when it should not be displayed. */
  @Nullable private Integer todayHeaderPosition;

  /** Position of the "Yesterday" header. Null when it should not be displayed. */
  @Nullable private Integer yesterdayHeaderPosition;

  /** Position of the "Older" header. Null when it should not be displayed. */
  @Nullable private Integer olderHeaderPosition;

  NewCallLogAdapter(Activity activity, Cursor cursor, Clock clock) {
    this.activity = activity;
    this.cursor = cursor;
    this.clock = clock;
    this.realtimeRowProcessor = CallLogUiComponent.get(activity).realtimeRowProcessor();
    this.sharedPref = StorageComponent.get(activity).unencryptedSharedPrefs();
    this.onScrollListenerForRecordingDuoDisclosureFirstViewTime =
        new OnScrollListenerForRecordingDuoDisclosureFirstViewTime(sharedPref, clock);

    setCardAndHeaderPositions();
  }

  void updateCursor(Cursor updatedCursor) {
    this.cursor = updatedCursor;
    this.realtimeRowProcessor.clearCache();
    this.popCounts.reset();

    setCardAndHeaderPositions();
    notifyDataSetChanged();
  }

  void clearCache() {
    this.realtimeRowProcessor.clearCache();
  }

  void logMetrics(Context context) {
    Logger.get(context).logAnnotatedCallLogMetrics(popCounts.popped, popCounts.didNotPop);
  }

  private void setCardAndHeaderPositions() {
    // Set the position for the Duo disclosure card if it should be shown.
    duoDisclosureCardPosition = null;
    int numCards = 0;
    if (shouldShowDuoDisclosureCard()) {
      duoDisclosureCardPosition = 0;
      numCards++;
    }

    // If there are no rows to display, set all header positions to null.
    if (!cursor.moveToFirst()) {
      todayHeaderPosition = null;
      yesterdayHeaderPosition = null;
      olderHeaderPosition = null;
      return;
    }

    // Calculate positions for headers.
    long currentTimeMillis = clock.currentTimeMillis();

    int numItemsInToday = 0;
    int numItemsInYesterday = 0;
    do {
      long timestamp = Coalescer.getTimestamp(cursor);
      long dayDifference = CallLogDates.getDayDifference(currentTimeMillis, timestamp);
      if (dayDifference == 0) {
        numItemsInToday++;
      } else if (dayDifference == 1) {
        numItemsInYesterday++;
      } else {
        break;
      }
    } while (cursor.moveToNext());

    if (numItemsInToday > 0) {
      numItemsInToday++; // including the "Today" header;
    }
    if (numItemsInYesterday > 0) {
      numItemsInYesterday++; // including the "Yesterday" header;
    }

    // Set all header positions.
    // A header position will be null if there is no item to be displayed under that header.
    todayHeaderPosition = numItemsInToday > 0 ? numCards : null;
    yesterdayHeaderPosition = numItemsInYesterday > 0 ? numItemsInToday + numCards : null;
    olderHeaderPosition =
        !cursor.isAfterLast() ? numItemsInToday + numItemsInYesterday + numCards : null;
  }

  private boolean shouldShowDuoDisclosureCard() {
    if (new RttPromotion(activity).shouldShow()) {
      return false;
    }
    // Don't show the Duo disclosure card if
    // (1) Duo integration is not enabled on the device, or
    // (2) Duo is not activated.
    Duo duo = DuoComponent.get(activity).getDuo();
    if (!duo.isEnabled(activity) || !duo.isActivated(activity)) {
      return false;
    }

    // Don't show the Duo disclosure card if it has been dismissed.
    if (sharedPref.getBoolean(SHARED_PREF_KEY_DUO_DISCLOSURE_DISMISSED, false)) {
      return false;
    }

    // At this point, Duo is activated and the disclosure card hasn't been dismissed.
    // We should show the card if it has never been viewed by the user.
    if (!sharedPref.contains(SHARED_PREF_KEY_DUO_DISCLOSURE_FIRST_VIEW_TIME_MILLIS)) {
      return true;
    }

    // At this point, the card has been viewed but not dismissed.
    // We should not show the card if it has been viewed for more than 1 day.
    long duoDisclosureFirstViewTimeMillis =
        sharedPref.getLong(SHARED_PREF_KEY_DUO_DISCLOSURE_FIRST_VIEW_TIME_MILLIS, 0);
    return clock.currentTimeMillis() - duoDisclosureFirstViewTimeMillis
        <= TimeUnit.DAYS.toMillis(1);
  }

  @Override
  public void onAttachedToRecyclerView(RecyclerView recyclerView) {
    super.onAttachedToRecyclerView(recyclerView);

    // Register a OnScrollListener that records the timestamp at which the Duo disclosure is first
    // viewed if
    // (1) the Duo disclosure card should be shown, and
    // (2) it hasn't been viewed yet.
    if (shouldShowDuoDisclosureCard()
        && !sharedPref.contains(SHARED_PREF_KEY_DUO_DISCLOSURE_FIRST_VIEW_TIME_MILLIS)) {
      recyclerView.addOnScrollListener(onScrollListenerForRecordingDuoDisclosureFirstViewTime);
    }
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup viewGroup, @RowType int viewType) {
    switch (viewType) {
      case RowType.DUO_DISCLOSURE_CARD:
        return new DuoDisclosureCardViewHolder(
            LayoutInflater.from(activity)
                .inflate(
                    R.layout.new_call_log_duo_disclosure_card,
                    viewGroup,
                    /* attachToRoot = */ false));
      case RowType.HEADER_TODAY:
      case RowType.HEADER_YESTERDAY:
      case RowType.HEADER_OLDER:
        return new HeaderViewHolder(
            LayoutInflater.from(activity)
                .inflate(R.layout.new_call_log_header, viewGroup, /* attachToRoot = */ false));
      case RowType.CALL_LOG_ENTRY:
        return new NewCallLogViewHolder(
            activity,
            LayoutInflater.from(activity)
                .inflate(R.layout.new_call_log_entry, viewGroup, /* attachToRoot = */ false),
            clock,
            realtimeRowProcessor,
            popCounts);
      default:
        throw Assert.createUnsupportedOperationFailException("Unsupported view type: " + viewType);
    }
  }

  @Override
  public void onBindViewHolder(ViewHolder viewHolder, int position) {
    @RowType int viewType = getItemViewType(position);
    switch (viewType) {
      case RowType.DUO_DISCLOSURE_CARD:
        ((DuoDisclosureCardViewHolder) viewHolder)
            .setDismissListener(
                unused -> {
                  StorageComponent.get(activity)
                      .unencryptedSharedPrefs()
                      .edit()
                      .putBoolean(SHARED_PREF_KEY_DUO_DISCLOSURE_DISMISSED, true)
                      .apply();
                  notifyItemRemoved(duoDisclosureCardPosition);
                  setCardAndHeaderPositions();
                });
        break;
      case RowType.HEADER_TODAY:
        ((HeaderViewHolder) viewHolder).setHeader(R.string.new_call_log_header_today);
        break;
      case RowType.HEADER_YESTERDAY:
        ((HeaderViewHolder) viewHolder).setHeader(R.string.new_call_log_header_yesterday);
        break;
      case RowType.HEADER_OLDER:
        ((HeaderViewHolder) viewHolder).setHeader(R.string.new_call_log_header_older);
        break;
      case RowType.CALL_LOG_ENTRY:
        NewCallLogViewHolder newCallLogViewHolder = (NewCallLogViewHolder) viewHolder;
        int previousCardAndHeaders = 0;
        if (duoDisclosureCardPosition != null && position > duoDisclosureCardPosition) {
          previousCardAndHeaders++;
        }
        if (todayHeaderPosition != null && position > todayHeaderPosition) {
          previousCardAndHeaders++;
        }
        if (yesterdayHeaderPosition != null && position > yesterdayHeaderPosition) {
          previousCardAndHeaders++;
        }
        if (olderHeaderPosition != null && position > olderHeaderPosition) {
          previousCardAndHeaders++;
        }
        cursor.moveToPosition(position - previousCardAndHeaders);
        newCallLogViewHolder.bind(cursor);
        break;
      default:
        throw Assert.createIllegalStateFailException(
            "Unexpected view type " + viewType + " at position: " + position);
    }
  }

  @Override
  @RowType
  public int getItemViewType(int position) {
    if (duoDisclosureCardPosition != null && position == duoDisclosureCardPosition) {
      return RowType.DUO_DISCLOSURE_CARD;
    }
    if (todayHeaderPosition != null && position == todayHeaderPosition) {
      return RowType.HEADER_TODAY;
    }
    if (yesterdayHeaderPosition != null && position == yesterdayHeaderPosition) {
      return RowType.HEADER_YESTERDAY;
    }
    if (olderHeaderPosition != null && position == olderHeaderPosition) {
      return RowType.HEADER_OLDER;
    }
    return RowType.CALL_LOG_ENTRY;
  }

  @Override
  public int getItemCount() {
    int numberOfCards = 0;
    int numberOfHeaders = 0;

    if (duoDisclosureCardPosition != null) {
      numberOfCards++;
    }
    if (todayHeaderPosition != null) {
      numberOfHeaders++;
    }
    if (yesterdayHeaderPosition != null) {
      numberOfHeaders++;
    }
    if (olderHeaderPosition != null) {
      numberOfHeaders++;
    }
    return cursor.getCount() + numberOfHeaders + numberOfCards;
  }

  /**
   * A {@link RecyclerView.OnScrollListener} that records the timestamp at which the Duo disclosure
   * card is first viewed.
   *
   * <p>We consider the card as viewed if the user scrolls the containing RecyclerView since such
   * action is a strong proof.
   */
  private static final class OnScrollListenerForRecordingDuoDisclosureFirstViewTime
      extends RecyclerView.OnScrollListener {

    private final SharedPreferences sharedPref;
    private final Clock clock;

    OnScrollListenerForRecordingDuoDisclosureFirstViewTime(
        SharedPreferences sharedPref, Clock clock) {
      this.sharedPref = sharedPref;
      this.clock = clock;
    }

    @Override
    public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
      if (!sharedPref.contains(SHARED_PREF_KEY_DUO_DISCLOSURE_FIRST_VIEW_TIME_MILLIS)
          && newState == RecyclerView.SCROLL_STATE_SETTLING) {
        sharedPref
            .edit()
            .putLong(
                SHARED_PREF_KEY_DUO_DISCLOSURE_FIRST_VIEW_TIME_MILLIS, clock.currentTimeMillis())
            .apply();

        // Recording the timestamp is this listener's sole responsibility.
        // We can remove it from the containing RecyclerView after the job is done.
        recyclerView.removeOnScrollListener(this);
      }

      super.onScrollStateChanged(recyclerView, newState);
    }
  }

  static class PopCounts {
    int popped;
    int didNotPop;

    private void reset() {
      popped = 0;
      didNotPop = 0;
    }
  }
}
