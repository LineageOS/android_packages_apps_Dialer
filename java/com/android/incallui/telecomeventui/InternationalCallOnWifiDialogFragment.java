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
 * limitations under the License
 */

package com.android.incallui.telecomeventui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.DialogFragment;
import android.support.v4.os.UserManagerCompat;
import android.view.View;
import android.widget.CheckBox;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.storage.StorageComponent;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;

/**
 * Dialog that may be shown when users place an outgoing call to an international number while on
 * Wifi.
 *
 * <p>The android.telephony.event.EVENT_NOTIFY_INTERNATIONAL_CALL_ON_WFC event is sent when users
 * attempt to place a call under these circumstances.
 */
public class InternationalCallOnWifiDialogFragment extends DialogFragment {

  /**
   * Returns {@code true} if an {@link InternationalCallOnWifiDialogFragment} should be shown.
   *
   * <p>Attempting to show an InternationalCallOnWifiDialogFragment when this method returns {@code
   * false} will result in an {@link IllegalStateException}.
   */
  public static boolean shouldShow(@NonNull Context context) {
    if (!UserManagerCompat.isUserUnlocked(context)) {
      LogUtil.i("InternationalCallOnWifiDialogFragment.shouldShow", "user locked, returning false");
      return false;
    }

    SharedPreferences preferences = StorageComponent.get(context).unencryptedSharedPrefs();
    boolean shouldShow = preferences.getBoolean(ALWAYS_SHOW_WARNING_PREFERENCE_KEY, true);

    LogUtil.i("InternationalCallOnWifiDialogFragment.shouldShow", "result: %b", shouldShow);
    return shouldShow;
  }

  /**
   * Returns a new instance of {@link InternationalCallOnWifiDialogFragment} with the given
   * callback.
   *
   * <p>Prefer this method over the default constructor.
   */
  public static InternationalCallOnWifiDialogFragment newInstance(@NonNull String callId) {
    InternationalCallOnWifiDialogFragment fragment = new InternationalCallOnWifiDialogFragment();
    Bundle args = new Bundle();
    args.putString(ARG_CALL_ID, Assert.isNotNull(callId));
    fragment.setArguments(args);
    return fragment;
  }

  /**
   * Key to the preference used to determine if the user wants to see {@link
   * InternationalCallOnWifiDialogFragment InternationalCallOnWifiDialogFragments}.
   */
  @VisibleForTesting
  public static final String ALWAYS_SHOW_WARNING_PREFERENCE_KEY =
      "ALWAYS_SHOW_INTERNATIONAL_CALL_ON_WIFI_WARNING";

  /** Key in the arguments bundle for call id. */
  private static final String ARG_CALL_ID = "call_id";

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle bundle) {
    super.onCreateDialog(bundle);
    LogUtil.enterBlock("InternationalCallOnWifiDialogFragment.onCreateDialog");

    if (!InternationalCallOnWifiDialogFragment.shouldShow(getActivity())) {
      throw new IllegalStateException(
          "shouldShow indicated InternationalCallOnWifiDialogFragment should not have showed");
    }

    View dialogView =
        View.inflate(getActivity(), R.layout.frag_international_call_on_wifi_dialog, null);

    CheckBox alwaysWarn = dialogView.findViewById(R.id.always_warn);

    SharedPreferences preferences = StorageComponent.get(getActivity()).unencryptedSharedPrefs();
    // The default is set to false in this case to ensure that the first time the dialog opens,
    // the checkbox is unchecked.
    alwaysWarn.setChecked(preferences.getBoolean(ALWAYS_SHOW_WARNING_PREFERENCE_KEY, false));

    AlertDialog alertDialog =
        new AlertDialog.Builder(getActivity())
            .setCancelable(false)
            .setView(dialogView)
            .setPositiveButton(
                android.R.string.ok,
                (dialog, which) -> onPositiveButtonClick(preferences, alwaysWarn.isChecked()))
            .setNegativeButton(
                android.R.string.cancel,
                (dialog, which) -> onNegativeButtonClick(preferences, alwaysWarn.isChecked()))
            .create();

    alertDialog.setCanceledOnTouchOutside(false);
    return alertDialog;
  }

  private void onPositiveButtonClick(@NonNull SharedPreferences preferences, boolean alwaysWarn) {
    LogUtil.i(
        "InternationalCallOnWifiDialogFragment.onPositiveButtonClick",
        "alwaysWarn: %b",
        alwaysWarn);
    preferences.edit().putBoolean(ALWAYS_SHOW_WARNING_PREFERENCE_KEY, alwaysWarn).apply();

    // Neither callback nor callId are null in normal circumstances. See comments on callback
    continueCall(getArguments().getString(ARG_CALL_ID));
  }

  private void onNegativeButtonClick(@NonNull SharedPreferences preferences, boolean alwaysWarn) {
    LogUtil.i(
        "InternationalCallOnWifiDialogFragment.onNegativeButtonClick",
        "alwaysWarn: %b",
        alwaysWarn);
    preferences.edit().putBoolean(ALWAYS_SHOW_WARNING_PREFERENCE_KEY, alwaysWarn).apply();

    // Neither callback nor callId are null in normal circumstances. See comments on callback
    cancelCall(getArguments().getString(ARG_CALL_ID));
  }

  private void continueCall(@NonNull String callId) {
    LogUtil.i(
        "InternationalCallOnWifiDialogFragment.continueCall",
        "Continuing call with ID: %s",
        callId);
    InternationalCallOnWifiDialogActivity activity =
        FragmentUtils.getParent(this, InternationalCallOnWifiDialogActivity.class);
    if (activity != null) {
      activity.finish();
    }
  }

  private void cancelCall(@NonNull String callId) {
    DialerCall call = CallList.getInstance().getCallById(callId);
    if (call == null) {
      LogUtil.i(
          "InternationalCallOnWifiDialogFragment.cancelCall",
          "Call destroyed before the dialog is closed");
    } else {
      LogUtil.i(
          "InternationalCallOnWifiDialogFragment.cancelCall",
          "Disconnecting international call on WiFi");
      call.disconnect();
    }
    InternationalCallOnWifiDialogActivity activity =
        FragmentUtils.getParent(this, InternationalCallOnWifiDialogActivity.class);
    if (activity != null) {
      activity.finish();
    }
  }
}
