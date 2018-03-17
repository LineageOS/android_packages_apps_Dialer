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
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.common.Assert;
import com.android.voicemail.PinChanger;
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
  public boolean isVoicemailTranscriptionAvailable(
      Context context, PhoneAccountHandle phoneAccountHandle) {
    return false;
  }

  @Override
  public boolean isVoicemailTranscriptionEnabled(Context context, PhoneAccountHandle account) {
    return false;
  }

  @Override
  public boolean isVoicemailDonationAvailable(Context context, PhoneAccountHandle account) {
    return false;
  }

  @Override
  public boolean isVoicemailDonationEnabled(Context context, PhoneAccountHandle account) {
    return false;
  }

  @Override
  public void setVoicemailTranscriptionEnabled(
      Context context, PhoneAccountHandle phoneAccountHandle, boolean enabled) {}

  @Override
  public void setVoicemailDonationEnabled(
      Context context, PhoneAccountHandle phoneAccountHandle, boolean enabled) {}

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

  @Override
  public void addActivationStateListener(ActivationStateListener listener) {
    // Do nothing
  }

  @Override
  public void removeActivationStateListener(ActivationStateListener listener) {
    // Do nothing
  }

  @Override
  public boolean hasCarrierSupport(Context context, PhoneAccountHandle phoneAccountHandle) {
    return false;
  }

  @Override
  public PinChanger createPinChanger(Context context, PhoneAccountHandle phoneAccountHandle) {
    throw Assert.createAssertionFailException("should never be called on stub.");
  }

  @Override
  public void onTosAccepted(Context context, PhoneAccountHandle account) {}

  @Override
  public boolean hasAcceptedTos(Context context, PhoneAccountHandle phoneAccountHandle) {
    return false;
  }

  @Override
  @Nullable
  public String getCarrierConfigString(Context context, PhoneAccountHandle account, String key) {
    return null;
  }
}
