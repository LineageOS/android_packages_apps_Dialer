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
package com.android.voicemail.impl;

import android.content.Context;
import android.preference.PreferenceManager;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.common.PerAccountSharedPreferences;

/**
 * Save visual voicemail values in shared preferences to be retrieved later. Because a voicemail
 * source is tied 1:1 to a phone account, the phone account handle is used in the key for each
 * voicemail source and the associated data.
 */
public class VisualVoicemailPreferences extends PerAccountSharedPreferences {

  public VisualVoicemailPreferences(Context context, PhoneAccountHandle phoneAccountHandle) {
    super(
        context,
        phoneAccountHandle,
        PreferenceManager.getDefaultSharedPreferences(context),
        "visual_voicemail_");
  }
}
