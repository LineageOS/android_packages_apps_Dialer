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

import android.app.FragmentManager;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import com.android.dialer.blocking.BlockReportSpamDialogs;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.ContactSource;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ReportingLocation;
import com.android.dialer.spam.Spam;
import com.android.dialer.spam.SpamComponent;

/** Listener to show dialogs for block and report spam actions. */
public class BlockReportSpamListener implements CallLogListItemViewHolder.OnClickListener {

  private final Context context;
  private final FragmentManager fragmentManager;
  private final RecyclerView.Adapter adapter;
  private final FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler;
  private final Spam spam;

  public BlockReportSpamListener(
      Context context,
      FragmentManager fragmentManager,
      RecyclerView.Adapter adapter,
      FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler) {
    this.context = context;
    this.fragmentManager = fragmentManager;
    this.adapter = adapter;
    this.filteredNumberAsyncQueryHandler = filteredNumberAsyncQueryHandler;
    spam = SpamComponent.get(context).spam();
  }

  @Override
  public void onBlockReportSpam(
      String displayNumber,
      final String number,
      final String countryIso,
      final int callType,
      @NonNull final ContactSource.Type contactSourceType) {
    BlockReportSpamDialogs.BlockReportSpamDialogFragment.newInstance(
            displayNumber,
            spam.isDialogReportSpamCheckedByDefault(),
            isSpamChecked -> {
              LogUtil.i("BlockReportSpamListener.onBlockReportSpam", "onClick");
              if (isSpamChecked && spam.isSpamEnabled()) {
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
    BlockReportSpamDialogs.BlockDialogFragment.newInstance(
            displayNumber,
            spam.isSpamEnabled(),
            () -> {
              LogUtil.i("BlockReportSpamListener.onBlock", "onClick");
              if (spam.isSpamEnabled()) {
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
    BlockReportSpamDialogs.UnblockDialogFragment.newInstance(
            displayNumber,
            isSpam,
            () -> {
              LogUtil.i("BlockReportSpamListener.onUnblock", "onClick");
              if (isSpam && spam.isSpamEnabled()) {
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
    BlockReportSpamDialogs.ReportNotSpamDialogFragment.newInstance(
            displayNumber,
            () -> {
              LogUtil.i("BlockReportSpamListener.onReportNotSpam", "onClick");
              if (spam.isSpamEnabled()) {
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
}
