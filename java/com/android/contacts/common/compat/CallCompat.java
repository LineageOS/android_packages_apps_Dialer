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
 * limitations under the License.
 */

package com.android.contacts.common.compat;

import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.telecom.Call;

/** Compatibility utilities for android.telecom.Call */
public class CallCompat {

  public static boolean canPullExternalCall(@NonNull android.telecom.Call call) {
    return VERSION.SDK_INT >= VERSION_CODES.N_MR1
        && ((call.getDetails().getCallCapabilities() & Details.CAPABILITY_CAN_PULL_CALL)
            == Details.CAPABILITY_CAN_PULL_CALL);
  }

  /** android.telecom.Call.Details */
  public static class Details {

    public static final int PROPERTY_IS_EXTERNAL_CALL = Call.Details.PROPERTY_IS_EXTERNAL_CALL;
    public static final int PROPERTY_ENTERPRISE_CALL = Call.Details.PROPERTY_ENTERPRISE_CALL;
    public static final int CAPABILITY_CAN_PULL_CALL = Call.Details.CAPABILITY_CAN_PULL_CALL;
    public static final int CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO =
        Call.Details.CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO;

    public static final String EXTRA_ANSWERING_DROPS_FOREGROUND_CALL =
        "android.telecom.extra.ANSWERING_DROPS_FG_CALL";
  }
}
