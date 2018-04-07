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

import static android.support.v4.view.ViewPager.SCROLL_STATE_SETTLING;

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
import com.android.dialer.app.calllog.CallLogFragment.CallLogFragmentListener;
import com.android.dialer.app.calllog.CallLogNotificationsService;
import com.android.dialer.app.calllog.VisualVoicemailCallLogFragment;
import com.android.dialer.common.LogUtil;
import com.android.dialer.database.CallLogQueryHandler;
import com.android.dialer.database.CallLogQueryHandler.Listener;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ScreenEvent;
import com.android.dialer.logging.UiAction;
import com.android.dialer.performancereport.PerformanceReport;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.voicemail.listui.error.VoicemailStatusCorruptionHandler;
import com.android.dialer.voicemail.listui.error.VoicemailStatusCorruptionHandler.Source;
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
public class ListsFragment extends Fragment
    implements OnPageChangeListener, Listener, CallLogFragmentListener {

  private static final String TAG = "ListsFragment";

  private DialerViewPager viewPager;
  private ViewPagerTabs viewPagerTabs;
  private DialtactsPagerAdapter adapter;
  private RemoveView removeView;
  private View removeViewContent;
  private Fragment currentPage;
  private SharedPreferences prefs;
  private boolean hasFetchedVoicemailStatus;
  private boolean showVoicemailTabAfterVoicemailStatusIsFetched;
  private final ArrayList<OnPageChangeListener> onPageChangeListeners = new ArrayList<>();
  /** The position of the currently selected tab. */
  private int tabIndex = DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL;

  private boolean paused;
  private CallLogQueryHandler callLogQueryHandler;

  private UiAction.Type[] actionTypeList;
  private final DialerImpression.Type[] swipeImpressionList =
      new DialerImpression.Type[DialtactsPagerAdapter.TAB_COUNT_WITH_VOICEMAIL];
  private final DialerImpression.Type[] clickImpressionList =
      new DialerImpression.Type[DialtactsPagerAdapter.TAB_COUNT_WITH_VOICEMAIL];

  // Only for detecting page selected by swiping or clicking.
  private boolean onPageScrolledBeforeScrollStateSettling;

  private final ContentObserver voicemailStatusObserver =
      new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
          super.onChange(selfChange);
          callLogQueryHandler.fetchVoicemailStatus();
        }
      };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    LogUtil.d("ListsFragment.onCreate", null);
    Trace.beginSection(TAG + " onCreate");
    super.onCreate(savedInstanceState);
    prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
    Trace.endSection();
  }

  @Override
  public void onResume() {
    LogUtil.enterBlock("ListsFragment.onResume");
    Trace.beginSection(TAG + " onResume");
    super.onResume();

    paused = false;

    if (getUserVisibleHint()) {
      sendScreenViewForCurrentPosition();
    }

    // Fetch voicemail status to determine if we should show the voicemail tab.
    callLogQueryHandler =
        new CallLogQueryHandler(getActivity(), getActivity().getContentResolver(), this);
    callLogQueryHandler.fetchVoicemailStatus();
    callLogQueryHandler.fetchMissedCallsUnreadCount();
    Trace.endSection();
    currentPage = adapter.getItem(viewPager.getCurrentItem());
  }

  @Override
  public void onPause() {
    LogUtil.enterBlock("ListsFragment.onPause");
    super.onPause();

    paused = true;
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    viewPager.removeOnPageChangeListener(this);
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    LogUtil.enterBlock("ListsFragment.onCreateView");
    Trace.beginSection(TAG + " onCreateView");
    Trace.beginSection(TAG + " inflate view");
    final View parentView = inflater.inflate(R.layout.lists_fragment, container, false);
    Trace.endSection();
    Trace.beginSection(TAG + " setup views");

    actionTypeList = new UiAction.Type[DialtactsPagerAdapter.TAB_COUNT_WITH_VOICEMAIL];
    actionTypeList[DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL] =
        UiAction.Type.CHANGE_TAB_TO_FAVORITE;
    actionTypeList[DialtactsPagerAdapter.TAB_INDEX_HISTORY] = UiAction.Type.CHANGE_TAB_TO_CALL_LOG;
    actionTypeList[DialtactsPagerAdapter.TAB_INDEX_ALL_CONTACTS] =
        UiAction.Type.CHANGE_TAB_TO_CONTACTS;
    actionTypeList[DialtactsPagerAdapter.TAB_INDEX_VOICEMAIL] =
        UiAction.Type.CHANGE_TAB_TO_VOICEMAIL;

    swipeImpressionList[DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL] =
        DialerImpression.Type.SWITCH_TAB_TO_FAVORITE_BY_SWIPE;
    swipeImpressionList[DialtactsPagerAdapter.TAB_INDEX_HISTORY] =
        DialerImpression.Type.SWITCH_TAB_TO_CALL_LOG_BY_SWIPE;
    swipeImpressionList[DialtactsPagerAdapter.TAB_INDEX_ALL_CONTACTS] =
        DialerImpression.Type.SWITCH_TAB_TO_CONTACTS_BY_SWIPE;
    swipeImpressionList[DialtactsPagerAdapter.TAB_INDEX_VOICEMAIL] =
        DialerImpression.Type.SWITCH_TAB_TO_VOICEMAIL_BY_SWIPE;

    clickImpressionList[DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL] =
        DialerImpression.Type.SWITCH_TAB_TO_FAVORITE_BY_CLICK;
    clickImpressionList[DialtactsPagerAdapter.TAB_INDEX_HISTORY] =
        DialerImpression.Type.SWITCH_TAB_TO_CALL_LOG_BY_CLICK;
    clickImpressionList[DialtactsPagerAdapter.TAB_INDEX_ALL_CONTACTS] =
        DialerImpression.Type.SWITCH_TAB_TO_CONTACTS_BY_CLICK;
    clickImpressionList[DialtactsPagerAdapter.TAB_INDEX_VOICEMAIL] =
        DialerImpression.Type.SWITCH_TAB_TO_VOICEMAIL_BY_CLICK;

    String[] tabTitles = new String[DialtactsPagerAdapter.TAB_COUNT_WITH_VOICEMAIL];
    tabTitles[DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL] =
        getResources().getString(R.string.tab_speed_dial);
    tabTitles[DialtactsPagerAdapter.TAB_INDEX_HISTORY] =
        getResources().getString(R.string.tab_history);
    tabTitles[DialtactsPagerAdapter.TAB_INDEX_ALL_CONTACTS] =
        getResources().getString(R.string.tab_all_contacts);
    tabTitles[DialtactsPagerAdapter.TAB_INDEX_VOICEMAIL] =
        getResources().getString(R.string.tab_voicemail);

    int[] tabIcons = new int[DialtactsPagerAdapter.TAB_COUNT_WITH_VOICEMAIL];
    tabIcons[DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL] = R.drawable.quantum_ic_grade_white_24;
    tabIcons[DialtactsPagerAdapter.TAB_INDEX_HISTORY] = R.drawable.quantum_ic_schedule_white_24;
    tabIcons[DialtactsPagerAdapter.TAB_INDEX_ALL_CONTACTS] = R.drawable.quantum_ic_people_white_24;
    tabIcons[DialtactsPagerAdapter.TAB_INDEX_VOICEMAIL] = R.drawable.quantum_ic_voicemail_white_24;

    viewPager = (DialerViewPager) parentView.findViewById(R.id.lists_pager);
    adapter =
        new DialtactsPagerAdapter(
            getChildFragmentManager(),
            tabTitles,
            prefs.getBoolean(
                VisualVoicemailEnabledChecker.PREF_KEY_HAS_ACTIVE_VOICEMAIL_PROVIDER, false));
    viewPager.setAdapter(adapter);

    // This is deliberate. See cl/172018946 for the app startup implications of using 1 here
    // versus loading more fragments upfront.
    viewPager.setOffscreenPageLimit(1);

    viewPager.addOnPageChangeListener(this);
    showTab(DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL);

    viewPagerTabs = (ViewPagerTabs) parentView.findViewById(R.id.lists_pager_header);
    viewPagerTabs.configureTabIcons(tabIcons);
    viewPagerTabs.setViewPager(viewPager);
    addOnPageChangeListener(viewPagerTabs);
    removeView = (RemoveView) parentView.findViewById(R.id.remove_view);
    removeViewContent = parentView.findViewById(R.id.remove_view_content);

    if (PermissionsUtil.hasReadVoicemailPermissions(getContext())
        && PermissionsUtil.hasAddVoicemailPermissions(getContext())) {
      getActivity()
          .getContentResolver()
          .registerContentObserver(
              VoicemailContract.Status.CONTENT_URI, true, voicemailStatusObserver);
    } else {
      LogUtil.w("ListsFragment.onCreateView", "no voicemail read permissions");
    }

    Trace.endSection();
    Trace.endSection();
    return parentView;
  }

  @Override
  public void onDestroy() {
    getActivity().getContentResolver().unregisterContentObserver(voicemailStatusObserver);
    super.onDestroy();
  }

  public void addOnPageChangeListener(OnPageChangeListener onPageChangeListener) {
    if (!onPageChangeListeners.contains(onPageChangeListener)) {
      onPageChangeListeners.add(onPageChangeListener);
    }
  }

  /**
   * Shows the tab with the specified index. If the voicemail tab index is specified, but the
   * voicemail status hasn't been fetched, it will show the speed dial tab and try to show the
   * voicemail tab after the voicemail status has been fetched.
   */
  public void showTab(int index) {
    if (index == DialtactsPagerAdapter.TAB_INDEX_VOICEMAIL) {
      if (adapter.hasActiveVoicemailProvider()) {
        viewPager.setCurrentItem(adapter.getRtlPosition(DialtactsPagerAdapter.TAB_INDEX_VOICEMAIL));
      } else if (!hasFetchedVoicemailStatus) {
        // Try to show the voicemail tab after the voicemail status returns.
        showVoicemailTabAfterVoicemailStatusIsFetched = true;
      }
    } else if (index < getTabCount()) {
      viewPager.setCurrentItem(adapter.getRtlPosition(index));
    }
  }

  @Override
  public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    // onPageScrolled(0, 0, 0) is called when app launch. And we should ignore it.
    // It's also called when swipe right from first tab, but we don't care.
    if (positionOffsetPixels != 0) {
      onPageScrolledBeforeScrollStateSettling = true;
    }
    tabIndex = adapter.getRtlPosition(position);

    final int count = onPageChangeListeners.size();
    for (int i = 0; i < count; i++) {
      onPageChangeListeners.get(i).onPageScrolled(position, positionOffset, positionOffsetPixels);
    }
  }

  @Override
  public void onPageSelected(int position) {
    // onPageScrollStateChanged(SCROLL_STATE_SETTLING) must be called before this.
    // If onPageScrolled() is called before that, the page is selected by swiping;
    // otherwise the page is selected by clicking.
    if (onPageScrolledBeforeScrollStateSettling) {
      Logger.get(getContext()).logImpression(swipeImpressionList[position]);
      onPageScrolledBeforeScrollStateSettling = false;
    } else {
      Logger.get(getContext()).logImpression(clickImpressionList[position]);
    }

    PerformanceReport.recordClick(actionTypeList[position]);

    LogUtil.i("ListsFragment.onPageSelected", "position: %d", position);
    tabIndex = adapter.getRtlPosition(position);

    // Show the tab which has been selected instead.
    showVoicemailTabAfterVoicemailStatusIsFetched = false;

    final int count = onPageChangeListeners.size();
    for (int i = 0; i < count; i++) {
      onPageChangeListeners.get(i).onPageSelected(position);
    }
    sendScreenViewForCurrentPosition();

    if (currentPage instanceof CallLogFragment) {
      ((CallLogFragment) currentPage).onNotVisible();
    }
    currentPage = adapter.getItem(position);
    if (currentPage instanceof CallLogFragment) {
      ((CallLogFragment) currentPage).onVisible();
    }
  }

  @Override
  public void onPageScrollStateChanged(int state) {
    if (state != SCROLL_STATE_SETTLING) {
      onPageScrolledBeforeScrollStateSettling = false;
    }

    final int count = onPageChangeListeners.size();
    for (int i = 0; i < count; i++) {
      onPageChangeListeners.get(i).onPageScrollStateChanged(state);
    }
  }

  @Override
  public void onVoicemailStatusFetched(Cursor statusCursor) {
    hasFetchedVoicemailStatus = true;

    if (getActivity() == null || paused) {
      return;
    }

    VoicemailStatusCorruptionHandler.maybeFixVoicemailStatus(
        getContext(), statusCursor, Source.Activity);

    // Update hasActiveVoicemailProvider, which controls the number of tabs displayed.
    boolean hasActiveVoicemailProvider =
        VoicemailStatusHelper.getNumberActivityVoicemailSources(statusCursor) > 0;
    if (hasActiveVoicemailProvider != adapter.hasActiveVoicemailProvider()) {
      adapter.setHasActiveVoicemailProvider(hasActiveVoicemailProvider);
      adapter.notifyDataSetChanged();

      if (hasActiveVoicemailProvider) {
        Logger.get(getContext()).logImpression(DialerImpression.Type.VVM_TAB_VISIBLE);
        viewPagerTabs.updateTab(DialtactsPagerAdapter.TAB_INDEX_VOICEMAIL);
      } else {
        viewPagerTabs.removeTab(DialtactsPagerAdapter.TAB_INDEX_VOICEMAIL);
        adapter.removeVoicemailFragment(getChildFragmentManager());
      }

      prefs
          .edit()
          .putBoolean(
              VisualVoicemailEnabledChecker.PREF_KEY_HAS_ACTIVE_VOICEMAIL_PROVIDER,
              hasActiveVoicemailProvider)
          .apply();
    }

    if (hasActiveVoicemailProvider) {
      callLogQueryHandler.fetchVoicemailUnreadCount();
    }

    if (adapter.hasActiveVoicemailProvider() && showVoicemailTabAfterVoicemailStatusIsFetched) {
      showVoicemailTabAfterVoicemailStatusIsFetched = false;
      showTab(DialtactsPagerAdapter.TAB_INDEX_VOICEMAIL);
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

    viewPagerTabs.setUnreadCount(count, DialtactsPagerAdapter.TAB_INDEX_VOICEMAIL);
    viewPagerTabs.updateTab(DialtactsPagerAdapter.TAB_INDEX_VOICEMAIL);
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

    viewPagerTabs.setUnreadCount(count, DialtactsPagerAdapter.TAB_INDEX_HISTORY);
    viewPagerTabs.updateTab(DialtactsPagerAdapter.TAB_INDEX_HISTORY);
  }

  @Override
  public boolean onCallsFetched(Cursor statusCursor) {
    // Return false; did not take ownership of cursor
    return false;
  }

  public int getCurrentTabIndex() {
    return tabIndex;
  }

  public boolean shouldShowFab() {
    // If the VVM TOS is visible, don't show the fab
    if (currentPage instanceof VisualVoicemailCallLogFragment
        && ((VisualVoicemailCallLogFragment) currentPage).isModalAlertVisible()) {
      return false;
    }

    return true;
  }

  @Override
  public void updateTabUnreadCounts() {
    if (callLogQueryHandler != null) {
      callLogQueryHandler.fetchMissedCallsUnreadCount();
      if (adapter.hasActiveVoicemailProvider()) {
        callLogQueryHandler.fetchVoicemailUnreadCount();
      }
    }
  }

  /** External method to mark all missed calls as read. */
  public void markMissedCallsAsReadAndRemoveNotifications() {
    if (callLogQueryHandler != null) {
      callLogQueryHandler.markMissedCallsAsRead();
      CallLogNotificationsService.cancelAllMissedCalls(getContext());
    }
  }

  public void showRemoveView(boolean show) {
    removeViewContent.setVisibility(show ? View.VISIBLE : View.GONE);
    removeView.setAlpha(show ? 0 : 1);
    removeView.animate().alpha(show ? 1 : 0).start();
  }

  @Override
  public void showMultiSelectRemoveView(boolean show) {
    viewPagerTabs.setVisibility(show ? View.GONE : View.VISIBLE);
    viewPager.setEnableSwipingPages(!show);
  }

  public boolean hasFrequents() {
    OldSpeedDialFragment page =
        (OldSpeedDialFragment)
            adapter.getItem(adapter.getRtlPosition(DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL));
    return page.hasFrequents();
  }

  public RemoveView getRemoveView() {
    return removeView;
  }

  public int getTabCount() {
    return adapter.getCount();
  }

  public void sendScreenViewForCurrentPosition() {
    if (!isResumed()) {
      return;
    }

    ScreenEvent.Type screenType;
    switch (getCurrentTabIndex()) {
      case DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL:
        screenType = ScreenEvent.Type.SPEED_DIAL;
        break;
      case DialtactsPagerAdapter.TAB_INDEX_HISTORY:
        screenType = ScreenEvent.Type.CALL_LOG;
        break;
      case DialtactsPagerAdapter.TAB_INDEX_ALL_CONTACTS:
        screenType = ScreenEvent.Type.ALL_CONTACTS;
        break;
      case DialtactsPagerAdapter.TAB_INDEX_VOICEMAIL:
        screenType = ScreenEvent.Type.VOICEMAIL_LOG;
        break;
      default:
        return;
    }
    Logger.get(getActivity()).logScreenView(screenType, getActivity());
  }
}
