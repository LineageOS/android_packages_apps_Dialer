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

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.Toolbar.OnMenuItemClickListener;
import android.view.MenuItem;
import com.android.dialer.calldetails.CallDetailsEntries.CallDetailsEntry;
import com.android.dialer.callrecord.CallRecordingDataStore;
import com.android.dialer.common.Assert;
import com.android.dialer.common.concurrent.AsyncTaskExecutors;
import com.android.dialer.dialercontact.DialerContact;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.UiAction;
import com.android.dialer.performancereport.PerformanceReport;
import com.android.dialer.postcall.PostCall;
import com.android.dialer.protos.ProtoParsers;
import java.util.List;

/** Displays the details of a specific call log entry. */
public class CallDetailsActivity extends AppCompatActivity
    implements OnMenuItemClickListener, CallDetailsFooterViewHolder.ReportCallIdListener {

  public static final String EXTRA_PHONE_NUMBER = "phone_number";
  public static final String EXTRA_HAS_ENRICHED_CALL_DATA = "has_enriched_call_data";
  private static final String EXTRA_CALL_DETAILS_ENTRIES = "call_details_entries";
  private static final String EXTRA_CONTACT = "contact";
  private static final String EXTRA_CAN_REPORT_CALLER_ID = "can_report_caller_id";
  private static final String TASK_DELETE = "task_delete";

  private List<CallDetailsEntry> entries;
  private DialerContact contact;
  private CallRecordingDataStore callRecordingDataStore;

  public static boolean isLaunchIntent(Intent intent) {
    return intent.getComponent() != null
        && CallDetailsActivity.class.getName().equals(intent.getComponent().getClassName());
  }

  public static Intent newInstance(
      Context context,
      @NonNull CallDetailsEntries details,
      @NonNull DialerContact contact,
      boolean canReportCallerId) {
    Assert.isNotNull(details);
    Assert.isNotNull(contact);

    Intent intent = new Intent(context, CallDetailsActivity.class);
    ProtoParsers.put(intent, EXTRA_CONTACT, contact);
    ProtoParsers.put(intent, EXTRA_CALL_DETAILS_ENTRIES, details);
    intent.putExtra(EXTRA_CAN_REPORT_CALLER_ID, canReportCallerId);
    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.call_details_activity);
    Toolbar toolbar = findViewById(R.id.toolbar);
    toolbar.inflateMenu(R.menu.call_details_menu);
    toolbar.setOnMenuItemClickListener(this);
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
                intent, EXTRA_CALL_DETAILS_ENTRIES, CallDetailsEntries.getDefaultInstance())
            .getEntriesList();

    RecyclerView recyclerView = findViewById(R.id.recycler_view);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));
    recyclerView.setAdapter(new CallDetailsAdapter(this,
        contact, entries, this, callRecordingDataStore));
    PerformanceReport.logOnScrollStateChange(recyclerView);
  }

  @Override
  public boolean onMenuItemClick(MenuItem item) {
    if (item.getItemId() == R.id.call_detail_delete_menu_item) {
      Logger.get(this).logImpression(DialerImpression.Type.USER_DELETED_CALL_LOG_ITEM);
      AsyncTaskExecutors.createAsyncTaskExecutor().submit(TASK_DELETE, new DeleteCallsTask());
      item.setEnabled(false);
      return true;
    }
    return false;
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

  /** Delete specified calls from the call log. */
  private class DeleteCallsTask extends AsyncTask<Void, Void, Void> {

    private final String callIds;

    DeleteCallsTask() {
      StringBuilder callIds = new StringBuilder();
      for (CallDetailsEntry entry : entries) {
        if (callIds.length() != 0) {
          callIds.append(",");
        }
        callIds.append(entry.getCallId());
      }
      this.callIds = callIds.toString();
    }

    @Override
    protected Void doInBackground(Void... params) {
      getContentResolver()
          .delete(Calls.CONTENT_URI, CallLog.Calls._ID + " IN (" + callIds + ")", null);
      return null;
    }

    @Override
    public void onPostExecute(Void result) {
      Intent data = new Intent();
      data.putExtra(EXTRA_PHONE_NUMBER, contact.getNumber());
      for (CallDetailsEntry entry : entries) {
        if (entry.getHistoryResultsCount() > 0) {
          data.putExtra(EXTRA_HAS_ENRICHED_CALL_DATA, true);
          break;
        }
      }
      setResult(RESULT_OK, data);
      finish();
    }
  }
}
