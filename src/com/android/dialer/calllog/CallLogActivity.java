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

import android.animation.ValueAnimator;
import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.SearchView;
import android.widget.Toolbar;

import com.android.contacts.common.interactions.TouchPointManager;
import com.android.contacts.common.list.ViewPagerTabs;
import com.android.contacts.common.util.ViewUtil;
import com.android.contacts.commonbind.analytics.AnalyticsUtil;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.callstats.CallStatsFragment;
import com.android.dialer.widget.DoubleDatePickerDialog;

public class CallLogActivity extends Activity implements
        ViewPager.OnPageChangeListener, SearchView.OnCloseListener,
        View.OnClickListener, DoubleDatePickerDialog.OnDateSetListener {
    private ViewPager mViewPager;
    private ViewPagerTabs mViewPagerTabs;
    private Toolbar mToolbar;
    private MSimViewPagerAdapter mViewPagerAdapter;

    private MSimCallLogFragment mCallsFragment;
    private CallStatsFragment mStatsFragment;
    private CallLogSearchFragment mSearchFragment;

    private int mCurrentTab;
    private int mMaxTabHeight;
    private int mMaxToolbarContentInsetStart;

    private boolean mSearchMode;
    private String mQueryString;

    private EditText mSearchView;
    private View mSearchContainer;

    private static final int TAB_INDEX_MSIM = 0;
    private static final int TAB_INDEX_MSIM_STATS = 1;
    private static final int TAB_INDEX_COUNT_MSIM = 2;

    private static final String EXTRA_KEY_SEARCH_MODE = "searchMode";
    private static final String EXTRA_KEY_QUERY = "query";
    private static final String EXTRA_KEY_SELECTED_TAB = "selectedTab";

    public class MSimViewPagerAdapter extends PagerAdapter {
        private final FragmentManager mFragmentManager;
        private FragmentTransaction mCurTransaction = null;
        private Fragment mCurrentPrimaryItem;
        private String[] mTabTitles;
        private boolean mPagerInSearchMode;

        public MSimViewPagerAdapter(FragmentManager fm) {
            mFragmentManager = fm;

            mTabTitles = new String[TAB_INDEX_COUNT_MSIM];
            mTabTitles[0] = getString(R.string.call_log_all_title);
            mTabTitles[1] = getString(R.string.call_log_stats_title);
        }

        public void setSearchMode(boolean searchMode) {
            mPagerInSearchMode = searchMode;
            notifyDataSetChanged();
        }

        public boolean isSearchMode() {
            return mPagerInSearchMode;
        }

        @Override
        public int getCount() {
            return mPagerInSearchMode ? 1 : TAB_INDEX_COUNT_MSIM;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mTabTitles[position];
        }

        /** Gets called when the number of items changes. */
        @Override
        public int getItemPosition(Object object) {
            if (mPagerInSearchMode) {
                if (object == mSearchFragment) {
                    return 0;
                }
            } else {
                if (object == mCallsFragment) {
                    return TAB_INDEX_MSIM;
                }
                if (object == mStatsFragment) {
                    return TAB_INDEX_MSIM_STATS;
                }
            }
            return POSITION_NONE;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            if (mCurTransaction == null) {
                mCurTransaction = mFragmentManager.beginTransaction();
            }
            Fragment f = getFragment(position);
            mCurTransaction.show(f);

            // Non primary pages are not visible.
            f.setUserVisibleHint(f == mCurrentPrimaryItem);
            return f;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            if (mCurTransaction == null) {
                mCurTransaction = mFragmentManager.beginTransaction();
            }
            mCurTransaction.hide((Fragment) object);
        }

        @Override
        public void startUpdate(ViewGroup container) {
        }


        @Override
        public void finishUpdate(ViewGroup container) {
            if (mCurTransaction != null) {
                mCurTransaction.commitAllowingStateLoss();
                mCurTransaction = null;
                mFragmentManager.executePendingTransactions();
            }
            invalidateOptionsMenu();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return ((Fragment) object).getView() == view;
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            Fragment fragment = (Fragment) object;
            if (mCurrentPrimaryItem != fragment) {
                if (mCurrentPrimaryItem != null) {
                    mCurrentPrimaryItem.setUserVisibleHint(false);
                }
                if (fragment != null) {
                    fragment.setUserVisibleHint(true);
                }
                mCurrentPrimaryItem = fragment;
            }
        }

        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void restoreState(Parcelable state, ClassLoader loader) {
        }

        private Fragment getFragment(int position) {
            if (mPagerInSearchMode) {
                return mSearchFragment;
            }
            switch (position) {
                case TAB_INDEX_MSIM:
                    return mCallsFragment;
                case TAB_INDEX_MSIM_STATS:
                    return mStatsFragment;
            }
            throw new IllegalStateException("No fragment at position " + position);
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

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mMaxToolbarContentInsetStart = mToolbar.getContentInsetStart();

        setActionBar(mToolbar);
        setupSearchViews();

        final View toolbarContainer = findViewById(R.id.toolbar_container);
        ViewUtil.addRectangularOutlineProvider(toolbarContainer, getResources());

        mMaxTabHeight = getResources().getDimensionPixelSize(R.dimen.tab_height);

        mViewPager = (ViewPager) findViewById(R.id.call_log_pager);

        mViewPagerAdapter = new MSimViewPagerAdapter(getFragmentManager());
        mViewPager.setAdapter(mViewPagerAdapter);

        mViewPagerTabs = (ViewPagerTabs) findViewById(R.id.viewpager_header);
        mViewPager.setOnPageChangeListener(mViewPagerTabs);
        mViewPagerTabs.setViewPager(mViewPager);

        setupFragments();

        if (savedInstanceState != null) {
            setQueryString(savedInstanceState.getString(EXTRA_KEY_QUERY));
            mSearchMode = savedInstanceState.getBoolean(EXTRA_KEY_SEARCH_MODE);
            mCurrentTab = savedInstanceState.getInt(EXTRA_KEY_SELECTED_TAB);
        }

        update(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(EXTRA_KEY_SEARCH_MODE, mSearchMode);
        outState.putString(EXTRA_KEY_QUERY, mQueryString);
        outState.putInt(EXTRA_KEY_SELECTED_TAB, mCurrentTab);
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

        if (mSearchMode) {
            itemSearchCallLog.setVisible(false);
            itemDeleteAll.setVisible(false);
        } else {
            itemSearchCallLog.setVisible(true);

            final CallLogAdapter adapter = mCallsFragment.getAdapter();
            itemDeleteAll.setVisible(adapter != null && !adapter.isEmpty());
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
            onSearchRequested();
            return true;

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSearchRequested() { // Search key pressed.
        setSearchMode(true);
        return true;
    }

    @Override
    public boolean onClose() {
        setSearchMode(false);
        return false;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.search_close_button:
                setQueryString(null);
                break;
            case R.id.search_back_button:
                onBackPressed();
                break;
        }
    }

    private void setupFragments() {
        final String CALLS_TAG = "tab-pager-calls";
        final String STATS_TAG = "tab-pager-stats";
        final String SEARCH_TAG = "tab-pager-search";

        final FragmentManager fm = getFragmentManager();
        final FragmentTransaction transaction = fm.beginTransaction();

        // Create the fragments and add as children of the view pager.
        // The pager adapter will only change the visibility; it'll never create/destroy
        // fragments.
        // However, if it's after screen rotation, the fragments have been re-created by
        // the fragment manager, so first see if there're already the target fragments
        // existing.
        mCallsFragment = (MSimCallLogFragment) fm.findFragmentByTag(CALLS_TAG);
        mStatsFragment = (CallStatsFragment) fm.findFragmentByTag(STATS_TAG);
        mSearchFragment = (CallLogSearchFragment) fm.findFragmentByTag(SEARCH_TAG);

        if (mCallsFragment == null) {
            mCallsFragment = new MSimCallLogFragment();
            mStatsFragment = new CallStatsFragment();
            mSearchFragment = new CallLogSearchFragment();

            transaction.add(R.id.call_log_pager, mCallsFragment, CALLS_TAG);
            transaction.add(R.id.call_log_pager, mStatsFragment, STATS_TAG);
            transaction.add(R.id.call_log_pager, mSearchFragment, SEARCH_TAG);
        }

        transaction.hide(mCallsFragment);
        transaction.hide(mStatsFragment);
        transaction.hide(mSearchFragment);

        transaction.commitAllowingStateLoss();
        fm.executePendingTransactions();
    }

    private void setupSearchViews() {
        final LayoutInflater inflater =
                (LayoutInflater) mToolbar.getContext().getSystemService(LAYOUT_INFLATER_SERVICE);
        mSearchContainer = inflater.inflate(R.layout.search_bar_expanded, mToolbar, false);
        mSearchContainer.setVisibility(View.VISIBLE);
        mSearchContainer.setBackgroundColor(
                getResources().getColor(R.color.searchbox_background_color));

        mSearchView = (EditText) mSearchContainer.findViewById(R.id.search_view);
        mSearchView.setHint(getString(R.string.calllog_search_hint));
        mSearchView.addTextChangedListener(new SearchTextWatcher());
        mSearchContainer.findViewById(R.id.search_close_button).setOnClickListener(this);
        mSearchContainer.findViewById(R.id.search_back_button).setOnClickListener(this);
    }

    private void onDelCallLog() {
        Intent intent = new Intent(
                "com.android.contacts.action.MULTI_PICK_CALL");
        startActivity(intent);
    }

    private void setFocusOnSearchView() {
        mSearchView.requestFocus();
        showInputMethod(mSearchView); // Workaround for the "IME not popping up" issue.
    }

    private void showInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, 0);
        }
    }

    private void update(boolean skipAnimation) {
        final boolean isIconifiedChanging = (mSearchContainer.getParent() == null) == mSearchMode;
        if (!isIconifiedChanging) {
            updateDisplayOptions(false);
            return;
        }

        if (skipAnimation) {
            if (mSearchMode) {
                setTabHeight(0);
                addSearchContainer();
            } else {
                setTabHeight(mMaxTabHeight);
                mToolbar.removeView(mSearchContainer);
            }
            updateDisplayOptions(true);
        } else {
            if (mSearchMode) {
                addSearchContainer();
                mSearchContainer.setAlpha(0);
                mSearchContainer.animate().alpha(1);
                animateTabHeightChange(mMaxTabHeight, 0);
                updateDisplayOptions(true);
            } else {
                mSearchContainer.setAlpha(1);
                animateTabHeightChange(0, mMaxTabHeight);
                mSearchContainer.animate().alpha(0).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        updateDisplayOptions(true);
                        mToolbar.removeView(mSearchContainer);
                    }
                });
            }
        }
    }

    private void updateDisplayOptions(boolean isIconifiedChanging) {
        if (mSearchMode) {
            setFocusOnSearchView();
            // Since we have the {@link SearchView} in a custom action bar, we must manually handle
            // expanding the {@link SearchView} when a search is initiated. Note that a side effect
            // of this method is that the {@link SearchView} query text is set to empty string.
            if (isIconifiedChanging) {
                final CharSequence queryText = mSearchView.getText();
                if (!TextUtils.isEmpty(queryText)) {
                    mSearchView.setText(queryText);
                }
            }
        } else if (mViewPager.getCurrentItem() != mCurrentTab) {
            mViewPager.setCurrentItem(mCurrentTab, !mViewPagerAdapter.isSearchMode());
        }

        updateDisplayOptionsInner();
        mViewPagerAdapter.setSearchMode(mSearchMode);
    }

    private void updateDisplayOptionsInner() {
        // All the flags we may change in this method.
        final int MASK = ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_HOME
                | ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_CUSTOM;

        // The current flags set to the action bar.  (only the ones that we may change here)
        final ActionBar actionBar = getActionBar();
        final int current = actionBar.getDisplayOptions() & MASK;

        // Build the new flags...
        int newFlags = 0;
        if (mSearchMode) {
            newFlags |= ActionBar.DISPLAY_SHOW_CUSTOM;
            mToolbar.setContentInsetsRelative(0, mToolbar.getContentInsetEnd());
        } else {
            newFlags |= ActionBar.DISPLAY_SHOW_TITLE;
            newFlags |= ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_HOME_AS_UP;
            mToolbar.setContentInsetsRelative(mMaxToolbarContentInsetStart,
                    mToolbar.getContentInsetEnd());
        }


        if (current != newFlags) {
            // Pass the mask here to preserve other flags that we're not interested here.
            actionBar.setDisplayOptions(newFlags, MASK);
        }
    }

    private void addSearchContainer() {
        mToolbar.removeView(mSearchContainer);
        mToolbar.addView(mSearchContainer);
    }

    private void animateTabHeightChange(int start, int end) {
        if (mViewPagerTabs == null) {
            return;
        }
        final ValueAnimator animator = ValueAnimator.ofInt(start, end);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int value = (Integer) valueAnimator.getAnimatedValue();
                setTabHeight(value);
            }
        });
        animator.setDuration(100).start();
    }

    private void setTabHeight(int height) {
        if (mViewPagerTabs == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = mViewPagerTabs.getLayoutParams();
        layoutParams.height = height;
        mViewPagerTabs.setLayoutParams(layoutParams);
    }

    private void setQueryString(String query) {
        mQueryString = query;
        if (mSearchView != null) {
            mSearchView.setText(query);
            // When programmatically entering text into the search view, the most reasonable
            // place for the cursor is after all the text.
            mSearchView.setSelection(mSearchView.getText() == null ?
                    0 : mSearchView.getText().length());
        }
    }

    private void setSearchMode(boolean flag) {
        if (mSearchMode == flag) {
            if (flag) {
                setFocusOnSearchView();
            }
            return;
        }

        mSearchMode = flag;
        update(false);
        if (flag) {
            mSearchView.setEnabled(true);
            setFocusOnSearchView();
        } else {
            mSearchView.setEnabled(false);
        }

        setQueryString(null);
    }

    private class SearchTextWatcher implements TextWatcher {
        @Override
        public void onTextChanged(CharSequence queryString, int start, int before, int count) {
            if (queryString.equals(mQueryString)) {
                return;
            }
            mQueryString = queryString.toString();
            mSearchFragment.setQueryString(mQueryString);
            if (!mSearchMode && !TextUtils.isEmpty(queryString)) {
                setSearchMode(true);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {}

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    }

    @Override
    public void onBackPressed() {
        if (mSearchMode) {
            // We should let the user go back to usual screens with tabs.
            setSearchMode(false);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDateSet(long from, long to) {
        switch (mViewPager.getCurrentItem()) {
            case TAB_INDEX_MSIM:
                mCallsFragment.onDateSet(from, to);
                break;
            case TAB_INDEX_MSIM_STATS:
                mStatsFragment.onDateSet(from, to);
                break;
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        mViewPagerTabs.onPageScrolled(position, positionOffset, positionOffsetPixels);
    }

    @Override
    public void onPageSelected(int position) {
        if (isResumed()) {
            sendScreenViewForChildFragment(position);
        }
        mViewPagerTabs.onPageSelected(position);
        if (!mSearchMode) {
            mCurrentTab = position;
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        mViewPagerTabs.onPageScrollStateChanged(state);
    }

    private void sendScreenViewForChildFragment(int position) {
        AnalyticsUtil.sendScreenView(CallLogFragment.class.getSimpleName(), this,
                getFragmentTagForPosition(position));
    }

    /**
     * Returns the fragment located at the given position in the {@link ViewPagerAdapter}. May
     * be null if the position is invalid.
     */
    private String getFragmentTagForPosition(int position) {
        switch (position) {
            case TAB_INDEX_MSIM:
                return "All";
            case TAB_INDEX_MSIM_STATS:
                return "Stats";
        }
        return null;
    }
}
