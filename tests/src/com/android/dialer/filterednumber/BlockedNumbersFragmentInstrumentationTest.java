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

package com.android.dialer.filterednumber;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;

import android.test.ActivityInstrumentationTestCase2;
import android.view.View;

import com.android.contacts.common.compat.CompatUtils;
import com.android.dialer.R;
import com.android.dialer.compat.FilteredNumberCompat;
import com.android.dialer.filterednumber.BlockedNumbersMigrator.Listener;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Instrumentation tests for {@link BlockedNumbersFragment}. Note for these tests to work properly,
 * the device's screen must be on.
 */
public class BlockedNumbersFragmentInstrumentationTest extends
    ActivityInstrumentationTestCase2<BlockedNumbersSettingsActivity> {

  private static final String FRAGMENT_TAG = "blocked_management";

  private BlockedNumbersFragment blockedNumbersFragment;
  @Mock private BlockedNumbersMigrator blockedNumbersMigrator;

  public BlockedNumbersFragmentInstrumentationTest() {
    super(BlockedNumbersSettingsActivity.class);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);
    FilteredNumberCompat.setIsEnabledForTest(true);
    blockedNumbersFragment = new BlockedNumbersFragment();
    blockedNumbersFragment.setBlockedNumbersMigratorForTest(blockedNumbersMigrator);
    getActivity().getFragmentManager().beginTransaction()
        .replace(R.id.blocked_numbers_activity_container, blockedNumbersFragment, FRAGMENT_TAG)
        .commit();
    getInstrumentation().waitForIdleSync();
  }

  public void testMigrationPromo_NotShown_M() {
    if (CompatUtils.isNCompatible()) {
      return;
    }
    assertEquals(View.GONE, blockedNumbersFragment.migratePromoView.getVisibility());
  }

  public void testMigrationPromo_Shown_N() {
    if (!CompatUtils.isNCompatible()) {
      return;
    }
    assertEquals(View.VISIBLE, blockedNumbersFragment.migratePromoView.getVisibility());
  }

  public void testOnClick_Migrate() {
    if (!CompatUtils.isNCompatible()) {
      return;
    }

    getInstrumentation().runOnMainSync(new Runnable() {
      @Override
      public void run() {
        blockedNumbersFragment.getListView().findViewById(R.id.migrate_promo_allow_button)
            .performClick();
      }
    });
    getInstrumentation().waitForIdleSync();
    assertFalse(blockedNumbersFragment.getListView().findViewById(R.id.migrate_promo_allow_button)
        .isEnabled());
    verify(blockedNumbersMigrator).migrate(any(Listener.class));
  }
}
