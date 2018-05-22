/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.blockreportspam;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import com.android.dialer.blocking.FilteredNumberCompat;

/** Creates dialog fragments to block a number and/or report it as spam/not spam. */
public final class BlockReportSpamDialogs {

  public static final String BLOCK_REPORT_SPAM_DIALOG_TAG = "BlockReportSpamDialog";
  public static final String BLOCK_DIALOG_TAG = "BlockDialog";
  public static final String UNBLOCK_DIALOG_TAG = "UnblockDialog";
  public static final String NOT_SPAM_DIALOG_TAG = "NotSpamDialog";

  /** Creates a dialog with the default cancel button listener (which dismisses the dialog). */
  private static AlertDialog.Builder createDialogBuilder(
      Activity activity, final DialogFragment fragment) {
    return new AlertDialog.Builder(activity)
        .setCancelable(true)
        .setNegativeButton(android.R.string.cancel, (dialog, which) -> fragment.dismiss());
  }

  /**
   * Creates a generic click listener which dismisses the fragment and then calls the actual
   * listener.
   */
  private static DialogInterface.OnClickListener createGenericOnClickListener(
      final DialogFragment fragment, final OnConfirmListener listener) {
    return (dialog, which) -> {
      fragment.dismiss();
      listener.onClick();
    };
  }

  private static String getBlockMessage(Context context) {
    String message;
    if (FilteredNumberCompat.useNewFiltering(context)) {
      message = context.getString(R.string.block_number_confirmation_message_new_filtering);
    } else {
      message = context.getString(R.string.block_report_number_alert_details);
    }
    return message;
  }

  /**
   * Positive listener for the "Block/Report spam" dialog {@link
   * DialogFragmentForBlockingNumberAndOptionallyReportingAsSpam}.
   */
  public interface OnSpamDialogClickListener {

    /**
     * Called when the user clicks on the positive button of the "Block/Report spam" dialog.
     *
     * @param isSpamChecked Whether the spam checkbox is checked.
     */
    void onClick(boolean isSpamChecked);
  }

  /** Positive listener for dialogs other than the "Block/Report spam" dialog. */
  public interface OnConfirmListener {

    /** Called when the user clicks on the positive button of the dialog. */
    void onClick();
  }

  /** Contains common attributes shared among all dialog fragments. */
  private abstract static class CommonDialogsFragment extends DialogFragment {

    /** The number to display in the dialog title. */
    protected String displayNumber;

    /** Listener for the positive button. */
    protected OnConfirmListener positiveListener;

    /** Listener for when the dialog is dismissed. */
    @Nullable protected DialogInterface.OnDismissListener dismissListener;

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
      displayNumber = null;
      super.onPause();
    }
  }

  /**
   * Dialog for blocking a number and optionally reporting it as spam.
   *
   * <p>This dialog is for a number that is neither blocked nor marked as spam. It has a checkbox
   * that allows the user to report a number as spam when they block it.
   */
  public static class DialogFragmentForBlockingNumberAndOptionallyReportingAsSpam
      extends CommonDialogsFragment {

    /** Called when dialog positive button is pressed. */
    private OnSpamDialogClickListener onSpamDialogClickListener;

    /** Whether the mark as spam checkbox is checked before displaying the dialog. */
    private boolean spamChecked;

    public static DialogFragment newInstance(
        String displayNumber,
        boolean spamChecked,
        OnSpamDialogClickListener onSpamDialogClickListener,
        @Nullable DialogInterface.OnDismissListener dismissListener) {
      DialogFragmentForBlockingNumberAndOptionallyReportingAsSpam fragment =
          new DialogFragmentForBlockingNumberAndOptionallyReportingAsSpam();
      fragment.spamChecked = spamChecked;
      fragment.displayNumber = displayNumber;
      fragment.onSpamDialogClickListener = onSpamDialogClickListener;
      fragment.dismissListener = dismissListener;
      return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      super.onCreateDialog(savedInstanceState);
      View dialogView = View.inflate(getActivity(), R.layout.block_report_spam_dialog, null);
      final CheckBox isSpamCheckbox =
          (CheckBox) dialogView.findViewById(R.id.report_number_as_spam_action);
      // Listen for changes on the checkbox and update if orientation changes
      isSpamCheckbox.setChecked(spamChecked);
      isSpamCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> spamChecked = isChecked);

      TextView details = (TextView) dialogView.findViewById(R.id.block_details);
      details.setText(getBlockMessage(getContext()));

      AlertDialog.Builder alertDialogBuilder = createDialogBuilder(getActivity(), this);
      Dialog blockReportSpamDialog =
          alertDialogBuilder
              .setView(dialogView)
              .setTitle(getString(R.string.block_report_number_alert_title, displayNumber))
              .setPositiveButton(
                  R.string.block_number_ok,
                  (dialog, which) -> {
                    dismiss();
                    onSpamDialogClickListener.onClick(isSpamCheckbox.isChecked());
                  })
              .create();
      blockReportSpamDialog.setCanceledOnTouchOutside(true);
      return blockReportSpamDialog;
    }
  }

  /**
   * Dialog for blocking a number and reporting it as spam.
   *
   * <p>This dialog is for the migration of blocked numbers. Its positive action should block a
   * number, and also marks it as spam if the spam feature is enabled.
   */
  public static class DialogFragmentForBlockingNumberAndReportingAsSpam
      extends CommonDialogsFragment {

    private boolean isSpamEnabled;

    public static DialogFragment newInstance(
        String displayNumber,
        boolean isSpamEnabled,
        OnConfirmListener positiveListener,
        @Nullable DialogInterface.OnDismissListener dismissListener) {
      DialogFragmentForBlockingNumberAndReportingAsSpam fragment =
          new DialogFragmentForBlockingNumberAndReportingAsSpam();
      fragment.displayNumber = displayNumber;
      fragment.positiveListener = positiveListener;
      fragment.dismissListener = dismissListener;
      fragment.isSpamEnabled = isSpamEnabled;
      return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      super.onCreateDialog(savedInstanceState);
      // Return the newly created dialog
      AlertDialog.Builder alertDialogBuilder = createDialogBuilder(getActivity(), this);
      Dialog dialog =
          alertDialogBuilder
              .setTitle(getString(R.string.block_number_confirmation_title, displayNumber))
              .setMessage(
                  isSpamEnabled
                      ? getString(
                          R.string.block_number_alert_details, getBlockMessage(getContext()))
                      : getString(R.string.block_report_number_alert_details))
              .setPositiveButton(
                  R.string.block_number_ok, createGenericOnClickListener(this, positiveListener))
              .create();
      dialog.setCanceledOnTouchOutside(true);
      return dialog;
    }
  }

  /**
   * Dialog for blocking a number.
   *
   * <p>This dialog is for a spam number that hasn't been blocked. For example, if the user receives
   * a spam call, this dialog will be shown if they would like to block the number.
   */
  public static class DialogFragmentForBlockingNumber extends CommonDialogsFragment {

    public static DialogFragment newInstance(
        String displayNumber,
        OnConfirmListener positiveListener,
        @Nullable DialogInterface.OnDismissListener dismissListener) {
      DialogFragmentForBlockingNumberAndReportingAsSpam fragment =
          new DialogFragmentForBlockingNumberAndReportingAsSpam();
      fragment.displayNumber = displayNumber;
      fragment.positiveListener = positiveListener;
      fragment.dismissListener = dismissListener;
      return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      super.onCreateDialog(savedInstanceState);
      // Return the newly created dialog
      AlertDialog.Builder alertDialogBuilder = createDialogBuilder(getActivity(), this);
      Dialog dialog =
          alertDialogBuilder
              .setTitle(getString(R.string.block_number_confirmation_title, displayNumber))
              .setMessage(getString(R.string.block_report_number_alert_details))
              .setPositiveButton(
                  R.string.block_number_ok, createGenericOnClickListener(this, positiveListener))
              .create();
      dialog.setCanceledOnTouchOutside(true);
      return dialog;
    }
  }

  /**
   * Dialog for unblocking a number and marking it as not spam.
   *
   * <p>This dialog is used in the old call log, where unblocking a number will also mark it as not
   * spam.
   */
  public static class DialogFragmentForUnblockingNumberAndReportingAsNotSpam
      extends CommonDialogsFragment {

    /** Whether or not the number is spam. */
    private boolean isSpam;

    public static DialogFragment newInstance(
        String displayNumber,
        boolean isSpam,
        OnConfirmListener positiveListener,
        @Nullable DialogInterface.OnDismissListener dismissListener) {
      DialogFragmentForUnblockingNumberAndReportingAsNotSpam fragment =
          new DialogFragmentForUnblockingNumberAndReportingAsNotSpam();
      fragment.displayNumber = displayNumber;
      fragment.isSpam = isSpam;
      fragment.positiveListener = positiveListener;
      fragment.dismissListener = dismissListener;
      return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      super.onCreateDialog(savedInstanceState);
      // Return the newly created dialog
      AlertDialog.Builder alertDialogBuilder = createDialogBuilder(getActivity(), this);
      if (isSpam) {
        alertDialogBuilder
            .setMessage(R.string.unblock_number_alert_details)
            .setTitle(getString(R.string.unblock_report_number_alert_title, displayNumber));
      } else {
        alertDialogBuilder.setMessage(
            getString(R.string.unblock_report_number_alert_title, displayNumber));
      }
      Dialog dialog =
          alertDialogBuilder
              .setPositiveButton(
                  R.string.unblock_number_ok, createGenericOnClickListener(this, positiveListener))
              .create();
      dialog.setCanceledOnTouchOutside(true);
      return dialog;
    }
  }

  /**
   * Dialog for unblocking a number.
   *
   * <p>This dialog is used in the new call log, where unblocking a number will *not* mark it as not
   * spam.
   */
  public static class DialogFragmentForUnblockingNumber extends CommonDialogsFragment {

    public static DialogFragment newInstance(
        String displayNumber,
        OnConfirmListener positiveListener,
        @Nullable DialogInterface.OnDismissListener dismissListener) {
      DialogFragmentForUnblockingNumberAndReportingAsNotSpam fragment =
          new DialogFragmentForUnblockingNumberAndReportingAsNotSpam();
      fragment.displayNumber = displayNumber;
      fragment.positiveListener = positiveListener;
      fragment.dismissListener = dismissListener;
      return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      super.onCreateDialog(savedInstanceState);
      // Return the newly created dialog
      AlertDialog.Builder alertDialogBuilder = createDialogBuilder(getActivity(), this);
      alertDialogBuilder.setMessage(
          getString(R.string.unblock_report_number_alert_title, displayNumber));
      Dialog dialog =
          alertDialogBuilder
              .setPositiveButton(
                  R.string.unblock_number_ok, createGenericOnClickListener(this, positiveListener))
              .create();
      dialog.setCanceledOnTouchOutside(true);
      return dialog;
    }
  }

  /** Dialog for reporting a number as not spam. */
  public static class DialogFragmentForReportingNotSpam extends CommonDialogsFragment {

    public static DialogFragment newInstance(
        String displayNumber,
        OnConfirmListener positiveListener,
        @Nullable DialogInterface.OnDismissListener dismissListener) {
      DialogFragmentForReportingNotSpam fragment = new DialogFragmentForReportingNotSpam();
      fragment.displayNumber = displayNumber;
      fragment.positiveListener = positiveListener;
      fragment.dismissListener = dismissListener;
      return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      super.onCreateDialog(savedInstanceState);
      // Return the newly created dialog
      AlertDialog.Builder alertDialogBuilder = createDialogBuilder(getActivity(), this);
      Dialog dialog =
          alertDialogBuilder
              .setTitle(R.string.report_not_spam_alert_title)
              .setMessage(getString(R.string.report_not_spam_alert_details, displayNumber))
              .setPositiveButton(
                  R.string.report_not_spam_alert_button,
                  createGenericOnClickListener(this, positiveListener))
              .create();
      dialog.setCanceledOnTouchOutside(true);
      return dialog;
    }
  }
}
