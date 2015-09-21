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
 * limitations under the License.
 */
package com.android.dialer.filterednumber;

import android.app.AlertDialog;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.dialog.IndeterminateProgressDialog;
import com.android.dialer.R;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler.OnCheckBlockedListener;
import com.android.dialer.database.FilteredNumberContract;

public class BlockedNumberFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<Cursor>, View.OnClickListener {

    private BlockedNumberAdapter mAdapter;
    private FilteredNumberAsyncQueryHandler mFilteredNumberAsyncQueryHandler;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        LayoutInflater inflater = LayoutInflater.from(getContext());
        getListView().addHeaderView(inflater.inflate(R.layout.blocked_number_header, null));
        mFilteredNumberAsyncQueryHandler =
                new FilteredNumberAsyncQueryHandler(getActivity().getContentResolver());
        if (mAdapter == null) {
            mAdapter = new BlockedNumberAdapter(getContext(), mFilteredNumberAsyncQueryHandler);
        }
        setListAdapter(mAdapter);
        getActivity().findViewById(R.id.add_number_button).setOnClickListener(this);
        getListView().getEmptyView().findViewById(R.id.add_number_button).setOnClickListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        setListAdapter(null);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.blocked_number_fragment, container, false);
        return view;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        final String[] projection = {
            FilteredNumberContract.FilteredNumberColumns._ID,
            FilteredNumberContract.FilteredNumberColumns.COUNTRY_ISO,
            FilteredNumberContract.FilteredNumberColumns.NUMBER,
            FilteredNumberContract.FilteredNumberColumns.NORMALIZED_NUMBER
        };
        final String selection = FilteredNumberContract.FilteredNumberColumns.TYPE
                + "=" + FilteredNumberContract.FilteredNumberTypes.BLOCKED_NUMBER;
        final CursorLoader cursorLoader = new CursorLoader(
                getContext(), FilteredNumberContract.FilteredNumber.CONTENT_URI, projection,
                selection, null, null);
        return cursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }

    @Override
    public void onClick(final View v) {
        final String countryIso = GeoUtil.getCurrentCountryIso(getContext());
        final EditText numberField = new EditText(getContext());
        final DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                final String number = numberField.getText().toString();
                final IndeterminateProgressDialog progressDialog =
                        IndeterminateProgressDialog.show(getFragmentManager(),
                                getString(R.string.checkingNumber, number), null, 1000);
                final String normalizedNumber =
                        FilteredNumberAsyncQueryHandler.getNormalizedNumber(number, countryIso);
                if (normalizedNumber == null) {
                    progressDialog.dismiss();
                    Toast.makeText(getContext(), getString(R.string.invalidNumber, number),
                            Toast.LENGTH_LONG).show();
                } else {
                    final OnCheckBlockedListener onCheckListener = new OnCheckBlockedListener() {
                        @Override
                        public void onCheckComplete(Integer id) {
                            progressDialog.dismiss();
                            if (id == null) {
                                FilterNumberDialogFragment newFragment =
                                        FilterNumberDialogFragment.newInstance(id, normalizedNumber,
                                                number, countryIso, number);
                                newFragment.setQueryHandler(mFilteredNumberAsyncQueryHandler);
                                newFragment.setParentView(v);
                                newFragment.show(getActivity().getFragmentManager(),
                                        FilterNumberDialogFragment.BLOCK_DIALOG_FRAGMENT);
                            } else {
                                Toast.makeText(getContext(),
                                        getString(R.string.alreadyBlocked, number),
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    };
                    mFilteredNumberAsyncQueryHandler.startBlockedQuery(
                            onCheckListener, normalizedNumber, number, countryIso);
                }
            }
        };
        new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.blockNumber))
                .setView(numberField)
                .setPositiveButton(getString(R.string.blockNumberOk), okListener)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}