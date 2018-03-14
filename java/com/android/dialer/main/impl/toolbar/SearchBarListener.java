/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.main.impl.toolbar;

import android.app.Activity;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

/** Useful callback for {@link SearchBarView} listeners. */
public interface SearchBarListener {

  /** Called when the user clicks on the search bar. */
  void onSearchBarClicked();

  /** Called when the search query updates. */
  void onSearchQueryUpdated(String query);

  /** Called when the back button is clicked in the search bar. */
  void onSearchBackButtonClicked();

  /** Called when the voice search button is clicked. */
  void onVoiceButtonClicked(VoiceSearchResultCallback voiceSearchResultCallback);

  /** Called when a toolbar menu item is clicked. */
  boolean onMenuItemClicked(MenuItem menuItem);

  /** Called when {@link Activity#onPause()} is called. */
  void onActivityPause();

  /** Called when {@link AppCompatActivity#onUserLeaveHint()} is called. */
  void onUserLeaveHint();

  /** Called when the user places a call from search (regular or dialpad). */
  void onCallPlacedFromSearch();

  /** Called when a permission is about to be requested. */
  void requestingPermission();

  /** Interface for returning voice results to the search bar. */
  interface VoiceSearchResultCallback {

    /** Sets the voice results in the search bar and expands the search UI. */
    void setResult(String result);
  }
}
