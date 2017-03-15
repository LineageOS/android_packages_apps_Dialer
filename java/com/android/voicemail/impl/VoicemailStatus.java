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

package com.android.voicemail.impl;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.VoicemailContract;
import android.provider.VoicemailContract.Status;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;

public class VoicemailStatus {

  private static final String TAG = "VvmStatus";

  public static class Editor {

    private final Context mContext;
    @Nullable private final PhoneAccountHandle mPhoneAccountHandle;

    private ContentValues mValues = new ContentValues();

    private Editor(Context context, PhoneAccountHandle phoneAccountHandle) {
      mContext = context;
      mPhoneAccountHandle = phoneAccountHandle;
      if (mPhoneAccountHandle == null) {
        VvmLog.w(
            TAG,
            "VoicemailStatus.Editor created with null phone account, status will"
                + " not be written");
      }
    }

    @Nullable
    public PhoneAccountHandle getPhoneAccountHandle() {
      return mPhoneAccountHandle;
    }

    public Editor setType(String type) {
      mValues.put(Status.SOURCE_TYPE, type);
      return this;
    }

    public Editor setConfigurationState(int configurationState) {
      mValues.put(Status.CONFIGURATION_STATE, configurationState);
      return this;
    }

    public Editor setDataChannelState(int dataChannelState) {
      mValues.put(Status.DATA_CHANNEL_STATE, dataChannelState);
      return this;
    }

    public Editor setNotificationChannelState(int notificationChannelState) {
      mValues.put(Status.NOTIFICATION_CHANNEL_STATE, notificationChannelState);
      return this;
    }

    public Editor setQuota(int occupied, int total) {
      if (occupied == VoicemailContract.Status.QUOTA_UNAVAILABLE
          && total == VoicemailContract.Status.QUOTA_UNAVAILABLE) {
        return this;
      }

      mValues.put(Status.QUOTA_OCCUPIED, occupied);
      mValues.put(Status.QUOTA_TOTAL, total);
      return this;
    }

    /**
     * Apply the changes to the {@link VoicemailStatus} {@link #Editor}.
     *
     * @return {@code true} if the changes were successfully applied, {@code false} otherwise.
     */
    public boolean apply() {
      if (mPhoneAccountHandle == null) {
        return false;
      }
      mValues.put(
          Status.PHONE_ACCOUNT_COMPONENT_NAME,
          mPhoneAccountHandle.getComponentName().flattenToString());
      mValues.put(Status.PHONE_ACCOUNT_ID, mPhoneAccountHandle.getId());
      ContentResolver contentResolver = mContext.getContentResolver();
      Uri statusUri = VoicemailContract.Status.buildSourceUri(mContext.getPackageName());
      try {
        contentResolver.insert(statusUri, mValues);
      } catch (IllegalArgumentException iae) {
        VvmLog.e(TAG, "apply :: failed to insert content resolver ", iae);
        mValues.clear();
        return false;
      }
      mValues.clear();
      return true;
    }

    public ContentValues getValues() {
      return mValues;
    }
  }

  /**
   * A voicemail status editor that the decision of whether to actually write to the database can be
   * deferred. This object will be passed around as a usual {@link Editor}, but {@link #apply()}
   * doesn't do anything. If later the creator of this object decides any status changes written to
   * it should be committed, {@link #deferredApply()} should be called.
   */
  public static class DeferredEditor extends Editor {

    private DeferredEditor(Context context, PhoneAccountHandle phoneAccountHandle) {
      super(context, phoneAccountHandle);
    }

    @Override
    public boolean apply() {
      // Do nothing
      return true;
    }

    public void deferredApply() {
      super.apply();
    }
  }

  public static Editor edit(Context context, PhoneAccountHandle phoneAccountHandle) {
    return new Editor(context, phoneAccountHandle);
  }

  /**
   * Reset the status to the "disabled" state, which the UI should not show anything for this
   * phoneAccountHandle.
   */
  public static void disable(Context context, PhoneAccountHandle phoneAccountHandle) {
    edit(context, phoneAccountHandle)
        .setConfigurationState(Status.CONFIGURATION_STATE_NOT_CONFIGURED)
        .setDataChannelState(Status.DATA_CHANNEL_STATE_NO_CONNECTION)
        .setNotificationChannelState(Status.NOTIFICATION_CHANNEL_STATE_NO_CONNECTION)
        .apply();
  }

  public static DeferredEditor deferredEdit(
      Context context, PhoneAccountHandle phoneAccountHandle) {
    return new DeferredEditor(context, phoneAccountHandle);
  }
}
