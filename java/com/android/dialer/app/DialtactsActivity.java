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
import android.speech.RecognizerIntent;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
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
import android.view.KeyEvent;
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
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import com.android.contacts.common.dialog.ClearFrequentsDialog;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.common.list.PhoneNumberListAdapter;
import com.android.contacts.common.list.PhoneNumberPickerFragment.CursorReranker;
import com.android.contacts.common.list.PhoneNumberPickerFragment.OnLoadFinishedListener;
import com.android.contacts.common.widget.FloatingActionButtonController;
import com.android.dialer.animation.AnimUtils;
import com.android.dialer.animation.AnimationListenerAdapter;
import com.android.dialer.app.calllog.CallLogActivity;
import com.android.dialer.app.calllog.CallLogAdapter;
import com.android.dialer.app.calllog.CallLogFragment;
import com.android.dialer.app.calllog.CallLogNotificationsService;
import com.android.dialer.app.calllog.IntentProvider;
import com.android.dialer.app.dialpad.DialpadFragment;
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
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.callintent.CallSpecificAppData;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.database.Database;
import com.android.dialer.database.DialerDatabaseHelper;
import com.android.dialer.interactions.PhoneNumberInteraction;
import com.android.dialer.interactions.PhoneNumberInteraction.InteractionErrorCode;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ScreenEvent;
import com.android.dialer.logging.UiAction;
import com.android.dialer.main.Main;
import com.android.dialer.main.MainComponent;
import com.android.dialer.p13n.inference.P13nRanking;
import com.android.dialer.p13n.inference.protocol.P13nRanker;
import com.android.dialer.p13n.inference.protocol.P13nRanker.P13nRefreshCompleteListener;
import com.android.dialer.p13n.logging.P13nLogger;
import com.android.dialer.p13n.logging.P13nLogging;
import com.android.dialer.performancereport.PerformanceReport;
import com.android.dialer.postcall.PostCall;
import com.android.dialer.proguard.UsedByReflection;
import com.android.dialer.searchfragment.list.NewSearchFragment;
import com.android.dialer.simulator.Simulator;
import com.android.dialer.simulator.SimulatorComponent;
import com.android.dialer.smartdial.SmartDialNameMatcher;
import com.android.dialer.smartdial.SmartDialPrefix;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.util.TouchPointManager;
import com.android.dialer.util.TransactionSafeActivity;
import com.android.dialer.util.ViewUtil;
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
        ActivityCompat.OnRequestPermissionsResultCallback {

  public static final boolean DEBUG = false;
  @VisibleForTesting public static final String TAG_DIALPAD_FRAGMENT = "dialpad";
  private static final String ACTION_SHOW_TAB = "ACTION_SHOW_TAB";
  @VisibleForTesting public static final String EXTRA_SHOW_TAB = "EXTRA_SHOW_TAB";
  public static final String EXTRA_CLEAR_NEW_VOICEMAILS = "EXTRA_CLEAR_NEW_VOICEMAILS";
  private static final String KEY_LAST_TAB = "last_tab";
  private static final String TAG = "DialtactsActivity";
  private static final String KEY_IN_REGULAR_SEARCH_UI = "in_regular_search_ui";
  private static final String KEY_IN_DIALPAD_SEARCH_UI = "in_dialpad_search_ui";
  private static final String KEY_SEARCH_QUERY = "search_query";
  private static final String KEY_FIRST_LAUNCH = "first_launch";
  private static final String KEY_WAS_CONFIGURATION_CHANGE = "was_configuration_change";
  private static final String KEY_IS_DIALPAD_SHOWN = "is_dialpad_shown";
  private static final String TAG_NEW_SEARCH_FRAGMENT = "new_search";
  private static final String TAG_REGULAR_SEARCH_FRAGMENT = "search";
  private static final String TAG_SMARTDIAL_SEARCH_FRAGMENT = "smartdial";
  private static final String TAG_FAVORITES_FRAGMENT = "favorites";
  /** Just for backward compatibility. Should behave as same as {@link Intent#ACTION_DIAL}. */
  private static final String ACTION_TOUCH_DIALER = "com.android.phone.action.TOUCH_DIALER";

  private static final int ACTIVITY_REQUEST_CODE_VOICE_SEARCH = 1;
  public static final int ACTIVITY_REQUEST_CODE_CALL_COMPOSE = 2;
  public static final int ACTIVITY_REQUEST_CODE_LIGHTBRINGER = 3;
  public static final int ACTIVITY_REQUEST_CODE_CALL_DETAILS = 4;

  private static final int FAB_SCALE_IN_DELAY_MS = 300;

  /**
   * Minimum time the history tab must have been selected for it to be marked as seen in onStop()
   */
  private static final long HISTORY_TAB_SEEN_TIMEOUT = TimeUnit.SECONDS.toMillis(3);

  /** Fragment containing the dialpad that slides into view */
  protected DialpadFragment mDialpadFragment;

  private CoordinatorLayout mParentLayout;
  /** Fragment for searching phone numbers using the alphanumeric keyboard. */
  private RegularSearchFragment mRegularSearchFragment;

  /** Fragment for searching phone numbers using the dialpad. */
  private SmartDialSearchFragment mSmartDialSearchFragment;

  /** new Fragment for search phone numbers using the keyboard and the dialpad. */
  private NewSearchFragment mNewSearchFragment;

  /** Animation that slides in. */
  private Animation mSlideIn;

  /** Animation that slides out. */
  private Animation mSlideOut;
  /** Fragment containing the speed dial list, call history list, and all contacts list. */
  private ListsFragment mListsFragment;
  /**
   * Tracks whether onSaveInstanceState has been called. If true, no fragment transactions can be
   * commited.
   */
  private boolean mStateSaved;

  private boolean mIsRestarting;
  private boolean mInDialpadSearch;
  private boolean mInRegularSearch;
  private boolean mClearSearchOnPause;
  private boolean mIsDialpadShown;
  private boolean mShowDialpadOnResume;
  /** Whether or not the device is in landscape orientation. */
  private boolean mIsLandscape;
  /** True if the dialpad is only temporarily showing due to being in call */
  private boolean mInCallDialpadUp;
  /** True when this activity has been launched for the first time. */
  private boolean mFirstLaunch;
  /**
   * Search query to be applied to the SearchView in the ActionBar once onCreateOptionsMenu has been
   * called.
   */
  private String mPendingSearchViewQuery;

  private PopupMenu mOverflowMenu;
  private EditText mSearchView;
  private View mVoiceSearchButton;
  private String mSearchQuery;
  private String mDialpadQuery;
  private DialerDatabaseHelper mDialerDatabaseHelper;
  private DragDropController mDragDropController;
  private ActionBarController mActionBarController;
  private FloatingActionButtonController mFloatingActionButtonController;
  private boolean mWasConfigurationChange;
  private long timeTabSelected;

  private P13nLogger mP13nLogger;
  private P13nRanker mP13nRanker;
  public boolean isMultiSelectModeEnabled;

  private boolean isLastTabEnabled;

  AnimationListenerAdapter mSlideInListener =
      new AnimationListenerAdapter() {
        @Override
        public void onAnimationEnd(Animation animation) {
          maybeEnterSearchUi();
        }
      };
  /** Listener for after slide out animation completes on dialer fragment. */
  AnimationListenerAdapter mSlideOutListener =
      new AnimationListenerAdapter() {
        @Override
        public void onAnimationEnd(Animation animation) {
          commitDialpadFragmentHide();
        }
      };
  /** Listener used to send search queries to the phone search fragment. */
  private final TextWatcher mPhoneSearchQueryTextListener =
      new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
          final String newText = s.toString();
          if (newText.equals(mSearchQuery)) {
            // If the query hasn't changed (perhaps due to activity being destroyed
            // and restored, or user launching the same DIAL intent twice), then there is
            // no need to do anything here.
            return;
          }

          if (count != 0) {
            PerformanceReport.recordClick(UiAction.Type.TEXT_CHANGE_WITH_INPUT);
          }

          if (DEBUG) {
            LogUtil.v("DialtactsActivity.onTextChanged", "called with new query: " + newText);
            LogUtil.v("DialtactsActivity.onTextChanged", "previous query: " + mSearchQuery);
          }
          mSearchQuery = newText;

          // TODO: show p13n when newText is empty.
          // Show search fragment only when the query string is changed to non-empty text.
          if (!TextUtils.isEmpty(newText)) {
            // Call enterSearchUi only if we are switching search modes, or showing a search
            // fragment for the first time.
            final boolean sameSearchMode =
                (mIsDialpadShown && mInDialpadSearch) || (!mIsDialpadShown && mInRegularSearch);
            if (!sameSearchMode) {
              enterSearchUi(mIsDialpadShown, mSearchQuery, true /* animate */);
            }
          }

          if (mSmartDialSearchFragment != null && mSmartDialSearchFragment.isVisible()) {
            mSmartDialSearchFragment.setQueryString(mSearchQuery);
          } else if (mRegularSearchFragment != null && mRegularSearchFragment.isVisible()) {
            mRegularSearchFragment.setQueryString(mSearchQuery);
          } else if (mNewSearchFragment != null) {
            mNewSearchFragment.setQuery(mSearchQuery);
          }
        }

        @Override
        public void afterTextChanged(Editable s) {}
      };
  /** Open the search UI when the user clicks on the search box. */
  private final View.OnClickListener mSearchViewOnClickListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          if (!isInSearchUi()) {
            PerformanceReport.recordClick(UiAction.Type.OPEN_SEARCH);
            mActionBarController.onSearchBoxTapped();
            enterSearchUi(
                false /* smartDialSearch */, mSearchView.getText().toString(), true /* animate */);
          }
        }
      };

  private int mActionBarHeight;
  private int mPreviouslySelectedTabIndex;
  /** Handles the user closing the soft keyboard. */
  private final View.OnKeyListener mSearchEditTextLayoutListener =
      new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
          if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (TextUtils.isEmpty(mSearchView.getText().toString())) {
              // If the search term is empty, close the search UI.
              PerformanceReport.recordClick(UiAction.Type.CLOSE_SEARCH_WITH_HIDE_BUTTON);
              maybeExitSearchUi();
            } else {
              // If the search term is not empty, show the dialpad fab.
              if (!mFloatingActionButtonController.isVisible()) {
                PerformanceReport.recordClick(UiAction.Type.HIDE_KEYBOARD_IN_SEARCH);
              }
              showFabInSearchUi();
            }
          }
          return false;
        }
      };
  /**
   * The text returned from a voice search query. Set in {@link #onActivityResult} and used in
   * {@link #onResume()} to populate the search box.
   */
  private String mVoiceSearchQuery;

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
    super.onCreate(savedInstanceState);

    mFirstLaunch = true;
    isLastTabEnabled = ConfigProviderBindings.get(this).getBoolean("last_tab_enabled", false);

    final Resources resources = getResources();
    mActionBarHeight = resources.getDimensionPixelSize(R.dimen.action_bar_height_large);

    Trace.beginSection(TAG + " setContentView");
    setContentView(R.layout.dialtacts_activity);
    Trace.endSection();
    getWindow().setBackgroundDrawable(null);

    Trace.beginSection(TAG + " setup Views");
    final ActionBar actionBar = getActionBarSafely();
    actionBar.setCustomView(R.layout.search_edittext);
    actionBar.setDisplayShowCustomEnabled(true);
    actionBar.setBackgroundDrawable(null);

    SearchEditTextLayout searchEditTextLayout =
        (SearchEditTextLayout) actionBar.getCustomView().findViewById(R.id.search_view_container);
    searchEditTextLayout.setPreImeKeyListener(mSearchEditTextLayoutListener);

    mActionBarController = new ActionBarController(this, searchEditTextLayout);

    mSearchView = (EditText) searchEditTextLayout.findViewById(R.id.search_view);
    mSearchView.addTextChangedListener(mPhoneSearchQueryTextListener);
    mVoiceSearchButton = searchEditTextLayout.findViewById(R.id.voice_search_button);
    searchEditTextLayout
        .findViewById(R.id.search_box_collapsed)
        .setOnClickListener(mSearchViewOnClickListener);
    searchEditTextLayout.setCallback(
        new SearchEditTextLayout.Callback() {
          @Override
          public void onBackButtonClicked() {
            onBackPressed();
          }

          @Override
          public void onSearchViewClicked() {
            // Hide FAB, as the keyboard is shown.
            mFloatingActionButtonController.scaleOut();
          }
        });

    mIsLandscape =
        getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    mPreviouslySelectedTabIndex = DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL;
    FloatingActionButton floatingActionButton =
        (FloatingActionButton) findViewById(R.id.floating_action_button);
    floatingActionButton.setOnClickListener(this);
    mFloatingActionButtonController =
        new FloatingActionButtonController(this, floatingActionButton);

    ImageButton optionsMenuButton =
        (ImageButton) searchEditTextLayout.findViewById(R.id.dialtacts_options_menu_button);
    optionsMenuButton.setOnClickListener(this);
    mOverflowMenu = buildOptionsMenu(optionsMenuButton);
    optionsMenuButton.setOnTouchListener(mOverflowMenu.getDragToOpenListener());

    // Add the favorites fragment but only if savedInstanceState is null. Otherwise the
    // fragment manager is responsible for recreating it.
    if (savedInstanceState == null) {
      getFragmentManager()
          .beginTransaction()
          .add(R.id.dialtacts_frame, new ListsFragment(), TAG_FAVORITES_FRAGMENT)
          .commit();
    } else {
      mSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY);
      mInRegularSearch = savedInstanceState.getBoolean(KEY_IN_REGULAR_SEARCH_UI);
      mInDialpadSearch = savedInstanceState.getBoolean(KEY_IN_DIALPAD_SEARCH_UI);
      mFirstLaunch = savedInstanceState.getBoolean(KEY_FIRST_LAUNCH);
      mWasConfigurationChange = savedInstanceState.getBoolean(KEY_WAS_CONFIGURATION_CHANGE);
      mShowDialpadOnResume = savedInstanceState.getBoolean(KEY_IS_DIALPAD_SHOWN);
      mActionBarController.restoreInstanceState(savedInstanceState);
    }

    final boolean isLayoutRtl = ViewUtil.isRtl();
    if (mIsLandscape) {
      mSlideIn =
          AnimationUtils.loadAnimation(
              this, isLayoutRtl ? R.anim.dialpad_slide_in_left : R.anim.dialpad_slide_in_right);
      mSlideOut =
          AnimationUtils.loadAnimation(
              this, isLayoutRtl ? R.anim.dialpad_slide_out_left : R.anim.dialpad_slide_out_right);
    } else {
      mSlideIn = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_in_bottom);
      mSlideOut = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_out_bottom);
    }

    mSlideIn.setInterpolator(AnimUtils.EASE_IN);
    mSlideOut.setInterpolator(AnimUtils.EASE_OUT);

    mSlideIn.setAnimationListener(mSlideInListener);
    mSlideOut.setAnimationListener(mSlideOutListener);

    mParentLayout = (CoordinatorLayout) findViewById(R.id.dialtacts_mainlayout);
    mParentLayout.setOnDragListener(new LayoutOnDragListener());
    ViewUtil.doOnGlobalLayout(
        floatingActionButton,
        view -> {
          int screenWidth = mParentLayout.getWidth();
          mFloatingActionButtonController.setScreenWidth(screenWidth);
          mFloatingActionButtonController.align(getFabAlignment(), false /* animate */);

          SmartDialPrefix.initializeNanpSettings(this);
        });

    Trace.endSection();

    Trace.beginSection(TAG + " initialize smart dialing");
    mDialerDatabaseHelper = Database.get(this).getDatabaseHelper(this);
    SmartDialPrefix.initializeNanpSettings(this);
    Trace.endSection();

    mP13nLogger = P13nLogging.get(getApplicationContext());
    mP13nRanker = P13nRanking.get(getApplicationContext());
    Trace.endSection();
  }

  @NonNull
  private ActionBar getActionBarSafely() {
    return Assert.isNotNull(getSupportActionBar());
  }

  @Override
  protected void onResume() {
    LogUtil.d("DialtactsActivity.onResume", "");
    Trace.beginSection(TAG + " onResume");
    super.onResume();

    // Some calls may not be recorded (eg. from quick contact),
    // so we should restart recording after these calls. (Recorded call is stopped)
    PostCall.restartPerformanceRecordingIfARecentCallExist(this);
    if (!PerformanceReport.isRecording()) {
      PerformanceReport.startRecording();
    }

    mStateSaved = false;
    if (mFirstLaunch) {
      displayFragment(getIntent());
    } else if (!phoneIsInUse() && mInCallDialpadUp) {
      hideDialpadFragment(false, true);
      mInCallDialpadUp = false;
    } else if (mShowDialpadOnResume) {
      showDialpadFragment(false);
      mShowDialpadOnResume = false;
    } else {
      PostCall.promptUserForMessageIfNecessary(this, mParentLayout);
    }

    // If there was a voice query result returned in the {@link #onActivityResult} callback, it
    // will have been stashed in mVoiceSearchQuery since the search results fragment cannot be
    // shown until onResume has completed.  Active the search UI and set the search term now.
    if (!TextUtils.isEmpty(mVoiceSearchQuery)) {
      mActionBarController.onSearchBoxTapped();
      mSearchView.setText(mVoiceSearchQuery);
      mVoiceSearchQuery = null;
    }

    if (mIsRestarting) {
      // This is only called when the activity goes from resumed -> paused -> resumed, so it
      // will not cause an extra view to be sent out on rotation
      if (mIsDialpadShown) {
        Logger.get(this).logScreenView(ScreenEvent.Type.DIALPAD, this);
      }
      mIsRestarting = false;
    }

    prepareVoiceSearchButton();
    if (!mWasConfigurationChange) {
      mDialerDatabaseHelper.startSmartDialUpdateThread();
    }
    mFloatingActionButtonController.align(getFabAlignment(), false /* animate */);

    if (mFirstLaunch) {
      // Only process the Intent the first time onResume() is called after receiving it
      if (Calls.CONTENT_TYPE.equals(getIntent().getType())) {
        // Externally specified extras take precedence to EXTRA_SHOW_TAB, which is only
        // used internally.
        final Bundle extras = getIntent().getExtras();
        if (extras != null && extras.getInt(Calls.EXTRA_CALL_TYPE_FILTER) == Calls.VOICEMAIL_TYPE) {
          mListsFragment.showTab(DialtactsPagerAdapter.TAB_INDEX_VOICEMAIL);
          Logger.get(this).logImpression(DialerImpression.Type.VVM_NOTIFICATION_CLICKED);
        } else {
          mListsFragment.showTab(DialtactsPagerAdapter.TAB_INDEX_HISTORY);
        }
      } else if (getIntent().hasExtra(EXTRA_SHOW_TAB)) {
        int index =
            getIntent().getIntExtra(EXTRA_SHOW_TAB, DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL);
        if (index < mListsFragment.getTabCount()) {
          // Hide dialpad since this is an explicit intent to show a specific tab, which is coming
          // from missed call or voicemail notification.
          hideDialpadFragment(false, false);
          exitSearchUi();
          mListsFragment.showTab(index);
        }
      }

      if (getIntent().getBooleanExtra(EXTRA_CLEAR_NEW_VOICEMAILS, false)) {
        LogUtil.i("DialtactsActivity.onResume", "clearing all new voicemails");
        CallLogNotificationsService.markAllNewVoicemailsAsOld(this);
      }
    }

    mFirstLaunch = false;

    setSearchBoxHint();
    timeTabSelected = SystemClock.elapsedRealtime();

    mP13nLogger.reset();
    mP13nRanker.refresh(
        new P13nRefreshCompleteListener() {
          @Override
          public void onP13nRefreshComplete() {
            // TODO: make zero-query search results visible
          }
        });
    Trace.endSection();
  }

  @Override
  protected void onRestart() {
    super.onRestart();
    mIsRestarting = true;
  }

  @Override
  protected void onPause() {
    if (mClearSearchOnPause) {
      hideDialpadAndSearchUi();
      mClearSearchOnPause = false;
    }
    if (mSlideOut.hasStarted() && !mSlideOut.hasEnded()) {
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
        mListsFragment.getCurrentTabIndex() == DialtactsPagerAdapter.TAB_INDEX_HISTORY;
    if (isOnHistoryTab
        && timeoutElapsed
        && !isChangingConfigurations()
        && !getSystemService(KeyguardManager.class).isKeyguardLocked()) {
      mListsFragment.markMissedCallsAsReadAndRemoveNotifications();
    }
    DialerUtils.getDefaultSharedPreferenceForDeviceProtectedStorageContext(this)
        .edit()
        .putInt(KEY_LAST_TAB, mListsFragment.getCurrentTabIndex())
        .apply();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(KEY_SEARCH_QUERY, mSearchQuery);
    outState.putBoolean(KEY_IN_REGULAR_SEARCH_UI, mInRegularSearch);
    outState.putBoolean(KEY_IN_DIALPAD_SEARCH_UI, mInDialpadSearch);
    outState.putBoolean(KEY_FIRST_LAUNCH, mFirstLaunch);
    outState.putBoolean(KEY_IS_DIALPAD_SHOWN, mIsDialpadShown);
    outState.putBoolean(KEY_WAS_CONFIGURATION_CHANGE, isChangingConfigurations());
    mActionBarController.saveInstanceState(outState);
    mStateSaved = true;
  }

  @Override
  public void onAttachFragment(final Fragment fragment) {
    LogUtil.d("DialtactsActivity.onAttachFragment", "fragment: %s", fragment);
    if (fragment instanceof DialpadFragment) {
      mDialpadFragment = (DialpadFragment) fragment;
      if (!mIsDialpadShown && !mShowDialpadOnResume) {
        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.hide(mDialpadFragment);
        transaction.commit();
      }
    } else if (fragment instanceof SmartDialSearchFragment) {
      mSmartDialSearchFragment = (SmartDialSearchFragment) fragment;
      mSmartDialSearchFragment.setOnPhoneNumberPickerActionListener(this);
      if (!TextUtils.isEmpty(mDialpadQuery)) {
        mSmartDialSearchFragment.setAddToContactNumber(mDialpadQuery);
      }
    } else if (fragment instanceof SearchFragment) {
      mRegularSearchFragment = (RegularSearchFragment) fragment;
      mRegularSearchFragment.setOnPhoneNumberPickerActionListener(this);
    } else if (fragment instanceof ListsFragment) {
      mListsFragment = (ListsFragment) fragment;
      mListsFragment.addOnPageChangeListener(this);
    } else if (fragment instanceof NewSearchFragment) {
      mNewSearchFragment = (NewSearchFragment) fragment;
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
              return mP13nRanker.rankCursor(data, queryString == null ? 0 : queryString.length());
            }
          });
      searchFragment.addOnLoadFinishedListener(
          new OnLoadFinishedListener() {
            @Override
            public void onLoadFinished() {
              mP13nLogger.onSearchQuery(
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

  @Override
  public void onClick(View view) {
    int resId = view.getId();
    if (resId == R.id.floating_action_button) {
      if (!mIsDialpadShown) {
        PerformanceReport.recordClick(UiAction.Type.OPEN_DIALPAD);
        mInCallDialpadUp = false;
        showDialpadFragment(true);
        PostCall.closePrompt();
      }
    } else if (resId == R.id.voice_search_button) {
      try {
        startActivityForResult(
            new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
            ACTIVITY_REQUEST_CODE_VOICE_SEARCH);
      } catch (ActivityNotFoundException e) {
        Toast.makeText(
                DialtactsActivity.this, R.string.voice_search_not_available, Toast.LENGTH_SHORT)
            .show();
      }
    } else if (resId == R.id.dialtacts_options_menu_button) {
      mOverflowMenu.show();
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
      MainComponent.get(this).getMain().createNewUiLauncherShortcut(this);
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
    if (requestCode == ACTIVITY_REQUEST_CODE_VOICE_SEARCH) {
      if (resultCode == RESULT_OK) {
        final ArrayList<String> matches =
            data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (matches.size() > 0) {
          mVoiceSearchQuery = matches.get(0);
        } else {
          LogUtil.i("DialtactsActivity.onActivityResult", "voice search - nothing heard");
        }
      } else {
        LogUtil.e("DialtactsActivity.onActivityResult", "voice search failed");
      }
    } else if (requestCode == ACTIVITY_REQUEST_CODE_CALL_COMPOSE) {
      if (resultCode == RESULT_FIRST_USER) {
        LogUtil.i(
            "DialtactsActivity.onActivityResult", "returned from call composer, error occurred");
        String message =
            getString(
                R.string.call_composer_connection_failed,
                data.getStringExtra(CallComposerActivity.KEY_CONTACT_NAME));
        Snackbar.make(mParentLayout, message, Snackbar.LENGTH_LONG).show();
      } else {
        LogUtil.i("DialtactsActivity.onActivityResult", "returned from call composer, no error");
      }
    } else if (requestCode == ACTIVITY_REQUEST_CODE_CALL_DETAILS) {
      if (resultCode == RESULT_OK
          && data != null
          && data.getBooleanExtra(CallDetailsActivity.EXTRA_HAS_ENRICHED_CALL_DATA, false)) {
        String number = data.getStringExtra(CallDetailsActivity.EXTRA_PHONE_NUMBER);
        int snackbarDurationMillis = 5_000;
        Snackbar.make(mParentLayout, getString(R.string.ec_data_deleted), snackbarDurationMillis)
            .setAction(
                R.string.view_conversation,
                v -> startActivity(IntentProvider.getSendSmsIntentProvider(number).getIntent(this)))
            .setActionTextColor(getResources().getColor(R.color.dialer_snackbar_action_text_color))
            .show();
      }
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  /**
   * Update the number of unread voicemails (potentially other tabs) displayed next to the tab icon.
   */
  public void updateTabUnreadCounts() {
    mListsFragment.updateTabUnreadCounts();
  }

  /**
   * Initiates a fragment transaction to show the dialpad fragment. Animations and other visual
   * updates are handled by a callback which is invoked after the dialpad fragment is shown.
   *
   * @see #onDialpadShown
   */
  private void showDialpadFragment(boolean animate) {
    LogUtil.d("DialtactActivity.showDialpadFragment", "animate: %b", animate);
    if (mIsDialpadShown || mStateSaved) {
      return;
    }
    mIsDialpadShown = true;

    mListsFragment.setUserVisibleHint(false);

    final FragmentTransaction ft = getFragmentManager().beginTransaction();
    if (mDialpadFragment == null) {
      mDialpadFragment = new DialpadFragment();
      ft.add(R.id.dialtacts_container, mDialpadFragment, TAG_DIALPAD_FRAGMENT);
    } else {
      ft.show(mDialpadFragment);
    }

    mDialpadFragment.setAnimate(animate);
    Logger.get(this).logScreenView(ScreenEvent.Type.DIALPAD, this);
    ft.commit();

    if (animate) {
      mFloatingActionButtonController.scaleOut();
      maybeEnterSearchUi();
    } else {
      mFloatingActionButtonController.setVisible(false);
      maybeEnterSearchUi();
    }
    mActionBarController.onDialpadUp();

    Assert.isNotNull(mListsFragment.getView()).animate().alpha(0).withLayer();

    //adjust the title, so the user will know where we're at when the activity start/resumes.
    setTitle(R.string.launcherDialpadActivityLabel);
  }

  /** Callback from child DialpadFragment when the dialpad is shown. */
  public void onDialpadShown() {
    LogUtil.d("DialtactsActivity.onDialpadShown", "");
    Assert.isNotNull(mDialpadFragment);
    if (mDialpadFragment.getAnimate()) {
      Assert.isNotNull(mDialpadFragment.getView()).startAnimation(mSlideIn);
    } else {
      mDialpadFragment.setYFraction(0);
    }

    updateSearchFragmentPosition();
  }

  /**
   * Initiates animations and other visual updates to hide the dialpad. The fragment is hidden in a
   * callback after the hide animation ends.
   *
   * @see #commitDialpadFragmentHide
   */
  public void hideDialpadFragment(boolean animate, boolean clearDialpad) {
    if (mDialpadFragment == null || mDialpadFragment.getView() == null) {
      return;
    }
    if (clearDialpad) {
      // Temporarily disable accessibility when we clear the dialpad, since it should be
      // invisible and should not announce anything.
      mDialpadFragment
          .getDigitsWidget()
          .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      mDialpadFragment.clearDialpad();
      mDialpadFragment
          .getDigitsWidget()
          .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }
    if (!mIsDialpadShown) {
      return;
    }
    mIsDialpadShown = false;
    mDialpadFragment.setAnimate(animate);
    mListsFragment.setUserVisibleHint(true);
    mListsFragment.sendScreenViewForCurrentPosition();

    updateSearchFragmentPosition();

    mFloatingActionButtonController.align(getFabAlignment(), animate);
    if (animate) {
      mDialpadFragment.getView().startAnimation(mSlideOut);
    } else {
      commitDialpadFragmentHide();
    }

    mActionBarController.onDialpadDown();

    if (isInSearchUi()) {
      if (TextUtils.isEmpty(mSearchQuery)) {
        exitSearchUi();
      }
    }
    //reset the title to normal.
    setTitle(R.string.launcherActivityLabel);
  }

  /** Finishes hiding the dialpad fragment after any animations are completed. */
  private void commitDialpadFragmentHide() {
    if (!mStateSaved
        && mDialpadFragment != null
        && !mDialpadFragment.isHidden()
        && !isDestroyed()) {
      final FragmentTransaction ft = getFragmentManager().beginTransaction();
      ft.hide(mDialpadFragment);
      ft.commit();
    }
    mFloatingActionButtonController.scaleIn(AnimUtils.NO_DELAY);
  }

  private void updateSearchFragmentPosition() {
    SearchFragment fragment = null;
    if (mSmartDialSearchFragment != null) {
      fragment = mSmartDialSearchFragment;
    } else if (mRegularSearchFragment != null) {
      fragment = mRegularSearchFragment;
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
    }
  }

  @Override
  public boolean isInSearchUi() {
    return mInDialpadSearch || mInRegularSearch;
  }

  @Override
  public boolean hasSearchQuery() {
    return !TextUtils.isEmpty(mSearchQuery);
  }

  private void setNotInSearchUi() {
    mInDialpadSearch = false;
    mInRegularSearch = false;
  }

  private void hideDialpadAndSearchUi() {
    if (mIsDialpadShown) {
      hideDialpadFragment(false, true);
    } else {
      exitSearchUi();
    }
  }

  private void prepareVoiceSearchButton() {
    final Intent voiceIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    if (canIntentBeHandled(voiceIntent)) {
      mVoiceSearchButton.setVisibility(View.VISIBLE);
      mVoiceSearchButton.setOnClickListener(this);
    } else {
      mVoiceSearchButton.setVisibility(View.GONE);
    }
  }

  public boolean isNearbyPlacesSearchEnabled() {
    return false;
  }

  protected int getSearchBoxHint() {
    return R.string.dialer_hint_find_contact;
  }

  /** Sets the hint text for the contacts search box */
  private void setSearchBoxHint() {
    SearchEditTextLayout searchEditTextLayout =
        (SearchEditTextLayout)
            getActionBarSafely().getCustomView().findViewById(R.id.search_view_container);
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
    if (mPendingSearchViewQuery != null) {
      mSearchView.setText(mPendingSearchViewQuery);
      mPendingSearchViewQuery = null;
    }
    if (mActionBarController != null) {
      mActionBarController.restoreActionBarOffset();
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

    final boolean showDialpadChooser =
        !ACTION_SHOW_TAB.equals(intent.getAction())
            && phoneIsInUse()
            && !DialpadFragment.isAddCallMode(intent);
    if (showDialpadChooser || (intent.getData() != null && isDialIntent(intent))) {
      showDialpadFragment(false);
      mDialpadFragment.setStartedFromNewIntent(true);
      if (showDialpadChooser && !mDialpadFragment.isVisible()) {
        mInCallDialpadUp = true;
      }
    } else if (isLastTabEnabled) {
      @TabIndex
      int tabIndex =
          DialerUtils.getDefaultSharedPreferenceForDeviceProtectedStorageContext(this)
              .getInt(KEY_LAST_TAB, DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL);
      // If voicemail tab is saved and its availability changes, we still move to the voicemail tab
      // but it is quickly removed and shown the contacts tab.
      if (mListsFragment != null) {
        mListsFragment.showTab(tabIndex);
        PerformanceReport.setStartingTabIndex(tabIndex);
      } else {
        PerformanceReport.setStartingTabIndex(DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL);
      }
    }
  }

  @Override
  public void onNewIntent(Intent newIntent) {
    setIntent(newIntent);
    mFirstLaunch = true;

    mStateSaved = false;
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
    if (mStateSaved || getFragmentManager().isDestroyed()) {
      // Weird race condition where fragment is doing work after the activity is destroyed
      // due to talkback being on (b/10209937). Just return since we can't do any
      // constructive here.
      return;
    }

    if (DEBUG) {
      LogUtil.v("DialtactsActivity.enterSearchUi", "smart dial " + smartDialSearch);
    }

    final FragmentTransaction transaction = getFragmentManager().beginTransaction();
    if (mInDialpadSearch && mSmartDialSearchFragment != null) {
      transaction.remove(mSmartDialSearchFragment);
    } else if (mInRegularSearch && mRegularSearchFragment != null) {
      transaction.remove(mRegularSearchFragment);
    }

    final String tag;
    boolean useNewSearch =
        ConfigProviderBindings.get(this).getBoolean("enable_new_search_fragment", false);
    if (useNewSearch) {
      tag = TAG_NEW_SEARCH_FRAGMENT;
    } else if (smartDialSearch) {
      tag = TAG_SMARTDIAL_SEARCH_FRAGMENT;
    } else {
      tag = TAG_REGULAR_SEARCH_FRAGMENT;
    }
    mInDialpadSearch = smartDialSearch;
    mInRegularSearch = !smartDialSearch;

    mFloatingActionButtonController.scaleOut();

    if (animate) {
      transaction.setCustomAnimations(android.R.animator.fade_in, 0);
    } else {
      transaction.setTransition(FragmentTransaction.TRANSIT_NONE);
    }

    Fragment fragment = getFragmentManager().findFragmentByTag(tag);
    if (fragment == null) {
      if (useNewSearch) {
        fragment = new NewSearchFragment();
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
      // TODO: if this is a transition from dialpad to searchbar, animate fragment
      // down, and vice versa. Perhaps just add a coordinator behavior with the search bar.
      transaction.show(fragment);
    }

    // DialtactsActivity will provide the options menu
    fragment.setHasOptionsMenu(false);

    // Will show empty list if P13nRanker is not enabled. Else, re-ranked list by the ranker.
    if (!useNewSearch) {
      ((SearchFragment) fragment)
          .setShowEmptyListForNullQuery(mP13nRanker.shouldShowEmptyListForNullQuery());
    } else {
      // TODO: add p13n ranker to new search.
    }

    if (!smartDialSearch && !useNewSearch) {
      ((SearchFragment) fragment).setQueryString(query);
    } else if (useNewSearch) {
      ((NewSearchFragment) fragment).setQuery(query);
    }
    transaction.commit();

    if (animate) {
      Assert.isNotNull(mListsFragment.getView()).animate().alpha(0).withLayer();
    }
    mListsFragment.setUserVisibleHint(false);

    if (smartDialSearch) {
      Logger.get(this).logScreenView(ScreenEvent.Type.SMART_DIAL_SEARCH, this);
    } else {
      Logger.get(this).logScreenView(ScreenEvent.Type.REGULAR_SEARCH, this);
    }
  }

  /** Hides the search fragment */
  private void exitSearchUi() {
    // See related bug in enterSearchUI();
    if (getFragmentManager().isDestroyed() || mStateSaved) {
      return;
    }

    mSearchView.setText(null);

    if (mDialpadFragment != null) {
      mDialpadFragment.clearDialpad();
    }

    setNotInSearchUi();

    // Restore the FAB for the lists fragment.
    if (getFabAlignment() != FloatingActionButtonController.ALIGN_END) {
      mFloatingActionButtonController.setVisible(false);
    }
    mFloatingActionButtonController.scaleIn(FAB_SCALE_IN_DELAY_MS);
    onPageScrolled(mListsFragment.getCurrentTabIndex(), 0 /* offset */, 0 /* pixelOffset */);
    onPageSelected(mListsFragment.getCurrentTabIndex());

    final FragmentTransaction transaction = getFragmentManager().beginTransaction();
    if (mSmartDialSearchFragment != null) {
      transaction.remove(mSmartDialSearchFragment);
    }
    if (mRegularSearchFragment != null) {
      transaction.remove(mRegularSearchFragment);
    }
    if (mNewSearchFragment != null) {
      transaction.remove(mNewSearchFragment);
    }
    transaction.commit();

    Assert.isNotNull(mListsFragment.getView()).animate().alpha(1).withLayer();

    if (mDialpadFragment == null || !mDialpadFragment.isVisible()) {
      // If the dialpad fragment wasn't previously visible, then send a screen view because
      // we are exiting regular search. Otherwise, the screen view will be sent by
      // {@link #hideDialpadFragment}.
      mListsFragment.sendScreenViewForCurrentPosition();
      mListsFragment.setUserVisibleHint(true);
    }

    mActionBarController.onSearchUiExited();
  }

  @Override
  public void onBackPressed() {
    PerformanceReport.recordClick(UiAction.Type.PRESS_ANDROID_BACK_BUTTON);

    if (mStateSaved) {
      return;
    }
    if (mIsDialpadShown) {
      if (TextUtils.isEmpty(mSearchQuery)
          || (mSmartDialSearchFragment != null
              && mSmartDialSearchFragment.isVisible()
              && mSmartDialSearchFragment.getAdapter().getCount() == 0)) {
        exitSearchUi();
      }
      hideDialpadFragment(true, false);
    } else if (isInSearchUi()) {
      exitSearchUi();
      DialerUtils.hideInputMethod(mParentLayout);
    } else {
      super.onBackPressed();
    }
  }

  private void maybeEnterSearchUi() {
    if (!isInSearchUi()) {
      enterSearchUi(true /* isSmartDial */, mSearchQuery, false);
    }
  }

  /** @return True if the search UI was exited, false otherwise */
  private boolean maybeExitSearchUi() {
    if (isInSearchUi() && TextUtils.isEmpty(mSearchQuery)) {
      exitSearchUi();
      DialerUtils.hideInputMethod(mParentLayout);
      return true;
    }
    return false;
  }

  private void showFabInSearchUi() {
    mFloatingActionButtonController.changeIcon(
        getResources().getDrawable(R.drawable.quantum_ic_dialpad_white_24, null),
        getResources().getString(R.string.action_menu_dialpad_button));
    mFloatingActionButtonController.align(getFabAlignment(), false /* animate */);
    mFloatingActionButtonController.scaleIn(FAB_SCALE_IN_DELAY_MS);
  }

  @Override
  public void onDialpadQueryChanged(String query) {
    mDialpadQuery = query;
    if (mSmartDialSearchFragment != null) {
      mSmartDialSearchFragment.setAddToContactNumber(query);
    }
    final String normalizedQuery =
        SmartDialNameMatcher.normalizeNumber(query, SmartDialPrefix.getMap());

    if (!TextUtils.equals(mSearchView.getText(), normalizedQuery)) {
      if (DEBUG) {
        LogUtil.v("DialtactsActivity.onDialpadQueryChanged", "new query: " + query);
      }
      if (mDialpadFragment == null || !mDialpadFragment.isVisible()) {
        // This callback can happen if the dialpad fragment is recreated because of
        // activity destruction. In that case, don't update the search view because
        // that would bring the user back to the search fragment regardless of the
        // previous state of the application. Instead, just return here and let the
        // fragment manager correctly figure out whatever fragment was last displayed.
        if (!TextUtils.isEmpty(normalizedQuery)) {
          mPendingSearchViewQuery = normalizedQuery;
        }
        return;
      }
      mSearchView.setText(normalizedQuery);
    }

    try {
      if (mDialpadFragment != null && mDialpadFragment.isVisible()) {
        mDialpadFragment.process_quote_emergency_unquote(normalizedQuery);
      }
    } catch (Exception ignored) {
      // Skip any exceptions for this piece of code
    }
  }

  @Override
  public boolean onDialpadSpacerTouchWithEmptyQuery() {
    if (mInDialpadSearch
        && mSmartDialSearchFragment != null
        && !mSmartDialSearchFragment.isShowingPermissionRequest()) {
      PerformanceReport.recordClick(UiAction.Type.CLOSE_DIALPAD);
      hideDialpadFragment(true /* animate */, true /* clearDialpad */);
      return true;
    }
    return false;
  }

  @Override
  public void onListFragmentScrollStateChange(int scrollState) {
    PerformanceReport.recordScrollStateChange(scrollState);
    if (scrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
      hideDialpadFragment(true, false);
      DialerUtils.hideInputMethod(mParentLayout);
    }
  }

  @Override
  public void onListFragmentScroll(int firstVisibleItem, int visibleItemCount, int totalItemCount) {
    // TODO: No-op for now. This should eventually show/hide the actionBar based on
    // interactions with the ListsFragments.
  }

  private boolean phoneIsInUse() {
    return TelecomUtil.isInCall(this);
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
    mListsFragment.showRemoveView(true);
  }

  @Override
  public void onDragHovered(int x, int y, PhoneFavoriteSquareTileView view) {}

  /** Called when the user has released a contact tile after long-pressing it. */
  @Override
  public void onDragFinished(int x, int y) {
    mListsFragment.showRemoveView(false);
  }

  @Override
  public void onDroppedOnRemove() {}

  /**
   * Allows the SpeedDialFragment to attach the drag controller to mRemoveViewContainer once it has
   * been attached to the activity.
   */
  @Override
  public void setDragDropController(DragDropController dragController) {
    mDragDropController = dragController;
    mListsFragment.getRemoveView().setDragDropController(dragController);
  }

  /** Implemented to satisfy {@link OldSpeedDialFragment.HostInterface} */
  @Override
  public void showAllContactsTab() {
    if (mListsFragment != null) {
      mListsFragment.showTab(DialtactsPagerAdapter.TAB_INDEX_ALL_CONTACTS);
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
      mFloatingActionButtonController.setVisible(enabled);
    }
  }

  @Override
  public void onPickDataUri(
      Uri dataUri, boolean isVideoCall, CallSpecificAppData callSpecificAppData) {
    mClearSearchOnPause = true;
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

    Intent intent =
        new CallIntentBuilder(phoneNumber, callSpecificAppData).setIsVideoCall(isVideoCall).build();

    DialerUtils.startActivityWithErrorToast(this, intent);
    mClearSearchOnPause = true;
  }

  @Override
  public void onHomeInActionBarSelected() {
    exitSearchUi();
  }

  @Override
  public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    int tabIndex = mListsFragment.getCurrentTabIndex();

    // Scroll the button from center to end when moving from the Speed Dial to Call History tab.
    // In RTL, scroll when the current tab is Call History instead, since the order of the tabs
    // is reversed and the ViewPager returns the left tab position during scroll.
    boolean isRtl = ViewUtil.isRtl();
    if (!isRtl && tabIndex == DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL && !mIsLandscape) {
      mFloatingActionButtonController.onPageScrolled(positionOffset);
    } else if (isRtl && tabIndex == DialtactsPagerAdapter.TAB_INDEX_HISTORY && !mIsLandscape) {
      mFloatingActionButtonController.onPageScrolled(1 - positionOffset);
    } else if (tabIndex != DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL) {
      mFloatingActionButtonController.onPageScrolled(1);
    }
  }

  @Override
  public void onPageSelected(int position) {
    updateMissedCalls();
    int tabIndex = mListsFragment.getCurrentTabIndex();
    mPreviouslySelectedTabIndex = tabIndex;
    mFloatingActionButtonController.setVisible(true);
    timeTabSelected = SystemClock.elapsedRealtime();
  }

  @Override
  public void onPageScrollStateChanged(int state) {}

  @Override
  public boolean isActionBarShowing() {
    return mActionBarController.isActionBarShowing();
  }

  @Override
  public boolean isDialpadShown() {
    return mIsDialpadShown;
  }

  @Override
  public int getDialpadHeight() {
    if (mDialpadFragment != null) {
      return mDialpadFragment.getDialpadHeight();
    }
    return 0;
  }

  @Override
  public void setActionBarHideOffset(int offset) {
    getActionBarSafely().setHideOffset(offset);
  }

  @Override
  public int getActionBarHeight() {
    return mActionBarHeight;
  }

  private int getFabAlignment() {
    if (!mIsLandscape
        && !isInSearchUi()
        && mListsFragment.getCurrentTabIndex() == DialtactsPagerAdapter.TAB_INDEX_SPEED_DIAL) {
      return FloatingActionButtonController.ALIGN_MIDDLE;
    }
    return FloatingActionButtonController.ALIGN_END;
  }

  private void updateMissedCalls() {
    if (mPreviouslySelectedTabIndex == DialtactsPagerAdapter.TAB_INDEX_HISTORY) {
      mListsFragment.markMissedCallsAsReadAndRemoveNotifications();
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
              && mListsFragment != null
              && mListsFragment.hasFrequents());

      menu.findItem(R.id.menu_history)
          .setVisible(PermissionsUtil.hasPhonePermissions(DialtactsActivity.this));

      Context context = DialtactsActivity.this.getApplicationContext();
      MenuItem simulatorMenuItem = menu.findItem(R.id.menu_simulator_submenu);
      Simulator simulator = SimulatorComponent.get(context).getSimulator();
      if (simulator.shouldShow()) {
        simulatorMenuItem.setVisible(true);
        simulatorMenuItem.setActionProvider(simulator.getActionProvider(context));
      } else {
        simulatorMenuItem.setVisible(false);
      }

      Main dialtacts = MainComponent.get(context).getMain();
      menu.findItem(R.id.menu_new_ui_launcher_shortcut)
          .setVisible(dialtacts.isNewUiEnabled(context));

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
        mDragDropController.handleDragHovered(v, (int) event.getX(), (int) event.getY());
      }
      return true;
    }
  }
}
