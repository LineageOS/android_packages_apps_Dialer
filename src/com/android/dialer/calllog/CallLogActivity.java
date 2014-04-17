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
 * limitations under the License.
 */
package com.android.dialer.calllog;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.list.ViewPagerTabs;
import com.android.dialer.voicemail.VoicemailStatusHelper;
import com.android.dialer.voicemail.VoicemailStatusHelperImpl;

public class CallLogActivity extends Activity implements CallLogQueryHandler.Listener {
    private ViewPager mViewPager;
    private ViewPagerTabs mViewPagerTabs;
    private ViewPagerAdapter mViewPagerAdapter;
    private CallLogFragment mAllCallsFragment;
    private CallLogFragment mMissedCallsFragment;
    private CallLogFragment mVoicemailFragment;
    private VoicemailStatusHelper mVoicemailStatusHelper;

    private String[] mTabTitles;

    private static final int TAB_INDEX_ALL = 0;
    private static final int TAB_INDEX_MISSED = 1;
    private static final int TAB_INDEX_VOICEMAIL = 2;

    private static final int TAB_INDEX_COUNT_DEFAULT = 2;
    private static final int TAB_INDEX_COUNT_WITH_VOICEMAIL = 3;

    private boolean mHasActiveVoicemailProvider;

    public class ViewPagerAdapter extends FragmentPagerAdapter {
        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case TAB_INDEX_ALL:
                    mAllCallsFragment = new CallLogFragment(CallLogQueryHandler.CALL_TYPE_ALL);
                    return mAllCallsFragment;
                case TAB_INDEX_MISSED:
                    mMissedCallsFragment = new CallLogFragment(Calls.MISSED_TYPE);
                    return mMissedCallsFragment;
                case TAB_INDEX_VOICEMAIL:
                    mVoicemailFragment = new CallLogFragment(Calls.VOICEMAIL_TYPE);
                    return mVoicemailFragment;
            }
            throw new IllegalStateException("No fragment at position " + position);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mTabTitles[position];
        }

        @Override
        public int getCount() {
            return mHasActiveVoicemailProvider ? TAB_INDEX_COUNT_WITH_VOICEMAIL :
                    TAB_INDEX_COUNT_DEFAULT;
        }
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

        mTabTitles = new String[TAB_INDEX_COUNT_WITH_VOICEMAIL];
        mTabTitles[0] = getString(R.string.call_log_all_title);
        mTabTitles[1] = getString(R.string.call_log_missed_title);
        mTabTitles[2] = getString(R.string.call_log_voicemail_title);

        mViewPager = (ViewPager) findViewById(R.id.call_log_pager);

        mViewPagerAdapter = new ViewPagerAdapter(getFragmentManager());
        mViewPager.setAdapter(mViewPagerAdapter);
        mViewPager.setOffscreenPageLimit(2);

        mViewPagerTabs = (ViewPagerTabs) findViewById(R.id.viewpager_header);
        mViewPagerTabs.setViewPager(mViewPager);

        mVoicemailStatusHelper = new VoicemailStatusHelperImpl();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CallLogQueryHandler callLogQueryHandler =
                new CallLogQueryHandler(this.getContentResolver(), this);
        callLogQueryHandler.fetchVoicemailStatus();
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

        // If onPrepareOptionsMenu is called before fragments loaded. Don't do anything.
        if (mAllCallsFragment != null && itemDeleteAll != null) {
            final CallLogAdapter adapter = mAllCallsFragment.getAdapter();
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
                ClearCallLogDialog.show(getFragmentManager());
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        if (this.isFinishing()) {
            return;
        }

        // Update mHasActiveVoicemailProvider, which controls the number of tabs displayed.
        int activeSources = mVoicemailStatusHelper.getNumberActivityVoicemailSources(statusCursor);
        if (activeSources > 0 != mHasActiveVoicemailProvider) {
            mHasActiveVoicemailProvider = activeSources > 0;
            mViewPagerAdapter.notifyDataSetChanged();
            mViewPagerTabs.setViewPager(mViewPager);
        }
    }

    @Override
    public void onCallsFetched(Cursor statusCursor) {
        // Do nothing. Implemented to satisfy CallLogQueryHandler.Listener.
    }
}