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

package com.android.dialer.promotion;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.DrawableRes;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.spannable.ContentWithLearnMoreSpanner;
import com.android.dialer.storage.StorageComponent;

/** RTT promotion. */
public final class RttPromotion implements Promotion {
  private static final String SHARED_PREFERENCE_KEY_ENABLED = "rtt_promotion_enabled";
  private static final String SHARED_PREFERENCE_KEY_DISMISSED = "rtt_promotion_dismissed";
  private final Context appContext;

  public RttPromotion(Context context) {
    appContext = context.getApplicationContext();
  }

  @Override
  public boolean shouldShow() {
    SharedPreferences sharedPreferences = StorageComponent.get(appContext).unencryptedSharedPrefs();
    return sharedPreferences.getBoolean(SHARED_PREFERENCE_KEY_ENABLED, false)
        && !sharedPreferences.getBoolean(SHARED_PREFERENCE_KEY_DISMISSED, false);
  }

  @Override
  public CharSequence getTitle() {
    return appContext.getString(R.string.rtt_promotion_title);
  }

  @Override
  public CharSequence getDetails() {
    return new ContentWithLearnMoreSpanner(appContext)
        .create(
            appContext.getString(R.string.rtt_promotion_details),
            ConfigProviderBindings.get(appContext)
                .getString(
                    "rtt_promo_learn_more_link_full_url",
                    "http://support.google.com/pixelphone/?p=dialer_rtt"));
  }

  @Override
  @DrawableRes
  public int getIconRes() {
    return R.drawable.quantum_ic_rtt_vd_theme_24;
  }

  @Override
  public void setShouldShow(boolean shouldShow) {
    LogUtil.i("RttPromotion.setShouldShow", "shouldShow: %b", shouldShow);
    StorageComponent.get(appContext)
        .unencryptedSharedPrefs()
        .edit()
        .putBoolean(SHARED_PREFERENCE_KEY_ENABLED, shouldShow)
        .apply();
  }

  @Override
  public void dismiss() {
    StorageComponent.get(appContext)
        .unencryptedSharedPrefs()
        .edit()
        .putBoolean(SHARED_PREFERENCE_KEY_DISMISSED, true)
        .apply();
  }
}
