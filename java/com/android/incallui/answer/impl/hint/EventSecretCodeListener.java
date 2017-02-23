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

package com.android.incallui.answer.impl.hint;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.widget.Toast;
import com.android.dialer.common.ConfigProviderBindings;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.nano.DialerImpression;

/**
 * Listen to the broadcast when the user dials "*#*#[number]#*#*" to toggle the event answer hint.
 */
public class EventSecretCodeListener extends BroadcastReceiver {

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final String CONFIG_EVENT_SECRET_CODE = "event_secret_code";

  public static final String EVENT_ENABLED_WITH_SECRET_CODE_KEY = "event_enabled_with_secret_code";

  @Override
  public void onReceive(Context context, Intent intent) {
    String host = intent.getData().getHost();
    String secretCode =
        ConfigProviderBindings.get(context).getString(CONFIG_EVENT_SECRET_CODE, null);
    if (secretCode == null) {
      return;
    }
    if (!TextUtils.equals(secretCode, host)) {
      return;
    }
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    boolean wasEnabled = preferences.getBoolean(EVENT_ENABLED_WITH_SECRET_CODE_KEY, false);
    if (wasEnabled) {
      preferences.edit().putBoolean(EVENT_ENABLED_WITH_SECRET_CODE_KEY, false).apply();
      Toast.makeText(context, R.string.event_deactivated, Toast.LENGTH_SHORT).show();
      Logger.get(context).logImpression(DialerImpression.Type.EVENT_ANSWER_HINT_DEACTIVATED);
      LogUtil.i("EventSecretCodeListener.onReceive", "EventAnswerHint disabled");
    } else {
      preferences.edit().putBoolean(EVENT_ENABLED_WITH_SECRET_CODE_KEY, true).apply();
      Toast.makeText(context, R.string.event_activated, Toast.LENGTH_SHORT).show();
      Logger.get(context).logImpression(DialerImpression.Type.EVENT_ANSWER_HINT_ACTIVATED);
      LogUtil.i("EventSecretCodeListener.onReceive", "EventAnswerHint enabled");
    }
  }
}
