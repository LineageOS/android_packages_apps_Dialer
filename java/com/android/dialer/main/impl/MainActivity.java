/*
 * Copyright (C) 2017 The Android Open Source Project
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
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.widget.ImageView;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.dialer.app.calllog.CallLogAdapter;
import com.android.dialer.app.calllog.CallLogFragment;
import com.android.dialer.app.calllog.CallLogFragment.CallLogFragmentListener;
import com.android.dialer.app.calllog.CallLogNotificationsService;
import com.android.dialer.app.list.DragDropController;
import com.android.dialer.app.list.OldSpeedDialFragment;
import com.android.dialer.app.list.OnDragDropListener;
import com.android.dialer.app.list.OnListFragmentScrolledListener;
import com.android.dialer.app.list.PhoneFavoriteSquareTileView;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.callintent.CallSpecificAppData;
import com.android.dialer.calllog.ui.NewCallLogFragment;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils.FragmentUtilListener;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.UiListener;
import com.android.dialer.compat.CompatUtils;
import com.android.dialer.configprovider.ConfigProviderComponent;
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
import com.android.dialer.interactions.PhoneNumberInteraction.DisambigDialogDismissedListener;
import com.android.dialer.interactions.PhoneNumberInteraction.InteractionErrorCode;
import com.android.dialer.interactions.PhoneNumberInteraction.InteractionErrorListener;
import com.android.dialer.main.impl.BottomNavBar.OnBottomNavTabSelectedListener;
import com.android.dialer.main.impl.BottomNavBar.TabIndex;
import com.android.dialer.main.impl.toolbar.MainToolbar;
import com.android.dialer.postcall.PostCall;
import com.android.dialer.precall.PreCall;
import com.android.dialer.searchfragment.list.NewSearchFragment.SearchFragmentListener;
import com.android.dialer.smartdial.util.SmartDialPrefix;
import com.android.dialer.speeddial.SpeedDialFragment;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.TransactionSafeActivity;
import com.android.dialer.voicemail.listui.NewVoicemailFragment;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.TimeUnit;

/** This is the main activity for dialer. It hosts favorites, call log, search, dialpad, etc... */
// TODO(calderwoodra): Do not extend TransactionSafeActivity after new SpeedDial is launched
public final class MainActivity extends TransactionSafeActivity
    implements FragmentUtilListener,
        // TODO(calderwoodra): remove these 2 interfaces when we migrate to new speed dial fragment
        InteractionErrorListener,
        DisambigDialogDismissedListener {

  private static final String KEY_SAVED_LANGUAGE_CODE = "saved_language_code";

  private final MainOnContactSelectedListener onContactSelectedListener =
      new MainOnContactSelectedListener(this);
  private final MainDialpadFragmentHost dialpadFragmentHostInterface =
      new MainDialpadFragmentHost();

  private MainSearchController searchController;
  private MainOnDialpadQueryChangedListener onDialpadQueryChangedListener;
  private MainDialpadListener dialpadListener;
  private MainSearchFragmentListener searchFragmentListener;
  private MainCallLogAdapterOnActionModeStateChangedListener
      callLogAdapterOnActionModeStateChangedListener;
  private MainCallLogHost callLogHostInterface;
  private MainCallLogFragmentListener callLogFragmentListener;
  private MainOnListFragmentScrolledListener onListFragmentScrolledListener;
  private MainOnPhoneNumberPickerActionListener onPhoneNumberPickerActionListener;
  private MainOldSpeedDialFragmentHostInterface oldSpeedDialFragmentHostInterface;
  private MainOnDragDropListener onDragDropListener;

  /** Language the device was in last time {@link #onSaveInstanceState(Bundle)} was called. */
  private String savedLanguageCode;

  private View snackbarContainer;
  private UiListener<String> getLastOutgoingCallListener;

  /**
   * @param context Context of the application package implementing MainActivity class.
   * @return intent for MainActivity.class
   */
  public static Intent getIntent(Context context) {
    return new Intent(context, MainActivity.class)
        .setAction(Intent.ACTION_VIEW)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    LogUtil.enterBlock("MainActivity.onCreate");
    setContentView(R.layout.main_activity);
    initUiListeners();
    initLayout(savedInstanceState);
    SmartDialPrefix.initializeNanpSettings(this);
  }

  private void initUiListeners() {
    getLastOutgoingCallListener =
        DialerExecutorComponent.get(this)
            .createUiListener(getFragmentManager(), "Query last phone number");
  }

  private void initLayout(Bundle savedInstanceState) {
    snackbarContainer = findViewById(R.id.coordinator_layout);

    FloatingActionButton fab = findViewById(R.id.fab);
    fab.setOnClickListener(v -> searchController.showDialpad(true));

    MainToolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(findViewById(R.id.toolbar));

    BottomNavBar bottomNav = findViewById(R.id.bottom_nav_bar);
    MainBottomNavBarBottomNavTabListener bottomNavTabListener =
        new MainBottomNavBarBottomNavTabListener(
            this, getFragmentManager(), getSupportFragmentManager());
    bottomNav.addOnTabSelectedListener(bottomNavTabListener);

    callLogFragmentListener =
        new MainCallLogFragmentListener(this, getContentResolver(), bottomNav);
    bottomNav.addOnTabSelectedListener(callLogFragmentListener);

    searchController = new MainSearchController(this, bottomNav, fab, toolbar);
    toolbar.setSearchBarListener(searchController);

    onDialpadQueryChangedListener = new MainOnDialpadQueryChangedListener(searchController);
    dialpadListener = new MainDialpadListener(this, searchController, getLastOutgoingCallListener);
    searchFragmentListener = new MainSearchFragmentListener(searchController);
    callLogAdapterOnActionModeStateChangedListener =
        new MainCallLogAdapterOnActionModeStateChangedListener();
    callLogHostInterface = new MainCallLogHost(searchController, fab);

    onListFragmentScrolledListener = new MainOnListFragmentScrolledListener(snackbarContainer);
    onPhoneNumberPickerActionListener = new MainOnPhoneNumberPickerActionListener(this);
    oldSpeedDialFragmentHostInterface =
        new MainOldSpeedDialFragmentHostInterface(
            bottomNavTabListener, findViewById(R.id.contact_tile_drag_shadow_overlay));
    onDragDropListener = new MainOnDragDropListener();

    // Restore our view state if needed, else initialize as if the app opened for the first time
    if (savedInstanceState != null) {
      savedLanguageCode = savedInstanceState.getString(KEY_SAVED_LANGUAGE_CODE);
      searchController.onRestoreInstanceState(savedInstanceState);
    } else {
      // TODO(calderwoodra): Implement last tab
      bottomNav.selectTab(BottomNavBar.TabIndex.SPEED_DIAL);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    callLogFragmentListener.onActivityResume();
    // Start the thread that updates the smart dial database if the activity is recreated with a
    // language change.
    boolean forceUpdate = !CompatUtils.getLocale(this).getISO3Language().equals(savedLanguageCode);
    Database.get(this).getDatabaseHelper(this).startSmartDialUpdateThread(forceUpdate);
    showPostCallPrompt();
  }

  @Override
  protected void onStop() {
    super.onStop();
    callLogFragmentListener.onActivityStop(
        isChangingConfigurations(), getSystemService(KeyguardManager.class).isKeyguardLocked());
  }

  private void showPostCallPrompt() {
    if (TelecomUtil.isInManagedCall(this)) {
      // No prompt to show if the user is in a call
      return;
    }

    if (searchController.isInSearch()) {
      // Don't show the prompt if we're in the search ui
      return;
    }

    PostCall.promptUserForMessageIfNecessary(this, snackbarContainer);
  }

  @Override
  protected void onSaveInstanceState(Bundle bundle) {
    super.onSaveInstanceState(bundle);
    bundle.putString(KEY_SAVED_LANGUAGE_CODE, CompatUtils.getLocale(this).getISO3Language());
    searchController.onSaveInstanceState(bundle);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == ActivityRequestCodes.DIALTACTS_VOICE_SEARCH) {
      searchController.onVoiceResults(resultCode, data);
    } else {
      LogUtil.e("MainActivity.onActivityResult", "Unknown request code: " + requestCode);
    }
  }

  @Override
  public void onBackPressed() {
    if (searchController.onBackPressed()) {
      return;
    }
    super.onBackPressed();
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
    } else if (callbackInterface.isInstance(oldSpeedDialFragmentHostInterface)) {
      return (T) oldSpeedDialFragmentHostInterface;
    } else if (callbackInterface.isInstance(onDragDropListener)) {
      return (T) onDragDropListener;
    } else {
      return null;
    }
  }

  @Override
  public void interactionError(@InteractionErrorCode int interactionErrorCode) {
    switch (interactionErrorCode) {
      case InteractionErrorCode.USER_LEAVING_ACTIVITY:
        // This is expected to happen if the user exits the activity before the interaction occurs.
        return;
      case InteractionErrorCode.CONTACT_NOT_FOUND:
      case InteractionErrorCode.CONTACT_HAS_NO_NUMBER:
      case InteractionErrorCode.OTHER_ERROR:
      default:
        // All other error codes are unexpected. For example, it should be impossible to start an
        // interaction with an invalid contact from this activity.
        throw Assert.createIllegalStateFailException(
            "PhoneNumberInteraction error: " + interactionErrorCode);
    }
  }

  @Override
  public void onDisambigDialogDismissed() {
    // Don't do anything; the app will remain open with favorites tiles displayed.
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
  // TODO(a bug): handle multiselect mode
  private static final class MainCallLogAdapterOnActionModeStateChangedListener
      implements CallLogAdapter.OnActionModeStateChangedListener {

    @Override
    public void onActionModeStateChanged(boolean isEnabled) {}

    @Override
    public boolean isActionModeStateEnabled() {
      return false;
    }
  }

  /** @see CallLogFragment.HostInterface */
  private static final class MainCallLogHost implements CallLogFragment.HostInterface {

    private final MainSearchController searchController;
    private final FloatingActionButton fab;

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
    private final BottomNavBar bottomNavBar;
    private final Context context;

    private @TabIndex int currentTab = TabIndex.SPEED_DIAL;
    private long timeSelected = -1;
    private boolean activityIsAlive;

    MainCallLogFragmentListener(
        Context context, ContentResolver contentResolver, BottomNavBar bottomNavBar) {
      callLogQueryHandler = new CallLogQueryHandler(context, contentResolver, this);
      this.bottomNavBar = bottomNavBar;
      this.context = context;
    }

    @Override
    public void updateTabUnreadCounts() {
      callLogQueryHandler.fetchMissedCallsUnreadCount();
      callLogQueryHandler.fetchVoicemailUnreadCount();
    }

    @Override
    public void showMultiSelectRemoveView(boolean show) {
      // TODO(a bug): handle multiselect mode
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

  /** @see OldSpeedDialFragment.HostInterface */
  private static final class MainOldSpeedDialFragmentHostInterface
      implements OldSpeedDialFragment.HostInterface {

    private final MainBottomNavBarBottomNavTabListener listener;
    private final ImageView dragShadowOverlay;

    // TODO(calderwoodra): Use this for drag and drop
    @SuppressWarnings("unused")
    private DragDropController dragDropController;

    MainOldSpeedDialFragmentHostInterface(
        MainBottomNavBarBottomNavTabListener listener, ImageView dragShadowOverlay) {
      this.listener = listener;
      this.dragShadowOverlay = dragShadowOverlay;
    }

    @Override
    public void setDragDropController(DragDropController dragDropController) {
      this.dragDropController = dragDropController;
    }

    @Override
    public void showAllContactsTab() {
      listener.onContactsSelected();
    }

    @Override
    public ImageView getDragShadowOverlay() {
      return dragShadowOverlay;
    }
  }

  /** @see com.android.dialer.app.list.OnDragDropListener */
  // TODO(calderwoodra): implement drag and drop
  private static final class MainOnDragDropListener implements OnDragDropListener {

    @Override
    public void onDragStarted(int x, int y, PhoneFavoriteSquareTileView view) {}

    @Override
    public void onDragHovered(int x, int y, PhoneFavoriteSquareTileView view) {}

    @Override
    public void onDragFinished(int x, int y) {}

    @Override
    public void onDroppedOnRemove() {}
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

    private final Context context;
    private final FragmentManager fragmentManager;
    private final android.support.v4.app.FragmentManager supportFragmentManager;

    private MainBottomNavBarBottomNavTabListener(
        Context context,
        FragmentManager fragmentManager,
        android.support.v4.app.FragmentManager supportFragmentManager) {
      this.context = context;
      this.fragmentManager = fragmentManager;
      this.supportFragmentManager = supportFragmentManager;
    }

    @Override
    public void onSpeedDialSelected() {
      hideAllFragments();
      Fragment fragment = fragmentManager.findFragmentByTag(SPEED_DIAL_TAG);
      if (fragment == null) {
        if (ConfigProviderComponent.get(context)
            .getConfigProvider()
            .getBoolean("enable_new_favorites_tab", false)) {
          fragment = SpeedDialFragment.newInstance();
        } else {
          fragment = new OldSpeedDialFragment();
        }
        fragmentManager
            .beginTransaction()
            .add(R.id.fragment_container, fragment, SPEED_DIAL_TAG)
            .commit();
      } else {
        fragmentManager.beginTransaction().show(fragment).commit();
      }
    }

    @Override
    public void onCallLogSelected() {
      hideAllFragments();
      if (ConfigProviderComponent.get(context)
          .getConfigProvider()
          .getBoolean("enable_new_call_log", false)) {
        NewCallLogFragment fragment =
            (NewCallLogFragment) supportFragmentManager.findFragmentByTag(CALL_LOG_TAG);
        if (fragment == null) {
          supportFragmentManager
              .beginTransaction()
              .add(R.id.fragment_container, new NewCallLogFragment(), CALL_LOG_TAG)
              .commit();
        } else {
          supportFragmentManager.beginTransaction().show(fragment).commit();
        }
      } else {
        CallLogFragment fragment =
            (CallLogFragment) fragmentManager.findFragmentByTag(CALL_LOG_TAG);
        if (fragment == null) {
          fragmentManager
              .beginTransaction()
              .add(R.id.fragment_container, new CallLogFragment(), CALL_LOG_TAG)
              .commit();
        } else {
          fragmentManager.beginTransaction().show(fragment).commit();
        }
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
      NewVoicemailFragment fragment =
          (NewVoicemailFragment) supportFragmentManager.findFragmentByTag(VOICEMAIL_TAG);
      if (fragment == null) {
        supportFragmentManager
            .beginTransaction()
            .add(R.id.fragment_container, new NewVoicemailFragment(), VOICEMAIL_TAG)
            .commit();
      } else {
        supportFragmentManager.beginTransaction().show(fragment).commit();
      }
    }

    private void hideAllFragments() {
      FragmentTransaction supportTransaction = supportFragmentManager.beginTransaction();
      if (supportFragmentManager.findFragmentByTag(CALL_LOG_TAG) != null) {
        // NewCallLogFragment
        supportTransaction.hide(supportFragmentManager.findFragmentByTag(CALL_LOG_TAG));
      }
      if (supportFragmentManager.findFragmentByTag(VOICEMAIL_TAG) != null) {
        supportTransaction.hide(supportFragmentManager.findFragmentByTag(VOICEMAIL_TAG));
      }
      supportTransaction.commit();

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
      transaction.commit();
    }
  }
}
