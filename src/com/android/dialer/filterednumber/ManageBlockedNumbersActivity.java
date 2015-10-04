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
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.Toast;

import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.dialog.IndeterminateProgressDialog;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.dialer.R;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.list.BlockedListSearchAdapter;
import com.android.dialer.list.OnListFragmentScrolledListener;
import com.android.dialer.list.BlockedListSearchFragment;
import com.android.dialer.list.SearchFragment;
import com.android.dialer.widget.SearchEditTextLayout;

public class ManageBlockedNumbersActivity extends AppCompatActivity
        implements SearchFragment.HostInterface {

    private static final String TAG_BLOCKED_MANAGEMENT_FRAGMENT = "blocked_management";
    private static final String TAG_BLOCKED_SEARCH_FRAGMENT = "blocked_search";

    private FilteredNumberAsyncQueryHandler mFilteredNumberAsyncQueryHandler;

    private BlockedNumberFragment mManagementFragment;
    private SearchFragment mSearchFragment;

    private EditText mSearchView;
    private ActionBar mActionBar;
    private String mSearchQuery;

    private boolean mIsShowingManagementUi;

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

    private final SearchEditTextLayout.Callback mSearchLayoutCallback =
            new SearchEditTextLayout.Callback() {
                @Override
                public void onBackButtonClicked() {
                    showManagementUi();
                }

                @Override
                public void onSearchViewClicked() {
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.blocked_numbers_activity);

        mFilteredNumberAsyncQueryHandler =
                new FilteredNumberAsyncQueryHandler(getContentResolver());

        showManagementUi();
    }

    private void showManagementUi() {
        mIsShowingManagementUi = true;

        showManagementUiActionBar();

        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        if (mSearchFragment != null) {
            transaction.hide(mSearchFragment);
        }

        BlockedNumberFragment fragment = (BlockedNumberFragment) getFragmentManager()
                .findFragmentByTag(TAG_BLOCKED_MANAGEMENT_FRAGMENT);
        if (fragment == null) {
            fragment = new BlockedNumberFragment();
            transaction.add(R.id.blocked_numbers_activity_container, fragment,
                    TAG_BLOCKED_MANAGEMENT_FRAGMENT);
        } else {
            transaction.show(fragment);
        }
        transaction.commit();
    }

    private void showManagementUiActionBar() {
        mActionBar = getSupportActionBar();
        ColorDrawable backgroundDrawable = new ColorDrawable(getColor(R.color.dialer_theme_color));
        mActionBar.setBackgroundDrawable(backgroundDrawable);
        mActionBar.setElevation(getResources().getDimensionPixelSize(R.dimen.action_bar_elevation));
        mActionBar.setDisplayShowCustomEnabled(false);
        mActionBar.setDisplayHomeAsUpEnabled(true);
        mActionBar.setDisplayShowHomeEnabled(true);
        mActionBar.setDisplayShowTitleEnabled(true);
        mActionBar.setTitle(R.string.manage_blocked_numbers_label);
    }

    public void enterSearchUi() {
        mIsShowingManagementUi = false;

        showSearchUiActionBar();

        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        if (mManagementFragment != null) {
            transaction.hide(mManagementFragment);
        }

        BlockedListSearchFragment fragment = (BlockedListSearchFragment) getFragmentManager()
                .findFragmentByTag(TAG_BLOCKED_SEARCH_FRAGMENT);
        if (fragment == null) {
            fragment = new BlockedListSearchFragment();
            fragment.setHasOptionsMenu(false);
            fragment.setShowEmptyListForNullQuery(true);
            fragment.setDirectorySearchEnabled(false);
            transaction.add(R.id.blocked_numbers_activity_container, fragment,
                    TAG_BLOCKED_SEARCH_FRAGMENT);
        } else {
            transaction.show(fragment);
        }
        transaction.commit();
    }

    private void showSearchUiActionBar() {
        mActionBar = getSupportActionBar();
        mActionBar.setCustomView(R.layout.search_edittext);
        mActionBar.setBackgroundDrawable(null);
        mActionBar.setElevation(0);
        mActionBar.setDisplayShowCustomEnabled(true);
        mActionBar.setDisplayHomeAsUpEnabled(false);
        mActionBar.setDisplayShowHomeEnabled(false);

        final SearchEditTextLayout searchEditTextLayout = (SearchEditTextLayout) mActionBar
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
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof BlockedNumberFragment) {
            mManagementFragment = (BlockedNumberFragment) fragment;
        } else if (fragment instanceof BlockedListSearchFragment) {
            mSearchFragment = (BlockedListSearchFragment) fragment;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (mIsShowingManagementUi) {
            super.onBackPressed();
        } else {
            showManagementUi();
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
        return getSupportActionBar().getHeight();
    }
}
