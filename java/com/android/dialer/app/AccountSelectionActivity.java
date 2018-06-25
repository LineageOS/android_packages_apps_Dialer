/*
 * Copyright (C) 2018 The LineageOS Project
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

package com.android.dialer.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;

import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.util.CallUtil;

import java.util.ArrayList;
import java.util.List;

public class AccountSelectionActivity extends AppCompatActivity {
  public static Intent createIntent(Context context, String number,
          CallInitiationType.Type initiationType) {
    if (TextUtils.isEmpty(number)) {
      return null;
    }

    List<PhoneAccount> accounts =
        CallUtil.getCallCapablePhoneAccounts(context, PhoneAccount.SCHEME_TEL);
    if (accounts == null || accounts.size() <= 1) {
      return null;
    }
    ArrayList<PhoneAccountHandle> accountHandles = new ArrayList<>();
    for (PhoneAccount account : accounts) {
      accountHandles.add(account.getAccountHandle());
    }

    return new Intent(context, AccountSelectionActivity.class)
        .putExtra("number", number)
        .putExtra("accountHandles", accountHandles)
        .putExtra("type", initiationType.ordinal());
  }

  private String mNumber;
  private CallInitiationType.Type mInitiationType;

  private SelectPhoneAccountDialogFragment.SelectPhoneAccountListener mListener =
      new SelectPhoneAccountDialogFragment.SelectPhoneAccountListener() {
    @Override
    public void onPhoneAccountSelected(PhoneAccountHandle selectedAccountHandle,
        boolean setDefault, String callId) {
      Intent intent = new CallIntentBuilder(mNumber, mInitiationType)
          .setPhoneAccountHandle(selectedAccountHandle)
          .build();
      startActivity(intent);
      finish();
    }

    @Override
    public void onDialogDismissed(String callId) {
      finish();
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    mNumber = getIntent().getStringExtra("number");
    mInitiationType = CallInitiationType.Type.values()[getIntent().getIntExtra("type", 0)];

    if (getFragmentManager().findFragmentByTag("dialog") == null) {
      List<PhoneAccountHandle> handles = getIntent().getParcelableArrayListExtra("accountHandles");
      SelectPhoneAccountDialogFragment dialog = SelectPhoneAccountDialogFragment.newInstance(
        R.string.call_via_dialog_title, false, handles, mListener, null);

      dialog.show(getFragmentManager(), "dialog");
    }
  }
}
