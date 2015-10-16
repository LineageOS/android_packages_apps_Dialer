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
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.dialer.R;
import com.android.dialer.database.FilteredNumberContract;

public class BlockedNumberFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<Cursor>, View.OnClickListener {

    private static class SendToVoicemailContactQuery {
        static final String[] PROJECTION = {
            Contacts._ID
        };

        static final String SELECT_SEND_TO_VOICEMAIL_TRUE = Contacts.SEND_TO_VOICEMAIL + "=1";
    }

    private BlockedNumberAdapter mAdapter;
    private View mImportSettings;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        getListView().addHeaderView(inflater.inflate(R.layout.blocked_number_header, null));
        if (mAdapter == null) {
            mAdapter = BlockedNumberAdapter.newBlockedNumberAdapter(
                    getContext(), getActivity().getFragmentManager());
        }
        setListAdapter(mAdapter);

        getActivity().findViewById(R.id.add_number_button).setOnClickListener(this);
        getListView().getEmptyView().findViewById(R.id.add_number_button).setOnClickListener(this);

        mImportSettings = getActivity().findViewById(R.id.importsettings);
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
        checkForSendToVoicemailContact();
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

    /**
     * Checks if there exists a contact with {@code Contacts.SEND_TO_VOICEMAIL} set to true,
     * and updates the visibility of the import settings buttons accordingly.
     */
    private void checkForSendToVoicemailContact() {
        final AsyncTask task = new AsyncTask<Object, Void, Boolean>() {
            @Override
            public Boolean doInBackground(Object[]  params) {
                if (getActivity() == null) {
                    return false;
                }

                final Cursor cursor = getActivity().getContentResolver().query(
                        Contacts.CONTENT_URI,
                        SendToVoicemailContactQuery.PROJECTION,
                        SendToVoicemailContactQuery.SELECT_SEND_TO_VOICEMAIL_TRUE,
                        null,
                        null);

                boolean hasSendToVoicemailContacts = false;
                if (cursor != null) {
                    try {
                        hasSendToVoicemailContacts = cursor.getCount() > 0;
                    } finally {
                        cursor.close();
                    }
                }

                return hasSendToVoicemailContacts;
            }

            @Override
            public void onPostExecute(Boolean hasSendToVoicemailContact) {
                final int visibility = hasSendToVoicemailContact ? View.VISIBLE : View.GONE;
                mImportSettings.setVisibility(visibility);
            }
        };
        task.execute();
    }
}
