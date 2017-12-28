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

package com.android.dialer.blocking;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

/** Helper class for creating block/report dialog fragments. */
public class BlockReportSpamDialogs {

  public static final String BLOCK_REPORT_SPAM_DIALOG_TAG = "BlockReportSpamDialog";
  public static final String BLOCK_DIALOG_TAG = "BlockDialog";
  public static final String UNBLOCK_DIALOG_TAG = "UnblockDialog";
  public static final String NOT_SPAM_DIALOG_TAG = "NotSpamDialog";

  /** Creates a dialog with the default cancel button listener (dismisses dialog). */
  private static AlertDialog.Builder createDialogBuilder(
      Activity activity, final DialogFragment fragment) {
    return new AlertDialog.Builder(activity, R.style.AlertDialogTheme)
        .setCancelable(true)
        .setNegativeButton(
            android.R.string.cancel,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                fragment.dismiss();
              }
            });
  }

  /**
   * Creates a generic click listener which dismisses the fragment and then calls the actual
   * listener.
   */
  private static DialogInterface.OnClickListener createGenericOnClickListener(
      final DialogFragment fragment, final OnConfirmListener listener) {
    return new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        fragment.dismiss();
        listener.onClick();
      }
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
   * Listener passed to block/report spam dialog for positive click in {@link
   * BlockReportSpamDialogFragment}.
   */
  public interface OnSpamDialogClickListener {

    /**
     * Called when user clicks on positive button in block/report spam dialog.
     *
     * @param isSpamChecked Whether the spam checkbox is checked.
     */
    void onClick(boolean isSpamChecked);
  }

  /** Listener passed to all dialogs except the block/report spam dialog for positive click. */
  public interface OnConfirmListener {

    /** Called when user clicks on positive button in the dialog. */
    void onClick();
  }

  /** Contains the common attributes between all block/unblock/report dialog fragments. */
  private static class CommonDialogsFragment extends DialogFragment {

    /** The number to display in the dialog title. */
    protected String displayNumber;

    /** Called when dialog positive button is pressed. */
    protected OnConfirmListener positiveListener;

    /** Called when dialog is dismissed. */
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

  /** Dialog for block/report spam with the mark as spam checkbox. */
  public static class BlockReportSpamDialogFragment extends CommonDialogsFragment {

    /** Called when dialog positive button is pressed. */
    private OnSpamDialogClickListener positiveListener;

    /** Whether the mark as spam checkbox is checked before displaying the dialog. */
    private boolean spamChecked;

    public static DialogFragment newInstance(
        String displayNumber,
        boolean spamChecked,
        OnSpamDialogClickListener positiveListener,
        @Nullable DialogInterface.OnDismissListener dismissListener) {
      BlockReportSpamDialogFragment fragment = new BlockReportSpamDialogFragment();
      fragment.spamChecked = spamChecked;
      fragment.displayNumber = displayNumber;
      fragment.positiveListener = positiveListener;
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
      isSpamCheckbox.setOnCheckedChangeListener(
          new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
              spamChecked = isChecked;
            }
          });

      TextView details = (TextView) dialogView.findViewById(R.id.block_details);
      details.setText(getBlockMessage(getContext()));

      AlertDialog.Builder alertDialogBuilder = createDialogBuilder(getActivity(), this);
      Dialog dialog =
          alertDialogBuilder
              .setView(dialogView)
              .setTitle(getString(R.string.block_report_number_alert_title, displayNumber))
              .setPositiveButton(
                  R.string.block_number_ok,
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                      dismiss();
                      positiveListener.onClick(isSpamCheckbox.isChecked());
                    }
                  })
              .create();
      dialog.setCanceledOnTouchOutside(true);
      return dialog;
    }
  }

  /** Dialog for blocking a number. */
  public static class BlockDialogFragment extends CommonDialogsFragment {

    private boolean isSpamEnabled;

    public static DialogFragment newInstance(
        String displayNumber,
        boolean isSpamEnabled,
        OnConfirmListener positiveListener,
        @Nullable DialogInterface.OnDismissListener dismissListener) {
      BlockDialogFragment fragment = new BlockDialogFragment();
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

  /** Dialog for unblocking a number. */
  public static class UnblockDialogFragment extends CommonDialogsFragment {

    /** Whether or not the number is spam. */
    private boolean isSpam;

    public static DialogFragment newInstance(
        String displayNumber,
        boolean isSpam,
        OnConfirmListener positiveListener,
        @Nullable DialogInterface.OnDismissListener dismissListener) {
      UnblockDialogFragment fragment = new UnblockDialogFragment();
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

  /** Dialog for reporting a number as not spam. */
  public static class ReportNotSpamDialogFragment extends CommonDialogsFragment {

    public static DialogFragment newInstance(
        String displayNumber,
        OnConfirmListener positiveListener,
        @Nullable DialogInterface.OnDismissListener dismissListener) {
      ReportNotSpamDialogFragment fragment = new ReportNotSpamDialogFragment();
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
