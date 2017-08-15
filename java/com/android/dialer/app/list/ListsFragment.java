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

package com.android.dialer.app.list;

import static com.android.dialer.app.list.DialtactsPagerAdapter.TAB_COUNT_WITH_VOICEMAIL;
import static com.android.dialer.app.list.DialtactsPagerAdapter.TAB_INDEX_ALL_CONTACTS;
import static com.android.dialer.app.list.DialtactsPagerAdapter.TAB_INDEX_HISTORY;
import static com.android.dialer.app.list.DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL;
import static com.android.dialer.app.list.DialtactsPagerAdapter.TAB_INDEX_VOICEMAIL;

import android.app.Fragment;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Trace;
import android.preference.PreferenceManager;
import android.provider.VoicemailContract;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.contacts.common.list.ViewPagerTabs;
import com.android.dialer.app.R;
import com.android.dialer.app.calllog.CallLogFragment;
import com.android.dialer.app.calllog.CallLogNotificationsService;
import com.android.dialer.app.voicemail.error.VoicemailStatusCorruptionHandler;
import com.android.dialer.app.voicemail.error.VoicemailStatusCorruptionHandler.Source;
import com.android.dialer.common.LogUtil;
import com.android.dialer.database.CallLogQueryHandler;
import com.android.dialer.database.CallLogQueryHandler.Listener;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ScreenEvent;
import com.android.dialer.logging.UiAction;
import com.android.dialer.performancereport.PerformanceReport;
import com.android.dialer.speeddial.SpeedDialFragment;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.voicemailstatus.VisualVoicemailEnabledChecker;
import com.android.dialer.voicemailstatus.VoicemailStatusHelper;
import java.util.ArrayList;

/**
 * Fragment that is used as the main screen of the Dialer.
 *
 * <p>Contains a ViewPager that contains various contact lists like the Speed Dial list and the All
 * Contacts list. This will also eventually contain the logic that allows sliding the ViewPager
 * containing the lists up above the search bar and pin it against the top of the screen.
 */
public class ListsFragment extends Fragment implements OnPageChangeListener, Listener {

  private static final String TAG = "ListsFragment";

  private DialerViewPager mViewPager;
  private ViewPagerTabs mViewPagerTabs;
  private DialtactsPagerAdapter mAdapter;
  private RemoveView mRemoveView;
  private View mRemoveViewContent;
  private Fragment mCurrentPage;
  private SharedPreferences mPrefs;
  private boolean mHasFetchedVoicemailStatus;
  private boolean mShowVoicemailTabAfterVoicemailStatusIsFetched;
  private final ArrayList<OnPageChangeListener> mOnPageChangeListeners = new ArrayList<>();
  /** The position of the currently selected tab. */
  private int mTabIndex = TAB_INDEX_SPEED_DIAL;

  private boolean mPaused;
  private CallLogQueryHandler mCallLogQueryHandler;

  private UiAction.Type[] actionTypeList;

  private final ContentObserver mVoicemailStatusObserver =
      new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
          super.onChange(selfChange);
          mCallLogQueryHandler.fetchVoicemailStatus();
        }
      };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    LogUtil.d("ListsFragment.onCreate", null);
    Trace.beginSection(TAG + " onCreate");
    super.onCreate(savedInstanceState);
    mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
    Trace.endSection();
  }

  @Override
  public void onResume() {
    LogUtil.d("ListsFragment.onResume", null);
    Trace.beginSection(TAG + " onResume");
    super.onResume();

    mPaused = false;

    if (getUserVisibleHint()) {
      sendScreenViewForCurrentPosition();
    }

    // Fetch voicemail status to determine if we should show the voicemail tab.
    mCallLogQueryHandler =
        new CallLogQueryHandler(getActivity(), getActivity().getContentResolver(), this);
    mCallLogQueryHandler.fetchVoicemailStatus();
    mCallLogQueryHandler.fetchMissedCallsUnreadCount();
    Trace.endSection();
    mCurrentPage = mAdapter.getItem(mViewPager.getCurrentItem());
    if (mCurrentPage instanceof CallLogFragment) {
      ((CallLogFragment) mCurrentPage).onVisible();
    }
  }

  @Override
  public void onPause() {
    LogUtil.d("ListsFragment.onPause", null);
    if (mCurrentPage instanceof CallLogFragment) {
      ((CallLogFragment) mCurrentPage).onNotVisible();
    }
    super.onPause();

    mPaused = true;
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    mViewPager.removeOnPageChangeListener(this);
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    LogUtil.d("ListsFragment.onCreateView", null);
    Trace.beginSection(TAG + " onCreateView");
    Trace.beginSection(TAG + " inflate view");
    final View parentView = inflater.inflate(R.layout.lists_fragment, container, false);
    Trace.endSection();
    Trace.beginSection(TAG + " setup views");

    actionTypeList = new UiAction.Type[TAB_COUNT_WITH_VOICEMAIL];
    actionTypeList[TAB_INDEX_SPEED_DIAL] = UiAction.Type.CHANGE_TAB_TO_FAVORITE;
    actionTypeList[TAB_INDEX_HISTORY] = UiAction.Type.CHANGE_TAB_TO_CALL_LOG;
    actionTypeList[TAB_INDEX_ALL_CONTACTS] = UiAction.Type.CHANGE_TAB_TO_CONTACTS;
    actionTypeList[TAB_INDEX_VOICEMAIL] = UiAction.Type.CHANGE_TAB_TO_VOICEMAIL;

    String[] tabTitles = new String[TAB_COUNT_WITH_VOICEMAIL];
    tabTitles[TAB_INDEX_SPEED_DIAL] = getResources().getString(R.string.tab_speed_dial);
    tabTitles[TAB_INDEX_HISTORY] = getResources().getString(R.string.tab_history);
    tabTitles[TAB_INDEX_ALL_CONTACTS] = getResources().getString(R.string.tab_all_contacts);
    tabTitles[TAB_INDEX_VOICEMAIL] = getResources().getString(R.string.tab_voicemail);

    int[] tabIcons = new int[TAB_COUNT_WITH_VOICEMAIL];
    tabIcons[TAB_INDEX_SPEED_DIAL] = R.drawable.quantum_ic_grade_white_24;
    tabIcons[TAB_INDEX_HISTORY] = R.drawable.quantum_ic_schedule_white_24;
    tabIcons[TAB_INDEX_ALL_CONTACTS] = R.drawable.quantum_ic_people_white_24;
    tabIcons[TAB_INDEX_VOICEMAIL] = R.drawable.quantum_ic_voicemail_white_24;

    mViewPager = (DialerViewPager) parentView.findViewById(R.id.lists_pager);
    mAdapter =
        new DialtactsPagerAdapter(
            getContext(),
            getChildFragmentManager(),
            tabTitles,
            mPrefs.getBoolean(
                VisualVoicemailEnabledChecker.PREF_KEY_HAS_ACTIVE_VOICEMAIL_PROVIDER, false));
    mViewPager.setAdapter(mAdapter);
    mViewPager.setOffscreenPageLimit(TAB_COUNT_WITH_VOICEMAIL - 1);
    mViewPager.addOnPageChangeListener(this);
    showTab(TAB_INDEX_SPEED_DIAL);

    mViewPagerTabs = (ViewPagerTabs) parentView.findViewById(R.id.lists_pager_header);
    mViewPagerTabs.configureTabIcons(tabIcons);
    mViewPagerTabs.setViewPager(mViewPager);
    addOnPageChangeListener(mViewPagerTabs);
    mRemoveView = (RemoveView) parentView.findViewById(R.id.remove_view);
    mRemoveViewContent = parentView.findViewById(R.id.remove_view_content);

    if (PermissionsUtil.hasReadVoicemailPermissions(getContext())
        && PermissionsUtil.hasAddVoicemailPermissions(getContext())) {
      getActivity()
          .getContentResolver()
          .registerContentObserver(
              VoicemailContract.Status.CONTENT_URI, true, mVoicemailStatusObserver);
    } else {
      LogUtil.w("ListsFragment.onCreateView", "no voicemail read permissions");
    }

    Trace.endSection();
    Trace.endSection();
    return parentView;
  }

  @Override
  public void onDestroy() {
    getActivity().getContentResolver().unregisterContentObserver(mVoicemailStatusObserver);
    super.onDestroy();
  }

  public void addOnPageChangeListener(OnPageChangeListener onPageChangeListener) {
    if (!mOnPageChangeListeners.contains(onPageChangeListener)) {
      mOnPageChangeListeners.add(onPageChangeListener);
    }
  }

  /**
   * Shows the tab with the specified index. If the voicemail tab index is specified, but the
   * voicemail status hasn't been fetched, it will show the speed dial tab and try to show the
   * voicemail tab after the voicemail status has been fetched.
   */
  public void showTab(int index) {
    if (index == TAB_INDEX_VOICEMAIL) {
      if (mAdapter.hasActiveVoicemailProvider()) {
        mViewPager.setCurrentItem(mAdapter.getRtlPosition(TAB_INDEX_VOICEMAIL));
      } else if (!mHasFetchedVoicemailStatus) {
        // Try to show the voicemail tab after the voicemail status returns.
        mShowVoicemailTabAfterVoicemailStatusIsFetched = true;
      }
    } else if (index < getTabCount()) {
      mViewPager.setCurrentItem(mAdapter.getRtlPosition(index));
    }
  }

  @Override
  public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    mTabIndex = mAdapter.getRtlPosition(position);

    final int count = mOnPageChangeListeners.size();
    for (int i = 0; i < count; i++) {
      mOnPageChangeListeners.get(i).onPageScrolled(position, positionOffset, positionOffsetPixels);
    }
  }

  @Override
  public void onPageSelected(int position) {
    PerformanceReport.recordClick(actionTypeList[position]);

    LogUtil.i("ListsFragment.onPageSelected", "position: %d", position);
    mTabIndex = mAdapter.getRtlPosition(position);

    // Show the tab which has been selected instead.
    mShowVoicemailTabAfterVoicemailStatusIsFetched = false;

    final int count = mOnPageChangeListeners.size();
    for (int i = 0; i < count; i++) {
      mOnPageChangeListeners.get(i).onPageSelected(position);
    }
    sendScreenViewForCurrentPosition();

    if (mCurrentPage instanceof CallLogFragment) {
      ((CallLogFragment) mCurrentPage).onNotVisible();
    }
    mCurrentPage = mAdapter.getItem(position);
    if (mCurrentPage instanceof CallLogFragment) {
      ((CallLogFragment) mCurrentPage).onVisible();
    }
  }

  @Override
  public void onPageScrollStateChanged(int state) {
    final int count = mOnPageChangeListeners.size();
    for (int i = 0; i < count; i++) {
      mOnPageChangeListeners.get(i).onPageScrollStateChanged(state);
    }
  }

  @Override
  public void onVoicemailStatusFetched(Cursor statusCursor) {
    mHasFetchedVoicemailStatus = true;

    if (getActivity() == null || mPaused) {
      return;
    }

    VoicemailStatusCorruptionHandler.maybeFixVoicemailStatus(
        getContext(), statusCursor, Source.Activity);

    // Update hasActiveVoicemailProvider, which controls the number of tabs displayed.
    boolean hasActiveVoicemailProvider =
        VoicemailStatusHelper.getNumberActivityVoicemailSources(statusCursor) > 0;
    if (hasActiveVoicemailProvider != mAdapter.hasActiveVoicemailProvider()) {
      mAdapter.setHasActiveVoicemailProvider(hasActiveVoicemailProvider);
      mAdapter.notifyDataSetChanged();

      if (hasActiveVoicemailProvider) {
        Logger.get(getContext()).logImpression(DialerImpression.Type.VVM_TAB_VISIBLE);
        mViewPagerTabs.updateTab(TAB_INDEX_VOICEMAIL);
      } else {
        mViewPagerTabs.removeTab(TAB_INDEX_VOICEMAIL);
        mAdapter.removeVoicemailFragment(getChildFragmentManager());
      }

      mPrefs
          .edit()
          .putBoolean(
              VisualVoicemailEnabledChecker.PREF_KEY_HAS_ACTIVE_VOICEMAIL_PROVIDER,
              hasActiveVoicemailProvider)
          .apply();
    }

    if (hasActiveVoicemailProvider) {
      mCallLogQueryHandler.fetchVoicemailUnreadCount();
    }

    if (mAdapter.hasActiveVoicemailProvider() && mShowVoicemailTabAfterVoicemailStatusIsFetched) {
      mShowVoicemailTabAfterVoicemailStatusIsFetched = false;
      showTab(TAB_INDEX_VOICEMAIL);
    }
  }

  @Override
  public void onVoicemailUnreadCountFetched(Cursor cursor) {
    if (getActivity() == null || getActivity().isFinishing() || cursor == null) {
      return;
    }

    int count = 0;
    try {
      count = cursor.getCount();
    } finally {
      cursor.close();
    }

    mViewPagerTabs.setUnreadCount(count, TAB_INDEX_VOICEMAIL);
    mViewPagerTabs.updateTab(TAB_INDEX_VOICEMAIL);
  }

  @Override
  public void onMissedCallsUnreadCountFetched(Cursor cursor) {
    if (getActivity() == null || getActivity().isFinishing() || cursor == null) {
      return;
    }

    int count = 0;
    try {
      count = cursor.getCount();
    } finally {
      cursor.close();
    }

    mViewPagerTabs.setUnreadCount(count, TAB_INDEX_HISTORY);
    mViewPagerTabs.updateTab(TAB_INDEX_HISTORY);
  }

  @Override
  public boolean onCallsFetched(Cursor statusCursor) {
    // Return false; did not take ownership of cursor
    return false;
  }

  public int getCurrentTabIndex() {
    return mTabIndex;
  }

  /**
   * External method to update unread count because the unread count changes when the user expands a
   * voicemail in the call log or when the user expands an unread call in the call history tab.
   */
  public void updateTabUnreadCounts() {
    if (mCallLogQueryHandler != null) {
      mCallLogQueryHandler.fetchMissedCallsUnreadCount();
      if (mAdapter.hasActiveVoicemailProvider()) {
        mCallLogQueryHandler.fetchVoicemailUnreadCount();
      }
    }
  }

  /** External method to mark all missed calls as read. */
  public void markMissedCallsAsReadAndRemoveNotifications() {
    if (mCallLogQueryHandler != null) {
      mCallLogQueryHandler.markMissedCallsAsRead();
      CallLogNotificationsService.cancelAllMissedCalls(getContext());
    }
  }

  public void showRemoveView(boolean show) {
    mRemoveViewContent.setVisibility(show ? View.VISIBLE : View.GONE);
    mRemoveView.setAlpha(show ? 0 : 1);
    mRemoveView.animate().alpha(show ? 1 : 0).start();
  }

  public void showMultiSelectRemoveView(boolean show) {
    mViewPagerTabs.setVisibility(show ? View.GONE : View.VISIBLE);
    mViewPager.setEnableSwipingPages(!show);
  }

  public boolean hasFrequents() {
    Fragment page = mAdapter.getItem(mAdapter.getRtlPosition(TAB_INDEX_SPEED_DIAL));
    return page instanceof OldSpeedDialFragment
        ? ((OldSpeedDialFragment) page).hasFrequents()
        : ((SpeedDialFragment) page).hasFrequents();
  }

  public RemoveView getRemoveView() {
    return mRemoveView;
  }

  public int getTabCount() {
    return mAdapter.getCount();
  }

  public void sendScreenViewForCurrentPosition() {
    if (!isResumed()) {
      return;
    }

    ScreenEvent.Type screenType;
    switch (getCurrentTabIndex()) {
      case TAB_INDEX_SPEED_DIAL:
        screenType = ScreenEvent.Type.SPEED_DIAL;
        break;
      case TAB_INDEX_HISTORY:
        screenType = ScreenEvent.Type.CALL_LOG;
        break;
      case TAB_INDEX_ALL_CONTACTS:
        screenType = ScreenEvent.Type.ALL_CONTACTS;
        break;
      case TAB_INDEX_VOICEMAIL:
        screenType = ScreenEvent.Type.VOICEMAIL_LOG;
        break;
      default:
        return;
    }
    Logger.get(getActivity()).logScreenView(screenType, getActivity());
  }
}
