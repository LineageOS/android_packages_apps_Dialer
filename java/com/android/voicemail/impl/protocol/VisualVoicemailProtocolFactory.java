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

package com.android.voicemail.impl.protocol;

import android.content.res.Resources;
import android.support.annotation.Nullable;
import android.telephony.TelephonyManager;
import com.android.voicemail.VisualVoicemailTypeExtensions;
import com.android.voicemail.impl.VvmLog;

public class VisualVoicemailProtocolFactory {

  private static final String TAG = "VvmProtocolFactory";

  @Nullable
  public static VisualVoicemailProtocol create(Resources resources, String type) {
    if (type == null) {
      return null;
    }
    switch (type) {
      case TelephonyManager.VVM_TYPE_OMTP:
        return new OmtpProtocol();
      case TelephonyManager.VVM_TYPE_CVVM:
        return new CvvmProtocol();
      case VisualVoicemailTypeExtensions.VVM_TYPE_VVM3:
        return new Vvm3Protocol();
      default:
        VvmLog.e(TAG, "Unexpected visual voicemail type: " + type);
    }
    return null;
  }
}
