/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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
package com.android.voicemail.impl.settings;

import android.content.Context;
import android.provider.VoicemailContract.Voicemails;
import android.telecom.PhoneAccountHandle;

import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.voicemail.VoicemailComponent;
import com.android.voicemail.impl.OmtpVvmCarrierConfigHelper;
import com.android.voicemail.impl.VisualVoicemailPreferences;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.sync.VvmAccountManager;

/** Save whether or not a particular account is enabled in shared to be retrieved later. */
public class VisualVoicemailSettingsUtil {

  private static final String IS_ENABLED_KEY = "is_enabled";
  private static final String ARCHIVE_ENABLED_KEY = "archive_is_enabled";

  public static void setEnabled(
      Context context, PhoneAccountHandle phoneAccount, boolean isEnabled) {
    VvmLog.i("VisualVoicemailSettingsUtil.setEnable", phoneAccount + " enabled:" + isEnabled);
    new VisualVoicemailPreferences(context, phoneAccount)
        .edit()
        .putBoolean(IS_ENABLED_KEY, isEnabled)
        .apply();
    OmtpVvmCarrierConfigHelper config = new OmtpVvmCarrierConfigHelper(context, phoneAccount);
    if (isEnabled) {
      config.startActivation();
    } else {
      VvmAccountManager.removeAccount(context, phoneAccount);
      config.startDeactivation();
      // Remove all voicemails from the database
      DialerExecutorComponent.get(context)
          .dialerExecutorFactory()
          .createNonUiTaskBuilder(new VoicemailDeleteWorker(context))
          .onSuccess(VisualVoicemailSettingsUtil::onSuccess)
          .onFailure(VisualVoicemailSettingsUtil::onFailure)
          .build()
          .executeParallel(null);
    }
  }

  private static void onSuccess(Void unused) {
    VvmLog.i("VisualVoicemailSettingsUtil.onSuccess", "delete voicemails");
  }

  private static void onFailure(Throwable t) {
    VvmLog.e("VisualVoicemailSettingsUtil.onFailure", "delete voicemails", t);
  }

  public static void setArchiveEnabled(
      Context context, PhoneAccountHandle phoneAccount, boolean isEnabled) {
    Assert.checkArgument(
        VoicemailComponent.get(context).getVoicemailClient().isVoicemailArchiveAvailable(context));
    new VisualVoicemailPreferences(context, phoneAccount)
        .edit()
        .putBoolean(ARCHIVE_ENABLED_KEY, isEnabled)
        .apply();
  }

  public static boolean isEnabled(Context context, PhoneAccountHandle phoneAccount) {
    if (phoneAccount == null) {
      LogUtil.i("VisualVoicemailSettingsUtil.isEnabled", "phone account is null");
      return false;
    }

    VisualVoicemailPreferences prefs = new VisualVoicemailPreferences(context, phoneAccount);
    if (prefs.contains(IS_ENABLED_KEY)) {
      // isEnableByDefault is a bit expensive, so don't use it as default value of
      // getBoolean(). The "false" here should never be actually used.
      return prefs.getBoolean(IS_ENABLED_KEY, false);
    }
    return new OmtpVvmCarrierConfigHelper(context, phoneAccount).isEnabledByDefault();
  }

  public static boolean isArchiveEnabled(Context context, PhoneAccountHandle phoneAccount) {
    Assert.isNotNull(phoneAccount);

    VisualVoicemailPreferences prefs = new VisualVoicemailPreferences(context, phoneAccount);
    return prefs.getBoolean(ARCHIVE_ENABLED_KEY, false);
  }

  /** Delete all the voicemails whose source_package field matches this package */
  private static class VoicemailDeleteWorker implements Worker<Void, Void> {
    private final Context context;

    VoicemailDeleteWorker(Context context) {
      this.context = context;
    }

    @Override
    public Void doInBackground(Void unused) {
      int deleted =
          context
              .getContentResolver()
              .delete(Voicemails.buildSourceUri(context.getPackageName()), null, null);

      VvmLog.i("VisualVoicemailSettingsUtil.doInBackground", "deleted " + deleted + " voicemails");
      return null;
    }
  }
}
