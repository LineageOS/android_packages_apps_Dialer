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
import android.app.FragmentManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import com.android.dialer.R;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler.OnBlockNumberListener;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler.OnUnblockNumberListener;

public class FilterNumberDialogFragment extends DialogFragment {

    /**
     * Use a callback interface to update UI after success/undo. Favor this approach over other
     * more standard paradigms because of the variety of scenarios in which the DialogFragment
     * can be invoked (by an Activity, by a fragment, by an adapter, by an adapter list item).
     * Because of this, we do NOT support retaining state on rotation, and will dismiss the dialog
     * upon rotation instead.
     */
    public interface Callback {
        public void onChangeFilteredNumberSuccess();
        public void onChangeFilteredNumberUndo();
    }

    private static final String BLOCK_DIALOG_FRAGMENT = "blockUnblockNumberDialog";

    private static final String ARG_BLOCK_ID = "argBlockId";
    private static final String ARG_NORMALIZED_NUMBER = "argNormalizedNumber";
    private static final String ARG_NUMBER = "argNumber";
    private static final String ARG_COUNTRY_ISO = "argCountryIso";
    private static final String ARG_DISPLAY_NUMBER = "argDisplayNumber";
    private static final String ARG_PARENT_VIEW_ID = "parentViewId";

    private String mDisplayNumber;
    private String mNormalizedNumber;

    private FilteredNumberAsyncQueryHandler mHandler;
    private View mParentView;
    private Callback mCallback;

    public static void show(
            Integer blockId,
            String normalizedNumber,
            String number,
            String countryIso,
            String displayNumber,
            Integer parentViewId,
            FragmentManager fragmentManager,
            Callback callback) {
        final FilterNumberDialogFragment newFragment = FilterNumberDialogFragment.newInstance(
                blockId, normalizedNumber, number, countryIso, displayNumber, parentViewId);

        newFragment.setCallback(callback);
        newFragment.show(fragmentManager, FilterNumberDialogFragment.BLOCK_DIALOG_FRAGMENT);
    }

    private static FilterNumberDialogFragment newInstance(
            Integer blockId,
            String normalizedNumber,
            String number,
            String countryIso,
            String displayNumber,
            Integer parentViewId) {
        final FilterNumberDialogFragment fragment = new FilterNumberDialogFragment();
        final Bundle args = new Bundle();
        if (blockId != null) {
            args.putInt(ARG_BLOCK_ID, blockId.intValue());
        }
        if (parentViewId != null) {
            args.putInt(ARG_PARENT_VIEW_ID, parentViewId.intValue());
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

        mDisplayNumber = getArguments().getString(ARG_DISPLAY_NUMBER);
        if (TextUtils.isEmpty(mNormalizedNumber)) {
            String number = getArguments().getString(ARG_NUMBER);
            String countryIso = getArguments().getString(ARG_COUNTRY_ISO);
            mNormalizedNumber =
                    FilteredNumberAsyncQueryHandler.getNormalizedNumber(number, countryIso);
        }

        mHandler = new FilteredNumberAsyncQueryHandler(getContext().getContentResolver());
        mParentView = getActivity().findViewById(getArguments().getInt(ARG_PARENT_VIEW_ID));

        String message;
        String okText;
        if (isBlocked) {
            message = getString(R.string.unblockNumberConfirmation, mDisplayNumber);
            okText = getString(R.string.unblockNumberOk);
        } else {
            message = getString(R.string.blockNumberConfirmation, mDisplayNumber);
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

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        String number = getArguments().getString(ARG_NUMBER);
        if (TextUtils.isEmpty(mNormalizedNumber) ||
                !FilteredNumbersUtil.canBlockNumber(getActivity(), number)) {
            dismiss();
            Toast.makeText(getContext(), getString(R.string.invalidNumber, number),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPause() {
        // Dismiss on rotation.
        dismiss();
        mCallback = null;

        super.onPause();
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    private String getBlockedMessage() {
        return getString(R.string.snackbar_number_blocked, mDisplayNumber);
    }

    private String getUnblockedMessage() {
        return getString(R.string.snackbar_number_unblocked, mDisplayNumber);
    }

    private int getActionTextColor() {
        return getContext().getResources().getColor(R.color.dialer_snackbar_action_text_color);
    }

    private void blockNumber() {
        final String message = getBlockedMessage();
        final String undoMessage = getUnblockedMessage();
        final Callback callback = mCallback;
        final int actionTextColor = getActionTextColor();
        final Context context = getContext();

        final OnUnblockNumberListener onUndoListener = new OnUnblockNumberListener() {
            @Override
            public void onUnblockComplete(int rows, ContentValues values) {
                Snackbar.make(mParentView, undoMessage, Snackbar.LENGTH_LONG).show();
                if (callback != null) {
                    callback.onChangeFilteredNumberUndo();
                }
            }
        };

        final OnBlockNumberListener onBlockNumberListener = new OnBlockNumberListener() {
            @Override
            public void onBlockComplete(final Uri uri) {
                final View.OnClickListener undoListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // Delete the newly created row on 'undo'.
                        mHandler.unblock(onUndoListener, uri);
                    }
                };

                Snackbar.make(mParentView, message, Snackbar.LENGTH_LONG)
                        .setAction(R.string.block_number_undo, undoListener)
                        .setActionTextColor(actionTextColor)
                        .show();

                if (callback != null) {
                    callback.onChangeFilteredNumberSuccess();
                }

                if (context != null && FilteredNumbersUtil.hasRecentEmergencyCall(context)) {
                    FilteredNumbersUtil.maybeNotifyCallBlockingDisabled(context);
                }
            }
        };

        mHandler.blockNumber(
                onBlockNumberListener,
                getArguments().getString(ARG_NORMALIZED_NUMBER),
                getArguments().getString(ARG_NUMBER),
                getArguments().getString(ARG_COUNTRY_ISO));
    }

    private void unblockNumber() {
        final String message = getUnblockedMessage();
        final String undoMessage = getBlockedMessage();
        final Callback callback = mCallback;
        final int actionTextColor = getActionTextColor();

        final OnBlockNumberListener onUndoListener = new OnBlockNumberListener() {
            @Override
            public void onBlockComplete(final Uri uri) {
                Snackbar.make(mParentView, undoMessage, Snackbar.LENGTH_LONG).show();
                if (callback != null) {
                    callback.onChangeFilteredNumberUndo();
                }
            }
        };

        mHandler.unblock(new OnUnblockNumberListener() {
            @Override
            public void onUnblockComplete(int rows, final ContentValues values) {
                final View.OnClickListener undoListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // Re-insert the row on 'undo', with a new ID.
                        mHandler.blockNumber(onUndoListener, values);
                    }
                };

                Snackbar.make(mParentView, message, Snackbar.LENGTH_LONG)
                        .setAction(R.string.block_number_undo, undoListener)
                        .setActionTextColor(actionTextColor)
                        .show();

                if (callback != null) {
                    callback.onChangeFilteredNumberSuccess();
                }
            }
        }, getArguments().getInt(ARG_BLOCK_ID));
    }
}
