/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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

package com.android.dialer.postcall;

import android.Manifest.permission;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.SmsManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.dialer.R;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.widget.DialerToolbar;
import com.android.dialer.widget.MessageFragment;

/** Activity used to send post call messages after a phone call. */
public class PostCallActivity extends AppCompatActivity implements MessageFragment.Listener {

  public static final String KEY_PHONE_NUMBER = "phone_number";
  public static final String KEY_MESSAGE = "message";
  public static final String KEY_RCS_POST_CALL = "rcs_post_call";

  private final ActivityResultLauncher<String> smsPermissionLauncher =
          registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                  grantResult -> {
            PermissionsUtil.permissionRequested(this, permission.SEND_SMS);
            onMessageFragmentSendMessage(getIntent().getStringExtra(KEY_MESSAGE));
          });

  private boolean useRcs;

  public static Intent newIntent(
          @NonNull Context context, @NonNull String number, boolean isRcsPostCall) {
    Intent intent = new Intent(Assert.isNotNull(context), PostCallActivity.class);
    intent.putExtra(KEY_PHONE_NUMBER, Assert.isNotNull(number));
    intent.putExtra(KEY_RCS_POST_CALL, isRcsPostCall);
    return intent;
  }

  @Override
  protected void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.post_call_activity);

    ((DialerToolbar) findViewById(R.id.toolbar)).setTitle(R.string.post_call_message);
    useRcs = getIntent().getBooleanExtra(KEY_RCS_POST_CALL, false);
    LogUtil.i("PostCallActivity.onCreate", "useRcs: %b", useRcs);

    int postCallCharLimit =
        useRcs
            ? getResources().getInteger(R.integer.post_call_char_limit)
            : MessageFragment.NO_CHAR_LIMIT;
    String[] messages =
        new String[] {
          getString(R.string.post_call_message_1),
          getString(R.string.post_call_message_2),
          getString(R.string.post_call_message_3)
        };
    MessageFragment fragment =
        MessageFragment.builder()
            .setCharLimit(postCallCharLimit)
            .showSendIcon()
            .setMessages(messages)
            .build();
    getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.message_container, fragment)
        .commit();
  }

  @Override
  public void onMessageFragmentSendMessage(@NonNull String message) {
    String number = Assert.isNotNull(getIntent().getStringExtra(KEY_PHONE_NUMBER));
    getIntent().putExtra(KEY_MESSAGE, message);

    if (useRcs) {
      LogUtil.i("PostCallActivity.onMessageFragmentSendMessage", "sending post call Rcs.");
      PostCall.onMessageSent(this, number);
      finish();
    } else if (PermissionsUtil.hasPermission(this, permission.SEND_SMS)) {
      LogUtil.i("PostCallActivity.sendMessage", "Sending post call SMS.");
      SmsManager smsManager = getSystemService(SmsManager.class);
      smsManager.sendMultipartTextMessage(
          number, null, smsManager.divideMessage(message), null, null);
      PostCall.onMessageSent(this, number);
      finish();
    } else if (PermissionsUtil.isFirstRequest(this, permission.SEND_SMS)
        || shouldShowRequestPermissionRationale(permission.SEND_SMS)) {
      LogUtil.i("PostCallActivity.sendMessage", "Requesting SMS_SEND permission.");
      smsPermissionLauncher.launch(permission.SEND_SMS);
    } else {
      LogUtil.i(
          "PostCallActivity.sendMessage", "Permission permanently denied, sending to settings.");
      Intent intent = new Intent(Intent.ACTION_VIEW);
      intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.setData(Uri.parse("package:" + this.getPackageName()));
      startActivity(intent);
    }
  }
}
