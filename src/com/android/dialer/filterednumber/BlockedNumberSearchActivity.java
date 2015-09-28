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

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.Toast;

import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.dialog.IndeterminateProgressDialog;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.dialer.R;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.list.OnListFragmentScrolledListener;
import com.android.dialer.list.BlockedListSearchFragment;
import com.android.dialer.list.SearchFragment;
import com.android.dialer.widget.SearchEditTextLayout;

public class BlockedNumberSearchActivity extends AppCompatActivity
        implements SearchFragment.HostInterface, OnPhoneNumberPickerActionListener {
    private static final String TAG = "BlockedNumberSearch";
    private static final String TAG_BLOCKED_SEARCH_FRAGMENT = "blocked_search";

    private FilteredNumberAsyncQueryHandler mFilteredNumberAsyncQueryHandler;
    private SearchFragment mSearchFragment;
    private EditText mSearchView;
    private ActionBar mActionBar;
    private String mSearchQuery;

    private final TextWatcher mPhoneSearchQueryTextListener = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            final String newText = s.toString();
            if (newText.equals(mSearchQuery)) {
                return;
            }
            mSearchQuery = newText;
            mSearchFragment.setQueryString(mSearchQuery, false);
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFilteredNumberAsyncQueryHandler =
                new FilteredNumberAsyncQueryHandler(getContentResolver());

        setContentView(R.layout.search_activity);

        mActionBar = getSupportActionBar();
        mActionBar.setCustomView(R.layout.search_edittext);
        mActionBar.setBackgroundDrawable(null);
        mActionBar.setDisplayShowCustomEnabled(true);
        mActionBar.setDisplayHomeAsUpEnabled(false);
        mActionBar.setDisplayShowHomeEnabled(false);

        final SearchEditTextLayout searchEditTextLayout = (SearchEditTextLayout) mActionBar
                .getCustomView().findViewById(R.id.search_view_container);
        searchEditTextLayout.expand(false, true);
        searchEditTextLayout.setCallback(new SearchEditTextLayout.Callback() {
            @Override
            public void onBackButtonClicked() {
                onBackPressed();
            }

            @Override
            public void onSearchViewClicked() {
            }
        });

        mSearchView = (EditText) searchEditTextLayout.findViewById(R.id.search_view);
        mSearchView.addTextChangedListener(mPhoneSearchQueryTextListener);
        mSearchView.setHint(R.string.block_number_search_hint);
        mSearchView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.blocked_number_search_text_size));

        enterSearchUi();
    }

    private void enterSearchUi() {
        if (mSearchFragment != null) {
            return;
        }
        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        SearchFragment fragment = (SearchFragment) getFragmentManager()
                .findFragmentByTag(TAG_BLOCKED_SEARCH_FRAGMENT);
        if (fragment == null) {
            fragment = new BlockedListSearchFragment();
            transaction.add(R.id.search_activity_container, fragment, TAG_BLOCKED_SEARCH_FRAGMENT);
        } else {
            transaction.show(fragment);
        }
        fragment.setHasOptionsMenu(false);
        fragment.setShowEmptyListForNullQuery(true);
        transaction.commit();
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof BlockedListSearchFragment) {
            mSearchFragment = (BlockedListSearchFragment) fragment;
            mSearchFragment.setOnPhoneNumberPickerActionListener(this);
        }
    }

    @Override
    public void onPickPhoneNumberAction(Uri dataUri, int callInitiationType) {
        Log.w(TAG, "onPickPhoneNumberAction unsupported, ignoring.");
    }

    @Override
    public void onCallNumberDirectly(
            String phoneNumber, boolean isVideoCall, int callInitiationType) {
        blockNumber(phoneNumber);
    }

    @Override
    public void onShortcutIntentCreated(Intent intent) {
        Log.w(TAG, "Unsupported intent has come (" + intent + "). Ignoring.");
    }

    @Override
    public void onHomeInActionBarSelected() {
    }

    private void blockNumber(final String number) {
        final String countryIso = GeoUtil.getCurrentCountryIso(BlockedNumberSearchActivity.this);
        final IndeterminateProgressDialog progressDialog =
                IndeterminateProgressDialog.show(getFragmentManager(),
                        getString(R.string.checkingNumber, number), null, 500);
        final String normalizedNumber =
                FilteredNumberAsyncQueryHandler.getNormalizedNumber(number, countryIso);
        if (normalizedNumber == null) {
            progressDialog.dismiss();
            Toast.makeText(
                    BlockedNumberSearchActivity.this, getString(R.string.invalidNumber, number),
                    Toast.LENGTH_SHORT).show();
        } else {
            final FilteredNumberAsyncQueryHandler.OnCheckBlockedListener onCheckListener =
                    new FilteredNumberAsyncQueryHandler.OnCheckBlockedListener() {
                        @Override
                        public void onCheckComplete(Integer id) {
                            progressDialog.dismiss();
                            if (id == null) {
                                final FilterNumberDialogFragment newFragment =
                                        FilterNumberDialogFragment.newInstance(id, normalizedNumber,
                                                number, countryIso, number);
                                newFragment.setQueryHandler(mFilteredNumberAsyncQueryHandler);
                                newFragment.setParentView(
                                        findViewById(R.id.search_activity_container));
                                newFragment.show(getFragmentManager(),
                                        FilterNumberDialogFragment.BLOCK_DIALOG_FRAGMENT);
                            } else {
                                Toast.makeText(BlockedNumberSearchActivity.this,
                                        getString(R.string.alreadyBlocked, number),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    };
            mFilteredNumberAsyncQueryHandler.startBlockedQuery(
                    onCheckListener, normalizedNumber, number, countryIso);
        }
    }

    @Override
    public boolean isActionBarShowing() {
        return true;
    }

    @Override
    public boolean isDialpadShown() {
        return false;
    }

    @Override
    public int getDialpadHeight() {
        return 0;
    }

    @Override
    public int getActionBarHideOffset() {
        return getSupportActionBar().getHideOffset();
    }

    @Override
    public int getActionBarHeight() {
        return getResources().getDimensionPixelSize(R.dimen.action_bar_height_large);
    }
}