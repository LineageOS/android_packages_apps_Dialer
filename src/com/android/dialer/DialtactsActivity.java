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

package com.android.dialer;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Trace;
import android.provider.CallLog.Calls;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.speech.RecognizerIntent;
import android.support.v4.view.ViewPager;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnDragListener;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.Toast;

import com.android.contacts.common.activity.TransactionSafeActivity;
import com.android.contacts.common.dialog.ClearFrequentsDialog;
import com.android.contacts.common.interactions.ImportExportDialogFragment;
import com.android.contacts.common.interactions.TouchPointManager;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.contacts.common.widget.FloatingActionButtonController;
import com.android.contacts.commonbind.analytics.AnalyticsUtil;
import com.android.dialer.calllog.CallLogActivity;
import com.android.dialer.calllog.CallLogFragment;
import com.android.dialer.database.DialerDatabaseHelper;
import com.android.dialer.dialpad.DialpadFragment;
import com.android.dialer.dialpad.SmartDialNameMatcher;
import com.android.dialer.dialpad.SmartDialPrefix;
import com.android.dialer.incall.CallMethodSpinnerHelper;
import com.android.dialer.interactions.PhoneNumberInteraction;
import com.android.dialer.list.DragDropController;
import com.android.dialer.list.ListsFragment;
import com.android.dialer.list.OnDragDropListener;
import com.android.dialer.list.OnListFragmentScrolledListener;
import com.android.dialer.list.PhoneFavoriteSquareTileView;
import com.android.dialer.list.RegularSearchFragment;
import com.android.dialer.list.SearchFragment;
import com.android.dialer.list.SmartDialSearchFragment;
import com.android.dialer.list.SpeedDialFragment;
import com.android.dialer.settings.DialerSettingsActivity;
import com.android.dialer.util.IntentUtil;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.widget.ActionBarController;
import com.android.dialer.widget.SearchEditTextLayout;
import com.android.dialerbind.DatabaseHelperManager;
import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.common.animation.AnimUtils;
import com.android.phone.common.ambient.AmbientConnection;
import com.android.phone.common.incall.CreditBarHelper;
import com.android.phone.common.incall.CallMethodInfo;
import com.android.phone.common.incall.CallMethodHelper;
import com.android.phone.common.incall.CallMethodSpinnerAdapter;
import com.android.phone.common.incall.CallMethodUtils;
import com.android.phone.common.util.SettingsUtil;
import com.android.phone.common.animation.AnimationListenerAdapter;

import com.cyanogen.ambient.incall.InCallServices;
import com.cyanogen.ambient.incall.extension.StartCallRequest;
import com.cyanogen.ambient.incall.extension.OriginCodes;

import com.android.phone.common.util.StartInCallCallReceiver;
import junit.framework.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * The dialer tab's title is 'phone', a more common name (see strings.xml).
 */
public class DialtactsActivity extends TransactionSafeActivity implements View.OnClickListener,
        DialpadFragment.OnDialpadQueryChangedListener,
        CallMethodSpinnerHelper.OnCallMethodChangedListener,
        OnListFragmentScrolledListener,
        CallLogFragment.HostInterface,
        DialpadFragment.HostInterface,
        ListsFragment.HostInterface,
        SpeedDialFragment.HostInterface,
        SearchFragment.HostInterface,
        OnDragDropListener,
        OnPhoneNumberPickerActionListener,
        PopupMenu.OnMenuItemClickListener,
        ViewPager.OnPageChangeListener,
        ActionBarController.ActivityUi {
    private static final String TAG = "DialtactsActivity";

    public static final boolean DEBUG = false;

    public static final String SHARED_PREFS_NAME = "com.android.dialer_preferences";
    private static final String PREF_LAST_T9_LOCALE = "smart_dial_prefix_last_t9_locale";

    /** @see #getCallOrigin() */
    private static final String CALL_ORIGIN_DIALTACTS =
            "com.android.dialer.DialtactsActivity";

    private static final String KEY_IN_REGULAR_SEARCH_UI = "in_regular_search_ui";
    private static final String KEY_IN_DIALPAD_SEARCH_UI = "in_dialpad_search_ui";
    private static final String KEY_SEARCH_QUERY = "search_query";
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    private static final String KEY_IS_DIALPAD_SHOWN = "is_dialpad_shown";
    private static final String KEY_CALL_METHOD_COMPONENT = "call_method_component";

    private static final String TAG_DIALPAD_FRAGMENT = "dialpad";
    private static final String TAG_REGULAR_SEARCH_FRAGMENT = "search";
    private static final String TAG_SMARTDIAL_SEARCH_FRAGMENT = "smartdial";
    private static final String TAG_FAVORITES_FRAGMENT = "favorites";

    /**
     * Just for backward compatibility. Should behave as same as {@link Intent#ACTION_DIAL}.
     */
    private static final String ACTION_TOUCH_DIALER = "com.android.phone.action.TOUCH_DIALER";
    public static final String EXTRA_SHOW_TAB = "EXTRA_SHOW_TAB";

    private static final int ACTIVITY_REQUEST_CODE_VOICE_SEARCH = 1;

    private static final int FAB_SCALE_IN_DELAY_MS = 300;

    private FrameLayout mParentLayout;

    private FrameLayout mGlobalCreditsBar;

    /**
     * Fragment containing the dialpad that slides into view
     */
    protected DialpadFragment mDialpadFragment;

    /**
     * Fragment for searching phone numbers using the alphanumeric keyboard.
     */
    private RegularSearchFragment mRegularSearchFragment;

    /**
     * Fragment for searching phone numbers using the dialpad.
     */
    private SmartDialSearchFragment mSmartDialSearchFragment;

    /**
     * Animation that slides in.
     */
    private Animation mSlideIn;

    /**
     * Animation that slides out.
     */
    private Animation mSlideOut;

    HashMap<ComponentName, CallMethodInfo> mAvailableProviders = new HashMap<>();

    AnimationListenerAdapter mSlideInListener = new AnimationListenerAdapter() {
        @Override
        public void onAnimationEnd(Animation animation) {
            maybeEnterSearchUi();
        }
    };

    /**
     * Listener for after slide out animation completes on dialer fragment.
     */
    AnimationListenerAdapter mSlideOutListener = new AnimationListenerAdapter() {
        @Override
        public void onAnimationEnd(Animation animation) {
            commitDialpadFragmentHide();
        }
    };

    /**
     * Fragment containing the speed dial list, recents list, and all contacts list.
     */
    private ListsFragment mListsFragment;

    /**
     * Tracks whether onSaveInstanceState has been called. If true, no fragment transactions can
     * be commited.
     */
    private boolean mStateSaved;
    private boolean mIsRestarting;
    private boolean mInDialpadSearch;
    private boolean mInRegularSearch;
    private boolean mClearSearchOnPause;
    private boolean mIsDialpadShown;
    private boolean mShowDialpadOnResume;

    /**
     * Whether or not the device is in landscape orientation.
     */
    private boolean mIsLandscape;

    /**
     * True if the dialpad is only temporarily showing due to being in call
     */
    private boolean mInCallDialpadUp;

    /**
     * True when this activity has been launched for the first time.
     */
    private boolean mFirstLaunch;

    /**
     * Search query to be applied to the SearchView in the ActionBar once
     * onCreateOptionsMenu has been called.
     */
    private String mPendingSearchViewQuery;

    private PopupMenu mOverflowMenu;
    private EditText mSearchView;
    private View mVoiceSearchButton;

    private String mLastKnownCallMethod;

    private String mSearchQuery;

    private DialerDatabaseHelper mDialerDatabaseHelper;
    private DragDropController mDragDropController;
    private ActionBarController mActionBarController;

    private FloatingActionButtonController mFloatingActionButtonController;

    private int mActionBarHeight;

    /**
     * The text returned from a voice search query.  Set in {@link #onActivityResult} and used in
     * {@link #onResume()} to populate the search box.
     */
    private String mVoiceSearchQuery;

    private CallMethodInfo mCurrentCallMethod;
    private String AMBIENT_SUBSCRIPTION_ID = "DialtactsActivity";

    private CallMethodHelper.CallMethodReceiver pluginsUpdatedReceiver =
            new CallMethodHelper.CallMethodReceiver() {
                @Override
                public void onChanged(HashMap<ComponentName, CallMethodInfo> callMethodInfos) {
                    providersUpdated(callMethodInfos);
                }
            };

    @Override
    public void onCallMethodChangedListener(CallMethodInfo cmi) {
        if (cmi == null) {
            Log.e(TAG, "Somehow our CallMethod is Null. This should never happen.",
                    new Exception());
            return;
        }
        mLastKnownCallMethod = CallMethodSpinnerAdapter.getCallMethodKey(cmi);
        if (mCurrentCallMethod == null || !cmi.equals(mCurrentCallMethod)) {
            mCurrentCallMethod = cmi;
            if (mSearchEditTextLayout != null) {
                mSearchEditTextLayout.setCurrentCallMethod(mCurrentCallMethod);
            }
            if (mDialpadFragment != null) {
                mDialpadFragment.setCurrentCallMethod(mCurrentCallMethod);
                if (mIsDialpadShown) {
                    mDialpadFragment.onCallMethodChanged(mCurrentCallMethod);
                }
            }
            if (mSmartDialSearchFragment != null && mSmartDialSearchFragment.isVisible()) {
                mSmartDialSearchFragment.setCurrentCallMethod(mCurrentCallMethod);
            } else if (mRegularSearchFragment != null && mRegularSearchFragment.isVisible()) {
                mRegularSearchFragment.setCurrentCallMethod(mCurrentCallMethod);
            }
        }
    }

    public CallMethodInfo getCurrentCallMethod() {
        return mCurrentCallMethod;
    }

    BroadcastReceiver mCallStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ConnectivityManager.CONNECTIVITY_ACTION:
                    if (mSmartDialSearchFragment != null) {
                        mSmartDialSearchFragment.setupEmptyView();
                    }
                    break;
                case TelephonyIntents.ACTION_SUBINFO_CONTENT_CHANGE:
                case TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED:
                    if (mSearchEditTextLayout != null) {
                        mSearchEditTextLayout.updateSpinner(mLastKnownCallMethod,
                                mAvailableProviders);
                    }
                    if (mDialpadFragment != null) {
                        mDialpadFragment.updateSpinner(mLastKnownCallMethod, mAvailableProviders);
                    }
                    break;
            }
        }
    };

    private void providersUpdated(HashMap<ComponentName, CallMethodInfo> availableCallMethods) {
        // We don't want to update this until we know that our providers are loaded. otherwise
        // we may miss some data.
        updateSmartDialDatabase();
        mAvailableProviders.clear();
        CallMethodHelper.removeDisabled(availableCallMethods, mAvailableProviders);
        if (mSmartDialSearchFragment != null) {
            mSmartDialSearchFragment.setAvailableProviders(mAvailableProviders);
        }

        // Check to see if the current call method should be updated with new data
        if (mCurrentCallMethod != null) {
            if (availableCallMethods.containsKey(mCurrentCallMethod.mComponent)) {
                mCurrentCallMethod = availableCallMethods.get(mCurrentCallMethod.mComponent);
            }
            if (mDialpadFragment != null && mIsDialpadShown) {
                // ensure providers are updated
                mDialpadFragment.providersUpdated(mLastKnownCallMethod, mAvailableProviders);
            }
        }

        mSearchEditTextLayout.updateSpinner(mLastKnownCallMethod, mAvailableProviders);
    }

    public String getLastKnownCallMethod() {
        return mLastKnownCallMethod;
    }

    protected class OptionsPopupMenu extends PopupMenu {
        public OptionsPopupMenu(Context context, View anchor) {
            super(context, anchor, Gravity.END);
        }

        @Override
        public void show() {
            final boolean hasContactsPermission =
                    PermissionsUtil.hasContactsPermissions(DialtactsActivity.this);
            final Menu menu = getMenu();
            final MenuItem clearFrequents = menu.findItem(R.id.menu_clear_frequents);
            clearFrequents.setVisible(mListsFragment != null &&
                    mListsFragment.getSpeedDialFragment() != null &&
                    mListsFragment.getSpeedDialFragment().hasFrequents() && hasContactsPermission);

            menu.findItem(R.id.menu_import_export).setVisible(hasContactsPermission);
            menu.findItem(R.id.menu_add_contact).setVisible(hasContactsPermission);

            menu.findItem(R.id.menu_history).setVisible(
                    PermissionsUtil.hasPhonePermissions(DialtactsActivity.this));
            super.show();
        }
    }

    /**
     * Listener that listens to drag events and sends their x and y coordinates to a
     * {@link DragDropController}.
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

    /**
     * Listener used to send search queries to the phone search fragment.
     */
    private final TextWatcher mPhoneSearchQueryTextListener = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            final String newText = s.toString();
            if (newText.equals(mSearchQuery)) {
                // If the query hasn't changed (perhaps due to activity being destroyed
                // and restored, or user launching the same DIAL intent twice), then there is
                // no need to do anything here.
                return;
            }
            if (DEBUG) {
                Log.d(TAG, "onTextChange for mSearchView called with new query: " + newText);
                Log.d(TAG, "Previous Query: " + mSearchQuery);
            }
            mSearchQuery = newText;

            // Show search fragment only when the query string is changed to non-empty text.
            if (!TextUtils.isEmpty(newText)) {
                // Call enterSearchUi only if we are switching search modes, or showing a search
                // fragment for the first time.
                final boolean sameSearchMode = (mIsDialpadShown && mInDialpadSearch) ||
                        (!mIsDialpadShown && mInRegularSearch);
                if (!sameSearchMode) {
                    enterSearchUi(mIsDialpadShown, mSearchQuery, true /* animate */);
                }
            }

            if (mSmartDialSearchFragment != null && mSmartDialSearchFragment.isVisible()) {
                mSmartDialSearchFragment.setQueryString(mSearchQuery, false /* delaySelection */);
            } else if (mRegularSearchFragment != null && mRegularSearchFragment.isVisible()) {
                if (mCurrentCallMethod != null) {
                    mRegularSearchFragment.setCurrentCallMethod(mCurrentCallMethod);
                }
                mRegularSearchFragment.setQueryString(mSearchQuery, false /* delaySelection */);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };


    /**
     * Open the search UI when the user clicks on the search box.
     */
    private final View.OnClickListener mSearchViewOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!isInSearchUi()) {
                mActionBarController.onSearchBoxTapped();
                enterSearchUi(false /* smartDialSearch */, mSearchView.getText().toString(),
                        true /* animate */);
            }
        }
    };

    SearchEditTextLayout mSearchEditTextLayout;

    /**
     * Handles the user closing the soft keyboard.
     */
    private final View.OnKeyListener mSearchEditTextLayoutListener = new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
                if (TextUtils.isEmpty(mSearchView.getText().toString())) {
                    // If the search term is empty, close the search UI.
                    maybeExitSearchUi();
                } else {
                    // If the search term is not empty, show the dialpad fab.
                    showFabInSearchUi();
                }
            }
            return false;
        }
    };

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

        final Resources resources = getResources();
        mActionBarHeight = resources.getDimensionPixelSize(R.dimen.action_bar_height_large);

        Trace.beginSection(TAG + " setContentView");
        setContentView(R.layout.dialtacts_activity);
        Trace.endSection();
        getWindow().setBackgroundDrawable(null);

        Trace.beginSection(TAG + " setup Views");
        final ActionBar actionBar = getActionBar();
        actionBar.setCustomView(R.layout.search_edittext);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setBackgroundDrawable(null);

        mSearchEditTextLayout =
                (SearchEditTextLayout) actionBar.getCustomView().findViewById(R.id.search_view_container);
        mSearchEditTextLayout.setPreImeKeyListener(mSearchEditTextLayoutListener);
        mSearchEditTextLayout.setCallMethodChangedListener(this);

        mActionBarController = new ActionBarController(this, mSearchEditTextLayout);

        mSearchView = (EditText) mSearchEditTextLayout.findViewById(R.id.search_view);
        mSearchView.addTextChangedListener(mPhoneSearchQueryTextListener);
        mVoiceSearchButton = mSearchEditTextLayout.findViewById(R.id.voice_search_button);
        mSearchEditTextLayout.findViewById(R.id.search_magnifying_glass)
                .setOnClickListener(mSearchViewOnClickListener);
        mSearchEditTextLayout.findViewById(R.id.search_box_start_search)
                .setOnClickListener(mSearchViewOnClickListener);
        mSearchEditTextLayout.setOnClickListener(mSearchViewOnClickListener);
        mSearchEditTextLayout.setCallback(new SearchEditTextLayout.Callback() {
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

        mIsLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        final View floatingActionButtonContainer = findViewById(
                R.id.floating_action_button_container);
        ImageButton floatingActionButton = (ImageButton) findViewById(R.id.floating_action_button);
        floatingActionButton.setOnClickListener(this);
        mFloatingActionButtonController = new FloatingActionButtonController(this,
                floatingActionButtonContainer, floatingActionButton);

        View optionsMenuButton =
                mSearchEditTextLayout.findViewById(R.id.dialtacts_options_menu_button);
        optionsMenuButton.setOnClickListener(this);
        mOverflowMenu = buildOptionsMenu(mSearchEditTextLayout);
        optionsMenuButton.setOnTouchListener(mOverflowMenu.getDragToOpenListener());

        // Add the favorites fragment but only if savedInstanceState is null. Otherwise the
        // fragment manager is responsible for recreating it.
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.dialtacts_frame, new ListsFragment(), TAG_FAVORITES_FRAGMENT)
                    .commit();
        } else {
            mSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY);
            mInRegularSearch = savedInstanceState.getBoolean(KEY_IN_REGULAR_SEARCH_UI);
            mInDialpadSearch = savedInstanceState.getBoolean(KEY_IN_DIALPAD_SEARCH_UI);
            mFirstLaunch = savedInstanceState.getBoolean(KEY_FIRST_LAUNCH);
            mShowDialpadOnResume = savedInstanceState.getBoolean(KEY_IS_DIALPAD_SHOWN);
            mLastKnownCallMethod = savedInstanceState.getString(KEY_CALL_METHOD_COMPONENT);
            mActionBarController.restoreInstanceState(savedInstanceState);
        }

        final boolean isLayoutRtl = DialerUtils.isRtl();
        if (mIsLandscape) {
            mSlideIn = AnimationUtils.loadAnimation(this,
                    isLayoutRtl ? R.anim.dialpad_slide_in_left : R.anim.dialpad_slide_in_right);
            mSlideOut = AnimationUtils.loadAnimation(this,
                    isLayoutRtl ? R.anim.dialpad_slide_out_left : R.anim.dialpad_slide_out_right);
        } else {
            mSlideIn = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_in_bottom);
            mSlideOut = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_out_bottom);
        }

        mSlideIn.setInterpolator(AnimUtils.EASE_IN);
        mSlideOut.setInterpolator(AnimUtils.EASE_OUT);

        mSlideIn.setAnimationListener(mSlideInListener);
        mSlideOut.setAnimationListener(mSlideOutListener);

        mParentLayout = (FrameLayout) findViewById(R.id.dialtacts_mainlayout);
        mParentLayout.setOnDragListener(new LayoutOnDragListener());
        floatingActionButtonContainer.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        final ViewTreeObserver observer =
                                floatingActionButtonContainer.getViewTreeObserver();
                        if (!observer.isAlive()) {
                            return;
                        }
                        observer.removeOnGlobalLayoutListener(this);
                        int screenWidth = mParentLayout.getWidth();
                        mFloatingActionButtonController.setScreenWidth(screenWidth);
                        mFloatingActionButtonController.align(
                                getFabAlignment(), false /* animate */);
                    }
                });

        mGlobalCreditsBar = (FrameLayout) mParentLayout.findViewById(R.id.ild_global_container);

        Trace.endSection();

        Trace.beginSection(TAG + " initialize smart dialing");
        mDialerDatabaseHelper = DatabaseHelperManager.getDatabaseHelper(this);
        Trace.endSection();
        Trace.endSection();

        IntentFilter callStateIntentFilter = new IntentFilter();
        callStateIntentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        callStateIntentFilter.addAction(TelephonyIntents.ACTION_SUBINFO_CONTENT_CHANGE);
        callStateIntentFilter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        registerReceiver(mCallStateReceiver, callStateIntentFilter);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // make this call on resume in case user changed t9 locale in settings
        SmartDialPrefix.initializeNanpSettings(this);
    }

    private void updateSmartDialDatabase() {
        // if locale has changed since last time, refresh the smart dial db
        Locale locale = SettingsUtil.getT9SearchInputLocale(this);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String prevLocale = prefs.getString(PREF_LAST_T9_LOCALE, null);

        if (!TextUtils.equals(locale.toString(), prevLocale)) {
            mDialerDatabaseHelper.recreateSmartDialDatabaseInBackground();
            if (mDialpadFragment != null)
                mDialpadFragment.refreshKeypad();

            prefs.edit().putString(PREF_LAST_T9_LOCALE, locale.toString()).apply();
        }
        else {
            mDialerDatabaseHelper.startSmartDialUpdateThread();
        }
    }

    @Override
    protected void onResume() {
        Trace.beginSection(TAG + " onResume");
        super.onResume();

        if (CallMethodHelper.subscribe(AMBIENT_SUBSCRIPTION_ID, pluginsUpdatedReceiver)) {
            providersUpdated(CallMethodHelper.getAllCallMethods());
            CallMethodHelper.refreshDynamicItems();
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
        }

        // If there was a voice query result returned in the {@link #onActivityResult} callback, it
        // will have been stashed in mVoiceSearchQuery since the search results fragment cannot be
        // shown until onResume has completed.  Active the search UI and set the search term now.
        if (!TextUtils.isEmpty(mVoiceSearchQuery)) {
            mActionBarController.onSearchBoxTapped();
            mSearchView.setText(mVoiceSearchQuery);
            mVoiceSearchQuery = null;
        }

        mFirstLaunch = false;

        if (mIsRestarting) {
            // This is only called when the activity goes from resumed -> paused -> resumed, so it
            // will not cause an extra view to be sent out on rotation
            if (mIsDialpadShown) {
                AnalyticsUtil.sendScreenView(mDialpadFragment, this);
            }
            mIsRestarting = false;
        }

        prepareVoiceSearchButton();
        moveFabInSearchUI();

        if (getIntent().hasExtra(EXTRA_SHOW_TAB)) {
            int index = getIntent().getIntExtra(EXTRA_SHOW_TAB, ListsFragment.TAB_INDEX_SPEED_DIAL);
            if (index < mListsFragment.getTabCount()) {
                mListsFragment.showTab(index);
            }
        } else if (Calls.CONTENT_TYPE.equals(getIntent().getType())) {
            mListsFragment.showTab(ListsFragment.TAB_INDEX_RECENTS);
        }

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
    public void onDestroy() {
        unregisterReceiver(mCallStateReceiver);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_SEARCH_QUERY, mSearchQuery);
        outState.putString(KEY_CALL_METHOD_COMPONENT, mLastKnownCallMethod);
        outState.putBoolean(KEY_IN_REGULAR_SEARCH_UI, mInRegularSearch);
        outState.putBoolean(KEY_IN_DIALPAD_SEARCH_UI, mInDialpadSearch);
        outState.putBoolean(KEY_FIRST_LAUNCH, mFirstLaunch);
        outState.putBoolean(KEY_IS_DIALPAD_SHOWN, mIsDialpadShown);
        mActionBarController.saveInstanceState(outState);
        mStateSaved = true;
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
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
        } else if (fragment instanceof SearchFragment) {
            mRegularSearchFragment = (RegularSearchFragment) fragment;
            mRegularSearchFragment.setOnPhoneNumberPickerActionListener(this);
        } else if (fragment instanceof ListsFragment) {
            mListsFragment = (ListsFragment) fragment;
            mListsFragment.addOnPageChangeListener(this);
        }
    }

    protected void handleMenuSettings() {
        final Intent intent = new Intent(this, DialerSettingsActivity.class);
        startActivity(intent);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.floating_action_button:
                if (mListsFragment.getCurrentTabIndex()
                        == ListsFragment.TAB_INDEX_ALL_CONTACTS && !mInRegularSearch) {
                    DialerUtils.startActivityWithErrorToast(
                            this,
                            IntentUtil.getNewContactIntent(),
                            R.string.add_contact_not_available);
                } else if (!mIsDialpadShown) {
                    mInCallDialpadUp = false;
                    showDialpadFragment(true);
                }
                break;
            case R.id.voice_search_button:
                try {
                    startActivityForResult(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
                            ACTIVITY_REQUEST_CODE_VOICE_SEARCH);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(DialtactsActivity.this, R.string.voice_search_not_available,
                            Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.dialtacts_options_menu_button:
                mOverflowMenu.show();
                break;
            default: {
                Log.wtf(TAG, "Unexpected onClick event from " + view);
                break;
            }
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_history:
                // Use explicit CallLogActivity intent instead of ACTION_VIEW +
                // CONTENT_TYPE, so that we always open our call log from our dialer
                final Intent intent = new Intent(this, CallLogActivity.class);
                startActivity(intent);
                break;
            case R.id.menu_add_contact:
                DialerUtils.startActivityWithErrorToast(
                        this,
                        IntentUtil.getNewContactIntent(),
                        R.string.add_contact_not_available);
                break;
            case R.id.menu_import_export:
                // We hard-code the "contactsAreAvailable" argument because doing it properly would
                // involve querying a {@link ProviderStatusLoader}, which we don't want to do right
                // now in Dialtacts for (potential) performance reasons. Compare with how it is
                // done in {@link PeopleActivity}.
                ImportExportDialogFragment.show(getFragmentManager(), true,
                        DialtactsActivity.class);
                return true;
            case R.id.menu_clear_frequents:
                ClearFrequentsDialog.show(getFragmentManager());
                return true;
            case R.id.menu_call_settings:
                handleMenuSettings();
                return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ACTIVITY_REQUEST_CODE_VOICE_SEARCH) {
            if (resultCode == RESULT_OK) {
                final ArrayList<String> matches = data.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS);
                if (matches.size() > 0) {
                    final String match = matches.get(0);
                    mVoiceSearchQuery = match;
                } else {
                    Log.e(TAG, "Voice search - nothing heard");
                }
            } else {
                Log.e(TAG, "Voice search failed");
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Initiates a fragment transaction to show the dialpad fragment. Animations and other visual
     * updates are handled by a callback which is invoked after the dialpad fragment is shown.
     * @see #onDialpadShown
     */
    private void showDialpadFragment(boolean animate) {
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

        CreditBarHelper.CreditBarVisibilityListener cbvl =
                new CreditBarHelper.CreditBarVisibilityListener() {
                    @Override
                    public void creditsBarVisibilityChanged(int visibility) {
                        // do nothing yet
                    }
                };
        CreditBarHelper.clearCallRateInformation(getGlobalCreditsBar(), cbvl);

        if (mCurrentCallMethod != null && mDialpadFragment.isAdded()) {
            // ensure the call method is updated in the fragment
            mDialpadFragment.onCallMethodChanged(mCurrentCallMethod);
        }

        mDialpadFragment.setAnimate(animate);
        AnalyticsUtil.sendScreenView(mDialpadFragment);
        ft.commit();

        if (animate) {
            mFloatingActionButtonController.scaleOut();
        } else {
            mFloatingActionButtonController.setVisible(false);
            maybeEnterSearchUi();
        }
        mActionBarController.onDialpadUp();

        mListsFragment.getView().animate().alpha(0).withLayer();
    }

    /**
     * Callback from child DialpadFragment when the dialpad is shown.
     */
    public void onDialpadShown() {
        Assert.assertNotNull(mDialpadFragment);
        if (mDialpadFragment.getAnimate()) {
            mDialpadFragment.getView().startAnimation(mSlideIn);
        } else {
            mDialpadFragment.setYFraction(0);
        }
        mDialpadFragment.providersUpdated(getLastKnownCallMethod(), mAvailableProviders);

        updateSearchFragmentPosition();
    }

    /**
     * Initiates animations and other visual updates to hide the dialpad. The fragment is hidden in
     * a callback after the hide animation ends.
     * @see #commitDialpadFragmentHide
     */
    public void hideDialpadFragment(boolean animate, boolean clearDialpad) {
        if (mDialpadFragment == null || mDialpadFragment.getView() == null) {
            return;
        }
        if (clearDialpad) {
            mDialpadFragment.clearDialpad();
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

        if (mInRegularSearch) {
            mRegularSearchFragment.updateCallCreditInfo();
        }

        mActionBarController.onDialpadDown();

        if (isInSearchUi()) {
            if (TextUtils.isEmpty(mSearchQuery)) {
                exitSearchUi();
            }
            if (mInRegularSearch) {
                mRegularSearchFragment.updateCallCreditInfo();
                mRegularSearchFragment.updateCoachMarkDrawable();
            } else {
                mSmartDialSearchFragment.updateCallCreditInfo();
                mSmartDialSearchFragment.updateCoachMarkDrawable();
            }
        }
    }

    /**
     * Finishes hiding the dialpad fragment after any animations are completed.
     */
    private void commitDialpadFragmentHide() {
        if (!mStateSaved && mDialpadFragment != null && !mDialpadFragment.isHidden()) {
            final FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.hide(mDialpadFragment);
            ft.commit();
        }
        mFloatingActionButtonController.scaleIn(AnimUtils.NO_DELAY);
    }

    private void updateSearchFragmentPosition() {
        SearchFragment fragment = null;
        if (mSmartDialSearchFragment != null && mSmartDialSearchFragment.isVisible()) {
            fragment = mSmartDialSearchFragment;
        } else if (mRegularSearchFragment != null && mRegularSearchFragment.isVisible()) {
            fragment = mRegularSearchFragment;
        }
        if (fragment != null && fragment.isVisible()) {
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

    @Override
    public boolean shouldShowActionBar() {
        return mListsFragment.shouldShowActionBar();
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

        if (callKey) {
            getTelecomManager().showInCallScreen(false);
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

        final boolean phoneIsInUse = phoneIsInUse();
        if (phoneIsInUse || (intent.getData() !=  null && isDialIntent(intent))) {
            showDialpadFragment(false);
            mDialpadFragment.setStartedFromNewIntent(true);
            if (phoneIsInUse && !mDialpadFragment.isVisible()) {
                mInCallDialpadUp = true;
            }
        }
    }

    @Override
    public void onNewIntent(Intent newIntent) {
        setIntent(newIntent);

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

    /**
     * Returns an appropriate call origin for this Activity. May return null when no call origin
     * should be used (e.g. when some 3rd party application launched the screen. Call origin is
     * for remembering the tab in which the user made a phone call, so the external app's DIAL
     * request should not be counted.)
     */
    public String getCallOrigin() {
        return !isDialIntent(getIntent()) ? CALL_ORIGIN_DIALTACTS : null;
    }

    /**
     * Shows the search fragment
     */
    private void enterSearchUi(boolean smartDialSearch, String query, boolean animate) {
        if (mStateSaved || getFragmentManager().isDestroyed()) {
            // Weird race condition where fragment is doing work after the activity is destroyed
            // due to talkback being on (b/10209937). Just return since we can't do any
            // constructive here.
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Entering search UI - smart dial " + smartDialSearch);
        }

        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        if (mInDialpadSearch && mSmartDialSearchFragment != null) {
            transaction.remove(mSmartDialSearchFragment);
        } else if (mInRegularSearch && mRegularSearchFragment != null) {
            transaction.remove(mRegularSearchFragment);
        }

        final String tag;
        if (smartDialSearch) {
            tag = TAG_SMARTDIAL_SEARCH_FRAGMENT;
        } else {
            tag = TAG_REGULAR_SEARCH_FRAGMENT;
        }
        mInDialpadSearch = smartDialSearch;
        mInRegularSearch = !smartDialSearch;

        SearchFragment fragment = (SearchFragment) getFragmentManager().findFragmentByTag(tag);
        if (animate) {
            transaction.setCustomAnimations(android.R.animator.fade_in, 0);
        } else {
            transaction.setTransition(FragmentTransaction.TRANSIT_NONE);
        }
        if (fragment == null) {
            if (smartDialSearch) {
                fragment = new SmartDialSearchFragment();
            } else {
                fragment = new RegularSearchFragment();
            }
            transaction.add(R.id.dialtacts_frame, fragment, tag);
        } else {
            transaction.show(fragment);
        }
        // DialtactsActivity will provide the options menu
        fragment.setHasOptionsMenu(false);
        fragment.setShowEmptyListForNullQuery(true);
        if (!smartDialSearch) {
            fragment.setQueryString(query, false /* delaySelection */);
        }
        transaction.commit();

        if (animate) {
            mListsFragment.getView().animate().alpha(0).withLayer();
        }

        mListsFragment.setUserVisibleHint(false);
    }

    /**
     * Hides the search fragment
     */
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
        transaction.commit();

        // Left search UI, clear overlay coachmark if user has not dismissed it.
        getSearchTextLayout().getOverlay().clear();

        mListsFragment.getView().animate().alpha(1).withLayer();

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
        if (mStateSaved) {
            return;
        }
        if (mIsDialpadShown) {
            if (TextUtils.isEmpty(mSearchQuery) ||
                    (mSmartDialSearchFragment != null && mSmartDialSearchFragment.isVisible()
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

    /**
     * @return True if the search UI was exited, false otherwise
     */
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
                getResources().getDrawable(R.drawable.fab_ic_dial),
                getResources().getString(R.string.action_menu_dialpad_button));

        moveFabInSearchUI();
        mFloatingActionButtonController.scaleIn(FAB_SCALE_IN_DELAY_MS);
    }

    public void moveFabInSearchUI() {
        if (mGlobalCreditsBar.getVisibility() == View.VISIBLE) {
            mFloatingActionButtonController.align(getFabAlignment(), 0 /* offsetX */, -125, true);
        } else {
            mFloatingActionButtonController.align(getFabAlignment(), true /* animate */);
        }
    }

    @Override
    public void onDialpadQueryChanged(String query) {
        if (mSmartDialSearchFragment != null) {
            mSmartDialSearchFragment.setAddToContactNumber(query);
        }
        final String normalizedQuery = SmartDialNameMatcher.normalizeNumber(query,
                SmartDialPrefix.getMap());

        if (!TextUtils.equals(mSearchView.getText(), normalizedQuery)) {
            if (DEBUG) {
                Log.d(TAG, "onDialpadQueryChanged - new query: " + query);
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
        if (mInDialpadSearch && mSmartDialSearchFragment != null
                && !mSmartDialSearchFragment.isShowingPermissionRequest()) {
            hideDialpadFragment(true /* animate */, true /* clearDialpad */);
            return true;
        }
        return false;
    }

    @Override
    public void onListFragmentScrollStateChange(int scrollState) {
        if (scrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            hideDialpadFragment(true, false);
            DialerUtils.hideInputMethod(mParentLayout);
        }
    }

    @Override
    public void onListFragmentScroll(int firstVisibleItem, int visibleItemCount,
                                     int totalItemCount) {
        // TODO: No-op for now. This should eventually show/hide the actionBar based on
        // interactions with the ListsFragments.
    }

    private boolean phoneIsInUse() {
        return getTelecomManager().isInCall();
    }

    private boolean canIntentBeHandled(Intent intent) {
        final PackageManager packageManager = getPackageManager();
        final List<ResolveInfo> resolveInfo = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return resolveInfo != null && resolveInfo.size() > 0;
    }

    /**
     * Called when the user has long-pressed a contact tile to start a drag operation.
     */
    @Override
    public void onDragStarted(int x, int y, PhoneFavoriteSquareTileView view) {
        mListsFragment.showRemoveView(true);
    }

    @Override
    public void onDragHovered(int x, int y, PhoneFavoriteSquareTileView view) {
    }

    /**
     * Called when the user has released a contact tile after long-pressing it.
     */
    @Override
    public void onDragFinished(int x, int y) {
        mListsFragment.showRemoveView(false);
    }

    @Override
    public void onDroppedOnRemove() {}

    /**
     * Allows the SpeedDialFragment to attach the drag controller to mRemoveViewContainer
     * once it has been attached to the activity.
     */
    @Override
    public void setDragDropController(DragDropController dragController) {
        mDragDropController = dragController;
        mListsFragment.getRemoveView().setDragDropController(dragController);
    }

    /**
     * Implemented to satisfy {@link SpeedDialFragment.HostInterface}
     */
    @Override
    public void showAllContactsTab() {
        if (mListsFragment != null) {
            mListsFragment.showTab(ListsFragment.TAB_INDEX_ALL_CONTACTS);
        }
    }

    /**
     * Implemented to satisfy {@link CallLogFragment.HostInterface}
     */
    @Override
    public void showDialpad() {
        showDialpadFragment(true);
    }

    @Override
    public void onPickPhoneNumberAction(Uri dataUri) {
        // Only use selected call method if call method spinner is visible (t9 dialpad or search)
        CallMethodInfo cmi = isInSearchUi() || isDialpadShown() ? mCurrentCallMethod : null;

        // If no sim is selected, or emergency callmethod selected, or number is
        // an emergency number, phone account handle should be null, and will use the
        // default account.
        // Else, create PhoneAccountHandle from selected callmethod components and
        // initial call using that account.
        PhoneAccountHandle handle = CallMethodInfo.getPhoneAccountHandleFromCallMethodInfo(this,
                cmi, null);

        // Specify call-origin so that users will see the previous tab instead of
        // CallLog screen (search UI will be automatically exited).
        PhoneNumberInteraction.startInteractionForPhoneCall(
                DialtactsActivity.this, dataUri, getCallOrigin(), handle);
        mClearSearchOnPause = true;
    }

    @Override
    public void onCallNumberDirectly(String phoneNumber) {
        onCallNumberDirectly(phoneNumber, false /* isVideoCall */);
    }

    @Override
    public void onCallNumberDirectly(String phoneNumber, boolean isVideoCall) {
        onCallNumberDirectly(phoneNumber, isVideoCall, null);
    }

    @Override
    public void onCallNumberDirectly(String phoneNumber, boolean isVideoCall, String mimeType) {
        if (phoneNumber == null) {
            // Invalid phone number, but let the call go through so that InCallUI can show
            // an error message.
            phoneNumber = "";
        }
        // Only use selected call method if call method spinner is visible (t9 dialpad or search)
        CallMethodInfo cmi = isInSearchUi() || isDialpadShown() ? mCurrentCallMethod : null;

        boolean isInCallProvider = (cmi != null && cmi.mIsInCallProvider);
        boolean isEmergencyNumber = PhoneNumberUtils.isEmergencyNumber(phoneNumber);
        boolean isRegularCallableMimeType = TextUtils.isEmpty(mimeType) ||
                TextUtils.equals(mimeType, Phone.CONTENT_ITEM_TYPE) ||
                TextUtils.equals(mimeType, SipAddress.CONTENT_ITEM_TYPE);

        // Assume InCallAPI Provider only if not emergency number and selected call method is incall
        // provider or number's mimetype is null or a non-incall mimetype
        if (!isEmergencyNumber && (isInCallProvider || !isRegularCallableMimeType)) {
            StartCallRequest request = new StartCallRequest(phoneNumber, OriginCodes.CONTACT_SEARCH,
                    0, new StartInCallCallReceiver(new Handler(Looper.getMainLooper())));

            InCallServices.getInstance().startVoiceCallForMimeType(
                    AmbientConnection.CLIENT.get(this), mimeType, request);
        } else {
            // If no sim is selected, or emergency callmethod selected, or number is
            // an emergency number, phone account handle should be null, and will use the
            // default account.
            // Else, create PhoneAccountHandle from selected callmethod components and
            // initial call using that account.
            PhoneAccountHandle handle = CallMethodInfo.getPhoneAccountHandleFromCallMethodInfo(this,
                    cmi, phoneNumber);
            Intent intent = isVideoCall ?
                    IntentUtil.getVideoCallIntent(phoneNumber, OriginCodes.CONTACT_SEARCH) :
                    IntentUtil.getCallIntent(phoneNumber, OriginCodes.CONTACT_SEARCH, handle);
            DialerUtils.startActivityWithErrorToast(this, intent);
        }
        mClearSearchOnPause = true;
    }

    @Override
    public void onShortcutIntentCreated(Intent intent) {
        Log.w(TAG, "Unsupported intent has come (" + intent + "). Ignoring.");
    }

    @Override
    public void onHomeInActionBarSelected() {
        exitSearchUi();
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        int tabIndex = mListsFragment.getCurrentTabIndex();

        // Scroll the button from center to end when moving from the Speed Dial to Recents tab.
        // In RTL, scroll when the current tab is Recents instead of Speed Dial, because the order
        // of the tabs is reversed and the ViewPager returns the left tab position during scroll.
        boolean isRtl = DialerUtils.isRtl();
        if (!isRtl && tabIndex == ListsFragment.TAB_INDEX_SPEED_DIAL && !mIsLandscape) {
            mFloatingActionButtonController.onPageScrolled(positionOffset);
        } else if (isRtl && tabIndex == ListsFragment.TAB_INDEX_RECENTS && !mIsLandscape) {
            mFloatingActionButtonController.onPageScrolled(1 - positionOffset);
        } else if (tabIndex != ListsFragment.TAB_INDEX_SPEED_DIAL) {
            mFloatingActionButtonController.onPageScrolled(1);
        }
    }

    @Override
    public void onPageSelected(int position) {
        int tabIndex = mListsFragment.getCurrentTabIndex();
        if (tabIndex == ListsFragment.TAB_INDEX_ALL_CONTACTS) {
            mFloatingActionButtonController.changeIcon(
                    getResources().getDrawable(R.drawable.ic_person_add_24dp),
                    getResources().getString(R.string.search_shortcut_create_new_contact));
        } else {
            mFloatingActionButtonController.changeIcon(
                    getResources().getDrawable(R.drawable.fab_ic_dial),
                    getResources().getString(R.string.action_menu_dialpad_button));
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    private TelecomManager getTelecomManager() {
        return (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
    }

    @Override
    public boolean isActionBarShowing() {
        return mActionBarController.isActionBarShowing();
    }

    @Override
    public ActionBarController getActionBarController() {
        return mActionBarController;
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

    public int getDialpadWidth() {
        if (mDialpadFragment != null) {
            return mDialpadFragment.getDialpadWidth();
        }
        return 0;
    }

    public FrameLayout getGlobalCreditsBar() {
        return mGlobalCreditsBar;
    }

    public View getSearchTextLayout() {
        return mSearchEditTextLayout;
    }

    public View getSearchEditText() {
        return mSearchView;
    }

    @Override
    public int getActionBarHideOffset() {
        return getActionBar().getHideOffset();
    }

    @Override
    public void setActionBarHideOffset(int offset) {
        getActionBar().setHideOffset(offset);
    }

    @Override
    public int getActionBarHeight() {
        return mActionBarHeight;
    }

    private int getFabAlignment() {
        if (!mIsLandscape && !isInSearchUi() &&
                mListsFragment.getCurrentTabIndex() == ListsFragment.TAB_INDEX_SPEED_DIAL) {
            return FloatingActionButtonController.ALIGN_MIDDLE;
        }
        return FloatingActionButtonController.ALIGN_END;
    }
}
