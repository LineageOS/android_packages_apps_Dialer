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
 * limitations under the License
 */

package com.android.dialer.app.voicemail.error;

import android.content.ComponentName;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.provider.VoicemailContract.Status;
import android.support.annotation.Nullable;
import android.support.v4.os.BuildCompat;
import android.telecom.PhoneAccountHandle;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import com.android.dialer.common.LogUtil;
import com.android.dialer.database.VoicemailStatusQuery;

/** Structured data from {@link android.provider.VoicemailContract.Status} */
public class VoicemailStatus {

  public final String sourcePackage;
  public final String type;

  public final String phoneAccountComponentName;
  public final String phoneAccountId;

  @Nullable public final Uri settingsUri;
  @Nullable public final Uri voicemailAccessUri;

  public final int configurationState;
  public final int dataChannelState;
  public final int notificationChannelState;

  public final int quotaOccupied;
  public final int quotaTotal;

  // System status

  public final boolean isAirplaneMode;

  /** Wraps the row currently pointed by <code>statusCursor</code> */
  public VoicemailStatus(Context context, Cursor statusCursor) {
    sourcePackage = getString(statusCursor, VoicemailStatusQuery.SOURCE_PACKAGE_INDEX, "");

    settingsUri = getUri(statusCursor, VoicemailStatusQuery.SETTINGS_URI_INDEX);
    voicemailAccessUri = getUri(statusCursor, VoicemailStatusQuery.VOICEMAIL_ACCESS_URI_INDEX);

    if (VERSION.SDK_INT >= VERSION_CODES.N_MR1) {
      type =
          getString(
              statusCursor, VoicemailStatusQuery.SOURCE_TYPE_INDEX, TelephonyManager.VVM_TYPE_OMTP);
      phoneAccountComponentName =
          getString(statusCursor, VoicemailStatusQuery.PHONE_ACCOUNT_COMPONENT_NAME, "");
      phoneAccountId = getString(statusCursor, VoicemailStatusQuery.PHONE_ACCOUNT_ID, "");
    } else {
      type = TelephonyManager.VVM_TYPE_OMTP;
      phoneAccountComponentName = "";
      phoneAccountId = "";
    }

    configurationState =
        getInt(
            statusCursor,
            VoicemailStatusQuery.CONFIGURATION_STATE_INDEX,
            Status.CONFIGURATION_STATE_NOT_CONFIGURED);
    dataChannelState =
        getInt(
            statusCursor,
            VoicemailStatusQuery.DATA_CHANNEL_STATE_INDEX,
            Status.DATA_CHANNEL_STATE_NO_CONNECTION);

    /* Before O, the NOTIFICATION_CHANNEL_STATE in the voicemail status table for the system
     * visual voicemail client always correspond to the service state (cellular signal availability)
     * Tracking the state in the background is redundant because it will not be visible to the
     * user. It is much simpler to poll the status on the UI side. The result is injected back to
     * the status query result so the handling will be consistent with other voicemail clients.
     */
    if (BuildCompat.isAtLeastO() && sourcePackage.equals(context.getPackageName())) {
      notificationChannelState =
          getNotificationChannelStateFormTelephony(context, getPhoneAccountHandle());
    } else {
      notificationChannelState =
          getInt(
              statusCursor,
              VoicemailStatusQuery.NOTIFICATION_CHANNEL_STATE_INDEX,
              Status.NOTIFICATION_CHANNEL_STATE_NO_CONNECTION);
    }
    isAirplaneMode =
        Settings.System.getInt(context.getContentResolver(), Global.AIRPLANE_MODE_ON, 0) != 0;

    if (VERSION.SDK_INT >= VERSION_CODES.N) {
      quotaOccupied =
          getInt(statusCursor, VoicemailStatusQuery.QUOTA_OCCUPIED_INDEX, Status.QUOTA_UNAVAILABLE);
      quotaTotal =
          getInt(statusCursor, VoicemailStatusQuery.QUOTA_TOTAL_INDEX, Status.QUOTA_UNAVAILABLE);
    } else {
      quotaOccupied = Status.QUOTA_UNAVAILABLE;
      quotaTotal = Status.QUOTA_UNAVAILABLE;
    }
  }

  private static int getNotificationChannelStateFormTelephony(
      Context context, PhoneAccountHandle phoneAccountHandle) {
    TelephonyManager telephonyManager =
        context
            .getSystemService(TelephonyManager.class)
            .createForPhoneAccountHandle(phoneAccountHandle);
    if (telephonyManager == null) {
      LogUtil.e("VoicemailStatus.constructor", "invalid PhoneAccountHandle");
      return Status.NOTIFICATION_CHANNEL_STATE_NO_CONNECTION;
    } else {
      int state = telephonyManager.getServiceState().getState();
      if (state == ServiceState.STATE_IN_SERVICE) {
        return Status.NOTIFICATION_CHANNEL_STATE_OK;
      } else {
        return Status.NOTIFICATION_CHANNEL_STATE_NO_CONNECTION;
      }
    }
  }

  private VoicemailStatus(Builder builder) {
    sourcePackage = builder.sourcePackage;
    phoneAccountComponentName = builder.phoneAccountComponentName;
    phoneAccountId = builder.phoneAccountId;
    type = builder.type;
    settingsUri = builder.settingsUri;
    voicemailAccessUri = builder.voicemailAccessUri;
    configurationState = builder.configurationState;
    dataChannelState = builder.dataChannelState;
    notificationChannelState = builder.notificationChannelState;
    quotaOccupied = builder.quotaOccupied;
    quotaTotal = builder.quotaTotal;
    isAirplaneMode = builder.isAirplaneMode;
  }

  static class Builder {

    private String sourcePackage = "";
    private String type = TelephonyManager.VVM_TYPE_OMTP;
    private String phoneAccountComponentName = "";
    private String phoneAccountId = "";

    @Nullable private Uri settingsUri;
    @Nullable private Uri voicemailAccessUri;

    private int configurationState = Status.CONFIGURATION_STATE_NOT_CONFIGURED;
    private int dataChannelState = Status.DATA_CHANNEL_STATE_NO_CONNECTION;
    private int notificationChannelState = Status.NOTIFICATION_CHANNEL_STATE_NO_CONNECTION;

    private int quotaOccupied = Status.QUOTA_UNAVAILABLE;
    private int quotaTotal = Status.QUOTA_UNAVAILABLE;

    private boolean isAirplaneMode;

    public VoicemailStatus build() {
      return new VoicemailStatus(this);
    }

    public Builder setSourcePackage(String sourcePackage) {
      this.sourcePackage = sourcePackage;
      return this;
    }

    public Builder setType(String type) {
      this.type = type;
      return this;
    }

    public Builder setPhoneAccountComponentName(String name) {
      this.phoneAccountComponentName = name;
      return this;
    }

    public Builder setPhoneAccountId(String id) {
      this.phoneAccountId = id;
      return this;
    }

    public Builder setSettingsUri(Uri settingsUri) {
      this.settingsUri = settingsUri;
      return this;
    }

    public Builder setVoicemailAccessUri(Uri voicemailAccessUri) {
      this.voicemailAccessUri = voicemailAccessUri;
      return this;
    }

    public Builder setConfigurationState(int configurationState) {
      this.configurationState = configurationState;
      return this;
    }

    public Builder setDataChannelState(int dataChannelState) {
      this.dataChannelState = dataChannelState;
      return this;
    }

    public Builder setNotificationChannelState(int notificationChannelState) {
      this.notificationChannelState = notificationChannelState;
      return this;
    }

    public Builder setQuotaOccupied(int quotaOccupied) {
      this.quotaOccupied = quotaOccupied;
      return this;
    }

    public Builder setQuotaTotal(int quotaTotal) {
      this.quotaTotal = quotaTotal;
      return this;
    }

    public Builder setAirplaneMode(boolean isAirplaneMode) {
      this.isAirplaneMode = isAirplaneMode;
      return this;
    }
  }

  public boolean isActive() {
    switch (configurationState) {
      case Status.CONFIGURATION_STATE_NOT_CONFIGURED:
      case Status.CONFIGURATION_STATE_DISABLED:
        return false;
      default:
        return true;
    }
  }

  @Override
  public String toString() {
    return "VoicemailStatus["
        + "sourcePackage: "
        + sourcePackage
        + ", type:"
        + type
        + ", settingsUri: "
        + settingsUri
        + ", voicemailAccessUri: "
        + voicemailAccessUri
        + ", configurationState: "
        + configurationState
        + ", dataChannelState: "
        + dataChannelState
        + ", notificationChannelState: "
        + notificationChannelState
        + ", quotaOccupied: "
        + quotaOccupied
        + ", quotaTotal: "
        + quotaTotal
        + ", isAirplaneMode: "
        + isAirplaneMode
        + "]";
  }

  @Nullable
  private static Uri getUri(Cursor cursor, int index) {
    if (cursor.getString(index) != null) {
      return Uri.parse(cursor.getString(index));
    }
    return null;
  }

  private static int getInt(Cursor cursor, int index, int defaultValue) {
    if (cursor.isNull(index)) {
      return defaultValue;
    }
    return cursor.getInt(index);
  }

  private static String getString(Cursor cursor, int index, String defaultValue) {
    if (cursor.isNull(index)) {
      return defaultValue;
    }
    return cursor.getString(index);
  }

  public PhoneAccountHandle getPhoneAccountHandle() {
    return new PhoneAccountHandle(
        ComponentName.unflattenFromString(phoneAccountComponentName), phoneAccountId);
  }
}
