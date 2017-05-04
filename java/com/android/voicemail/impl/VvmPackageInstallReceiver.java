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
package com.android.voicemail.impl;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import com.android.voicemail.VoicemailComponent;
import com.android.voicemail.impl.settings.VisualVoicemailSettingsUtil;

/**
 * When a new package is installed, check if it matches any of the vvm carrier apps of the currently
 * enabled dialer VVM sources. The dialer VVM client will be disabled upon carrier VVM app
 * installation, unless it was explicitly enabled by the user.
 */
public class VvmPackageInstallReceiver extends BroadcastReceiver {

  private static final String TAG = "VvmPkgInstallReceiver";

  @Override
  public void onReceive(Context context, Intent intent) {
    if (!VoicemailComponent.get(context).getVoicemailClient().isVoicemailModuleEnabled()) {
      return;
    }

    if (intent.getData() == null) {
      return;
    }

    String packageName = intent.getData().getSchemeSpecificPart();
    if (packageName == null) {
      return;
    }

    // This get called every time an app is installed and will be noisy. Don't log until the app
    // is identified as a carrier VVM app.
    for (PhoneAccountHandle phoneAccount :
        context.getSystemService(TelecomManager.class).getCallCapablePhoneAccounts()) {
      OmtpVvmCarrierConfigHelper carrierConfigHelper =
          new OmtpVvmCarrierConfigHelper(context, phoneAccount);
      if (!carrierConfigHelper.isValid()) {
        continue;
      }
      if (carrierConfigHelper.getCarrierVvmPackageNames() == null) {
        continue;
      }
      if (!carrierConfigHelper.getCarrierVvmPackageNames().contains(packageName)) {
        continue;
      }

      VvmLog.i(TAG, "Carrier app installed");
      if (VisualVoicemailSettingsUtil.isEnabledUserSet(context, phoneAccount)) {
        // Skip the check if this voicemail source's setting is overridden by the user.
        VvmLog.i(TAG, "VVM enabled by user, not disabling");
        continue;
      }

      // Force deactivate the client. The user can re-enable it in the settings.
      // There is no need to update the settings for deactivation. At this point, if the
      // default value is used it should be false because a carrier package is present.
      VvmLog.i(TAG, "Carrier VVM package installed, disabling system VVM client");
      VisualVoicemailSettingsUtil.setEnabled(context, phoneAccount, false);
    }
  }
}
