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
import android.database.Cursor;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import com.android.dialer.calllogutils.CallLogDates;
import com.android.dialer.common.Assert;
import com.android.dialer.time.Clock;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** {@link RecyclerView.Adapter} for the new call log fragment. */
final class NewCallLogAdapter extends RecyclerView.Adapter<ViewHolder> {

  /** IntDef for the different types of rows that can be shown in the call log. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({RowType.HEADER_TODAY, RowType.HEADER_OLDER, RowType.CALL_LOG_ENTRY})
  @interface RowType {
    /** Header that displays "Today". */
    int HEADER_TODAY = 1;
    /** Header that displays "Older". */
    int HEADER_OLDER = 2;
    /** A row representing a call log entry (which could represent one or more calls). */
    int CALL_LOG_ENTRY = 3;
  }

  private final Clock clock;
  private final RealtimeRowProcessor realtimeRowProcessor;

  private Cursor cursor;

  /** Null when the "Today" header should not be displayed. */
  @Nullable private Integer todayHeaderPosition;
  /** Null when the "Older" header should not be displayed. */
  @Nullable private Integer olderHeaderPosition;

  NewCallLogAdapter(Context context, Cursor cursor, Clock clock) {
    this.cursor = cursor;
    this.clock = clock;
    this.realtimeRowProcessor = CallLogUiComponent.get(context).realtimeRowProcessor();

    setHeaderPositions();
  }

  void updateCursor(Cursor updatedCursor) {
    this.cursor = updatedCursor;
    this.realtimeRowProcessor.clearCache();

    setHeaderPositions();
    notifyDataSetChanged();
  }

  private void setHeaderPositions() {
    // Calculate header adapter positions by reading cursor.
    long currentTimeMillis = clock.currentTimeMillis();
    if (cursor.moveToFirst()) {
      long firstTimestamp = CoalescedAnnotatedCallLogCursorLoader.getTimestamp(cursor);
      if (CallLogDates.isSameDay(currentTimeMillis, firstTimestamp)) {
        this.todayHeaderPosition = 0;
        int adapterPosition = 2; // Accounted for "Today" header and first row.
        while (cursor.moveToNext()) {
          long timestamp = CoalescedAnnotatedCallLogCursorLoader.getTimestamp(cursor);

          if (CallLogDates.isSameDay(currentTimeMillis, timestamp)) {
            adapterPosition++;
          } else {
            this.olderHeaderPosition = adapterPosition;
            return;
          }
        }
        this.olderHeaderPosition = null; // Didn't find any "Older" rows.
      } else {
        this.todayHeaderPosition = null; // Didn't find any "Today" rows.
        this.olderHeaderPosition = 0;
      }
    } else { // There are no rows, just need to set these because they are final.
      this.todayHeaderPosition = null;
      this.olderHeaderPosition = null;
    }
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup viewGroup, @RowType int viewType) {
    switch (viewType) {
      case RowType.HEADER_TODAY:
      case RowType.HEADER_OLDER:
        return new HeaderViewHolder(
            LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.new_call_log_header, viewGroup, false));
      case RowType.CALL_LOG_ENTRY:
        return new NewCallLogViewHolder(
            LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.new_call_log_entry, viewGroup, false),
            clock,
            realtimeRowProcessor);
      default:
        throw Assert.createUnsupportedOperationFailException("Unsupported view type: " + viewType);
    }
  }

  @Override
  public void onBindViewHolder(ViewHolder viewHolder, int position) {
    if (viewHolder instanceof HeaderViewHolder) {
      HeaderViewHolder headerViewHolder = (HeaderViewHolder) viewHolder;
      @RowType int viewType = getItemViewType(position);
      if (viewType == RowType.HEADER_OLDER) {
        headerViewHolder.setHeader(R.string.new_call_log_header_older);
      } else if (viewType == RowType.HEADER_TODAY) {
        headerViewHolder.setHeader(R.string.new_call_log_header_today);
      } else {
        throw Assert.createIllegalStateFailException(
            "Unexpected view type " + viewType + " at position: " + position);
      }
      return;
    }
    NewCallLogViewHolder newCallLogViewHolder = (NewCallLogViewHolder) viewHolder;
    int previousHeaders = 0;
    if (todayHeaderPosition != null && position > todayHeaderPosition) {
      previousHeaders++;
    }
    if (olderHeaderPosition != null && position > olderHeaderPosition) {
      previousHeaders++;
    }
    cursor.moveToPosition(position - previousHeaders);
    newCallLogViewHolder.bind(cursor);
  }

  @Override
  @RowType
  public int getItemViewType(int position) {
    if (todayHeaderPosition != null && position == todayHeaderPosition) {
      return RowType.HEADER_TODAY;
    }
    if (olderHeaderPosition != null && position == olderHeaderPosition) {
      return RowType.HEADER_OLDER;
    }
    return RowType.CALL_LOG_ENTRY;
  }

  @Override
  public int getItemCount() {
    int numberOfHeaders = 0;
    if (todayHeaderPosition != null) {
      numberOfHeaders++;
    }
    if (olderHeaderPosition != null) {
      numberOfHeaders++;
    }
    return cursor.getCount() + numberOfHeaders;
  }
}
