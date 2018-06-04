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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.app.FragmentManager;
import android.widget.Toast;
import com.android.dialer.blocking.Blocking;
import com.android.dialer.blocking.Blocking.BlockingFailedException;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.DialogFragmentForBlockingNumber;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.DialogFragmentForBlockingNumberAndOptionallyReportingAsSpam;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.DialogFragmentForReportingNotSpam;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.DialogFragmentForUnblockingNumber;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.OnConfirmListener;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.OnSpamDialogClickListener;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.DialerImpression.Type;
import com.android.dialer.logging.Logger;
import com.android.dialer.protos.ProtoParsers;
import com.android.dialer.spam.Spam;
import com.android.dialer.spam.SpamComponent;
import com.android.dialer.spam.SpamSettings;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

/**
 * A {@link BroadcastReceiver} that shows an appropriate dialog upon receiving notifications from
 * {@link ShowBlockReportSpamDialogNotifier}.
 */
public final class ShowBlockReportSpamDialogReceiver extends BroadcastReceiver {

  static final String ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER = "show_dialog_to_block_number";
  static final String ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER_AND_OPTIONALLY_REPORT_SPAM =
      "show_dialog_to_block_number_and_optionally_report_spam";
  static final String ACTION_SHOW_DIALOG_TO_REPORT_NOT_SPAM = "show_dialog_to_report_not_spam";
  static final String ACTION_SHOW_DIALOG_TO_UNBLOCK_NUMBER = "show_dialog_to_unblock_number";
  static final String EXTRA_DIALOG_INFO = "dialog_info";

  /** {@link FragmentManager} needed to show a {@link android.app.DialogFragment}. */
  private final FragmentManager fragmentManager;

  /** Returns an {@link IntentFilter} containing all actions accepted by this broadcast receiver. */
  public static IntentFilter getIntentFilter() {
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER_AND_OPTIONALLY_REPORT_SPAM);
    intentFilter.addAction(ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER);
    intentFilter.addAction(ACTION_SHOW_DIALOG_TO_REPORT_NOT_SPAM);
    intentFilter.addAction(ACTION_SHOW_DIALOG_TO_UNBLOCK_NUMBER);
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
      case ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER:
        showDialogToBlockNumber(context, intent);
        break;
      case ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER_AND_OPTIONALLY_REPORT_SPAM:
        showDialogToBlockNumberAndOptionallyReportSpam(context, intent);
        break;
      case ACTION_SHOW_DIALOG_TO_REPORT_NOT_SPAM:
        showDialogToReportNotSpam(context, intent);
        break;
      case ACTION_SHOW_DIALOG_TO_UNBLOCK_NUMBER:
        showDialogToUnblockNumber(context, intent);
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
    SpamSettings spamSettings = SpamComponent.get(context).spamSettings();

    // Set up the positive listener for the dialog.
    OnSpamDialogClickListener onSpamDialogClickListener =
        reportSpam -> {
          LogUtil.i(
              "ShowBlockReportSpamDialogReceiver.showDialogToBlockNumberAndOptionallyReportSpam",
              "confirmed");

          if (reportSpam && spamSettings.isSpamEnabled()) {
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

          blockNumber(context, dialogInfo);
        };

    // Create and show the dialog.
    DialogFragmentForBlockingNumberAndOptionallyReportingAsSpam.newInstance(
            dialogInfo.getNormalizedNumber(),
            spamSettings.isDialogReportSpamCheckedByDefault(),
            onSpamDialogClickListener,
            /* dismissListener = */ null)
        .show(fragmentManager, BlockReportSpamDialogs.BLOCK_REPORT_SPAM_DIALOG_TAG);
  }

  private void showDialogToBlockNumber(Context context, Intent intent) {
    LogUtil.enterBlock("ShowBlockReportSpamDialogReceiver.showDialogToBlockNumber");

    Assert.checkArgument(intent.hasExtra(EXTRA_DIALOG_INFO));
    BlockReportSpamDialogInfo dialogInfo =
        ProtoParsers.getTrusted(
            intent, EXTRA_DIALOG_INFO, BlockReportSpamDialogInfo.getDefaultInstance());

    // Set up the positive listener for the dialog.
    OnConfirmListener onConfirmListener =
        () -> {
          LogUtil.i("ShowBlockReportSpamDialogReceiver.showDialogToBlockNumber", "block number");
          blockNumber(context, dialogInfo);
        };

    // Create and show the dialog.
    DialogFragmentForBlockingNumber.newInstance(
            dialogInfo.getNormalizedNumber(), onConfirmListener, /* dismissListener = */ null)
        .show(fragmentManager, BlockReportSpamDialogs.BLOCK_DIALOG_TAG);
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

          if (SpamComponent.get(context).spamSettings().isSpamEnabled()) {
            Logger.get(context)
                .logImpression(DialerImpression.Type.DIALOG_ACTION_CONFIRM_NUMBER_NOT_SPAM);
            SpamComponent.get(context)
                .spam()
                .reportNotSpamFromCallHistory(
                    dialogInfo.getNormalizedNumber(),
                    dialogInfo.getCountryIso(),
                    dialogInfo.getCallType(),
                    dialogInfo.getReportingLocation(),
                    dialogInfo.getContactSource());
          }
        };

    // Create & show the dialog.
    DialogFragmentForReportingNotSpam.newInstance(
            dialogInfo.getNormalizedNumber(), onConfirmListener, /* dismissListener = */ null)
        .show(fragmentManager, BlockReportSpamDialogs.NOT_SPAM_DIALOG_TAG);
  }

  private void showDialogToUnblockNumber(Context context, Intent intent) {
    LogUtil.enterBlock("ShowBlockReportSpamDialogReceiver.showDialogToUnblockNumber");

    Assert.checkArgument(intent.hasExtra(EXTRA_DIALOG_INFO));
    BlockReportSpamDialogInfo dialogInfo =
        ProtoParsers.getTrusted(
            intent, EXTRA_DIALOG_INFO, BlockReportSpamDialogInfo.getDefaultInstance());

    // Set up the positive listener for the dialog.
    OnConfirmListener onConfirmListener =
        () -> {
          LogUtil.i("ShowBlockReportSpamDialogReceiver.showDialogToUnblockNumber", "confirmed");

          unblockNumber(context, dialogInfo);
        };

    // Create & show the dialog.
    DialogFragmentForUnblockingNumber.newInstance(
            dialogInfo.getNormalizedNumber(), onConfirmListener, /* dismissListener = */ null)
        .show(fragmentManager, BlockReportSpamDialogs.UNBLOCK_DIALOG_TAG);
  }

  private static void blockNumber(Context context, BlockReportSpamDialogInfo dialogInfo) {
    Logger.get(context).logImpression(Type.USER_ACTION_BLOCKED_NUMBER);
    Futures.addCallback(
        Blocking.block(
            context,
            ImmutableList.of(dialogInfo.getNormalizedNumber()),
            dialogInfo.getCountryIso()),
        new FutureCallback<Void>() {
          @Override
          public void onSuccess(Void unused) {
            // Do nothing
          }

          @Override
          public void onFailure(Throwable throwable) {
            if (throwable instanceof BlockingFailedException) {
              Logger.get(context).logImpression(Type.USER_ACTION_BLOCK_NUMBER_FAILED);
              Toast.makeText(context, R.string.block_number_failed_toast, Toast.LENGTH_LONG).show();
            } else {
              throw new RuntimeException(throwable);
            }
          }
        },
        DialerExecutorComponent.get(context).uiExecutor());
  }

  private static void unblockNumber(Context context, BlockReportSpamDialogInfo dialogInfo) {
    Logger.get(context).logImpression(Type.USER_ACTION_UNBLOCKED_NUMBER);
    Futures.addCallback(
        Blocking.unblock(
            context,
            ImmutableList.of(dialogInfo.getNormalizedNumber()),
            dialogInfo.getCountryIso()),
        new FutureCallback<Void>() {
          @Override
          public void onSuccess(Void unused) {
            // Do nothing
          }

          @Override
          public void onFailure(Throwable throwable) {
            if (throwable instanceof BlockingFailedException) {
              Logger.get(context).logImpression(Type.USER_ACTION_UNBLOCK_NUMBER_FAILED);
              Toast.makeText(context, R.string.unblock_number_failed_toast, Toast.LENGTH_LONG)
                  .show();
            } else {
              throw new RuntimeException(throwable);
            }
          }
        },
        DialerExecutorComponent.get(context).uiExecutor());
  }
}
