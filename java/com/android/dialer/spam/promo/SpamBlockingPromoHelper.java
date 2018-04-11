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
 * limitations under the License.
 */

package com.android.dialer.spam.promo;

import android.app.FragmentManager;
import android.content.Context;
import android.preference.PreferenceManager;
import android.support.annotation.VisibleForTesting;
import android.support.design.widget.Snackbar;
import android.view.View;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.spam.SpamSettings;

/** Helper class for showing spam blocking on-boarding promotions. */
public class SpamBlockingPromoHelper {

  static final String SPAM_BLOCKING_PROMO_PERIOD_MILLIS = "spam_blocking_promo_period_millis";
  static final String SPAM_BLOCKING_PROMO_LAST_SHOW_MILLIS = "spam_blocking_promo_last_show_millis";
  static final String ENABLE_SPAM_BLOCKING_PROMO = "enable_spam_blocking_promo";

  private final Context context;
  private final SpamSettings spamSettings;

  public SpamBlockingPromoHelper(Context context, SpamSettings spamSettings) {
    this.context = context;
    this.spamSettings = spamSettings;
  }

  /** Shows a spam blocking promo dialog with on complete snackbar if all the prerequisites meet. */
  public void showSpamBlockingPromoDialog(View view, FragmentManager fragmentManager) {
    if (!shouldShowSpamBlockingPromo()) {
      return;
    }

    updateLastShowSpamTimestamp();
    Logger.get(context).logImpression(DialerImpression.Type.SPAM_BLOCKING_CALL_LOG_PROMO_SHOWN);
    SpamBlockingPromoDialogFragment.newInstance(
            () -> {
              Logger.get(context)
                  .logImpression(
                      DialerImpression.Type.SPAM_BLOCKING_ENABLED_THROUGH_CALL_LOG_PROMO);
              spamSettings.modifySpamBlockingSetting(
                  true, success -> showModifySettingOnCompleteSnackbar(view, success));
            })
        .show(fragmentManager, SpamBlockingPromoDialogFragment.SPAM_BLOCKING_PROMO_DIALOG_TAG);
  }

  /**
   * Returns true if we should show a spam blocking promo.
   *
   * <p>Should show spam blocking promo only when all of the following criteria meet 1. Spam
   * blocking promo is enabled by flag. 2. Spam blocking setting is available. 3. Spam blocking
   * setting is not yet enabled. 4. Time since last spam blocking promo exceeds the threshold.
   *
   * @return true if we should show a spam blocking promo.
   */
  @VisibleForTesting
  boolean shouldShowSpamBlockingPromo() {
    if (!ConfigProviderBindings.get(context).getBoolean(ENABLE_SPAM_BLOCKING_PROMO, false)
        || !spamSettings.isSpamEnabled()
        || !spamSettings.isSpamBlockingEnabledByFlag()
        || spamSettings.isSpamBlockingEnabledByUser()) {
      return false;
    }

    long lastShowMillis =
        PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
            .getLong(SPAM_BLOCKING_PROMO_LAST_SHOW_MILLIS, 0);
    long showPeriodMillis =
        ConfigProviderBindings.get(context)
            .getLong(SPAM_BLOCKING_PROMO_PERIOD_MILLIS, Long.MAX_VALUE);
    return lastShowMillis == 0 || System.currentTimeMillis() - lastShowMillis > showPeriodMillis;
  }

  private void updateLastShowSpamTimestamp() {
    PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
        .edit()
        .putLong(SPAM_BLOCKING_PROMO_LAST_SHOW_MILLIS, System.currentTimeMillis())
        .commit();
  }

  /**
   * Shows a modify setting on complete snackbar and a link to redirect to setting page
   *
   * @param view the view to attach on-complete notice snackbar
   * @param success whether the modify setting operation succceeds
   */
  private void showModifySettingOnCompleteSnackbar(View view, boolean success) {
    if (!success) {
      Logger.get(context)
          .logImpression(DialerImpression.Type.SPAM_BLOCKING_MODIFY_FAILURE_THROUGH_CALL_LOG_PROMO);
    }
    String snackBarText =
        success
            ? context.getString(R.string.spam_blocking_settings_enable_complete_text)
            : context.getString(R.string.spam_blocking_settings_enable_error_text);
    Snackbar.make(view, snackBarText, Snackbar.LENGTH_LONG)
        .setAction(
            R.string.spam_blocking_setting_prompt,
            v -> context.startActivity(spamSettings.getSpamBlockingSettingIntent(context)))
        .setActionTextColor(
            context.getResources().getColor(R.color.dialer_snackbar_action_text_color))
        .show();
  }
}
