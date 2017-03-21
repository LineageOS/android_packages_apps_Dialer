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

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Trace;
import android.preference.PreferenceManager;
import android.provider.VoicemailContract;
import android.support.annotation.Nullable;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.contacts.common.list.ViewPagerTabs;
import com.android.dialer.app.R;
import com.android.dialer.app.calllog.CallLogFragment;
import com.android.dialer.app.calllog.CallLogNotificationsService;
import com.android.dialer.app.calllog.VisualVoicemailCallLogFragment;
import com.android.dialer.app.voicemail.error.VoicemailStatusCorruptionHandler;
import com.android.dialer.app.voicemail.error.VoicemailStatusCorruptionHandler.Source;
import com.android.dialer.common.LogUtil;
import com.android.dialer.database.CallLogQueryHandler;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.nano.DialerImpression;
import com.android.dialer.logging.nano.ScreenEvent;
import com.android.dialer.util.ViewUtil;
import com.android.dialer.voicemailstatus.VisualVoicemailEnabledChecker;
import com.android.dialer.voicemailstatus.VoicemailStatusHelper;
import java.util.ArrayList;
import java.util.List;

/**
 * Fragment that is used as the main screen of the Dialer.
 *
 * <p>Contains a ViewPager that contains various contact lists like the Speed Dial list and the All
 * Contacts list. This will also eventually contain the logic that allows sliding the ViewPager
 * containing the lists up above the search bar and pin it against the top of the screen.
 */
public class ListsFragment extends Fragment
    implements ViewPager.OnPageChangeListener, CallLogQueryHandler.Listener {

  /** Every fragment in the list show implement this interface. */
  public interface ListsPage {

    /**
     * Called when the page is resumed, including selecting the page or activity resume. Note: This
     * is called before the page fragment is attached to a activity.
     *
     * @param activity the activity hosting the ListFragment
     */
    void onPageResume(@Nullable Activity activity);

    /**
     * Called when the page is paused, including selecting another page or activity pause. Note:
     * This is called after the page fragment is detached from a activity.
     *
     * @param activity the activity hosting the ListFragment
     */
    void onPagePause(@Nullable Activity activity);
  }

  public static final int TAB_INDEX_SPEED_DIAL = 0;
  public static final int TAB_INDEX_HISTORY = 1;
  public static final int TAB_INDEX_ALL_CONTACTS = 2;
  public static final int TAB_INDEX_VOICEMAIL = 3;
  public static final int TAB_COUNT_DEFAULT = 3;
  public static final int TAB_COUNT_WITH_VOICEMAIL = 4;
  private static final String TAG = "ListsFragment";
  private ViewPager mViewPager;
  private ViewPagerTabs mViewPagerTabs;
  private ViewPagerAdapter mViewPagerAdapter;
  private RemoveView mRemoveView;
  private View mRemoveViewContent;
  private SpeedDialFragment mSpeedDialFragment;
  private CallLogFragment mHistoryFragment;
  private AllContactsFragment mAllContactsFragment;
  private CallLogFragment mVoicemailFragment;
  private ListsPage mCurrentPage;
  private SharedPreferences mPrefs;
  private boolean mHasActiveVoicemailProvider;
  private boolean mHasFetchedVoicemailStatus;
  private boolean mShowVoicemailTabAfterVoicemailStatusIsFetched;
  private VoicemailStatusHelper mVoicemailStatusHelper;
  private final ArrayList<OnPageChangeListener> mOnPageChangeListeners = new ArrayList<>();
  private String[] mTabTitles;
  private int[] mTabIcons;
  /** The position of the currently selected tab. */
  private int mTabIndex = TAB_INDEX_SPEED_DIAL;

  private CallLogQueryHandler mCallLogQueryHandler;

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

    mVoicemailStatusHelper = new VoicemailStatusHelper();
    mHasFetchedVoicemailStatus = false;

    mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
    mHasActiveVoicemailProvider =
        mPrefs.getBoolean(
            VisualVoicemailEnabledChecker.PREF_KEY_HAS_ACTIVE_VOICEMAIL_PROVIDER, false);

    Trace.endSection();
  }

  @Override
  public void onResume() {
    LogUtil.d("ListsFragment.onResume", null);
    Trace.beginSection(TAG + " onResume");
    super.onResume();

    if (getUserVisibleHint()) {
      sendScreenViewForCurrentPosition();
    }

    // Fetch voicemail status to determine if we should show the voicemail tab.
    mCallLogQueryHandler =
        new CallLogQueryHandler(getActivity(), getActivity().getContentResolver(), this);
    mCallLogQueryHandler.fetchVoicemailStatus();
    mCallLogQueryHandler.fetchMissedCallsUnreadCount();
    Trace.endSection();
    mCurrentPage = getListsPage(mViewPager.getCurrentItem());
    if (mCurrentPage != null) {
      mCurrentPage.onPageResume(getActivity());
    }
  }

  @Override
  public void onPause() {
    LogUtil.d("ListsFragment.onPause", null);
    if (mCurrentPage != null) {
      mCurrentPage.onPagePause(getActivity());
    }
    super.onPause();
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
    mViewPager = (ViewPager) parentView.findViewById(R.id.lists_pager);
    mViewPagerAdapter = new ViewPagerAdapter(getChildFragmentManager());
    mViewPager.setAdapter(mViewPagerAdapter);
    mViewPager.setOffscreenPageLimit(TAB_COUNT_WITH_VOICEMAIL - 1);
    mViewPager.addOnPageChangeListener(this);
    showTab(TAB_INDEX_SPEED_DIAL);

    mTabTitles = new String[TAB_COUNT_WITH_VOICEMAIL];
    mTabTitles[TAB_INDEX_SPEED_DIAL] = getResources().getString(R.string.tab_speed_dial);
    mTabTitles[TAB_INDEX_HISTORY] = getResources().getString(R.string.tab_history);
    mTabTitles[TAB_INDEX_ALL_CONTACTS] = getResources().getString(R.string.tab_all_contacts);
    mTabTitles[TAB_INDEX_VOICEMAIL] = getResources().getString(R.string.tab_voicemail);

    mTabIcons = new int[TAB_COUNT_WITH_VOICEMAIL];
    mTabIcons[TAB_INDEX_SPEED_DIAL] = R.drawable.ic_grade_24dp;
    mTabIcons[TAB_INDEX_HISTORY] = R.drawable.ic_schedule_24dp;
    mTabIcons[TAB_INDEX_ALL_CONTACTS] = R.drawable.ic_people_24dp;
    mTabIcons[TAB_INDEX_VOICEMAIL] = R.drawable.ic_voicemail_24dp;

    mViewPagerTabs = (ViewPagerTabs) parentView.findViewById(R.id.lists_pager_header);
    mViewPagerTabs.configureTabIcons(mTabIcons);
    mViewPagerTabs.setViewPager(mViewPager);
    addOnPageChangeListener(mViewPagerTabs);

    mRemoveView = (RemoveView) parentView.findViewById(R.id.remove_view);
    mRemoveViewContent = parentView.findViewById(R.id.remove_view_content);

    getActivity()
        .getContentResolver()
        .registerContentObserver(
            VoicemailContract.Status.CONTENT_URI, true, mVoicemailStatusObserver);

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
   * voicemail status hasn't been fetched, it will try to show the tab after the voicemail status
   * has been fetched.
   */
  public void showTab(int index) {
    if (index == TAB_INDEX_VOICEMAIL) {
      if (mHasActiveVoicemailProvider) {
        mViewPager.setCurrentItem(getRtlPosition(TAB_INDEX_VOICEMAIL));
      } else if (!mHasFetchedVoicemailStatus) {
        // Try to show the voicemail tab after the voicemail status returns.
        mShowVoicemailTabAfterVoicemailStatusIsFetched = true;
      }
    } else if (index < getTabCount()) {
      mViewPager.setCurrentItem(getRtlPosition(index));
    }
  }

  @Override
  public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    mTabIndex = getRtlPosition(position);

    final int count = mOnPageChangeListeners.size();
    for (int i = 0; i < count; i++) {
      mOnPageChangeListeners.get(i).onPageScrolled(position, positionOffset, positionOffsetPixels);
    }
  }

  @Override
  public void onPageSelected(int position) {
    LogUtil.i("ListsFragment.onPageSelected", "position: %d", position);
    mTabIndex = getRtlPosition(position);

    // Show the tab which has been selected instead.
    mShowVoicemailTabAfterVoicemailStatusIsFetched = false;

    final int count = mOnPageChangeListeners.size();
    for (int i = 0; i < count; i++) {
      mOnPageChangeListeners.get(i).onPageSelected(position);
    }
    sendScreenViewForCurrentPosition();

    if (mCurrentPage != null) {
      mCurrentPage.onPagePause(getActivity());
    }
    mCurrentPage = getListsPage(position);
    if (mCurrentPage != null) {
      mCurrentPage.onPageResume(getActivity());
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

    if (getActivity() == null || getActivity().isFinishing()) {
      return;
    }

    VoicemailStatusCorruptionHandler.maybeFixVoicemailStatus(
        getContext(), statusCursor, Source.Activity);

    // Update mHasActiveVoicemailProvider, which controls the number of tabs displayed.
    boolean hasActiveVoicemailProvider =
        mVoicemailStatusHelper.getNumberActivityVoicemailSources(statusCursor) > 0;
    if (hasActiveVoicemailProvider != mHasActiveVoicemailProvider) {
      mHasActiveVoicemailProvider = hasActiveVoicemailProvider;
      mViewPagerAdapter.notifyDataSetChanged();

      if (hasActiveVoicemailProvider) {
        Logger.get(getContext()).logImpression(DialerImpression.Type.VVM_TAB_VISIBLE);
        mViewPagerTabs.updateTab(TAB_INDEX_VOICEMAIL);
      } else {
        mViewPagerTabs.removeTab(TAB_INDEX_VOICEMAIL);
        removeVoicemailFragment();
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

    if (mHasActiveVoicemailProvider && mShowVoicemailTabAfterVoicemailStatusIsFetched) {
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
      if (mHasActiveVoicemailProvider) {
        mCallLogQueryHandler.fetchVoicemailUnreadCount();
      }
    }
  }

  /** External method to mark all missed calls as read. */
  public void markMissedCallsAsReadAndRemoveNotifications() {
    if (mCallLogQueryHandler != null) {
      mCallLogQueryHandler.markMissedCallsAsRead();
      CallLogNotificationsService.markNewMissedCallsAsOld(getContext(), null);
    }
  }

  public void showRemoveView(boolean show) {
    mRemoveViewContent.setVisibility(show ? View.VISIBLE : View.GONE);
    mRemoveView.setAlpha(show ? 0 : 1);
    mRemoveView.animate().alpha(show ? 1 : 0).start();
  }

  public SpeedDialFragment getSpeedDialFragment() {
    return mSpeedDialFragment;
  }

  public RemoveView getRemoveView() {
    return mRemoveView;
  }

  public int getTabCount() {
    return mViewPagerAdapter.getCount();
  }

  private int getRtlPosition(int position) {
    if (ViewUtil.isRtl()) {
      return mViewPagerAdapter.getCount() - 1 - position;
    }
    return position;
  }

  public void sendScreenViewForCurrentPosition() {
    if (!isResumed()) {
      return;
    }

    int screenType;
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

  private void removeVoicemailFragment() {
    if (mVoicemailFragment != null) {
      getChildFragmentManager()
          .beginTransaction()
          .remove(mVoicemailFragment)
          .commitAllowingStateLoss();
      mVoicemailFragment = null;
    }
  }

  private ListsPage getListsPage(int position) {
    switch (getRtlPosition(position)) {
      case TAB_INDEX_SPEED_DIAL:
        return mSpeedDialFragment;
      case TAB_INDEX_HISTORY:
        return mHistoryFragment;
      case TAB_INDEX_ALL_CONTACTS:
        return mAllContactsFragment;
      case TAB_INDEX_VOICEMAIL:
        return mVoicemailFragment;
    }
    throw new IllegalStateException("No fragment at position " + position);
  }

  public class ViewPagerAdapter extends FragmentPagerAdapter {

    private final List<Fragment> mFragments = new ArrayList<>();

    public ViewPagerAdapter(FragmentManager fm) {
      super(fm);
      for (int i = 0; i < TAB_COUNT_WITH_VOICEMAIL; i++) {
        mFragments.add(null);
      }
    }

    @Override
    public long getItemId(int position) {
      return getRtlPosition(position);
    }

    @Override
    public Fragment getItem(int position) {
      LogUtil.d("ViewPagerAdapter.getItem", "position: %d", position);
      switch (getRtlPosition(position)) {
        case TAB_INDEX_SPEED_DIAL:
          if (mSpeedDialFragment == null) {
            mSpeedDialFragment = new SpeedDialFragment();
          }
          return mSpeedDialFragment;
        case TAB_INDEX_HISTORY:
          if (mHistoryFragment == null) {
            mHistoryFragment = new CallLogFragment(CallLogQueryHandler.CALL_TYPE_ALL);
          }
          return mHistoryFragment;
        case TAB_INDEX_ALL_CONTACTS:
          if (mAllContactsFragment == null) {
            mAllContactsFragment = new AllContactsFragment();
          }
          return mAllContactsFragment;
        case TAB_INDEX_VOICEMAIL:
          if (mVoicemailFragment == null) {
            mVoicemailFragment = new VisualVoicemailCallLogFragment();
            LogUtil.v(
                "ViewPagerAdapter.getItem",
                "new VisualVoicemailCallLogFragment: %s",
                mVoicemailFragment);
          }
          return mVoicemailFragment;
      }
      throw new IllegalStateException("No fragment at position " + position);
    }

    @Override
    public Fragment instantiateItem(ViewGroup container, int position) {
      LogUtil.d("ViewPagerAdapter.instantiateItem", "position: %d", position);
      // On rotation the FragmentManager handles rotation. Therefore getItem() isn't called.
      // Copy the fragments that the FragmentManager finds so that we can store them in
      // instance variables for later.
      final Fragment fragment = (Fragment) super.instantiateItem(container, position);
      if (fragment instanceof SpeedDialFragment) {
        mSpeedDialFragment = (SpeedDialFragment) fragment;
      } else if (fragment instanceof CallLogFragment && position == TAB_INDEX_HISTORY) {
        mHistoryFragment = (CallLogFragment) fragment;
      } else if (fragment instanceof AllContactsFragment) {
        mAllContactsFragment = (AllContactsFragment) fragment;
      } else if (fragment instanceof CallLogFragment && position == TAB_INDEX_VOICEMAIL) {
        mVoicemailFragment = (CallLogFragment) fragment;
        LogUtil.v("ViewPagerAdapter.instantiateItem", mVoicemailFragment.toString());
      }
      mFragments.set(position, fragment);
      return fragment;
    }

    /**
     * When {@link android.support.v4.view.PagerAdapter#notifyDataSetChanged} is called, this method
     * is called on all pages to determine whether they need to be recreated. When the voicemail tab
     * is removed, the view needs to be recreated by returning POSITION_NONE. If
     * notifyDataSetChanged is called for some other reason, the voicemail tab is recreated only if
     * it is active. All other tabs do not need to be recreated and POSITION_UNCHANGED is returned.
     */
    @Override
    public int getItemPosition(Object object) {
      return !mHasActiveVoicemailProvider && mFragments.indexOf(object) == TAB_INDEX_VOICEMAIL
          ? POSITION_NONE
          : POSITION_UNCHANGED;
    }

    @Override
    public int getCount() {
      return mHasActiveVoicemailProvider ? TAB_COUNT_WITH_VOICEMAIL : TAB_COUNT_DEFAULT;
    }

    @Override
    public CharSequence getPageTitle(int position) {
      return mTabTitles[position];
    }
  }
}
