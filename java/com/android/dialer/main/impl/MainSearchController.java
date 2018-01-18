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

import android.app.FragmentTransaction;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.dialpadview.DialpadFragment;
import com.android.dialer.dialpadview.DialpadFragment.DialpadListener;
import com.android.dialer.dialpadview.DialpadFragment.OnDialpadQueryChangedListener;
import com.android.dialer.main.impl.toolbar.MainToolbar;
import com.android.dialer.main.impl.toolbar.SearchBarListener;
import com.android.dialer.searchfragment.list.NewSearchFragment;
import com.android.dialer.searchfragment.list.NewSearchFragment.SearchFragmentListener;
import com.android.dialer.util.ViewUtil;
import com.google.common.base.Optional;

/**
 * Search controller for handling all the logic related to entering and exiting the search UI.
 *
 * <p>Components modified are:
 *
 * <ul>
 *   <li>Bottom Nav Bar, completely hidden when in search ui.
 *   <li>FAB, visible in dialpad search when dialpad is hidden. Otherwise, FAB is hidden.
 *   <li>Toolbar, expanded and visible when dialpad is hidden. Otherwise, hidden off screen.
 *   <li>Dialpad, shown through fab clicks and hidden with Android back button.
 * </ul>
 *
 * @see #onBackPressed()
 */
final class MainSearchController implements SearchBarListener {

  private static final String DIALPAD_FRAGMENT_TAG = "dialpad_fragment_tag";
  private static final String SEARCH_FRAGMENT_TAG = "search_fragment_tag";

  private final MainActivity mainActivity;
  private final BottomNavBar bottomNav;
  private final FloatingActionButton fab;
  private final MainToolbar toolbar;

  MainSearchController(
      MainActivity mainActivity,
      BottomNavBar bottomNav,
      FloatingActionButton fab,
      MainToolbar toolbar) {
    this.mainActivity = mainActivity;
    this.bottomNav = bottomNav;
    this.fab = fab;
    this.toolbar = toolbar;
  }

  /** Shows the dialpad, hides the FAB and slides the toolbar off screen. */
  public void showDialpad(boolean animate) {
    Assert.checkArgument(!isDialpadVisible());

    fab.hide();
    toolbar.slideUp(animate);
    toolbar.expand(animate, Optional.absent());
    mainActivity.setTitle(R.string.dialpad_activity_title);

    FragmentTransaction transaction = mainActivity.getFragmentManager().beginTransaction();

    // Show Search
    if (getSearchFragment() == null) {
      NewSearchFragment searchFragment = NewSearchFragment.newInstance(false);
      transaction.add(R.id.search_fragment_container, searchFragment, SEARCH_FRAGMENT_TAG);
    } else if (!isSearchVisible()) {
      transaction.show(getSearchFragment());
    }

    // Show Dialpad
    if (getDialpadFragment() == null) {
      DialpadFragment dialpadFragment = new DialpadFragment();
      transaction.add(R.id.dialpad_fragment_container, dialpadFragment, DIALPAD_FRAGMENT_TAG);
    } else {
      DialpadFragment dialpadFragment = getDialpadFragment();
      transaction.show(dialpadFragment);
    }
    transaction.commit();
  }

  /** Hides the dialpad, reveals the FAB and slides the toolbar back onto the screen. */
  private void hideDialpad(boolean animate, boolean bottomNavVisible) {
    Assert.checkArgument(isDialpadVisible());

    fab.show();
    toolbar.slideDown(animate);
    toolbar.transferQueryFromDialpad(getDialpadFragment().getQuery());
    mainActivity.setTitle(R.string.main_activity_label);

    DialpadFragment dialpadFragment = getDialpadFragment();
    dialpadFragment.setAnimate(animate);
    dialpadFragment.slideDown(
        animate,
        new AnimationListener() {
          @Override
          public void onAnimationStart(Animation animation) {
            // Slide the bottom nav on animation start so it's (not) visible when the dialpad
            // finishes animating down.
            if (bottomNavVisible) {
              showBottomNav();
            } else {
              hideBottomNav();
            }
          }

          @Override
          public void onAnimationEnd(Animation animation) {
            if (!(mainActivity.isFinishing() || mainActivity.isDestroyed())) {
              mainActivity.getFragmentManager().beginTransaction().remove(dialpadFragment).commit();
            }
          }

          @Override
          public void onAnimationRepeat(Animation animation) {}
        });
  }

  private void hideBottomNav() {
    bottomNav.setVisibility(View.INVISIBLE);
    if (bottomNav.getHeight() == 0) {
      ViewUtil.doOnGlobalLayout(bottomNav, v -> fab.setTranslationY(bottomNav.getHeight()));
    } else {
      fab.setTranslationY(bottomNav.getHeight());
    }
  }

  private void showBottomNav() {
    bottomNav.setVisibility(View.VISIBLE);
    fab.setTranslationY(0);
  }

  /** Should be called when {@link DialpadListener#onDialpadShown()} is called. */
  public void onDialpadShown() {
    getDialpadFragment().slideUp(true);
  }

  /**
   * @see SearchFragmentListener#onSearchListTouch()
   *     <p>There are 4 scenarios we support to provide a nice UX experience:
   *     <ol>
   *       <li>When the dialpad is visible with an empty query, close the search UI.
   *       <li>When the dialpad is visible with a non-empty query, hide the dialpad.
   *       <li>When the regular search UI is visible with an empty query, close the search UI.
   *       <li>When the regular search UI is visible with a non-empty query, hide the keyboard.
   *     </ol>
   */
  public void onSearchListTouch() {
    if (isDialpadVisible()) {
      if (TextUtils.isEmpty(getDialpadFragment().getQuery())) {
        closeSearch(true);
      } else {
        hideDialpad(/* animate=*/ true, /* bottomNavVisible=*/ false);
      }
    } else if (isSearchVisible()) {
      if (TextUtils.isEmpty(toolbar.getQuery())) {
        closeSearch(true);
      } else {
        toolbar.hideKeyboard();
      }
    }
  }

  /**
   * Should be called when the user presses the back button.
   *
   * @return true if #onBackPressed() handled to action.
   */
  public boolean onBackPressed() {
    if (isDialpadVisible() && !TextUtils.isEmpty(getDialpadFragment().getQuery())) {
      LogUtil.i("MainSearchController#onBackPressed", "Dialpad visible with query");
      hideDialpad(/* animate=*/ true, /* bottomNavVisible=*/ false);
      return true;
    } else if (isSearchVisible()) {
      LogUtil.i("MainSearchController#onBackPressed", "Search is visible");
      closeSearch(true);
      return true;
    } else {
      return false;
    }
  }

  /** Calls {@link #hideDialpad(boolean, boolean)} and removes the search fragment. */
  private void closeSearch(boolean animate) {
    Assert.checkArgument(isSearchVisible());
    if (isDialpadVisible()) {
      hideDialpad(animate, /* bottomNavVisible=*/ true);
    } else if (!fab.isShown()) {
      fab.show();
    }
    showBottomNav();
    toolbar.collapse(animate);
    mainActivity.getFragmentManager().beginTransaction().remove(getSearchFragment()).commit();
  }

  @Nullable
  private DialpadFragment getDialpadFragment() {
    return (DialpadFragment)
        mainActivity.getFragmentManager().findFragmentByTag(DIALPAD_FRAGMENT_TAG);
  }

  @Nullable
  private NewSearchFragment getSearchFragment() {
    return (NewSearchFragment)
        mainActivity.getFragmentManager().findFragmentByTag(SEARCH_FRAGMENT_TAG);
  }

  private boolean isDialpadVisible() {
    DialpadFragment fragment = getDialpadFragment();
    return fragment != null
        && fragment.isAdded()
        && !fragment.isHidden()
        && fragment.isDialpadSlideUp();
  }

  private boolean isSearchVisible() {
    NewSearchFragment fragment = getSearchFragment();
    return fragment != null && fragment.isAdded() && !fragment.isHidden();
  }

  /**
   * Opens search in regular/search bar search mode.
   *
   * <p>Hides fab, expands toolbar and starts the search fragment.
   */
  @Override
  public void onSearchBarClicked() {
    fab.hide();
    toolbar.expand(/* animate=*/ true, Optional.absent());
    toolbar.showKeyboard();
    hideBottomNav();

    FragmentTransaction transaction = mainActivity.getFragmentManager().beginTransaction();

    // Show Search
    if (getSearchFragment() == null) {
      NewSearchFragment searchFragment = NewSearchFragment.newInstance(false);
      transaction.add(R.id.search_fragment_container, searchFragment, SEARCH_FRAGMENT_TAG);
    } else if (!isSearchVisible()) {
      transaction.show(getSearchFragment());
    }
    transaction.commit();
  }

  @Override
  public void onSearchBackButtonClicked() {
    closeSearch(true);
  }

  @Override
  public void onSearchQueryUpdated(String query) {
    NewSearchFragment fragment = getSearchFragment();
    if (fragment != null) {
      fragment.setQuery(query, CallInitiationType.Type.REGULAR_SEARCH);
    }
  }

  /** @see OnDialpadQueryChangedListener#onDialpadQueryChanged(java.lang.String) */
  public void onDialpadQueryChanged(String query) {
    NewSearchFragment fragment = getSearchFragment();
    if (fragment != null) {
      fragment.setQuery(query, CallInitiationType.Type.DIALPAD);
    }
  }

  @Override
  public void onVoiceButtonClicked(VoiceSearchResultCallback voiceSearchResultCallback) {}

  @Override
  public void openSettings() {}

  @Override
  public void sendFeedback() {}
}
