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

package com.android.voicemail.stub;

import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import com.android.voicemail.VoicemailClient;
import java.util.List;
import javax.inject.Inject;

/**
 * A no-op version of the voicemail module for build targets that don't support the new OTMP client.
 */
public final class StubVoicemailClient implements VoicemailClient {
  @Inject
  public StubVoicemailClient() {}

  @Override
  public boolean isVoicemailModuleEnabled() {
    return false;
  }

  @Override
  public boolean isVoicemailEnabled(Context context, PhoneAccountHandle phoneAccountHandle) {
    return false;
  }

  @Override
  public void setVoicemailEnabled(
      Context context, PhoneAccountHandle phoneAccountHandle, boolean enabled) {}

  @Override
  public void appendOmtpVoicemailSelectionClause(
      Context context, StringBuilder where, List<String> selectionArgs) {}

  @Override
  public void appendOmtpVoicemailStatusSelectionClause(
      Context context, StringBuilder where, List<String> selectionArgs) {}

  @Override
  public String getSettingsFragment() {
    return null;
  }

  @Override
  public boolean isVoicemailArchiveEnabled(Context context, PhoneAccountHandle phoneAccountHandle) {
    return false;
  }

  @Override
  public boolean isVoicemailArchiveAvailable(Context context) {
    return false;
  }

  @Override
  public void setVoicemailArchiveEnabled(
      Context context, PhoneAccountHandle phoneAccountHandle, boolean value) {}

  @Override
  public Intent getSetPinIntent(Context context, PhoneAccountHandle phoneAccountHandle) {
    return new Intent(TelephonyManager.ACTION_CONFIGURE_VOICEMAIL);
  }

  @Override
  public boolean isActivated(Context context, PhoneAccountHandle phoneAccountHandle) {
    return false;
  }

  @Override
  public void showConfigUi(@NonNull Context context) {}

  @Override
  public PersistableBundle getConfig(
      @NonNull Context context, @Nullable PhoneAccountHandle phoneAccountHandle) {
    return new PersistableBundle();
  }

  @Override
  public void onBoot(@NonNull Context context) {}

  @Override
  public void onShutdown(@NonNull Context context) {}
}
