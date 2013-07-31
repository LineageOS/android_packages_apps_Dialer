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
import android.app.backup.BackupManager;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents.UI;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewConfiguration;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView.OnScrollListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.SearchView;
import android.widget.SearchView.OnCloseListener;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.Toast;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.activity.TransactionSafeActivity;
import com.android.contacts.common.dialog.ClearFrequentsDialog;
import com.android.contacts.common.interactions.ImportExportDialogFragment;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.common.list.PhoneNumberPickerFragment;
import com.android.dialer.calllog.NewCallLogActivity;
import com.android.dialer.dialpad.NewDialpadFragment;
import com.android.dialer.dialpad.SmartDialNameMatcher;
import com.android.dialer.interactions.PhoneNumberInteraction;
import com.android.dialer.list.NewPhoneFavoriteFragment;
import com.android.dialer.list.OnListFragmentScrolledListener;
import com.android.dialer.list.SmartDialSearchFragment;
import com.android.internal.telephony.ITelephony;

import java.util.ArrayList;

/**
 * The dialer tab's title is 'phone', a more common name (see strings.xml).
 *
 * TODO krelease: All classes currently prefixed with New will replace the original classes or
 * be renamed more appropriately before shipping.
 */
public class NewDialtactsActivity extends TransactionSafeActivity implements View.OnClickListener,
        NewDialpadFragment.OnDialpadQueryChangedListener, PopupMenu.OnMenuItemClickListener,
        OnListFragmentScrolledListener,
        NewPhoneFavoriteFragment.OnPhoneFavoriteFragmentStartedListener,
        NewDialpadFragment.OnDialpadFragmentStartedListener {
    private static final String TAG = "DialtactsActivity";

    public static final boolean DEBUG = false;

    /** Used to open Call Setting */
    private static final String PHONE_PACKAGE = "com.android.phone";
    private static final String CALL_SETTINGS_CLASS_NAME =
            "com.android.phone.CallFeaturesSetting";

    /** @see #getCallOrigin() */
    private static final String CALL_ORIGIN_DIALTACTS =
            "com.android.dialer.DialtactsActivity";

    private static final String TAG_DIALPAD_FRAGMENT = "dialpad";
    private static final String TAG_REGULAR_SEARCH_FRAGMENT = "search";
    private static final String TAG_SMARTDIAL_SEARCH_FRAGMENT = "smartdial";
    private static final String TAG_FAVORITES_FRAGMENT = "favorites";

    /**
     * Just for backward compatibility. Should behave as same as {@link Intent#ACTION_DIAL}.
     */
    private static final String ACTION_TOUCH_DIALER = "com.android.phone.action.TOUCH_DIALER";

    private static final int SUBACTIVITY_ACCOUNT_FILTER = 1;

    private static final int ACTIVITY_REQUEST_CODE_VOICE_SEARCH = 1;

    private String mFilterText;

    /**
     * The main fragment displaying the user's favorites and frequent contacts
     */
    private NewPhoneFavoriteFragment mPhoneFavoriteFragment;

    /**
     * Fragment containing the dialpad that slides into view
     */
    private NewDialpadFragment mDialpadFragment;

    /**
     * Fragment for searching phone numbers using the alphanumeric keyboard.
     */
    private NewSearchFragment mRegularSearchFragment;

    /**
     * Fragment for searching phone numbers using the dialpad.
     */
    private SmartDialSearchFragment mSmartDialSearchFragment;

    private View mMenuButton;
    private View mCallHistoryButton;
    private View mDialpadButton;

    // Padding view used to shift the fragments up when the dialpad is shown.
    private View mBottomPaddingView;

    /**
     * True when this Activity is in its search UI (with a {@link SearchView} and
     * {@link PhoneNumberPickerFragment}).
     */
    private boolean mInSearchUi;
    private View mSearchViewContainer;
    private View mSearchViewCloseButton;
    private View mVoiceSearchButton;
    private EditText mSearchView;

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
                        NewDialtactsActivity.this, dataUri, getCallOrigin());
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
                // TODO krelease: populate the search fragments with the correct
                // search query at the correct point in time of the fragment lifecycle.
                // The current behavior is to simply return to the favorites screen
                // (when docked), or returning to the Dialer after it has been
                // swapped out of memory.
                if (mDialpadFragment == null) return;
                final boolean smartDialSearch = isDialpadShowing();
                final String newText = s.toString();
                // Show search result with non-empty text. Show a bare list otherwise.
                if (TextUtils.isEmpty(newText) && mInSearchUi) {
                    exitSearchUi();
                    mSearchViewCloseButton.setVisibility(View.GONE);
                    return;
                } else if (!TextUtils.isEmpty(newText) && !mInSearchUi) {
                    enterSearchUi(smartDialSearch);
                }

                if (smartDialSearch) {
                    mSmartDialSearchFragment.setQueryString(newText, false);
                } else {
                    mRegularSearchFragment.setQueryString(newText, false);
                }
                mSearchViewCloseButton.setVisibility(View.VISIBLE);
                return;
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
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        fixIntent(intent);

        setContentView(R.layout.new_dialtacts_activity);

        getActionBar().hide();

        if (savedInstanceState == null) {
            mPhoneFavoriteFragment = new NewPhoneFavoriteFragment();
            mPhoneFavoriteFragment.setRetainInstance(true);
            mPhoneFavoriteFragment.setListener(mPhoneFavoriteListener);

            mRegularSearchFragment = new NewSearchFragment();
            mSmartDialSearchFragment = new SmartDialSearchFragment();
            mDialpadFragment = new NewDialpadFragment();

            // TODO krelease: load fragments on demand instead of creating all of them at run time
            final FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.add(R.id.dialtacts_frame, mPhoneFavoriteFragment, TAG_FAVORITES_FRAGMENT);
            ft.add(R.id.dialtacts_frame, mRegularSearchFragment, TAG_REGULAR_SEARCH_FRAGMENT);
            ft.add(R.id.dialtacts_frame, mSmartDialSearchFragment, TAG_SMARTDIAL_SEARCH_FRAGMENT);
            ft.add(R.id.dialtacts_container, mDialpadFragment, TAG_DIALPAD_FRAGMENT);
            // Fragments will be hidden as necessary in onAttachFragment
            ft.commit();
        }

        mBottomPaddingView = findViewById(R.id.dialtacts_bottom_padding);
        prepareSearchView();

        displayFragment(intent);

        if (UI.FILTER_CONTACTS_ACTION.equals(intent.getAction())
                && savedInstanceState == null) {
            setupFilterText(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        final FragmentManager fm = getFragmentManager();
        mPhoneFavoriteFragment = (NewPhoneFavoriteFragment) fm.findFragmentByTag(
                TAG_FAVORITES_FRAGMENT);
        mDialpadFragment = (NewDialpadFragment) fm.findFragmentByTag(TAG_DIALPAD_FRAGMENT);

        mRegularSearchFragment = (NewSearchFragment) fm.findFragmentByTag(
                TAG_REGULAR_SEARCH_FRAGMENT);
        mRegularSearchFragment.setOnPhoneNumberPickerActionListener(
                mPhoneNumberPickerActionListener);

        mSmartDialSearchFragment = (SmartDialSearchFragment) fm.findFragmentByTag(
                TAG_SMARTDIAL_SEARCH_FRAGMENT);
        mSmartDialSearchFragment.setOnPhoneNumberPickerActionListener(
                mPhoneNumberPickerActionListener);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof NewDialpadFragment || fragment instanceof NewSearchFragment
                || fragment instanceof SmartDialSearchFragment) {
            final FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.hide(fragment);
            transaction.commit();
        }
        // TODO krelease: Save some kind of state here to show the appropriate fragment
        // based on the state of the dialer when it was last paused
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
            case R.id.add_contact:
                try {
                    startActivity(new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI));
                } catch (ActivityNotFoundException e) {
                    Toast toast = Toast.makeText(this, R.string.add_contact_not_available,
                            Toast.LENGTH_SHORT);
                    toast.show();
                }
                return true;
            case R.id.menu_call_settings:
                final Intent settingsIntent = DialtactsActivity.getCallSettingsIntent();
                startActivity(settingsIntent);
        }
        return false;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.overflow_menu_on_dialpad:
            case R.id.overflow_menu: {
                final PopupMenu popupMenu = new PopupMenu(NewDialtactsActivity.this, view);
                final Menu menu = popupMenu.getMenu();
                popupMenu.inflate(R.menu.dialtacts_options_new);
                popupMenu.setOnMenuItemClickListener(this);
                popupMenu.show();
                break;
            }
            case R.id.dialpad_button:
                showDialpadFragment(true);
                break;
            case R.id.call_history_on_dialpad_button:
            case R.id.call_history_button:
                // TODO krelease: This should start an intent with content type
                // CallLog.Calls.CONTENT_TYPE, once the intent filters for the call log activity
                // is enabled
                final Intent intent = new Intent(this, NewCallLogActivity.class);
                startActivity(intent);
                break;
            case R.id.search_close_button:
                // Clear the search field
                if (!TextUtils.isEmpty(mSearchView.getText())) {
                    mSearchView.setText("");
                }
                break;
            case R.id.voice_search_button:
                final Intent voiceIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                startActivityForResult(voiceIntent, ACTIVITY_REQUEST_CODE_VOICE_SEARCH);
                break;
            default: {
                Log.wtf(TAG, "Unexpected onClick event from " + view);
                break;
            }
        }
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
        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        if (animate) {
            ft.setCustomAnimations(R.anim.slide_in, 0);
        }
        ft.show(mDialpadFragment);
        ft.commit();
    }

    private void hideDialpadFragment(boolean animate) {
        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        if (animate) {
            ft.setCustomAnimations(0, R.anim.slide_out);
        }
        ft.hide(mDialpadFragment);
        ft.commit();
    }

    private void prepareSearchView() {
        mSearchViewContainer = findViewById(R.id.search_view_container);
        mSearchViewCloseButton = findViewById(R.id.search_close_button);
        mSearchViewCloseButton.setOnClickListener(this);
        mVoiceSearchButton = findViewById(R.id.voice_search_button);
        mVoiceSearchButton.setOnClickListener(this);
        mSearchView = (EditText) findViewById(R.id.search_view);
        mSearchView.addTextChangedListener(mPhoneSearchQueryTextListener);
        mSearchView.setHint(getString(R.string.dialer_hint_find_contact));
        mSearchView.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus) {
                    showInputMethod(view.findFocus());
                }
            }
        });
    }

    private void hideDialpadFragmentIfNecessary() {
        if (mDialpadFragment.isVisible()) {
            hideDialpadFragment(true);
        }
    }

    final AnimatorListener mHideListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            mSearchViewContainer.setVisibility(View.GONE);
        }
    };

    public void hideSearchBar() {
        // If the favorites fragment hasn't been fully created before the dialpad fragment
        // is hidden (i.e. onResume), don't bother animating
        if (mPhoneFavoriteFragment == null || mPhoneFavoriteFragment.getView() == null) {
            return;
        }
        mSearchViewContainer.animate().cancel();
        mSearchViewContainer.setAlpha(1);
        mSearchViewContainer.setTranslationY(0);
        mSearchViewContainer.animate().withLayer().alpha(0).translationY(-mSearchView.getHeight())
                .setDuration(200).setListener(mHideListener);

        mPhoneFavoriteFragment.getView().animate().withLayer()
                .translationY(-mSearchViewContainer.getHeight()).setDuration(200).setListener(
                    new AnimatorListenerAdapter() {
                    @Override
                        public void onAnimationEnd(Animator animation) {
                            mBottomPaddingView.setVisibility(View.VISIBLE);
                            mPhoneFavoriteFragment.getView().setTranslationY(0);
                        }
                    });
    }

    public void showSearchBar() {
        // If the favorites fragment hasn't been fully created before the dialpad fragment
        // is hidden (i.e. onResume), don't bother animating
        if (mPhoneFavoriteFragment == null || mPhoneFavoriteFragment.getView() == null) {
            return;
        }
        mSearchViewContainer.animate().cancel();
        mSearchViewContainer.setAlpha(0);
        mSearchViewContainer.setTranslationY(-mSearchViewContainer.getHeight());
        mSearchViewContainer.animate().withLayer().alpha(1).translationY(0).setDuration(200)
                .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                        mSearchViewContainer.setVisibility(View.VISIBLE);
                        }
                });

        mPhoneFavoriteFragment.getView().setTranslationY(-mSearchViewContainer.getHeight());
        mPhoneFavoriteFragment.getView().animate().withLayer().translationY(0).setDuration(200)
                .setListener(
                        new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationStart(Animator animation) {
                                    mBottomPaddingView.setVisibility(View.GONE);
                                }
                        });
    }


    public void setupFakeActionBarItemsForFavoritesFragment() {
        mMenuButton = findViewById(R.id.overflow_menu);
        if (mMenuButton != null) {
            mMenuButton.setOnClickListener(this);
        }

        mCallHistoryButton = findViewById(R.id.call_history_button);
        // mCallHistoryButton.setMinimumWidth(fakeMenuItemWidth);
        mCallHistoryButton.setOnClickListener(this);

        mDialpadButton = findViewById(R.id.dialpad_button);
        // DialpadButton.setMinimumWidth(fakeMenuItemWidth);
        mDialpadButton.setOnClickListener(this);
    }

    public void setupFakeActionBarItemsForDialpadFragment() {
        final View overflowButton = findViewById(R.id.overflow_menu_on_dialpad);
        overflowButton.setOnClickListener(this);
        final View callhistoryButton = findViewById(R.id.call_history_on_dialpad_button);
        callhistoryButton.setOnClickListener(this);
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
        // TODO krelease: Make navigation via intent work by displaying the correct fragment
        // as appropriate.

        // If we got here by hitting send and we're in call forward along to the in-call activity
        boolean recentCallsRequest = Calls.CONTENT_TYPE.equals(intent.resolveType(
            getContentResolver()));
        if (isSendKeyWhileInCall(intent, recentCallsRequest)) {
            finish();
            return;
        }

        if ((mDialpadFragment != null && phoneIsInUse())
                || isDialIntent(intent)) {
            mDialpadFragment.setStartedFromNewIntent(true);
            // TODO krelease: This should use showDialpadFragment(false) to avoid animating
            // the dialpad in. Need to fix the onPreDrawListener in NewDialpadFragment first.
            showDialpadFragment(true);
        }
    }

    @Override
    public void onNewIntent(Intent newIntent) {
        setIntent(newIntent);
        fixIntent(newIntent);
        displayFragment(newIntent);
        final String action = newIntent.getAction();

        if (mInSearchUi || (mRegularSearchFragment != null && mRegularSearchFragment.isVisible())) {
            exitSearchUi();
        }

        // TODO krelease: Handle onNewIntent for all other fragments
        /*
         *if (mViewPager.getCurrentItem() == TAB_INDEX_DIALER) { if (mDialpadFragment != null) {
         * mDialpadFragment.setStartedFromNewIntent(true); } else { Log.e(TAG,
         * "DialpadFragment isn't ready yet when the tab is already selected."); } } else if
         * (mViewPager.getCurrentItem() == TAB_INDEX_CALL_LOG) { if (mCallLogFragment != null) {
         * mCallLogFragment.configureScreenFromIntent(newIntent); } else { Log.e(TAG,
         * "CallLogFragment isn't ready yet when the tab is already selected."); } }
         */
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

    private final NewPhoneFavoriteFragment.Listener mPhoneFavoriteListener =
            new NewPhoneFavoriteFragment.Listener() {
        @Override
        public void onContactSelected(Uri contactUri) {
            PhoneNumberInteraction.startInteractionForPhoneCall(
                        NewDialtactsActivity.this, contactUri, getCallOrigin());
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
    private void enterSearchUi(boolean smartDialSearch) {
        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        transaction.hide(mPhoneFavoriteFragment);
        if (smartDialSearch) {
            transaction.show(mSmartDialSearchFragment);
        } else {
            transaction.show(mRegularSearchFragment);
        }
        transaction.commit();

        mInSearchUi = true;
    }

    /**
     * Hides the search fragment
     */
    private void exitSearchUi() {
        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        transaction.hide(mRegularSearchFragment);
        transaction.hide(mSmartDialSearchFragment);
        transaction.show(mPhoneFavoriteFragment);
        transaction.commit();
        mInSearchUi = false;
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
        if (mDialpadFragment.isVisible()) {
            hideDialpadFragment(true);
        } else if (mInSearchUi) {
            mSearchView.setText(null);
        } else if (isTaskRoot()) {
            // Instead of stopping, simply push this to the back of the stack.
            // This is only done when running at the top of the stack;
            // otherwise, we have been launched by someone else so need to
            // allow the user to go back to the caller.
            moveTaskToBack(false);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDialpadQueryChanged(String query) {
        final String normalizedQuery = SmartDialNameMatcher.normalizeNumber(query,
                SmartDialNameMatcher.LATIN_SMART_DIAL_MAP);
        if (!TextUtils.equals(mSearchView.getText(), normalizedQuery)) {
            mSearchView.setText(normalizedQuery);
        }
    }

    @Override
    public void onListFragmentScrollStateChange(int scrollState) {
        if (scrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            hideDialpadFragmentIfNecessary();
            hideInputMethod(getCurrentFocus());
        }
    }

    @Override
    public void onPhoneFavoriteFragmentStarted() {
        setupFakeActionBarItemsForFavoritesFragment();
    }

    @Override
    public void onDialpadFragmentStarted() {
        setupFakeActionBarItemsForDialpadFragment();
    }

    private boolean phoneIsInUse() {
        final TelephonyManager tm = (TelephonyManager) getSystemService(
                Context.TELEPHONY_SERVICE);
        return tm.getCallState() != TelephonyManager.CALL_STATE_IDLE;
    }
}
