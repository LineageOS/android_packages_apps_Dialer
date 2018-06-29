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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.CallLog.Calls;
import android.provider.VoicemailContract.Voicemails;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.voicemail.VoicemailComponent;
import com.android.voicemail.VoicemailVersionConstants;

/**
 * Receives MY_PACKAGE_REPLACED to trigger VVM activation and to check for legacy voicemail users.
 */
public class PackageReplacedReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    VvmLog.i("PackageReplacedReceiver.onReceive", "package replaced, starting activation");

    if (!VoicemailComponent.get(context).getVoicemailClient().isVoicemailModuleEnabled()) {
      VvmLog.e("PackageReplacedReceiver.onReceive", "module disabled");
      return;
    }

    for (PhoneAccountHandle phoneAccountHandle :
        context.getSystemService(TelecomManager.class).getCallCapablePhoneAccounts()) {
      ActivationTask.start(context, phoneAccountHandle, null);
    }

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    if (!prefs.contains(VoicemailVersionConstants.PREF_DIALER_FEATURE_VERSION_ACKNOWLEDGED_KEY)) {
      setVoicemailFeatureVersionAsync(context);
    }
  }

  private void setVoicemailFeatureVersionAsync(Context context) {
    LogUtil.enterBlock("PackageReplacedReceiver.setVoicemailFeatureVersionAsync");

    // Check if user is already using voicemail (ie do they have any voicemails), and set the
    // acknowledged feature value accordingly.
    PendingResult pendingResult = goAsync();
    DialerExecutorComponent.get(context)
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(new ExistingVoicemailCheck(context))
        .onSuccess(
            output -> {
              LogUtil.i("PackageReplacedReceiver.setVoicemailFeatureVersionAsync", "success");
              pendingResult.finish();
            })
        .onFailure(
            throwable -> {
              LogUtil.i("PackageReplacedReceiver.setVoicemailFeatureVersionAsync", "failure");
              pendingResult.finish();
            })
        .build()
        .executeParallel(null);
  }

  private static class ExistingVoicemailCheck implements Worker<Void, Void> {
    private static final String[] PROJECTION = new String[] {Voicemails._ID};

    private final Context context;

    ExistingVoicemailCheck(Context context) {
      this.context = context;
    }

    @Override
    public Void doInBackground(Void arg) throws Throwable {
      LogUtil.i("PackageReplacedReceiver.ExistingVoicemailCheck.doInBackground", "");

      // Check the database for existing voicemails.
      boolean hasVoicemails = false;
      Uri uri = Voicemails.buildSourceUri(context.getPackageName());
      String whereClause = Calls.TYPE + " = " + Calls.VOICEMAIL_TYPE;
      try (Cursor cursor =
          context.getContentResolver().query(uri, PROJECTION, whereClause, null, null)) {
        if (cursor == null) {
          LogUtil.e(
              "PackageReplacedReceiver.ExistingVoicemailCheck.doInBackground",
              "failed to check for existing voicemails");
        } else if (cursor.moveToFirst()) {
          hasVoicemails = true;
        }
      }

      LogUtil.i(
          "PackageReplacedReceiver.ExistingVoicemailCheck.doInBackground",
          "has voicemails: " + hasVoicemails);
      int version = hasVoicemails ? VoicemailVersionConstants.LEGACY_VOICEMAIL_FEATURE_VERSION : 0;
      PreferenceManager.getDefaultSharedPreferences(context)
          .edit()
          .putInt(VoicemailVersionConstants.PREF_DIALER_FEATURE_VERSION_ACKNOWLEDGED_KEY, version)
          .apply();
      return null;
    }
  }
}
