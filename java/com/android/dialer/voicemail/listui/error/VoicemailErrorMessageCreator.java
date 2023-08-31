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

package com.android.dialer.voicemail.listui.error;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.voicemail.VisualVoicemailTypeExtensions;

/**
 * Given a VoicemailStatus, {@link VoicemailErrorMessageCreator#create(Context, VoicemailStatus)}
 * will return a {@link VoicemailErrorMessage} representing the message to be shown to the user, or
 * <code>null</code> if no message should be shown.
 */
public class VoicemailErrorMessageCreator {

  @Nullable
  public VoicemailErrorMessage create(
      Context context, VoicemailStatus status, VoicemailStatusReader statusReader) {
    switch (status.type) {
      case VisualVoicemailTypeExtensions.VVM_TYPE_VVM3:
        return Vvm3VoicemailMessageCreator.create(context, status, statusReader);
      default:
        return OmtpVoicemailMessageCreator.create(context, status, statusReader);
    }
  }

  public boolean isSyncBlockingError(VoicemailStatus status) {
    switch (status.type) {
      case VisualVoicemailTypeExtensions.VVM_TYPE_VVM3:
        return Vvm3VoicemailMessageCreator.isSyncBlockingError(status);
      default:
        return OmtpVoicemailMessageCreator.isSyncBlockingError(status);
    }
  }
}
