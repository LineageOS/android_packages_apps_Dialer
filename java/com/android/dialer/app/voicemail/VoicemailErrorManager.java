/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.app.voicemail;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import com.android.dialer.app.calllog.CallLogAlertManager;
import com.android.dialer.app.calllog.CallLogModalAlertManager;
import com.android.dialer.app.voicemail.error.VoicemailErrorAlert;
import com.android.dialer.app.voicemail.error.VoicemailErrorMessageCreator;
import com.android.dialer.app.voicemail.error.VoicemailStatus;
import com.android.dialer.app.voicemail.error.VoicemailStatusReader;
import com.android.dialer.database.CallLogQueryHandler;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches voicemail status and generate {@link VoicemailStatus} for {@link VoicemailErrorAlert} to
 * show.
 */
public class VoicemailErrorManager implements CallLogQueryHandler.Listener, VoicemailStatusReader {

  private final Context context;
  private final CallLogQueryHandler callLogQueryHandler;
  private final VoicemailErrorAlert alertItem;

  private final ContentObserver statusObserver =
      new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
          super.onChange(selfChange);
          maybeFetchStatus();
        }
      };

  private boolean isForeground;
  private boolean statusInvalidated;

  public VoicemailErrorManager(
      Context context,
      CallLogAlertManager alertManager,
      CallLogModalAlertManager modalAlertManager) {
    this.context = context;
    alertItem =
        new VoicemailErrorAlert(
            context, alertManager, modalAlertManager, new VoicemailErrorMessageCreator());
    callLogQueryHandler = new CallLogQueryHandler(context, context.getContentResolver(), this);
    maybeFetchStatus();
  }

  public ContentObserver getContentObserver() {
    return statusObserver;
  }

  @Override
  public void onVoicemailStatusFetched(Cursor statusCursor) {
    List<VoicemailStatus> statuses = new ArrayList<>();
    while (statusCursor.moveToNext()) {
      VoicemailStatus status = new VoicemailStatus(context, statusCursor);
      if (status.isActive()) {
        statuses.add(status);
      }
    }
    alertItem.updateStatus(statuses, this);
    // TODO: b/30668323 support error from multiple sources.
    return;
  }

  @Override
  public void onVoicemailUnreadCountFetched(Cursor cursor) {
    // Do nothing
  }

  @Override
  public void onMissedCallsUnreadCountFetched(Cursor cursor) {
    // Do nothing
  }

  @Override
  public boolean onCallsFetched(Cursor combinedCursor) {
    // Do nothing
    return false;
  }

  public void onResume() {
    isForeground = true;
    if (statusInvalidated) {
      maybeFetchStatus();
    }
  }

  public void onPause() {
    isForeground = false;
    statusInvalidated = false;
  }

  @Override
  public void refresh() {
    maybeFetchStatus();
  }

  /**
   * Fetch the status when the dialer is in foreground, or queue a fetch when the dialer resumes.
   */
  private void maybeFetchStatus() {
    if (!isForeground) {
      // Dialer is in the background, UI should not be updated. Reload the status when it resumes.
      statusInvalidated = true;
      return;
    }
    callLogQueryHandler.fetchVoicemailStatus();
  }
}
