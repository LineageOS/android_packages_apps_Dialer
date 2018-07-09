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
import com.android.dialer.calldetails.CallDetailsEntryViewHolder.CallDetailsEntryListener;
import com.android.dialer.calldetails.CallDetailsFooterViewHolder.DeleteCallDetailsListener;
import com.android.dialer.calldetails.CallDetailsFooterViewHolder.ReportCallIdListener;
import com.android.dialer.calldetails.CallDetailsHeaderViewHolder.CallDetailsHeaderListener;
import com.android.dialer.callrecord.CallRecordingDataStore;
import com.android.dialer.common.Assert;
import com.android.dialer.dialercontact.DialerContact;
import com.android.dialer.protos.ProtoParsers;

/**
 * Displays the details of a specific call log entry.
 *
 * <p>This activity is for the old call log.
 *
 * <p>See {@link CallDetailsAdapterCommon} for logic shared between this activity and the one for
 * the new call log.
 */
public final class OldCallDetailsActivity extends CallDetailsActivityCommon {
  public static final String EXTRA_CALL_DETAILS_ENTRIES = "call_details_entries";
  public static final String EXTRA_CONTACT = "contact";

  /** Contains info to be shown in the header. */
  private DialerContact contact;

  public static boolean isLaunchIntent(Intent intent) {
    return intent.getComponent() != null
        && OldCallDetailsActivity.class.getName().equals(intent.getComponent().getClassName());
  }

  /** Returns an {@link Intent} to launch this activity. */
  public static Intent newInstance(
      Context context,
      CallDetailsEntries details,
      DialerContact contact,
      boolean canReportCallerId,
      boolean canSupportAssistedDialing) {
    Intent intent = new Intent(context, OldCallDetailsActivity.class);
    ProtoParsers.put(intent, EXTRA_CONTACT, Assert.isNotNull(contact));
    ProtoParsers.put(intent, EXTRA_CALL_DETAILS_ENTRIES, Assert.isNotNull(details));
    intent.putExtra(EXTRA_CAN_REPORT_CALLER_ID, canReportCallerId);
    intent.putExtra(EXTRA_CAN_SUPPORT_ASSISTED_DIALING, canSupportAssistedDialing);
    return intent;
  }

  @Override
  protected void handleIntent(Intent intent) {
    Assert.checkArgument(intent.hasExtra(EXTRA_CONTACT));
    Assert.checkArgument(intent.hasExtra(EXTRA_CALL_DETAILS_ENTRIES));
    Assert.checkArgument(intent.hasExtra(EXTRA_CAN_REPORT_CALLER_ID));
    Assert.checkArgument(intent.hasExtra(EXTRA_CAN_SUPPORT_ASSISTED_DIALING));

    contact = ProtoParsers.getTrusted(intent, EXTRA_CONTACT, DialerContact.getDefaultInstance());
    setCallDetailsEntries(
        ProtoParsers.getTrusted(
            intent, EXTRA_CALL_DETAILS_ENTRIES, CallDetailsEntries.getDefaultInstance()));
    loadRttTranscriptAvailability();
  }

  @Override
  protected CallDetailsAdapterCommon createAdapter(
      CallDetailsEntryListener callDetailsEntryListener,
      CallDetailsHeaderListener callDetailsHeaderListener,
      ReportCallIdListener reportCallIdListener,
      DeleteCallDetailsListener deleteCallDetailsListener,
      CallRecordingDataStore callRecordingDataStore) {
    return new OldCallDetailsAdapter(
        /* context = */ this,
        contact,
        getCallDetailsEntries(),
        callDetailsEntryListener,
        callDetailsHeaderListener,
        reportCallIdListener,
        deleteCallDetailsListener,
        callRecordingDataStore);
  }

  @Override
  protected String getNumber() {
    return contact.getNumber();
  }
}
