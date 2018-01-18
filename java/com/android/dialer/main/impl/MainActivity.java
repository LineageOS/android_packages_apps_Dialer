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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.QuickContact;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;
import com.android.dialer.calllog.ui.NewCallLogFragment;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.CompatUtils;
import com.android.dialer.contactsfragment.ContactsFragment;
import com.android.dialer.contactsfragment.ContactsFragment.Header;
import com.android.dialer.contactsfragment.ContactsFragment.OnContactSelectedListener;
import com.android.dialer.database.Database;
import com.android.dialer.dialpadview.DialpadFragment;
import com.android.dialer.dialpadview.DialpadFragment.DialpadListener;
import com.android.dialer.dialpadview.DialpadFragment.LastOutgoingCallCallback;
import com.android.dialer.dialpadview.DialpadFragment.OnDialpadQueryChangedListener;
import com.android.dialer.main.impl.BottomNavBar.OnBottomNavTabSelectedListener;
import com.android.dialer.main.impl.toolbar.MainToolbar;
import com.android.dialer.searchfragment.list.NewSearchFragment.SearchFragmentListener;
import com.android.dialer.smartdial.util.SmartDialPrefix;
import com.android.dialer.speeddial.SpeedDialFragment;
import com.android.dialer.voicemail.listui.NewVoicemailFragment;

/** This is the main activity for dialer. It hosts favorites, call log, search, dialpad, etc... */
public final class MainActivity extends AppCompatActivity
    implements OnContactSelectedListener,
        OnDialpadQueryChangedListener,
        DialpadListener,
        DialpadFragment.HostInterface,
        SearchFragmentListener {

  private static final String IS_FAB_HIDDEN_KEY = "is_fab_hidden";
  private static final String KEY_SAVED_LANGUAGE_CODE = "saved_language_code";

  private MainSearchController searchController;
  private FloatingActionButton fab;

  /** Language the device was in last time {@link #onSaveInstanceState(Bundle)} was called. */
  private String savedLanguageCode;

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
    initLayout();
    SmartDialPrefix.initializeNanpSettings(this);
  }

  private void initLayout() {
    fab = findViewById(R.id.fab);
    fab.setOnClickListener(v -> searchController.showDialpad(true));

    MainToolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(findViewById(R.id.toolbar));

    BottomNavBar navBar = findViewById(R.id.bottom_nav_bar);
    navBar.setOnTabSelectedListener(new MainBottomNavBarBottomNavTabListener());
    // TODO(calderwoodra): Implement last tab
    navBar.selectTab(BottomNavBar.TabIndex.SPEED_DIAL);

    searchController = new MainSearchController(this, navBar, fab, toolbar);
    toolbar.setSearchBarListener(searchController);
  }

  @Override
  protected void onResume() {
    super.onResume();
    // Start the thread that updates the smart dial database if the activity is recreated with a
    // language change.
    boolean forceUpdate = !CompatUtils.getLocale(this).getISO3Language().equals(savedLanguageCode);
    Database.get(this).getDatabaseHelper(this).startSmartDialUpdateThread(forceUpdate);
  }

  @Override
  protected void onSaveInstanceState(Bundle bundle) {
    super.onSaveInstanceState(bundle);
    bundle.putBoolean(IS_FAB_HIDDEN_KEY, !fab.isShown());
    bundle.putString(KEY_SAVED_LANGUAGE_CODE, CompatUtils.getLocale(this).getISO3Language());
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    if (savedInstanceState.getBoolean(IS_FAB_HIDDEN_KEY, false)) {
      fab.hide();
    }
    savedLanguageCode = savedInstanceState.getString(KEY_SAVED_LANGUAGE_CODE);
  }

  @Override
  public void onContactSelected(ImageView photo, Uri contactUri, long contactId) {
    // TODO(calderwoodra): Add impression logging
    QuickContact.showQuickContact(
        this, photo, contactUri, QuickContact.MODE_LARGE, null /* excludeMimes */);
  }

  @Override // OnDialpadQueryChangedListener
  public void onDialpadQueryChanged(String query) {
    searchController.onDialpadQueryChanged(query);
  }

  @Override // DialpadListener
  public void getLastOutgoingCall(LastOutgoingCallCallback callback) {
    // TODO(calderwoodra): migrate CallLogAsync class outside of dialer/app and call it here.
  }

  @Override // DialpadListener
  public void onDialpadShown() {
    searchController.onDialpadShown();
  }

  @Override // DialpadListener
  public void onCallPlacedFromDialpad() {
    // TODO(calderwoodra): logging
  }

  @Override
  public void onBackPressed() {
    if (searchController.onBackPressed()) {
      return;
    }
    super.onBackPressed();
  }

  @Override // DialpadFragment.HostInterface
  public boolean onDialpadSpacerTouchWithEmptyQuery() {
    searchController.onBackPressed();
    return true;
  }

  @Override // SearchFragmentListener
  public void onSearchListTouch() {
    searchController.onBackPressed();
  }

  @Override // SearchFragmentListener
  public void onCallPlacedFromSearch() {
    // TODO(calderwoodra): logging
  }

  /**
   * Implementation of {@link OnBottomNavTabSelectedListener} that handles logic for showing each of
   * the main tabs.
   */
  private final class MainBottomNavBarBottomNavTabListener
      implements OnBottomNavTabSelectedListener {

    private static final String SPEED_DIAL_TAG = "speed_dial";
    private static final String CALL_LOG_TAG = "call_log";
    private static final String CONTACTS_TAG = "contacts";
    private static final String VOICEMAIL_TAG = "voicemail";

    @Override
    public void onSpeedDialSelected() {
      hideAllFragments();
      SpeedDialFragment fragment =
          (SpeedDialFragment) getFragmentManager().findFragmentByTag(SPEED_DIAL_TAG);
      if (fragment == null) {
        getFragmentManager()
            .beginTransaction()
            .add(R.id.fragment_container, SpeedDialFragment.newInstance(), SPEED_DIAL_TAG)
            .commit();
      } else {
        getFragmentManager().beginTransaction().show(fragment).commit();
      }
    }

    @Override
    public void onCallLogSelected() {
      hideAllFragments();
      NewCallLogFragment fragment =
          (NewCallLogFragment) getSupportFragmentManager().findFragmentByTag(CALL_LOG_TAG);
      if (fragment == null) {
        getSupportFragmentManager()
            .beginTransaction()
            .add(R.id.fragment_container, new NewCallLogFragment(), CALL_LOG_TAG)
            .commit();
      } else {
        getSupportFragmentManager().beginTransaction().show(fragment).commit();
      }
    }

    @Override
    public void onContactsSelected() {
      hideAllFragments();
      ContactsFragment fragment =
          (ContactsFragment) getFragmentManager().findFragmentByTag(CONTACTS_TAG);
      if (fragment == null) {
        getFragmentManager()
            .beginTransaction()
            .add(
                R.id.fragment_container,
                ContactsFragment.newInstance(Header.ADD_CONTACT),
                CONTACTS_TAG)
            .commit();
      } else {
        getFragmentManager().beginTransaction().show(fragment).commit();
      }
    }

    @Override
    public void onVoicemailSelected() {
      hideAllFragments();
      NewVoicemailFragment fragment =
          (NewVoicemailFragment) getSupportFragmentManager().findFragmentByTag(VOICEMAIL_TAG);
      if (fragment == null) {
        getSupportFragmentManager()
            .beginTransaction()
            .add(R.id.fragment_container, new NewVoicemailFragment(), VOICEMAIL_TAG)
            .commit();
      } else {
        getSupportFragmentManager().beginTransaction().show(fragment).commit();
      }
    }

    private void hideAllFragments() {
      FragmentTransaction supportTransaction = getSupportFragmentManager().beginTransaction();
      if (getSupportFragmentManager().findFragmentByTag(CALL_LOG_TAG) != null) {
        supportTransaction.hide(getSupportFragmentManager().findFragmentByTag(CALL_LOG_TAG));
      }
      if (getSupportFragmentManager().findFragmentByTag(VOICEMAIL_TAG) != null) {
        supportTransaction.hide(getSupportFragmentManager().findFragmentByTag(VOICEMAIL_TAG));
      }
      supportTransaction.commit();

      android.app.FragmentTransaction transaction = getFragmentManager().beginTransaction();
      if (getFragmentManager().findFragmentByTag(SPEED_DIAL_TAG) != null) {
        transaction.hide(getFragmentManager().findFragmentByTag(SPEED_DIAL_TAG));
      }
      if (getFragmentManager().findFragmentByTag(CONTACTS_TAG) != null) {
        transaction.hide(getFragmentManager().findFragmentByTag(CONTACTS_TAG));
      }
      transaction.commit();
    }
  }
}
