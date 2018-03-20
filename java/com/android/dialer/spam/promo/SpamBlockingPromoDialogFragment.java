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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;

/** Dialog for spam blocking on-boarding promotion. */
public class SpamBlockingPromoDialogFragment extends DialogFragment {

  public static final String SPAM_BLOCKING_PROMO_DIALOG_TAG = "SpamBlockingPromoDialog";

  /** Called when dialog positive button is pressed. */
  protected OnEnableListener positiveListener;

  public static DialogFragment newInstance(OnEnableListener positiveListener) {
    SpamBlockingPromoDialogFragment fragment = new SpamBlockingPromoDialogFragment();
    fragment.positiveListener = positiveListener;
    return fragment;
  }

  @Override
  public void onPause() {
    // The dialog is dismissed onPause, i.e. rotation.
    dismiss();
    super.onPause();
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    super.onCreateDialog(savedInstanceState);
    // Return the newly created dialog
    return new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
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
