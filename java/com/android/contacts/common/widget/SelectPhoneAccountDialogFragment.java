/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.contacts.common.widget;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;
import com.android.contacts.common.R;
import com.android.contacts.common.compat.PhoneAccountCompat;
import com.android.dialer.location.GeoUtil;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.telecom.TelecomUtil;
import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog that allows the user to select a phone accounts for a given action. Optionally provides
 * the choice to set the phone account as default.
 */
public class SelectPhoneAccountDialogFragment extends DialogFragment {

  private static final String ARG_TITLE_RES_ID = "title_res_id";
  private static final String ARG_CAN_SET_DEFAULT = "can_set_default";
  private static final String ARG_SET_DEFAULT_RES_ID = "set_default_res_id";
  private static final String ARG_ACCOUNT_HANDLES = "account_handles";
  private static final String ARG_IS_DEFAULT_CHECKED = "is_default_checked";
  private static final String ARG_LISTENER = "listener";
  private static final String ARG_CALL_ID = "call_id";
  private static final String ARG_HINTS = "hints";

  private List<PhoneAccountHandle> mAccountHandles;
  private List<String> mHints;
  private boolean mIsSelected;
  private boolean mIsDefaultChecked;
  private SelectPhoneAccountListener mListener;

  public SelectPhoneAccountDialogFragment() {}

  /**
   * Create new fragment instance with default title and no option to set as default.
   *
   * @param accountHandles The {@code PhoneAccountHandle}s available to select from.
   * @param listener The listener for the results of the account selection.
   */
  public static SelectPhoneAccountDialogFragment newInstance(
      List<PhoneAccountHandle> accountHandles,
      SelectPhoneAccountListener listener,
      @Nullable String callId) {
    return newInstance(
        R.string.select_account_dialog_title, false, 0, accountHandles, listener, callId, null);
  }

  /**
   * Create new fragment instance. This method also allows specifying a custom title and "set
   * default" checkbox.
   *
   * @param titleResId The resource ID for the string to use in the title of the dialog.
   * @param canSetDefault {@code true} if the dialog should include an option to set the selection
   *     as the default. False otherwise.
   * @param setDefaultResId The resource ID for the string to use in the "set as default" checkbox
   * @param accountHandles The {@code PhoneAccountHandle}s available to select from.
   * @param listener The listener for the results of the account selection.
   * @param callId The callId to be passed back to the listener in {@link
   *     SelectPhoneAccountListener#EXTRA_CALL_ID}
   * @param hints Additional information to be shown underneath the phone account to help user
   *     choose. Index must match {@code accountHandles}
   */
  public static SelectPhoneAccountDialogFragment newInstance(
      int titleResId,
      boolean canSetDefault,
      int setDefaultResId,
      List<PhoneAccountHandle> accountHandles,
      SelectPhoneAccountListener listener,
      @Nullable String callId,
      @Nullable List<String> hints) {
    ArrayList<PhoneAccountHandle> accountHandlesCopy = new ArrayList<>();
    if (accountHandles != null) {
      accountHandlesCopy.addAll(accountHandles);
    }
    SelectPhoneAccountDialogFragment fragment = new SelectPhoneAccountDialogFragment();
    final Bundle args = new Bundle();
    args.putInt(ARG_TITLE_RES_ID, titleResId);
    args.putBoolean(ARG_CAN_SET_DEFAULT, canSetDefault);
    if (setDefaultResId != 0) {
      args.putInt(ARG_SET_DEFAULT_RES_ID, setDefaultResId);
    }
    args.putParcelableArrayList(ARG_ACCOUNT_HANDLES, accountHandlesCopy);
    args.putParcelable(ARG_LISTENER, listener);
    args.putString(ARG_CALL_ID, callId);
    if (hints != null) {
      args.putStringArrayList(ARG_HINTS, new ArrayList<>(hints));
    }
    fragment.setArguments(args);
    fragment.setListener(listener);
    return fragment;
  }

  public void setListener(SelectPhoneAccountListener listener) {
    mListener = listener;
  }

  @Nullable
  @VisibleForTesting
  public SelectPhoneAccountListener getListener() {
    return mListener;
  }

  @VisibleForTesting
  public boolean canSetDefault() {
    return getArguments().getBoolean(ARG_CAN_SET_DEFAULT);
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(ARG_IS_DEFAULT_CHECKED, mIsDefaultChecked);
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    int titleResId = getArguments().getInt(ARG_TITLE_RES_ID);
    boolean canSetDefault = getArguments().getBoolean(ARG_CAN_SET_DEFAULT);
    mAccountHandles = getArguments().getParcelableArrayList(ARG_ACCOUNT_HANDLES);
    mListener = getArguments().getParcelable(ARG_LISTENER);
    mHints = getArguments().getStringArrayList(ARG_HINTS);
    if (savedInstanceState != null) {
      mIsDefaultChecked = savedInstanceState.getBoolean(ARG_IS_DEFAULT_CHECKED);
    }
    mIsSelected = false;

    final DialogInterface.OnClickListener selectionListener =
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            mIsSelected = true;
            PhoneAccountHandle selectedAccountHandle = mAccountHandles.get(which);
            Bundle result = new Bundle();
            result.putParcelable(
                SelectPhoneAccountListener.EXTRA_SELECTED_ACCOUNT_HANDLE, selectedAccountHandle);
            result.putBoolean(SelectPhoneAccountListener.EXTRA_SET_DEFAULT, mIsDefaultChecked);
            result.putString(SelectPhoneAccountListener.EXTRA_CALL_ID, getCallId());
            if (mListener != null) {
              mListener.onReceiveResult(SelectPhoneAccountListener.RESULT_SELECTED, result);
            }
          }
        };

    final CompoundButton.OnCheckedChangeListener checkListener =
        new CompoundButton.OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(CompoundButton check, boolean isChecked) {
            mIsDefaultChecked = isChecked;
          }
        };

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    ListAdapter selectAccountListAdapter =
        new SelectAccountListAdapter(
            builder.getContext(), R.layout.select_account_list_item, mAccountHandles, mHints);

    AlertDialog dialog =
        builder
            .setTitle(titleResId)
            .setAdapter(selectAccountListAdapter, selectionListener)
            .create();

    if (canSetDefault) {
      // Generate custom checkbox view, lint suppressed since no appropriate parent (is dialog)
      @SuppressLint("InflateParams")
      LinearLayout checkboxLayout =
          (LinearLayout)
              LayoutInflater.from(builder.getContext())
                  .inflate(R.layout.default_account_checkbox, null);

      CheckBox checkBox = checkboxLayout.findViewById(R.id.default_account_checkbox_view);
      checkBox.setOnCheckedChangeListener(checkListener);
      checkBox.setChecked(mIsDefaultChecked);

      TextView textView = checkboxLayout.findViewById(R.id.default_account_checkbox_text);
      int setDefaultResId =
          getArguments().getInt(ARG_SET_DEFAULT_RES_ID, R.string.set_default_account);
      textView.setText(setDefaultResId);
      textView.setOnClickListener((view) -> checkBox.performClick());
      checkboxLayout.setOnClickListener((view) -> checkBox.performClick());
      checkboxLayout.setContentDescription(getString(setDefaultResId));
      dialog.getListView().addFooterView(checkboxLayout);
    }

    return dialog;
  }

  @Override
  public void onCancel(DialogInterface dialog) {
    if (!mIsSelected && mListener != null) {
      Bundle result = new Bundle();
      result.putString(SelectPhoneAccountListener.EXTRA_CALL_ID, getCallId());
      mListener.onReceiveResult(SelectPhoneAccountListener.RESULT_DISMISSED, result);
    }
    super.onCancel(dialog);
  }

  @Nullable
  private String getCallId() {
    return getArguments().getString(ARG_CALL_ID);
  }

  public static class SelectPhoneAccountListener extends ResultReceiver {

    static final int RESULT_SELECTED = 1;
    static final int RESULT_DISMISSED = 2;

    static final String EXTRA_SELECTED_ACCOUNT_HANDLE = "extra_selected_account_handle";
    static final String EXTRA_SET_DEFAULT = "extra_set_default";
    static final String EXTRA_CALL_ID = "extra_call_id";

    protected SelectPhoneAccountListener() {
      super(new Handler());
    }

    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {
      if (resultCode == RESULT_SELECTED) {
        onPhoneAccountSelected(
            resultData.getParcelable(EXTRA_SELECTED_ACCOUNT_HANDLE),
            resultData.getBoolean(EXTRA_SET_DEFAULT),
            resultData.getString(EXTRA_CALL_ID));
      } else if (resultCode == RESULT_DISMISSED) {
        onDialogDismissed(resultData.getString(EXTRA_CALL_ID));
      }
    }

    public void onPhoneAccountSelected(
        PhoneAccountHandle selectedAccountHandle, boolean setDefault, @Nullable String callId) {}

    public void onDialogDismissed(@Nullable String callId) {}
  }

  private static class SelectAccountListAdapter extends ArrayAdapter<PhoneAccountHandle> {

    private int mResId;
    private final List<String> mHints;

    SelectAccountListAdapter(
        Context context,
        int resource,
        List<PhoneAccountHandle> accountHandles,
        @Nullable List<String> hints) {
      super(context, resource, accountHandles);
      mHints = hints;
      mResId = resource;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      LayoutInflater inflater =
          (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

      View rowView;
      final ViewHolder holder;

      if (convertView == null) {
        // Cache views for faster scrolling
        rowView = inflater.inflate(mResId, null);
        holder = new ViewHolder();
        holder.labelTextView = (TextView) rowView.findViewById(R.id.label);
        holder.numberTextView = (TextView) rowView.findViewById(R.id.number);
        holder.hintTextView = rowView.findViewById(R.id.hint);
        holder.imageView = (ImageView) rowView.findViewById(R.id.icon);
        rowView.setTag(holder);
      } else {
        rowView = convertView;
        holder = (ViewHolder) rowView.getTag();
      }

      PhoneAccountHandle accountHandle = getItem(position);
      PhoneAccount account =
          getContext().getSystemService(TelecomManager.class).getPhoneAccount(accountHandle);
      if (account == null) {
        return rowView;
      }
      holder.labelTextView.setText(account.getLabel());
      if (account.getAddress() == null
          || TextUtils.isEmpty(account.getAddress().getSchemeSpecificPart())) {
        holder.numberTextView.setVisibility(View.GONE);
      } else {
        holder.numberTextView.setVisibility(View.VISIBLE);
        holder.numberTextView.setText(
            PhoneNumberHelper.formatNumberForDisplay(
                account.getAddress().getSchemeSpecificPart(),
                getCountryIso(getContext(), accountHandle)));
      }
      holder.imageView.setImageDrawable(
          PhoneAccountCompat.createIconDrawable(account, getContext()));
      if (mHints != null && position < mHints.size()) {
        String hint = mHints.get(position);
        if (TextUtils.isEmpty(hint)) {
          holder.hintTextView.setVisibility(View.GONE);
        } else {
          holder.hintTextView.setVisibility(View.VISIBLE);
          holder.hintTextView.setText(hint);
        }
      } else {
        holder.hintTextView.setVisibility(View.GONE);
      }

      return rowView;
    }

    private static String getCountryIso(
        Context context, @NonNull PhoneAccountHandle phoneAccountHandle) {
      Optional<SubscriptionInfo> info =
          TelecomUtil.getSubscriptionInfo(context, phoneAccountHandle);
      if (!info.isPresent()) {
        return GeoUtil.getCurrentCountryIso(context);
      }
      return info.get().getCountryIso().toUpperCase();
    }

    private static final class ViewHolder {

      TextView labelTextView;
      TextView numberTextView;
      TextView hintTextView;
      ImageView imageView;
    }
  }
}
