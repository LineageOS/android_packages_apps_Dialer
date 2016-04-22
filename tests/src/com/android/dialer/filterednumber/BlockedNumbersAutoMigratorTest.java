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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.AndroidTestCase;

import com.android.contacts.common.compat.CompatUtils;
import com.android.dialer.compat.FilteredNumberCompat;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler.OnHasBlockedNumbersListener;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class BlockedNumbersAutoMigratorTest extends AndroidTestCase {

    private static final String HAS_CHECKED_AUTO_MIGRATE_KEY_FOR_TEST = "checkedAutoMigrateForTest";

    @Mock
    private FilteredNumberAsyncQueryHandler mockQueryHandler;

    private SharedPreferences sharedPreferences;

    private BlockedNumbersAutoMigrator blockedNumbersAutoMigrator;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        FilteredNumberCompat.setContextForTest(getContext());
        FilteredNumberCompat.setHasMigratedToNewBlocking(false);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        // SharedPreference state isn't cleaned up between each test automatically, clear it now
        sharedPreferences.edit().clear().apply();

        blockedNumbersAutoMigrator = new BlockedNumbersAutoMigrator(sharedPreferences,
                mockQueryHandler);
    }

    public void testConstructor_NullSharedPreferences() {
        try {
            new BlockedNumbersAutoMigrator(null, mockQueryHandler);
            fail();
        } catch (NullPointerException e) {
        }
    }

    public void testConstructor_NullQueryHandler() {
        try {
            new BlockedNumbersAutoMigrator(sharedPreferences, null);
            fail();
        } catch (NullPointerException e) {
        }
    }

    public void testAutoMigrate_M() {
        if (CompatUtils.isNCompatible()) {
            return;
        }
        blockedNumbersAutoMigrator.autoMigrate();

        verify(mockQueryHandler, never()).hasBlockedNumbers(any(OnHasBlockedNumbersListener.class));
    }

    public void testAutoMigrate_AlreadyMigrated() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        FilteredNumberCompat.setHasMigratedToNewBlocking(true);

        blockedNumbersAutoMigrator.autoMigrate();

        verify(mockQueryHandler, never()).hasBlockedNumbers(any(OnHasBlockedNumbersListener.class));
    }

    public void testAutoMigrate_AlreadyChecked() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        sharedPreferences.edit()
                .putBoolean(HAS_CHECKED_AUTO_MIGRATE_KEY_FOR_TEST, true)
                .apply();

        blockedNumbersAutoMigrator.autoMigrate();

        verify(mockQueryHandler, never()).hasBlockedNumbers(any(OnHasBlockedNumbersListener.class));
    }

    public void testAutoMigrate_HasNumbers() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        setupFilteredNumberHasBlockedNumbersExpectation(true);

        blockedNumbersAutoMigrator.autoMigrate();

        verify(mockQueryHandler).hasBlockedNumbers(any(OnHasBlockedNumbersListener.class));
        assertFalse(FilteredNumberCompat.hasMigratedToNewBlocking());
    }

    public void testAutoMigrate_HasNumbers_MultipleCalls() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        setupFilteredNumberHasBlockedNumbersExpectation(true);

        blockedNumbersAutoMigrator.autoMigrate();
        blockedNumbersAutoMigrator.autoMigrate();

        verify(mockQueryHandler, times(1))
                .hasBlockedNumbers(any(OnHasBlockedNumbersListener.class));
        assertFalse(FilteredNumberCompat.hasMigratedToNewBlocking());
    }

    public void testAutoMigrate_NoNumbers() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        setupFilteredNumberHasBlockedNumbersExpectation(false);

        blockedNumbersAutoMigrator.autoMigrate();

        verify(mockQueryHandler).hasBlockedNumbers(any(OnHasBlockedNumbersListener.class));
        assertTrue(FilteredNumberCompat.hasMigratedToNewBlocking());
    }

    public void testAutoMigrate_NoNumbers_MultipleCalls() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        setupFilteredNumberHasBlockedNumbersExpectation(false);

        blockedNumbersAutoMigrator.autoMigrate();
        blockedNumbersAutoMigrator.autoMigrate();

        verify(mockQueryHandler, times(1))
                .hasBlockedNumbers(any(OnHasBlockedNumbersListener.class));
        assertTrue(FilteredNumberCompat.hasMigratedToNewBlocking());
    }


    public void testAutoMigrate_SimulateClearingAppData() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        setupFilteredNumberHasBlockedNumbersExpectation(true);

        blockedNumbersAutoMigrator.autoMigrate();

        // Clearing app data removes the sharedPreferences and all of the blocked numbers
        sharedPreferences.edit().clear().apply();
        setupFilteredNumberHasBlockedNumbersExpectation(false);

        blockedNumbersAutoMigrator.autoMigrate();

        verify(mockQueryHandler, times(2))
                .hasBlockedNumbers(any(OnHasBlockedNumbersListener.class));
        assertTrue(FilteredNumberCompat.hasMigratedToNewBlocking());
    }

    /*
     * Sets up the {@link #mockQueryHandler} to call the {@link OnHasBlockedNumbersListener} with
     * the given hasBlockedNumbers value as the parameter, when
     * {@link FilteredNumberAsyncQueryHandler#hasBlockedNumbers} is called.
     */
    private void setupFilteredNumberHasBlockedNumbersExpectation(final boolean hasBlockedNumbers) {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                ((OnHasBlockedNumbersListener) invocation.getArguments()[0])
                        .onHasBlockedNumbers(hasBlockedNumbers);
                return null;
            }
        }).when(mockQueryHandler).hasBlockedNumbers(any(OnHasBlockedNumbersListener.class));
    }
}
