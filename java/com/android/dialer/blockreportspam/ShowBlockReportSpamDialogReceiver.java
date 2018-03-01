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

package com.android.dialer.blockreportspam;

import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.Toast;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.OnConfirmListener;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.OnSpamDialogClickListener;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.protos.ProtoParsers;
import com.android.dialer.spam.Spam;
import com.android.dialer.spam.SpamComponent;
import java.util.Locale;

/**
 * A {@link BroadcastReceiver} that shows an appropriate dialog upon receiving notifications from
 * {@link ShowBlockReportSpamDialogNotifier}.
 */
public final class ShowBlockReportSpamDialogReceiver extends BroadcastReceiver {

  static final String ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER_AND_OPTIONALLY_REPORT_SPAM =
      "show_dialog_to_block_number_and_optionally_report_spam";
  static final String ACTION_SHOW_DIALOG_TO_REPORT_NOT_SPAM = "show_dialog_to_report_not_spam";
  static final String EXTRA_DIALOG_INFO = "dialog_info";

  /** {@link FragmentManager} needed to show a {@link android.app.DialogFragment}. */
  private final FragmentManager fragmentManager;

  /** Returns an {@link IntentFilter} containing all actions accepted by this broadcast receiver. */
  public static IntentFilter getIntentFilter() {
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER_AND_OPTIONALLY_REPORT_SPAM);
    intentFilter.addAction(ACTION_SHOW_DIALOG_TO_REPORT_NOT_SPAM);
    return intentFilter;
  }

  public ShowBlockReportSpamDialogReceiver(FragmentManager fragmentManager) {
    this.fragmentManager = fragmentManager;
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    LogUtil.enterBlock("ShowBlockReportSpamDialogReceiver.onReceive");

    String action = intent.getAction();

    switch (Assert.isNotNull(action)) {
      case ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER_AND_OPTIONALLY_REPORT_SPAM:
        showDialogToBlockNumberAndOptionallyReportSpam(context, intent);
        break;
      case ACTION_SHOW_DIALOG_TO_REPORT_NOT_SPAM:
        showDialogToReportNotSpam(context, intent);
        break;
      default:
        throw new IllegalStateException("Unsupported action: " + action);
    }
  }

  private void showDialogToBlockNumberAndOptionallyReportSpam(Context context, Intent intent) {
    LogUtil.enterBlock(
        "ShowBlockReportSpamDialogReceiver.showDialogToBlockNumberAndOptionallyReportSpam");

    Assert.checkArgument(intent.hasExtra(EXTRA_DIALOG_INFO));
    BlockReportSpamDialogInfo dialogInfo =
        ProtoParsers.getTrusted(
            intent, EXTRA_DIALOG_INFO, BlockReportSpamDialogInfo.getDefaultInstance());

    Spam spam = SpamComponent.get(context).spam();

    // Set up the positive listener for the dialog.
    OnSpamDialogClickListener onSpamDialogClickListener =
        reportSpam -> {
          LogUtil.i(
              "ShowBlockReportSpamDialogReceiver.showDialogToBlockNumberAndOptionallyReportSpam",
              "confirmed");

          if (reportSpam && spam.isSpamEnabled()) {
            LogUtil.i(
                "ShowBlockReportSpamDialogReceiver.showDialogToBlockNumberAndOptionallyReportSpam",
                "report spam");
            Logger.get(context)
                .logImpression(
                    DialerImpression.Type
                        .REPORT_CALL_AS_SPAM_VIA_CALL_LOG_BLOCK_REPORT_SPAM_SENT_VIA_BLOCK_NUMBER_DIALOG);
            spam.reportSpamFromCallHistory(
                dialogInfo.getNormalizedNumber(),
                dialogInfo.getCountryIso(),
                dialogInfo.getCallType(),
                dialogInfo.getReportingLocation(),
                dialogInfo.getContactSource());
          }

          // TODO(a bug): Block the number.
          Toast.makeText(
                  context,
                  String.format(
                      Locale.ENGLISH,
                      "TODO: " + "Block number %s.",
                      dialogInfo.getNormalizedNumber()),
                  Toast.LENGTH_SHORT)
              .show();
        };

    // Create and show the dialog.
    BlockReportSpamDialogs.BlockReportSpamDialogFragment.newInstance(
            dialogInfo.getNormalizedNumber(),
            spam.isDialogReportSpamCheckedByDefault(),
            onSpamDialogClickListener,
            /* dismissListener = */ null)
        .show(fragmentManager, BlockReportSpamDialogs.BLOCK_REPORT_SPAM_DIALOG_TAG);
  }

  private void showDialogToReportNotSpam(Context context, Intent intent) {
    LogUtil.enterBlock("ShowBlockReportSpamDialogReceiver.showDialogToReportNotSpam");

    Assert.checkArgument(intent.hasExtra(EXTRA_DIALOG_INFO));
    BlockReportSpamDialogInfo dialogInfo =
        ProtoParsers.getTrusted(
            intent, EXTRA_DIALOG_INFO, BlockReportSpamDialogInfo.getDefaultInstance());

    // Set up the positive listener for the dialog.
    OnConfirmListener onConfirmListener =
        () -> {
          LogUtil.i("ShowBlockReportSpamDialogReceiver.showDialogToReportNotSpam", "confirmed");

          Spam spam = SpamComponent.get(context).spam();
          if (spam.isSpamEnabled()) {
            Logger.get(context)
                .logImpression(DialerImpression.Type.DIALOG_ACTION_CONFIRM_NUMBER_NOT_SPAM);
            spam.reportNotSpamFromCallHistory(
                dialogInfo.getNormalizedNumber(),
                dialogInfo.getCountryIso(),
                dialogInfo.getCallType(),
                dialogInfo.getReportingLocation(),
                dialogInfo.getContactSource());
          }
        };

    // Create & show the dialog.
    BlockReportSpamDialogs.ReportNotSpamDialogFragment.newInstance(
            dialogInfo.getNormalizedNumber(), onConfirmListener, /* dismissListener = */ null)
        .show(fragmentManager, BlockReportSpamDialogs.NOT_SPAM_DIALOG_TAG);
  }
}
