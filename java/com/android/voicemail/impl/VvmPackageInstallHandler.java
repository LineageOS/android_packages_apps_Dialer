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
 * limitations under the License
 */

package com.android.voicemail.impl;

import android.content.Context;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import com.android.voicemail.impl.settings.VisualVoicemailSettingsUtil;

/**
 * When a new package is installed, check if it matches any of the vvm carrier apps of the currently
 * enabled dialer VVM sources. The dialer VVM client will be disabled upon carrier VVM app
 * installation, unless it was explicitly enabled by the user.
 *
 * <p>The ACTION_PACKAGE_ADDED broadcast can no longer be received. (see
 * https://developer.android.com/preview/features/background.html#broadcasts) New apps are scanned
 * when a VVM SMS is received instead, as it can be a result of the carrier VVM app trying to run
 * activation.
 */
public final class VvmPackageInstallHandler {

  /**
   * Iterates through all phone account and disable VVM on a account if {@code packageName} is
   * listed as a carrier VVM package.
   */
  public static void handlePackageInstalled(Context context) {
    // This get called every time an app is installed and will be noisy. Don't log until the app
    // is identified as a carrier VVM app.
    for (PhoneAccountHandle phoneAccount :
        context.getSystemService(TelecomManager.class).getCallCapablePhoneAccounts()) {
      OmtpVvmCarrierConfigHelper carrierConfigHelper =
          new OmtpVvmCarrierConfigHelper(context, phoneAccount);
      if (!carrierConfigHelper.isValid()) {
        continue;
      }
      if (!carrierConfigHelper.isCarrierAppInstalled()) {
        continue;
      }

      // Force deactivate the client.
      VvmLog.i(
          "VvmPackageInstallHandler.handlePackageInstalled",
          "Carrier VVM package installed, disabling system VVM client");
      VisualVoicemailSettingsUtil.setEnabled(context, phoneAccount, false);
    }
  }
}
