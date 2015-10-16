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

import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.dialer.R;
import com.android.dialer.database.FilteredNumberContract;
import com.android.dialer.filterednumber.FilteredNumbersUtil.CheckForSendToVoicemailContactListener;
import com.android.dialer.filterednumber.FilteredNumbersUtil.ImportSendToVoicemailContactsListener;

public class BlockedNumberFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<Cursor>, View.OnClickListener {

    private BlockedNumberAdapter mAdapter;

    private View mImportSettings;
    private View mImportButton;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mAdapter == null) {
            mAdapter = new BlockedNumberAdapter(getContext());
        }
        setListAdapter(mAdapter);

        getActivity().findViewById(R.id.add_number_button).setOnClickListener(this);

        mImportSettings = getActivity().findViewById(R.id.import_settings);
        mImportButton = getActivity().findViewById(R.id.import_button);
        mImportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FilteredNumbersUtil.importSendToVoicemailContacts(
                        getActivity(), new ImportSendToVoicemailContactsListener() {
                            @Override
                            public void onImportComplete() {
                                mImportSettings.setVisibility(View.GONE);
                            }
                        });
            }
        });
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
    public void onResume() {
        super.onResume();

        FilteredNumbersUtil.checkForSendToVoicemailContact(
                getActivity(), new CheckForSendToVoicemailContactListener() {
                    @Override
                    public void onComplete(boolean hasSendToVoicemailContact) {
                        final int visibility = hasSendToVoicemailContact ? View.VISIBLE : View.GONE;
                        mImportSettings.setVisibility(visibility);
                    }
                });
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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
        ManageBlockedNumbersActivity manageBlockedNumbersActivity =
                (ManageBlockedNumbersActivity) getActivity();
        if (manageBlockedNumbersActivity != null && v.getId() == R.id.add_number_button) {
            manageBlockedNumbersActivity.enterSearchUi();
        }
    }
}
