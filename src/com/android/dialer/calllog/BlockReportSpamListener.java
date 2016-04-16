package com.android.dialer.calllog;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;

import com.android.dialer.util.BlockReportSpamDialogs;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.service.ExtendedCallInfoService;

/**
 * Listener to show dialogs for block and report spam actions.
 */
public class BlockReportSpamListener implements CallLogListItemViewHolder.OnClickListener {

    private final FragmentManager mFragmentManager;
    private final RecyclerView.Adapter mAdapter;
    private final ExtendedCallInfoService mExtendedCallInfoService;
    private final FilteredNumberAsyncQueryHandler mFilteredNumberAsyncQueryHandler;

    public BlockReportSpamListener(FragmentManager fragmentManager, RecyclerView.Adapter adapter,
                                   ExtendedCallInfoService extendedCallInfoService,
                                   FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler) {
        mFragmentManager = fragmentManager;
        mAdapter = adapter;
        mExtendedCallInfoService = extendedCallInfoService;
        mFilteredNumberAsyncQueryHandler = filteredNumberAsyncQueryHandler;
    }

    @Override
    public void onBlockReportSpam(String displayNumber, final String number,
                                  final String countryIso, final int callType) {
        BlockReportSpamDialogs.BlockReportSpamDialogFragment.newInstance(
                displayNumber,
                false,
                new BlockReportSpamDialogs.OnSpamDialogClickListener() {
                    @Override
                    public void onClick(boolean isSpamChecked) {
                        if (isSpamChecked) {
                            mExtendedCallInfoService.reportSpam(
                                    number, countryIso, callType);
                        }
                        mFilteredNumberAsyncQueryHandler.blockNumber(
                                new FilteredNumberAsyncQueryHandler.OnBlockNumberListener() {
                                    @Override
                                    public void onBlockComplete(Uri uri) {
                                        mAdapter.notifyDataSetChanged();
                                    }
                                },
                                number,
                                countryIso);
                    }
                }, null)
                .show(mFragmentManager, BlockReportSpamDialogs.BLOCK_REPORT_SPAM_DIALOG_TAG);
    }

    @Override
    public void onBlock(String displayNumber, final String number, final String countryIso,
                        final int callType) {
        BlockReportSpamDialogs.BlockDialogFragment.newInstance(displayNumber,
                new BlockReportSpamDialogs.OnConfirmListener() {
                    @Override
                    public void onClick() {
                        mExtendedCallInfoService.reportSpam(number, countryIso, callType);
                        mFilteredNumberAsyncQueryHandler.blockNumber(
                                new FilteredNumberAsyncQueryHandler.OnBlockNumberListener() {
                                    @Override
                                    public void onBlockComplete(Uri uri) {
                                        mAdapter.notifyDataSetChanged();
                                    }
                                },
                                number,
                                countryIso);
                    }
                }, null)
                .show(mFragmentManager, BlockReportSpamDialogs.BLOCK_DIALOG_TAG);
    }

    @Override
    public void onUnblock(String displayNumber, final String number, final String countryIso,
                          final Integer blockId, final boolean isSpam, final int callType) {
        BlockReportSpamDialogs.UnblockDialogFragment.newInstance(displayNumber, isSpam,
                new BlockReportSpamDialogs.OnConfirmListener() {
                    @Override
                    public void onClick() {
                        if (isSpam) {
                            mExtendedCallInfoService.reportNotSpam(
                                    number, countryIso, callType);
                        }
                        mFilteredNumberAsyncQueryHandler.unblock(
                                new FilteredNumberAsyncQueryHandler.OnUnblockNumberListener() {
                                    @Override
                                    public void onUnblockComplete(int rows, ContentValues values) {
                                        mAdapter.notifyDataSetChanged();
                                    }
                                },
                                blockId);
                    }
                }, null)
                .show(mFragmentManager, BlockReportSpamDialogs.UNBLOCK_DIALOG_TAG);
    }

    @Override
    public void onReportNotSpam(String displayNumber, final String number, final String countryIso,
                                final int callType) {
        BlockReportSpamDialogs.ReportNotSpamDialogFragment.newInstance(displayNumber,
                new BlockReportSpamDialogs.OnConfirmListener() {
                    @Override
                    public void onClick() {
                        mExtendedCallInfoService.reportNotSpam(
                                number, countryIso, callType);
                        mAdapter.notifyDataSetChanged();
                    }
                }, null)
                .show(mFragmentManager, BlockReportSpamDialogs.NOT_SPAM_DIALOG_TAG);
    }
}
