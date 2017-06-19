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
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.VoicemailContract.Status;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import com.android.contacts.common.compat.TelephonyManagerCompat;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;

/**
 * This class will detect the corruption in the voicemail status and log it so we can track how many
 * users are affected.
 */
public class VoicemailStatusCorruptionHandler {

  /** Where the check is made so logging can be done. */
  public enum Source {
    Activity,
    Notification
  }

  private static final String CONFIG_VVM_STATUS_FIX_DISABLED = "vvm_status_fix_disabled";

  public static void maybeFixVoicemailStatus(Context context, Cursor statusCursor, Source source) {

    if (ConfigProviderBindings.get(context).getBoolean(CONFIG_VVM_STATUS_FIX_DISABLED, false)) {
      return;
    }

    if (VERSION.SDK_INT != VERSION_CODES.N_MR1) {
      // This issue is specific to N MR1, it is fixed in future SDK.
      return;
    }

    if (statusCursor.getCount() == 0) {
      return;
    }

    statusCursor.moveToFirst();
    VoicemailStatus status = new VoicemailStatus(context, statusCursor);
    PhoneAccountHandle phoneAccountHandle =
        new PhoneAccountHandle(
            ComponentName.unflattenFromString(status.phoneAccountComponentName),
            status.phoneAccountId);

    TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);

    boolean visualVoicemailEnabled =
        TelephonyManagerCompat.isVisualVoicemailEnabled(telephonyManager, phoneAccountHandle);
    LogUtil.i(
        "VoicemailStatusCorruptionHandler.maybeFixVoicemailStatus",
        "Source="
            + source
            + ", CONFIGURATION_STAIE="
            + status.configurationState
            + ", visualVoicemailEnabled="
            + visualVoicemailEnabled);

    // If visual voicemail is enabled, the CONFIGURATION_STATE should be either OK, PIN_NOT_SET,
    // or other failure code. CONFIGURATION_STATE_NOT_CONFIGURED means that the client has been
    // shut down improperly (b/32371710). The client should be reset or the VVM tab will be
    // missing.
    if (Status.CONFIGURATION_STATE_NOT_CONFIGURED == status.configurationState
        && visualVoicemailEnabled) {
      LogUtil.e(
          "VoicemailStatusCorruptionHandler.maybeFixVoicemailStatus",
          "VVM3 voicemail status corrupted");

      switch (source) {
        case Activity:
          Logger.get(context)
              .logImpression(
                  DialerImpression.Type
                      .VOICEMAIL_CONFIGURATION_STATE_CORRUPTION_DETECTED_FROM_ACTIVITY);
          break;
        case Notification:
          Logger.get(context)
              .logImpression(
                  DialerImpression.Type
                      .VOICEMAIL_CONFIGURATION_STATE_CORRUPTION_DETECTED_FROM_NOTIFICATION);
          break;
        default:
          Assert.fail("this should never happen");
          break;
      }
      // At this point we could attempt to work around the issue by disabling and re-enabling
      // voicemail. Unfortunately this work around is buggy so we'll do nothing for now.
    }
  }
}
