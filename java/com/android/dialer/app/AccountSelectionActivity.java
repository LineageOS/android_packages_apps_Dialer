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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

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

    return new Intent(context, AccountSelectionActivity.class)
        .putExtra("number", number)
        .putExtra("accounts", new ArrayList<PhoneAccount>(accounts))
        .putExtra("type", initiationType.ordinal());
  }

  private String mNumber;
  private List<PhoneAccount> mAccounts;
  private CallInitiationType.Type mInitiationType;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    mNumber = getIntent().getStringExtra("number");
    mAccounts = getIntent().getParcelableArrayListExtra("accounts");
    mInitiationType = CallInitiationType.Type.values()[getIntent().getIntExtra("type", 0)];

    if (getFragmentManager().findFragmentByTag("dialog") == null) {
      AccountDialogFragment.newInstance(mAccounts)
          .show(getFragmentManager(), "dialog");
    }
  }

  private void handleAccountSelected(PhoneAccount account) {
    Intent intent = new CallIntentBuilder(mNumber, mInitiationType)
        .setPhoneAccountHandle(account.getAccountHandle())
        .build();
    startActivity(intent);
    finish();
  }

  private void handleCancel() {
    finish();
  }

  public static class AccountDialogFragment extends DialogFragment
      implements DialogInterface.OnClickListener {
    static AccountDialogFragment newInstance(List<PhoneAccount> accounts) {
      Bundle args = new Bundle();
      args.putParcelableArrayList("accounts", new ArrayList<PhoneAccount>(accounts));

      AccountDialogFragment f = new AccountDialogFragment();
      f.setArguments(args);
      return f;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      List<PhoneAccount> accounts = getArguments().getParcelableArrayList("accounts");
      ListAdapter adapter = new SelectAccountListAdapter(getActivity(), accounts);

      return new AlertDialog.Builder(getActivity())
          .setTitle(R.string.call_via)
          .setAdapter(adapter, this)
          .create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
      List<PhoneAccount> accounts = getArguments().getParcelableArrayList("accounts");
      PhoneAccount account = accounts.get(which);
      ((AccountSelectionActivity) getActivity()).handleAccountSelected(account);
    }

    @Override
    public void onCancel(DialogInterface dialog) {
      ((AccountSelectionActivity) getActivity()).handleCancel();
    }
  }

  private static class SelectAccountListAdapter extends ArrayAdapter<PhoneAccount> {
    private LayoutInflater mInflater;

    public SelectAccountListAdapter(Context context, List<PhoneAccount> accounts) {
      super(context, 0, accounts);
      mInflater = LayoutInflater.from(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      final View rowView;
      final ViewHolder holder;

      if (convertView == null) {
        rowView = mInflater.inflate(R.layout.select_account_list_item, parent, false);
        holder = new ViewHolder();
        holder.title = (TextView) rowView.findViewById(R.id.title);
        holder.icon = (ImageView) rowView.findViewById(R.id.icon);
        rowView.setTag(holder);
      } else {
        rowView = convertView;
        holder = (ViewHolder) rowView.getTag();
      }

      final PhoneAccount account = getItem(position);
      holder.title.setText(account.getLabel());
      holder.icon.setImageIcon(account.getIcon());

      return rowView;
    }

    private class ViewHolder {
      TextView title;
      ImageView icon;
    }
  }
}
