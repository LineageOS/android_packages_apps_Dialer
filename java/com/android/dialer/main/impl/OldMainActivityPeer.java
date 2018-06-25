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

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.QuickContact;
import android.provider.VoicemailContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.ActionMode;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.dialer.animation.AnimUtils;
import com.android.dialer.app.MainComponent;
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
import com.android.dialer.calldetails.OldCallDetailsActivity;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.callintent.CallSpecificAppData;
import com.android.dialer.calllog.CallLogComponent;
import com.android.dialer.calllog.config.CallLogConfigComponent;
import com.android.dialer.calllog.ui.NewCallLogFragment;
import com.android.dialer.common.FragmentUtils.FragmentUtilListener;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DefaultFutureCallback;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.common.concurrent.UiListener;
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
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.i18n.LocaleUtils;
import com.android.dialer.interactions.PhoneNumberInteraction;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ScreenEvent;
import com.android.dialer.main.MainActivityPeer;
import com.android.dialer.main.impl.bottomnav.BottomNavBar;
import com.android.dialer.main.impl.bottomnav.BottomNavBar.OnBottomNavTabSelectedListener;
import com.android.dialer.main.impl.bottomnav.BottomNavBar.TabIndex;
import com.android.dialer.main.impl.bottomnav.MissedCallCountObserver;
import com.android.dialer.main.impl.toolbar.MainToolbar;
import com.android.dialer.metrics.Metrics;
import com.android.dialer.metrics.MetricsComponent;
import com.android.dialer.postcall.PostCall;
import com.android.dialer.precall.PreCall;
import com.android.dialer.promotion.Promotion;
import com.android.dialer.promotion.Promotion.PromotionType;
import com.android.dialer.promotion.PromotionComponent;
import com.android.dialer.searchfragment.list.NewSearchFragment.SearchFragmentListener;
import com.android.dialer.smartdial.util.SmartDialPrefix;
import com.android.dialer.speeddial.SpeedDialFragment;
import com.android.dialer.storage.StorageComponent;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.theme.base.Theme;
import com.android.dialer.theme.base.ThemeComponent;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.util.TransactionSafeActivity;
import com.android.dialer.voicemail.listui.NewVoicemailFragment;
import com.android.dialer.voicemail.listui.error.VoicemailStatusCorruptionHandler;
import com.android.dialer.voicemail.listui.error.VoicemailStatusCorruptionHandler.Source;
import com.android.dialer.voicemailstatus.VisualVoicemailEnabledChecker;
import com.android.dialer.voicemailstatus.VoicemailStatusHelper;
import com.android.voicemail.VoicemailComponent;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Locale;
import java.util.Optional;
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

  // TODO(calderwoodra): change to AppCompatActivity once new speed dial ships
  private final TransactionSafeActivity activity;

  private final BroadcastReceiver disableCallLogFrameworkReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (bottomNavTabListener == null) {
            return;
          }
          /*
           * Remove the NewCallLogFragment and NewVoicemailFragment if it is currently attached. If
           * this is not done, user interaction with the fragment could cause call log framework
           * state to be unexpectedly written. For example scrolling could cause the
           * AnnotatedCallLog to be read (which would trigger database creation).
           */
          bottomNavTabListener.disableNewCallLogFragment();
          bottomNavTabListener.disableNewVoicemailFragment();
        }
      };

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
  private MainSpeedDialFragmentHost speedDialFragmentHost;

  /** Language the device was in last time {@link #onSaveInstanceState(Bundle)} was called. */
  private String savedLanguageCode;

  private LastTabController lastTabController;

  private BottomNavBar bottomNav;
  private MainBottomNavBarBottomNavTabListener bottomNavTabListener;
  private View snackbarContainer;
  private MissedCallCountObserver missedCallCountObserver;
  private UiListener<String> getLastOutgoingCallListener;
  private UiListener<Integer> missedCallObserverUiListener;
  private View bottomSheet;

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

  public OldMainActivityPeer(TransactionSafeActivity activity) {
    this.activity = activity;
  }

  @Override
  public void onActivityCreate(Bundle savedInstanceState) {
    LogUtil.enterBlock("OldMainActivityPeer.onActivityCreate");
    setTheme();
    activity.setContentView(R.layout.main_activity);
    initUiListeners();
    initLayout(savedInstanceState);
    SmartDialPrefix.initializeNanpSettings(activity);
  }

  /** should be called before {@link AppCompatActivity#setContentView(int)}. */
  private void setTheme() {
    @Theme.Type int theme = ThemeComponent.get(activity).theme().getTheme();
    switch (theme) {
      case Theme.DARK:
        activity.setTheme(R.style.MainActivityTheme_Dark);
        break;
      case Theme.LIGHT:
      case Theme.LIGHT_M2:
        activity.setTheme(R.style.MainActivityTheme);
        break;
      case Theme.UNKNOWN:
      default:
        throw new IllegalArgumentException("Invalid theme.");
    }
  }

  private void initUiListeners() {
    getLastOutgoingCallListener =
        DialerExecutorComponent.get(activity)
            .createUiListener(activity.getFragmentManager(), "Query last phone number");
    missedCallObserverUiListener =
        DialerExecutorComponent.get(activity)
            .createUiListener(activity.getFragmentManager(), "Missed call observer");
  }

  private void initLayout(Bundle savedInstanceState) {
    onContactSelectedListener = new MainOnContactSelectedListener(activity);
    dialpadFragmentHostInterface = new MainDialpadFragmentHost();

    snackbarContainer = activity.findViewById(R.id.coordinator_layout);
    bottomSheet = activity.findViewById(R.id.promotion_bottom_sheet);
    BottomSheetBehavior<View> bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

    FloatingActionButton fab = activity.findViewById(R.id.fab);
    fab.setOnClickListener(
        v -> {
          Logger.get(activity).logImpression(DialerImpression.Type.MAIN_CLICK_FAB_TO_OPEN_DIALPAD);
          searchController.showDialpad(true);
          if (callLogAdapterOnActionModeStateChangedListener.isEnabled) {
            LogUtil.i("OldMainActivityPeer.onFabClicked", "closing multiselect");
            callLogAdapterOnActionModeStateChangedListener.actionMode.finish();
          }
        });

    MainToolbar toolbar = activity.findViewById(R.id.toolbar);
    toolbar.maybeShowSimulator(activity);
    activity.setSupportActionBar(activity.findViewById(R.id.toolbar));

    bottomNav = activity.findViewById(R.id.bottom_nav_bar);
    bottomNavTabListener =
        new MainBottomNavBarBottomNavTabListener(
            activity,
            activity.getFragmentManager(),
            activity.getSupportFragmentManager(),
            fab,
            bottomSheet);
    bottomNav.addOnTabSelectedListener(bottomNavTabListener);
    // TODO(uabdullah): Handle case of when a sim is inserted/removed while the activity is open.
    boolean showVoicemailTab = canVoicemailTabBeShown(activity);
    bottomNav.showVoicemail(showVoicemailTab);

    missedCallCountObserver =
        new MissedCallCountObserver(
            activity.getApplicationContext(), bottomNav, missedCallObserverUiListener);

    callLogFragmentListener =
        new MainCallLogFragmentListener(
            activity, activity.getContentResolver(), bottomNav, toolbar, bottomNavTabListener);
    bottomNav.addOnTabSelectedListener(callLogFragmentListener);

    searchController =
        getNewMainSearchController(
            bottomNav, fab, toolbar, activity.findViewById(R.id.toolbar_shadow), snackbarContainer);
    toolbar.setSearchBarListener(searchController);

    onDialpadQueryChangedListener = getNewOnDialpadQueryChangedListener(searchController);
    dialpadListener =
        new MainDialpadListener(activity, searchController, getLastOutgoingCallListener);
    searchFragmentListener = new MainSearchFragmentListener(searchController);
    callLogAdapterOnActionModeStateChangedListener =
        new MainCallLogAdapterOnActionModeStateChangedListener();
    callLogHostInterface = new MainCallLogHost(searchController, fab);

    onListFragmentScrolledListener = new MainOnListFragmentScrolledListener(snackbarContainer);
    onPhoneNumberPickerActionListener = new MainOnPhoneNumberPickerActionListener(activity);
    oldSpeedDialFragmentHost =
        new MainOldSpeedDialFragmentHost(
            activity,
            activity.findViewById(R.id.root_layout),
            bottomNav,
            activity.findViewById(R.id.contact_tile_drag_shadow_overlay),
            activity.findViewById(R.id.remove_view),
            activity.findViewById(R.id.search_view_container),
            toolbar);
    speedDialFragmentHost =
        new MainSpeedDialFragmentHost(
            toolbar,
            activity.findViewById(R.id.root_layout),
            activity.findViewById(R.id.coordinator_layout),
            activity.findViewById(R.id.fragment_container));

    lastTabController = new LastTabController(activity, bottomNav, showVoicemailTab);

    // Restore our view state if needed, else initialize as if the app opened for the first time
    if (savedInstanceState != null) {
      savedLanguageCode = savedInstanceState.getString(KEY_SAVED_LANGUAGE_CODE);
      searchController.onRestoreInstanceState(savedInstanceState);
      bottomNav.selectTab(savedInstanceState.getInt(KEY_CURRENT_TAB));
    } else {
      onHandleIntent(activity.getIntent());
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
  @SuppressLint("MissingPermission")
  private static boolean isVoicemailAvailable(
      Context context, PhoneAccountHandle defaultUserSelectedAccount) {

    if (!PermissionsUtil.hasReadPhoneStatePermissions(context)) {
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
    @TabIndex int tabToSelect;
    if (Calls.CONTENT_TYPE.equals(intent.getType())) {
      Bundle extras = intent.getExtras();
      if (extras != null && extras.getInt(Calls.EXTRA_CALL_TYPE_FILTER) == Calls.VOICEMAIL_TYPE) {
        LogUtil.i("OldMainActivityPeer.onHandleIntent", "Voicemail content type intent");
        tabToSelect = TabIndex.VOICEMAIL;
        Logger.get(activity).logImpression(DialerImpression.Type.VVM_NOTIFICATION_CLICKED);
      } else {
        LogUtil.i("OldMainActivityPeer.onHandleIntent", "Call log content type intent");
        tabToSelect = TabIndex.CALL_LOG;
      }

    } else if (isShowTabIntent(intent)) {
      LogUtil.i("OldMainActivityPeer.onHandleIntent", "Show tab intent");
      tabToSelect = getTabFromIntent(intent);
    } else {
      LogUtil.i("OldMainActivityPeer.onHandleIntent", "Show last tab");
      tabToSelect = lastTabController.getLastTab();
    }
    logImpressionForSelectedTab(tabToSelect);
    bottomNav.selectTab(tabToSelect);

    if (isDialOrAddCallIntent(intent)) {
      LogUtil.i("OldMainActivityPeer.onHandleIntent", "Dial or add call intent");
      // Dialpad will grab the intent and populate the number
      searchController.showDialpadFromNewIntent();
      Logger.get(activity).logImpression(DialerImpression.Type.MAIN_OPEN_WITH_DIALPAD);
    }

    if (intent.getBooleanExtra(MainComponent.EXTRA_CLEAR_NEW_VOICEMAILS, false)) {
      LogUtil.i("OldMainActivityPeer.onHandleIntent", "clearing all new voicemails");
      CallLogNotificationsService.markAllNewVoicemailsAsOld(activity);
    }
  }

  /** Log impression for non user tab selection. */
  private void logImpressionForSelectedTab(@TabIndex int tab) {
    if (tab == TabIndex.SPEED_DIAL) {
      Logger.get(activity).logImpression(DialerImpression.Type.MAIN_OPEN_WITH_TAB_FAVORITE);
    } else if (tab == TabIndex.CALL_LOG) {
      Logger.get(activity).logImpression(DialerImpression.Type.MAIN_OPEN_WITH_TAB_CALL_LOG);
    } else if (tab == TabIndex.CONTACTS) {
      Logger.get(activity).logImpression(DialerImpression.Type.MAIN_OPEN_WITH_TAB_CONTACTS);
    } else if (tab == TabIndex.VOICEMAIL) {
      Logger.get(activity).logImpression(DialerImpression.Type.MAIN_OPEN_WITH_TAB_VOICEMAIL);
    } else {
      throw new IllegalStateException("Invalid tab: " + tab);
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

  @SuppressLint("MissingPermission")
  @Override
  public void onActivityResume() {
    LogUtil.enterBlock("MainBottomNavBarBottomNavTabListener.onActivityResume");
    callLogFragmentListener.onActivityResume();
    // Start the thread that updates the smart dial database if the activity is recreated with a
    // language change.
    boolean forceUpdate =
        !LocaleUtils.getLocale(activity).getISO3Language().equals(savedLanguageCode);
    Database.get(activity).getDatabaseHelper(activity).startSmartDialUpdateThread(forceUpdate);
    showPostCallPrompt();

    if (searchController.isInSearch()
        || callLogAdapterOnActionModeStateChangedListener.isActionModeStateEnabled()) {
      bottomNav.setVisibility(View.GONE);
    } else {
      bottomNav.setVisibility(View.VISIBLE);
    }

    /*
     * While the activity is running, listen for the call log framework being disabled. If this is
     * not done, user interaction with the fragment could cause call log framework state to be
     * unexpectedly written. For example scrolling could cause the AnnotatedCallLog to be read
     * (which would trigger database creation).
     */
    LocalBroadcastManager.getInstance(activity)
        .registerReceiver(
            disableCallLogFrameworkReceiver, new IntentFilter("disableCallLogFramework"));

    /*
     * Similar to above, if the new call log/new voicemail is being shown and then the activity is
     * paused, when the user returns we need to remove the NewCallLogFragment if the framework has
     * been disabled in the meantime.
     */
    bottomNavTabListener.ensureCorrectCallLogShown();
    bottomNavTabListener.ensureCorrectVoicemailShown();

    // Config the badge of missed calls for the new call log.
    if (bottomNavTabListener.newCallLogFragmentActive()) {
      if (PermissionsUtil.hasCallLogReadPermissions(activity)) {
        missedCallCountObserver.onChange(false); // Set the initial value for the badge
        activity
            .getContentResolver()
            .registerContentObserver(Calls.CONTENT_URI, true, missedCallCountObserver);
      } else {
        bottomNav.setNotificationCount(TabIndex.CALL_LOG, 0);
      }
    }

    // add 1 sec delay to get memory snapshot so that dialer wont react slowly on resume.
    ThreadUtil.postDelayedOnUiThread(
        () ->
            MetricsComponent.get(activity)
                .metrics()
                .recordMemory(Metrics.OLD_MAIN_ACTIVITY_PEER_ON_RESUME_MEMORY_EVENT_NAME),
        1000);
  }

  @Override
  public void onUserLeaveHint() {
    searchController.onUserLeaveHint();
  }

  @Override
  public void onActivityPause() {
    searchController.onActivityPause();
    LocalBroadcastManager.getInstance(activity).unregisterReceiver(disableCallLogFrameworkReceiver);
    activity.getContentResolver().unregisterContentObserver(missedCallCountObserver);
  }

  @Override
  public void onActivityStop() {
    lastTabController.onActivityStop();
    callLogFragmentListener.onActivityStop(
        activity.isChangingConfigurations(),
        activity.getSystemService(KeyguardManager.class).isKeyguardLocked());
  }

  @Override
  public void onActivityDestroyed() {}

  private void showPostCallPrompt() {
    if (TelecomUtil.isInManagedCall(activity)) {
      // No prompt to show if the user is in a call
      return;
    }

    if (searchController.isInSearch()) {
      // Don't show the prompt if we're in the search ui
      return;
    }

    PostCall.promptUserForMessageIfNecessary(activity, snackbarContainer);
  }

  @Override
  public void onSaveInstanceState(Bundle bundle) {
    bundle.putString(KEY_SAVED_LANGUAGE_CODE, LocaleUtils.getLocale(activity).getISO3Language());
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
            activity.getString(
                R.string.call_composer_connection_failed,
                data.getStringExtra(CallComposerActivity.KEY_CONTACT_NAME));
        Snackbar.make(snackbarContainer, message, Snackbar.LENGTH_LONG).show();
      } else {
        LogUtil.i("OldMainActivityPeer.onActivityResult", "returned from call composer, no error");
      }

    } else if (requestCode == ActivityRequestCodes.DIALTACTS_CALL_DETAILS) {
      if (resultCode == AppCompatActivity.RESULT_OK
          && data != null
          && data.getBooleanExtra(OldCallDetailsActivity.EXTRA_HAS_ENRICHED_CALL_DATA, false)) {
        String number = data.getStringExtra(OldCallDetailsActivity.EXTRA_PHONE_NUMBER);
        int snackbarDurationMillis = 5_000;
        Snackbar.make(
                snackbarContainer,
                activity.getString(R.string.ec_data_deleted),
                snackbarDurationMillis)
            .setAction(
                R.string.view_conversation,
                v ->
                    activity.startActivity(
                        IntentProvider.getSendSmsIntentProvider(number).getClickIntent(activity)))
            .setActionTextColor(
                ContextCompat.getColor(activity, R.color.dialer_snackbar_action_text_color))
            .show();
      }

    } else if (requestCode == ActivityRequestCodes.DIALTACTS_DUO) {
      // We just returned from starting Duo for a task. Reload our reachability data since it
      // may have changed after a user finished activating Duo.
      DuoComponent.get(activity).getDuo().reloadReachability(activity);

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
    } else if (callbackInterface.isInstance(speedDialFragmentHost)) {
      return (T) speedDialFragmentHost;
    } else {
      return null;
    }
  }

  public MainSearchController getNewMainSearchController(
      BottomNavBar bottomNavBar,
      FloatingActionButton fab,
      MainToolbar mainToolbar,
      View toolbarShadow,
      View fragmentContainer) {
    return new MainSearchController(
        activity, bottomNavBar, fab, mainToolbar, toolbarShadow, fragmentContainer);
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

    @Override
    public void requestingPermission() {
      searchController.requestingPermission();
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
    private ActionMode actionMode;

    @Override
    public void onActionModeStateChanged(ActionMode actionMode, boolean isEnabled) {
      this.actionMode = actionMode;
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
    private final MainBottomNavBarBottomNavTabListener bottomNavTabListener;

    private @TabIndex int currentTab = TabIndex.SPEED_DIAL;
    private long timeSelected = -1;
    private boolean activityIsAlive;

    private final ContentObserver voicemailStatusObserver =
        new ContentObserver(new Handler()) {
          @Override
          public void onChange(boolean selfChange) {
            LogUtil.i(
                "MainCallLogFragmentListener",
                "voicemailStatusObserver.onChange selfChange:%b",
                selfChange);
            super.onChange(selfChange);
            callLogQueryHandler.fetchVoicemailStatus();
          }
        };

    MainCallLogFragmentListener(
        Context context,
        ContentResolver contentResolver,
        BottomNavBar bottomNavBar,
        Toolbar toolbar,
        MainBottomNavBarBottomNavTabListener bottomNavTabListener) {
      callLogQueryHandler = new CallLogQueryHandler(context, contentResolver, this);
      this.context = context;
      this.bottomNavBar = bottomNavBar;
      this.toolbar = toolbar;
      this.bottomNavTabListener = bottomNavTabListener;
    }

    private void registerVoicemailStatusContentObserver(Context context) {
      LogUtil.enterBlock("MainCallLogFragmentListener.registerVoicemailStatusContentObserver");
      if (PermissionsUtil.hasReadVoicemailPermissions(context)
          && PermissionsUtil.hasAddVoicemailPermissions(context)) {
        LogUtil.i("MainCallLogFragmentListener.registerVoicemailStatusContentObserver", "register");
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
        Logger.get(context).logImpression(DialerImpression.Type.MAIN_VVM_TAB_VISIBLE);
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
      if (bottomNavTabListener.newCallLogFragmentActive()) {
        Futures.addCallback(
            CallLogComponent.get(context).getClearMissedCalls().clearAll(),
            new DefaultFutureCallback<>(),
            MoreExecutors.directExecutor());
      } else {
        callLogQueryHandler.markMissedCallsAsRead();
        CallLogNotificationsService.cancelAllMissedCalls(context);
      }
    }

    private void setCurrentTab(@TabIndex int tabIndex) {
      if (currentTab == TabIndex.CALL_LOG && tabIndex != TabIndex.CALL_LOG) {
        markMissedCallsAsReadAndRemoveNotification();
      }
      currentTab = tabIndex;
      timeSelected = System.currentTimeMillis();
    }

    public void onActivityResume() {
      LogUtil.enterBlock("MainCallLogFragmentListener.onActivityResume");
      activityIsAlive = true;
      registerVoicemailStatusContentObserver(context);
      // TODO(a bug): Don't use callLogQueryHandler
      callLogQueryHandler.fetchVoicemailStatus();

      if (!bottomNavTabListener.newCallLogFragmentActive()) {
        callLogQueryHandler.fetchMissedCallsUnreadCount();
      }
      // Reset the tab on resume to restart the timer
      setCurrentTab(bottomNavBar.getSelectedTab());
    }

    /** Should be called when {@link AppCompatActivity#onStop()} is called. */
    public void onActivityStop(boolean changingConfigurations, boolean keyguardLocked) {
      context.getContentResolver().unregisterContentObserver(voicemailStatusObserver);
      activityIsAlive = false;
      // The new call log fragment handles this on its own.
      if (!bottomNavTabListener.newCallLogFragmentActive()
          && viewedCallLogTabPastTimeThreshold()
          && !changingConfigurations
          && !keyguardLocked) {
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

    private final Context context;
    private final View rootLayout;
    private final BottomNavBar bottomNavBar;
    private final ImageView dragShadowOverlay;
    private final RemoveView removeView;
    private final View removeViewContent;
    private final View searchViewContainer;
    private final MainToolbar toolbar;

    MainOldSpeedDialFragmentHost(
        Context context,
        View rootLayout,
        BottomNavBar bottomNavBar,
        ImageView dragShadowOverlay,
        RemoveView removeView,
        View searchViewContainer,
        MainToolbar toolbar) {
      this.context = context;
      this.rootLayout = rootLayout;
      this.bottomNavBar = bottomNavBar;
      this.dragShadowOverlay = dragShadowOverlay;
      this.removeView = removeView;
      this.searchViewContainer = searchViewContainer;
      this.toolbar = toolbar;
      removeViewContent = removeView.findViewById(R.id.remove_view_content);
    }

    @Override
    public void setDragDropController(DragDropController dragDropController) {
      removeView.setDragDropController(dragDropController);
      rootLayout.setOnDragListener(
          (v, event) -> {
            if (event.getAction() == DragEvent.ACTION_DRAG_LOCATION) {
              dragDropController.handleDragHovered(v, (int) event.getX(), (int) event.getY());
            }
            return true;
          });
    }

    @Override
    public void showAllContactsTab() {
      bottomNavBar.selectTab(TabIndex.CONTACTS);
      Logger.get(context).logImpression(DialerImpression.Type.MAIN_OPEN_WITH_TAB_CONTACTS);
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
        AnimUtils.crossFadeViews(removeViewContent, searchViewContainer, 300);
      } else {
        AnimUtils.crossFadeViews(searchViewContainer, removeViewContent, 300);
      }
    }
  }

  /**
   * Handles the callbacks for {@link SpeedDialFragment}.
   *
   * @see SpeedDialFragment.HostInterface
   */
  private static final class MainSpeedDialFragmentHost implements SpeedDialFragment.HostInterface {

    private final MainToolbar toolbar;
    private final ViewGroup rootLayout;
    private final ViewGroup coordinatorLayout;
    private final ViewGroup fragmentContainer;

    MainSpeedDialFragmentHost(
        MainToolbar toolbar,
        ViewGroup rootLayout,
        ViewGroup coordinatorLayout,
        ViewGroup fragmentContainer) {
      this.toolbar = toolbar;
      this.rootLayout = rootLayout;
      this.coordinatorLayout = coordinatorLayout;
      this.fragmentContainer = fragmentContainer;
    }

    @Override
    public void setHasFrequents(boolean hasFrequents) {
      toolbar.showClearFrequents(hasFrequents);
    }

    @Override
    public void dragFavorite(boolean start) {
      rootLayout.setClipChildren(!start);
      coordinatorLayout.setClipChildren(!start);
      fragmentContainer.setClipChildren(!start);
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

    private final TransactionSafeActivity activity;
    private final FragmentManager fragmentManager;
    private final android.support.v4.app.FragmentManager supportFragmentManager;
    private final FloatingActionButton fab;
    private final View bottomSheet;

    @TabIndex private int selectedTab = -1;

    private MainBottomNavBarBottomNavTabListener(
        TransactionSafeActivity activity,
        FragmentManager fragmentManager,
        android.support.v4.app.FragmentManager supportFragmentManager,
        FloatingActionButton fab,
        View bottomSheet) {
      this.activity = activity;
      this.fragmentManager = fragmentManager;
      this.supportFragmentManager = supportFragmentManager;
      this.fab = fab;
      this.bottomSheet = bottomSheet;
    }

    @Override
    public void onSpeedDialSelected() {
      LogUtil.enterBlock("MainBottomNavBarBottomNavTabListener.onSpeedDialSelected");
      if (selectedTab == TabIndex.SPEED_DIAL) {
        return;
      }
      Logger.get(activity).logScreenView(ScreenEvent.Type.MAIN_SPEED_DIAL, activity);
      selectedTab = TabIndex.SPEED_DIAL;

      if (ConfigProviderComponent.get(activity)
          .getConfigProvider()
          .getBoolean("enable_new_favorites_tab", false)) {
        android.support.v4.app.Fragment supportFragment =
            supportFragmentManager.findFragmentByTag(SPEED_DIAL_TAG);
        showSupportFragment(
            supportFragment == null ? SpeedDialFragment.newInstance() : supportFragment,
            SPEED_DIAL_TAG);
      } else {
        Fragment fragment = fragmentManager.findFragmentByTag(SPEED_DIAL_TAG);
        showFragment(fragment == null ? new OldSpeedDialFragment() : fragment, SPEED_DIAL_TAG);
      }
      fab.show();
    }

    @Override
    public void onCallLogSelected() {
      LogUtil.enterBlock("MainBottomNavBarBottomNavTabListener.onCallLogSelected");
      if (selectedTab == TabIndex.CALL_LOG) {
        return;
      }
      Logger.get(activity).logScreenView(ScreenEvent.Type.MAIN_CALL_LOG, activity);
      selectedTab = TabIndex.CALL_LOG;

      if (CallLogConfigComponent.get(activity).callLogConfig().isNewCallLogFragmentEnabled()) {
        android.support.v4.app.Fragment supportFragment =
            supportFragmentManager.findFragmentByTag(CALL_LOG_TAG);
        showSupportFragment(
            supportFragment == null ? new NewCallLogFragment() : supportFragment, CALL_LOG_TAG);
      } else {
        Fragment fragment = fragmentManager.findFragmentByTag(CALL_LOG_TAG);
        showFragment(fragment == null ? new CallLogFragment() : fragment, CALL_LOG_TAG);
      }
      fab.show();
      showPromotionBottomSheet(activity, bottomSheet);
    }

    private static void showPromotionBottomSheet(Context context, View view) {
      BottomSheetBehavior<View> bottomSheetBehavior = BottomSheetBehavior.from(view);
      Optional<Promotion> promotionOptional =
          PromotionComponent.get(context)
              .promotionManager()
              .getHighestPriorityPromotion(PromotionType.BOTTOM_SHEET);
      if (!promotionOptional.isPresent()) {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        return;
      }

      Promotion promotion = promotionOptional.get();
      ImageView icon = view.findViewById(R.id.promotion_icon);
      icon.setImageResource(promotion.getIconRes());
      TextView details = view.findViewById(R.id.promotion_details);
      details.setText(promotion.getDetails());
      // Required to make link clickable.
      details.setMovementMethod(LinkMovementMethod.getInstance());
      TextView title = view.findViewById(R.id.promotion_title);
      title.setText(promotion.getTitle());
      view.findViewById(R.id.ok_got_it)
          .setOnClickListener(
              v -> {
                promotion.dismiss();
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
              });
      view.setVisibility(View.VISIBLE);
      bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    void disableNewCallLogFragment() {
      LogUtil.i("MainBottomNavBarBottomNavTabListener.disableNewCallLogFragment", "disabled");
      android.support.v4.app.Fragment supportFragment =
          supportFragmentManager.findFragmentByTag(CALL_LOG_TAG);
      if (supportFragment != null) {
        supportFragmentManager.beginTransaction().remove(supportFragment).commitAllowingStateLoss();
        // If the NewCallLogFragment was showing, immediately show the old call log fragment
        // instead.
        if (selectedTab == TabIndex.CALL_LOG) {
          LogUtil.i(
              "MainBottomNavBarBottomNavTabListener.disableNewCallLogFragment", "showing old");
          Fragment fragment = fragmentManager.findFragmentByTag(CALL_LOG_TAG);
          showFragment(fragment == null ? new CallLogFragment() : fragment, CALL_LOG_TAG);
        }
      }
    }

    void disableNewVoicemailFragment() {
      LogUtil.i("MainBottomNavBarBottomNavTabListener.disableNewVoicemailFragment", "disabled");
      android.support.v4.app.Fragment supportFragment =
          supportFragmentManager.findFragmentByTag(VOICEMAIL_TAG);
      if (supportFragment != null) {
        supportFragmentManager.beginTransaction().remove(supportFragment).commitAllowingStateLoss();
        // If the NewVoicemailFragment was showing, immediately show the old voicemail fragment
        // instead.
        if (selectedTab == TabIndex.VOICEMAIL) {
          LogUtil.i(
              "MainBottomNavBarBottomNavTabListener.disableNewVoicemailFragment", "showing old");
          Fragment fragment = fragmentManager.findFragmentByTag(VOICEMAIL_TAG);
          showFragment(
              fragment == null ? new VisualVoicemailCallLogFragment() : fragment, VOICEMAIL_TAG);
        }
      }
    }

    void ensureCorrectCallLogShown() {
      android.support.v4.app.Fragment supportFragment =
          supportFragmentManager.findFragmentByTag(CALL_LOG_TAG);
      if (supportFragment != null
          && !CallLogConfigComponent.get(activity).callLogConfig().isNewCallLogFragmentEnabled()) {
        LogUtil.i("MainBottomNavBarBottomNavTabListener.ensureCorrectCallLogShown", "disabling");
        disableNewCallLogFragment();
      }
    }

    void ensureCorrectVoicemailShown() {
      android.support.v4.app.Fragment supportFragment =
          supportFragmentManager.findFragmentByTag(VOICEMAIL_TAG);
      if (supportFragment != null
          && !CallLogConfigComponent.get(activity)
              .callLogConfig()
              .isNewVoicemailFragmentEnabled()) {
        LogUtil.i("MainBottomNavBarBottomNavTabListener.ensureCorrectVoicemailShown", "disabling");
        disableNewVoicemailFragment();
      }
    }

    boolean newCallLogFragmentActive() {
      return supportFragmentManager.findFragmentByTag(CALL_LOG_TAG) != null
          || (fragmentManager.findFragmentByTag(CALL_LOG_TAG) == null
              && CallLogConfigComponent.get(activity)
                  .callLogConfig()
                  .isNewCallLogFragmentEnabled());
    }

    boolean newVoicemailFragmentActive() {
      return supportFragmentManager.findFragmentByTag(VOICEMAIL_TAG) != null
          || (fragmentManager.findFragmentByTag(VOICEMAIL_TAG) == null
              && CallLogConfigComponent.get(activity)
                  .callLogConfig()
                  .isNewVoicemailFragmentEnabled());
    }

    @Override
    public void onContactsSelected() {
      LogUtil.enterBlock("MainBottomNavBarBottomNavTabListener.onContactsSelected");
      if (selectedTab == TabIndex.CONTACTS) {
        return;
      }
      Logger.get(activity).logScreenView(ScreenEvent.Type.MAIN_CONTACTS, activity);
      selectedTab = TabIndex.CONTACTS;
      Fragment fragment = fragmentManager.findFragmentByTag(CONTACTS_TAG);
      showFragment(
          fragment == null ? ContactsFragment.newInstance(Header.ADD_CONTACT) : fragment,
          CONTACTS_TAG);
      fab.show();
    }

    @Override
    public void onVoicemailSelected() {
      LogUtil.enterBlock("MainBottomNavBarBottomNavTabListener.onVoicemailSelected");
      if (selectedTab == TabIndex.VOICEMAIL) {
        return;
      }
      Logger.get(activity).logScreenView(ScreenEvent.Type.MAIN_VOICEMAIL, activity);
      selectedTab = TabIndex.VOICEMAIL;

      if (CallLogConfigComponent.get(activity).callLogConfig().isNewVoicemailFragmentEnabled()) {
        android.support.v4.app.Fragment supportFragment =
            supportFragmentManager.findFragmentByTag(VOICEMAIL_TAG);
        showSupportFragment(
            supportFragment == null ? new NewVoicemailFragment() : supportFragment, VOICEMAIL_TAG);
      } else {
        VisualVoicemailCallLogFragment fragment =
            (VisualVoicemailCallLogFragment) fragmentManager.findFragmentByTag(VOICEMAIL_TAG);
        if (fragment == null) {
          fragment = new VisualVoicemailCallLogFragment();
        }
        showFragment(fragment, VOICEMAIL_TAG);
        fragment.setUserVisibleHint(true);
        fragment.onVisible();
      }
    }

    private void showFragment(@NonNull Fragment fragment, String tag) {
      showFragment(fragment, null, tag);
    }

    /**
     * Shows the passed in fragment and hides all of the others in one transaction.
     *
     * <p>Exactly one of fragment or supportFragment should be provided.
     *
     * <p>Executes all fragment shows/hides in one transaction with no conflicting transactions
     * (like showing and hiding the same fragment in the same transaction). See a bug.
     *
     * <p>Special care should be taken to avoid calling this method several times in a short window
     * as it can lead to fragments overlapping.
     */
    private void showFragment(
        @Nullable Fragment fragment,
        @Nullable android.support.v4.app.Fragment supportFragment,
        String tag) {
      LogUtil.enterBlock("MainBottomNavBarBottomNavTabListener.showFragment");
      Fragment oldSpeedDial = fragmentManager.findFragmentByTag(SPEED_DIAL_TAG);
      Fragment oldCallLog = fragmentManager.findFragmentByTag(CALL_LOG_TAG);
      Fragment contacts = fragmentManager.findFragmentByTag(CONTACTS_TAG);
      Fragment oldVoicemail = fragmentManager.findFragmentByTag(VOICEMAIL_TAG);

      FragmentTransaction transaction = fragmentManager.beginTransaction();
      boolean fragmentShown = showIfEqualElseHide(transaction, fragment, oldSpeedDial);
      fragmentShown |= showIfEqualElseHide(transaction, fragment, oldCallLog);
      fragmentShown |= showIfEqualElseHide(transaction, fragment, contacts);
      fragmentShown |= showIfEqualElseHide(transaction, fragment, oldVoicemail);

      if (!fragmentShown && fragment != null) {
        LogUtil.i(
            "MainBottomNavBarBottomNavTabListener.showFragment", "Not added yet: " + fragment);
        transaction.add(R.id.fragment_container, fragment, tag);
      }
      if (activity.isSafeToCommitTransactions()) {
        transaction.commit();
      }

      // Handle support fragments.
      // TODO(calderwoodra): Handle other new fragments.
      android.support.v4.app.Fragment speedDial =
          supportFragmentManager.findFragmentByTag(SPEED_DIAL_TAG);
      android.support.v4.app.Fragment newCallLog =
          supportFragmentManager.findFragmentByTag(CALL_LOG_TAG);
      android.support.v4.app.Fragment newVoicemail =
          supportFragmentManager.findFragmentByTag(VOICEMAIL_TAG);

      android.support.v4.app.FragmentTransaction supportTransaction =
          supportFragmentManager.beginTransaction();
      boolean supportFragmentShown =
          showIfEqualElseHideSupport(supportTransaction, supportFragment, speedDial);
      supportFragmentShown |=
          showIfEqualElseHideSupport(supportTransaction, supportFragment, newCallLog);
      supportFragmentShown |=
          showIfEqualElseHideSupport(supportTransaction, supportFragment, newVoicemail);

      if (!supportFragmentShown && supportFragment != null) {
        LogUtil.i(
            "MainBottomNavBarBottomNavTabListener.showFragment",
            "Not added yet: " + supportFragment);
        supportTransaction.add(R.id.fragment_container, supportFragment, tag);
      }
      if (activity.isSafeToCommitTransactions()) {
        supportTransaction.commit();
      }
    }

    private void showSupportFragment(
        @NonNull android.support.v4.app.Fragment supportFragment, String tag) {
      showFragment(null, supportFragment, tag);
    }

    /**
     * @param fragment1 will be shown if equal to {@code fragment2}
     * @param fragment2 will be hidden if unequal to {@code fragment1}
     * @return {@code true} if {@code fragment1} was shown
     */
    private boolean showIfEqualElseHide(
        FragmentTransaction transaction, Fragment fragment1, Fragment fragment2) {
      boolean shown = false;
      if (fragment1 != null && fragment1.equals(fragment2)) {
        transaction.show(fragment1);
        shown = true;
      } else if (fragment2 != null) {
        if (fragment2 instanceof VisualVoicemailCallLogFragment) {
          fragment2.setUserVisibleHint(false);
          ((VisualVoicemailCallLogFragment) fragment2).onNotVisible();
        }
        transaction.hide(fragment2);
      }
      return shown;
    }

    /**
     * @param supportFragment1 will be shown if equal to {@code fragment2}
     * @param supportFragment2 will be hidden if unequal to {@code fragment1}
     * @return {@code true} if {@code fragment1} was shown
     */
    private boolean showIfEqualElseHideSupport(
        android.support.v4.app.FragmentTransaction supportTransaction,
        android.support.v4.app.Fragment supportFragment1,
        android.support.v4.app.Fragment supportFragment2) {
      boolean shown = false;
      if (supportFragment1 != null && supportFragment1.equals(supportFragment2)) {
        supportTransaction.show(supportFragment1);
        shown = true;
      } else if (supportFragment2 != null) {
        supportTransaction.hide(supportFragment2);
      }
      return shown;
    }
  }

  private static final class LastTabController {

    private final Context context;
    private final BottomNavBar bottomNavBar;
    private final boolean canShowVoicemailTab;

    LastTabController(Context context, BottomNavBar bottomNavBar, boolean canShowVoicemailTab) {
      this.context = context;
      this.bottomNavBar = bottomNavBar;
      this.canShowVoicemailTab = canShowVoicemailTab;
    }

    /**
     * Get the last tab shown to the user, or the speed dial tab if this is the first time the user
     * has opened the app.
     */
    @TabIndex
    int getLastTab() {
      @TabIndex int tabIndex = TabIndex.SPEED_DIAL;

      tabIndex =
          StorageComponent.get(context)
              .unencryptedSharedPrefs()
              .getInt(KEY_LAST_TAB, TabIndex.SPEED_DIAL);

      // If the voicemail tab cannot be shown, default to showing speed dial
      if (tabIndex == TabIndex.VOICEMAIL && !canShowVoicemailTab) {
        tabIndex = TabIndex.SPEED_DIAL;
      }

      return tabIndex;
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
