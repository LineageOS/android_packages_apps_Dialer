/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.dialer.filterednumber;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.view.View;

import com.android.dialer.R;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;

public class FilterNumberDialogFragment extends DialogFragment {
    public static final String BLOCK_DIALOG_FRAGMENT = "blockUnblockNumberDialog";

    private static final String ARG_BLOCK_ID = "argBlockId";
    private static final String ARG_NORMALIZED_NUMBER = "argNormalizedNumber";
    private static final String ARG_NUMBER = "argNumber";
    private static final String ARG_COUNTRY_ISO = "argCountryIso";
    private static final String ARG_DISPLAY_NUMBER = "argDisplayNumber";

    private FilteredNumberAsyncQueryHandler mHandler;

    public void setQueryHandler (FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler) {
        mHandler = filteredNumberAsyncQueryHandler;
    }

    public static FilterNumberDialogFragment newInstance(Integer blockId, String normalizedNumber,
        String number, String countryIso, String displayNumber) {
        final FilterNumberDialogFragment fragment = new FilterNumberDialogFragment();
        final Bundle args = new Bundle();
        if (blockId != null) {
            args.putInt(ARG_BLOCK_ID, blockId.intValue());
        }
        args.putString(ARG_NORMALIZED_NUMBER, normalizedNumber);
        args.putString(ARG_NUMBER, number);
        args.putString(ARG_COUNTRY_ISO, countryIso);
        args.putString(ARG_DISPLAY_NUMBER, displayNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        final boolean isBlocked = getArguments().containsKey(ARG_BLOCK_ID);
        final String displayNumber = getArguments().getString(ARG_DISPLAY_NUMBER);

        String message;
        String okText;
        if (isBlocked) {
            message = getString(R.string.unblockNumberConfirmation, displayNumber);
            okText = getString(R.string.unblockNumberOk);
        } else {
            message = getString(R.string.blockNumberConfirmation, displayNumber);
            okText = getString(R.string.blockNumberOk);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setMessage(message)
                .setPositiveButton(okText, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (isBlocked) {
                            unblockNumber();
                        } else {
                            blockNumber();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);
        return builder.create();
    }

    public void blockNumber() {
        final View view = getActivity().findViewById(R.id.floating_action_button_container);
        final String displayNumber = getArguments().getString(ARG_DISPLAY_NUMBER);
        final String message = getString(R.string.snackbar_number_blocked, displayNumber);
        final String undoMessage = getString(R.string.snackbar_number_unblocked, displayNumber);
        final FilteredNumberAsyncQueryHandler.OnUnblockNumberListener undoListener =
                new FilteredNumberAsyncQueryHandler.OnUnblockNumberListener() {
                    @Override
                    public void onUnblockComplete(int rows, ContentValues values) {
                        Snackbar.make(view, undoMessage, Snackbar.LENGTH_LONG).show();
                    }
                };

        mHandler.blockNumber(
                new FilteredNumberAsyncQueryHandler.OnBlockNumberListener() {
                    @Override
                    public void onBlockComplete(final Uri uri) {
                        Snackbar.make(view, message, Snackbar.LENGTH_LONG)
                                .setAction(R.string.block_number_undo,
                                        // Delete the newly created row on 'undo'.
                                        new View.OnClickListener() {
                                            @Override
                                            public void onClick(View view) {
                                                mHandler.unblock(undoListener, uri);
                                            }
                                        })
                                .show();
                    }
                }, getArguments().getString(ARG_NORMALIZED_NUMBER),
                getArguments().getString(ARG_NUMBER), getArguments().getString(ARG_COUNTRY_ISO));
    }

    public void unblockNumber() {
        final View view = getActivity().findViewById(R.id.floating_action_button_container);
        final String displayNumber = getArguments().getString(ARG_DISPLAY_NUMBER);
        final String message = getString(R.string.snackbar_number_unblocked, displayNumber);
        final String undoMessage = getString(R.string.snackbar_number_blocked, displayNumber);
        final FilteredNumberAsyncQueryHandler.OnBlockNumberListener undoListener =
                new FilteredNumberAsyncQueryHandler.OnBlockNumberListener() {
                    @Override
                    public void onBlockComplete(final Uri uri) {
                        Snackbar.make(view, undoMessage, Snackbar.LENGTH_LONG).show();
                    }
                };
        mHandler.unblock(
                new FilteredNumberAsyncQueryHandler.OnUnblockNumberListener() {
                    @Override
                    public void onUnblockComplete(int rows, final ContentValues values) {
                        Snackbar.make(view, message, Snackbar.LENGTH_LONG)
                                .setAction(R.string.block_number_undo,
                                        new View.OnClickListener() {
                                            // Re-insert the row on 'undo', with a new ID.
                                            @Override
                                            public void onClick(View view) {
                                                mHandler.blockNumber(undoListener, values);
                                            }
                                        })
                                .show();
                    }
                }, getArguments().getInt(ARG_BLOCK_ID));
    }
}