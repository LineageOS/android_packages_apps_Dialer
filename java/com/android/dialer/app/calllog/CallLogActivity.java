/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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
package com.android.dialer.app.calllog;

import android.content.Intent;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.android.contacts.common.list.ViewPagerTabs;
import com.android.dialer.R;
import com.android.dialer.callstats.CallStatsFragment;
import com.android.dialer.callstats.DoubleDatePickerDialog;
import com.android.dialer.common.Assert;
import com.android.dialer.database.CallLogQueryHandler;
import com.android.dialer.util.TransactionSafeActivity;
import com.android.dialer.util.ViewUtil;

/** Activity for viewing call history. */
public class CallLogActivity extends TransactionSafeActivity implements
    ViewPager.OnPageChangeListener, DoubleDatePickerDialog.OnDateSetListener {

  private static final int TAB_INDEX_ALL = 0;
  private static final int TAB_INDEX_MISSED = 1;
  private static final int TAB_INDEX_STATS = 2;
  private static final int TAB_INDEX_COUNT = 3;
  private ViewPager viewPager;
  private ViewPagerTabs viewPagerTabs;
  private ViewPagerAdapter viewPagerAdapter;
  private CallLogFragment allCallsFragment;
  private CallLogFragment missedCallsFragment;
  private CallStatsFragment statsFragment;
  private String[] tabTitles;
  private boolean isResumed;
  private int selectedPageIndex;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.call_log_activity);
    getWindow().setBackgroundDrawable(null);

    final ActionBar actionBar = getSupportActionBar();
    actionBar.setDisplayShowHomeEnabled(true);
    actionBar.setDisplayHomeAsUpEnabled(true);
    actionBar.setDisplayShowTitleEnabled(true);
    actionBar.setElevation(0);

    int startingTab = TAB_INDEX_ALL;
    final Intent intent = getIntent();
    if (intent != null) {
      final int callType = intent.getIntExtra(CallLog.Calls.EXTRA_CALL_TYPE_FILTER, -1);
      if (callType == CallLog.Calls.MISSED_TYPE) {
        startingTab = TAB_INDEX_MISSED;
      }
    }
    selectedPageIndex = startingTab;

    tabTitles = new String[TAB_INDEX_COUNT];
    tabTitles[0] = getString(R.string.call_log_all_title);
    tabTitles[1] = getString(R.string.call_log_missed_title);
    tabTitles[2] = getString(R.string.call_log_stats_title);

    viewPager = (ViewPager) findViewById(R.id.call_log_pager);

    viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager());
    viewPager.setAdapter(viewPagerAdapter);
    viewPager.setOffscreenPageLimit(1);
    viewPager.setOnPageChangeListener(this);

    viewPagerTabs = (ViewPagerTabs) findViewById(R.id.viewpager_header);

    viewPagerTabs.setViewPager(viewPager);
    viewPager.setCurrentItem(startingTab);
  }

  @Override
  protected void onResume() {
    isResumed = true;
    super.onResume();
  }

  @Override
  protected void onPause() {
    isResumed = false;
    super.onPause();
  }

  @Override
  protected void onStop() {
    if (!isChangingConfigurations() && viewPager != null) {
      // Make sure current index != selectedPageIndex
      selectedPageIndex = -1;
      updateMissedCalls(viewPager.getCurrentItem());
    }
    super.onStop();
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
    if (allCallsFragment != null && itemDeleteAll != null) {
      // If onPrepareOptionsMenu is called before fragments are loaded, don't do anything.
      final CallLogAdapter adapter = allCallsFragment.getAdapter();
      itemDeleteAll.setVisible(adapter != null && !adapter.isEmpty());
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (!isSafeToCommitTransactions()) {
      return true;
    }

    if (item.getItemId() == android.R.id.home) {
      final Intent intent = new Intent("com.android.dialer.main.impl.MAIN");
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      startActivity(intent);
      return true;
    } else if (item.getItemId() == R.id.delete_all) {
      ClearCallLogDialog.show(getSupportFragmentManager());
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    viewPagerTabs.onPageScrolled(position, positionOffset, positionOffsetPixels);
  }

  @Override
  public void onPageSelected(int position) {
    updateMissedCalls(position);
    selectedPageIndex = position;
    viewPagerTabs.onPageSelected(position);
  }

  @Override
  public void onPageScrollStateChanged(int state) {
    viewPagerTabs.onPageScrollStateChanged(state);
  }

  @Override
  public void onDateSet(long from, long to) {
    switch (viewPager.getCurrentItem()) {
      case TAB_INDEX_STATS:
        statsFragment.onDateSet(from, to);
        break;
    }
  }

  private int getRtlPosition(int position) {
    if (ViewUtil.isRtl()) {
      return viewPagerAdapter.getCount() - 1 - position;
    }
    return position;
  }

  private void updateMissedCalls(int position) {
    if (position == selectedPageIndex) {
      return;
    }
    switch (getRtlPosition(position)) {
      case TAB_INDEX_ALL:
        if (allCallsFragment != null) {
          allCallsFragment.markMissedCallsAsReadAndRemoveNotifications();
        }
        break;
      case TAB_INDEX_MISSED:
        if (missedCallsFragment != null) {
          missedCallsFragment.markMissedCallsAsReadAndRemoveNotifications();
        }
        break;
      case TAB_INDEX_STATS:
        break;
      default:
        throw Assert.createIllegalStateFailException("Invalid position: " + position);
    }
  }

  @Override
  public void onBackPressed() {
    super.onBackPressed();
  }

  /** Adapter for the view pager. */
  public class ViewPagerAdapter extends FragmentPagerAdapter {

    public ViewPagerAdapter(FragmentManager fm) {
      super(fm);
    }

    @Override
    public long getItemId(int position) {
      return getRtlPosition(position);
    }

    @Override
    public Fragment getItem(int position) {
      switch (getRtlPosition(position)) {
        case TAB_INDEX_ALL:
          return new CallLogFragment(
              CallLogQueryHandler.CALL_TYPE_ALL, true /* isCallLogActivity */);
        case TAB_INDEX_MISSED:
          return new CallLogFragment(Calls.MISSED_TYPE, true /* isCallLogActivity */);
        case TAB_INDEX_STATS:
          return new CallStatsFragment();
        default:
          throw new IllegalStateException("No fragment at position " + position);
      }
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
      final Object fragment = super.instantiateItem(container, position);
      switch (getRtlPosition(position)) {
        case TAB_INDEX_ALL:
          allCallsFragment = (CallLogFragment) fragment;
          break;
        case TAB_INDEX_MISSED:
          missedCallsFragment = (CallLogFragment) fragment;
          break;
        case TAB_INDEX_STATS:
          statsFragment = (CallStatsFragment) fragment;
          break;
        default:
          throw Assert.createIllegalStateFailException("Invalid position: " + position);
      }
      return fragment;
    }

    @Override
    public CharSequence getPageTitle(int position) {
      return tabTitles[position];
    }

    @Override
    public int getCount() {
      return TAB_INDEX_COUNT;
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
  }
}
