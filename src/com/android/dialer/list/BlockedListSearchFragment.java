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
package com.android.dialer.list;

import android.content.ContentUris;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Toast;

import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.dialog.IndeterminateProgressDialog;
import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.ContactListItemView;
import com.android.dialer.R;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler.OnCheckBlockedListener;
import com.android.dialer.filterednumber.FilterNumberDialogFragment;
import com.android.dialer.filterednumber.ManageBlockedNumbersActivity;
import com.android.dialer.widget.SearchEditTextLayout;

public class BlockedListSearchFragment extends RegularSearchFragment
        implements FilterNumberDialogFragment.Callback {
    private static final String TAG = BlockedListSearchFragment.class.getSimpleName();

    private FilteredNumberAsyncQueryHandler mFilteredNumberAsyncQueryHandler;

    private EditText mSearchView;

    private final TextWatcher mPhoneSearchQueryTextListener = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            setQueryString(s.toString(), false);
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };

    private final SearchEditTextLayout.Callback mSearchLayoutCallback =
            new SearchEditTextLayout.Callback() {
                @Override
                public void onBackButtonClicked() {
                    getActivity().onBackPressed();
                }

                @Override
                public void onSearchViewClicked() {
                }
            };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show list of all phone numbers when search query is empty.
        setShowEmptyListForNullQuery(false);

        mFilteredNumberAsyncQueryHandler = new FilteredNumberAsyncQueryHandler(
                getContext().getContentResolver());
    }

    @Override
    public void onResume() {
        super.onResume();

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setCustomView(R.layout.search_edittext);
        actionBar.setBackgroundDrawable(null);
        actionBar.setElevation(0);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setDisplayShowHomeEnabled(false);

        final SearchEditTextLayout searchEditTextLayout = (SearchEditTextLayout) actionBar
                .getCustomView().findViewById(R.id.search_view_container);
        searchEditTextLayout.expand(false, true);
        searchEditTextLayout.setCallback(mSearchLayoutCallback);

        mSearchView = (EditText) searchEditTextLayout.findViewById(R.id.search_view);
        mSearchView.addTextChangedListener(mPhoneSearchQueryTextListener);
        mSearchView.setHint(R.string.block_number_search_hint);

        // TODO: Don't set custom text size; use default search text size.
        mSearchView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.blocked_number_search_text_size));
    }

    @Override
    protected ContactEntryListAdapter createListAdapter() {
        BlockedListSearchAdapter adapter = new BlockedListSearchAdapter(getActivity());
        adapter.setDisplayPhotos(true);
        adapter.setUseCallableUri(usesCallableUri());
        return adapter;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        super.onItemClick(parent, view, position, id);
        final int adapterPosition = position - getListView().getHeaderViewsCount();
        final BlockedListSearchAdapter adapter = (BlockedListSearchAdapter) getAdapter();
        final int shortcutType = adapter.getShortcutTypeFromPosition(adapterPosition);
        final Integer blockId = (Integer) view.getTag(R.id.block_id);
        final String number;
        switch (shortcutType) {
            case DialerPhoneNumberListAdapter.SHORTCUT_INVALID:
                // Handles click on a search result, either contact or nearby places result.
                number = adapter.getPhoneNumber(adapterPosition);
                blockContactNumber(adapter, (ContactListItemView) view, number, blockId);
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_BLOCK_NUMBER:
                // Handles click on 'Block number' shortcut to add the user query as a number.
                number = adapter.getQueryString();
                blockNumber(number);
                break;
            default:
                Log.w(TAG, "Ignoring unsupported shortcut type: " + shortcutType);
                break;
        }
    }

    @Override
    protected void onItemClick(int position, long id) {
        // Prevent SearchFragment.onItemClicked from being called.
    }

    private void blockNumber(final String number) {
        final String countryIso = GeoUtil.getCurrentCountryIso(getContext());
        final OnCheckBlockedListener onCheckListener = new OnCheckBlockedListener() {
            @Override
            public void onCheckComplete(Integer id) {
                if (id == null) {
                    FilterNumberDialogFragment.show(
                            id,
                            null,
                            number,
                            countryIso,
                            number,
                            R.id.blocked_numbers_activity_container,
                            getFragmentManager(),
                            null /* callback */);
                } else {
                    Toast.makeText(getContext(), getString(R.string.alreadyBlocked, number),
                            Toast.LENGTH_SHORT).show();
                }
            }
        };
        boolean failed = mFilteredNumberAsyncQueryHandler.startBlockedQuery(
                onCheckListener, null, number, countryIso);
        if (failed) {
            Toast.makeText(getContext(), getString(R.string.invalidNumber, number),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onChangeFilteredNumberSuccess() {
        ManageBlockedNumbersActivity activity = (ManageBlockedNumbersActivity) getActivity();
        if (activity == null) {
            return;
        }

        activity.showManagementUi();
    }

    @Override
    public void onChangeFilteredNumberUndo() {
        getAdapter().notifyDataSetChanged();
    }

    private void blockContactNumber(
            final BlockedListSearchAdapter adapter,
            final ContactListItemView view,
            final String number,
            final Integer blockId) {
        if (blockId != null) {
            Toast.makeText(getContext(), getString(R.string.alreadyBlocked, number),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        FilterNumberDialogFragment.show(
                blockId,
                null,
                number,
                GeoUtil.getCurrentCountryIso(getContext()),
                number,
                R.id.blocked_numbers_activity_container,
                getFragmentManager(),
                this);
    }
}
