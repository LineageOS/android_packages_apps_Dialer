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
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.android.dialer.R;
import com.android.dialer.calllog.CallLogQueryHandler;
import com.android.dialer.database.FilteredNumberContract;
import com.android.dialer.filterednumber.FilteredNumbersUtil.CheckForSendToVoicemailContactListener;
import com.android.dialer.filterednumber.FilteredNumbersUtil.ImportSendToVoicemailContactsListener;
import com.android.dialer.voicemail.VoicemailStatusHelper;
import com.android.dialer.voicemail.VoicemailStatusHelperImpl;

public class BlockedNumbersFragment extends ListFragment
        implements LoaderManager.LoaderCallbacks<Cursor>, View.OnClickListener,
                CallLogQueryHandler.Listener {

    private BlockedNumberAdapter mAdapter;
    private CallLogQueryHandler mCallLogQueryHandler;
    private VoicemailStatusHelper mVoicemailStatusHelper;

    private Switch mHideSettingSwitch;
    private View mImportSettings;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mAdapter == null) {
            mAdapter = BlockedNumberAdapter.newBlockedNumberAdapter(
                    getContext(), getActivity().getFragmentManager());
        }
        setListAdapter(mAdapter);

        mCallLogQueryHandler
                = new CallLogQueryHandler(getContext(), getContext().getContentResolver(), this);
        mVoicemailStatusHelper = new VoicemailStatusHelperImpl();

        mHideSettingSwitch = (Switch) getActivity().findViewById(R.id.hide_blocked_calls_switch);
        mHideSettingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                FilteredNumbersUtil.setShouldHideBlockedCalls(getActivity(), isChecked);
            }
        });

        mImportSettings = getActivity().findViewById(R.id.import_settings);

        getActivity().findViewById(R.id.import_button).setOnClickListener(this);;
        getActivity().findViewById(R.id.view_numbers_button).setOnClickListener(this);
        getActivity().findViewById(R.id.add_number_button).setOnClickListener(this);
    }

    @Override
    public void onDestroy() {
        setListAdapter(null);
        super.onDestroy();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onResume() {
        super.onResume();

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        ColorDrawable backgroundDrawable =
                new ColorDrawable(getActivity().getColor(R.color.dialer_theme_color));
        actionBar.setBackgroundDrawable(backgroundDrawable);
        actionBar.setElevation(getResources().getDimensionPixelSize(R.dimen.action_bar_elevation));
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(R.string.manage_blocked_numbers_label);

        FilteredNumbersUtil.checkForSendToVoicemailContact(
                getActivity(), new CheckForSendToVoicemailContactListener() {
                    @Override
                    public void onComplete(boolean hasSendToVoicemailContact) {
                        final int visibility = hasSendToVoicemailContact ? View.VISIBLE : View.GONE;
                        mImportSettings.setVisibility(visibility);
                    }
                });

        mHideSettingSwitch.setChecked(FilteredNumbersUtil.shouldHideBlockedCalls(getActivity()));
        mCallLogQueryHandler.fetchVoicemailStatus();
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
    public void onClick(final View view) {
        ManageBlockedNumbersActivity manageBlockedNumbersActivity =
                (ManageBlockedNumbersActivity) getActivity();
        if (manageBlockedNumbersActivity == null) {
            return;
        }

        switch (view.getId()) {
            case R.id.add_number_button:
                manageBlockedNumbersActivity.enterSearchUi();
                break;
            case R.id.view_numbers_button:
                manageBlockedNumbersActivity.showNumbersToImportPreviewUi();
                break;
            case R.id.import_button:
                FilteredNumbersUtil.importSendToVoicemailContacts(manageBlockedNumbersActivity,
                        new ImportSendToVoicemailContactsListener() {
                            @Override
                            public void onImportComplete() {
                                mImportSettings.setVisibility(View.GONE);
                            }
                        });
                break;
        }
    }

    @Override
    public void onVoicemailStatusFetched(Cursor cursor) {
        final View hideSetting = getActivity().findViewById(R.id.hide_blocked_calls_setting);
        if (cursor == null) {
            hideSetting.setVisibility(View.GONE);
            return;
        }

        final boolean hasVisualVoicemailSource =
                mVoicemailStatusHelper.getNumberActivityVoicemailSources(cursor) > 0;
        if (hasVisualVoicemailSource) {
            hideSetting.setVisibility(View.VISIBLE);
        } else {
            hideSetting.setVisibility(View.GONE);
        }
    }

    @Override
    public void onVoicemailUnreadCountFetched(Cursor cursor) {
        // Do nothing.
    }

    @Override
    public boolean onCallsFetched(Cursor cursor) {
        // Do nothing.
        return false;
    }
}
