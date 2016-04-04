package com.android.dialer.calllog;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.CheckBox;

import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.service.ExtendedCallInfoService;
import com.android.dialer.R;

/**
 * Listener to show dialogs for block and report spam actions.
 */
public class BlockReportSpamListener implements CallLogListItemViewHolder.OnClickListener {

    private final Context mContext;
    private final RecyclerView.Adapter mAdapter;
    private final ExtendedCallInfoService mExtendedCallInfoService;
    private final FilteredNumberAsyncQueryHandler mFilteredNumberAsyncQueryHandler;

    public BlockReportSpamListener(Context context, RecyclerView.Adapter adapter,
                                   ExtendedCallInfoService extendedCallInfoService,
                                   FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler) {
        mContext = context;
        mAdapter = adapter;
        mExtendedCallInfoService = extendedCallInfoService;
        mFilteredNumberAsyncQueryHandler = filteredNumberAsyncQueryHandler;
    }

    @Override
    public void onBlockReportSpam(String displayNumber, final String number,
                                  final String countryIso, final int callType) {
        final View dialogView = View.inflate(mContext, R.layout.block_report_spam_dialog, null);

        AlertDialog.Builder alertDialogBuilder = createDialogBuilder();
        alertDialogBuilder
                .setView(dialogView)
                .setTitle(mContext.getString(
                        R.string.block_report_number_alert_title, displayNumber))
                .setPositiveButton(mContext.getString(R.string.block_number_ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                CheckBox isSpamCheckbox = (CheckBox) dialogView
                                        .findViewById(R.id.report_number_as_spam_action);
                                if (isSpamCheckbox.isChecked()) {
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
                        });
        alertDialogBuilder.show();
    }

    @Override
    public void onBlock(String displayNumber, final String number, final String countryIso,
                        final int callType) {
        AlertDialog.Builder alertDialogBuilder = createDialogBuilder();
        alertDialogBuilder
                .setTitle(mContext.getString(
                        R.string.block_report_number_alert_title, displayNumber))
                .setMessage(R.string.block_number_alert_details)
                .setPositiveButton(mContext.getString(R.string.block_number_ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
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
                        });
        alertDialogBuilder.show();
    }

    @Override
    public void onUnblock(String displayNumber, final String number, final String countryIso,
                          final Integer blockId, final boolean isSpam, final int callType) {
        AlertDialog.Builder alertDialogBuilder = createDialogBuilder();
        if (isSpam) {
            alertDialogBuilder.setMessage(R.string.unblock_number_alert_details);
        }
        alertDialogBuilder
                .setTitle(mContext.getString(
                        R.string.unblock_report_number_alert_title, displayNumber))
                .setPositiveButton(R.string.unblock_number_ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
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
                        });
        alertDialogBuilder.show();
    }

    @Override
    public void onReportNotSpam(String displayNumber, final String number, final String countryIso,
                                final int callType) {
        AlertDialog.Builder alertDialogBuilder = createDialogBuilder();
        alertDialogBuilder
                .setTitle(mContext.getString(
                        R.string.report_not_spam_alert_title, displayNumber))
                .setMessage(R.string.report_not_spam_alert_details)
                .setPositiveButton(R.string.report_not_spam_alert_button,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mExtendedCallInfoService.reportNotSpam(
                                        number, countryIso, callType);
                                mAdapter.notifyDataSetChanged();
                            }
                        });
        alertDialogBuilder.show();
    }

    private AlertDialog.Builder createDialogBuilder() {
        return new AlertDialog.Builder(mContext)
                .setCancelable(true)
                .setNegativeButton(mContext.getString(android.R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
    }
}
