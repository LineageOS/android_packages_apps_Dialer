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
import android.support.annotation.MainThread;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import com.android.dialer.app.calllog.CallLogAlertManager;
import com.android.dialer.app.calllog.CallLogModalAlertManager;
import com.android.dialer.app.voicemail.error.VoicemailErrorAlert;
import com.android.dialer.app.voicemail.error.VoicemailErrorMessageCreator;
import com.android.dialer.app.voicemail.error.VoicemailStatus;
import com.android.dialer.app.voicemail.error.VoicemailStatusReader;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.database.CallLogQueryHandler;
import com.android.voicemail.VoicemailComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fetches voicemail status and generate {@link VoicemailStatus} for {@link VoicemailErrorAlert} to
 * show.
 */
public class VoicemailErrorManager implements CallLogQueryHandler.Listener, VoicemailStatusReader {

  private final Context context;
  private final CallLogQueryHandler callLogQueryHandler;
  private final VoicemailErrorAlert alertItem;

  private final Map<PhoneAccountHandle, ServiceStateListener> listeners = new ArrayMap<>();

  private final ContentObserver statusObserver =
      new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
          super.onChange(selfChange);
          fetchStatus();
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
    fetchStatus();
  }

  public ContentObserver getContentObserver() {
    return statusObserver;
  }

  @MainThread
  @Override
  public void onVoicemailStatusFetched(Cursor statusCursor) {
    List<VoicemailStatus> statuses = new ArrayList<>();
    while (statusCursor.moveToNext()) {
      VoicemailStatus status = new VoicemailStatus(context, statusCursor);
      if (status.isActive()) {
        statuses.add(status);
        addServiceStateListener(status);
      }
    }
    alertItem.updateStatus(statuses, this);
    // TODO: b/30668323 support error from multiple sources.
    return;
  }

  @MainThread
  private void addServiceStateListener(VoicemailStatus status) {
    Assert.isMainThread();
    if (!VoicemailComponent.get(context).getVoicemailClient().isVoicemailModuleEnabled()) {
      LogUtil.i("VoicemailErrorManager.addServiceStateListener", "VVM module not enabled");
      return;
    }
    if (!status.sourcePackage.equals(context.getPackageName())) {
      LogUtil.i("VoicemailErrorManager.addServiceStateListener", "non-dialer source");
      return;
    }
    TelephonyManager telephonyManager =
        context
            .getSystemService(TelephonyManager.class)
            .createForPhoneAccountHandle(status.getPhoneAccountHandle());
    if (telephonyManager == null) {
      LogUtil.e("VoicemailErrorManager.addServiceStateListener", "invalid PhoneAccountHandle");
      return;
    }
    PhoneAccountHandle phoneAccountHandle = status.getPhoneAccountHandle();
    if (listeners.containsKey(phoneAccountHandle)) {
      return;
    }
    LogUtil.i(
        "VoicemailErrorManager.addServiceStateListener",
        "adding listener for " + phoneAccountHandle);
    ServiceStateListener serviceStateListener = new ServiceStateListener();
    telephonyManager.listen(serviceStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
    listeners.put(phoneAccountHandle, serviceStateListener);
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
      fetchStatus();
    }
  }

  public void onPause() {
    isForeground = false;
    statusInvalidated = false;
  }

  public void onDestroy() {
    TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
    for (ServiceStateListener listener : listeners.values()) {
      telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE);
    }
  }

  @Override
  public void refresh() {
    fetchStatus();
  }

  /**
   * Fetch the status when the dialer is in foreground, or queue a fetch when the dialer resumes.
   */
  private void fetchStatus() {
    if (!isForeground) {
      // Dialer is in the background, UI should not be updated. Reload the status when it resumes.
      statusInvalidated = true;
      return;
    }
    callLogQueryHandler.fetchVoicemailStatus();
  }

  private class ServiceStateListener extends PhoneStateListener {

    @Override
    public void onServiceStateChanged(ServiceState serviceState) {
      fetchStatus();
    }
  }
}
