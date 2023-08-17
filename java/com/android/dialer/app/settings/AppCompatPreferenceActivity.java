/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.dialer.app.settings;

import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * A {@link android.preference.PreferenceActivity} which implements and proxies the necessary calls
 * to be used with AppCompat.
 */
public class AppCompatPreferenceActivity extends PreferenceActivity {

  private AppCompatDelegate delegate;

  private boolean isSafeToCommitTransactions;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    getDelegate().installViewFactory();
    getDelegate().onCreate(savedInstanceState);
    super.onCreate(savedInstanceState);
    isSafeToCommitTransactions = true;
  }

  @Override
  protected void onPostCreate(Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);
    getDelegate().onPostCreate(savedInstanceState);
  }

  @Override
  public MenuInflater getMenuInflater() {
    return getDelegate().getMenuInflater();
  }

  @Override
  public void setContentView(int layoutResID) {
    getDelegate().setContentView(layoutResID);
  }

  @Override
  public void setContentView(View view) {
    getDelegate().setContentView(view);
  }

  @Override
  public void setContentView(View view, ViewGroup.LayoutParams params) {
    getDelegate().setContentView(view, params);
  }

  @Override
  public void addContentView(View view, ViewGroup.LayoutParams params) {
    getDelegate().addContentView(view, params);
  }

  @Override
  protected void onPostResume() {
    super.onPostResume();
    getDelegate().onPostResume();
  }

  @Override
  protected void onTitleChanged(CharSequence title, int color) {
    super.onTitleChanged(title, color);
    getDelegate().setTitle(title);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    getDelegate().onConfigurationChanged(newConfig);
  }

  @Override
  protected void onStop() {
    super.onStop();
    getDelegate().onStop();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    getDelegate().onDestroy();
  }

  @Override
  public void invalidateOptionsMenu() {
    getDelegate().invalidateOptionsMenu();
  }

  private AppCompatDelegate getDelegate() {
    if (delegate == null) {
      delegate = AppCompatDelegate.create(this, null);
    }
    return delegate;
  }

  @Override
  protected void onStart() {
    super.onStart();
    isSafeToCommitTransactions = true;
  }

  @Override
  protected void onResume() {
    super.onResume();
    isSafeToCommitTransactions = true;
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    isSafeToCommitTransactions = false;
  }

  /**
   * Returns true if it is safe to commit {@link FragmentTransaction}s at this time, based on
   * whether {@link Activity#onSaveInstanceState} has been called or not.
   *
   * <p>Make sure that the current activity calls into {@link super.onSaveInstanceState(Bundle
   * outState)} (if that method is overridden), so the flag is properly set.
   */
  public boolean isSafeToCommitTransactions() {
    return isSafeToCommitTransactions;
  }
}
