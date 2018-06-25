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
 * limitations under the License
 */

package com.android.dialer.app;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Trace;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.QuickContact;
import android.speech.RecognizerIntent;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.FloatingActionButton.OnVisibilityChangedListener;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.telecom.PhoneAccount;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnDragListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView.OnScrollListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import com.android.contacts.common.dialog.ClearFrequentsDialog;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.common.list.PhoneNumberListAdapter;
import com.android.contacts.common.list.PhoneNumberPickerFragment.CursorReranker;
import com.android.contacts.common.list.PhoneNumberPickerFragment.OnLoadFinishedListener;
import com.android.dialer.animation.AnimUtils;
import com.android.dialer.animation.AnimationListenerAdapter;
import com.android.dialer.app.calllog.CallLogActivity;
import com.android.dialer.app.calllog.CallLogAdapter;
import com.android.dialer.app.calllog.CallLogFragment;
import com.android.dialer.app.calllog.CallLogNotificationsService;
import com.android.dialer.app.calllog.IntentProvider;
import com.android.dialer.app.list.DialtactsPagerAdapter;
import com.android.dialer.app.list.DialtactsPagerAdapter.TabIndex;
import com.android.dialer.app.list.DragDropController;
import com.android.dialer.app.list.ListsFragment;
import com.android.dialer.app.list.OldSpeedDialFragment;
import com.android.dialer.app.list.OnDragDropListener;
import com.android.dialer.app.list.OnListFragmentScrolledListener;
import com.android.dialer.app.list.PhoneFavoriteSquareTileView;
import com.android.dialer.app.list.RegularSearchFragment;
import com.android.dialer.app.list.SearchFragment;
import com.android.dialer.app.list.SmartDialSearchFragment;
import com.android.dialer.app.settings.DialerSettingsActivity;
import com.android.dialer.app.widget.ActionBarController;
import com.android.dialer.app.widget.SearchEditTextLayout;
import com.android.dialer.callcomposer.CallComposerActivity;
import com.android.dialer.calldetails.CallDetailsActivity;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.callintent.CallSpecificAppData;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.UiUtil;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.compat.CompatUtils;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.constants.ActivityRequestCodes;
import com.android.dialer.contactsfragment.ContactsFragment;
import com.android.dialer.contactsfragment.ContactsFragment.OnContactSelectedListener;
import com.android.dialer.database.Database;
import com.android.dialer.database.DialerDatabaseHelper;
import com.android.dialer.dialpadview.DialpadFragment;
import com.android.dialer.dialpadview.DialpadFragment.DialpadListener;
import com.android.dialer.dialpadview.DialpadFragment.LastOutgoingCallCallback;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.interactions.PhoneNumberInteraction;
import com.android.dialer.interactions.PhoneNumberInteraction.InteractionErrorCode;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.InteractionEvent;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ScreenEvent;
import com.android.dialer.logging.UiAction;
import com.android.dialer.metrics.Metrics;
import com.android.dialer.metrics.MetricsComponent;
import com.android.dialer.p13n.inference.P13nRanking;
import com.android.dialer.p13n.inference.protocol.P13nRanker;
import com.android.dialer.p13n.inference.protocol.P13nRanker.P13nRefreshCompleteListener;
import com.android.dialer.p13n.logging.P13nLogger;
import com.android.dialer.p13n.logging.P13nLogging;
import com.android.dialer.performancereport.PerformanceReport;
import com.android.dialer.postcall.PostCall;
import com.android.dialer.precall.PreCall;
import com.android.dialer.proguard.UsedByReflection;
import com.android.dialer.searchfragment.list.NewSearchFragment;
import com.android.dialer.searchfragment.list.NewSearchFragment.SearchFragmentListener;
import com.android.dialer.simulator.Simulator;
import com.android.dialer.simulator.SimulatorComponent;
import com.android.dialer.smartdial.util.SmartDialNameMatcher;
import com.android.dialer.smartdial.util.SmartDialPrefix;
import com.android.dialer.storage.StorageComponent;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.util.TouchPointManager;
import com.android.dialer.util.TransactionSafeActivity;
import com.android.dialer.util.ViewUtil;
import com.android.dialer.widget.FloatingActionButtonController;
import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** The dialer tab's title is 'phone', a more common name (see strings.xml). */
@UsedByReflection(value = "AndroidManifest-app.xml")
public class DialtactsActivity extends TransactionSafeActivity
    implements View.OnClickListener,
        DialpadFragment.OnDialpadQueryChangedListener,
        OnListFragmentScrolledListener,
        CallLogFragment.HostInterface,
        CallLogAdapter.OnActionModeStateChangedListener,
        ContactsFragment.OnContactsListScrolledListener,
        DialpadFragment.HostInterface,
        OldSpeedDialFragment.HostInterface,
        SearchFragment.HostInterface,
        OnDragDropListener,
        OnPhoneNumberPickerActionListener,
        PopupMenu.OnMenuItemClickListener,
        ViewPager.OnPageChangeListener,
        ActionBarController.ActivityUi,
        PhoneNumberInteraction.InteractionErrorListener,
        PhoneNumberInteraction.DisambigDialogDismissedListener,
        ActivityCompat.OnRequestPermissionsResultCallback,
        DialpadListener,
        SearchFragmentListener,
        OnContactSelectedListener {

  public static final boolean DEBUG = false;
  @VisibleForTesting public static final String TAG_DIALPAD_FRAGMENT = "dialpad";
  private static final String ACTION_SHOW_TAB = "ACTION_SHOW_TAB";
  @VisibleForTesting public static final String EXTRA_SHOW_TAB = "EXTRA_SHOW_TAB";
  public static final String EXTRA_CLEAR_NEW_VOICEMAILS = "EXTRA_CLEAR_NEW_VOICEMAILS";
  private static final String KEY_LAST_TAB = "last_tab";
  private static final String TAG = "DialtactsActivity";
  private static final String KEY_IN_REGULAR_SEARCH_UI = "in_regular_search_ui";
  private static final String KEY_IN_DIALPAD_SEARCH_UI = "in_dialpad_search_ui";
  private static final String KEY_IN_NEW_SEARCH_UI = "in_new_search_ui";
  private static final String KEY_SEARCH_QUERY = "search_query";
  private static final String KEY_DIALPAD_QUERY = "dialpad_query";
  private static final String KEY_FIRST_LAUNCH = "first_launch";
  private static final String KEY_SAVED_LANGUAGE_CODE = "saved_language_code";
  private static final String KEY_WAS_CONFIGURATION_CHANGE = "was_configuration_change";
  private static final String KEY_IS_DIALPAD_SHOWN = "is_dialpad_shown";
  private static final String KEY_FAB_VISIBLE = "fab_visible";
  private static final String TAG_NEW_SEARCH_FRAGMENT = "new_search";
  private static final String TAG_REGULAR_SEARCH_FRAGMENT = "search";
  private static final String TAG_SMARTDIAL_SEARCH_FRAGMENT = "smartdial";
  private static final String TAG_FAVORITES_FRAGMENT = "favorites";
  /** Just for backward compatibility. Should behave as same as {@link Intent#ACTION_DIAL}. */
  private static final String ACTION_TOUCH_DIALER = "com.android.phone.action.TOUCH_DIALER";

  private static final int FAB_SCALE_IN_DELAY_MS = 300;

  /**
   * Minimum time the history tab must have been selected for it to be marked as seen in onStop()
   */
  private static final long HISTORY_TAB_SEEN_TIMEOUT = TimeUnit.SECONDS.toMillis(3);

  private static Optional<Boolean> voiceSearchEnabledForTest = Optional.absent();

  /** Fragment containing the dialpad that slides into view */
  protected DialpadFragment dialpadFragment;

  /** Root layout of DialtactsActivity */
  private CoordinatorLayout parentLayout;
  /** Fragment for searching phone numbers using the alphanumeric keyboard. */
  private RegularSearchFragment regularSearchFragment;

  /** Fragment for searching phone numbers using the dialpad. */
  private SmartDialSearchFragment smartDialSearchFragment;

  /** new Fragment for search phone numbers using the keyboard and the dialpad. */
  private NewSearchFragment newSearchFragment;

  /** Animation that slides in. */
  private Animation slideIn;

  /** Animation that slides out. */
  private Animation slideOut;
  /** Fragment containing the speed dial list, call history list, and all contacts list. */
  private ListsFragment listsFragment;
  /**
   * Tracks whether onSaveInstanceState has been called. If true, no fragment transactions can be
   * commited.
   */
  private boolean stateSaved;

  private boolean isKeyboardOpen;
  private boolean inNewSearch;
  private boolean isRestarting;
  private boolean inDialpadSearch;
  private boolean inRegularSearch;
  private boolean clearSearchOnPause;
  private boolean isDialpadShown;
  /** Whether or not the device is in landscape orientation. */
  private boolean isLandscape;
  /** True if the dialpad is only temporarily showing due to being in call */
  private boolean inCallDialpadUp;
  /** True when this activity has been launched for the first time. */
  private boolean firstLaunch;
  /**
   * Search query to be applied to the SearchView in the ActionBar once onCreateOptionsMenu has been
   * called.
   */
  private String pendingSearchViewQuery;

  private PopupMenu overflowMenu;
  private EditText searchView;
  private SearchEditTextLayout searchEditTextLayout;
  private View voiceSearchButton;
  private String searchQuery;
  private String dialpadQuery;
  private DialerDatabaseHelper dialerDatabaseHelper;
  private DragDropController dragDropController;
  private ActionBarController actionBarController;
  private FloatingActionButtonController floatingActionButtonController;
  private String savedLanguageCode;
  private boolean wasConfigurationChange;
  private long timeTabSelected;

  private P13nLogger p13nLogger;
  private P13nRanker p13nRanker;
  public boolean isMultiSelectModeEnabled;

  private boolean isLastTabEnabled;

  AnimationListenerAdapter slideInListener =
      new AnimationListenerAdapter() {
        @Override
        public void onAnimationEnd(Animation animation) {
          maybeEnterSearchUi();
        }
      };
  /** Listener for after slide out animation completes on dialer fragment. */
  AnimationListenerAdapter slideOutListener =
      new AnimationListenerAdapter() {
        @Override
        public void onAnimationEnd(Animation animation) {
          commitDialpadFragmentHide();
        }
      };
  /** Listener used to send search queries to the phone search fragment. */
  private final TextWatcher phoneSearchQueryTextListener =
      new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
          final String newText = s.toString();
          if (newText.equals(searchQuery)) {
            // If the query hasn't changed (perhaps due to activity being destroyed
            // and restored, or user launching the same DIAL intent twice), then there is
            // no need to do anything here.
            return;
          }

          if (count != 0) {
            PerformanceReport.recordClick(UiAction.Type.TEXT_CHANGE_WITH_INPUT);
          }

          LogUtil.v("DialtactsActivity.onTextChanged", "called with new query: " + newText);
          LogUtil.v("DialtactsActivity.onTextChanged", "previous query: " + searchQuery);
          searchQuery = newText;

          // TODO(calderwoodra): show p13n when newText is empty.
          // Show search fragment only when the query string is changed to non-empty text.
          if (!TextUtils.isEmpty(newText)) {
            // Call enterSearchUi only if we are switching search modes, or showing a search
            // fragment for the first time.
            final boolean sameSearchMode =
                (isDialpadShown && inDialpadSearch) || (!isDialpadShown && inRegularSearch);
            if (!sameSearchMode) {
              enterSearchUi(isDialpadShown, searchQuery, true /* animate */);
            }
          }

          if (smartDialSearchFragment != null && smartDialSearchFragment.isVisible()) {
            smartDialSearchFragment.setQueryString(searchQuery);
          } else if (regularSearchFragment != null && regularSearchFragment.isVisible()) {
            regularSearchFragment.setQueryString(searchQuery);
          } else if (newSearchFragment != null && newSearchFragment.isVisible()) {
            newSearchFragment.setQuery(searchQuery, getCallInitiationType());
          }
        }

        @Override
        public void afterTextChanged(Editable s) {}
      };
  /** Open the search UI when the user clicks on the search box. */
  private final View.OnClickListener searchViewOnClickListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          if (!isInSearchUi()) {
            PerformanceReport.recordClick(UiAction.Type.OPEN_SEARCH);
            actionBarController.onSearchBoxTapped();
            enterSearchUi(
                false /* smartDialSearch */, searchView.getText().toString(), true /* animate */);
          }
        }
      };

  private int actionBarHeight;
  private int previouslySelectedTabIndex;

  /**
   * The text returned from a voice search query. Set in {@link #onActivityResult} and used in
   * {@link #onResume()} to populate the search box.
   */
  private String voiceSearchQuery;

  /**
   * @param tab the TAB_INDEX_* constant in {@link ListsFragment}
   * @return A intent that will open the DialtactsActivity into the specified tab. The intent for
   *     each tab will be unique.
   */
  public static Intent getShowTabIntent(Context context, int tab) {
    Intent intent = new Intent(context, DialtactsActivity.class);
    intent.setAction(ACTION_SHOW_TAB);
    intent.putExtra(DialtactsActivity.EXTRA_SHOW_TAB, tab);
    intent.setData(
        new Uri.Builder()
            .scheme("intent")
            .authority(context.getPackageName())
            .appendPath(TAG)
            .appendQueryParameter(DialtactsActivity.EXTRA_SHOW_TAB, String.valueOf(tab))
            .build());

    return intent;
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent ev) {
    if (ev.getAction() == MotionEvent.ACTION_DOWN) {
      TouchPointManager.getInstance().setPoint((int) ev.getRawX(), (int) ev.getRawY());
    }
    return super.dispatchTouchEvent(ev);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Trace.beginSection(TAG + " onCreate");
    LogUtil.enterBlock("DialtactsActivity.onCreate");
    super.onCreate(savedInstanceState);

    firstLaunch = true;
    isLastTabEnabled = ConfigProviderBindings.get(this).getBoolean("last_tab_enabled", false);

    final Resources resources = getResources();
    actionBarHeight = resources.getDimensionPixelSize(R.dimen.action_bar_height_large);

    Trace.beginSection(TAG + " setContentView");
    setContentView(R.layout.dialtacts_activity);
    Trace.endSection();
    getWindow().setBackgroundDrawable(null);

    Trace.beginSection(TAG + " setup Views");
    final ActionBar actionBar = getActionBarSafely();
    actionBar.setCustomView(R.layout.search_edittext);
    actionBar.setDisplayShowCustomEnabled(true);
    actionBar.setBackgroundDrawable(null);

    searchEditTextLayout = actionBar.getCustomView().findViewById(R.id.search_view_container);

    actionBarController = new ActionBarController(this, searchEditTextLayout);

    searchView = searchEditTextLayout.findViewById(R.id.search_view);
    searchView.addTextChangedListener(phoneSearchQueryTextListener);
    searchView.setHint(getSearchBoxHint());

    voiceSearchButton = searchEditTextLayout.findViewById(R.id.voice_search_button);
    searchEditTextLayout
        .findViewById(R.id.search_box_collapsed)
        .setOnClickListener(searchViewOnClickListener);
    searchEditTextLayout
        .findViewById(R.id.search_back_button)
        .setOnClickListener(v -> exitSearchUi());

    isLandscape =
        getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    previouslySelectedTabIndex = DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL;
    FloatingActionButton floatingActionButton = findViewById(R.id.floating_action_button);
    floatingActionButton.setOnClickListener(this);
    floatingActionButtonController = new FloatingActionButtonController(this, floatingActionButton);

    ImageButton optionsMenuButton =
        searchEditTextLayout.findViewById(R.id.dialtacts_options_menu_button);
    optionsMenuButton.setOnClickListener(this);
    overflowMenu = buildOptionsMenu(optionsMenuButton);
    optionsMenuButton.setOnTouchListener(overflowMenu.getDragToOpenListener());

    // Add the favorites fragment but only if savedInstanceState is null. Otherwise the
    // fragment manager is responsible for recreating it.
    if (savedInstanceState == null) {
      getFragmentManager()
          .beginTransaction()
          .add(R.id.dialtacts_frame, new ListsFragment(), TAG_FAVORITES_FRAGMENT)
          .commit();
    } else {
      searchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY);
      dialpadQuery = savedInstanceState.getString(KEY_DIALPAD_QUERY);
      inRegularSearch = savedInstanceState.getBoolean(KEY_IN_REGULAR_SEARCH_UI);
      inDialpadSearch = savedInstanceState.getBoolean(KEY_IN_DIALPAD_SEARCH_UI);
      inNewSearch = savedInstanceState.getBoolean(KEY_IN_NEW_SEARCH_UI);
      firstLaunch = savedInstanceState.getBoolean(KEY_FIRST_LAUNCH);
      savedLanguageCode = savedInstanceState.getString(KEY_SAVED_LANGUAGE_CODE);
      wasConfigurationChange = savedInstanceState.getBoolean(KEY_WAS_CONFIGURATION_CHANGE);
      isDialpadShown = savedInstanceState.getBoolean(KEY_IS_DIALPAD_SHOWN);
      floatingActionButtonController.setVisible(savedInstanceState.getBoolean(KEY_FAB_VISIBLE));
      actionBarController.restoreInstanceState(savedInstanceState);
    }

    final boolean isLayoutRtl = ViewUtil.isRtl();
    if (isLandscape) {
      slideIn =
          AnimationUtils.loadAnimation(
              this, isLayoutRtl ? R.anim.dialpad_slide_in_left : R.anim.dialpad_slide_in_right);
      slideOut =
          AnimationUtils.loadAnimation(
              this, isLayoutRtl ? R.anim.dialpad_slide_out_left : R.anim.dialpad_slide_out_right);
    } else {
      slideIn = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_in_bottom);
      slideOut = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_out_bottom);
    }

    slideIn.setInterpolator(AnimUtils.EASE_IN);
    slideOut.setInterpolator(AnimUtils.EASE_OUT);

    slideIn.setAnimationListener(slideInListener);
    slideOut.setAnimationListener(slideOutListener);

    parentLayout = (CoordinatorLayout) findViewById(R.id.dialtacts_mainlayout);
    parentLayout.setOnDragListener(new LayoutOnDragListener());
    ViewUtil.doOnGlobalLayout(
        floatingActionButton,
        view -> {
          int screenWidth = parentLayout.getWidth();
          floatingActionButtonController.setScreenWidth(screenWidth);
          floatingActionButtonController.align(getFabAlignment(), false /* animate */);
        });

    Trace.endSection();

    Trace.beginSection(TAG + " initialize smart dialing");
    dialerDatabaseHelper = Database.get(this).getDatabaseHelper(this);
    SmartDialPrefix.initializeNanpSettings(this);
    Trace.endSection();

    p13nLogger = P13nLogging.get(getApplicationContext());
    p13nRanker = P13nRanking.get(getApplicationContext());
    Trace.endSection();

    // Update the new search fragment to the correct position and the ActionBar's visibility.
    if (ConfigProviderBindings.get(this).getBoolean("enable_new_search_fragment", false)) {
      updateSearchFragmentPosition();
    }
  }

  @NonNull
  private ActionBar getActionBarSafely() {
    return Assert.isNotNull(getSupportActionBar());
  }

  @Override
  protected void onResume() {
    LogUtil.enterBlock("DialtactsActivity.onResume");
    Trace.beginSection(TAG + " onResume");
    super.onResume();

    // Some calls may not be recorded (eg. from quick contact),
    // so we should restart recording after these calls. (Recorded call is stopped)
    PostCall.restartPerformanceRecordingIfARecentCallExist(this);
    if (!PerformanceReport.isRecording()) {
      PerformanceReport.startRecording();
    }

    stateSaved = false;
    if (firstLaunch) {
      LogUtil.i("DialtactsActivity.onResume", "mFirstLaunch true, displaying fragment");
      displayFragment(getIntent());
    } else if (!phoneIsInUse() && inCallDialpadUp) {
      LogUtil.i("DialtactsActivity.onResume", "phone not in use, hiding dialpad fragment");
      hideDialpadFragment(false, true);
      inCallDialpadUp = false;
    } else if (isDialpadShown) {
      LogUtil.i("DialtactsActivity.onResume", "showing dialpad on resume");
      showDialpadFragment(false);
    } else {
      PostCall.promptUserForMessageIfNecessary(this, parentLayout);
    }

    // On M the fragment manager does not restore the hidden state of a fragment from
    // savedInstanceState so it must be hidden again.
    if (!isDialpadShown && dialpadFragment != null && !dialpadFragment.isHidden()) {
      LogUtil.i(
          "DialtactsActivity.onResume", "mDialpadFragment attached but not hidden, forcing hide");
      getFragmentManager().beginTransaction().hide(dialpadFragment).commit();
    }

    // If there was a voice query result returned in the {@link #onActivityResult} callback, it
    // will have been stashed in mVoiceSearchQuery since the search results fragment cannot be
    // shown until onResume has completed.  Active the search UI and set the search term now.
    if (!TextUtils.isEmpty(voiceSearchQuery)) {
      actionBarController.onSearchBoxTapped();
      searchView.setText(voiceSearchQuery);
      voiceSearchQuery = null;
    }

    if (isRestarting) {
      // This is only called when the activity goes from resumed -> paused -> resumed, so it
      // will not cause an extra view to be sent out on rotation
      if (isDialpadShown) {
        Logger.get(this).logScreenView(ScreenEvent.Type.DIALPAD, this);
      }
      isRestarting = false;
    }

    prepareVoiceSearchButton();

    // Start the thread that updates the smart dial database if
    // (1) the activity is not recreated with a new configuration, or
    // (2) the activity is recreated with a new configuration but the change is a language change.
    boolean isLanguageChanged =
        !CompatUtils.getLocale(this).getISO3Language().equals(savedLanguageCode);
    if (!wasConfigurationChange || isLanguageChanged) {
      dialerDatabaseHelper.startSmartDialUpdateThread(/* forceUpdate = */ isLanguageChanged);
    }

    if (isDialpadShown) {
      floatingActionButtonController.scaleOut();
    } else {
      floatingActionButtonController.align(getFabAlignment(), false /* animate */);
    }

    if (firstLaunch) {
      // Only process the Intent the first time onResume() is called after receiving it
      if (Calls.CONTENT_TYPE.equals(getIntent().getType())) {
        // Externally specified extras take precedence to EXTRA_SHOW_TAB, which is only
        // used internally.
        final Bundle extras = getIntent().getExtras();
        if (extras != null && extras.getInt(Calls.EXTRA_CALL_TYPE_FILTER) == Calls.VOICEMAIL_TYPE) {
          listsFragment.showTab(DialtactsPagerAdapter.TAB_INDEX_VOICEMAIL);
          Logger.get(this).logImpression(DialerImpression.Type.VVM_NOTIFICATION_CLICKED);
        } else {
          listsFragment.showTab(DialtactsPagerAdapter.TAB_INDEX_HISTORY);
        }
      } else if (getIntent().hasExtra(EXTRA_SHOW_TAB)) {
        int index =
            getIntent().getIntExtra(EXTRA_SHOW_TAB, DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL);
        if (index < listsFragment.getTabCount()) {
          // Hide dialpad since this is an explicit intent to show a specific tab, which is coming
          // from missed call or voicemail notification.
          hideDialpadFragment(false, false);
          exitSearchUi();
          listsFragment.showTab(index);
        }
      }

      if (getIntent().getBooleanExtra(EXTRA_CLEAR_NEW_VOICEMAILS, false)) {
        LogUtil.i("DialtactsActivity.onResume", "clearing all new voicemails");
        CallLogNotificationsService.markAllNewVoicemailsAsOld(this);
      }
      // add 1 sec delay to get memory snapshot so that dialer wont react slowly on resume.
      ThreadUtil.postDelayedOnUiThread(
          () ->
              MetricsComponent.get(this)
                  .metrics()
                  .recordMemory(Metrics.DIALTACTS_ON_RESUME_MEMORY_EVENT_NAME),
          1000);
    }

    firstLaunch = false;

    setSearchBoxHint();
    timeTabSelected = SystemClock.elapsedRealtime();

    p13nLogger.reset();
    p13nRanker.refresh(
        new P13nRefreshCompleteListener() {
          @Override
          public void onP13nRefreshComplete() {
            // TODO(strongarm): make zero-query search results visible
          }
        });
    Trace.endSection();
  }

  @Override
  protected void onRestart() {
    super.onRestart();
    isRestarting = true;
  }

  @Override
  protected void onPause() {
    if (clearSearchOnPause) {
      hideDialpadAndSearchUi();
      clearSearchOnPause = false;
    }
    if (slideOut.hasStarted() && !slideOut.hasEnded()) {
      commitDialpadFragmentHide();
    }
    super.onPause();
  }

  @Override
  protected void onStop() {
    super.onStop();
    boolean timeoutElapsed =
        SystemClock.elapsedRealtime() - timeTabSelected >= HISTORY_TAB_SEEN_TIMEOUT;
    boolean isOnHistoryTab =
        listsFragment.getCurrentTabIndex() == DialtactsPagerAdapter.TAB_INDEX_HISTORY;
    if (isOnHistoryTab
        && timeoutElapsed
        && !isChangingConfigurations()
        && !getSystemService(KeyguardManager.class).isKeyguardLocked()) {
      listsFragment.markMissedCallsAsReadAndRemoveNotifications();
    }
    StorageComponent.get(this)
        .unencryptedSharedPrefs()
        .edit()
        .putInt(KEY_LAST_TAB, listsFragment.getCurrentTabIndex())
        .apply();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    LogUtil.enterBlock("DialtactsActivity.onSaveInstanceState");
    super.onSaveInstanceState(outState);
    outState.putString(KEY_SEARCH_QUERY, searchQuery);
    outState.putString(KEY_DIALPAD_QUERY, dialpadQuery);
    outState.putString(KEY_SAVED_LANGUAGE_CODE, CompatUtils.getLocale(this).getISO3Language());
    outState.putBoolean(KEY_IN_REGULAR_SEARCH_UI, inRegularSearch);
    outState.putBoolean(KEY_IN_DIALPAD_SEARCH_UI, inDialpadSearch);
    outState.putBoolean(KEY_IN_NEW_SEARCH_UI, inNewSearch);
    outState.putBoolean(KEY_FIRST_LAUNCH, firstLaunch);
    outState.putBoolean(KEY_IS_DIALPAD_SHOWN, isDialpadShown);
    outState.putBoolean(KEY_FAB_VISIBLE, floatingActionButtonController.isVisible());
    outState.putBoolean(KEY_WAS_CONFIGURATION_CHANGE, isChangingConfigurations());
    actionBarController.saveInstanceState(outState);
    stateSaved = true;
  }

  @Override
  public void onAttachFragment(final Fragment fragment) {
    LogUtil.i("DialtactsActivity.onAttachFragment", "fragment: %s", fragment);
    if (fragment instanceof DialpadFragment) {
      dialpadFragment = (DialpadFragment) fragment;
    } else if (fragment instanceof SmartDialSearchFragment) {
      smartDialSearchFragment = (SmartDialSearchFragment) fragment;
      smartDialSearchFragment.setOnPhoneNumberPickerActionListener(this);
      if (!TextUtils.isEmpty(dialpadQuery)) {
        smartDialSearchFragment.setAddToContactNumber(dialpadQuery);
      }
    } else if (fragment instanceof SearchFragment) {
      regularSearchFragment = (RegularSearchFragment) fragment;
      regularSearchFragment.setOnPhoneNumberPickerActionListener(this);
    } else if (fragment instanceof ListsFragment) {
      listsFragment = (ListsFragment) fragment;
      listsFragment.addOnPageChangeListener(this);
    } else if (fragment instanceof NewSearchFragment) {
      newSearchFragment = (NewSearchFragment) fragment;
      updateSearchFragmentPosition();
    }
    if (fragment instanceof SearchFragment) {
      final SearchFragment searchFragment = (SearchFragment) fragment;
      searchFragment.setReranker(
          new CursorReranker() {
            @Override
            @MainThread
            public Cursor rerankCursor(Cursor data) {
              Assert.isMainThread();
              String queryString = searchFragment.getQueryString();
              return p13nRanker.rankCursor(data, queryString == null ? 0 : queryString.length());
            }
          });
      searchFragment.addOnLoadFinishedListener(
          new OnLoadFinishedListener() {
            @Override
            public void onLoadFinished() {
              p13nLogger.onSearchQuery(
                  searchFragment.getQueryString(),
                  (PhoneNumberListAdapter) searchFragment.getAdapter());
            }
          });
    }
  }

  protected void handleMenuSettings() {
    final Intent intent = new Intent(this, DialerSettingsActivity.class);
    startActivity(intent);
  }

  public boolean isListsFragmentVisible() {
    return listsFragment.getUserVisibleHint();
  }

  @Override
  public void onClick(View view) {
    int resId = view.getId();
    if (resId == R.id.floating_action_button) {
      if (!isDialpadShown) {
        LogUtil.i(
            "DialtactsActivity.onClick", "floating action button clicked, going to show dialpad");
        PerformanceReport.recordClick(UiAction.Type.OPEN_DIALPAD);
        inCallDialpadUp = false;
        showDialpadFragment(true);
        PostCall.closePrompt();
      } else {
        LogUtil.i(
            "DialtactsActivity.onClick",
            "floating action button clicked, but dialpad is already showing");
      }
    } else if (resId == R.id.voice_search_button) {
      try {
        startActivityForResult(
            new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
            ActivityRequestCodes.DIALTACTS_VOICE_SEARCH);
      } catch (ActivityNotFoundException e) {
        Toast.makeText(
                DialtactsActivity.this, R.string.voice_search_not_available, Toast.LENGTH_SHORT)
            .show();
      }
    } else if (resId == R.id.dialtacts_options_menu_button) {
      overflowMenu.show();
    } else {
      Assert.fail("Unexpected onClick event from " + view);
    }
  }

  @Override
  public boolean onMenuItemClick(MenuItem item) {
    if (!isSafeToCommitTransactions()) {
      return true;
    }

    int resId = item.getItemId();
    if (resId == R.id.menu_history) {
      PerformanceReport.recordClick(UiAction.Type.OPEN_CALL_HISTORY);
      final Intent intent = new Intent(this, CallLogActivity.class);
      startActivity(intent);
    } else if (resId == R.id.menu_clear_frequents) {
      ClearFrequentsDialog.show(getFragmentManager());
      Logger.get(this).logScreenView(ScreenEvent.Type.CLEAR_FREQUENTS, this);
      return true;
    } else if (resId == R.id.menu_call_settings) {
      handleMenuSettings();
      Logger.get(this).logScreenView(ScreenEvent.Type.SETTINGS, this);
      return true;
    } else if (resId == R.id.menu_new_ui_launcher_shortcut) {
      MainComponent.createNewUiLauncherShortcut(this);
      return true;
    }
    return false;
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    LogUtil.i(
        "DialtactsActivity.onActivityResult",
        "requestCode:%d, resultCode:%d",
        requestCode,
        resultCode);
    if (requestCode == ActivityRequestCodes.DIALTACTS_VOICE_SEARCH) {
      if (resultCode == RESULT_OK) {
        final ArrayList<String> matches =
            data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (matches.size() > 0) {
          voiceSearchQuery = matches.get(0);
        } else {
          LogUtil.i("DialtactsActivity.onActivityResult", "voice search - nothing heard");
        }
      } else {
        LogUtil.e("DialtactsActivity.onActivityResult", "voice search failed");
      }
    } else if (requestCode == ActivityRequestCodes.DIALTACTS_CALL_COMPOSER) {
      if (resultCode == RESULT_FIRST_USER) {
        LogUtil.i(
            "DialtactsActivity.onActivityResult", "returned from call composer, error occurred");
        String message =
            getString(
                R.string.call_composer_connection_failed,
                data.getStringExtra(CallComposerActivity.KEY_CONTACT_NAME));
        Snackbar.make(parentLayout, message, Snackbar.LENGTH_LONG).show();
      } else {
        LogUtil.i("DialtactsActivity.onActivityResult", "returned from call composer, no error");
      }
    } else if (requestCode == ActivityRequestCodes.DIALTACTS_CALL_DETAILS) {
      if (resultCode == RESULT_OK
          && data != null
          && data.getBooleanExtra(CallDetailsActivity.EXTRA_HAS_ENRICHED_CALL_DATA, false)) {
        String number = data.getStringExtra(CallDetailsActivity.EXTRA_PHONE_NUMBER);
        int snackbarDurationMillis = 5_000;
        Snackbar.make(parentLayout, getString(R.string.ec_data_deleted), snackbarDurationMillis)
            .setAction(R.string.view_conversation, v -> {
                IntentProvider provider = IntentProvider.getSendSmsIntentProvider(number);
                startActivity(provider.getClickIntent(this));
            })
            .setActionTextColor(getResources().getColor(R.color.dialer_snackbar_action_text_color))
            .show();
      }
    } else if (requestCode == ActivityRequestCodes.DIALTACTS_DUO) {
      // We just returned from starting Duo for a task. Reload our reachability data since it
      // may have changed after a user finished activating Duo.
      DuoComponent.get(this).getDuo().reloadReachability(this);
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  /**
   * Update the number of unread voicemails (potentially other tabs) displayed next to the tab icon.
   */
  public void updateTabUnreadCounts() {
    listsFragment.updateTabUnreadCounts();
  }

  /**
   * Initiates a fragment transaction to show the dialpad fragment. Animations and other visual
   * updates are handled by a callback which is invoked after the dialpad fragment is shown.
   *
   * @see #onDialpadShown
   */
  private void showDialpadFragment(boolean animate) {
    LogUtil.i("DialtactActivity.showDialpadFragment", "animate: %b", animate);
    if (isDialpadShown) {
      LogUtil.i("DialtactsActivity.showDialpadFragment", "dialpad already shown");
      return;
    }
    if (stateSaved) {
      LogUtil.i("DialtactsActivity.showDialpadFragment", "state already saved");
      return;
    }
    isDialpadShown = true;

    listsFragment.setUserVisibleHint(false);

    final FragmentTransaction ft = getFragmentManager().beginTransaction();
    if (dialpadFragment == null) {
      dialpadFragment = new DialpadFragment();
      ft.add(R.id.dialtacts_container, dialpadFragment, TAG_DIALPAD_FRAGMENT);
    } else {
      ft.show(dialpadFragment);
    }

    dialpadFragment.setAnimate(animate);
    Logger.get(this).logScreenView(ScreenEvent.Type.DIALPAD, this);
    ft.commit();

    if (animate) {
      floatingActionButtonController.scaleOut();
      maybeEnterSearchUi();
    } else {
      floatingActionButtonController.scaleOut();
      maybeEnterSearchUi();
    }
    actionBarController.onDialpadUp();

    Assert.isNotNull(listsFragment.getView()).animate().alpha(0).withLayer();

    // adjust the title, so the user will know where we're at when the activity start/resumes.
    setTitle(R.string.launcherDialpadActivityLabel);
  }

  @Override
  public void getLastOutgoingCall(LastOutgoingCallCallback callback) {
    DialerExecutorComponent.get(this)
        .dialerExecutorFactory()
        .createUiTaskBuilder(
            getFragmentManager(), "Query last phone number", Calls::getLastOutgoingCall)
        .onSuccess(output -> callback.lastOutgoingCall(output))
        .build()
        .executeParallel(this);
  }

  /** Callback from child DialpadFragment when the dialpad is shown. */
  @Override
  public void onDialpadShown() {
    LogUtil.enterBlock("DialtactsActivity.onDialpadShown");
    Assert.isNotNull(dialpadFragment);
    if (dialpadFragment.getAnimate()) {
      Assert.isNotNull(dialpadFragment.getView()).startAnimation(slideIn);
    } else {
      dialpadFragment.setYFraction(0);
    }

    updateSearchFragmentPosition();
  }

  @Override
  public void onCallPlacedFromDialpad() {
    clearSearchOnPause = true;
  }

  @Override
  public void onContactsListScrolled(boolean isDragging) {
    // intentionally empty.
  }

  /**
   * Initiates animations and other visual updates to hide the dialpad. The fragment is hidden in a
   * callback after the hide animation ends.
   *
   * @see #commitDialpadFragmentHide
   */
  private void hideDialpadFragment(boolean animate, boolean clearDialpad) {
    LogUtil.enterBlock("DialtactsActivity.hideDialpadFragment");
    if (dialpadFragment == null || dialpadFragment.getView() == null) {
      return;
    }
    if (clearDialpad) {
      // Temporarily disable accessibility when we clear the dialpad, since it should be
      // invisible and should not announce anything.
      dialpadFragment
          .getDigitsWidget()
          .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      dialpadFragment.clearDialpad();
      dialpadFragment
          .getDigitsWidget()
          .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }
    if (!isDialpadShown) {
      return;
    }
    isDialpadShown = false;
    dialpadFragment.setAnimate(animate);
    listsFragment.setUserVisibleHint(true);
    listsFragment.sendScreenViewForCurrentPosition();

    updateSearchFragmentPosition();

    floatingActionButtonController.align(getFabAlignment(), animate);
    if (animate) {
      dialpadFragment.getView().startAnimation(slideOut);
    } else {
      commitDialpadFragmentHide();
    }

    actionBarController.onDialpadDown();

    // reset the title to normal.
    setTitle(R.string.launcherActivityLabel);
  }

  /** Finishes hiding the dialpad fragment after any animations are completed. */
  private void commitDialpadFragmentHide() {
    if (!stateSaved && dialpadFragment != null && !dialpadFragment.isHidden() && !isDestroyed()) {
      final FragmentTransaction ft = getFragmentManager().beginTransaction();
      ft.hide(dialpadFragment);
      ft.commit();
    }
    floatingActionButtonController.scaleIn();
  }

  private void updateSearchFragmentPosition() {
    SearchFragment fragment = null;
    if (smartDialSearchFragment != null) {
      fragment = smartDialSearchFragment;
    } else if (regularSearchFragment != null) {
      fragment = regularSearchFragment;
    }
    LogUtil.d(
        "DialtactsActivity.updateSearchFragmentPosition",
        "fragment: %s, isVisible: %b",
        fragment,
        fragment != null && fragment.isVisible());
    if (fragment != null) {
      // We need to force animation here even when fragment is not visible since it might not be
      // visible immediately after screen orientation change and dialpad height would not be
      // available immediately which is required to update position. By forcing an animation,
      // position will be updated after a delay by when the dialpad height would be available.
      fragment.updatePosition(true /* animate */);
    } else if (newSearchFragment != null) {
      int animationDuration = getResources().getInteger(R.integer.dialpad_slide_in_duration);
      int actionbarHeight = getResources().getDimensionPixelSize(R.dimen.action_bar_height_large);
      int shadowHeight = getResources().getDrawable(R.drawable.search_shadow).getIntrinsicHeight();
      int start = isDialpadShown() ? actionbarHeight - shadowHeight : 0;
      int end = isDialpadShown() ? 0 : actionbarHeight - shadowHeight;
      newSearchFragment.animatePosition(start, end, animationDuration);
    }
  }

  @Override
  public boolean isInSearchUi() {
    return inDialpadSearch || inRegularSearch || inNewSearch;
  }

  @Override
  public boolean hasSearchQuery() {
    return !TextUtils.isEmpty(searchQuery);
  }

  private void setNotInSearchUi() {
    inDialpadSearch = false;
    inRegularSearch = false;
    inNewSearch = false;
  }

  private void hideDialpadAndSearchUi() {
    if (isDialpadShown) {
      hideDialpadFragment(false, true);
    }
    exitSearchUi();
  }

  private void prepareVoiceSearchButton() {
    searchEditTextLayout.setVoiceSearchEnabled(isVoiceSearchEnabled());
    voiceSearchButton.setOnClickListener(this);
  }

  private boolean isVoiceSearchEnabled() {
    if (voiceSearchEnabledForTest.isPresent()) {
      return voiceSearchEnabledForTest.get();
    }
    return canIntentBeHandled(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH));
  }

  public boolean isNearbyPlacesSearchEnabled() {
    return false;
  }

  protected int getSearchBoxHint() {
    return R.string.dialer_hint_find_contact;
  }

  /** Sets the hint text for the contacts search box */
  private void setSearchBoxHint() {
    ((TextView) searchEditTextLayout.findViewById(R.id.search_box_start_search))
        .setHint(getSearchBoxHint());
  }

  protected OptionsPopupMenu buildOptionsMenu(View invoker) {
    final OptionsPopupMenu popupMenu = new OptionsPopupMenu(this, invoker);
    popupMenu.inflate(R.menu.dialtacts_options);
    popupMenu.setOnMenuItemClickListener(this);
    return popupMenu;
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    if (pendingSearchViewQuery != null) {
      searchView.setText(pendingSearchViewQuery);
      pendingSearchViewQuery = null;
    }
    if (actionBarController != null) {
      actionBarController.restoreActionBarOffset();
    }
    return false;
  }

  /**
   * Returns true if the intent is due to hitting the green send key (hardware call button:
   * KEYCODE_CALL) while in a call.
   *
   * @param intent the intent that launched this activity
   * @return true if the intent is due to hitting the green send key while in a call
   */
  private boolean isSendKeyWhileInCall(Intent intent) {
    // If there is a call in progress and the user launched the dialer by hitting the call
    // button, go straight to the in-call screen.
    final boolean callKey = Intent.ACTION_CALL_BUTTON.equals(intent.getAction());

    // When KEYCODE_CALL event is handled it dispatches an intent with the ACTION_CALL_BUTTON.
    // Besides of checking the intent action, we must check if the phone is really during a
    // call in order to decide whether to ignore the event or continue to display the activity.
    if (callKey && phoneIsInUse()) {
      TelecomUtil.showInCallScreen(this, false);
      return true;
    }

    return false;
  }

  /**
   * Sets the current tab based on the intent's request type
   *
   * @param intent Intent that contains information about which tab should be selected
   */
  private void displayFragment(Intent intent) {
    // If we got here by hitting send and we're in call forward along to the in-call activity
    if (isSendKeyWhileInCall(intent)) {
      finish();
      return;
    }

    boolean showDialpadChooser =
        !ACTION_SHOW_TAB.equals(intent.getAction())
            && phoneIsInUse()
            && !DialpadFragment.isAddCallMode(intent);
    boolean isDialIntent = intent.getData() != null && isDialIntent(intent);
    boolean isAddCallIntent = DialpadFragment.isAddCallMode(intent);
    if (showDialpadChooser || isDialIntent || isAddCallIntent) {
      LogUtil.i(
          "DialtactsActivity.displayFragment",
          "show dialpad fragment (showDialpadChooser: %b, isDialIntent: %b, isAddCallIntent: %b)",
          showDialpadChooser,
          isDialIntent,
          isAddCallIntent);
      showDialpadFragment(false);
      dialpadFragment.setStartedFromNewIntent(true);
      if (showDialpadChooser && !dialpadFragment.isVisible()) {
        inCallDialpadUp = true;
      }
    } else if (isLastTabEnabled) {
      @TabIndex
      int tabIndex =
          StorageComponent.get(this)
              .unencryptedSharedPrefs()
              .getInt(KEY_LAST_TAB, DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL);
      // If voicemail tab is saved and its availability changes, we still move to the voicemail tab
      // but it is quickly removed and shown the contacts tab.
      if (listsFragment != null) {
        listsFragment.showTab(tabIndex);
        PerformanceReport.setStartingTabIndex(tabIndex);
      } else {
        PerformanceReport.setStartingTabIndex(DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL);
      }
    }
  }

  @Override
  public void onNewIntent(Intent newIntent) {
    LogUtil.enterBlock("DialtactsActivity.onNewIntent");
    setIntent(newIntent);
    firstLaunch = true;

    stateSaved = false;
    displayFragment(newIntent);

    invalidateOptionsMenu();
  }

  /** Returns true if the given intent contains a phone number to populate the dialer with */
  private boolean isDialIntent(Intent intent) {
    final String action = intent.getAction();
    if (Intent.ACTION_DIAL.equals(action) || ACTION_TOUCH_DIALER.equals(action)) {
      return true;
    }
    if (Intent.ACTION_VIEW.equals(action)) {
      final Uri data = intent.getData();
      if (data != null && PhoneAccount.SCHEME_TEL.equals(data.getScheme())) {
        return true;
      }
    }
    return false;
  }

  /** Shows the search fragment */
  private void enterSearchUi(boolean smartDialSearch, String query, boolean animate) {
    LogUtil.i("DialtactsActivity.enterSearchUi", "smart dial: %b", smartDialSearch);
    if (stateSaved || getFragmentManager().isDestroyed()) {
      // Weird race condition where fragment is doing work after the activity is destroyed
      // due to talkback being on (a bug). Just return since we can't do any
      // constructive here.
      LogUtil.i(
          "DialtactsActivity.enterSearchUi",
          "not entering search UI (mStateSaved: %b, isDestroyed: %b)",
          stateSaved,
          getFragmentManager().isDestroyed());
      return;
    }

    final FragmentTransaction transaction = getFragmentManager().beginTransaction();
    if (inDialpadSearch && smartDialSearchFragment != null) {
      transaction.remove(smartDialSearchFragment);
    } else if (inRegularSearch && regularSearchFragment != null) {
      transaction.remove(regularSearchFragment);
    }

    final String tag;
    inDialpadSearch = false;
    inRegularSearch = false;
    inNewSearch = false;
    boolean useNewSearch =
        ConfigProviderBindings.get(this).getBoolean("enable_new_search_fragment", false);
    if (useNewSearch) {
      tag = TAG_NEW_SEARCH_FRAGMENT;
      inNewSearch = true;
    } else if (smartDialSearch) {
      tag = TAG_SMARTDIAL_SEARCH_FRAGMENT;
      inDialpadSearch = true;
    } else {
      tag = TAG_REGULAR_SEARCH_FRAGMENT;
      inRegularSearch = true;
    }

    floatingActionButtonController.scaleOut();

    if (animate) {
      transaction.setCustomAnimations(android.R.animator.fade_in, 0);
    } else {
      transaction.setTransition(FragmentTransaction.TRANSIT_NONE);
    }

    Fragment fragment = getFragmentManager().findFragmentByTag(tag);
    if (fragment == null) {
      if (useNewSearch) {
        fragment = NewSearchFragment.newInstance(!isDialpadShown());
      } else if (smartDialSearch) {
        fragment = new SmartDialSearchFragment();
      } else {
        fragment = Bindings.getLegacy(this).newRegularSearchFragment();
        ((SearchFragment) fragment)
            .setOnTouchListener(
                (v, event) -> {
                  // Show the FAB when the user touches the lists fragment and the soft
                  // keyboard is hidden.
                  hideDialpadFragment(true, false);
                  v.performClick();
                  return false;
                });
      }
      transaction.add(R.id.dialtacts_frame, fragment, tag);
    } else {
      // TODO(calderwoodra): if this is a transition from dialpad to searchbar, animate fragment
      // down, and vice versa. Perhaps just add a coordinator behavior with the search bar.
      transaction.show(fragment);
    }

    // DialtactsActivity will provide the options menu
    fragment.setHasOptionsMenu(false);

    // Will show empty list if P13nRanker is not enabled. Else, re-ranked list by the ranker.
    if (!useNewSearch) {
      ((SearchFragment) fragment)
          .setShowEmptyListForNullQuery(p13nRanker.shouldShowEmptyListForNullQuery());
    } else {
      // TODO(calderwoodra): add p13n ranker to new search.
    }

    if (!smartDialSearch && !useNewSearch) {
      ((SearchFragment) fragment).setQueryString(query);
    } else if (useNewSearch) {
      ((NewSearchFragment) fragment).setQuery(query, getCallInitiationType());
    }
    transaction.commit();

    if (animate) {
      Assert.isNotNull(listsFragment.getView()).animate().alpha(0).withLayer();
    }
    listsFragment.setUserVisibleHint(false);

    if (smartDialSearch) {
      Logger.get(this).logScreenView(ScreenEvent.Type.SMART_DIAL_SEARCH, this);
    } else {
      Logger.get(this).logScreenView(ScreenEvent.Type.REGULAR_SEARCH, this);
    }
  }

  /** Hides the search fragment */
  private void exitSearchUi() {
    LogUtil.enterBlock("DialtactsActivity.exitSearchUi");

    // See related bug in enterSearchUI();
    if (getFragmentManager().isDestroyed() || stateSaved) {
      return;
    }

    searchView.setText(null);

    if (dialpadFragment != null) {
      dialpadFragment.clearDialpad();
    }

    setNotInSearchUi();

    // There are four states the fab can be in:
    //   - Not visible and should remain not visible (do nothing)
    //   - Not visible (move then show the fab)
    //   - Visible, in the correct position (do nothing)
    //   - Visible, in the wrong position (hide, move, then show the fab)
    if (floatingActionButtonController.isVisible()
        && getFabAlignment() != FloatingActionButtonController.ALIGN_END) {
      floatingActionButtonController.scaleOut(
          new OnVisibilityChangedListener() {
            @Override
            public void onHidden(FloatingActionButton floatingActionButton) {
              super.onHidden(floatingActionButton);
              onPageScrolled(
                  listsFragment.getCurrentTabIndex(), 0 /* offset */, 0 /* pixelOffset */);
              floatingActionButtonController.scaleIn();
            }
          });
    } else if (!floatingActionButtonController.isVisible() && listsFragment.shouldShowFab()) {
      onPageScrolled(listsFragment.getCurrentTabIndex(), 0 /* offset */, 0 /* pixelOffset */);
      ThreadUtil.getUiThreadHandler()
          .postDelayed(() -> floatingActionButtonController.scaleIn(), FAB_SCALE_IN_DELAY_MS);
    }

    final FragmentTransaction transaction = getFragmentManager().beginTransaction();
    if (smartDialSearchFragment != null) {
      transaction.remove(smartDialSearchFragment);
    }
    if (regularSearchFragment != null) {
      transaction.remove(regularSearchFragment);
    }
    if (newSearchFragment != null) {
      transaction.remove(newSearchFragment);
    }
    transaction.commit();

    Assert.isNotNull(listsFragment.getView()).animate().alpha(1).withLayer();

    if (dialpadFragment == null || !dialpadFragment.isVisible()) {
      // If the dialpad fragment wasn't previously visible, then send a screen view because
      // we are exiting regular search. Otherwise, the screen view will be sent by
      // {@link #hideDialpadFragment}.
      listsFragment.sendScreenViewForCurrentPosition();
      listsFragment.setUserVisibleHint(true);
    }
    onPageSelected(listsFragment.getCurrentTabIndex());

    actionBarController.onSearchUiExited();
  }

  @Override
  public void onBackPressed() {
    PerformanceReport.recordClick(UiAction.Type.PRESS_ANDROID_BACK_BUTTON);

    if (stateSaved) {
      return;
    }
    if (isDialpadShown) {
      hideDialpadFragment(true, false);
      if (TextUtils.isEmpty(dialpadQuery)) {
        exitSearchUi();
      }
    } else if (isInSearchUi()) {
      if (isKeyboardOpen) {
        DialerUtils.hideInputMethod(parentLayout);
        PerformanceReport.recordClick(UiAction.Type.HIDE_KEYBOARD_IN_SEARCH);
      } else {
        exitSearchUi();
      }
    } else {
      super.onBackPressed();
    }
  }

  @Override
  public void onConfigurationChanged(Configuration configuration) {
    super.onConfigurationChanged(configuration);
    // Checks whether a hardware keyboard is available
    if (configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) {
      isKeyboardOpen = true;
    } else if (configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES) {
      isKeyboardOpen = false;
    }
  }

  private void maybeEnterSearchUi() {
    if (!isInSearchUi()) {
      enterSearchUi(true /* isSmartDial */, searchQuery, false);
    }
  }

  @Override
  public void onDialpadQueryChanged(String query) {
    dialpadQuery = query;
    if (smartDialSearchFragment != null) {
      smartDialSearchFragment.setAddToContactNumber(query);
    }
    if (newSearchFragment != null) {
      newSearchFragment.setRawNumber(query);
    }
    final String normalizedQuery =
        SmartDialNameMatcher.normalizeNumber(/* context = */ this, query);

    if (!TextUtils.equals(searchView.getText(), normalizedQuery)) {
      if (DEBUG) {
        LogUtil.v("DialtactsActivity.onDialpadQueryChanged", "new query: " + query);
      }
      if (dialpadFragment == null || !dialpadFragment.isVisible()) {
        // This callback can happen if the dialpad fragment is recreated because of
        // activity destruction. In that case, don't update the search view because
        // that would bring the user back to the search fragment regardless of the
        // previous state of the application. Instead, just return here and let the
        // fragment manager correctly figure out whatever fragment was last displayed.
        if (!TextUtils.isEmpty(normalizedQuery)) {
          pendingSearchViewQuery = normalizedQuery;
        }
        return;
      }
      searchView.setText(normalizedQuery);
    }

    try {
      if (dialpadFragment != null && dialpadFragment.isVisible()) {
        dialpadFragment.process_quote_emergency_unquote(normalizedQuery);
      }
    } catch (Exception ignored) {
      // Skip any exceptions for this piece of code
    }
  }

  @Override
  public boolean onDialpadSpacerTouchWithEmptyQuery() {
    if (inDialpadSearch
        && smartDialSearchFragment != null
        && !smartDialSearchFragment.isShowingPermissionRequest()) {
      PerformanceReport.recordClick(UiAction.Type.CLOSE_DIALPAD);
      hideDialpadFragment(true /* animate */, true /* clearDialpad */);
      return true;
    }
    return false;
  }

  @Override
  public boolean shouldShowDialpadChooser() {
    // Show the dialpad chooser if we're in a call
    return true;
  }

  @Override
  public void onSearchListTouch() {
    if (isDialpadShown) {
      PerformanceReport.recordClick(UiAction.Type.CLOSE_DIALPAD);
      hideDialpadFragment(true, false);
      if (TextUtils.isEmpty(dialpadQuery)) {
        exitSearchUi();
      }
    } else {
      UiUtil.hideKeyboardFrom(this, searchEditTextLayout);
    }
  }

  @Override
  public void onListFragmentScrollStateChange(int scrollState) {
    PerformanceReport.recordScrollStateChange(scrollState);
    if (scrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
      hideDialpadFragment(true, false);
      DialerUtils.hideInputMethod(parentLayout);
    }
  }

  @Override
  public void onListFragmentScroll(int firstVisibleItem, int visibleItemCount, int totalItemCount) {
    // TODO: No-op for now. This should eventually show/hide the actionBar based on
    // interactions with the ListsFragments.
  }

  private boolean phoneIsInUse() {
    return TelecomUtil.isInManagedCall(this);
  }

  private boolean canIntentBeHandled(Intent intent) {
    final PackageManager packageManager = getPackageManager();
    final List<ResolveInfo> resolveInfo =
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
    return resolveInfo != null && resolveInfo.size() > 0;
  }

  /** Called when the user has long-pressed a contact tile to start a drag operation. */
  @Override
  public void onDragStarted(int x, int y, PhoneFavoriteSquareTileView view) {
    listsFragment.showRemoveView(true);
  }

  @Override
  public void onDragHovered(int x, int y, PhoneFavoriteSquareTileView view) {}

  /** Called when the user has released a contact tile after long-pressing it. */
  @Override
  public void onDragFinished(int x, int y) {
    listsFragment.showRemoveView(false);
  }

  @Override
  public void onDroppedOnRemove() {}

  @Override
  public ImageView getDragShadowOverlay() {
    return findViewById(R.id.contact_tile_drag_shadow_overlay);
  }

  @Override
  public void setHasFrequents(boolean hasFrequents) {
    // No-op
  }

  /**
   * Allows the SpeedDialFragment to attach the drag controller to mRemoveViewContainer once it has
   * been attached to the activity.
   */
  @Override
  public void setDragDropController(DragDropController dragController) {
    dragDropController = dragController;
    listsFragment.getRemoveView().setDragDropController(dragController);
  }

  /** Implemented to satisfy {@link OldSpeedDialFragment.HostInterface} */
  @Override
  public void showAllContactsTab() {
    if (listsFragment != null) {
      listsFragment.showTab(DialtactsPagerAdapter.TAB_INDEX_ALL_CONTACTS);
    }
  }

  /** Implemented to satisfy {@link CallLogFragment.HostInterface} */
  @Override
  public void showDialpad() {
    showDialpadFragment(true);
  }

  @Override
  public void enableFloatingButton(boolean enabled) {
    LogUtil.d("DialtactsActivity.enableFloatingButton", "enable: %b", enabled);
    // Floating button shouldn't be enabled when dialpad is shown.
    if (!isDialpadShown() || !enabled) {
      floatingActionButtonController.setVisible(enabled);
    }
  }

  @Override
  public void onPickDataUri(
      Uri dataUri, boolean isVideoCall, CallSpecificAppData callSpecificAppData) {
    clearSearchOnPause = true;
    PhoneNumberInteraction.startInteractionForPhoneCall(
        DialtactsActivity.this, dataUri, isVideoCall, callSpecificAppData);
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
        this,
        new CallIntentBuilder(phoneNumber, callSpecificAppData)
            .setIsVideoCall(isVideoCall)
            .setAllowAssistedDial(callSpecificAppData.getAllowAssistedDialing()));

    clearSearchOnPause = true;
  }

  @Override
  public void onHomeInActionBarSelected() {
    exitSearchUi();
  }

  @Override
  public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    // FAB does not move with the new favorites UI
    if (newFavoritesIsEnabled()) {
      return;
    }
    int tabIndex = listsFragment.getCurrentTabIndex();

    // Scroll the button from center to end when moving from the Speed Dial to Call History tab.
    // In RTL, scroll when the current tab is Call History instead, since the order of the tabs
    // is reversed and the ViewPager returns the left tab position during scroll.
    boolean isRtl = ViewUtil.isRtl();
    if (!isRtl && tabIndex == DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL && !isLandscape) {
      floatingActionButtonController.onPageScrolled(positionOffset);
    } else if (isRtl && tabIndex == DialtactsPagerAdapter.TAB_INDEX_HISTORY && !isLandscape) {
      floatingActionButtonController.onPageScrolled(1 - positionOffset);
    } else if (tabIndex != DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL) {
      floatingActionButtonController.onPageScrolled(1);
    }
  }

  @Override
  public void onPageSelected(int position) {
    updateMissedCalls();
    int tabIndex = listsFragment.getCurrentTabIndex();
    if (tabIndex != previouslySelectedTabIndex) {
      floatingActionButtonController.scaleIn();
    }
    LogUtil.i("DialtactsActivity.onPageSelected", "tabIndex: %d", tabIndex);
    previouslySelectedTabIndex = tabIndex;
    timeTabSelected = SystemClock.elapsedRealtime();
  }

  @Override
  public void onPageScrollStateChanged(int state) {}

  @Override
  public boolean isActionBarShowing() {
    return actionBarController.isActionBarShowing();
  }

  @Override
  public boolean isDialpadShown() {
    return isDialpadShown;
  }

  @Override
  public int getDialpadHeight() {
    if (dialpadFragment != null) {
      return dialpadFragment.getDialpadHeight();
    }
    return 0;
  }

  @Override
  public void setActionBarHideOffset(int offset) {
    getActionBarSafely().setHideOffset(offset);
  }

  @Override
  public int getActionBarHeight() {
    return actionBarHeight;
  }

  @VisibleForTesting
  public int getFabAlignment() {
    if (!newFavoritesIsEnabled()
        && !isLandscape
        && !isInSearchUi()
        && listsFragment.getCurrentTabIndex() == DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL) {
      return FloatingActionButtonController.ALIGN_MIDDLE;
    }
    return FloatingActionButtonController.ALIGN_END;
  }

  private void updateMissedCalls() {
    if (previouslySelectedTabIndex == DialtactsPagerAdapter.TAB_INDEX_HISTORY) {
      listsFragment.markMissedCallsAsReadAndRemoveNotifications();
    }
  }

  @Override
  public void onDisambigDialogDismissed() {
    // Don't do anything; the app will remain open with favorites tiles displayed.
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
        // interaction with an invalid contact from the Dialtacts activity.
        Assert.fail("PhoneNumberInteraction error: " + interactionErrorCode);
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    // This should never happen; it should be impossible to start an interaction without the
    // contacts permission from the Dialtacts activity.
    Assert.fail(
        String.format(
            Locale.US,
            "Permissions requested unexpectedly: %d/%s/%s",
            requestCode,
            Arrays.toString(permissions),
            Arrays.toString(grantResults)));
  }

  @Override
  public void onActionModeStateChanged(boolean isEnabled) {
    isMultiSelectModeEnabled = isEnabled;
  }

  @Override
  public boolean isActionModeStateEnabled() {
    return isMultiSelectModeEnabled;
  }

  private CallInitiationType.Type getCallInitiationType() {
    return isDialpadShown
        ? CallInitiationType.Type.DIALPAD
        : CallInitiationType.Type.REGULAR_SEARCH;
  }

  @Override
  public void onCallPlacedFromSearch() {
    DialerUtils.hideInputMethod(parentLayout);
    clearSearchOnPause = true;
  }

  protected int getPreviouslySelectedTabIndex() {
    return previouslySelectedTabIndex;
  }

  @Override
  public void onContactSelected(ImageView photo, Uri contactUri, long contactId) {
    Logger.get(this)
        .logInteraction(InteractionEvent.Type.OPEN_QUICK_CONTACT_FROM_CONTACTS_FRAGMENT_ITEM);
    QuickContact.showQuickContact(
        this, photo, contactUri, QuickContact.MODE_LARGE, null /* excludeMimes */);
  }

  /** Popup menu accessible from the search bar */
  protected class OptionsPopupMenu extends PopupMenu {

    public OptionsPopupMenu(Context context, View anchor) {
      super(context, anchor, Gravity.END);
    }

    @Override
    public void show() {
      Menu menu = getMenu();
      MenuItem clearFrequents = menu.findItem(R.id.menu_clear_frequents);
      clearFrequents.setVisible(
          PermissionsUtil.hasContactsReadPermissions(DialtactsActivity.this)
              && listsFragment != null
              && listsFragment.hasFrequents());

      menu.findItem(R.id.menu_history)
          .setVisible(PermissionsUtil.hasPhonePermissions(DialtactsActivity.this));

      Context context = DialtactsActivity.this.getApplicationContext();
      MenuItem simulatorMenuItem = menu.findItem(R.id.menu_simulator_submenu);
      Simulator simulator = SimulatorComponent.get(context).getSimulator();
      if (simulator.shouldShow()) {
        simulatorMenuItem.setVisible(true);
        simulatorMenuItem.setActionProvider(simulator.getActionProvider(DialtactsActivity.this));
      } else {
        simulatorMenuItem.setVisible(false);
      }

      menu.findItem(R.id.menu_new_ui_launcher_shortcut)
          .setVisible(MainComponent.isNewUiEnabled(context));

      super.show();
    }
  }

  /**
   * Listener that listens to drag events and sends their x and y coordinates to a {@link
   * DragDropController}.
   */
  private class LayoutOnDragListener implements OnDragListener {

    @Override
    public boolean onDrag(View v, DragEvent event) {
      if (event.getAction() == DragEvent.ACTION_DRAG_LOCATION) {
        dragDropController.handleDragHovered(v, (int) event.getX(), (int) event.getY());
      }
      return true;
    }
  }

  @VisibleForTesting
  static void setVoiceSearchEnabledForTest(Optional<Boolean> enabled) {
    voiceSearchEnabledForTest = enabled;
  }

  private boolean newFavoritesIsEnabled() {
    return ConfigProviderBindings.get(this).getBoolean("enable_new_favorites_tab", false);
  }
}
