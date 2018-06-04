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
import android.support.annotation.IntDef;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.widget.Toast;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProviderComponent;
import com.android.dialer.logging.DialerImpression.Type;
import com.android.dialer.logging.Logger;
import com.android.dialer.storage.StorageComponent;
import java.util.Random;

/**
 * Listen to the broadcast when the user dials "*#*#[number]#*#*" to toggle the event answer hint.
 */
public class PawSecretCodeListener extends BroadcastReceiver {

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final String CONFIG_PAW_SECRET_CODE = "paw_secret_code";

  public static final String PAW_ENABLED_WITH_SECRET_CODE_KEY = "paw_enabled_with_secret_code";

  /** Which paw to show, must be {@link PawType} */
  public static final String PAW_TYPE = "paw_type";

  /** Resource id is not stable across app versions. Use {@link #PAW_TYPE} instead. */
  @Deprecated public static final String PAW_DRAWABLE_ID_KEY = "paw_drawable_id";

  /** Enum for all paws. */
  @IntDef({PAW_TYPE_INVALID, PAW_TYPE_CAT, PAW_TYPE_DOG})
  @interface PawType {}

  public static final int PAW_TYPE_INVALID = 0;
  public static final int PAW_TYPE_CAT = 1;
  public static final int PAW_TYPE_DOG = 2;

  @Override
  public void onReceive(Context context, Intent intent) {
    String host = intent.getData().getHost();
    if (TextUtils.isEmpty(host)) {
      return;
    }
    String secretCode =
        ConfigProviderComponent.get(context)
            .getConfigProvider()
            .getString(CONFIG_PAW_SECRET_CODE, "729");
    if (secretCode == null) {
      return;
    }
    if (!TextUtils.equals(secretCode, host)) {
      return;
    }
    SharedPreferences preferences = StorageComponent.get(context).unencryptedSharedPrefs();
    boolean wasEnabled = preferences.getBoolean(PAW_ENABLED_WITH_SECRET_CODE_KEY, false);
    if (wasEnabled) {
      preferences.edit().putBoolean(PAW_ENABLED_WITH_SECRET_CODE_KEY, false).apply();
      Toast.makeText(context, R.string.event_deactivated, Toast.LENGTH_SHORT).show();
      Logger.get(context).logImpression(Type.EVENT_ANSWER_HINT_DEACTIVATED);
      LogUtil.i("PawSecretCodeListener.onReceive", "PawAnswerHint disabled");
    } else {
      selectPawType(preferences);
      Toast.makeText(context, R.string.event_activated, Toast.LENGTH_SHORT).show();
      Logger.get(context).logImpression(Type.EVENT_ANSWER_HINT_ACTIVATED);
      LogUtil.i("PawSecretCodeListener.onReceive", "PawAnswerHint enabled");
    }
  }

  public static void selectPawType(SharedPreferences preferences) {
    @PawType int pawType;
    if (new Random().nextBoolean()) {
      pawType = PAW_TYPE_CAT;
    } else {
      pawType = PAW_TYPE_DOG;
    }
    preferences
        .edit()
        .putBoolean(PAW_ENABLED_WITH_SECRET_CODE_KEY, true)
        .putInt(PAW_TYPE, pawType)
        .apply();
  }
}
