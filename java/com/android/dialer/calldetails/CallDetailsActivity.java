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
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
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
import android.view.View;
import android.widget.Toast;
import com.android.dialer.CoalescedIds;
import com.android.dialer.assisteddialing.ui.AssistedDialingSettingActivity;
import com.android.dialer.calldetails.CallDetailsEntries.CallDetailsEntry;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.callrecord.CallRecordingDataStore;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.AsyncTaskExecutors;
import com.android.dialer.common.concurrent.DialerExecutor.FailureListener;
import com.android.dialer.common.concurrent.DialerExecutor.SuccessListener;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.constants.ActivityRequestCodes;
import com.android.dialer.dialercontact.DialerContact;
import com.android.dialer.duo.Duo;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.enrichedcall.EnrichedCallComponent;
import com.android.dialer.enrichedcall.EnrichedCallManager;
import com.android.dialer.enrichedcall.historyquery.proto.HistoryResult;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.UiAction;
import com.android.dialer.performancereport.PerformanceReport;
import com.android.dialer.postcall.PostCall;
import com.android.dialer.precall.PreCall;
import com.android.dialer.protos.ProtoParsers;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Displays the details of a specific call log entry. */
public class CallDetailsActivity extends AppCompatActivity {
  private static final int CALL_DETAILS_LOADER_ID = 0;

  public static final String EXTRA_PHONE_NUMBER = "phone_number";
  public static final String EXTRA_HAS_ENRICHED_CALL_DATA = "has_enriched_call_data";
  public static final String EXTRA_CALL_DETAILS_ENTRIES = "call_details_entries";
  public static final String EXTRA_COALESCED_CALL_LOG_IDS = "coalesced_call_log_ids";
  public static final String EXTRA_CONTACT = "contact";
  public static final String EXTRA_CAN_REPORT_CALLER_ID = "can_report_caller_id";
  public static final String EXTRA_CAN_SUPPORT_ASSISTED_DIALING = "can_support_assisted_dialing";

  private final CallDetailsHeaderViewHolder.CallDetailsHeaderListener callDetailsHeaderListener =
      new CallDetailsHeaderListener(this);
  private final CallDetailsFooterViewHolder.DeleteCallDetailsListener deleteCallDetailsListener =
      new DeleteCallDetailsListener(this);
  private final CallDetailsFooterViewHolder.ReportCallIdListener reportCallIdListener =
      new ReportCallIdListener(this);
  private final EnrichedCallManager.HistoricalDataChangedListener
      enrichedCallHistoricalDataChangedListener =
          new EnrichedCallHistoricalDataChangedListener(this);

  private CallDetailsEntries entries;
  private DialerContact contact;
  private CallDetailsAdapter adapter;
  private CallRecordingDataStore callRecordingDataStore;

  // This will be present only when the activity is launched from the new call log UI, i.e., a list
  // of coalesced annotated call log IDs is included in the intent.
  private Optional<CoalescedIds> coalescedCallLogIds = Optional.absent();

  public static boolean isLaunchIntent(Intent intent) {
    return intent.getComponent() != null
        && CallDetailsActivity.class.getName().equals(intent.getComponent().getClassName());
  }

  /**
   * Returns an {@link Intent} for launching the {@link CallDetailsActivity} from the old call log
   * UI.
   */
  public static Intent newInstance(
      Context context,
      CallDetailsEntries details,
      DialerContact contact,
      boolean canReportCallerId,
      boolean canSupportAssistedDialing) {
    Intent intent = new Intent(context, CallDetailsActivity.class);
    ProtoParsers.put(intent, EXTRA_CONTACT, Assert.isNotNull(contact));
    ProtoParsers.put(intent, EXTRA_CALL_DETAILS_ENTRIES, Assert.isNotNull(details));
    intent.putExtra(EXTRA_CAN_REPORT_CALLER_ID, canReportCallerId);
    intent.putExtra(EXTRA_CAN_SUPPORT_ASSISTED_DIALING, canSupportAssistedDialing);
    return intent;
  }

  /**
   * Returns an {@link Intent} for launching the {@link CallDetailsActivity} from the new call log
   * UI.
   */
  public static Intent newInstance(
      Context context,
      CoalescedIds coalescedAnnotatedCallLogIds,
      DialerContact contact,
      boolean canReportCallerId,
      boolean canSupportAssistedDialing) {
    Intent intent = new Intent(context, CallDetailsActivity.class);
    ProtoParsers.put(intent, EXTRA_CONTACT, Assert.isNotNull(contact));
    ProtoParsers.put(
        intent, EXTRA_COALESCED_CALL_LOG_IDS, Assert.isNotNull(coalescedAnnotatedCallLogIds));
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
    callRecordingDataStore = new CallRecordingDataStore();
    onHandleIntent(getIntent());
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    callRecordingDataStore.close();
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
        .registerHistoricalDataChangedListener(enrichedCallHistoricalDataChangedListener);
    EnrichedCallComponent.get(this)
        .getEnrichedCallManager()
        .requestAllHistoricalData(contact.getNumber(), entries);
  }

  @Override
  protected void onPause() {
    super.onPause();

    EnrichedCallComponent.get(this)
        .getEnrichedCallManager()
        .unregisterHistoricalDataChangedListener(enrichedCallHistoricalDataChangedListener);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    onHandleIntent(intent);
  }

  private void onHandleIntent(Intent intent) {
    boolean hasCallDetailsEntries = intent.hasExtra(EXTRA_CALL_DETAILS_ENTRIES);
    boolean hasCoalescedCallLogIds = intent.hasExtra(EXTRA_COALESCED_CALL_LOG_IDS);
    Assert.checkArgument(
        (hasCallDetailsEntries && !hasCoalescedCallLogIds)
            || (!hasCallDetailsEntries && hasCoalescedCallLogIds),
        "One and only one of EXTRA_CALL_DETAILS_ENTRIES and EXTRA_COALESCED_CALL_LOG_IDS "
            + "can be included in the intent.");

    contact = ProtoParsers.getTrusted(intent, EXTRA_CONTACT, DialerContact.getDefaultInstance());
    if (hasCallDetailsEntries) {
      entries =
          ProtoParsers.getTrusted(
              intent, EXTRA_CALL_DETAILS_ENTRIES, CallDetailsEntries.getDefaultInstance());
    } else {
      entries = CallDetailsEntries.getDefaultInstance();
      coalescedCallLogIds =
          Optional.of(
              ProtoParsers.getTrusted(
                  intent, EXTRA_COALESCED_CALL_LOG_IDS, CoalescedIds.getDefaultInstance()));
      getLoaderManager()
          .initLoader(
              CALL_DETAILS_LOADER_ID, /* args = */ null, new CallDetailsLoaderCallbacks(this));
    }

    adapter =
        new CallDetailsAdapter(
            this /* context */,
            contact,
            entries.getEntriesList(),
            callDetailsHeaderListener,
            reportCallIdListener,
            deleteCallDetailsListener,
            callRecordingDataStore);

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
      Assert.checkState(activity.coalescedCallLogIds.isPresent());

      return new CallDetailsCursorLoader(activity, activity.coalescedCallLogIds.get());
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
      updateCallDetailsEntries(CallDetailsCursorLoader.toCallDetailsEntries(data));
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
      updateCallDetailsEntries(CallDetailsEntries.getDefaultInstance());
    }

    private void updateCallDetailsEntries(CallDetailsEntries newEntries) {
      activity.entries = newEntries;
      activity.adapter.updateCallDetailsEntries(newEntries.getEntriesList());
      EnrichedCallComponent.get(activity)
          .getEnrichedCallManager()
          .requestAllHistoricalData(activity.contact.getNumber(), newEntries);
    }
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

  private static final class CallDetailsHeaderListener
      implements CallDetailsHeaderViewHolder.CallDetailsHeaderListener {
    private final WeakReference<CallDetailsActivity> activityWeakReference;

    CallDetailsHeaderListener(CallDetailsActivity activity) {
      this.activityWeakReference = new WeakReference<>(activity);
    }

    @Override
    public void placeImsVideoCall(String phoneNumber) {
      Logger.get(getActivity())
          .logImpression(DialerImpression.Type.CALL_DETAILS_IMS_VIDEO_CALL_BACK);
      PreCall.start(
          getActivity(),
          new CallIntentBuilder(phoneNumber, CallInitiationType.Type.CALL_DETAILS)
              .setIsVideoCall(true));
    }

    @Override
    public void placeDuoVideoCall(String phoneNumber) {
      Logger.get(getActivity())
          .logImpression(DialerImpression.Type.CALL_DETAILS_LIGHTBRINGER_CALL_BACK);
      Duo duo = DuoComponent.get(getActivity()).getDuo();
      if (!duo.isReachable(getActivity(), phoneNumber)) {
        placeImsVideoCall(phoneNumber);
        return;
      }

      try {
        getActivity()
            .startActivityForResult(
                duo.getIntent(getActivity(), phoneNumber), ActivityRequestCodes.DIALTACTS_DUO);
      } catch (ActivityNotFoundException e) {
        Toast.makeText(getActivity(), R.string.activity_not_available, Toast.LENGTH_SHORT).show();
      }
    }

    @Override
    public void placeVoiceCall(String phoneNumber, String postDialDigits) {
      Logger.get(getActivity()).logImpression(DialerImpression.Type.CALL_DETAILS_VOICE_CALL_BACK);

      boolean canSupportedAssistedDialing =
          getActivity()
              .getIntent()
              .getExtras()
              .getBoolean(EXTRA_CAN_SUPPORT_ASSISTED_DIALING, false);
      CallIntentBuilder callIntentBuilder =
          new CallIntentBuilder(phoneNumber + postDialDigits, CallInitiationType.Type.CALL_DETAILS);
      if (canSupportedAssistedDialing) {
        callIntentBuilder.setAllowAssistedDial(true);
      }

      PreCall.start(getActivity(), callIntentBuilder);
    }

    private CallDetailsActivity getActivity() {
      return Preconditions.checkNotNull(activityWeakReference.get());
    }

    @Override
    public void openAssistedDialingSettings(View unused) {
        Intent intent = new Intent(getActivity(), AssistedDialingSettingActivity.class);
        getActivity().startActivity(intent);
    }

    @Override
    public void createAssistedDialerNumberParserTask(
        AssistedDialingNumberParseWorker worker,
        SuccessListener<Integer> successListener,
        FailureListener failureListener) {
      DialerExecutorComponent.get(getActivity().getApplicationContext())
          .dialerExecutorFactory()
          .createUiTaskBuilder(
              getActivity().getFragmentManager(),
              "CallDetailsActivity.createAssistedDialerNumberParserTask",
              new AssistedDialingNumberParseWorker())
          .onSuccess(successListener)
          .onFailure(failureListener)
          .build()
          .executeParallel(getActivity().contact.getNumber());
    }
  }

  static class AssistedDialingNumberParseWorker implements Worker<String, Integer> {

    @Override
    public Integer doInBackground(@NonNull String phoneNumber) {
      PhoneNumber parsedNumber = null;
      try {
        parsedNumber = PhoneNumberUtil.getInstance().parse(phoneNumber, null);
      } catch (NumberParseException e) {
        LogUtil.w(
            "AssistedDialingNumberParseWorker.doInBackground",
            "couldn't parse phone number: " + LogUtil.sanitizePii(phoneNumber),
            e);
        return 0;
      }
      return parsedNumber.getCountryCode();
    }
  }

  private static final class DeleteCallDetailsListener
      implements CallDetailsFooterViewHolder.DeleteCallDetailsListener {
    private static final String ASYNC_TASK_ID = "task_delete";

    private final WeakReference<CallDetailsActivity> activityWeakReference;

    DeleteCallDetailsListener(CallDetailsActivity activity) {
      this.activityWeakReference = new WeakReference<>(activity);
    }

    @Override
    public void delete() {
      AsyncTaskExecutors.createAsyncTaskExecutor()
          .submit(
              ASYNC_TASK_ID,
              new DeleteCallsTask(getActivity(), getActivity().contact, getActivity().entries));
    }

    private CallDetailsActivity getActivity() {
      return Preconditions.checkNotNull(activityWeakReference.get());
    }
  }

  private static final class ReportCallIdListener
      implements CallDetailsFooterViewHolder.ReportCallIdListener {
    private final WeakReference<Activity> activityWeakReference;

    ReportCallIdListener(Activity activity) {
      this.activityWeakReference = new WeakReference<>(activity);
    }

    @Override
    public void reportCallId(String number) {
      ReportDialogFragment.newInstance(number)
          .show(getActivity().getFragmentManager(), null /* tag */);
    }

    @Override
    public boolean canReportCallerId(String number) {
      return getActivity().getIntent().getExtras().getBoolean(EXTRA_CAN_REPORT_CALLER_ID, false);
    }

    private Activity getActivity() {
      return Preconditions.checkNotNull(activityWeakReference.get());
    }
  }

  private static final class EnrichedCallHistoricalDataChangedListener
      implements EnrichedCallManager.HistoricalDataChangedListener {
    private final WeakReference<CallDetailsActivity> activityWeakReference;

    EnrichedCallHistoricalDataChangedListener(CallDetailsActivity activity) {
      this.activityWeakReference = new WeakReference<>(activity);
    }

    @Override
    public void onHistoricalDataChanged() {
      CallDetailsActivity activity = getActivity();
      Map<CallDetailsEntry, List<HistoryResult>> mappedResults =
          getAllHistoricalData(activity.contact.getNumber(), activity.entries);

      activity.adapter.updateCallDetailsEntries(
          generateAndMapNewCallDetailsEntriesHistoryResults(
                  activity.contact.getNumber(), activity.entries, mappedResults)
              .getEntriesList());
    }

    private CallDetailsActivity getActivity() {
      return Preconditions.checkNotNull(activityWeakReference.get());
    }

    @NonNull
    private Map<CallDetailsEntry, List<HistoryResult>> getAllHistoricalData(
        @Nullable String number, @NonNull CallDetailsEntries entries) {
      if (number == null) {
        return Collections.emptyMap();
      }

      Map<CallDetailsEntry, List<HistoryResult>> historicalData =
          EnrichedCallComponent.get(getActivity())
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
              "CallDetailsActivity.generateAndMapNewCallDetailsEntriesHistoryResults",
              "mapped %d results",
              newEntry.getHistoryResultsList().size());
        }
        mutableCallDetailsEntries.addEntries(newEntry.build());
      }
      return mutableCallDetailsEntries.build();
    }
  }
}
