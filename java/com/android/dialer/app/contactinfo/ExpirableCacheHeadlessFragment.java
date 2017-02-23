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

package com.android.dialer.app.contactinfo;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.util.ExpirableCache;

/**
 * Fragment without any UI whose purpose is to retain an instance of {@link ExpirableCache} across
 * configuration change through the use of {@link #setRetainInstance(boolean)}. This is done as
 * opposed to implementing {@link android.os.Parcelable} as it is a less widespread change.
 */
public class ExpirableCacheHeadlessFragment extends Fragment {

  private static final String FRAGMENT_TAG = "ExpirableCacheHeadlessFragment";
  private static final int CONTACT_INFO_CACHE_SIZE = 100;

  private ExpirableCache<NumberWithCountryIso, ContactInfo> retainedCache;

  @NonNull
  public static ExpirableCacheHeadlessFragment attach(@NonNull AppCompatActivity parentActivity) {
    return attach(parentActivity.getSupportFragmentManager());
  }

  @NonNull
  private static ExpirableCacheHeadlessFragment attach(FragmentManager fragmentManager) {
    ExpirableCacheHeadlessFragment fragment =
        (ExpirableCacheHeadlessFragment) fragmentManager.findFragmentByTag(FRAGMENT_TAG);
    if (fragment == null) {
      fragment = new ExpirableCacheHeadlessFragment();
      // Allowing state loss since in rare cases this is called after activity's state is saved and
      // it's fine if the cache is lost.
      fragmentManager.beginTransaction().add(fragment, FRAGMENT_TAG).commitNowAllowingStateLoss();
    }
    return fragment;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    retainedCache = ExpirableCache.create(CONTACT_INFO_CACHE_SIZE);
    setRetainInstance(true);
  }

  public ExpirableCache<NumberWithCountryIso, ContactInfo> getRetainedCache() {
    return retainedCache;
  }
}
