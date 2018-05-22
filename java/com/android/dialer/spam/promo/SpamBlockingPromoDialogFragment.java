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

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

/** Dialog for spam blocking on-boarding promotion. */
public class SpamBlockingPromoDialogFragment extends DialogFragment {

  public static final String SPAM_BLOCKING_PROMO_DIALOG_TAG = "SpamBlockingPromoDialog";

  /** Called when dialog positive button is pressed. */
  protected OnEnableListener positiveListener;

  /** Called when the dialog is dismissed. */
  @Nullable protected DialogInterface.OnDismissListener dismissListener;

  public static DialogFragment newInstance(
      OnEnableListener positiveListener,
      @Nullable DialogInterface.OnDismissListener dismissListener) {
    SpamBlockingPromoDialogFragment fragment = new SpamBlockingPromoDialogFragment();
    fragment.positiveListener = positiveListener;
    fragment.dismissListener = dismissListener;
    return fragment;
  }

  @Override
  public void onDismiss(DialogInterface dialog) {
    if (dismissListener != null) {
      dismissListener.onDismiss(dialog);
    }
    super.onDismiss(dialog);
  }

  @Override
  public void onPause() {
    // The dialog is dismissed onPause, i.e. rotation.
    dismiss();
    dismissListener = null;
    positiveListener = null;
    super.onPause();
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    super.onCreateDialog(savedInstanceState);
    // Return the newly created dialog
    return new AlertDialog.Builder(getActivity())
        .setCancelable(true)
        .setTitle(R.string.spam_blocking_promo_title)
        .setMessage(R.string.spam_blocking_promo_text)
        .setNegativeButton(
            R.string.spam_blocking_promo_action_dismiss, (dialog, which) -> dismiss())
        .setPositiveButton(
            R.string.spam_blocking_promo_action_filter_spam,
            (dialog, which) -> {
              dismiss();
              positiveListener.onClick();
            })
        .create();
  }

  /** Positive listener for spam blocking promotion dialog. */
  public interface OnEnableListener {
    /** Called when user clicks on positive button in the spam blocking promo dialog. */
    void onClick();
  }
}
