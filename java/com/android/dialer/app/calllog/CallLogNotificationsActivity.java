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
 * limitations under the License.
 */

package com.android.dialer.app.calllog;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import com.android.dialer.common.LogUtil;
import com.android.dialer.util.PermissionsUtil;

/**
 * Provides operations for managing call-related notifications. This is used to forward intent
 * that's requiring to unlock screen and it will never be visible to user.
 *
 * <p>It handles the following actions:
 *
 * <ul>
 *   <li>Sending an SMS from a missed call
 * </ul>
 */
public class CallLogNotificationsActivity extends AppCompatActivity {

  public static final String ACTION_SEND_SMS_FROM_MISSED_CALL_NOTIFICATION =
      "com.android.dialer.calllog.SEND_SMS_FROM_MISSED_CALL_NOTIFICATION";

  /**
   * Extra to be included with {@link #ACTION_SEND_SMS_FROM_MISSED_CALL_NOTIFICATION} to identify
   * the number to text back.
   *
   * <p>It must be a {@link String}.
   */
  public static final String EXTRA_MISSED_CALL_NUMBER = "MISSED_CALL_NUMBER";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Intent intent = getIntent();

    if (!PermissionsUtil.hasPermission(this, android.Manifest.permission.READ_CALL_LOG)) {
      return;
    }

    String action = intent.getAction();
    switch (action) {
      case ACTION_SEND_SMS_FROM_MISSED_CALL_NOTIFICATION:
        MissedCallNotifier.getIstance(this)
            .sendSmsFromMissedCall(
                intent.getStringExtra(EXTRA_MISSED_CALL_NUMBER), intent.getData());
        break;
      default:
        LogUtil.d("CallLogNotificationsActivity.onCreate", "could not handle: " + intent);
        break;
    }
    finish();
  }
}
