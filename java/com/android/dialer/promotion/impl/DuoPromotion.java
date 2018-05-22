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

package com.android.dialer.promotion.impl;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.configprovider.ConfigProvider;
import com.android.dialer.duo.Duo;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.promotion.Promotion;
import com.android.dialer.spannable.ContentWithLearnMoreSpanner;
import com.android.dialer.storage.Unencrypted;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/** Duo promotion. */
final class DuoPromotion implements Promotion {
  private static final String SHARED_PREF_KEY_DUO_DISCLOSURE_DISMISSED = "duo_disclosure_dismissed";

  private static final String SHARED_PREF_KEY_DUO_DISCLOSURE_FIRST_VIEW_TIME_MILLIS =
      "duo_disclosure_first_viewed_time_ms";
  @VisibleForTesting static final String FLAG_SHOW_DUO_DISCLOSURE = "show_duo_disclosure";

  @VisibleForTesting
  static final String FLAG_DUO_DISCLOSURE_AUTO_DISMISS_AFTER_VIEWED_TIME_MILLIS =
      "show_duo_disclosure_auto_dismiss_after_viewed_time_millis";

  private final Context appContext;
  private final ConfigProvider configProvider;
  private final Duo duo;
  private final SharedPreferences sharedPreferences;

  @Inject
  DuoPromotion(
      @ApplicationContext Context context,
      ConfigProvider configProvider,
      Duo duo,
      @Unencrypted SharedPreferences sharedPreferences) {
    this.appContext = context;
    this.configProvider = configProvider;
    this.duo = duo;
    this.sharedPreferences = sharedPreferences;
  }

  @Override
  public int getType() {
    return PromotionType.CARD;
  }

  @Override
  public boolean isEligibleToBeShown() {
    if (!configProvider.getBoolean(FLAG_SHOW_DUO_DISCLOSURE, false)) {
      return false;
    }
    // Don't show the Duo disclosure card if
    // (1) Duo integration is not enabled on the device, or
    // (2) Duo is not activated.
    if (!duo.isEnabled(appContext) || !duo.isActivated(appContext)) {
      return false;
    }

    // Don't show the Duo disclosure card if it has been dismissed.
    if (sharedPreferences.getBoolean(SHARED_PREF_KEY_DUO_DISCLOSURE_DISMISSED, false)) {
      return false;
    }

    // At this point, Duo is activated and the disclosure card hasn't been dismissed.
    // We should show the card if it has never been viewed by the user.
    if (!sharedPreferences.contains(SHARED_PREF_KEY_DUO_DISCLOSURE_FIRST_VIEW_TIME_MILLIS)) {
      return true;
    }

    // At this point, the card has been viewed but not dismissed.
    // We should not show the card if it has been viewed for more than 1 day.
    long duoDisclosureFirstViewTimeMillis =
        sharedPreferences.getLong(SHARED_PREF_KEY_DUO_DISCLOSURE_FIRST_VIEW_TIME_MILLIS, 0);
    return System.currentTimeMillis() - duoDisclosureFirstViewTimeMillis
        <= configProvider.getLong(
            FLAG_DUO_DISCLOSURE_AUTO_DISMISS_AFTER_VIEWED_TIME_MILLIS, TimeUnit.DAYS.toMillis(1));
  }

  @Override
  public void onViewed() {
    if (!sharedPreferences.contains(SHARED_PREF_KEY_DUO_DISCLOSURE_FIRST_VIEW_TIME_MILLIS)) {
      sharedPreferences
          .edit()
          .putLong(
              SHARED_PREF_KEY_DUO_DISCLOSURE_FIRST_VIEW_TIME_MILLIS, System.currentTimeMillis())
          .apply();
    }
  }

  @Override
  public void dismiss() {
    sharedPreferences.edit().putBoolean(SHARED_PREF_KEY_DUO_DISCLOSURE_DISMISSED, true).apply();
  }

  @Override
  public CharSequence getTitle() {
    return appContext.getString(R.string.duo_disclosure_title);
  }

  @Override
  public CharSequence getDetails() {
    return new ContentWithLearnMoreSpanner(appContext)
        .create(
            appContext.getString(R.string.duo_disclosure_details),
            configProvider.getString(
                "duo_disclosure_link_full_url",
                "http://support.google.com/pixelphone/?p=dialer_duo"));
  }

  @Override
  public int getIconRes() {
    return duo.getLogo();
  }
}
