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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.speech.RecognizerIntent;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView.OnScrollListener;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
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
import com.android.dialer.list.ListsFragment;
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
        DialpadFragment.OnDialpadQueryChangedListener,
        OnListFragmentScrolledListener,
        DialpadFragment.HostInterface,
        PhoneFavoriteFragment.OnShowAllContactsListener,
        PhoneFavoriteFragment.HostInterface,
        OnDragDropListener, View.OnLongClickListener,
        OnPhoneNumberPickerActionListener {
    private static final String TAG = "DialtactsActivity";

    public static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public static final String SHARED_PREFS_NAME = "com.android.dialer_preferences";

    /** Used to open Call Setting */
    private static final String PHONE_PACKAGE = "com.android.phone";
    private static final String CALL_SETTINGS_CLASS_NAME =
            "com.android.phone.CallFeaturesSetting";
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

    private static final int ANIMATION_DURATION = 250;

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

    /**
     * Fragment containing the speed dial list, recents list, and all contacts list.
     */
    private ListsFragment mListsFragment;

    private View mFakeActionBar;
    private View mMenuButton;
    private View mCallHistoryButton;
    private View mDialpadButton;
    private View mDialButton;
    private PopupMenu mDialpadOverflowMenu;

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

    // This view points to the Framelayout that houses both the search view and remove view
    // containers.
    private View mSearchAndRemoveViewContainer;
    private SearchView mSearchView;
    /**
     * View that contains the "Remove" dialog that shows up when the user long presses a contact.
     * If the user releases a contact when hovering on top of this, the contact is unfavorited and
     * removed from the speed dial list.
     */
    private RemoveView mRemoveViewContainer;

    final Interpolator hideActionBarInterpolator = new AccelerateInterpolator(1.5f);
    final Interpolator showActionBarInterpolator = new DecelerateInterpolator(1.5f);
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
            // TODO: Check mPhoneFavoriteFragment.hasFrequents()
            clearFrequents.setVisible(true);
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
    private final OnQueryTextListener mPhoneSearchQueryTextListener = new OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.equals(mSearchQuery)) {
                    // If the query hasn't changed (perhaps due to activity being destroyed
                    // and restored, or user launching the same DIAL intent twice), then there is
                    // no need to do anything here.
                    return true;
                }
                mSearchQuery = newText;
                if (DEBUG) {
                    Log.d(TAG, "onTextChange for mSearchView called with new query: " + newText);
                }
                final boolean dialpadSearch = isDialpadShowing();

                // Show search result with non-empty text. Show a bare list otherwise.
                if (TextUtils.isEmpty(newText) && getInSearchUi()) {
                    exitSearchUi();
                    return true;
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
                    } else if (mRegularSearchFragment != null) {
                        mRegularSearchFragment.setQueryString(newText, false);
                    }
                    return true;
                }
                return true;
            }
    };

    private boolean isDialpadShowing() {
        return mDialpadFragment != null && mDialpadFragment.isVisible();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFirstLaunch = true;

        setContentView(R.layout.dialtacts_activity);
        getWindow().setBackgroundDrawable(null);

        getActionBar().setDisplayShowHomeEnabled(false);
        getActionBar().setDisplayShowTitleEnabled(false);

        // Add the favorites fragment, and the dialpad fragment, but only if savedInstanceState
        // is null. Otherwise the fragment manager takes care of recreating these fragments.
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.dialtacts_frame, new ListsFragment(), TAG_FAVORITES_FRAGMENT)
                    .add(R.id.dialtacts_container, new DialpadFragment(), TAG_DIALPAD_FRAGMENT)
                    .commit();
        } else {
            mSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY);
            mInRegularSearch = savedInstanceState.getBoolean(KEY_IN_REGULAR_SEARCH_UI);
            mInDialpadSearch = savedInstanceState.getBoolean(KEY_IN_DIALPAD_SEARCH_UI);
            mFirstLaunch = savedInstanceState.getBoolean(KEY_FIRST_LAUNCH);
        }

        mFragmentsFrame = findViewById(R.id.dialtacts_frame);

        mFakeActionBar = findViewById(R.id.fake_action_bar);

        mCallHistoryButton = findViewById(R.id.call_history_button);
        mCallHistoryButton.setOnClickListener(this);
        mDialButton = findViewById(R.id.dial_button);
        mDialButton.setOnClickListener(this);
        mDialpadButton = findViewById(R.id.dialpad_button);
        mDialpadButton.setOnClickListener(this);
        mMenuButton = findViewById(R.id.overflow_menu_button);
        mMenuButton.setOnClickListener(this);

        mRemoveViewContainer = (RemoveView) findViewById(R.id.remove_view_container);
        mSearchAndRemoveViewContainer = findViewById(R.id.search_and_remove_view_container);

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
        } else if (fragment instanceof ListsFragment) {
            mListsFragment = (ListsFragment) fragment;
        }
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
            case R.id.overflow_menu_button:
                mDialpadOverflowMenu.show();
                break;
            case R.id.dialpad_button:
                // Reset the boolean flag that tracks whether the dialpad was up because
                // we were in call. Regardless of whether it was true before, we want to
                // show the dialpad because the user has explicitly clicked the dialpad
                // button.
                mInCallDialpadUp = false;
                showDialpadFragment(true);
                break;
            case R.id.call_history_button:
                // Use explicit CallLogActivity intent instead of ACTION_VIEW +
                // CONTENT_TYPE, so that we always open our call log from our dialer
                final Intent intent = new Intent(this, CallLogActivity.class);
                startActivity(intent);
                break;
            case R.id.dial_button:
                // Dial button was pressed; tell the Dialpad fragment
                mDialpadFragment.dialButtonPressed();
                break;
            case R.id.search_close_button:
                // Clear the search field
                if (!TextUtils.isEmpty(mSearchView.getQuery())) {
                    mDialpadFragment.clearDialpad();
                    mSearchView.setQuery("", false);
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
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_history:
                // Use explicit CallLogActivity intent instead of ACTION_VIEW +
                // CONTENT_TYPE, so that we always open our call log from our dialer
                final Intent intent = new Intent(this, CallLogActivity.class);
                startActivity(intent);
                break;
            case R.id.menu_add_contact:
                try {
                    startActivity(new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI));
                } catch (ActivityNotFoundException e) {
                    Toast toast = Toast.makeText(this,
                            R.string.add_contact_not_available,
                            Toast.LENGTH_SHORT);
                    toast.show();
                }
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
                // TODO: This should be enabled/disabled based on
                // PhoneFavoritesFragments.hasFrequents
                ClearFrequentsDialog.show(getFragmentManager());
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
                    mSearchView.setQuery(match, false);
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
        mSearchView.setQuery("", false);
        hideDialpadFragment(false, true);
    }

    /**
     * Callback from child DialpadFragment when the dialpad is shown.
     */
    public void onDialpadShown() {
        mDialButton.setVisibility(View.VISIBLE);
        mDialpadButton.setVisibility(View.GONE);
        mMenuButton.setVisibility(View.VISIBLE);
        if (mDialpadOverflowMenu == null) {
            mDialpadOverflowMenu = mDialpadFragment.buildOptionsMenu(mMenuButton);
            mMenuButton.setOnTouchListener(mDialpadOverflowMenu.getDragToOpenListener());
        }

        SearchFragment fragment = null;
        if (mInDialpadSearch) {
            fragment = mSmartDialSearchFragment;
        } else if (mInRegularSearch) {
            fragment = mRegularSearchFragment;
        }
        if (fragment != null && fragment.isVisible()) {
            fragment.getListView().animate().translationY(-getActionBar().getHeight())
                    .setInterpolator(hideActionBarInterpolator).setDuration(ANIMATION_DURATION);
        }

        if (mListsFragment != null && mListsFragment.isVisible()) {
            // If the favorites fragment is showing, fade to blank.
            mFragmentsFrame.animate().alpha(0.0f);
        }
        getActionBar().hide();
    }

    /**
     * Callback from child DialpadFragment when the dialpad is hidden.
     */
    public void onDialpadHidden() {
        mDialButton.setVisibility(View.GONE);
        mDialpadButton.setVisibility(View.VISIBLE);
        mMenuButton.setVisibility(View.GONE);

        SearchFragment fragment = null;
        if (mInDialpadSearch) {
            fragment = mSmartDialSearchFragment;
        } else if (mInRegularSearch) {
            fragment = mRegularSearchFragment;
        }
        if (fragment != null && fragment.isVisible()) {
            fragment.getListView().animate().translationY(0)
                    .setInterpolator(showActionBarInterpolator).setDuration(ANIMATION_DURATION);
        }

        if (mListsFragment != null && mListsFragment.isVisible()) {
            mFragmentsFrame.animate().alpha(1.0f);
        }
        getActionBar().show();
    }

    private void hideInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu");
        }
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.dialtacts_options, menu);
        final MenuItem searchItem = menu.findItem(R.id.menu_search);
        mSearchView = (SearchView) searchItem.getActionView();
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        mSearchView.setOnQueryTextListener(mPhoneSearchQueryTextListener);
        mSearchView.setIconifiedByDefault(false);
        return super.onCreateOptionsMenu(menu);
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

        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (callKey && phone != null && phone.showCallScreen()) {
                return true;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to handle send while in call", e);
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

        fragment = (SearchFragment) getFragmentManager().findFragmentByTag(tag);
        if (fragment == null) {
            if (smartDialSearch) {
                fragment = new SmartDialSearchFragment();
            } else {
                fragment = new RegularSearchFragment();
            }
        }
        // DialtactsActivity will provide the options menu
        fragment.setHasOptionsMenu(false);
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
        intent.setClassName(PHONE_PACKAGE, CALL_SETTINGS_CLASS_NAME);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    @Override
    public void onBackPressed() {
        if (mDialpadFragment != null && mDialpadFragment.isVisible()) {
            hideDialpadFragment(true, false);
        } else if (getInSearchUi()) {
            mSearchView.setQuery(null, false);
            mDialpadFragment.clearDialpad();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDialpadQueryChanged(String query) {
        if (mSmartDialSearchFragment != null) {
            mSmartDialSearchFragment.setAddToContactNumber(query);
        }
        final String normalizedQuery = SmartDialNameMatcher.normalizeNumber(query,
                SmartDialNameMatcher.LATIN_SMART_DIAL_MAP);
        if (!TextUtils.equals(mSearchView.getQuery(), normalizedQuery)) {
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
            if (mDialpadFragment != null) {
                mDialpadOverflowMenu = mDialpadFragment.buildOptionsMenu(mMenuButton);
            }
            mSearchView.setQuery(normalizedQuery, false);
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
    public void onListFragmentScroll(int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
        // TODO: No-op for now. This should eventually show/hide the actionBar based on
        // interactions with the ListsFragments.
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

    /**
     * Called when the user has long-pressed a contact tile to start a drag operation.
     */
    @Override
    public void onDragStarted(int itemIndex, int x, int y, PhoneFavoriteTileView view) {
        getActionBar().hide();
        mSearchAndRemoveViewContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDragHovered(int itemIndex, int x, int y) {}

    /**
     * Called when the user has released a contact tile after long-pressing it.
     */
    @Override
    public void onDragFinished(int x, int y) {
        getActionBar().show();
        mSearchAndRemoveViewContainer.setVisibility(View.GONE);
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

    @Override
    public void onPickPhoneNumberAction(Uri dataUri) {
        mPhoneNumberPickerActionListener.onPickPhoneNumberAction(dataUri);
    }

    @Override
    public void onCallNumberDirectly(String phoneNumber) {
        mPhoneNumberPickerActionListener.onCallNumberDirectly(phoneNumber);
    }

    @Override
    public void onShortcutIntentCreated(Intent intent) {
        mPhoneNumberPickerActionListener.onShortcutIntentCreated(intent);
    }

    @Override
    public void onHomeInActionBarSelected() {
        mPhoneNumberPickerActionListener.onHomeInActionBarSelected();
    }
}
