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
package com.android.dialer.app.calllog;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.support.annotation.VisibleForTesting;
import android.support.design.widget.Snackbar;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;
import com.android.contacts.common.list.ViewPagerTabs;
import com.android.dialer.app.R;
import com.android.dialer.calldetails.OldCallDetailsActivity;
import com.android.dialer.callstats.CallStatsFragment;
import com.android.dialer.callstats.DoubleDatePickerDialog;
import com.android.dialer.common.Assert;
import com.android.dialer.constants.ActivityRequestCodes;
import com.android.dialer.database.CallLogQueryHandler;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ScreenEvent;
import com.android.dialer.logging.UiAction;
import com.android.dialer.performancereport.PerformanceReport;
import com.android.dialer.postcall.PostCall;
import com.android.dialer.util.TransactionSafeActivity;
import com.android.dialer.util.ViewUtil;

/** Activity for viewing call history. */
public class CallLogActivity extends TransactionSafeActivity implements
    ViewPager.OnPageChangeListener, DoubleDatePickerDialog.OnDateSetListener {

  @VisibleForTesting static final int TAB_INDEX_ALL = 0;
  @VisibleForTesting static final int TAB_INDEX_MISSED = 1;
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

    viewPagerAdapter = new ViewPagerAdapter(getFragmentManager());
    viewPager.setAdapter(viewPagerAdapter);
    viewPager.setOffscreenPageLimit(1);
    viewPager.setOnPageChangeListener(this);

    viewPagerTabs = (ViewPagerTabs) findViewById(R.id.viewpager_header);

    viewPagerTabs.setViewPager(viewPager);
    viewPager.setCurrentItem(startingTab);
  }

  @Override
  protected void onResume() {
    // Some calls may not be recorded (eg. from quick contact),
    // so we should restart recording after these calls. (Recorded call is stopped)
    PostCall.restartPerformanceRecordingIfARecentCallExist(this);
    if (!PerformanceReport.isRecording()) {
      PerformanceReport.startRecording();
    }

    isResumed = true;
    super.onResume();
    sendScreenViewForChildFragment();
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
      PerformanceReport.recordClick(UiAction.Type.CLOSE_CALL_HISTORY_WITH_CANCEL_BUTTON);
      final Intent intent = new Intent("com.android.dialer.main.impl.MAIN");
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      startActivity(intent);
      return true;
    } else if (item.getItemId() == R.id.delete_all) {
      ClearCallLogDialog.show(getFragmentManager());
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
    if (isResumed) {
      sendScreenViewForChildFragment();
    }
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

  private void sendScreenViewForChildFragment() {
    Logger.get(this).logScreenView(ScreenEvent.Type.CALL_LOG_FILTER, this);
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
    PerformanceReport.recordClick(UiAction.Type.PRESS_ANDROID_BACK_BUTTON);
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
    if (requestCode == ActivityRequestCodes.DIALTACTS_CALL_DETAILS) {
      if (resultCode == RESULT_OK
          && data != null
          && data.getBooleanExtra(OldCallDetailsActivity.EXTRA_HAS_ENRICHED_CALL_DATA, false)) {
        String number = data.getStringExtra(OldCallDetailsActivity.EXTRA_PHONE_NUMBER);
        Snackbar.make(findViewById(R.id.calllog_frame), getString(R.string.ec_data_deleted), 5_000)
            .setAction(R.string.view_conversation, v -> {
                IntentProvider provider = IntentProvider.getSendSmsIntentProvider(number);
                startActivity(provider.getClickIntent(this));
            })
            .setActionTextColor(getResources().getColor(R.color.dialer_snackbar_action_text_color))
            .show();
      }
    }
    super.onActivityResult(requestCode, resultCode, data);
  }
}
