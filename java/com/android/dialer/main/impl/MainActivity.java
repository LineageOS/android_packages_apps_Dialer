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
import android.text.TextUtils;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.ImageView;
import com.android.dialer.calllog.ui.NewCallLogFragment;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.contactsfragment.ContactsFragment;
import com.android.dialer.contactsfragment.ContactsFragment.Header;
import com.android.dialer.contactsfragment.ContactsFragment.OnContactSelectedListener;
import com.android.dialer.dialpadview.DialpadFragment;
import com.android.dialer.dialpadview.DialpadFragment.DialpadListener;
import com.android.dialer.dialpadview.DialpadFragment.LastOutgoingCallCallback;
import com.android.dialer.dialpadview.DialpadFragment.OnDialpadQueryChangedListener;
import com.android.dialer.main.impl.BottomNavBar.OnBottomNavTabSelectedListener;
import com.android.dialer.main.impl.toolbar.MainToolbar;
import com.android.dialer.main.impl.toolbar.SearchBarListener;
import com.android.dialer.searchfragment.list.NewSearchFragment;
import com.android.dialer.speeddial.SpeedDialFragment;
import com.android.dialer.voicemail.listui.NewVoicemailFragment;

/** This is the main activity for dialer. It hosts favorites, call log, search, dialpad, etc... */
public final class MainActivity extends AppCompatActivity
    implements OnContactSelectedListener, OnDialpadQueryChangedListener, DialpadListener {

  private SearchController searchController;

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
  }

  private void initLayout() {
    FloatingActionButton fab = findViewById(R.id.fab);
    fab.setOnClickListener(v -> searchController.showDialpad(true));

    MainToolbar toolbar = findViewById(R.id.toolbar);
    toolbar.setSearchBarListener(new MainSearchBarListener());
    searchController = new SearchController(fab, toolbar);
    setSupportActionBar(findViewById(R.id.toolbar));

    BottomNavBar navBar = findViewById(R.id.bottom_nav_bar);
    navBar.setOnTabSelectedListener(new MainBottomNavBarBottomNavTabListener());
    // TODO(calderwoodra): Implement last tab
    navBar.selectTab(BottomNavBar.TabIndex.SPEED_DIAL);
  }

  @Override
  public void onContactSelected(ImageView photo, Uri contactUri, long contactId) {
    // TODO(calderwoodra): Add impression logging
    QuickContact.showQuickContact(
        this, photo, contactUri, QuickContact.MODE_LARGE, null /* excludeMimes */);
  }

  @Override // OnDialpadQueryChangedListener
  public void onDialpadQueryChanged(String query) {
    // TODO(calderwoodra): update search fragment
  }

  @Override // DialpadListener
  public void getLastOutgoingCall(LastOutgoingCallCallback callback) {
    // TODO(calderwoodra): migrate CallLogAsync class outside of dialer/app and call it here.
  }

  @Override // DialpadListener
  public void onDialpadShown() {
    searchController.getDialpadFragment().slideUp(true);
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

  /** Search controller for handling all the logic related to hiding/showing search and dialpad. */
  private final class SearchController {

    private static final String DIALPAD_FRAGMENT_TAG = "dialpad_fragment_tag";
    private static final String SEARCH_FRAGMENT_TAG = "search_fragment_tag";

    private final FloatingActionButton fab;
    private final MainToolbar toolbar;

    private boolean isDialpadVisible;
    private boolean isSearchVisible;

    private SearchController(FloatingActionButton fab, MainToolbar toolbar) {
      this.fab = fab;
      this.toolbar = toolbar;
    }

    /** Shows the dialpad, hides the FAB and slides the toolbar off screen. */
    public void showDialpad(boolean animate) {
      Assert.checkArgument(!isDialpadVisible);
      isDialpadVisible = true;
      isSearchVisible = true;

      fab.hide();
      toolbar.slideUp(animate);
      setTitle(R.string.dialpad_activity_title);

      android.app.FragmentTransaction transaction = getFragmentManager().beginTransaction();

      // Show Search
      if (getSearchFragment() == null) {
        NewSearchFragment searchFragment = NewSearchFragment.newInstance(false);
        transaction.add(R.id.search_fragment_container, searchFragment, SEARCH_FRAGMENT_TAG);
      } else if (!isSearchVisible) {
        transaction.show(getSearchFragment());
      }

      // Show Dialpad
      if (getDialpadFragment() == null) {
        DialpadFragment dialpadFragment = new DialpadFragment();
        transaction.add(R.id.search_fragment_container, dialpadFragment, DIALPAD_FRAGMENT_TAG);
      } else {
        DialpadFragment dialpadFragment = getDialpadFragment();
        transaction.show(dialpadFragment);
      }
      transaction.commit();
    }

    /** Hides the dialpad, reveals the FAB and slides the toolbar back onto the screen. */
    public void hideDialpad(boolean animate) {
      Assert.checkArgument(isDialpadVisible);
      isDialpadVisible = false;

      fab.show();
      toolbar.slideDown(animate);
      setTitle(R.string.main_activity_label);

      DialpadFragment dialpadFragment = getDialpadFragment();
      dialpadFragment.setAnimate(animate);
      dialpadFragment.slideDown(
          animate,
          new AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
              if (!(isFinishing() || isDestroyed())) {
                getFragmentManager().beginTransaction().remove(dialpadFragment).commit();
              }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
          });
    }

    /**
     * Should be called when the user presses the back button.
     *
     * @return true if {@link #onBackPressed()} handled to action.
     */
    public boolean onBackPressed() {
      if (isDialpadVisible && !TextUtils.isEmpty(getDialpadFragment().getQuery())) {
        hideDialpad(true);
        return true;
      } else if (isSearchVisible) {
        closeSearch(true);
        return true;
      } else {
        return false;
      }
    }

    /** Calls {@link #hideDialpad(boolean)} and removes the search fragment. */
    private void closeSearch(boolean animate) {
      Assert.checkArgument(isSearchVisible);
      if (isDialpadVisible) {
        hideDialpad(animate);
      }
      getFragmentManager().beginTransaction().remove(getSearchFragment()).commit();
      isSearchVisible = false;
    }

    private DialpadFragment getDialpadFragment() {
      return (DialpadFragment) getFragmentManager().findFragmentByTag(DIALPAD_FRAGMENT_TAG);
    }

    private NewSearchFragment getSearchFragment() {
      return (NewSearchFragment) getFragmentManager().findFragmentByTag(SEARCH_FRAGMENT_TAG);
    }
  }

  /**
   * Implementation of {@link SearchBarListener} that holds the logic for how to handle search bar
   * events.
   */
  private static final class MainSearchBarListener implements SearchBarListener {

    @Override
    public void onSearchQueryUpdated(String query) {}

    @Override
    public void onSearchBackButtonClicked() {}

    @Override
    public void onVoiceButtonClicked(VoiceSearchResultCallback voiceSearchResultCallback) {}

    @Override
    public void openSettings() {}

    @Override
    public void sendFeedback() {}
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
