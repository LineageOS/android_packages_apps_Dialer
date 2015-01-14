/*
 * Copyright (C) 2013 The Android Open Source Project
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
m * limitations under the License.
 */
package com.android.dialer.calllog;

import android.app.ActionBar;
import android.app.ActionBar.LayoutParams;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.InputMethodManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.SearchView;
import android.widget.SearchView.OnCloseListener;
import android.widget.SearchView.OnQueryTextListener;

import com.android.contacts.common.interactions.TouchPointManager;
import com.android.contacts.common.list.ViewPagerTabs;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialerbind.analytics.AnalyticsActivity;
import com.android.dialer.callstats.CallStatsFragment;
import com.android.dialer.widget.DoubleDatePickerDialog;

public class CallLogActivity extends AnalyticsActivity implements
        DoubleDatePickerDialog.OnDateSetListener {
    private ViewPager mViewPager;
    private ViewPagerTabs mViewPagerTabs;
    private FragmentPagerAdapter mViewPagerAdapter;
    private CallStatsFragment mStatsFragment;

    private MSimCallLogFragment mMSimCallsFragment;
    private CallLogSearchFragment mSearchFragment;
    private SearchView mSearchView;
    private boolean mInSearchUi;

    private static final int TAB_INDEX_MSIM = 0;
    private static final int TAB_INDEX_MSIM_STATS = 1;
    private static final int TAB_INDEX_COUNT_MSIM = 2;

    public class MSimViewPagerAdapter extends FragmentPagerAdapter {
        private String[] mTabTitles;

        public MSimViewPagerAdapter(FragmentManager fm) {
            super(fm);

            mTabTitles = new String[TAB_INDEX_COUNT_MSIM];
            mTabTitles[0] = getString(R.string.call_log_all_title);
            mTabTitles[1] = getString(R.string.call_log_stats_title);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case TAB_INDEX_MSIM:
                    mMSimCallsFragment = new MSimCallLogFragment();
                    mMSimCallsFragment.setHasOptionsMenu(true);
                    return mMSimCallsFragment;
                case TAB_INDEX_MSIM_STATS:
                    mStatsFragment = new CallStatsFragment();
                    return mStatsFragment;
            }
            throw new IllegalStateException("No fragment at position " + position);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mTabTitles[position];
        }

        @Override
        public int getCount() {
            return TAB_INDEX_COUNT_MSIM;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            TouchPointManager.getInstance().setPoint((int) ev.getRawX(), (int) ev.getRawY());
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.call_log_activity);
        getWindow().setBackgroundDrawable(null);

        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setElevation(0);

        mViewPager = (ViewPager) findViewById(R.id.call_log_pager);

        mViewPagerAdapter = new MSimViewPagerAdapter(getFragmentManager());
        mViewPager.setAdapter(mViewPagerAdapter);

        mViewPagerTabs = (ViewPagerTabs) findViewById(R.id.viewpager_header);
        mViewPager.setOnPageChangeListener(mViewPagerTabs);
        mViewPagerTabs.setViewPager(mViewPager);
        addSearchFragment();
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof CallLogSearchFragment) {
            mSearchFragment = (CallLogSearchFragment) fragment;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.call_log_options, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem itemDeleteAll = menu.findItem(R.id.delete_all);
        final MenuItem itemSearchCallLog = menu.findItem(R.id.search_calllog);
        if (mInSearchUi) {
            if (itemDeleteAll != null) {
                itemDeleteAll.setVisible(false);
            }
            if (itemSearchCallLog != null) {
                itemSearchCallLog.setVisible(false);
            }
        } else {
            if (mSearchFragment != null && itemSearchCallLog != null) {
                final CallLogAdapter adapter = mSearchFragment.getAdapter();
                itemSearchCallLog.setVisible(adapter != null
                        && !adapter.isEmpty());
            }

            // If onPrepareOptionsMenu is called before fragments loaded. Don't do anything.
            if (mMSimCallsFragment != null && itemDeleteAll != null) {
                final CallLogAdapter adapter = mMSimCallsFragment.getAdapter();
                itemDeleteAll.setVisible(adapter != null && !adapter.isEmpty());
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            final Intent intent = new Intent(this, DialtactsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            return true;
        case R.id.delete_all:
            onDelCallLog();
            return true;
        case R.id.search_calllog:
            enterSearchUi();
            return true;

        }
        return super.onOptionsItemSelected(item);
    }

    private void onDelCallLog() {
        Intent intent = new Intent(
                "com.android.contacts.action.MULTI_PICK_CALL");
        startActivity(intent);
    }

    private void enterSearchUi() {
        if (mSearchFragment == null) {
            return;
        }
        if (mSearchView == null) {
            prepareSearchView();
        }
        final ActionBar actionBar = getActionBar();

        mSearchView.setQuery(null, true);
        mSearchView.requestFocus();

        actionBar.setDisplayShowCustomEnabled(true);

        for (int i = 0; i < mViewPagerAdapter.getCount(); i++) {
            updateFragmentVisibility(i, false /* not visible */);
        }

        mSearchFragment.setUserVisibleHint(true);
        final FragmentTransaction transaction = getFragmentManager()
                .beginTransaction();
        transaction.show(mSearchFragment);
        transaction.commitAllowingStateLoss();
        getFragmentManager().executePendingTransactions();
        mViewPager.setVisibility(View.GONE);
        mViewPagerTabs.setVisibility(View.GONE);

        // We need to call this and onActionViewCollapsed() manually, since we
        // are using a custom
        // layout instead of asking the search menu item to take care of
        // SearchView.
        mSearchView.onActionViewExpanded();
        mInSearchUi = true;
    }

    private void updateFragmentVisibility(int position, boolean visibility) {
        if (position >= TAB_INDEX_MSIM) {
            final Fragment fragment = getFragmentAt(position);
            if (fragment != null) {
                fragment.setMenuVisibility(visibility);
                fragment.setUserVisibleHint(visibility);
            }
        }
    }

    private Fragment getFragmentAt(int position) {
        switch (position) {
        case TAB_INDEX_MSIM:
            return mMSimCallsFragment;
        case TAB_INDEX_MSIM_STATS:
            return mStatsFragment;
        default:
            throw new IllegalStateException("Unknown fragment index: "
                    + position);
        }
    }

    private void addSearchFragment() {
        if (mSearchFragment != null) {
            return;
        }
        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        final Fragment searchFragment = new CallLogSearchFragment();
        searchFragment.setUserVisibleHint(false);
        ft.add(R.id.calllog_frame, searchFragment);
        ft.hide(searchFragment);
        ft.commitAllowingStateLoss();
    }

    private void prepareSearchView() {
        final View searchViewLayout = getLayoutInflater().inflate(
                R.layout.custom_action_bar, null);
        mSearchView = (SearchView) searchViewLayout
                .findViewById(R.id.search_view);
        mSearchView.setOnQueryTextListener(mPhoneSearchQueryTextListener);
        mSearchView.setOnCloseListener(mPhoneSearchCloseListener);
        mSearchView.setQueryHint(getString(R.string.calllog_search_hint));
        mSearchView.setIconifiedByDefault(true);
        mSearchView.setIconified(false);

        mSearchView
                .setOnQueryTextFocusChangeListener(new OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View view, boolean hasFocus) {
                        if (hasFocus) {
                            showInputMethod(view.findFocus());
                        }
                    }
                });

        getActionBar().setCustomView(
                searchViewLayout,
                new LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT));
    }

    private void showInputMethod(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            if (!imm.showSoftInput(view, 0)) {
            }
        }
    }

    private void hideInputMethod(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Listener used to send search queries to the phone search fragment.
     */
    private final OnQueryTextListener mPhoneSearchQueryTextListener = new OnQueryTextListener() {
        @Override
        public boolean onQueryTextSubmit(String query) {
            View view = getCurrentFocus();
            if (view != null) {
                hideInputMethod(view);
                view.clearFocus();
            }
            return true;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            // Show search result with non-empty text. Show a bare list
            // otherwise.
            if (mSearchFragment != null) {
                mSearchFragment.setQueryString(newText);
            }
            return true;
        }
    };

    /**
     * Listener used to handle the "close" button on the right side of
     * {@link SearchView}. If some text is in the search view, this will clean
     * it up. Otherwise this will exit the search UI and let users go back to
     * usual Phone UI.
     *
     * This does _not_ handle back button.
     */
    private final OnCloseListener mPhoneSearchCloseListener = new OnCloseListener() {
        @Override
        public boolean onClose() {
            if (!TextUtils.isEmpty(mSearchView.getQuery())) {
                mSearchView.setQuery(null, true);
            }
            return true;
        }
    };

    @Override
    public void onBackPressed() {
        if (mInSearchUi) {
            // We should let the user go back to usual screens with tabs.
            exitSearchUi();
        } else {
            super.onBackPressed();
        }
    }

    private void exitSearchUi() {
        final ActionBar actionBar = getActionBar();
        if (mSearchFragment != null) {
            mSearchFragment.setUserVisibleHint(false);

            final FragmentTransaction transaction = getFragmentManager()
                    .beginTransaction();
            transaction.hide(mSearchFragment);
            transaction.commitAllowingStateLoss();

        }

        // We want to hide SearchView and show Tabs. Also focus on previously
        // selected one.
        actionBar.setDisplayShowCustomEnabled(false);

        for (int i = 0; i < mViewPagerAdapter.getCount(); i++) {
            updateFragmentVisibility(i, i == mViewPager.getCurrentItem());
        }

        mViewPager.setVisibility(View.VISIBLE);
        mViewPagerTabs.setVisibility(View.VISIBLE);

        hideInputMethod(getCurrentFocus());

        // Request to update option menu.
        invalidateOptionsMenu();

        // See comments in onActionViewExpanded()
        mSearchView.onActionViewCollapsed();
        mSearchView.clearFocus();
        mInSearchUi = false;
    }

    @Override
    public void onDateSet(long from, long to) {
        switch (mViewPager.getCurrentItem()) {
            case TAB_INDEX_MSIM:
                mMSimCallsFragment.onDateSet(from, to);
                break;
            case TAB_INDEX_MSIM_STATS:
                mStatsFragment.onDateSet(from, to);
                break;
        }
    }
}
