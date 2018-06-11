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
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.calllogutils.CallLogDates;
import com.android.dialer.common.Assert;
import com.android.dialer.logging.Logger;
import com.android.dialer.promotion.Promotion;
import com.android.dialer.time.Clock;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** {@link RecyclerView.Adapter} for the new call log fragment. */
final class NewCallLogAdapter extends RecyclerView.Adapter<ViewHolder> {

  /** IntDef for the different types of rows that can be shown in the call log. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    RowType.PROMOTION_CARD,
    RowType.HEADER_TODAY,
    RowType.HEADER_YESTERDAY,
    RowType.HEADER_OLDER,
    RowType.CALL_LOG_ENTRY
  })
  @interface RowType {
    /** The promotion card. */
    int PROMOTION_CARD = 1;

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
  @Nullable private final Promotion promotion;

  private ImmutableList<CoalescedRow> coalescedRows;

  /** Position of the promotion card. Null when it should not be displayed. */
  @Nullable private Integer promotionCardPosition;

  /** Position of the "Today" header. Null when it should not be displayed. */
  @Nullable private Integer todayHeaderPosition;

  /** Position of the "Yesterday" header. Null when it should not be displayed. */
  @Nullable private Integer yesterdayHeaderPosition;

  /** Position of the "Older" header. Null when it should not be displayed. */
  @Nullable private Integer olderHeaderPosition;

  NewCallLogAdapter(
      Activity activity,
      ImmutableList<CoalescedRow> coalescedRows,
      Clock clock,
      @Nullable Promotion promotion) {
    this.activity = activity;
    this.coalescedRows = coalescedRows;
    this.clock = clock;
    this.realtimeRowProcessor = CallLogUiComponent.get(activity).realtimeRowProcessor();
    this.promotion = promotion;

    setCardAndHeaderPositions();
  }

  void updateRows(ImmutableList<CoalescedRow> coalescedRows) {
    this.coalescedRows = coalescedRows;
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
    // Set the position for the promotion card if it should be shown.
    promotionCardPosition = null;
    int numCards = 0;
    if (promotion != null && promotion.isEligibleToBeShown()) {
      promotionCardPosition = 0;
      numCards++;
    }

    // If there are no rows to display, set all header positions to null.
    if (coalescedRows.isEmpty()) {
      todayHeaderPosition = null;
      yesterdayHeaderPosition = null;
      olderHeaderPosition = null;
      return;
    }

    // Calculate positions for headers.
    long currentTimeMillis = clock.currentTimeMillis();

    int numItemsInToday = 0;
    int numItemsInYesterday = 0;
    int numItemsInOlder = 0;
    for (CoalescedRow coalescedRow : coalescedRows) {
      long timestamp = coalescedRow.getTimestamp();
      long dayDifference = CallLogDates.getDayDifference(currentTimeMillis, timestamp);
      if (dayDifference == 0) {
        numItemsInToday++;
      } else if (dayDifference == 1) {
        numItemsInYesterday++;
      } else {
        numItemsInOlder = coalescedRows.size() - numItemsInToday - numItemsInYesterday;
        break;
      }
    }

    if (numItemsInToday > 0) {
      numItemsInToday++; // including the "Today" header;
    }
    if (numItemsInYesterday > 0) {
      numItemsInYesterday++; // including the "Yesterday" header;
    }
    if (numItemsInOlder > 0) {
      numItemsInOlder++; // include the "Older" header;
    }

    // Set all header positions.
    // A header position will be null if there is no item to be displayed under that header.
    todayHeaderPosition = numItemsInToday > 0 ? numCards : null;
    yesterdayHeaderPosition = numItemsInYesterday > 0 ? numItemsInToday + numCards : null;
    olderHeaderPosition =
        numItemsInOlder > 0 ? numItemsInToday + numItemsInYesterday + numCards : null;
  }

  @Override
  public void onAttachedToRecyclerView(RecyclerView recyclerView) {
    super.onAttachedToRecyclerView(recyclerView);

    // Register a OnScrollListener that records when the promotion is viewed.
    if (promotion != null && promotion.isEligibleToBeShown()) {
      recyclerView.addOnScrollListener(
          new OnScrollListenerForRecordingPromotionCardFirstViewTime(promotion));
    }
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup viewGroup, @RowType int viewType) {
    switch (viewType) {
      case RowType.PROMOTION_CARD:
        return new PromotionCardViewHolder(
            LayoutInflater.from(activity)
                .inflate(
                    R.layout.new_call_log_promotion_card, viewGroup, /* attachToRoot = */ false),
            promotion);
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
      case RowType.PROMOTION_CARD:
        ((PromotionCardViewHolder) viewHolder)
            .setDismissListener(
                () -> {
                  notifyItemRemoved(promotionCardPosition);
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
        if (promotionCardPosition != null && position > promotionCardPosition) {
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
        newCallLogViewHolder.bind(coalescedRows.get(position - previousCardAndHeaders));
        break;
      default:
        throw Assert.createIllegalStateFailException(
            "Unexpected view type " + viewType + " at position: " + position);
    }
  }

  @Override
  @RowType
  public int getItemViewType(int position) {
    if (promotionCardPosition != null && position == promotionCardPosition) {
      return RowType.PROMOTION_CARD;
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

    if (promotionCardPosition != null) {
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
    return coalescedRows.size() + numberOfHeaders + numberOfCards;
  }

  /**
   * A {@link RecyclerView.OnScrollListener} that records the timestamp at which the promotion card
   * is first viewed.
   *
   * <p>We consider the card as viewed if the user scrolls the containing RecyclerView since such
   * action is a strong proof.
   */
  private static final class OnScrollListenerForRecordingPromotionCardFirstViewTime
      extends RecyclerView.OnScrollListener {

    private final Promotion promotion;

    OnScrollListenerForRecordingPromotionCardFirstViewTime(Promotion promotion) {
      this.promotion = promotion;
    }

    @Override
    public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
      if (newState == RecyclerView.SCROLL_STATE_SETTLING) {
        promotion.onViewed();

        // Recording promotion is viewed is this listener's sole responsibility.
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
