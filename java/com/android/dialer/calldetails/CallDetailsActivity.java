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
 * limitations under the License.
 */

package com.android.dialer.calldetails;

import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import com.android.dialer.CoalescedIds;
import com.android.dialer.calldetails.CallDetailsEntryViewHolder.CallDetailsEntryListener;
import com.android.dialer.calldetails.CallDetailsFooterViewHolder.DeleteCallDetailsListener;
import com.android.dialer.calldetails.CallDetailsFooterViewHolder.ReportCallIdListener;
import com.android.dialer.calldetails.CallDetailsHeaderViewHolder.CallDetailsHeaderListener;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.callrecord.CallRecordingDataStore;
import com.android.dialer.common.Assert;
import com.android.dialer.enrichedcall.EnrichedCallComponent;
import com.android.dialer.protos.ProtoParsers;

/**
 * Displays the details of a specific call log entry.
 *
 * <p>This activity is for the new call log.
 *
 * <p>See {@link CallDetailsAdapterCommon} for logic shared between this activity and the one for
 * the old call log.
 */
public final class CallDetailsActivity extends CallDetailsActivityCommon {
  public static final String EXTRA_COALESCED_CALL_LOG_IDS = "coalesced_call_log_ids";
  public static final String EXTRA_HEADER_INFO = "header_info";

  private static final int CALL_DETAILS_LOADER_ID = 0;

  /** IDs of call log entries, used to retrieve them from the annotated call log. */
  private CoalescedIds coalescedCallLogIds;

  /** Info to be shown in the header. */
  private CallDetailsHeaderInfo headerInfo;

  /** Returns an {@link Intent} to launch this activity. */
  public static Intent newInstance(
      Context context,
      CoalescedIds coalescedAnnotatedCallLogIds,
      CallDetailsHeaderInfo callDetailsHeaderInfo,
      boolean canReportCallerId,
      boolean canSupportAssistedDialing) {
    Intent intent = new Intent(context, CallDetailsActivity.class);
    ProtoParsers.put(
        intent, EXTRA_COALESCED_CALL_LOG_IDS, Assert.isNotNull(coalescedAnnotatedCallLogIds));
    ProtoParsers.put(intent, EXTRA_HEADER_INFO, Assert.isNotNull(callDetailsHeaderInfo));
    intent.putExtra(EXTRA_CAN_REPORT_CALLER_ID, canReportCallerId);
    intent.putExtra(EXTRA_CAN_SUPPORT_ASSISTED_DIALING, canSupportAssistedDialing);
    return intent;
  }

  @Override
  protected void handleIntent(Intent intent) {
    Assert.checkArgument(intent.hasExtra(EXTRA_COALESCED_CALL_LOG_IDS));
    Assert.checkArgument(intent.hasExtra(EXTRA_HEADER_INFO));
    Assert.checkArgument(intent.hasExtra(EXTRA_CAN_REPORT_CALLER_ID));
    Assert.checkArgument(intent.hasExtra(EXTRA_CAN_SUPPORT_ASSISTED_DIALING));

    setCallDetailsEntries(CallDetailsEntries.getDefaultInstance());
    coalescedCallLogIds =
        ProtoParsers.getTrusted(
            intent, EXTRA_COALESCED_CALL_LOG_IDS, CoalescedIds.getDefaultInstance());
    headerInfo =
        ProtoParsers.getTrusted(
            intent, EXTRA_HEADER_INFO, CallDetailsHeaderInfo.getDefaultInstance());

    getLoaderManager()
        .initLoader(
            CALL_DETAILS_LOADER_ID, /* args = */ null, new CallDetailsLoaderCallbacks(this));
  }

  @Override
  protected CallDetailsAdapterCommon createAdapter(
      CallDetailsEntryListener callDetailsEntryListener,
      CallDetailsHeaderListener callDetailsHeaderListener,
      ReportCallIdListener reportCallIdListener,
      DeleteCallDetailsListener deleteCallDetailsListener,
      CallRecordingDataStore callRecordingDataStore) {
    return new CallDetailsAdapter(
        this,
        headerInfo,
        getCallDetailsEntries(),
        callDetailsEntryListener,
        callDetailsHeaderListener,
        reportCallIdListener,
        deleteCallDetailsListener,
        callRecordingDataStore);
  }

  @Override
  protected String getNumber() {
    return headerInfo.getDialerPhoneNumber().getNormalizedNumber();
  }

  /**
   * {@link LoaderCallbacks} for {@link CallDetailsCursorLoader}, which loads call detail entries
   * from {@link AnnotatedCallLog}.
   */
  private static final class CallDetailsLoaderCallbacks implements LoaderCallbacks<Cursor> {
    private final CallDetailsActivity activity;

    CallDetailsLoaderCallbacks(CallDetailsActivity callDetailsActivity) {
      this.activity = callDetailsActivity;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
      return new CallDetailsCursorLoader(activity, Assert.isNotNull(activity.coalescedCallLogIds));
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
      updateCallDetailsEntries(CallDetailsCursorLoader.toCallDetailsEntries(activity, data));
      activity.loadRttTranscriptAvailability();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
      updateCallDetailsEntries(CallDetailsEntries.getDefaultInstance());
    }

    private void updateCallDetailsEntries(CallDetailsEntries newEntries) {
      activity.setCallDetailsEntries(newEntries);
      EnrichedCallComponent.get(activity)
          .getEnrichedCallManager()
          .requestAllHistoricalData(activity.getNumber(), newEntries);
    }
  }
}
