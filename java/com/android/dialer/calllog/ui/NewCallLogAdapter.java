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
import com.android.dialer.calllogutils.CallLogDates;
import com.android.dialer.common.Assert;
import com.android.dialer.duo.Duo;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.logging.Logger;
import com.android.dialer.storage.StorageComponent;
import com.android.dialer.time.Clock;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** {@link RecyclerView.Adapter} for the new call log fragment. */
final class NewCallLogAdapter extends RecyclerView.Adapter<ViewHolder> {

  @VisibleForTesting
  static final String SHARED_PREF_KEY_DUO_DISCLOSURE_DISMISSED = "duo_disclosure_dismissed";

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
  private final Context context;
  private final RealtimeRowProcessor realtimeRowProcessor;
  private final PopCounts popCounts = new PopCounts();

  private Cursor cursor;

  /** Position of the Duo disclosure card. Null when it should not be displayed. */
  @Nullable private Integer duoDisclosureCardPosition;

  /** Position of the "Today" header. Null when it should not be displayed. */
  @Nullable private Integer todayHeaderPosition;

  /** Position of the "Yesterday" header. Null when it should not be displayed. */
  @Nullable private Integer yesterdayHeaderPosition;

  /** Position of the "Older" header. Null when it should not be displayed. */
  @Nullable private Integer olderHeaderPosition;

  NewCallLogAdapter(Context context, Cursor cursor, Clock clock) {
    this.context = context;
    this.cursor = cursor;
    this.clock = clock;
    this.realtimeRowProcessor = CallLogUiComponent.get(context).realtimeRowProcessor();

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
      long timestamp = CoalescedAnnotatedCallLogCursorLoader.getTimestamp(cursor);
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
    Duo duo = DuoComponent.get(context).getDuo();
    if (!duo.isEnabled(context) || !duo.isActivated(context)) {
      return false;
    }

    SharedPreferences sharedPref = StorageComponent.get(context).unencryptedSharedPrefs();
    return !sharedPref.getBoolean(SHARED_PREF_KEY_DUO_DISCLOSURE_DISMISSED, false);
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup viewGroup, @RowType int viewType) {
    switch (viewType) {
      case RowType.DUO_DISCLOSURE_CARD:
        return new DuoDisclosureCardViewHolder(
            LayoutInflater.from(context)
                .inflate(
                    R.layout.new_call_log_duo_disclosure_card,
                    viewGroup,
                    /* attachToRoot = */ false));
      case RowType.HEADER_TODAY:
      case RowType.HEADER_YESTERDAY:
      case RowType.HEADER_OLDER:
        return new HeaderViewHolder(
            LayoutInflater.from(context)
                .inflate(R.layout.new_call_log_header, viewGroup, /* attachToRoot = */ false));
      case RowType.CALL_LOG_ENTRY:
        return new NewCallLogViewHolder(
            LayoutInflater.from(context)
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
                  StorageComponent.get(context)
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

  static class PopCounts {
    int popped;
    int didNotPop;

    private void reset() {
      popped = 0;
      didNotPop = 0;
    }
  }
}
