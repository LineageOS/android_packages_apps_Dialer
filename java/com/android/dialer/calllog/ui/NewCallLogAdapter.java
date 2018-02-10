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
import com.android.dialer.glidephotomanager.GlidePhotoManager;
import com.android.dialer.glidephotomanager.GlidePhotoManagerComponent;
import com.android.dialer.time.Clock;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** {@link RecyclerView.Adapter} for the new call log fragment. */
final class NewCallLogAdapter extends RecyclerView.Adapter<ViewHolder> {

  /** IntDef for the different types of rows that can be shown in the call log. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    RowType.HEADER_TODAY,
    RowType.HEADER_YESTERDAY,
    RowType.HEADER_OLDER,
    RowType.CALL_LOG_ENTRY
  })
  @interface RowType {
    /** Header that displays "Today". */
    int HEADER_TODAY = 1;
    /** Header that displays "Yesterday". */
    int HEADER_YESTERDAY = 2;
    /** Header that displays "Older". */
    int HEADER_OLDER = 3;
    /** A row representing a call log entry (which could represent one or more calls). */
    int CALL_LOG_ENTRY = 4;
  }

  private final Clock clock;
  private final RealtimeRowProcessor realtimeRowProcessor;
  private final GlidePhotoManager glidePhotoManager;

  private Cursor cursor;

  /** Position of the "Today" header. Null when it should not be displayed. */
  @Nullable private Integer todayHeaderPosition;

  /** Position of the "Yesterday" header. Null when it should not be displayed. */
  @Nullable private Integer yesterdayHeaderPosition;

  /** Position of the "Older" header. Null when it should not be displayed. */
  @Nullable private Integer olderHeaderPosition;

  NewCallLogAdapter(Context context, Cursor cursor, Clock clock) {
    this.cursor = cursor;
    this.clock = clock;
    this.realtimeRowProcessor = CallLogUiComponent.get(context).realtimeRowProcessor();
    this.glidePhotoManager = GlidePhotoManagerComponent.get(context).glidePhotoManager();

    setHeaderPositions();
  }

  void updateCursor(Cursor updatedCursor) {
    this.cursor = updatedCursor;
    this.realtimeRowProcessor.clearCache();

    setHeaderPositions();
    notifyDataSetChanged();
  }

  void clearCache() {
    this.realtimeRowProcessor.clearCache();
  }

  private void setHeaderPositions() {
    // If there are no rows to display, set all header positions to null.
    if (!cursor.moveToFirst()) {
      todayHeaderPosition = null;
      yesterdayHeaderPosition = null;
      olderHeaderPosition = null;
      return;
    }

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
    todayHeaderPosition = numItemsInToday > 0 ? 0 : null;
    yesterdayHeaderPosition = numItemsInYesterday > 0 ? numItemsInToday : null;
    olderHeaderPosition = !cursor.isAfterLast() ? numItemsInToday + numItemsInYesterday : null;
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup viewGroup, @RowType int viewType) {
    switch (viewType) {
      case RowType.HEADER_TODAY:
      case RowType.HEADER_YESTERDAY:
      case RowType.HEADER_OLDER:
        return new HeaderViewHolder(
            LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.new_call_log_header, viewGroup, false));
      case RowType.CALL_LOG_ENTRY:
        return new NewCallLogViewHolder(
            LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.new_call_log_entry, viewGroup, false),
            clock,
            realtimeRowProcessor,
            glidePhotoManager);
      default:
        throw Assert.createUnsupportedOperationFailException("Unsupported view type: " + viewType);
    }
  }

  @Override
  public void onBindViewHolder(ViewHolder viewHolder, int position) {
    @RowType int viewType = getItemViewType(position);
    switch (viewType) {
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
        int previousHeaders = 0;
        if (todayHeaderPosition != null && position > todayHeaderPosition) {
          previousHeaders++;
        }
        if (yesterdayHeaderPosition != null && position > yesterdayHeaderPosition) {
          previousHeaders++;
        }
        if (olderHeaderPosition != null && position > olderHeaderPosition) {
          previousHeaders++;
        }
        cursor.moveToPosition(position - previousHeaders);
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
    int numberOfHeaders = 0;
    if (todayHeaderPosition != null) {
      numberOfHeaders++;
    }
    if (yesterdayHeaderPosition != null) {
      numberOfHeaders++;
    }
    if (olderHeaderPosition != null) {
      numberOfHeaders++;
    }
    return cursor.getCount() + numberOfHeaders;
  }
}
