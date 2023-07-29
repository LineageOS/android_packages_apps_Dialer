/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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
import android.provider.BlockedNumberContract;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.DialogFragmentForReportingNotSpam;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.DialogFragmentForUnblockingNumberAndReportingAsNotSpam;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.ContactSource;

/** Listener to show dialogs for block and report spam actions. */
public class BlockReportSpamListener implements CallLogListItemViewHolder.OnClickListener {

  private final Context context;
  private final View rootView;
  private final FragmentManager fragmentManager;
  private final RecyclerView.Adapter adapter;
  private final FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler;

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
            false,
            isSpamChecked -> {
              LogUtil.i("BlockReportSpamListener.onBlockReportSpam", "onClick");
              filteredNumberAsyncQueryHandler.blockNumber(
                  uri -> adapter.notifyDataSetChanged(),
                  number);

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
            () -> {
              LogUtil.i("BlockReportSpamListener.onBlock", "onClick");
              filteredNumberAsyncQueryHandler.blockNumber(
                  uri -> adapter.notifyDataSetChanged(),
                  number);
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
      final boolean isSpam) {
    DialogFragmentForUnblockingNumberAndReportingAsNotSpam.newInstance(
            displayNumber,
            isSpam,
            () -> {
              LogUtil.i("BlockReportSpamListener.onUnblock", "onClick");
              if (BlockedNumberContract.unblock(context, number) > 0) {
                  adapter.notifyDataSetChanged();
              }
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
              adapter.notifyDataSetChanged();
            },
            null)
        .show(fragmentManager, BlockReportSpamDialogs.NOT_SPAM_DIALOG_TAG);
  }

  private void showSpamBlockingPromoDialog() {
  }
}
