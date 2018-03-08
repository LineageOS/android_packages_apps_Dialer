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

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import com.android.dialer.common.LogUtil;
import com.android.dialer.protos.ProtoParsers;

/**
 * Notifies that a dialog for blocking a number and/or marking it as spam/not spam should be shown.
 */
public final class ShowBlockReportSpamDialogNotifier {

  private ShowBlockReportSpamDialogNotifier() {}

  /**
   * Notifies that a dialog for blocking a number and optionally report it as spam should be shown.
   */
  public static void notifyShowDialogToBlockNumberAndOptionallyReportSpam(
      Context context, BlockReportSpamDialogInfo blockReportSpamDialogInfo) {
    LogUtil.enterBlock(
        "ShowBlockReportSpamDialogNotifier.notifyShowDialogToBlockNumberAndOptionallyReportSpam");

    Intent intent = new Intent();
    intent.setAction(
        ShowBlockReportSpamDialogReceiver
            .ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER_AND_OPTIONALLY_REPORT_SPAM);
    ProtoParsers.put(
        intent, ShowBlockReportSpamDialogReceiver.EXTRA_DIALOG_INFO, blockReportSpamDialogInfo);

    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
  }

  /** Notifies that a dialog for blocking a number should be shown. */
  public static void notifyShowDialogToBlockNumber(
      Context context, BlockReportSpamDialogInfo blockReportSpamDialogInfo) {
    LogUtil.enterBlock("ShowBlockReportSpamDialogNotifier.notifyShowDialogToBlockNumber");

    Intent intent = new Intent();
    intent.setAction(ShowBlockReportSpamDialogReceiver.ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER);
    ProtoParsers.put(
        intent, ShowBlockReportSpamDialogReceiver.EXTRA_DIALOG_INFO, blockReportSpamDialogInfo);

    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
  }

  /** Notifies that a dialog for reporting a number as not spam should be shown. */
  public static void notifyShowDialogToReportNotSpam(
      Context context, BlockReportSpamDialogInfo blockReportSpamDialogInfo) {
    LogUtil.enterBlock("ShowBlockReportSpamDialogNotifier.notifyShowDialogToReportNotSpam");

    Intent intent = new Intent();
    intent.setAction(ShowBlockReportSpamDialogReceiver.ACTION_SHOW_DIALOG_TO_REPORT_NOT_SPAM);
    ProtoParsers.put(
        intent, ShowBlockReportSpamDialogReceiver.EXTRA_DIALOG_INFO, blockReportSpamDialogInfo);

    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
  }

  /** Notifies that a dialog for unblocking a number should be shown. */
  public static void notifyShowDialogToUnblockNumber(
      Context context, BlockReportSpamDialogInfo blockReportSpamDialogInfo) {
    LogUtil.enterBlock("ShowBlockReportSpamDialogNotifier.notifyShowDialogToUnblockNumber");

    Intent intent = new Intent();
    intent.setAction(ShowBlockReportSpamDialogReceiver.ACTION_SHOW_DIALOG_TO_UNBLOCK_NUMBER);
    ProtoParsers.put(
        intent, ShowBlockReportSpamDialogReceiver.EXTRA_DIALOG_INFO, blockReportSpamDialogInfo);

    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
  }
}
