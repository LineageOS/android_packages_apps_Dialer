/**
 * Copyright (C) 2014 The Android Open Source Project
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.voicemail.impl;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.text.TextUtils;

/**
 * Helper for manipulating intents or components with subscription-related information.
 *
 * <p>In settings, subscription ids and labels are passed along to indicate that settings are being
 * changed for particular subscriptions. This helper provides functions for helping extract this
 * info and perform common operations using this info.
 */
public class SubscriptionInfoHelper {
  public static final int NO_SUB_ID = -1;

  // Extra on intent containing the id of a subscription.
  public static final String SUB_ID_EXTRA =
      "com.android.voicemailomtp.settings.SubscriptionInfoHelper.SubscriptionId";
  // Extra on intent containing the label of a subscription.
  private static final String SUB_LABEL_EXTRA =
      "com.android.voicemailomtp.settings.SubscriptionInfoHelper.SubscriptionLabel";

  private static Context mContext;

  private static int mSubId = NO_SUB_ID;
  private static String mSubLabel;

  /** Instantiates the helper, by extracting the subscription id and label from the intent. */
  public SubscriptionInfoHelper(Context context, Intent intent) {
    mContext = context;
    mSubId = intent.getIntExtra(SUB_ID_EXTRA, NO_SUB_ID);
    mSubLabel = intent.getStringExtra(SUB_LABEL_EXTRA);
  }

  /**
   * Sets the action bar title to the string specified by the given resource id, formatting it with
   * the subscription label. This assumes the resource string is formattable with a string-type
   * specifier.
   *
   * <p>If the subscription label does not exists, leave the existing title.
   */
  public void setActionBarTitle(ActionBar actionBar, Resources res, int resId) {
    if (actionBar == null || TextUtils.isEmpty(mSubLabel)) {
      return;
    }

    String title = String.format(res.getString(resId), mSubLabel);
    actionBar.setTitle(title);
  }

  public int getSubId() {
    return mSubId;
  }
}
