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

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.widget.Toast;
import com.android.dialer.calldetails.CallDetailsEntries.CallDetailsEntry;
import com.android.dialer.calldetails.CallDetailsFooterViewHolder.DeleteCallDetailsListener;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.AsyncTaskExecutors;
import com.android.dialer.constants.ActivityRequestCodes;
import com.android.dialer.dialercontact.DialerContact;
import com.android.dialer.duo.Duo;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.enrichedcall.EnrichedCallComponent;
import com.android.dialer.enrichedcall.EnrichedCallManager.HistoricalDataChangedListener;
import com.android.dialer.enrichedcall.historyquery.proto.HistoryResult;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.UiAction;
import com.android.dialer.performancereport.PerformanceReport;
import com.android.dialer.postcall.PostCall;
import com.android.dialer.precall.PreCall;
import com.android.dialer.protos.ProtoParsers;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Displays the details of a specific call log entry. */
public class CallDetailsActivity extends AppCompatActivity
    implements CallDetailsHeaderViewHolder.CallbackActionListener,
        CallDetailsFooterViewHolder.ReportCallIdListener,
        DeleteCallDetailsListener,
        HistoricalDataChangedListener {

  public static final String EXTRA_PHONE_NUMBER = "phone_number";
  public static final String EXTRA_HAS_ENRICHED_CALL_DATA = "has_enriched_call_data";
  public static final String EXTRA_CALL_DETAILS_ENTRIES = "call_details_entries";
  public static final String EXTRA_CONTACT = "contact";
  public static final String EXTRA_CAN_REPORT_CALLER_ID = "can_report_caller_id";
  private static final String EXTRA_CAN_SUPPORT_ASSISTED_DIALING = "can_support_assisted_dialing";
  private static final String TASK_DELETE = "task_delete";

  private CallDetailsEntries entries;
  private DialerContact contact;
  private CallDetailsAdapter adapter;

  public static boolean isLaunchIntent(Intent intent) {
    return intent.getComponent() != null
        && CallDetailsActivity.class.getName().equals(intent.getComponent().getClassName());
  }

  public static Intent newInstance(
      Context context,
      @NonNull CallDetailsEntries details,
      @NonNull DialerContact contact,
      boolean canReportCallerId,
      boolean canSupportAssistedDialing) {
    Assert.isNotNull(details);
    Assert.isNotNull(contact);

    Intent intent = new Intent(context, CallDetailsActivity.class);
    ProtoParsers.put(intent, EXTRA_CONTACT, contact);
    ProtoParsers.put(intent, EXTRA_CALL_DETAILS_ENTRIES, details);
    intent.putExtra(EXTRA_CAN_REPORT_CALLER_ID, canReportCallerId);
    intent.putExtra(EXTRA_CAN_SUPPORT_ASSISTED_DIALING, canSupportAssistedDialing);
    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.call_details_activity);
    Toolbar toolbar = findViewById(R.id.toolbar);
    toolbar.setTitle(R.string.call_details);
    toolbar.setNavigationOnClickListener(
        v -> {
          PerformanceReport.recordClick(UiAction.Type.CLOSE_CALL_DETAIL_WITH_CANCEL_BUTTON);
          finish();
        });
    onHandleIntent(getIntent());
  }

  @Override
  protected void onResume() {
    super.onResume();

    // Some calls may not be recorded (eg. from quick contact),
    // so we should restart recording after these calls. (Recorded call is stopped)
    PostCall.restartPerformanceRecordingIfARecentCallExist(this);
    if (!PerformanceReport.isRecording()) {
      PerformanceReport.startRecording();
    }

    PostCall.promptUserForMessageIfNecessary(this, findViewById(R.id.recycler_view));

    EnrichedCallComponent.get(this)
        .getEnrichedCallManager()
        .registerHistoricalDataChangedListener(this);
    EnrichedCallComponent.get(this)
        .getEnrichedCallManager()
        .requestAllHistoricalData(contact.getNumber(), entries);
  }

  @Override
  protected void onPause() {
    super.onPause();

    EnrichedCallComponent.get(this)
        .getEnrichedCallManager()
        .unregisterHistoricalDataChangedListener(this);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    onHandleIntent(intent);
  }

  private void onHandleIntent(Intent intent) {
    contact = ProtoParsers.getTrusted(intent, EXTRA_CONTACT, DialerContact.getDefaultInstance());
    entries =
        ProtoParsers.getTrusted(
            intent, EXTRA_CALL_DETAILS_ENTRIES, CallDetailsEntries.getDefaultInstance());
    adapter =
        new CallDetailsAdapter(
            this /* context */,
            contact,
            entries.getEntriesList(),
            this /* callbackListener */,
            this /* reportCallIdListener */,
            this /* callDetailDeletionListener */);

    RecyclerView recyclerView = findViewById(R.id.recycler_view);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));
    recyclerView.setAdapter(adapter);
    PerformanceReport.logOnScrollStateChange(recyclerView);
  }

  @Override
  public void onBackPressed() {
    PerformanceReport.recordClick(UiAction.Type.PRESS_ANDROID_BACK_BUTTON);
    super.onBackPressed();
  }

  @Override
  public void reportCallId(String number) {
    ReportDialogFragment.newInstance(number).show(getFragmentManager(), null);
  }

  @Override
  public boolean canReportCallerId(String number) {
    return getIntent().getExtras().getBoolean(EXTRA_CAN_REPORT_CALLER_ID, false);
  }

  @Override
  public void onHistoricalDataChanged() {
    Map<CallDetailsEntry, List<HistoryResult>> mappedResults =
        getAllHistoricalData(contact.getNumber(), entries);

    adapter.updateCallDetailsEntries(
        generateAndMapNewCallDetailsEntriesHistoryResults(
                contact.getNumber(), entries, mappedResults)
            .getEntriesList());
  }

  @Override
  public void placeImsVideoCall(String phoneNumber) {
    Logger.get(this).logImpression(DialerImpression.Type.CALL_DETAILS_IMS_VIDEO_CALL_BACK);
    PreCall.start(
        this,
        new CallIntentBuilder(phoneNumber, CallInitiationType.Type.CALL_DETAILS)
            .setIsVideoCall(true));
  }

  @Override
  public void placeDuoVideoCall(String phoneNumber) {
    Logger.get(this).logImpression(DialerImpression.Type.CALL_DETAILS_LIGHTBRINGER_CALL_BACK);
    Duo duo = DuoComponent.get(this).getDuo();
    if (!duo.isReachable(this, phoneNumber)) {
      placeImsVideoCall(phoneNumber);
      return;
    }

    try {
      startActivityForResult(duo.getIntent(this, phoneNumber), ActivityRequestCodes.DIALTACTS_DUO);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(this, R.string.activity_not_available, Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void placeVoiceCall(String phoneNumber, String postDialDigits) {
    Logger.get(this).logImpression(DialerImpression.Type.CALL_DETAILS_VOICE_CALL_BACK);

    boolean canSupportedAssistedDialing =
        getIntent().getExtras().getBoolean(EXTRA_CAN_SUPPORT_ASSISTED_DIALING, false);
    CallIntentBuilder callIntentBuilder =
        new CallIntentBuilder(phoneNumber + postDialDigits, CallInitiationType.Type.CALL_DETAILS);
    if (canSupportedAssistedDialing) {
      callIntentBuilder.setAllowAssistedDial(true);
    }

    PreCall.start(this, callIntentBuilder);
  }

  @Override
  public void delete() {
    AsyncTaskExecutors.createAsyncTaskExecutor()
        .submit(TASK_DELETE, new DeleteCallsTask(this, contact, entries));
  }

  @NonNull
  private Map<CallDetailsEntry, List<HistoryResult>> getAllHistoricalData(
      @Nullable String number, @NonNull CallDetailsEntries entries) {
    if (number == null) {
      return Collections.emptyMap();
    }

    Map<CallDetailsEntry, List<HistoryResult>> historicalData =
        EnrichedCallComponent.get(this)
            .getEnrichedCallManager()
            .getAllHistoricalData(number, entries);
    if (historicalData == null) {
      return Collections.emptyMap();
    }
    return historicalData;
  }

  private static CallDetailsEntries generateAndMapNewCallDetailsEntriesHistoryResults(
      @Nullable String number,
      @NonNull CallDetailsEntries callDetailsEntries,
      @NonNull Map<CallDetailsEntry, List<HistoryResult>> mappedResults) {
    if (number == null) {
      return callDetailsEntries;
    }
    CallDetailsEntries.Builder mutableCallDetailsEntries = CallDetailsEntries.newBuilder();
    for (CallDetailsEntry entry : callDetailsEntries.getEntriesList()) {
      CallDetailsEntry.Builder newEntry = CallDetailsEntry.newBuilder().mergeFrom(entry);
      List<HistoryResult> results = mappedResults.get(entry);
      if (results != null) {
        newEntry.addAllHistoryResults(mappedResults.get(entry));
        LogUtil.v(
            "CallLogAdapter.generateAndMapNewCallDetailsEntriesHistoryResults",
            "mapped %d results",
            newEntry.getHistoryResultsList().size());
      }
      mutableCallDetailsEntries.addEntries(newEntry.build());
    }
    return mutableCallDetailsEntries.build();
  }

  /** Delete specified calls from the call log. */
  private static class DeleteCallsTask extends AsyncTask<Void, Void, Void> {
    // Use a weak reference to hold the Activity so that there is no memory leak.
    private final WeakReference<Activity> activityWeakReference;

    private final DialerContact contact;
    private final CallDetailsEntries callDetailsEntries;
    private final String callIds;

    DeleteCallsTask(
        Activity activity, DialerContact contact, CallDetailsEntries callDetailsEntries) {
      this.activityWeakReference = new WeakReference<>(activity);
      this.contact = contact;
      this.callDetailsEntries = callDetailsEntries;

      StringBuilder callIds = new StringBuilder();
      for (CallDetailsEntry entry : callDetailsEntries.getEntriesList()) {
        if (callIds.length() != 0) {
          callIds.append(",");
        }
        callIds.append(entry.getCallId());
      }
      this.callIds = callIds.toString();
    }

    @Override
    // Suppress the lint check here as the user will not be able to see call log entries if
    // permission.WRITE_CALL_LOG is not granted.
    @SuppressLint("MissingPermission")
    @RequiresPermission(value = permission.WRITE_CALL_LOG)
    protected Void doInBackground(Void... params) {
      Activity activity = activityWeakReference.get();
      if (activity == null) {
        return null;
      }

      activity
          .getContentResolver()
          .delete(
              Calls.CONTENT_URI,
              CallLog.Calls._ID + " IN (" + callIds + ")" /* where */,
              null /* selectionArgs */);
      return null;
    }

    @Override
    public void onPostExecute(Void result) {
      Activity activity = activityWeakReference.get();
      if (activity == null) {
        return;
      }

      Intent data = new Intent();
      data.putExtra(EXTRA_PHONE_NUMBER, contact.getNumber());
      for (CallDetailsEntry entry : callDetailsEntries.getEntriesList()) {
        if (entry.getHistoryResultsCount() > 0) {
          data.putExtra(EXTRA_HAS_ENRICHED_CALL_DATA, true);
          break;
        }
      }

      activity.setResult(RESULT_OK, data);
      activity.finish();
    }
  }
}
