/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.incallui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

/** Shows the {@link ConferenceManagerFragment} */
public class ManageConferenceActivity extends AppCompatActivity {

  private boolean isVisible;

  public boolean isVisible() {
    return isVisible;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    InCallPresenter.getInstance().setManageConferenceActivity(this);

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    setContentView(R.layout.activity_manage_conference);
    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.manageConferencePanel);
    if (fragment == null) {
      fragment = new ConferenceManagerFragment();
      getSupportFragmentManager()
          .beginTransaction()
          .replace(R.id.manageConferencePanel, fragment)
          .commit();
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (isFinishing()) {
      InCallPresenter.getInstance().setManageConferenceActivity(null);
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    final int itemId = item.getItemId();
    if (itemId == android.R.id.home) {
      onBackPressed();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onBackPressed() {
    InCallPresenter.getInstance().bringToForeground(false);
    finish();
  }

  @Override
  protected void onStart() {
    super.onStart();
    isVisible = true;
  }

  @Override
  protected void onStop() {
    super.onStop();
    isVisible = false;
  }
}
