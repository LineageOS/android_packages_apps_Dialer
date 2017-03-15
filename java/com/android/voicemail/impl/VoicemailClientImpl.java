/**
 * Copyright (C) 2017 The Android Open Source Project
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.voicemail.impl;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.provider.VoicemailContract.Voicemails;
import android.support.annotation.Nullable;
import android.support.v4.os.BuildCompat;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import com.android.dialer.common.Assert;
import com.android.voicemail.VoicemailClient;
import com.android.voicemail.impl.settings.VisualVoicemailSettingsUtil;
import com.android.voicemail.impl.settings.VoicemailSettingsFragment;
import java.util.List;
import javax.inject.Inject;

/**
 * {@link VoicemailClient} to be used when the voicemail module is activated. May only be used above
 * O.
 */
public class VoicemailClientImpl implements VoicemailClient {

  /**
   * List of legacy OMTP voicemail packages that should be ignored. It could never be the active VVM
   * package anymore. For example, voicemails in OC will no longer be handled by telephony, but
   * legacy voicemails might still exist in the database due to upgrading from NYC. Dialer will
   * fetch these voicemails again so it should be ignored.
   */
  private static final String[] OMTP_VOICEMAIL_BLACKLIST = {"com.android.phone"};

  @Inject
  public VoicemailClientImpl() {
    Assert.checkArgument(BuildCompat.isAtLeastO());
  }

  @Nullable
  @Override
  public String getSettingsFragment() {
    return VoicemailSettingsFragment.class.getName();
  }

  @Override
  public boolean isVoicemailArchiveEnabled(Context context, PhoneAccountHandle phoneAccountHandle) {
    return VisualVoicemailSettingsUtil.isArchiveEnabled(context, phoneAccountHandle);
  }

  @Override
  public void setVoicemailArchiveEnabled(
      Context context, PhoneAccountHandle phoneAccountHandle, boolean value) {
    VisualVoicemailSettingsUtil.setArchiveEnabled(context, phoneAccountHandle, value);
  }

  @TargetApi(VERSION_CODES.O)
  @Override
  public void appendOmtpVoicemailSelectionClause(
      Context context, StringBuilder where, List<String> selectionArgs) {
    TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
    String omtpSource = TelephonyMangerCompat.getVisualVoicemailPackageName(telephonyManager);
    where.append(
        "AND ("
            + "("
            + Voicemails.IS_OMTP_VOICEMAIL
            + " != 1)"
            + "OR "
            + "("
            + Voicemails.SOURCE_PACKAGE
            + " = ? )"
            + ")");
    selectionArgs.add(omtpSource);

    for (String blacklistedPackage : OMTP_VOICEMAIL_BLACKLIST) {
      where.append("AND (" + Voicemails.SOURCE_PACKAGE + "!= ?)");
      selectionArgs.add(blacklistedPackage);
    }
  }
}
