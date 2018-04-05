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

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.Toast;
import com.android.contacts.common.dialog.ClearFrequentsDialog;
import com.android.dialer.app.calllog.CallLogActivity;
import com.android.dialer.app.settings.DialerSettingsActivity;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.constants.ActivityRequestCodes;
import com.android.dialer.dialpadview.DialpadFragment;
import com.android.dialer.dialpadview.DialpadFragment.DialpadListener;
import com.android.dialer.dialpadview.DialpadFragment.OnDialpadQueryChangedListener;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ScreenEvent;
import com.android.dialer.main.impl.bottomnav.BottomNavBar;
import com.android.dialer.main.impl.toolbar.MainToolbar;
import com.android.dialer.main.impl.toolbar.SearchBarListener;
import com.android.dialer.searchfragment.list.NewSearchFragment;
import com.android.dialer.searchfragment.list.NewSearchFragment.SearchFragmentListener;
import com.android.dialer.smartdial.util.SmartDialNameMatcher;
import com.android.dialer.util.TransactionSafeActivity;
import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.List;

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
public class MainSearchController implements SearchBarListener {

  private static final String KEY_IS_FAB_HIDDEN = "is_fab_hidden";
  private static final String KEY_TOOLBAR_SHADOW_VISIBILITY = "toolbar_shadow_visibility";
  private static final String KEY_IS_TOOLBAR_EXPANDED = "is_toolbar_expanded";
  private static final String KEY_IS_TOOLBAR_SLIDE_UP = "is_toolbar_slide_up";

  private static final String DIALPAD_FRAGMENT_TAG = "dialpad_fragment_tag";
  private static final String SEARCH_FRAGMENT_TAG = "search_fragment_tag";

  private final TransactionSafeActivity activity;
  private final BottomNavBar bottomNav;
  private final FloatingActionButton fab;
  private final MainToolbar toolbar;
  private final View toolbarShadow;

  /** View located underneath the toolbar that needs to animate with it. */
  private final View fragmentContainer;

  private final List<OnSearchShowListener> onSearchShowListenerList = new ArrayList<>();

  /**
   * True when an action happens that closes search (like leaving the app or placing a call). We
   * want to wait until onPause is called otherwise the transition will look extremely janky.
   */
  private boolean closeSearchOnPause;
  private boolean requestingPermission;

  public MainSearchController(
      TransactionSafeActivity activity,
      BottomNavBar bottomNav,
      FloatingActionButton fab,
      MainToolbar toolbar,
      View toolbarShadow,
      View fragmentContainer) {
    this.activity = activity;
    this.bottomNav = bottomNav;
    this.fab = fab;
    this.toolbar = toolbar;
    this.toolbarShadow = toolbarShadow;
    this.fragmentContainer = fragmentContainer;
  }

  /** Should be called if we're showing the dialpad because of a new ACTION_DIAL intent. */
  public void showDialpadFromNewIntent() {
    LogUtil.enterBlock("MainSearchController.showDialpadFromNewIntent");
    if (isDialpadVisible()) {
      // One scenario where this can happen is if the user has the dialpad open when the receive a
      // call and press add call in the in call ui which calls this method.
      LogUtil.i("MainSearchController.showDialpadFromNewIntent", "Dialpad is already visible.");

      // Mark started from new intent in case there is a phone number in the intent
      getDialpadFragment().setStartedFromNewIntent(true);
      return;
    }
    showDialpad(/* animate=*/ false, /* fromNewIntent=*/ true);
  }

  /** Shows the dialpad, hides the FAB and slides the toolbar off screen. */
  public void showDialpad(boolean animate) {
    LogUtil.enterBlock("MainSearchController.showDialpad");
    showDialpad(animate, false);
  }

  private void showDialpad(boolean animate, boolean fromNewIntent) {
    Assert.checkArgument(!isDialpadVisible());

    Logger.get(activity).logScreenView(ScreenEvent.Type.MAIN_DIALPAD, activity);

    fab.hide();
    toolbar.slideUp(animate, fragmentContainer);
    toolbar.expand(animate, Optional.absent());
    toolbarShadow.setVisibility(View.VISIBLE);

    activity.setTitle(R.string.dialpad_activity_title);

    FragmentTransaction transaction = activity.getFragmentManager().beginTransaction();
    NewSearchFragment searchFragment = getSearchFragment();

    // Show Search
    if (searchFragment == null) {
      searchFragment = NewSearchFragment.newInstance();
      transaction.add(R.id.search_fragment_container, searchFragment, SEARCH_FRAGMENT_TAG);
      transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
    } else if (!isSearchVisible()) {
      transaction.show(searchFragment);
    }

    // Show Dialpad
    if (getDialpadFragment() == null) {
      DialpadFragment dialpadFragment = new DialpadFragment();
      dialpadFragment.setStartedFromNewIntent(fromNewIntent);
      transaction.add(R.id.dialpad_fragment_container, dialpadFragment, DIALPAD_FRAGMENT_TAG);
      searchFragment.setQuery("", CallInitiationType.Type.DIALPAD);
    } else {
      DialpadFragment dialpadFragment = getDialpadFragment();
      dialpadFragment.setStartedFromNewIntent(fromNewIntent);
      transaction.show(dialpadFragment);
      searchFragment.setQuery(dialpadFragment.getQuery(), CallInitiationType.Type.DIALPAD);
    }
    transaction.commit();

    notifyListenersOnSearchOpen();
  }

  /**
   * Hides the dialpad, reveals the FAB and slides the toolbar back onto the screen.
   *
   * <p>This method intentionally "hides" and does not "remove" the dialpad in order to preserve its
   * state (i.e. we call {@link FragmentTransaction#hide(Fragment)} instead of {@link
   * FragmentTransaction#remove(Fragment)}.
   *
   * @see {@link #closeSearch(boolean)} to "remove" the dialpad.
   */
  private void hideDialpad(boolean animate) {
    LogUtil.enterBlock("MainSearchController.hideDialpad");
    assertDialpadVisible();

    fab.show();
    toolbar.slideDown(animate, fragmentContainer);
    toolbar.transferQueryFromDialpad(getDialpadFragment().getQuery());
    activity.setTitle(R.string.main_activity_label);

    DialpadFragment dialpadFragment = getDialpadFragment();
    dialpadFragment.setAnimate(animate);
    dialpadFragment.slideDown(
        animate,
        new AnimationListener() {
          @Override
          public void onAnimationStart(Animation animation) {}

          @Override
          public void onAnimationEnd(Animation animation) {
            if (activity.isSafeToCommitTransactions()
                && !(activity.isFinishing() || activity.isDestroyed())) {
              activity.getFragmentManager().beginTransaction().hide(dialpadFragment).commit();
            }
          }

          @Override
          public void onAnimationRepeat(Animation animation) {}
        });
  }

  private void hideBottomNav() {
    bottomNav.setVisibility(View.GONE);
  }

  private void showBottomNav() {
    bottomNav.setVisibility(View.VISIBLE);
  }

  /** Should be called when {@link DialpadListener#onDialpadShown()} is called. */
  public void onDialpadShown() {
    LogUtil.enterBlock("MainSearchController.onDialpadShown");
    getDialpadFragment().slideUp(true);
    hideBottomNav();
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
    LogUtil.enterBlock("MainSearchController.onSearchListTouched");
    if (isDialpadVisible()) {
      if (TextUtils.isEmpty(getDialpadFragment().getQuery())) {
        Logger.get(activity)
            .logImpression(
                DialerImpression.Type.MAIN_TOUCH_DIALPAD_SEARCH_LIST_TO_CLOSE_SEARCH_AND_DIALPAD);
        closeSearch(true);
      } else {
        Logger.get(activity)
            .logImpression(DialerImpression.Type.MAIN_TOUCH_DIALPAD_SEARCH_LIST_TO_HIDE_DIALPAD);
        hideDialpad(/* animate=*/ true);
      }
    } else if (isSearchVisible()) {
      if (TextUtils.isEmpty(toolbar.getQuery())) {
        Logger.get(activity)
            .logImpression(DialerImpression.Type.MAIN_TOUCH_SEARCH_LIST_TO_CLOSE_SEARCH);
        closeSearch(true);
      } else {
        Logger.get(activity)
            .logImpression(DialerImpression.Type.MAIN_TOUCH_SEARCH_LIST_TO_HIDE_KEYBOARD);
        closeKeyboard();
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
      LogUtil.i("MainSearchController.onBackPressed", "Dialpad visible with query");
      Logger.get(activity)
          .logImpression(DialerImpression.Type.MAIN_PRESS_BACK_BUTTON_TO_HIDE_DIALPAD);
      hideDialpad(/* animate=*/ true);
      return true;
    } else if (isSearchVisible()) {
      LogUtil.i("MainSearchController.onBackPressed", "Search is visible");
      Logger.get(activity)
          .logImpression(
              isDialpadVisible()
                  ? DialerImpression.Type.MAIN_PRESS_BACK_BUTTON_TO_CLOSE_SEARCH_AND_DIALPAD
                  : DialerImpression.Type.MAIN_PRESS_BACK_BUTTON_TO_CLOSE_SEARCH);
      closeSearch(true);
      return true;
    } else {
      return false;
    }
  }

  /** Calls {@link #hideDialpad(boolean)}, removes the search fragment and clears the dialpad. */
  private void closeSearch(boolean animate) {
    LogUtil.enterBlock("MainSearchController.closeSearch");
    assertSearchIsVisible();
    if (isDialpadVisible()) {
      hideDialpad(animate);
    } else if (!fab.isShown()) {
      fab.show();
    }
    showBottomNav();
    toolbar.collapse(animate);
    toolbarShadow.setVisibility(View.GONE);
    activity.getFragmentManager().beginTransaction().hide(getSearchFragment()).commit();

    // Clear the dialpad so the phone number isn't persisted between search sessions.
    DialpadFragment dialpadFragment = getDialpadFragment();
    if (dialpadFragment != null) {
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

    notifyListenersOnSearchClose();
  }

  @Nullable
  protected DialpadFragment getDialpadFragment() {
    return (DialpadFragment) activity.getFragmentManager().findFragmentByTag(DIALPAD_FRAGMENT_TAG);
  }

  @Nullable
  private NewSearchFragment getSearchFragment() {
    return (NewSearchFragment) activity.getFragmentManager().findFragmentByTag(SEARCH_FRAGMENT_TAG);
  }

  private boolean isDialpadVisible() {
    DialpadFragment fragment = getDialpadFragment();
    return fragment != null
        && fragment.isAdded()
        && !fragment.isHidden()
        && fragment.isDialpadSlideUp();
  }

  private void assertDialpadVisible() {
    DialpadFragment fragment = getDialpadFragment();
    Assert.checkArgument(fragment != null, "Dialpad Fragment is null");
    Assert.checkArgument(fragment.isAdded(), "Dialpad Fragment is no added");
    Assert.checkArgument(!fragment.isHidden(), "Dialpad Fragment is hidden");
    Assert.checkArgument(fragment.isDialpadSlideUp(), "Dialpad Fragment is slide down");
  }

  private boolean isSearchVisible() {
    NewSearchFragment fragment = getSearchFragment();
    return fragment != null && fragment.isAdded() && !fragment.isHidden();
  }

  private void assertSearchIsVisible() {
    NewSearchFragment fragment = getSearchFragment();
    Assert.checkArgument(fragment != null, "Search Fragment is null");
    Assert.checkArgument(fragment.isAdded(), "Search Fragment is not added");
    Assert.checkArgument(!fragment.isHidden(), "Search Fragment is hidden.");
  }

  /** Returns true if the search UI is visible. */
  public boolean isInSearch() {
    return isSearchVisible();
  }

  /** Closes the keyboard if necessary. */
  private void closeKeyboard() {
    NewSearchFragment fragment = getSearchFragment();
    if (fragment != null && fragment.isAdded()) {
      toolbar.hideKeyboard();
    }
  }

  /**
   * Opens search in regular/search bar search mode.
   *
   * <p>Hides fab, expands toolbar and starts the search fragment.
   */
  @Override
  public void onSearchBarClicked() {
    LogUtil.enterBlock("MainSearchController.onSearchBarClicked");
    Logger.get(activity).logImpression(DialerImpression.Type.MAIN_CLICK_SEARCH_BAR);
    openSearch(Optional.absent());
  }

  private void openSearch(Optional<String> query) {
    LogUtil.enterBlock("MainSearchController.openSearch");

    Logger.get(activity).logScreenView(ScreenEvent.Type.MAIN_SEARCH, activity);

    fab.hide();
    toolbar.expand(/* animate=*/ true, query);
    toolbar.showKeyboard();
    toolbarShadow.setVisibility(View.VISIBLE);
    hideBottomNav();

    FragmentTransaction transaction = activity.getFragmentManager().beginTransaction();
    NewSearchFragment searchFragment = getSearchFragment();

    // Show Search
    if (searchFragment == null) {
      searchFragment = NewSearchFragment.newInstance();
      transaction.add(R.id.search_fragment_container, searchFragment, SEARCH_FRAGMENT_TAG);
      transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
    } else if (!isSearchVisible()) {
      transaction.show(getSearchFragment());
    }

    searchFragment.setQuery(
        query.isPresent() ? query.get() : "", CallInitiationType.Type.REGULAR_SEARCH);
    transaction.commit();

    notifyListenersOnSearchOpen();
  }

  @Override
  public void onSearchBackButtonClicked() {
    LogUtil.enterBlock("MainSearchController.onSearchBackButtonClicked");
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
    query = SmartDialNameMatcher.normalizeNumber(/* context = */ activity, query);
    NewSearchFragment fragment = getSearchFragment();
    if (fragment != null) {
      fragment.setQuery(query, CallInitiationType.Type.DIALPAD);
    }
    getDialpadFragment().process_quote_emergency_unquote(query);
  }

  @Override
  public void onVoiceButtonClicked(VoiceSearchResultCallback voiceSearchResultCallback) {
    Logger.get(activity).logImpression(DialerImpression.Type.MAIN_CLICK_SEARCH_BAR_VOICE_BUTTON);
    try {
      Intent voiceIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
      activity.startActivityForResult(voiceIntent, ActivityRequestCodes.DIALTACTS_VOICE_SEARCH);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(activity, R.string.voice_search_not_available, Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public boolean onMenuItemClicked(MenuItem menuItem) {
    if (menuItem.getItemId() == R.id.settings) {
      activity.startActivity(new Intent(activity, DialerSettingsActivity.class));
      Logger.get(activity).logScreenView(ScreenEvent.Type.SETTINGS, activity);
      return true;
    } else if (menuItem.getItemId() == R.id.clear_frequents) {
      ClearFrequentsDialog.show(activity.getFragmentManager());
      Logger.get(activity).logScreenView(ScreenEvent.Type.CLEAR_FREQUENTS, activity);
      return true;
    } else if (menuItem.getItemId() == R.id.menu_call_history) {
      final Intent intent = new Intent(activity, CallLogActivity.class);
      activity.startActivity(intent);
    }
    return false;
  }

  @Override
  public void onActivityPause() {
    LogUtil.enterBlock("MainSearchController.onActivityPause");
    closeKeyboard();

    if (closeSearchOnPause) {
      closeSearchOnPause = false;
      if (isInSearch()) {
        closeSearch(false);
      }
    }
  }

  @Override
  public void onUserLeaveHint() {
    if (isInSearch()) {
      // Requesting a permission causes this to be called and we want search to remain open when
      // that happens. Otherwise, close search.
      closeSearchOnPause = !requestingPermission;

      // Always hide the keyboard when the user leaves dialer (including permission requests)
      closeKeyboard();
    }
  }

  @Override
  public void onCallPlacedFromSearch() {
    closeSearchOnPause = true;
  }

  @Override
  public void requestingPermission() {
    LogUtil.enterBlock("MainSearchController.requestingPermission");
    requestingPermission = true;
  }

  public void onVoiceResults(int resultCode, Intent data) {
    if (resultCode == AppCompatActivity.RESULT_OK) {
      ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
      if (matches.size() > 0) {
        LogUtil.i("MainSearchController.onVoiceResults", "voice search - match found");
        openSearch(Optional.of(matches.get(0)));
      } else {
        LogUtil.i("MainSearchController.onVoiceResults", "voice search - nothing heard");
      }
    } else {
      LogUtil.e("MainSearchController.onVoiceResults", "voice search failed");
    }
  }

  public void onSaveInstanceState(Bundle bundle) {
    bundle.putBoolean(KEY_IS_FAB_HIDDEN, !fab.isShown());
    bundle.putInt(KEY_TOOLBAR_SHADOW_VISIBILITY, toolbarShadow.getVisibility());
    bundle.putBoolean(KEY_IS_TOOLBAR_EXPANDED, toolbar.isExpanded());
    bundle.putBoolean(KEY_IS_TOOLBAR_SLIDE_UP, toolbar.isSlideUp());
  }

  public void onRestoreInstanceState(Bundle savedInstanceState) {
    toolbarShadow.setVisibility(savedInstanceState.getInt(KEY_TOOLBAR_SHADOW_VISIBILITY));
    if (savedInstanceState.getBoolean(KEY_IS_FAB_HIDDEN, false)) {
      fab.hide();
    }
    if (savedInstanceState.getBoolean(KEY_IS_TOOLBAR_EXPANDED, false)) {
      toolbar.expand(false, Optional.absent());
    }
    if (savedInstanceState.getBoolean(KEY_IS_TOOLBAR_SLIDE_UP, false)) {
      toolbar.slideUp(false, fragmentContainer);
    }
  }

  public void addOnSearchShowListener(OnSearchShowListener listener) {
    onSearchShowListenerList.add(listener);
  }

  public void removeOnSearchShowListener(OnSearchShowListener listener) {
    onSearchShowListenerList.remove(listener);
  }

  private void notifyListenersOnSearchOpen() {
    for (OnSearchShowListener listener : onSearchShowListenerList) {
      listener.onSearchOpen();
    }
  }

  private void notifyListenersOnSearchClose() {
    for (OnSearchShowListener listener : onSearchShowListenerList) {
      listener.onSearchClose();
    }
  }

  /** Listener for search fragment show states change */
  public interface OnSearchShowListener {
    void onSearchOpen();

    void onSearchClose();
  }
}
