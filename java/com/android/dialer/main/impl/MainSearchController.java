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
import android.support.annotation.VisibleForTesting;
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

  private boolean callPlacedFromSearch;
  private boolean requestingPermission;

  private DialpadFragment dialpadFragment;
  private NewSearchFragment searchFragment;

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

    dialpadFragment =
        (DialpadFragment) activity.getFragmentManager().findFragmentByTag(DIALPAD_FRAGMENT_TAG);
    searchFragment =
        (NewSearchFragment) activity.getFragmentManager().findFragmentByTag(SEARCH_FRAGMENT_TAG);
  }

  /** Should be called if we're showing the dialpad because of a new ACTION_DIAL intent. */
  public void showDialpadFromNewIntent() {
    LogUtil.enterBlock("MainSearchController.showDialpadFromNewIntent");
    if (isDialpadVisible()) {
      // One scenario where this can happen is if the user has the dialpad open when the receive a
      // call and press add call in the in call ui which calls this method.
      LogUtil.i("MainSearchController.showDialpadFromNewIntent", "Dialpad is already visible.");

      // Mark started from new intent in case there is a phone number in the intent
      dialpadFragment.setStartedFromNewIntent(true);
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
    if (isDialpadVisible()) {
      LogUtil.e("MainSearchController.showDialpad", "Dialpad is already visible.");
      return;
    }

    Logger.get(activity).logScreenView(ScreenEvent.Type.MAIN_DIALPAD, activity);

    fab.hide();
    toolbar.slideUp(animate, fragmentContainer);
    toolbar.expand(animate, Optional.absent(), /* requestFocus */ false);
    toolbarShadow.setVisibility(View.VISIBLE);

    activity.setTitle(R.string.dialpad_activity_title);

    FragmentTransaction transaction = activity.getFragmentManager().beginTransaction();

    // Show Search
    if (searchFragment == null) {
      searchFragment = NewSearchFragment.newInstance();
      transaction.add(R.id.search_fragment_container, searchFragment, SEARCH_FRAGMENT_TAG);
      transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
    } else if (!isSearchVisible()) {
      transaction.show(searchFragment);
    }

    // Show Dialpad
    if (dialpadFragment == null) {
      dialpadFragment = new DialpadFragment();
      dialpadFragment.setStartedFromNewIntent(fromNewIntent);
      transaction.add(R.id.dialpad_fragment_container, dialpadFragment, DIALPAD_FRAGMENT_TAG);
      searchFragment.setQuery("", CallInitiationType.Type.DIALPAD);
    } else {
      dialpadFragment.setStartedFromNewIntent(fromNewIntent);
      transaction.show(dialpadFragment);
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
    if (dialpadFragment == null) {
      LogUtil.e("MainSearchController.hideDialpad", "Dialpad fragment is null.");
      return;
    }

    if (!dialpadFragment.isAdded()) {
      LogUtil.e("MainSearchController.hideDialpad", "Dialpad fragment is not added.");
      return;
    }

    if (dialpadFragment.isHidden()) {
      LogUtil.e("MainSearchController.hideDialpad", "Dialpad fragment is already hidden.");
      return;
    }

    if (!dialpadFragment.isDialpadSlideUp()) {
      LogUtil.e("MainSearchController.hideDialpad", "Dialpad fragment is already slide down.");
      return;
    }

    fab.show();
    toolbar.slideDown(animate, fragmentContainer);
    toolbar.transferQueryFromDialpad(dialpadFragment.getQuery());
    activity.setTitle(R.string.main_activity_label);

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
    dialpadFragment.slideUp(true);
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
      if (TextUtils.isEmpty(dialpadFragment.getQuery())) {
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
    if (isDialpadVisible() && !TextUtils.isEmpty(dialpadFragment.getQuery())) {
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
    if (searchFragment == null) {
      LogUtil.e("MainSearchController.closeSearch", "Search fragment is null.");
      return;
    }

    if (!searchFragment.isAdded()) {
      LogUtil.e("MainSearchController.closeSearch", "Search fragment isn't added.");
      return;
    }

    if (searchFragment.isHidden()) {
      LogUtil.e("MainSearchController.closeSearch", "Search fragment is already hidden.");
      return;
    }

    if (isDialpadVisible()) {
      hideDialpad(animate);
    } else if (!fab.isShown()) {
      fab.show();
    }
    showBottomNav();
    toolbar.collapse(animate);
    toolbarShadow.setVisibility(View.GONE);
    activity.getFragmentManager().beginTransaction().hide(searchFragment).commit();

    // Clear the dialpad so the phone number isn't persisted between search sessions.
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
    return dialpadFragment;
  }

  private boolean isDialpadVisible() {
    return dialpadFragment != null
        && dialpadFragment.isAdded()
        && !dialpadFragment.isHidden()
        && dialpadFragment.isDialpadSlideUp();
  }

  private boolean isSearchVisible() {
    return searchFragment != null && searchFragment.isAdded() && !searchFragment.isHidden();
  }

  /** Returns true if the search UI is visible. */
  public boolean isInSearch() {
    return isSearchVisible();
  }

  /** Closes the keyboard if necessary. */
  private void closeKeyboard() {
    if (searchFragment != null && searchFragment.isAdded()) {
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
    toolbar.expand(/* animate=*/ true, query, /* requestFocus */ true);
    toolbar.showKeyboard();
    toolbarShadow.setVisibility(View.VISIBLE);
    hideBottomNav();

    FragmentTransaction transaction = activity.getFragmentManager().beginTransaction();
    // Show Search
    if (searchFragment == null) {
      searchFragment = NewSearchFragment.newInstance();
      transaction.add(R.id.search_fragment_container, searchFragment, SEARCH_FRAGMENT_TAG);
      transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
    } else if (!isSearchVisible()) {
      transaction.show(searchFragment);
    }

    searchFragment.setQuery(
        query.isPresent() ? query.get() : "", CallInitiationType.Type.REGULAR_SEARCH);

    if (activity.isSafeToCommitTransactions()) {
      transaction.commit();
    }

    notifyListenersOnSearchOpen();
  }

  @Override
  public void onSearchBackButtonClicked() {
    LogUtil.enterBlock("MainSearchController.onSearchBackButtonClicked");
    closeSearch(true);
  }

  @Override
  public void onSearchQueryUpdated(String query) {
    if (searchFragment != null) {
      searchFragment.setQuery(query, CallInitiationType.Type.REGULAR_SEARCH);
    }
  }

  /** @see OnDialpadQueryChangedListener#onDialpadQueryChanged(java.lang.String) */
  public void onDialpadQueryChanged(String query) {
    String normalizedQuery = SmartDialNameMatcher.normalizeNumber(/* context = */ activity, query);
    if (searchFragment != null) {
      searchFragment.setRawNumber(query);
      searchFragment.setQuery(normalizedQuery, CallInitiationType.Type.DIALPAD);
    }
    dialpadFragment.process_quote_emergency_unquote(normalizedQuery);
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
      if (isInSearch() && (callPlacedFromSearch || !isDialpadVisible())) {
        closeSearch(false);
      }
      closeSearchOnPause = false;
      callPlacedFromSearch = false;
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
    callPlacedFromSearch = true;
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
    boolean isSlideUp = savedInstanceState.getBoolean(KEY_IS_TOOLBAR_SLIDE_UP, false);
    if (isSlideUp) {
      toolbar.slideUp(false, fragmentContainer);
    }
    if (savedInstanceState.getBoolean(KEY_IS_TOOLBAR_EXPANDED, false)) {
      // If the toolbar is slide up, that means the dialpad is showing. Thus we don't want to
      // request focus or we'll break physical/bluetooth keyboards typing.
      toolbar.expand(/* animate */ false, Optional.absent(), /* requestFocus */ !isSlideUp);
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

  @VisibleForTesting
  void setDialpadFragment(DialpadFragment dialpadFragment) {
    this.dialpadFragment = dialpadFragment;
  }

  @VisibleForTesting
  void setSearchFragment(NewSearchFragment searchFragment) {
    this.searchFragment = searchFragment;
  }
}
