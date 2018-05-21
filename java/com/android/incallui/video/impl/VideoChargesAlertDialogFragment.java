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

package com.android.incallui.video.impl;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.DialogFragment;
import android.support.v4.os.UserManagerCompat;
import android.telecom.Call.Details;
import android.view.View;
import android.widget.CheckBox;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;

/** Alert dialog for video charges. */
public class VideoChargesAlertDialogFragment extends DialogFragment {

  /** Preference key for whether to show the alert dialog for video charges next time. */
  @VisibleForTesting
  static final String KEY_DO_NOT_SHOW_VIDEO_CHARGES_ALERT = "key_do_not_show_video_charges_alert";

  /** Key in the arguments bundle for call id. */
  private static final String ARG_CALL_ID = "call_id";

  /**
   * Returns {@code true} if an {@link VideoChargesAlertDialogFragment} should be shown.
   *
   * <p>Attempting to show an VideoChargesAlertDialogFragment when this method returns {@code false}
   * will result in an {@link IllegalStateException}.
   */
  public static boolean shouldShow(@NonNull Context context, String callId) {
    DialerCall call = CallList.getInstance().getCallById(callId);
    if (call == null) {
      LogUtil.i("VideoChargesAlertDialogFragment.shouldShow", "null call");
      return false;
    }

    if (call.hasProperty(Details.PROPERTY_WIFI)) {
      return false;
    }

    if (call.didDismissVideoChargesAlertDialog()) {
      LogUtil.i(
          "VideoChargesAlertDialogFragment.shouldShow", "The dialog has been dismissed by user");
      return false;
    }

    if (!call.showVideoChargesAlertDialog()) {
      return false;
    }

    if (!UserManagerCompat.isUserUnlocked(context)) {
      LogUtil.i("VideoChargesAlertDialogFragment.shouldShow", "user locked, returning false");
      return false;
    }

    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    if (preferences.getBoolean(KEY_DO_NOT_SHOW_VIDEO_CHARGES_ALERT, false)) {
      LogUtil.i(
          "VideoChargesAlertDialogFragment.shouldShow",
          "Video charges alert has been disabled by user, returning false");
      return false;
    }

    return true;
  }

  /**
   * Returns a new instance of {@link VideoChargesAlertDialogFragment}
   *
   * <p>Prefer this method over the default constructor.
   */
  public static VideoChargesAlertDialogFragment newInstance(@NonNull String callId) {
    VideoChargesAlertDialogFragment fragment = new VideoChargesAlertDialogFragment();
    Bundle args = new Bundle();
    args.putString(ARG_CALL_ID, Assert.isNotNull(callId));
    fragment.setArguments(args);
    return fragment;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle bundle) {
    super.onCreateDialog(bundle);

    if (!VideoChargesAlertDialogFragment.shouldShow(
        getActivity(), getArguments().getString(ARG_CALL_ID))) {
      throw new IllegalStateException(
          "shouldShow indicated VideoChargesAlertDialogFragment should not have showed");
    }

    View dialogView = View.inflate(getActivity(), R.layout.frag_video_charges_alert_dialog, null);

    CheckBox alertCheckBox = dialogView.findViewById(R.id.do_not_show);

    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
    AlertDialog alertDialog =
        new AlertDialog.Builder(getActivity())
            .setView(dialogView)
            .setPositiveButton(
                android.R.string.ok,
                (dialog, which) -> onPositiveButtonClicked(preferences, alertCheckBox.isChecked()))
            .create();
    this.setCancelable(false);
    return alertDialog;
  }

  private void onPositiveButtonClicked(@NonNull SharedPreferences preferences, boolean isChecked) {
    LogUtil.i(
        "VideoChargesAlertDialogFragment.onPositiveButtonClicked", "isChecked: %b", isChecked);
    preferences.edit().putBoolean(KEY_DO_NOT_SHOW_VIDEO_CHARGES_ALERT, isChecked).apply();

    DialerCall dialerCall =
        CallList.getInstance().getCallById(getArguments().getString(ARG_CALL_ID));
    if (dialerCall != null) {
      dialerCall.setDidDismissVideoChargesAlertDialog(true);
    }
  }
}
