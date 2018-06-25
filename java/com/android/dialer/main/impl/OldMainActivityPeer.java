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
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.QuickContact;
import android.provider.VoicemailContract;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.dialer.animation.AnimUtils;
import com.android.dialer.app.DialtactsActivity;
import com.android.dialer.app.calllog.CallLogAdapter;
import com.android.dialer.app.calllog.CallLogFragment;
import com.android.dialer.app.calllog.CallLogFragment.CallLogFragmentListener;
import com.android.dialer.app.calllog.CallLogNotificationsService;
import com.android.dialer.app.calllog.IntentProvider;
import com.android.dialer.app.calllog.VisualVoicemailCallLogFragment;
import com.android.dialer.app.list.DragDropController;
import com.android.dialer.app.list.OldSpeedDialFragment;
import com.android.dialer.app.list.OnDragDropListener;
import com.android.dialer.app.list.OnListFragmentScrolledListener;
import com.android.dialer.app.list.PhoneFavoriteSquareTileView;
import com.android.dialer.app.list.RemoveView;
import com.android.dialer.callcomposer.CallComposerActivity;
import com.android.dialer.calldetails.CallDetailsActivity;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.callintent.CallSpecificAppData;
import com.android.dialer.common.FragmentUtils.FragmentUtilListener;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.ThreadUtil;
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
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.interactions.PhoneNumberInteraction;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.main.MainActivityPeer;
import com.android.dialer.main.impl.bottomnav.BottomNavBar;
import com.android.dialer.main.impl.bottomnav.BottomNavBar.OnBottomNavTabSelectedListener;
import com.android.dialer.main.impl.bottomnav.BottomNavBar.TabIndex;
import com.android.dialer.main.impl.toolbar.MainToolbar;
import com.android.dialer.metrics.Metrics;
import com.android.dialer.metrics.MetricsComponent;
import com.android.dialer.postcall.PostCall;
import com.android.dialer.precall.PreCall;
import com.android.dialer.searchfragment.list.NewSearchFragment.SearchFragmentListener;
import com.android.dialer.smartdial.util.SmartDialPrefix;
import com.android.dialer.storage.StorageComponent;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.util.TransactionSafeActivity;
import com.android.dialer.voicemail.listui.error.VoicemailStatusCorruptionHandler;
import com.android.dialer.voicemail.listui.error.VoicemailStatusCorruptionHandler.Source;
import com.android.dialer.voicemailstatus.VisualVoicemailEnabledChecker;
import com.android.dialer.voicemailstatus.VoicemailStatusHelper;
import com.android.voicemail.VoicemailComponent;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Locale;
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
    LogUtil.enterBlock("OldMainActivityPeer.onActivityCreate");
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
    fab.setOnClickListener(
        v -> {
          Logger.get(mainActivity)
              .logImpression(DialerImpression.Type.MAIN_CLICK_FAB_TO_OPEN_DIALPAD);
          searchController.showDialpad(true);
        });

    MainToolbar toolbar = mainActivity.findViewById(R.id.toolbar);
    toolbar.maybeShowSimulator(mainActivity);
    mainActivity.setSupportActionBar(mainActivity.findViewById(R.id.toolbar));

    bottomNav = mainActivity.findViewById(R.id.bottom_nav_bar);
    MainBottomNavBarBottomNavTabListener bottomNavTabListener =
        new MainBottomNavBarBottomNavTabListener(
            mainActivity, mainActivity.getFragmentManager(), fab);
    bottomNav.addOnTabSelectedListener(bottomNavTabListener);
    // TODO(uabdullah): Handle case of when a sim is inserted/removed while the activity is open.
    boolean showVoicemailTab = canVoicemailTabBeShown(mainActivity);
    bottomNav.showVoicemail(showVoicemailTab);

    callLogFragmentListener =
        new MainCallLogFragmentListener(
            mainActivity, mainActivity.getContentResolver(), bottomNav, toolbar);
    bottomNav.addOnTabSelectedListener(callLogFragmentListener);

    searchController =
        getNewMainSearchController(
            bottomNav, fab, toolbar, mainActivity.findViewById(R.id.toolbar_shadow));
    toolbar.setSearchBarListener(searchController);

    onDialpadQueryChangedListener = getNewOnDialpadQueryChangedListener(searchController);
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
            mainActivity.findViewById(R.id.search_view_container),
            toolbar);

    lastTabController = new LastTabController(mainActivity, bottomNav, showVoicemailTab);

    // Restore our view state if needed, else initialize as if the app opened for the first time
    if (savedInstanceState != null) {
      savedLanguageCode = savedInstanceState.getString(KEY_SAVED_LANGUAGE_CODE);
      searchController.onRestoreInstanceState(savedInstanceState);
      bottomNav.selectTab(savedInstanceState.getInt(KEY_CURRENT_TAB));
    } else {
      onHandleIntent(mainActivity.getIntent());
    }
  }

  /**
   * Check and return whether the voicemail tab should be shown or not. This includes the following
   * criteria under which we show the voicemail tab:
   * <li>The voicemail number exists (e.g we are able to dial into listen to voicemail or press and
   *     hold 1) (TODO (uabdullah): Handle this case properly)
   * <li>Visual voicemail is enabled from the settings tab
   * <li>Visual voicemail carrier is supported by dialer
   * <li>There is no voicemail carrier app installed.
   *
   * @param context
   * @return return if voicemail tab should be shown or not depending on what the voicemail state is
   *     for the carrier.
   */
  private static boolean canVoicemailTabBeShown(Context context) {
    PhoneAccountHandle defaultUserSelectedAccount =
        TelecomUtil.getDefaultOutgoingPhoneAccount(context, PhoneAccount.SCHEME_VOICEMAIL);

    if (!isVoicemailAvailable(context, defaultUserSelectedAccount)) {
      LogUtil.i("OldMainActivityPeer.canVoicemailTabBeShown", "Voicemail is not available");
      return false;
    }

    if (VoicemailComponent.get(context)
        .getVoicemailClient()
        .isVoicemailEnabled(context, defaultUserSelectedAccount)) {
      LogUtil.i("OldMainActivityPeer.canVoicemailTabBeShown", "Voicemail is enabled");
      return true;
    }
    LogUtil.i("OldMainActivityPeer.canVoicemailTabBeShown", "returning false");
    return false;
  }

  /**
   * Check if voicemail is enabled/accessible.
   *
   * @return true if voicemail is enabled and accessible. Note that this can be false "temporarily"
   *     after the app boot e.g if the sim isn't fully recognized. TODO(uabdullah): Possibly add a
   *     listener of some kind to detect when a sim is recognized. TODO(uabdullah): Move this to a
   *     utility class or wrap it all in a static inner class.
   */
  private static boolean isVoicemailAvailable(
      Context context, PhoneAccountHandle defaultUserSelectedAccount) {

    if (!TelecomUtil.hasReadPhoneStatePermission(context)) {
      LogUtil.i(
          "OldMainActivityPeer.isVoicemailAvailable",
          "No read phone permisison or not the default dialer.");
      return false;
    }

    if (defaultUserSelectedAccount == null) {
      // In a single-SIM phone, there is no default outgoing phone account selected by
      // the user, so just call TelephonyManager#getVoicemailNumber directly.
      return !TextUtils.isEmpty(getTelephonyManager(context).getVoiceMailNumber());
    } else {
      return !TextUtils.isEmpty(
          TelecomUtil.getVoicemailNumber(context, defaultUserSelectedAccount));
    }
  }

  private static TelephonyManager getTelephonyManager(Context context) {
    return context.getSystemService(TelephonyManager.class);
  }

  @Override
  public void onNewIntent(Intent intent) {
    LogUtil.enterBlock("OldMainActivityPeer.onNewIntent");
    onHandleIntent(intent);
  }

  private void onHandleIntent(Intent intent) {
    // Some important implementation notes:
    //  1) If the intent contains extra data to open to a specific screen (e.g. DIAL intent), when
    //     the user leaves that screen, they will return here and add see a blank screen unless we
    //     select a tab here.
    //  2) Don't return early here in case the intent does contain extra data.
    //  3) External intents should take priority over other intents (like Calls.CONTENT_TYPE).
    if (Calls.CONTENT_TYPE.equals(intent.getType())) {
      Bundle extras = intent.getExtras();
      if (extras != null && extras.getInt(Calls.EXTRA_CALL_TYPE_FILTER) == Calls.VOICEMAIL_TYPE) {
        LogUtil.i("OldMainActivityPeer.onHandleIntent", "Voicemail content type intent");
        bottomNav.selectTab(TabIndex.VOICEMAIL);
        Logger.get(mainActivity).logImpression(DialerImpression.Type.VVM_NOTIFICATION_CLICKED);
      } else {
        LogUtil.i("OldMainActivityPeer.onHandleIntent", "Call log content type intent");
        bottomNav.selectTab(TabIndex.CALL_LOG);
      }

    } else if (isShowTabIntent(intent)) {
      LogUtil.i("OldMainActivityPeer.onHandleIntent", "Show tab intent");
      bottomNav.selectTab(getTabFromIntent(intent));
    } else if (lastTabController.isEnabled) {
      LogUtil.i("OldMainActivityPeer.onHandleIntent", "Show last tab");
      lastTabController.selectLastTab();
    } else {
      bottomNav.selectTab(TabIndex.SPEED_DIAL);
    }

    if (isDialOrAddCallIntent(intent)) {
      LogUtil.i("OldMainActivityPeer.onHandleIntent", "Dial or add call intent");
      // Dialpad will grab the intent and populate the number
      searchController.showDialpadFromNewIntent();
    }

    if (intent.getBooleanExtra(DialtactsActivity.EXTRA_CLEAR_NEW_VOICEMAILS, false)) {
      LogUtil.i("OldMainActivityPeer.onHandleIntent", "clearing all new voicemails");
      CallLogNotificationsService.markAllNewVoicemailsAsOld(mainActivity);
    }
  }

  /** Returns true if the given intent is a Dial intent with data or an Add Call intent. */
  private boolean isDialOrAddCallIntent(Intent intent) {
    if (intent == null) {
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
    return DialpadFragment.isAddCallMode(intent);
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

    if (searchController.isInSearch()
        || callLogAdapterOnActionModeStateChangedListener.isActionModeStateEnabled()) {
      bottomNav.setVisibility(View.GONE);
    } else {
      bottomNav.setVisibility(View.VISIBLE);
    }

    // add 1 sec delay to get memory snapshot so that dialer wont react slowly on resume.
    ThreadUtil.postDelayedOnUiThread(
        () ->
            MetricsComponent.get(mainActivity)
                .metrics()
                .recordMemory(Metrics.OLD_MAIN_ACTIVITY_PEER_ON_RESUME_MEMORY_EVENT_NAME),
        1000);
  }

  @Override
  public void onUserLeaveHint() {
    searchController.onUserLeaveHint();
  }

  @Override
  public void onActivityPause() {}

  @Override
  public void onActivityStop() {
    lastTabController.onActivityStop();
    callLogFragmentListener.onActivityStop(
        mainActivity.isChangingConfigurations(),
        mainActivity.getSystemService(KeyguardManager.class).isKeyguardLocked());
  }

  @Override
  public void onActivityDestroyed() {}

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
    LogUtil.i(
        "OldMainActivityPeer.onActivityResult",
        "requestCode:%d, resultCode:%d",
        requestCode,
        resultCode);
    if (requestCode == ActivityRequestCodes.DIALTACTS_VOICE_SEARCH) {
      searchController.onVoiceResults(resultCode, data);
    } else if (requestCode == ActivityRequestCodes.DIALTACTS_CALL_COMPOSER) {
      if (resultCode == AppCompatActivity.RESULT_FIRST_USER) {
        LogUtil.i(
            "OldMainActivityPeer.onActivityResult", "returned from call composer, error occurred");
        String message =
            mainActivity.getString(
                R.string.call_composer_connection_failed,
                data.getStringExtra(CallComposerActivity.KEY_CONTACT_NAME));
        Snackbar.make(snackbarContainer, message, Snackbar.LENGTH_LONG).show();
      } else {
        LogUtil.i("OldMainActivityPeer.onActivityResult", "returned from call composer, no error");
      }

    } else if (requestCode == ActivityRequestCodes.DIALTACTS_CALL_DETAILS) {
      if (resultCode == AppCompatActivity.RESULT_OK
          && data != null
          && data.getBooleanExtra(CallDetailsActivity.EXTRA_HAS_ENRICHED_CALL_DATA, false)) {
        String number = data.getStringExtra(CallDetailsActivity.EXTRA_PHONE_NUMBER);
        int snackbarDurationMillis = 5_000;
        Snackbar.make(
                snackbarContainer,
                mainActivity.getString(R.string.ec_data_deleted),
                snackbarDurationMillis)
            .setAction(
                R.string.view_conversation,
                v -> mainActivity.startActivity(
                    IntentProvider.getSendSmsIntentProvider(number).getClickIntent(mainActivity)))
            .setActionTextColor(
                ContextCompat.getColor(mainActivity, R.color.dialer_snackbar_action_text_color))
            .show();
      }

    } else if (requestCode == ActivityRequestCodes.DIALTACTS_DUO) {
      // We just returned from starting Duo for a task. Reload our reachability data since it
      // may have changed after a user finished activating Duo.
      DuoComponent.get(mainActivity).getDuo().reloadReachability(mainActivity);

    } else {
      LogUtil.e("OldMainActivityPeer.onActivityResult", "Unknown request code: " + requestCode);
    }
  }

  @Override
  public boolean onBackPressed() {
    LogUtil.enterBlock("OldMainActivityPeer.onBackPressed");
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
    } else if (callbackInterface.isInstance(searchController)) {
      return (T) searchController;
    } else {
      return null;
    }
  }

  public MainSearchController getNewMainSearchController(
      BottomNavBar bottomNavBar,
      FloatingActionButton fab,
      MainToolbar mainToolbar,
      View toolbarShadow) {
    return new MainSearchController(mainActivity, bottomNavBar, fab, mainToolbar, toolbarShadow);
  }

  public MainOnDialpadQueryChangedListener getNewOnDialpadQueryChangedListener(
      MainSearchController mainSearchController) {
    return new MainOnDialpadQueryChangedListener(mainSearchController);
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
  protected static class MainOnDialpadQueryChangedListener
      implements OnDialpadQueryChangedListener {

    private final MainSearchController searchController;

    protected MainOnDialpadQueryChangedListener(MainSearchController searchController) {
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
      searchController.onCallPlacedFromSearch();
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
      searchController.onCallPlacedFromSearch();
    }
  }

  /** @see DialpadFragment.HostInterface */
  private static final class MainDialpadFragmentHost implements DialpadFragment.HostInterface {

    @Override
    public boolean onDialpadSpacerTouchWithEmptyQuery() {
      // No-op, just let the clicks fall through to the search list
      return false;
    }

    @Override
    public boolean shouldShowDialpadChooser() {
      // Never show the dialpad chooser. Ever.
      return false;
    }
  }

  /** @see CallLogAdapter.OnActionModeStateChangedListener */
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
      LogUtil.i("MainCallLogHost.enableFloatingButton", "enabled: " + enabled);
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

    private final ContentObserver voicemailStatusObserver =
        new ContentObserver(new Handler()) {
          @Override
          public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            callLogQueryHandler.fetchVoicemailStatus();
          }
        };

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

    private void registerVoicemailStatusContentObserver(Context context) {

      if (PermissionsUtil.hasReadVoicemailPermissions(context)
          && PermissionsUtil.hasAddVoicemailPermissions(context)) {
        context
            .getContentResolver()
            .registerContentObserver(
                VoicemailContract.Status.CONTENT_URI, true, voicemailStatusObserver);
      } else {
        LogUtil.w(
            "MainCallLogFragmentListener.registerVoicemailStatusContentObserver",
            "no voicemail read/add permissions");
      }
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
      LogUtil.i("OldMainActivityPeer.MainCallLogFragmentListener", "onVoicemailStatusFetched");
      VoicemailStatusCorruptionHandler.maybeFixVoicemailStatus(
          context, statusCursor, Source.Activity);

      // Update hasActiveVoicemailProvider, which controls the number of tabs displayed.
      int numberOfActiveVoicemailSources =
          VoicemailStatusHelper.getNumberActivityVoicemailSources(statusCursor);

      boolean hasActiveVoicemailProvider = numberOfActiveVoicemailSources > 0;
      LogUtil.i(
          "OldMainActivityPeer.onVoicemailStatusFetched",
          String.format(
              Locale.US,
              "hasActiveVoicemailProvider:%b, number of active voicemail sources:%d",
              hasActiveVoicemailProvider,
              numberOfActiveVoicemailSources));

      if (hasActiveVoicemailProvider) {
        // TODO(yueg): Use new logging for VVM_TAB_VISIBLE
        // Logger.get(context).logImpression(DialerImpression.Type.VVM_TAB_VISIBLE);
        bottomNavBar.showVoicemail(true);
        callLogQueryHandler.fetchVoicemailUnreadCount();
      } else {
        bottomNavBar.showVoicemail(false);
      }

      StorageComponent.get(context)
          .unencryptedSharedPrefs()
          .edit()
          .putBoolean(
              VisualVoicemailEnabledChecker.PREF_KEY_HAS_ACTIVE_VOICEMAIL_PROVIDER,
              hasActiveVoicemailProvider)
          .apply();

      // TODO(uabdullah): Check if we need to force move to the VM tab (e.g in the event of
      // clicking a vm notification and a status wasn't yet fetched).
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
      registerVoicemailStatusContentObserver(context);
      callLogQueryHandler.fetchVoicemailStatus();
      callLogQueryHandler.fetchMissedCallsUnreadCount();
      // Reset the tab on resume to restart the timer
      setCurrentTab(bottomNavBar.getSelectedTab());
    }

    /** Should be called when {@link Activity#onStop()} is called. */
    public void onActivityStop(boolean changingConfigurations, boolean keyguardLocked) {
      context.getContentResolver().unregisterContentObserver(voicemailStatusObserver);
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
    private final MainToolbar toolbar;

    // TODO(calderwoodra): Use this for drag and drop
    @SuppressWarnings("unused")
    private DragDropController dragDropController;

    MainOldSpeedDialFragmentHost(
        BottomNavBar bottomNavBar,
        ImageView dragShadowOverlay,
        RemoveView removeView,
        View searchViewContainer,
        MainToolbar toolbar) {
      this.bottomNavBar = bottomNavBar;
      this.dragShadowOverlay = dragShadowOverlay;
      this.removeView = removeView;
      this.searchViewContainer = searchViewContainer;
      this.toolbar = toolbar;
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
    public void setHasFrequents(boolean hasFrequents) {
      toolbar.showClearFrequents(hasFrequents);
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
   * the main tabs and FAB.
   *
   * <p>TODO(calderwoodra, uabdullah): Rethink the logic for showing/hiding the FAB when new
   * voicemail is ready.
   */
  private static final class MainBottomNavBarBottomNavTabListener
      implements OnBottomNavTabSelectedListener {

    private static final String SPEED_DIAL_TAG = "speed_dial";
    private static final String CALL_LOG_TAG = "call_log";
    private static final String CONTACTS_TAG = "contacts";
    private static final String VOICEMAIL_TAG = "voicemail";

    private final Context context;
    private final FragmentManager fragmentManager;
    private final FloatingActionButton fab;

    @TabIndex private int selectedTab = -1;

    private MainBottomNavBarBottomNavTabListener(
        Context context, FragmentManager fragmentManager, FloatingActionButton fab) {
      this.context = context;
      this.fragmentManager = fragmentManager;
      this.fab = fab;
      preloadCallLogFragment();
    }

    private void preloadCallLogFragment() {
      if (ConfigProviderBindings.get(context).getBoolean("nui_preload_call_log", true)) {
        CallLogFragment fragment = new CallLogFragment();
        fragmentManager
            .beginTransaction()
            .add(R.id.fragment_container, fragment, CALL_LOG_TAG)
            .hide(fragment)
            .commit();
      }
    }

    @Override
    public void onSpeedDialSelected() {
      LogUtil.enterBlock("MainBottomNavBarBottomNavTabListener.onSpeedDialSelected");
      if (selectedTab != TabIndex.SPEED_DIAL) {
        Logger.get(context).logImpression(DialerImpression.Type.MAIN_SWITCH_TAB_TO_FAVORITE);
        selectedTab = TabIndex.SPEED_DIAL;
      }
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
      fab.show();
    }

    @Override
    public void onCallLogSelected() {
      LogUtil.enterBlock("MainBottomNavBarBottomNavTabListener.onCallLogSelected");
      if (selectedTab != TabIndex.CALL_LOG) {
        Logger.get(context).logImpression(DialerImpression.Type.MAIN_SWITCH_TAB_TO_CALL_LOG);
        selectedTab = TabIndex.CALL_LOG;
      }
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
      fab.show();
    }

    @Override
    public void onContactsSelected() {
      LogUtil.enterBlock("MainBottomNavBarBottomNavTabListener.onContactsSelected");
      if (selectedTab != TabIndex.CONTACTS) {
        Logger.get(context).logImpression(DialerImpression.Type.MAIN_SWITCH_TAB_TO_CONTACTS);
        selectedTab = TabIndex.CONTACTS;
      }
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
      fab.show();
    }

    @Override
    public void onVoicemailSelected() {
      LogUtil.enterBlock("MainBottomNavBarBottomNavTabListener.onVoicemailSelected");
      if (selectedTab != TabIndex.VOICEMAIL) {
        Logger.get(context).logImpression(DialerImpression.Type.MAIN_SWITCH_TAB_TO_VOICEMAIL);
        selectedTab = TabIndex.VOICEMAIL;
      }
      hideAllFragments();
      VisualVoicemailCallLogFragment fragment =
          (VisualVoicemailCallLogFragment) fragmentManager.findFragmentByTag(VOICEMAIL_TAG);
      if (fragment == null) {
        fragment = new VisualVoicemailCallLogFragment();
        fragmentManager
            .beginTransaction()
            .add(R.id.fragment_container, fragment, VOICEMAIL_TAG)
            .commit();
      } else {
        fragmentManager.beginTransaction().show(fragment).commit();
      }
      fragment.setUserVisibleHint(true);
      fragment.onVisible();
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
        VisualVoicemailCallLogFragment fragment =
            (VisualVoicemailCallLogFragment) fragmentManager.findFragmentByTag(VOICEMAIL_TAG);
        fragment.setUserVisibleHint(false);
        fragment.onNotVisible();
        transaction.hide(fragment);
      }
      transaction.commit();
    }
  }

  private static final class LastTabController {

    private final Context context;
    private final BottomNavBar bottomNavBar;
    private final boolean isEnabled;
    private final boolean canShowVoicemailTab;

    LastTabController(Context context, BottomNavBar bottomNavBar, boolean canShowVoicemailTab) {
      this.context = context;
      this.bottomNavBar = bottomNavBar;
      isEnabled = ConfigProviderBindings.get(context).getBoolean("last_tab_enabled", false);
      this.canShowVoicemailTab = canShowVoicemailTab;
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

      // If the voicemail tab cannot be shown, default to showing speed dial
      if (tabIndex == TabIndex.VOICEMAIL && !canShowVoicemailTab) {
        tabIndex = TabIndex.SPEED_DIAL;
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
