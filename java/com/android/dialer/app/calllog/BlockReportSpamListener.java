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

package com.android.dialer.app.calllog;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.DialogFragmentForReportingNotSpam;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.DialogFragmentForUnblockingNumberAndReportingAsNotSpam;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.ContactSource;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ReportingLocation;
import com.android.dialer.spam.Spam;
import com.android.dialer.spam.SpamComponent;
import com.android.dialer.spam.SpamSettings;
import com.android.dialer.spam.promo.SpamBlockingPromoHelper;

/** Listener to show dialogs for block and report spam actions. */
public class BlockReportSpamListener implements CallLogListItemViewHolder.OnClickListener {

  private final Context context;
  private final View rootView;
  private final FragmentManager fragmentManager;
  private final RecyclerView.Adapter adapter;
  private final FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler;
  private final Spam spam;
  private final SpamSettings spamSettings;
  private final SpamBlockingPromoHelper spamBlockingPromoHelper;

  public BlockReportSpamListener(
      Context context,
      View rootView,
      FragmentManager fragmentManager,
      RecyclerView.Adapter adapter,
      FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler) {
    this.context = context;
    this.rootView = rootView;
    this.fragmentManager = fragmentManager;
    this.adapter = adapter;
    this.filteredNumberAsyncQueryHandler = filteredNumberAsyncQueryHandler;
    spam = SpamComponent.get(context).spam();
    spamSettings = SpamComponent.get(context).spamSettings();
    spamBlockingPromoHelper = new SpamBlockingPromoHelper(context, spamSettings);
  }

  @Override
  public void onBlockReportSpam(
      String displayNumber,
      final String number,
      final String countryIso,
      final int callType,
      @NonNull final ContactSource.Type contactSourceType) {
    BlockReportSpamDialogs.DialogFragmentForBlockingNumberAndOptionallyReportingAsSpam.newInstance(
            displayNumber,
            spamSettings.isDialogReportSpamCheckedByDefault(),
            isSpamChecked -> {
              LogUtil.i("BlockReportSpamListener.onBlockReportSpam", "onClick");
              if (isSpamChecked && spamSettings.isSpamEnabled()) {
                Logger.get(context)
                    .logImpression(
                        DialerImpression.Type
                            .REPORT_CALL_AS_SPAM_VIA_CALL_LOG_BLOCK_REPORT_SPAM_SENT_VIA_BLOCK_NUMBER_DIALOG);
                spam.reportSpamFromCallHistory(
                    number,
                    countryIso,
                    callType,
                    ReportingLocation.Type.CALL_LOG_HISTORY,
                    contactSourceType);
              }
              filteredNumberAsyncQueryHandler.blockNumber(
                  uri -> {
                    Logger.get(context)
                        .logImpression(DialerImpression.Type.USER_ACTION_BLOCKED_NUMBER);
                    adapter.notifyDataSetChanged();
                  },
                  number,
                  countryIso);

              if (isSpamChecked) {
                showSpamBlockingPromoDialog();
              }
            },
            null)
        .show(fragmentManager, BlockReportSpamDialogs.BLOCK_REPORT_SPAM_DIALOG_TAG);
  }

  @Override
  public void onBlock(
      String displayNumber,
      final String number,
      final String countryIso,
      final int callType,
      @NonNull final ContactSource.Type contactSourceType) {
    BlockReportSpamDialogs.DialogFragmentForBlockingNumberAndReportingAsSpam.newInstance(
            displayNumber,
            spamSettings.isSpamEnabled(),
            () -> {
              LogUtil.i("BlockReportSpamListener.onBlock", "onClick");
              if (spamSettings.isSpamEnabled()) {
                Logger.get(context)
                    .logImpression(
                        DialerImpression.Type
                            .DIALOG_ACTION_CONFIRM_NUMBER_SPAM_INDIRECTLY_VIA_BLOCK_NUMBER);
                spam.reportSpamFromCallHistory(
                    number,
                    countryIso,
                    callType,
                    ReportingLocation.Type.CALL_LOG_HISTORY,
                    contactSourceType);
              }
              filteredNumberAsyncQueryHandler.blockNumber(
                  uri -> {
                    Logger.get(context)
                        .logImpression(DialerImpression.Type.USER_ACTION_BLOCKED_NUMBER);
                    adapter.notifyDataSetChanged();
                  },
                  number,
                  countryIso);
              showSpamBlockingPromoDialog();
            },
            null)
        .show(fragmentManager, BlockReportSpamDialogs.BLOCK_DIALOG_TAG);
  }

  @Override
  public void onUnblock(
      String displayNumber,
      final String number,
      final String countryIso,
      final int callType,
      final ContactSource.Type contactSourceType,
      final boolean isSpam,
      final Integer blockId) {
    DialogFragmentForUnblockingNumberAndReportingAsNotSpam.newInstance(
            displayNumber,
            isSpam,
            () -> {
              LogUtil.i("BlockReportSpamListener.onUnblock", "onClick");
              if (isSpam && spamSettings.isSpamEnabled()) {
                Logger.get(context)
                    .logImpression(DialerImpression.Type.REPORT_AS_NOT_SPAM_VIA_UNBLOCK_NUMBER);
                spam.reportNotSpamFromCallHistory(
                    number,
                    countryIso,
                    callType,
                    ReportingLocation.Type.CALL_LOG_HISTORY,
                    contactSourceType);
              }
              filteredNumberAsyncQueryHandler.unblock(
                  (rows, values) -> {
                    Logger.get(context)
                        .logImpression(DialerImpression.Type.USER_ACTION_UNBLOCKED_NUMBER);
                    adapter.notifyDataSetChanged();
                  },
                  blockId);
            },
            null)
        .show(fragmentManager, BlockReportSpamDialogs.UNBLOCK_DIALOG_TAG);
  }

  @Override
  public void onReportNotSpam(
      String displayNumber,
      final String number,
      final String countryIso,
      final int callType,
      final ContactSource.Type contactSourceType) {
    DialogFragmentForReportingNotSpam.newInstance(
            displayNumber,
            () -> {
              LogUtil.i("BlockReportSpamListener.onReportNotSpam", "onClick");
              if (spamSettings.isSpamEnabled()) {
                Logger.get(context)
                    .logImpression(DialerImpression.Type.DIALOG_ACTION_CONFIRM_NUMBER_NOT_SPAM);
                spam.reportNotSpamFromCallHistory(
                    number,
                    countryIso,
                    callType,
                    ReportingLocation.Type.CALL_LOG_HISTORY,
                    contactSourceType);
              }
              adapter.notifyDataSetChanged();
            },
            null)
        .show(fragmentManager, BlockReportSpamDialogs.NOT_SPAM_DIALOG_TAG);
  }

  private void showSpamBlockingPromoDialog() {
    if (!spamBlockingPromoHelper.shouldShowSpamBlockingPromo()) {
      return;
    }

    Logger.get(context).logImpression(DialerImpression.Type.SPAM_BLOCKING_CALL_LOG_PROMO_SHOWN);
    spamBlockingPromoHelper.showSpamBlockingPromoDialog(
        fragmentManager,
        () -> {
          Logger.get(context)
              .logImpression(DialerImpression.Type.SPAM_BLOCKING_ENABLED_THROUGH_CALL_LOG_PROMO);
          spamSettings.modifySpamBlockingSetting(
              true,
              success -> {
                if (!success) {
                  Logger.get(context)
                      .logImpression(
                          DialerImpression.Type
                              .SPAM_BLOCKING_MODIFY_FAILURE_THROUGH_CALL_LOG_PROMO);
                }
                spamBlockingPromoHelper.showModifySettingOnCompleteSnackbar(rootView, success);
              });
        },
        null /* onDismissListener */);
  }
}
