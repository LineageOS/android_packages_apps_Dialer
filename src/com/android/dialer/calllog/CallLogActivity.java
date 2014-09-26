/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 * Not a Contribution
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
 * limitations under the License.
 */
package com.android.dialer.calllog;

import android.app.ActionBar;
import android.app.ActionBar.LayoutParams;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.telephony.MSimTelephonyManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.TypefaceSpan;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.SearchView;
import android.widget.SearchView.OnCloseListener;
import android.widget.SearchView.OnQueryTextListener;
import android.util.Log;

import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.calllog.CallLogFragment;
import com.android.dialer.callstats.CallStatsFragment;
import com.android.dialer.widget.DoubleDatePickerDialog;
import com.android.dialer.calllog.MSimCallLogFragment;

public class CallLogActivity extends Activity implements
        DoubleDatePickerDialog.OnDateSetListener {
    private static final String TAG = "CallLogActivity";

    private ViewPager mViewPager;
    private FragmentPagerAdapter mViewPagerAdapter;
    private CallLogFragment mAllCallsFragment;
    private CallLogFragment mMissedCallsFragment;
    private CallStatsFragment mStatsFragment;
    private MSimCallLogFragment mMSimCallsFragment;
    private CallLogSearchFragment mSearchFragment;

    private SearchView mSearchView;
    private boolean mInSearchUi;

    private static final int TAB_INDEX_MSIM = 0;
    private static final int TAB_INDEX__MSIM_COUNT = 1;

    private static final int TAB_INDEX_ALL = 0;
    private static final int TAB_INDEX_MISSED = 1;
    private static final int TAB_INDEX_STATS = 2;

    private static final int TAB_INDEX_COUNT = 3;

    private static final String STATE_KEY_SEARCH = "calllog:search";
    private static final String STATE_KEY_QUERY = "calllog:query";

    public class ViewPagerAdapter extends FragmentPagerAdapter {
        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case TAB_INDEX_ALL:
                    return CallLogFragment.newInstance(CallLogQueryHandler.CALL_TYPE_ALL);
                case TAB_INDEX_MISSED:
                    return CallLogFragment.newInstance(Calls.MISSED_TYPE);
                case TAB_INDEX_STATS:
                    mStatsFragment = new CallStatsFragment();
                    return mStatsFragment;
            }
            throw new IllegalStateException("No fragment at position " + position);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            // We can't distinguish the two fragments in onAttach, which is
            // why we differentiate between them here
            Object result = super.instantiateItem(container, position);
            switch (position) {
                case TAB_INDEX_ALL:
                    mAllCallsFragment = (CallLogFragment) result;
                    break;
                case TAB_INDEX_MISSED:
                    mMissedCallsFragment = (CallLogFragment) result;
                    break;
            }
            return result;
        }

        @Override
        public int getCount() {
            return TAB_INDEX_COUNT;
        }
    }

    public class MSimViewPagerAdapter extends FragmentPagerAdapter {
        public MSimViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case TAB_INDEX_MSIM:
                    mMSimCallsFragment = new MSimCallLogFragment();
                    return mMSimCallsFragment;
            }
            throw new IllegalStateException("No fragment at position " + position);
        }

        @Override
        public int getCount() {
            return TAB_INDEX__MSIM_COUNT;
        }
    }

    private final TabListener mTabListener = new TabListener() {
        @Override
        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        }

        @Override
        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            if (mViewPager != null && mViewPager.getCurrentItem() != tab.getPosition()) {
                mViewPager.setCurrentItem(tab.getPosition(), true);
            }
        }

        @Override
        public void onTabReselected(Tab tab, FragmentTransaction ft) {
        }
    };

    private final OnPageChangeListener mOnPageChangeListener = new OnPageChangeListener() {

        @Override
        public void onPageScrolled(
                int position, float positionOffset, int positionOffsetPixels) {}

        @Override
        public void onPageSelected(int position) {
            final ActionBar actionBar = getActionBar();
            actionBar.selectTab(actionBar.getTabAt(position));
        }

        @Override
        public void onPageScrollStateChanged(int arg0) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            initMSimCallLog();
            initSearchFragment();
            return;
        }

        setContentView(R.layout.call_log_activity);

        final ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);

        final Tab allTab = actionBar.newTab();
        final String allTitle = getString(R.string.call_log_all_title);
        allTab.setContentDescription(allTitle);
        allTab.setText(allTitle);
        allTab.setTabListener(mTabListener);
        actionBar.addTab(allTab);

        final Tab missedTab = actionBar.newTab();
        final String missedTitle = getString(R.string.call_log_missed_title);
        missedTab.setContentDescription(missedTitle);
        missedTab.setText(missedTitle);
        missedTab.setTabListener(mTabListener);
        actionBar.addTab(missedTab);

        final Tab statsTab = actionBar.newTab();
        final String statsTitle = getString(R.string.call_log_stats_title);
        statsTab.setContentDescription(statsTitle);
        statsTab.setText(statsTitle);
        statsTab.setTabListener(mTabListener);
        actionBar.addTab(statsTab);

        mViewPager = (ViewPager) findViewById(R.id.call_log_pager);
        mViewPagerAdapter = new ViewPagerAdapter(getFragmentManager());
        mViewPager.setAdapter(mViewPagerAdapter);
        mViewPager.setOnPageChangeListener(mOnPageChangeListener);
        mViewPager.setOffscreenPageLimit(2);

        initSearchFragment();
        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_KEY_SEARCH, false)) {
            enterSearchUi();
            mSearchView.setQuery(savedInstanceState.getString(STATE_KEY_QUERY), false);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean(STATE_KEY_SEARCH, mInSearchUi);
        if (mInSearchUi) {
            state.putString(STATE_KEY_QUERY, mSearchFragment.getQueryString());
        }
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof CallLogSearchFragment) {
            mSearchFragment = (CallLogSearchFragment) fragment;
        } else if (fragment instanceof MSimCallLogFragment) {
            mMSimCallsFragment = (MSimCallLogFragment) fragment;
        } else if (fragment instanceof CallStatsFragment) {
            mStatsFragment = (CallStatsFragment) fragment;
        }
    }

    private void initMSimCallLog() {
        setContentView(R.layout.msim_call_log_activity);

        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);

        mViewPager = (ViewPager) findViewById(R.id.call_log_pager);
        mViewPagerAdapter = new MSimViewPagerAdapter(getFragmentManager());

        mViewPager.setAdapter(mViewPagerAdapter);
        mViewPager.setOnPageChangeListener(mOnPageChangeListener);
        mViewPager.setOffscreenPageLimit(1);
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
            // If onPrepareOptionsMenu is called before fragments loaded. Don't
            // do anything.
            if (mAllCallsFragment != null && itemDeleteAll != null) {
                final CallLogAdapter adapter = mAllCallsFragment.getAdapter();
                itemDeleteAll.setVisible(adapter != null && !adapter.isEmpty());
            }
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

    @Override
    public void onDateSet(long from, long to) {
        mStatsFragment.onDateSet(from, to);
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
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);

        for (int i = 0; i < mViewPagerAdapter.getCount(); i++) {
            updateFragmentVisibility(i, false /* not visible */);
        }

        mSearchFragment.setUserVisibleHint(true);
        final FragmentTransaction transaction = getFragmentManager()
                .beginTransaction();
        transaction.show(mSearchFragment);
        transaction.commitAllowingStateLoss();
        mViewPager.setVisibility(View.GONE);

        // We need to call this and onActionViewCollapsed() manually, since we
        // are using a custom
        // layout instead of asking the search menu item to take care of
        // SearchView.
        mSearchView.onActionViewExpanded();
        mInSearchUi = true;
    }

    private void updateFragmentVisibility(int position, boolean visibility) {
        if (position >= TAB_INDEX_ALL) {
            final Fragment fragment = getFragmentAt(position);
            if (fragment != null) {
                fragment.setMenuVisibility(visibility);
                fragment.setUserVisibleHint(visibility);
            }
        }
    }

    private Fragment getFragmentAt(int position) {
        switch (position) {
        case TAB_INDEX_ALL:
            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                return mMSimCallsFragment;
            } else {
                return mAllCallsFragment;
            }
        case TAB_INDEX_MISSED:
            return mMissedCallsFragment;
        case TAB_INDEX_STATS:
            return mStatsFragment;
        default:
            throw new IllegalStateException("Unknown fragment index: "
                    + position);
        }
    }

    private void initSearchFragment() {
        final FragmentManager fm = getFragmentManager();
        if (mSearchFragment == null) {
            mSearchFragment = (CallLogSearchFragment) fm.findFragmentByTag("search");
        }
        if (mSearchFragment != null) {
            fm.beginTransaction().hide(mSearchFragment).commit();
            return;
        }
        final FragmentTransaction ft = fm.beginTransaction();
        final Fragment searchFragment = new CallLogSearchFragment();
        searchFragment.setUserVisibleHint(false);
        ft.add(R.id.calllog_frame, searchFragment, "search");
        ft.hide(searchFragment);
        ft.commitAllowingStateLoss();
    }

    private void prepareSearchView() {
        final LayoutInflater inflater = LayoutInflater.from(
                new ContextThemeWrapper(this, R.style.DialtactsSearchTheme));
        final View searchViewLayout = inflater.inflate(R.layout.custom_action_bar, null);
        mSearchView = (SearchView) searchViewLayout.findViewById(R.id.search_view);
        mSearchView.setOnQueryTextListener(mPhoneSearchQueryTextListener);
        mSearchView.setOnCloseListener(mPhoneSearchCloseListener);
        mSearchView.setQueryHint(getString(R.string.calllog_search_hint));
        mSearchView.setIconifiedByDefault(true);
        mSearchView.setIconified(false);

        mSearchView.setOnQueryTextFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus) {
                    showInputMethod(view.findFocus());
                }
            }
        });

        getActionBar().setCustomView(searchViewLayout,
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void showInputMethod(View view) {
        InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            if (!imm.showSoftInput(view, 0)) {
                Log.w(TAG, "Failed to show soft input method.");
            }
        }
    }

    private void hideInputMethod(View view) {
        InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
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
     * Listener used to handle the "close" button on the right side of {@link SearchView}.
     * If some text is in the search view, this will clean it up. Otherwise this will exit
     * the search UI and let users go back to usual Phone UI.
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
        if (!MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        }

        for (int i = 0; i < mViewPagerAdapter.getCount(); i++) {
            updateFragmentVisibility(i, i == mViewPager.getCurrentItem());
        }

        mViewPager.setVisibility(View.VISIBLE);

        hideInputMethod(getCurrentFocus());

        // Request to update option menu.
        invalidateOptionsMenu();

        // See comments in onActionViewExpanded()
        mSearchView.onActionViewCollapsed();
        mSearchView.clearFocus();
        mInSearchUi = false;
    }
}
