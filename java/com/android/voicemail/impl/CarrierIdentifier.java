/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import com.google.auto.value.AutoValue;
import java.util.Optional;

/** Identifies a carrier. */
@AutoValue
@TargetApi(VERSION_CODES.O)
@SuppressWarnings({"missingpermission"})
public abstract class CarrierIdentifier {

  public abstract String mccMnc();

  /**
   * Group ID Level 1. Used to identify MVNO(Mobile Virtual Network Operators) who subleases other
   * carrier's network and share their mccMnc. MVNO should have a GID1 different from the host.
   */
  public abstract String gid1();

  /** Builder for the matcher */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setMccMnc(String mccMnc);

    public abstract Builder setGid1(String gid1);

    public abstract CarrierIdentifier build();
  }

  public static Builder builder() {
    return new AutoValue_CarrierIdentifier.Builder().setGid1("");
  }

  /** Create a identifier for a {@link PhoneAccountHandle}. Absent if the handle is not valid. */
  public static Optional<CarrierIdentifier> forHandle(
      Context context, @Nullable PhoneAccountHandle phoneAccountHandle) {
    if (phoneAccountHandle == null) {
      return Optional.empty();
    }
    TelephonyManager telephonyManager =
        context
            .getSystemService(TelephonyManager.class)
            .createForPhoneAccountHandle(phoneAccountHandle);
    if (telephonyManager == null) {
      return Optional.empty();
    }
    String gid1 = telephonyManager.getGroupIdLevel1();
    if (gid1 == null) {
      gid1 = "";
    }

    return Optional.of(
        builder().setMccMnc(telephonyManager.getSimOperator()).setGid1(gid1).build());
  }
}
