/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.dialer.main.impl;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.QuickContact;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.Toolbar;
import android.telecom.PhoneAccount;
import android.view.View;
import android.widget.ImageView;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.dialer.animation.AnimUtils;
import com.android.dialer.app.calllog.CallLogAdapter;
import com.android.dialer.app.calllog.CallLogFragment;
import com.android.dialer.app.calllog.CallLogFragment.CallLogFragmentListener;
import com.android.dialer.app.calllog.CallLogNotificationsService;
import com.android.dialer.app.calllog.VisualVoicemailCallLogFragment;
import com.android.dialer.app.list.DragDropController;
import com.android.dialer.app.list.OldSpeedDialFragment;
import com.android.dialer.app.list.OnDragDropListener;
import com.android.dialer.app.list.OnListFragmentScrolledListener;
import com.android.dialer.app.list.PhoneFavoriteSquareTileView;
import com.android.dialer.app.list.RemoveView;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.callintent.CallSpecificAppData;
import com.android.dialer.common.FragmentUtils.FragmentUtilListener;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.UiListener;
import com.android.dialer.compat.CompatUtils;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.constants.ActivityRequestCodes;
import com.android.dialer.contactsfragment.ContactsFragment;
import com.android.dialer.contactsfragment.ContactsFragment.Header;
import com.android.dialer.contactsfragment.ContactsFragment.OnContactSelectedListener;
import com.android.dialer.database.CallLogQueryHandler;
import com.android.dialer.database.Database;
import com.android.dialer.dialpadview.DialpadFragment;
import com.android.dialer.dialpadview.DialpadFragment.DialpadListener;
import com.android.dialer.dialpadview.DialpadFragment.LastOutgoingCallCallback;
import com.android.dialer.dialpadview.DialpadFragment.OnDialpadQueryChangedListener;
import com.android.dialer.interactions.PhoneNumberInteraction;
import com.android.dialer.main.MainActivityPeer;
import com.android.dialer.main.impl.bottomnav.BottomNavBar;
import com.android.dialer.main.impl.bottomnav.BottomNavBar.OnBottomNavTabSelectedListener;
import com.android.dialer.main.impl.bottomnav.BottomNavBar.TabIndex;
import com.android.dialer.main.impl.toolbar.MainToolbar;
import com.android.dialer.postcall.PostCall;
import com.android.dialer.precall.PreCall;
import com.android.dialer.searchfragment.list.NewSearchFragment.SearchFragmentListener;
import com.android.dialer.smartdial.util.SmartDialPrefix;
import com.android.dialer.storage.StorageComponent;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.TransactionSafeActivity;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.TimeUnit;

/**
 * OldMainActivityPeer which implements all of the old fragments we know and love <3
 *
 * <p>TODO(calderwoodra): Deprecate this class when we launch NewmainActivityPeer.
 */
public class OldMainActivityPeer implements MainActivityPeer, FragmentUtilListener {

  private static final String KEY_SAVED_LANGUAGE_CODE = "saved_language_code";
  private static final String KEY_CURRENT_TAB = "current_tab";
  private static final String KEY_LAST_TAB = "last_tab";

  /** Action and extra to let the activity know which tab to open up to. */
  private static final String ACTION_SHOW_TAB = "ACTION_SHOW_TAB";

  private static final String EXTRA_SHOW_TAB = "EXTRA_SHOW_TAB";

  private final MainActivity mainActivity;

  // Contacts
  private MainOnContactSelectedListener onContactSelectedListener;

  // Dialpad and Search
  private MainDialpadFragmentHost dialpadFragmentHostInterface;
  private MainSearchController searchController;
  private MainOnDialpadQueryChangedListener onDialpadQueryChangedListener;
  private MainDialpadListener dialpadListener;
  private MainSearchFragmentListener searchFragmentListener;

  // Action Mode
  private MainCallLogAdapterOnActionModeStateChangedListener
      callLogAdapterOnActionModeStateChangedListener;

  // Call Log
  private MainCallLogHost callLogHostInterface;
  private MainCallLogFragmentListener callLogFragmentListener;
  private MainOnListFragmentScrolledListener onListFragmentScrolledListener;

  // Speed Dial
  private MainOnPhoneNumberPickerActionListener onPhoneNumberPickerActionListener;
  private MainOldSpeedDialFragmentHost oldSpeedDialFragmentHost;

  /** Language the device was in last time {@link #onSaveInstanceState(Bundle)} was called. */
  private String savedLanguageCode;

  private LastTabController lastTabController;

  private BottomNavBar bottomNav;
  private View snackbarContainer;
  private UiListener<String> getLastOutgoingCallListener;

  public static Intent getShowTabIntent(Context context, @TabIndex int tabIndex) {
    Intent intent = new Intent(context, MainActivity.class);
    intent.setAction(ACTION_SHOW_TAB);
    intent.putExtra(EXTRA_SHOW_TAB, tabIndex);
    // TODO(calderwoodra): Do we need to set some URI data here
    return intent;
  }

  static boolean isShowTabIntent(Intent intent) {
    return ACTION_SHOW_TAB.equals(intent.getAction()) && intent.hasExtra(EXTRA_SHOW_TAB);
  }

  static @TabIndex int getTabFromIntent(Intent intent) {
    return intent.getIntExtra(EXTRA_SHOW_TAB, -1);
  }

  public OldMainActivityPeer(MainActivity mainActivity) {
    this.mainActivity = mainActivity;
  }

  @Override
  public void onActivityCreate(Bundle savedInstanceState) {
    mainActivity.setContentView(R.layout.main_activity);
    initUiListeners();
    initLayout(savedInstanceState);
    SmartDialPrefix.initializeNanpSettings(mainActivity);
  }

  private void initUiListeners() {
    getLastOutgoingCallListener =
        DialerExecutorComponent.get(mainActivity)
            .createUiListener(mainActivity.getFragmentManager(), "Query last phone number");
  }

  private void initLayout(Bundle savedInstanceState) {
    onContactSelectedListener = new MainOnContactSelectedListener(mainActivity);
    dialpadFragmentHostInterface = new MainDialpadFragmentHost();

    snackbarContainer = mainActivity.findViewById(R.id.coordinator_layout);

    FloatingActionButton fab = mainActivity.findViewById(R.id.fab);
    fab.setOnClickListener(v -> searchController.showDialpad(true));

    MainToolbar toolbar = mainActivity.findViewById(R.id.toolbar);
    mainActivity.setSupportActionBar(mainActivity.findViewById(R.id.toolbar));

    bottomNav = mainActivity.findViewById(R.id.bottom_nav_bar);
    MainBottomNavBarBottomNavTabListener bottomNavTabListener =
        new MainBottomNavBarBottomNavTabListener(mainActivity.getFragmentManager());
    bottomNav.addOnTabSelectedListener(bottomNavTabListener);

    callLogFragmentListener =
        new MainCallLogFragmentListener(
            mainActivity, mainActivity.getContentResolver(), bottomNav, toolbar);
    bottomNav.addOnTabSelectedListener(callLogFragmentListener);

    searchController = new MainSearchController(mainActivity, bottomNav, fab, toolbar);
    toolbar.setSearchBarListener(searchController);

    onDialpadQueryChangedListener = new MainOnDialpadQueryChangedListener(searchController);
    dialpadListener =
        new MainDialpadListener(mainActivity, searchController, getLastOutgoingCallListener);
    searchFragmentListener = new MainSearchFragmentListener(searchController);
    callLogAdapterOnActionModeStateChangedListener =
        new MainCallLogAdapterOnActionModeStateChangedListener();
    callLogHostInterface = new MainCallLogHost(searchController, fab);

    onListFragmentScrolledListener = new MainOnListFragmentScrolledListener(snackbarContainer);
    onPhoneNumberPickerActionListener = new MainOnPhoneNumberPickerActionListener(mainActivity);
    oldSpeedDialFragmentHost =
        new MainOldSpeedDialFragmentHost(
            bottomNav,
            mainActivity.findViewById(R.id.contact_tile_drag_shadow_overlay),
            mainActivity.findViewById(R.id.remove_view),
            mainActivity.findViewById(R.id.search_view_container));

    lastTabController = new LastTabController(mainActivity, bottomNav);

    // Restore our view state if needed, else initialize as if the app opened for the first time
    if (savedInstanceState != null) {
      savedLanguageCode = savedInstanceState.getString(KEY_SAVED_LANGUAGE_CODE);
      searchController.onRestoreInstanceState(savedInstanceState);
      bottomNav.selectTab(savedInstanceState.getInt(KEY_CURRENT_TAB));
    } else {
      showTabOnIntent(mainActivity.getIntent());
    }
  }

  @Override
  public void onNewIntent(Intent intent) {
    showTabOnIntent(intent);
  }

  private void showTabOnIntent(Intent intent) {
    if (isShowTabIntent(intent)) {
      bottomNav.selectTab(getTabFromIntent(intent));
      return;
    }

    if (isDialIntent(intent)) {
      searchController.showDialpadFromNewIntent(false);
      // Dialpad will grab the intent and populate the number
      return;
    }

    if (lastTabController.isEnabled) {
      lastTabController.selectLastTab();
      return;
    }

    bottomNav.selectTab(TabIndex.SPEED_DIAL);
  }

  /** Returns true if the given intent contains a phone number to populate the dialer with */
  private boolean isDialIntent(Intent intent) {
    if (intent == null || intent.getData() == null) {
      return false;
    }

    if (Intent.ACTION_DIAL.equals(intent.getAction())) {
      return true;
    }

    if (Intent.ACTION_VIEW.equals(intent.getAction())) {
      Uri data = intent.getData();
      if (data != null && PhoneAccount.SCHEME_TEL.equals(data.getScheme())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void onActivityResume() {
    callLogFragmentListener.onActivityResume();
    // Start the thread that updates the smart dial database if the activity is recreated with a
    // language change.
    boolean forceUpdate =
        !CompatUtils.getLocale(mainActivity).getISO3Language().equals(savedLanguageCode);
    Database.get(mainActivity)
        .getDatabaseHelper(mainActivity)
        .startSmartDialUpdateThread(forceUpdate);
    showPostCallPrompt();
  }

  @Override
  public void onActivityStop() {
    lastTabController.onActivityStop();
    callLogFragmentListener.onActivityStop(
        mainActivity.isChangingConfigurations(),
        mainActivity.getSystemService(KeyguardManager.class).isKeyguardLocked());
  }

  private void showPostCallPrompt() {
    if (TelecomUtil.isInManagedCall(mainActivity)) {
      // No prompt to show if the user is in a call
      return;
    }

    if (searchController.isInSearch()) {
      // Don't show the prompt if we're in the search ui
      return;
    }

    PostCall.promptUserForMessageIfNecessary(mainActivity, snackbarContainer);
  }

  @Override
  public void onSaveInstanceState(Bundle bundle) {
    bundle.putString(
        KEY_SAVED_LANGUAGE_CODE, CompatUtils.getLocale(mainActivity).getISO3Language());
    bundle.putInt(KEY_CURRENT_TAB, bottomNav.getSelectedTab());
    searchController.onSaveInstanceState(bundle);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == ActivityRequestCodes.DIALTACTS_VOICE_SEARCH) {
      searchController.onVoiceResults(resultCode, data);
    } else {
      LogUtil.e("MainActivity.onActivityResult", "Unknown request code: " + requestCode);
    }
  }

  @Override
  public boolean onBackPressed() {
    if (searchController.onBackPressed()) {
      return true;
    }
    return false;
  }

  @Nullable
  @Override
  @SuppressWarnings("unchecked") // Casts are checked using runtime methods
  public <T> T getImpl(Class<T> callbackInterface) {
    if (callbackInterface.isInstance(onContactSelectedListener)) {
      return (T) onContactSelectedListener;
    } else if (callbackInterface.isInstance(onDialpadQueryChangedListener)) {
      return (T) onDialpadQueryChangedListener;
    } else if (callbackInterface.isInstance(dialpadListener)) {
      return (T) dialpadListener;
    } else if (callbackInterface.isInstance(dialpadFragmentHostInterface)) {
      return (T) dialpadFragmentHostInterface;
    } else if (callbackInterface.isInstance(searchFragmentListener)) {
      return (T) searchFragmentListener;
    } else if (callbackInterface.isInstance(callLogAdapterOnActionModeStateChangedListener)) {
      return (T) callLogAdapterOnActionModeStateChangedListener;
    } else if (callbackInterface.isInstance(callLogHostInterface)) {
      return (T) callLogHostInterface;
    } else if (callbackInterface.isInstance(callLogFragmentListener)) {
      return (T) callLogFragmentListener;
    } else if (callbackInterface.isInstance(onListFragmentScrolledListener)) {
      return (T) onListFragmentScrolledListener;
    } else if (callbackInterface.isInstance(onPhoneNumberPickerActionListener)) {
      return (T) onPhoneNumberPickerActionListener;
    } else if (callbackInterface.isInstance(oldSpeedDialFragmentHost)) {
      return (T) oldSpeedDialFragmentHost;
    } else {
      return null;
    }
  }

  /** @see OnContactSelectedListener */
  private static final class MainOnContactSelectedListener implements OnContactSelectedListener {

    private final Context context;

    MainOnContactSelectedListener(Context context) {
      this.context = context;
    }

    @Override
    public void onContactSelected(ImageView photo, Uri contactUri, long contactId) {
      // TODO(calderwoodra): Add impression logging
      QuickContact.showQuickContact(
          context, photo, contactUri, QuickContact.MODE_LARGE, null /* excludeMimes */);
    }
  }

  /** @see OnDialpadQueryChangedListener */
  private static final class MainOnDialpadQueryChangedListener
      implements OnDialpadQueryChangedListener {

    private final MainSearchController searchController;

    MainOnDialpadQueryChangedListener(MainSearchController searchController) {
      this.searchController = searchController;
    }

    @Override
    public void onDialpadQueryChanged(String query) {
      searchController.onDialpadQueryChanged(query);
    }
  }

  /** @see DialpadListener */
  private static final class MainDialpadListener implements DialpadListener {

    private final MainSearchController searchController;
    private final Context context;
    private final UiListener<String> listener;

    MainDialpadListener(
        Context context, MainSearchController searchController, UiListener<String> uiListener) {
      this.context = context;
      this.searchController = searchController;
      this.listener = uiListener;
    }

    @Override
    public void getLastOutgoingCall(LastOutgoingCallCallback callback) {
      ListenableFuture<String> listenableFuture =
          DialerExecutorComponent.get(context)
              .backgroundExecutor()
              .submit(() -> Calls.getLastOutgoingCall(context));
      listener.listen(context, listenableFuture, callback::lastOutgoingCall, throwable -> {});
    }

    @Override
    public void onDialpadShown() {
      searchController.onDialpadShown();
    }

    @Override
    public void onCallPlacedFromDialpad() {
      // TODO(calderwoodra): logging
    }
  }

  /** @see SearchFragmentListener */
  private static final class MainSearchFragmentListener implements SearchFragmentListener {

    private final MainSearchController searchController;

    MainSearchFragmentListener(MainSearchController searchController) {
      this.searchController = searchController;
    }

    @Override
    public void onSearchListTouch() {
      searchController.onSearchListTouch();
    }

    @Override
    public void onCallPlacedFromSearch() {
      // TODO(calderwoodra): logging
    }
  }

  /** @see DialpadFragment.HostInterface */
  private static final class MainDialpadFragmentHost implements DialpadFragment.HostInterface {

    @Override
    public boolean onDialpadSpacerTouchWithEmptyQuery() {
      // No-op, just let the clicks fall through to the search list
      return false;
    }
  }

  /** @see CallLogAdapter.OnActionModeStateChangedListener */
  // TODO(calderwoodra): What is the purpose of this listener?
  private static final class MainCallLogAdapterOnActionModeStateChangedListener
      implements CallLogAdapter.OnActionModeStateChangedListener {

    private boolean isEnabled;

    @Override
    public void onActionModeStateChanged(boolean isEnabled) {
      this.isEnabled = isEnabled;
    }

    @Override
    public boolean isActionModeStateEnabled() {
      return isEnabled;
    }
  }

  /** @see CallLogFragment.HostInterface */
  private static final class MainCallLogHost implements CallLogFragment.HostInterface {

    private final FloatingActionButton fab;
    private final MainSearchController searchController;

    MainCallLogHost(MainSearchController searchController, FloatingActionButton fab) {
      this.searchController = searchController;
      this.fab = fab;
    }

    @Override
    public void showDialpad() {
      searchController.showDialpad(true);
    }

    @Override
    public void enableFloatingButton(boolean enabled) {
      if (enabled) {
        fab.show();
      } else {
        fab.hide();
      }
    }
  }

  /**
   * Handles the logic for callbacks from:
   *
   * <ul>
   *   <li>{@link CallLogFragment}
   *   <li>{@link CallLogQueryHandler}
   *   <li>{@link BottomNavBar}
   * </ul>
   *
   * This mainly entails:
   *
   * <ul>
   *   <li>Handling querying for missed calls/unread voicemails.
   *   <li>Displaying a badge to the user in the bottom nav when there are missed calls/unread
   *       voicemails present.
   *   <li>Marking missed calls as read when appropriate. See {@link
   *       #markMissedCallsAsReadAndRemoveNotification()}
   *   <li>TODO(calderwoodra): multiselect
   *   <li>TODO(calderwoodra): voicemail status
   * </ul>
   *
   * @see CallLogFragmentListener
   * @see CallLogQueryHandler.Listener
   * @see OnBottomNavTabSelectedListener
   */
  private static final class MainCallLogFragmentListener
      implements CallLogFragmentListener,
          CallLogQueryHandler.Listener,
          OnBottomNavTabSelectedListener {

    private final CallLogQueryHandler callLogQueryHandler;
    private final Context context;
    private final BottomNavBar bottomNavBar;
    private final Toolbar toolbar;

    private @TabIndex int currentTab = TabIndex.SPEED_DIAL;
    private long timeSelected = -1;
    private boolean activityIsAlive;

    MainCallLogFragmentListener(
        Context context,
        ContentResolver contentResolver,
        BottomNavBar bottomNavBar,
        Toolbar toolbar) {
      callLogQueryHandler = new CallLogQueryHandler(context, contentResolver, this);
      this.context = context;
      this.bottomNavBar = bottomNavBar;
      this.toolbar = toolbar;
    }

    @Override
    public void updateTabUnreadCounts() {
      callLogQueryHandler.fetchMissedCallsUnreadCount();
      callLogQueryHandler.fetchVoicemailUnreadCount();
    }

    @Override
    public void showMultiSelectRemoveView(boolean show) {
      bottomNavBar.setVisibility(show ? View.GONE : View.VISIBLE);
      toolbar.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
      // TODO(calderwoodra): handle this when voicemail is implemented
    }

    @Override
    public void onVoicemailUnreadCountFetched(Cursor cursor) {
      if (activityIsAlive) {
        bottomNavBar.setNotificationCount(TabIndex.VOICEMAIL, cursor.getCount());
      }
      cursor.close();
    }

    @Override
    public void onMissedCallsUnreadCountFetched(Cursor cursor) {
      if (activityIsAlive) {
        bottomNavBar.setNotificationCount(TabIndex.CALL_LOG, cursor.getCount());
      }
      cursor.close();
    }

    @Override
    public boolean onCallsFetched(Cursor combinedCursor) {
      // Return false; did not take ownership of cursor
      return false;
    }

    @Override
    public void onSpeedDialSelected() {
      setCurrentTab(TabIndex.SPEED_DIAL);
    }

    @Override
    public void onCallLogSelected() {
      setCurrentTab(TabIndex.CALL_LOG);
    }

    @Override
    public void onContactsSelected() {
      setCurrentTab(TabIndex.CONTACTS);
    }

    @Override
    public void onVoicemailSelected() {
      setCurrentTab(TabIndex.VOICEMAIL);
    }

    private void markMissedCallsAsReadAndRemoveNotification() {
      callLogQueryHandler.markMissedCallsAsRead();
      CallLogNotificationsService.cancelAllMissedCalls(context);
    }

    private void setCurrentTab(@TabIndex int tabIndex) {
      if (currentTab == TabIndex.CALL_LOG && tabIndex != TabIndex.CALL_LOG) {
        markMissedCallsAsReadAndRemoveNotification();
      }
      currentTab = tabIndex;
      timeSelected = System.currentTimeMillis();
    }

    public void onActivityResume() {
      activityIsAlive = true;
      callLogQueryHandler.fetchVoicemailStatus();
      callLogQueryHandler.fetchMissedCallsUnreadCount();
      // Reset the tab on resume to restart the timer
      setCurrentTab(bottomNavBar.getSelectedTab());
    }

    /** Should be called when {@link Activity#onStop()} is called. */
    public void onActivityStop(boolean changingConfigurations, boolean keyguardLocked) {
      activityIsAlive = false;
      if (viewedCallLogTabPastTimeThreshold() && !changingConfigurations && !keyguardLocked) {
        markMissedCallsAsReadAndRemoveNotification();
      }
    }

    /**
     * Returns true if the user has been (and still is) on the history tab for long than the
     * threshold.
     */
    private boolean viewedCallLogTabPastTimeThreshold() {
      return currentTab == TabIndex.CALL_LOG
          && timeSelected != -1
          && System.currentTimeMillis() - timeSelected > TimeUnit.SECONDS.toMillis(3);
    }
  }

  /** @see OnListFragmentScrolledListener */
  private static final class MainOnListFragmentScrolledListener
      implements OnListFragmentScrolledListener {

    private final View parentLayout;

    MainOnListFragmentScrolledListener(View parentLayout) {
      this.parentLayout = parentLayout;
    }

    @Override
    public void onListFragmentScrollStateChange(int scrollState) {
      DialerUtils.hideInputMethod(parentLayout);
    }

    @Override
    public void onListFragmentScroll(
        int firstVisibleItem, int visibleItemCount, int totalItemCount) {
      // TODO: No-op for now. This should eventually show/hide the actionBar based on
      // interactions with the ListsFragments.
    }
  }

  /** @see OnPhoneNumberPickerActionListener */
  private static final class MainOnPhoneNumberPickerActionListener
      implements OnPhoneNumberPickerActionListener {

    private final TransactionSafeActivity activity;

    MainOnPhoneNumberPickerActionListener(TransactionSafeActivity activity) {
      this.activity = activity;
    }

    @Override
    public void onPickDataUri(
        Uri dataUri, boolean isVideoCall, CallSpecificAppData callSpecificAppData) {
      PhoneNumberInteraction.startInteractionForPhoneCall(
          activity, dataUri, isVideoCall, callSpecificAppData);
    }

    @Override
    public void onPickPhoneNumber(
        String phoneNumber, boolean isVideoCall, CallSpecificAppData callSpecificAppData) {
      if (phoneNumber == null) {
        // Invalid phone number, but let the call go through so that InCallUI can show
        // an error message.
        phoneNumber = "";
      }
      PreCall.start(
          activity,
          new CallIntentBuilder(phoneNumber, callSpecificAppData)
              .setIsVideoCall(isVideoCall)
              .setAllowAssistedDial(callSpecificAppData.getAllowAssistedDialing()));
    }

    @Override
    public void onHomeInActionBarSelected() {
      // TODO(calderwoodra): investigate if we need to exit search here
      // PhoneNumberPickerFragment#onOptionsItemSelected
    }
  }

  /**
   * Handles the callbacks for {@link OldSpeedDialFragment} and drag/drop logic for drag to remove.
   *
   * @see OldSpeedDialFragment.HostInterface
   * @see OnDragDropListener
   */
  private static final class MainOldSpeedDialFragmentHost
      implements OldSpeedDialFragment.HostInterface, OnDragDropListener {

    private final BottomNavBar bottomNavBar;
    private final ImageView dragShadowOverlay;
    private final RemoveView removeView;
    private final View searchViewContainer;

    // TODO(calderwoodra): Use this for drag and drop
    @SuppressWarnings("unused")
    private DragDropController dragDropController;

    MainOldSpeedDialFragmentHost(
        BottomNavBar bottomNavBar,
        ImageView dragShadowOverlay,
        RemoveView removeView,
        View searchViewContainer) {
      this.bottomNavBar = bottomNavBar;
      this.dragShadowOverlay = dragShadowOverlay;
      this.removeView = removeView;
      this.searchViewContainer = searchViewContainer;
    }

    @Override
    public void setDragDropController(DragDropController dragDropController) {
      removeView.setDragDropController(dragDropController);
    }

    @Override
    public void showAllContactsTab() {
      bottomNavBar.selectTab(TabIndex.CONTACTS);
    }

    @Override
    public ImageView getDragShadowOverlay() {
      return dragShadowOverlay;
    }

    @Override
    public void onDragStarted(int x, int y, PhoneFavoriteSquareTileView view) {
      showRemoveView(true);
    }

    @Override
    public void onDragHovered(int x, int y, PhoneFavoriteSquareTileView view) {}

    @Override
    public void onDragFinished(int x, int y) {
      showRemoveView(false);
    }

    @Override
    public void onDroppedOnRemove() {}

    private void showRemoveView(boolean show) {
      if (show) {
        AnimUtils.crossFadeViews(removeView, searchViewContainer, 300);
      } else {
        AnimUtils.crossFadeViews(searchViewContainer, removeView, 300);
      }
    }
  }

  /**
   * Implementation of {@link OnBottomNavTabSelectedListener} that handles logic for showing each of
   * the main tabs.
   */
  private static final class MainBottomNavBarBottomNavTabListener
      implements OnBottomNavTabSelectedListener {

    private static final String SPEED_DIAL_TAG = "speed_dial";
    private static final String CALL_LOG_TAG = "call_log";
    private static final String CONTACTS_TAG = "contacts";
    private static final String VOICEMAIL_TAG = "voicemail";

    private final FragmentManager fragmentManager;

    private MainBottomNavBarBottomNavTabListener(FragmentManager fragmentManager) {
      this.fragmentManager = fragmentManager;
    }

    @Override
    public void onSpeedDialSelected() {
      hideAllFragments();
      Fragment fragment = fragmentManager.findFragmentByTag(SPEED_DIAL_TAG);
      if (fragment == null) {
        fragmentManager
            .beginTransaction()
            .add(R.id.fragment_container, new OldSpeedDialFragment(), SPEED_DIAL_TAG)
            .commit();
      } else {
        fragmentManager.beginTransaction().show(fragment).commit();
      }
    }

    @Override
    public void onCallLogSelected() {
      hideAllFragments();
      CallLogFragment fragment = (CallLogFragment) fragmentManager.findFragmentByTag(CALL_LOG_TAG);
      if (fragment == null) {
        fragmentManager
            .beginTransaction()
            .add(R.id.fragment_container, new CallLogFragment(), CALL_LOG_TAG)
            .commit();
      } else {
        fragmentManager.beginTransaction().show(fragment).commit();
      }
    }

    @Override
    public void onContactsSelected() {
      hideAllFragments();
      ContactsFragment fragment =
          (ContactsFragment) fragmentManager.findFragmentByTag(CONTACTS_TAG);
      if (fragment == null) {
        fragmentManager
            .beginTransaction()
            .add(
                R.id.fragment_container,
                ContactsFragment.newInstance(Header.ADD_CONTACT),
                CONTACTS_TAG)
            .commit();
      } else {
        fragmentManager.beginTransaction().show(fragment).commit();
      }
    }

    @Override
    public void onVoicemailSelected() {
      hideAllFragments();
      VisualVoicemailCallLogFragment fragment =
          (VisualVoicemailCallLogFragment) fragmentManager.findFragmentByTag(VOICEMAIL_TAG);
      if (fragment == null) {
        fragmentManager
            .beginTransaction()
            .add(R.id.fragment_container, new VisualVoicemailCallLogFragment(), VOICEMAIL_TAG)
            .commit();
      } else {
        fragmentManager.beginTransaction().show(fragment).commit();
      }
    }

    private void hideAllFragments() {
      android.app.FragmentTransaction transaction = fragmentManager.beginTransaction();
      if (fragmentManager.findFragmentByTag(SPEED_DIAL_TAG) != null) {
        transaction.hide(fragmentManager.findFragmentByTag(SPEED_DIAL_TAG));
      }
      if (fragmentManager.findFragmentByTag(CALL_LOG_TAG) != null) {
        // Old CallLogFragment
        transaction.hide(fragmentManager.findFragmentByTag(CALL_LOG_TAG));
      }
      if (fragmentManager.findFragmentByTag(CONTACTS_TAG) != null) {
        transaction.hide(fragmentManager.findFragmentByTag(CONTACTS_TAG));
      }
      if (fragmentManager.findFragmentByTag(VOICEMAIL_TAG) != null) {
        // Old VisualVoicemailFragment
        transaction.hide(fragmentManager.findFragmentByTag(VOICEMAIL_TAG));
      }
      transaction.commit();
    }
  }

  private static final class LastTabController {

    private final Context context;
    private final BottomNavBar bottomNavBar;
    private final boolean isEnabled;

    LastTabController(Context context, BottomNavBar bottomNavBar) {
      this.context = context;
      this.bottomNavBar = bottomNavBar;
      isEnabled = ConfigProviderBindings.get(context).getBoolean("last_tab_enabled", false);
    }

    /** Sets the last tab if the feature is enabled, otherwise defaults to speed dial. */
    void selectLastTab() {
      @TabIndex int tabIndex = TabIndex.SPEED_DIAL;
      if (isEnabled) {
        tabIndex =
            StorageComponent.get(context)
                .unencryptedSharedPrefs()
                .getInt(KEY_LAST_TAB, TabIndex.SPEED_DIAL);
      }
      bottomNavBar.selectTab(tabIndex);
    }

    void onActivityStop() {
      StorageComponent.get(context)
          .unencryptedSharedPrefs()
          .edit()
          .putInt(KEY_LAST_TAB, bottomNavBar.getSelectedTab())
          .apply();
    }
  }
}
