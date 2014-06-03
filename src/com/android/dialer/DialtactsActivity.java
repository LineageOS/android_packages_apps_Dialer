/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.Intents.UI;
import android.speech.RecognizerIntent;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView.OnScrollListener;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.activity.TransactionSafeActivity;
import com.android.contacts.common.dialog.ClearFrequentsDialog;
import com.android.contacts.common.interactions.ImportExportDialogFragment;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.dialer.calllog.CallLogActivity;
import com.android.dialer.database.DialerDatabaseHelper;
import com.android.dialer.dialpad.DialpadFragment;
import com.android.dialer.dialpad.SmartDialNameMatcher;
import com.android.dialer.dialpad.SmartDialPrefix;
import com.android.dialer.interactions.PhoneNumberInteraction;
import com.android.dialer.list.AllContactsActivity;
import com.android.dialer.list.DragDropController;
import com.android.dialer.list.OnDragDropListener;
import com.android.dialer.list.OnListFragmentScrolledListener;
import com.android.dialer.list.PhoneFavoriteFragment;
import com.android.dialer.list.PhoneFavoriteTileView;
import com.android.dialer.list.RegularSearchFragment;
import com.android.dialer.list.RemoveView;
import com.android.dialer.list.SearchFragment;
import com.android.dialer.list.SmartDialSearchFragment;
import com.android.dialerbind.DatabaseHelperManager;
import com.android.internal.telephony.ITelephony;

import java.util.ArrayList;
import java.util.List;

/**
 * The dialer tab's title is 'phone', a more common name (see strings.xml).
 */
public class DialtactsActivity extends TransactionSafeActivity implements View.OnClickListener,
        DialpadFragment.OnDialpadQueryChangedListener, PopupMenu.OnMenuItemClickListener,
        OnListFragmentScrolledListener,
        DialpadFragment.HostInterface,
        PhoneFavoriteFragment.OnShowAllContactsListener,
        PhoneFavoriteFragment.HostInterface,
        OnDragDropListener, View.OnLongClickListener {
    private static final String TAG = "DialtactsActivity";

    public static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public static final String SHARED_PREFS_NAME = "com.android.dialer_preferences";

    /** Used to open Call Setting */
    private static final String PHONE_PACKAGE = "com.android.phone";
    private static final String CALL_SETTINGS_CLASS_NAME =
            "com.android.phone.CallFeaturesSetting";
    private static final String MSIM_CALL_SETTINGS_CLASS_NAME =
            "com.android.phone.MSimCallFeaturesSetting";

    /** @see #getCallOrigin() */
    private static final String CALL_ORIGIN_DIALTACTS =
            "com.android.dialer.DialtactsActivity";

    private static final String KEY_IN_REGULAR_SEARCH_UI = "in_regular_search_ui";
    private static final String KEY_IN_DIALPAD_SEARCH_UI = "in_dialpad_search_ui";
    private static final String KEY_SEARCH_QUERY = "search_query";
    private static final String KEY_FIRST_LAUNCH = "first_launch";

    private static final String TAG_DIALPAD_FRAGMENT = "dialpad";
    private static final String TAG_REGULAR_SEARCH_FRAGMENT = "search";
    private static final String TAG_SMARTDIAL_SEARCH_FRAGMENT = "smartdial";
    private static final String TAG_FAVORITES_FRAGMENT = "favorites";

    /**
     * Just for backward compatibility. Should behave as same as {@link Intent#ACTION_DIAL}.
     */
    private static final String ACTION_TOUCH_DIALER = "com.android.phone.action.TOUCH_DIALER";

    private static final int ACTIVITY_REQUEST_CODE_VOICE_SEARCH = 1;

    private static final int ANIMATION_DURATION = 200;

    private String mFilterText;

    /**
     * The main fragment displaying the user's favorites and frequent contacts
     */
    private PhoneFavoriteFragment mPhoneFavoriteFragment;

    /**
     * Fragment containing the dialpad that slides into view
     */
    private DialpadFragment mDialpadFragment;

    /**
     * Fragment for searching phone numbers using the alphanumeric keyboard.
     */
    private RegularSearchFragment mRegularSearchFragment;

    /**
     * Fragment for searching phone numbers using the dialpad.
     */
    private SmartDialSearchFragment mSmartDialSearchFragment;

    private View mMenuButton;
    private View mFakeActionBar;
    private View mCallHistoryButton;
    private View mDialpadButton;
    private View mDialButton;
    private PopupMenu mOverflowMenu;
    private PopupMenu mDialpadOverflowMenu;

    // Padding view used to shift the fragments up when the dialpad is shown.
    private View mFragmentsFrame;

    private boolean mInDialpadSearch;
    private boolean mInRegularSearch;
    private boolean mClearSearchOnPause;

    /**
     * True if the dialpad is only temporarily showing due to being in call
     */
    private boolean mInCallDialpadUp;

    /**
     * True when this activity has been launched for the first time.
     */
    private boolean mFirstLaunch;
    private View mSearchViewContainer;
    private RemoveView mRemoveViewContainer;
    // This view points to the Framelayout that houses both the search view and remove view
    // containers.
    private View mSearchAndRemoveViewContainer;
    private View mSearchViewCloseButton;
    private View mVoiceSearchButton;
    private EditText mSearchView;

    private String mSearchQuery;

    private DialerDatabaseHelper mDialerDatabaseHelper;

    private class OverflowPopupMenu extends PopupMenu {
        public OverflowPopupMenu(Context context, View anchor) {
            super(context, anchor);
        }

        @Override
        public void show() {
            final Menu menu = getMenu();
            final MenuItem clearFrequents = menu.findItem(R.id.menu_clear_frequents);
            clearFrequents.setVisible(mPhoneFavoriteFragment.hasFrequents());
            super.show();
        }
    }

    /**
     * Listener used when one of phone numbers in search UI is selected. This will initiate a
     * phone call using the phone number.
     */
    private final OnPhoneNumberPickerActionListener mPhoneNumberPickerActionListener =
            new OnPhoneNumberPickerActionListener() {
                @Override
                public void onPickPhoneNumberAction(Uri dataUri) {
                    // Specify call-origin so that users will see the previous tab instead of
                    // CallLog screen (search UI will be automatically exited).
                    PhoneNumberInteraction.startInteractionForPhoneCall(
                        DialtactsActivity.this, dataUri, getCallOrigin());
                    mClearSearchOnPause = true;
                }

                @Override
                public void onCallNumberDirectly(String phoneNumber) {
                    Intent intent = CallUtil.getCallIntent(phoneNumber, getCallOrigin());
                    startActivity(intent);
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
    };

    /**
     * Listener used to send search queries to the phone search fragment.
     */
    private final TextWatcher mPhoneSearchQueryTextListener = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                final boolean dialpadSearch = isDialpadShowing();

                final String newText = dialpadSearch ?
                   SmartDialNameMatcher.normalizeNumber(s.toString(), SmartDialPrefix.getMap()):
                   s.toString();
                if (newText.equals(mSearchQuery)) {
                    // If the query hasn't changed (perhaps due to activity being destroyed
                    // and restored, or user launching the same DIAL intent twice), then there is
                    // no need to do anything here.
                    return;
                }
                mSearchQuery = newText;
                if (DEBUG) {
                    Log.d(TAG, "onTextChange for mSearchView called with new query: " + s);
                }

                // Show search result with non-empty text. Show a bare list otherwise.
                if (TextUtils.isEmpty(newText) && getInSearchUi()) {
                    exitSearchUi();
                    mSearchViewCloseButton.setVisibility(View.GONE);
                    mVoiceSearchButton.setVisibility(View.VISIBLE);
                    return;
                } else if (!TextUtils.isEmpty(newText)) {
                    final boolean sameSearchMode = (dialpadSearch && mInDialpadSearch) ||
                            (!dialpadSearch && mInRegularSearch);
                    if (!sameSearchMode) {
                        // call enterSearchUi only if we are switching search modes, or entering
                        // search ui for the first time
                        enterSearchUi(dialpadSearch, newText);
                    }

                    if (dialpadSearch && mSmartDialSearchFragment != null) {
                            mSmartDialSearchFragment.setQueryString(newText, false);
                            mSmartDialSearchFragment.setDialpadQueryString(s.toString());
                    } else if (mRegularSearchFragment != null) {
                        mRegularSearchFragment.setQueryString(newText, false);
                    }
                    mSearchViewCloseButton.setVisibility(View.VISIBLE);
                    mVoiceSearchButton.setVisibility(View.GONE);
                    return;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
    };

    private boolean isDialpadShowing() {
        return mDialpadFragment != null && mDialpadFragment.isVisible();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        mFirstLaunch = true;

        final Intent intent = getIntent();
        fixIntent(intent);

        setContentView(R.layout.dialtacts_activity);

        // Add the favorites fragment, and the dialpad fragment, but only if savedInstanceState
        // is null. Otherwise the fragment manager takes care of recreating these fragments.
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.dialtacts_frame, new PhoneFavoriteFragment(), TAG_FAVORITES_FRAGMENT)
                    .add(R.id.dialtacts_container, new DialpadFragment(), TAG_DIALPAD_FRAGMENT)
                    .commit();
        } else {
            mSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY);
            mInRegularSearch = savedInstanceState.getBoolean(KEY_IN_REGULAR_SEARCH_UI);
            mInDialpadSearch = savedInstanceState.getBoolean(KEY_IN_DIALPAD_SEARCH_UI);
            mFirstLaunch = savedInstanceState.getBoolean(KEY_FIRST_LAUNCH);
        }

        mFragmentsFrame = findViewById(R.id.dialtacts_frame);

        mRemoveViewContainer = (RemoveView) findViewById(R.id.remove_view_container);
        mSearchAndRemoveViewContainer = (View) findViewById(R.id.search_and_remove_view_container);
        setupFakeActionBarItems();
        prepareSearchView();

        if (UI.FILTER_CONTACTS_ACTION.equals(intent.getAction())
                && savedInstanceState == null) {
            setupFilterText(intent);
        }

        hideDialpadFragment(false, false);

        mDialerDatabaseHelper = DatabaseHelperManager.getDatabaseHelper(this);
        SmartDialPrefix.initializeNanpSettings(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mFirstLaunch) {
            displayFragment(getIntent());
        } else if (!phoneIsInUse() && mInCallDialpadUp) {
            hideDialpadFragment(false, true);
            mInCallDialpadUp = false;
        }
        prepareVoiceSearchButton();
        mFirstLaunch = false;
        mDialerDatabaseHelper.startSmartDialUpdateThread();
    }

    @Override
    protected void onPause() {
        if (mClearSearchOnPause) {
            hideDialpadAndSearchUi();
            mClearSearchOnPause = false;
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_SEARCH_QUERY, mSearchQuery);
        outState.putBoolean(KEY_IN_REGULAR_SEARCH_UI, mInRegularSearch);
        outState.putBoolean(KEY_IN_DIALPAD_SEARCH_UI, mInDialpadSearch);
        outState.putBoolean(KEY_FIRST_LAUNCH, mFirstLaunch);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof DialpadFragment) {
            mDialpadFragment = (DialpadFragment) fragment;
            final FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.hide(mDialpadFragment);
            transaction.commit();
        } else if (fragment instanceof SmartDialSearchFragment) {
            mSmartDialSearchFragment = (SmartDialSearchFragment) fragment;
            mSmartDialSearchFragment.setOnPhoneNumberPickerActionListener(
                    mPhoneNumberPickerActionListener);
            if (mFragmentsFrame != null) {
                mFragmentsFrame.setAlpha(1.0f);
            }
        } else if (fragment instanceof SearchFragment) {
            mRegularSearchFragment = (RegularSearchFragment) fragment;
            mRegularSearchFragment.setOnPhoneNumberPickerActionListener(
                    mPhoneNumberPickerActionListener);
            if (mFragmentsFrame != null) {
                mFragmentsFrame.setAlpha(1.0f);
            }
        } else if (fragment instanceof PhoneFavoriteFragment) {
            mPhoneFavoriteFragment = (PhoneFavoriteFragment) fragment;
            mPhoneFavoriteFragment.setListener(mPhoneFavoriteListener);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
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
            case R.id.menu_add_contact:
                try {
                    startActivity(new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI));
                } catch (ActivityNotFoundException e) {
                    Toast toast = Toast.makeText(this,
                            R.string.add_contact_not_available,
                            Toast.LENGTH_SHORT);
                    toast.show();
                }
                return true;
            case R.id.menu_call_settings:
                handleMenuSettings();
                return true;
            case R.id.menu_all_contacts:
                onShowAllContacts();
                return true;
        }
        return false;
    }

    protected void handleMenuSettings() {
        openTelephonySetting(this);
    }

    public static void openTelephonySetting(Activity activity) {
        final Intent settingsIntent = getCallSettingsIntent();
        activity.startActivity(settingsIntent);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.overflow_menu: {
                if (isDialpadShowing()) {
                    mDialpadOverflowMenu.show();
                } else {
                    mOverflowMenu.show();
                }
                break;
            }
            case R.id.dialpad_button:
                // Reset the boolean flag that tracks whether the dialpad was up because
                // we were in call. Regardless of whether it was true before, we want to
                // show the dialpad because the user has explicitly clicked the dialpad
                // button.
                mInCallDialpadUp = false;
                showDialpadFragment(true);
                break;
            case R.id.dial_button:
                // Dial button was pressed; tell the Dialpad fragment
                mDialpadFragment.dialButtonPressed();
                break;
            case R.id.call_history_button:
                // Use explicit CallLogActivity intent instead of ACTION_VIEW +
                // CONTENT_TYPE, so that we always open our call log from our dialer
                final Intent intent = new Intent(this, CallLogActivity.class);
                startActivity(intent);
                break;
            case R.id.search_close_button:
                // Clear the search field
                if (!TextUtils.isEmpty(mSearchView.getText())) {
                    mDialpadFragment.clearDialpad();
                    mSearchView.setText("");
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
            default: {
                Log.wtf(TAG, "Unexpected onClick event from " + view);
                break;
            }
        }
    }

    @Override
    public boolean onLongClick(View view) {
        switch (view.getId()) {
            case R.id.dial_button: {
                // Dial button was pressed; tell the Dialpad fragment
                mDialpadFragment.dialButtonPressed();
                return true;  // Consume the event
            }
            default: {
                Log.wtf(TAG, "Unexpected onClick event from " + view);
                break;
            }
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
                    mSearchView.setText(match);
                } else {
                    Log.e(TAG, "Voice search - nothing heard");
                }
            } else {
                Log.e(TAG, "Voice search failed");
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void showDialpadFragment(boolean animate) {
        mDialpadFragment.setAdjustTranslationForAnimation(animate);
        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        if (animate) {
            ft.setCustomAnimations(R.anim.slide_in, 0);
        } else {
            mDialpadFragment.setYFraction(0);
        }
        ft.show(mDialpadFragment);
        ft.commit();
        mDialButton.setVisibility(shouldShowOnscreenDialButton() ? View.VISIBLE : View.GONE);
        mDialpadButton.setVisibility(View.GONE);

        if (mDialpadOverflowMenu == null) {
            mDialpadOverflowMenu = mDialpadFragment.buildOptionsMenu(mMenuButton);
        }

        mMenuButton.setOnTouchListener(mDialpadOverflowMenu.getDragToOpenListener());
    }

    public void hideDialpadFragment(boolean animate, boolean clearDialpad) {
        if (mDialpadFragment == null) return;
        if (clearDialpad) {
            mDialpadFragment.clearDialpad();
        }
        if (!mDialpadFragment.isVisible()) return;
        mDialpadFragment.setAdjustTranslationForAnimation(animate);
        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        if (animate) {
            ft.setCustomAnimations(0, R.anim.slide_out);
        }
        ft.hide(mDialpadFragment);
        ft.commit();
        mDialButton.setVisibility(View.GONE);
        mDialpadButton.setVisibility(View.VISIBLE);
        mMenuButton.setOnTouchListener(mOverflowMenu.getDragToOpenListener());
    }

    private void prepareSearchView() {
        mSearchViewContainer = findViewById(R.id.search_view_container);
        mSearchViewCloseButton = findViewById(R.id.search_close_button);
        mSearchViewCloseButton.setOnClickListener(this);

        mSearchView = (EditText) findViewById(R.id.search_view);
        mSearchView.addTextChangedListener(mPhoneSearchQueryTextListener);
        mSearchView.setHint(getString(R.string.dialer_hint_find_contact));

        prepareVoiceSearchButton();
    }

    private void prepareVoiceSearchButton() {
        mVoiceSearchButton = findViewById(R.id.voice_search_button);
        final Intent voiceIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        if (canIntentBeHandled(voiceIntent)) {
            mVoiceSearchButton.setVisibility(View.VISIBLE);
            mVoiceSearchButton.setOnClickListener(this);
        } else {
            mVoiceSearchButton.setVisibility(View.GONE);
        }
    }

    final AnimatorListener mHideListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            mSearchAndRemoveViewContainer.setVisibility(View.GONE);
        }
    };

    private boolean getInSearchUi() {
        return mInDialpadSearch || mInRegularSearch;
    }

    private void setNotInSearchUi() {
        mInDialpadSearch = false;
        mInRegularSearch = false;
    }

    private void hideDialpadAndSearchUi() {
        mSearchView.setText(null);
        hideDialpadFragment(false, true);
    }

    public void hideSearchBar() {
        final int height = mSearchAndRemoveViewContainer.getHeight();
        mSearchAndRemoveViewContainer.animate().cancel();
        mSearchAndRemoveViewContainer.setAlpha(1);
        mSearchAndRemoveViewContainer.setTranslationY(0);
        mSearchAndRemoveViewContainer.animate().withLayer().alpha(0)
                .translationY(-height).setDuration(ANIMATION_DURATION)
                .setListener(mHideListener);

        mFragmentsFrame.animate().withLayer()
                .translationY(-height).setDuration(ANIMATION_DURATION).setListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mFragmentsFrame.setTranslationY(0);
                    }
                });

        if (!mInDialpadSearch && !mInRegularSearch) {
            // If the favorites fragment is showing, fade to blank.
            mFragmentsFrame.animate().alpha(0.0f);
        }
    }

    public void showSearchBar() {
        final int height = mSearchAndRemoveViewContainer.getHeight();
        mSearchAndRemoveViewContainer.animate().cancel();
        mSearchAndRemoveViewContainer.setAlpha(0);
        mSearchAndRemoveViewContainer.setTranslationY(-height);
        mSearchAndRemoveViewContainer.animate().withLayer().alpha(1).translationY(0)
                .setDuration(ANIMATION_DURATION).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mSearchAndRemoveViewContainer.setVisibility(View.VISIBLE);
                        }
                    });

        mFragmentsFrame.setTranslationY(-height);
        mFragmentsFrame.animate().withLayer().translationY(0).setDuration(ANIMATION_DURATION)
                .alpha(1.0f)
                .setListener(
                        new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationStart(Animator animation) {
                            }
                        });
    }

    private void setupFakeActionBarItems() {
        mMenuButton = findViewById(R.id.overflow_menu);
        if (mMenuButton != null) {
            mMenuButton.setOnClickListener(this);
            if (mOverflowMenu == null) {
                mOverflowMenu = buildOptionsMenu(mMenuButton);
            }
            mMenuButton.setOnTouchListener(mOverflowMenu.getDragToOpenListener());
        }

        mFakeActionBar = findViewById(R.id.fake_action_bar);

        mCallHistoryButton = findViewById(R.id.call_history_button);
        mCallHistoryButton.setOnClickListener(this);

        mDialButton = findViewById(R.id.dial_button);
        mDialButton.setOnClickListener(this);
        mDialButton.setOnLongClickListener(this);

        mDialpadButton = findViewById(R.id.dialpad_button);
        mDialpadButton.setOnClickListener(this);
    }

    private PopupMenu buildOptionsMenu(View invoker) {
        PopupMenu menu = new OverflowPopupMenu(this, invoker);
        menu.inflate(R.menu.dialtacts_options);
        menu.setOnMenuItemClickListener(this);
        return menu;
    }

    private void fixIntent(Intent intent) {
        // This should be cleaned up: the call key used to send an Intent
        // that just said to go to the recent calls list.  It now sends this
        // abstract action, but this class hasn't been rewritten to deal with it.
        if (Intent.ACTION_CALL_BUTTON.equals(intent.getAction())) {
            intent.setDataAndType(Calls.CONTENT_URI, Calls.CONTENT_TYPE);
            intent.putExtra("call_key", true);
            setIntent(intent);
        }
    }

    /**
     * Returns true if the intent is due to hitting the green send key (hardware call button:
     * KEYCODE_CALL) while in a call.
     *
     * @param intent the intent that launched this activity
     * @param recentCallsRequest true if the intent is requesting to view recent calls
     * @return true if the intent is due to hitting the green send key while in a call
     */
    private boolean isSendKeyWhileInCall(Intent intent, boolean recentCallsRequest) {
        // If there is a call in progress go to the call screen
        if (recentCallsRequest) {
            final boolean callKey = intent.getBooleanExtra("call_key", false);

            try {
                ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
                if (callKey && phone != null && phone.showCallScreen()) {
                    return true;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to handle send while in call", e);
            }
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
        boolean recentCallsRequest = Calls.CONTENT_TYPE.equals(intent.resolveType(
            getContentResolver()));
        if (isSendKeyWhileInCall(intent, recentCallsRequest)) {
            finish();
            return;
        }

        if (mDialpadFragment != null) {
            final boolean phoneIsInUse = phoneIsInUse();
            if (phoneIsInUse || isDialIntent(intent)) {
                mDialpadFragment.setStartedFromNewIntent(true);
                if (phoneIsInUse && !mDialpadFragment.isVisible()) {
                    mInCallDialpadUp = true;
                }
                showDialpadFragment(false);
            }
        }
    }

    @Override
    public void onNewIntent(Intent newIntent) {
        setIntent(newIntent);
        fixIntent(newIntent);
        displayFragment(newIntent);
        final String action = newIntent.getAction();

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
            if (data != null && CallUtil.SCHEME_TEL.equals(data.getScheme())) {
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
     * Retrieves the filter text stored in {@link #setupFilterText(Intent)}.
     * This text originally came from a FILTER_CONTACTS_ACTION intent received
     * by this activity. The stored text will then be cleared after after this
     * method returns.
     *
     * @return The stored filter text
     */
    public String getAndClearFilterText() {
        String filterText = mFilterText;
        mFilterText = null;
        return filterText;
    }

    /**
     * Stores the filter text associated with a FILTER_CONTACTS_ACTION intent.
     * This is so child activities can check if they are supposed to display a filter.
     *
     * @param intent The intent received in {@link #onNewIntent(Intent)}
     */
    private void setupFilterText(Intent intent) {
        // If the intent was relaunched from history, don't apply the filter text.
        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            return;
        }
        String filter = intent.getStringExtra(UI.FILTER_TEXT_EXTRA_KEY);
        if (filter != null && filter.length() > 0) {
            mFilterText = filter;
        }
    }

    private final PhoneFavoriteFragment.Listener mPhoneFavoriteListener =
            new PhoneFavoriteFragment.Listener() {
        @Override
        public void onContactSelected(Uri contactUri) {
            PhoneNumberInteraction.startInteractionForPhoneCall(
                        DialtactsActivity.this, contactUri, getCallOrigin());
        }

        @Override
        public void onCallNumberDirectly(String phoneNumber) {
            Intent intent = CallUtil.getCallIntent(phoneNumber, getCallOrigin());
            startActivity(intent);
        }
    };

    /* TODO krelease: This is only relevant for phones that have a hard button search key (i.e.
     * Nexus S). Supporting it is a little more tricky because of the dialpad fragment might
     * be showing when the search key is pressed so there is more state management involved.

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, boolean globalSearch) {
        if (mRegularSearchFragment != null && mRegularSearchFragment.isAdded() && !globalSearch) {
            if (mInSearchUi) {
                if (mSearchView.hasFocus()) {
                    showInputMethod(mSearchView.findFocus());
                } else {
                    mSearchView.requestFocus();
                }
            } else {
                enterSearchUi();
            }
        } else {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        }
    }*/

    private void showInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, 0);
        }
    }

    private void hideInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Shows the search fragment
     */
    private void enterSearchUi(boolean smartDialSearch, String query) {
        if (getFragmentManager().isDestroyed()) {
            // Weird race condition where fragment is doing work after the activity is destroyed
            // due to talkback being on (b/10209937). Just return since we can't do any
            // constructive here.
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Entering search UI - smart dial " + smartDialSearch);
        }

        final FragmentTransaction transaction = getFragmentManager().beginTransaction();

        SearchFragment fragment;
        if (mInDialpadSearch) {
            transaction.remove(mSmartDialSearchFragment);
        } else if (mInRegularSearch) {
            transaction.remove(mRegularSearchFragment);
        } else {
            transaction.remove(mPhoneFavoriteFragment);
        }

        final String tag;
        if (smartDialSearch) {
            tag = TAG_SMARTDIAL_SEARCH_FRAGMENT;
        } else {
            tag = TAG_REGULAR_SEARCH_FRAGMENT;
        }
        mInDialpadSearch = smartDialSearch;
        mInRegularSearch = !smartDialSearch;

        fragment = (SearchFragment) getFragmentManager().findFragmentByTag(tag);
        if (fragment == null) {
            if (smartDialSearch) {
                fragment = new SmartDialSearchFragment();
            } else {
                fragment = new RegularSearchFragment();
            }
        }
        transaction.replace(R.id.dialtacts_frame, fragment, tag);
        transaction.addToBackStack(null);
        fragment.setQueryString(query, false);
        transaction.commit();
    }

    /**
     * Hides the search fragment
     */
    private void exitSearchUi() {
        // See related bug in enterSearchUI();
        if (getFragmentManager().isDestroyed()) {
            return;
        }
        // Go all the way back to the favorites fragment, regardless of how many times we
        // transitioned between search fragments
        getFragmentManager().popBackStack(0, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        setNotInSearchUi();

        if (isDialpadShowing()) {
            mFragmentsFrame.setAlpha(0);
        }
    }

    /** Returns an Intent to launch Call Settings screen */
    public static Intent getCallSettingsIntent() {
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            intent.setClassName(PHONE_PACKAGE, MSIM_CALL_SETTINGS_CLASS_NAME);
        } else {
            intent.setClassName(PHONE_PACKAGE, CALL_SETTINGS_CLASS_NAME);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    @Override
    public void onBackPressed() {
        if (mDialpadFragment != null && mDialpadFragment.isVisible()) {
            hideDialpadFragment(true, false);
        } else if (getInSearchUi()) {
            mSearchView.setText(null);
            mDialpadFragment.clearDialpad();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDialpadQueryChanged(String query) {
        final String normalizedQuery = query;
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
                return;
            }
            mSearchView.setText(normalizedQuery);
        }
    }

    @Override
    public void onListFragmentScrollStateChange(int scrollState) {
        if (scrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            hideDialpadFragment(true, false);
            hideInputMethod(getCurrentFocus());
        }
    }

    @Override
    public void setDialButtonEnabled(boolean enabled) {
        if (mDialButton != null) {
            mDialButton.setEnabled(enabled);
        }
    }

    @Override
    public void setDialButtonContainerVisible(boolean visible) {
        mFakeActionBar.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private boolean phoneIsInUse() {
        final TelephonyManager tm = (TelephonyManager) getSystemService(
                Context.TELEPHONY_SERVICE);
        return tm.getCallState() != TelephonyManager.CALL_STATE_IDLE;
    }

    @Override
    public void onShowAllContacts() {
        final Intent intent = new Intent(this, AllContactsActivity.class);
        startActivity(intent);
    }

    public static Intent getAddNumberToContactIntent(CharSequence text) {
        final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Intents.Insert.PHONE, text);
        intent.setType(Contacts.CONTENT_ITEM_TYPE);
        return intent;
    }

    private boolean canIntentBeHandled(Intent intent) {
        final PackageManager packageManager = getPackageManager();
        final List<ResolveInfo> resolveInfo = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return resolveInfo != null && resolveInfo.size() > 0;
    }

    @Override
    public void onDragStarted(int itemIndex, int x, int y, PhoneFavoriteTileView view) {
        crossfadeViews(mRemoveViewContainer, mSearchViewContainer, ANIMATION_DURATION);
    }

    @Override
    public void onDragHovered(int itemIndex, int x, int y) {}

    @Override
    public void onDragFinished(int x, int y) {
        crossfadeViews(mSearchViewContainer, mRemoveViewContainer, ANIMATION_DURATION);
    }

    @Override
    public void onDroppedOnRemove() {}

    /**
     * Allows the PhoneFavoriteFragment to attach the drag controller to mRemoveViewContainer
     * once it has been attached to the activity.
     */
    @Override
    public void setDragDropController(DragDropController dragController) {
        mRemoveViewContainer.setDragDropController(dragController);
    }

    /**
     * Crossfades two views so that the first one appears while the other one is fading
     * out of view.
     */
    private void crossfadeViews(final View fadeIn, final View fadeOut, int duration) {
        fadeOut.animate().alpha(0).setDuration(duration)
        .setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                fadeOut.setVisibility(View.GONE);
            }
        });

        fadeIn.setVisibility(View.VISIBLE);
        fadeIn.setAlpha(0);
        fadeIn.animate().alpha(1).setDuration(ANIMATION_DURATION)
                .setListener(null);
    }

    private boolean shouldShowOnscreenDialButton() {
        return getResources().getBoolean(R.bool.config_show_onscreen_dial_button);
    }
}
