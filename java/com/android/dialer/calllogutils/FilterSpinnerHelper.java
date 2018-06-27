/*
 * Copyright (C) 2014 The CyanogenMod Project
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
package com.android.dialer.calllogutils;

import android.content.Context;
import android.provider.CallLog;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.android.dialer.R;
import com.android.dialer.util.PermissionsUtil;
import java.util.ArrayList;
import java.util.List;

public class FilterSpinnerHelper implements AdapterView.OnItemSelectedListener {
  private static String TAG = FilterSpinnerHelper.class.getSimpleName();

  public interface OnFilterChangedListener {
    void onFilterChanged(PhoneAccountHandle account, int callType);
  }

  private OnFilterChangedListener mListener;
  private Spinner mAccountSpinner;
  private ArrayAdapter<AccountItem> mAccountAdapter;
  private Spinner mTypeSpinner;
  private ArrayAdapter<TypeItem> mTypeAdapter;

  public FilterSpinnerHelper(View rootView, boolean includeVoicemailType,
      OnFilterChangedListener listener) {
    mListener = listener;

    mAccountAdapter = createAccountAdapter(rootView.getContext());
    mAccountSpinner = initSpinner(rootView, R.id.filter_account_spinner, mAccountAdapter);

    mTypeAdapter = createTypeAdapter(rootView.getContext(), includeVoicemailType);
    mTypeSpinner = initSpinner(rootView, R.id.filter_status_spinner, mTypeAdapter);
  }

  @Override
  public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
    int selectedAccountPos = Math.max(mAccountSpinner.getSelectedItemPosition(), 0);
    int selectedTypePos = Math.max(mTypeSpinner.getSelectedItemPosition(), 0);
    PhoneAccountHandle selectedAccount = mAccountAdapter.getItem(selectedAccountPos).account;
    int selectedType = mTypeAdapter.getItem(selectedTypePos).value;
    mListener.onFilterChanged(selectedAccount, selectedType);
  }

  @Override
  public void onNothingSelected(AdapterView<?> parent) {
  }

  private Spinner initSpinner(View rootView, int spinnerResId, ArrayAdapter<?> adapter) {
    Spinner spinner = rootView.findViewById(spinnerResId);
    if (spinner == null) {
      throw new IllegalArgumentException("Could not find spinner "
          + rootView.getContext().getResources().getResourceName(spinnerResId));
    }
    spinner.setAdapter(adapter);
    spinner.setOnItemSelectedListener(this);
    if (adapter.getCount() <= 1) {
      spinner.setVisibility(View.GONE);
    }
    return spinner;
  }

  private ArrayAdapter<AccountItem> createAccountAdapter(Context context) {
    ArrayList<AccountItem> items = new ArrayList<>();
    items.add(new AccountItem(null, context.getString(R.string.call_log_show_all_accounts)));
    if (PermissionsUtil.hasPermission(context, android.Manifest.permission.READ_PHONE_STATE)) {
      TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
      for (PhoneAccountHandle account : tm.getCallCapablePhoneAccounts()) {
        String displayName = PhoneAccountUtils.getAccountLabel(context, account);
        if (!TextUtils.isEmpty(displayName)) {
          items.add(new AccountItem(account, displayName));
        }
      }
    }

    return new ArrayAdapter<AccountItem>(context, R.layout.call_log_filter_spinner_item, items);
  }

  private ArrayAdapter<TypeItem> createTypeAdapter(Context context, boolean includeVoicemail) {
    ArrayList<TypeItem> items = new ArrayList<>();
    items.add(new TypeItem(-1, context.getString(R.string.call_log_all_calls_header)));
    items.add(new TypeItem(CallLog.Calls.INCOMING_TYPE,
        context.getString(R.string.call_log_incoming_header)));
    items.add(new TypeItem(CallLog.Calls.OUTGOING_TYPE,
        context.getString(R.string.call_log_outgoing_header)));
    items.add(new TypeItem(CallLog.Calls.MISSED_TYPE,
        context.getString(R.string.call_log_missed_header)));
    items.add(new TypeItem(CallLog.Calls.BLOCKED_TYPE,
        context.getString(R.string.call_log_blacklist_header)));
    if (includeVoicemail) {
      items.add(new TypeItem(CallLog.Calls.VOICEMAIL_TYPE,
          context.getString(R.string.call_log_voicemail_header)));
    }

    return new ArrayAdapter<TypeItem>(context, R.layout.call_log_filter_spinner_item, items);
  }

  private final class AccountItem {
    public final PhoneAccountHandle account;
    public final String label;

    private AccountItem(PhoneAccountHandle account, String label) {
      this.account = account;
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private final class TypeItem {
    public final int value;
    public final String label;

    private TypeItem(int value, String label) {
      this.value = value;
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }
}
