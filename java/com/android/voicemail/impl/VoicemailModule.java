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
import android.support.v4.os.BuildCompat;
import com.android.dialer.inject.ApplicationContext;
import com.android.voicemail.VoicemailClient;
import com.android.voicemail.VoicemailPermissionHelper;
import com.android.voicemail.stub.StubVoicemailClient;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

/** This module provides an instance of the voicemail client. */
@Module
public final class VoicemailModule {

  @Provides
  @Singleton
  static VoicemailClient provideVoicemailClient(@ApplicationContext Context context) {
    if (!BuildCompat.isAtLeastO()) {
      VvmLog.i("VoicemailModule.provideVoicemailClient", "SDK below O");
      return new StubVoicemailClient();
    }

    if (!VoicemailPermissionHelper.hasPermissions(context)) {
      VvmLog.i(
          "VoicemailModule.provideVoicemailClient",
          "missing permissions " + VoicemailPermissionHelper.getMissingPermissions(context));
      return new StubVoicemailClient();
    }

    VvmLog.i("VoicemailModule.provideVoicemailClient", "providing VoicemailClientImpl");
    return new VoicemailClientImpl();
  }

  private VoicemailModule() {}
}
