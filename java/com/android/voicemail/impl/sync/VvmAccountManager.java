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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.os.UserManager;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.ArraySet;
import com.android.dialer.common.Assert;
import com.android.dialer.common.PerAccountSharedPreferences;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.util.DialerUtils;
import com.android.voicemail.impl.OmtpConstants;
import com.android.voicemail.impl.VisualVoicemailPreferences;
import com.android.voicemail.impl.VoicemailStatus;
import com.android.voicemail.impl.sms.StatusMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tracks the activation state of a visual voicemail phone account. An account is considered
 * activated if it has valid connection information from the {@link StatusMessage} stored on the
 * device. Once activation/provisioning is completed, {@link #addAccount(Context,
 * PhoneAccountHandle, StatusMessage)} should be called to store the connection information. When an
 * account is removed or if the connection information is deemed invalid, {@link
 * #removeAccount(Context, PhoneAccountHandle)} should be called to clear the connection information
 * and allow reactivation.
 */
@TargetApi(VERSION_CODES.O)
public class VvmAccountManager {
  public static final String TAG = "VvmAccountManager";

  /** Listener for activation state changes. Will be called on the main thread. */
  public interface Listener {
    @MainThread
    void onActivationStateChanged(PhoneAccountHandle phoneAccountHandle, boolean isActivated);
  }

  @VisibleForTesting static final String IS_ACCOUNT_ACTIVATED = "is_account_activated";

  private static Set<Listener> listeners = new ArraySet<>();

  public static void addAccount(
      Context context, PhoneAccountHandle phoneAccountHandle, StatusMessage statusMessage) {
    VisualVoicemailPreferences preferences =
        new VisualVoicemailPreferences(context, phoneAccountHandle);
    statusMessage.putStatus(preferences.edit()).apply();
    setAccountActivated(context, phoneAccountHandle, true);

    ThreadUtil.postOnUiThread(
        () -> {
          for (Listener listener : listeners) {
            listener.onActivationStateChanged(phoneAccountHandle, true);
          }
        });
  }

  public static void removeAccount(Context context, PhoneAccountHandle phoneAccount) {
    VoicemailStatus.disable(context, phoneAccount);
    setAccountActivated(context, phoneAccount, false);
    VisualVoicemailPreferences preferences = new VisualVoicemailPreferences(context, phoneAccount);
    preferences
        .edit()
        .putString(OmtpConstants.IMAP_USER_NAME, null)
        .putString(OmtpConstants.IMAP_PASSWORD, null)
        .apply();
    ThreadUtil.postOnUiThread(
        () -> {
          for (Listener listener : listeners) {
            listener.onActivationStateChanged(phoneAccount, false);
          }
        });
  }

  public static boolean isAccountActivated(Context context, PhoneAccountHandle phoneAccount) {
    Assert.isNotNull(phoneAccount);
    PerAccountSharedPreferences preferences =
        getPreferenceForActivationState(context, phoneAccount);
    migrateActivationState(context, preferences, phoneAccount);
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

  @MainThread
  public static void addListener(Listener listener) {
    Assert.isMainThread();
    listeners.add(listener);
  }

  @MainThread
  public static void removeListener(Listener listener) {
    Assert.isMainThread();
    listeners.remove(listener);
  }

  /**
   * The activation state is moved from credential protected storage to device protected storage
   * after v10, so it can be checked under FBE. The state should be migrated to avoid reactivation.
   */
  private static void migrateActivationState(
      Context context,
      PerAccountSharedPreferences deviceProtectedPreference,
      PhoneAccountHandle phoneAccountHandle) {
    if (!context.getSystemService(UserManager.class).isUserUnlocked()) {
      return;
    }
    if (deviceProtectedPreference.contains(IS_ACCOUNT_ACTIVATED)) {
      return;
    }

    PerAccountSharedPreferences credentialProtectedPreference =
        new VisualVoicemailPreferences(context, phoneAccountHandle);

    deviceProtectedPreference
        .edit()
        .putBoolean(
            IS_ACCOUNT_ACTIVATED,
            credentialProtectedPreference.getBoolean(IS_ACCOUNT_ACTIVATED, false))
        .apply();
  }

  private static void setAccountActivated(
      Context context, PhoneAccountHandle phoneAccountHandle, boolean activated) {
    Assert.isNotNull(phoneAccountHandle);
    getPreferenceForActivationState(context, phoneAccountHandle)
        .edit()
        .putBoolean(IS_ACCOUNT_ACTIVATED, activated)
        .apply();
  }

  private static PerAccountSharedPreferences getPreferenceForActivationState(
      Context context, PhoneAccountHandle phoneAccountHandle) {
    return new PerAccountSharedPreferences(
        context,
        phoneAccountHandle,
        DialerUtils.getDefaultSharedPreferenceForDeviceProtectedStorageContext(context));
  }
}
