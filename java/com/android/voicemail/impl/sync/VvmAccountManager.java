/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.voicemail.impl.sync;

import android.content.Context;
import android.support.annotation.NonNull;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import com.android.dialer.common.Assert;
import com.android.voicemail.impl.OmtpConstants;
import com.android.voicemail.impl.VisualVoicemailPreferences;
import com.android.voicemail.impl.VoicemailStatus;
import com.android.voicemail.impl.sms.StatusMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the activation state of a visual voicemail phone account. An account is considered
 * activated if it has valid connection information from the {@link StatusMessage} stored on the
 * device. Once activation/provisioning is completed, {@link #addAccount(Context,
 * PhoneAccountHandle, StatusMessage)} should be called to store the connection information. When an
 * account is removed or if the connection information is deemed invalid, {@link
 * #removeAccount(Context, PhoneAccountHandle)} should be called to clear the connection information
 * and allow reactivation.
 */
public class VvmAccountManager {
  public static final String TAG = "VvmAccountManager";

  private static final String IS_ACCOUNT_ACTIVATED = "is_account_activated";

  public static void addAccount(
      Context context, PhoneAccountHandle phoneAccountHandle, StatusMessage statusMessage) {
    VisualVoicemailPreferences preferences =
        new VisualVoicemailPreferences(context, phoneAccountHandle);
    statusMessage.putStatus(preferences.edit()).putBoolean(IS_ACCOUNT_ACTIVATED, true).apply();
  }

  public static void removeAccount(Context context, PhoneAccountHandle phoneAccount) {
    VoicemailStatus.disable(context, phoneAccount);
    VisualVoicemailPreferences preferences = new VisualVoicemailPreferences(context, phoneAccount);
    preferences
        .edit()
        .putBoolean(IS_ACCOUNT_ACTIVATED, false)
        .putString(OmtpConstants.IMAP_USER_NAME, null)
        .putString(OmtpConstants.IMAP_PASSWORD, null)
        .apply();
  }

  public static boolean isAccountActivated(Context context, PhoneAccountHandle phoneAccount) {
    Assert.isNotNull(phoneAccount);
    VisualVoicemailPreferences preferences = new VisualVoicemailPreferences(context, phoneAccount);
    return preferences.getBoolean(IS_ACCOUNT_ACTIVATED, false);
  }

  @NonNull
  public static List<PhoneAccountHandle> getActiveAccounts(Context context) {
    List<PhoneAccountHandle> results = new ArrayList<>();
    for (PhoneAccountHandle phoneAccountHandle :
        context.getSystemService(TelecomManager.class).getCallCapablePhoneAccounts()) {
      if (isAccountActivated(context, phoneAccountHandle)) {
        results.add(phoneAccountHandle);
      }
    }
    return results;
  }
}
