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
 * limitations under the License
 */

package com.android.dialer.compat;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.UserManager;
import android.provider.BlockedNumberContract.BlockedNumbers;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.common.compat.CompatUtils;
import com.android.dialer.DialerApplication;
import com.android.dialer.database.FilteredNumberContract.FilteredNumber;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberColumns;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberSources;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberTypes;
import com.android.dialer.filterednumber.BlockedNumbersSettingsActivity;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

@SmallTest
public class FilteredNumberCompatTest extends AndroidTestCase {

    private static final String E164_NUMBER = "+16502530000";
    private static final String NON_E164_NUMBER = "6502530000";
    private static final String COUNTRY_ISO = "US";

    private static final Uri EXPECTED_BASE_URI = CompatUtils.isNCompatible()
            ? BlockedNumbers.CONTENT_URI : FilteredNumber.CONTENT_URI;

    @Mock private Context mContext;
    @Mock private SharedPreferences mSharedPreferences;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        DialerApplication.setContextForTest(mContext);
        when(mContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mSharedPreferences);
        FilteredNumberCompat.setIsEnabledForTest(true);
    }

    public void testIsNewFilteringEnabled_TestValueFalse() {
        FilteredNumberCompat.setIsEnabledForTest(false);
        assertFalse(FilteredNumberCompat.canUseNewFiltering());
    }

    public void testIsNewFilteringEnabled_TestValueTrue() {
        FilteredNumberCompat.setIsEnabledForTest(true);
        assertEquals(CompatUtils.isNCompatible(), FilteredNumberCompat.canUseNewFiltering());
    }

    public void testHasMigratedToNewBlocking_False() {
        assertFalse(FilteredNumberCompat.hasMigratedToNewBlocking());
    }

    public void testHasMigratedToNewBlocking_Migrated() {
        when(mSharedPreferences
                .getBoolean(FilteredNumberCompat.HAS_MIGRATED_TO_NEW_BLOCKING_KEY, false))
                .thenReturn(true);
        assertTrue(FilteredNumberCompat.hasMigratedToNewBlocking());
    }

    public void testGetContentUri_NullId() {
        assertEquals(FilteredNumber.CONTENT_URI, FilteredNumberCompat.getContentUri(null));
    }

    public void testGetContentUri_NotMigrated() {
        assertEquals(ContentUris.withAppendedId(FilteredNumber.CONTENT_URI, 1),
                FilteredNumberCompat.getContentUri(1));
    }

    public void testGetContentUri_Migrated() {
        when(mSharedPreferences
                .getBoolean(FilteredNumberCompat.HAS_MIGRATED_TO_NEW_BLOCKING_KEY, false))
                .thenReturn(true);
        assertEquals(ContentUris.withAppendedId(EXPECTED_BASE_URI, 1),
                FilteredNumberCompat.getContentUri(1));
    }

    public void testFilter_NullProjection() {
        assertNull(FilteredNumberCompat.filter(null));
    }

    public void testFilter_NoNulls() {
        assertArrayEquals(new String[] {"a", "b", "c"},
                FilteredNumberCompat.filter(new String[] {"a", "b", "c"}));
    }

    public void testFilter_WithNulls() {
        assertArrayEquals(new String[] {"a", "b"},
                FilteredNumberCompat.filter(new String[] {"a", null, "b"}));
    }

    public void testNewBlockNumberContentValues_NullNumber() {
        try {
            FilteredNumberCompat.newBlockNumberContentValues(null, null, null);
            fail();
        } catch (NullPointerException e) {}
    }

    public void testNewBlockNumberContentValues_N_NotMigrated() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        assertEquals(newExpectedContentValuesM(NON_E164_NUMBER, null, null),
                FilteredNumberCompat.newBlockNumberContentValues(NON_E164_NUMBER, null, null));
    }

    public void testNewBlockNumberContentValues_N_Migrated() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        ContentValues contentValues = new ContentValues();
        contentValues.put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, NON_E164_NUMBER);
        when(mSharedPreferences
                .getBoolean(FilteredNumberCompat.HAS_MIGRATED_TO_NEW_BLOCKING_KEY, false))
                .thenReturn(true);
        assertEquals(contentValues, FilteredNumberCompat.newBlockNumberContentValues(
                NON_E164_NUMBER,
                null, null));
    }

    public void testNewBlockNumberContentValues_N_Disabled() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        FilteredNumberCompat.setIsEnabledForTest(false);
        assertEquals(newExpectedContentValuesM(NON_E164_NUMBER, E164_NUMBER, COUNTRY_ISO),
                FilteredNumberCompat.newBlockNumberContentValues(NON_E164_NUMBER, E164_NUMBER, COUNTRY_ISO));
    }

    public void testNewBlockNumberContentValues_M_NullE164() {
        if (CompatUtils.isNCompatible()) {
            return;
        }
        assertEquals(newExpectedContentValuesM(NON_E164_NUMBER, E164_NUMBER, COUNTRY_ISO),
                FilteredNumberCompat.newBlockNumberContentValues(NON_E164_NUMBER, null, COUNTRY_ISO));
    }

    public void testNewBlockNumberContentValues_M_NullCountryIso() {
        if (CompatUtils.isNCompatible()) {
            return;
        }
        assertEquals(newExpectedContentValuesM(NON_E164_NUMBER, E164_NUMBER, null),
                FilteredNumberCompat.newBlockNumberContentValues(NON_E164_NUMBER, E164_NUMBER, null));
    }

    public void testNewBlockNumberContentValues_M_NullE164AndCountryIso() {
        if (CompatUtils.isNCompatible()) {
            return;
        }
        // Number can't be formatted properly without country code
        assertEquals(newExpectedContentValuesM(NON_E164_NUMBER, null, null),
                FilteredNumberCompat.newBlockNumberContentValues(NON_E164_NUMBER, null, null));
    }

    public void testCreateManageBlockedNumbersIntent_NullContext() {
        try {
            FilteredNumberCompat.createManageBlockedNumbersIntent(null);
            fail();
        } catch (NullPointerException e) {}
    }

    public void testCreateManageBlockedNumbersIntent_M() {
        if (CompatUtils.isNCompatible()) {
            return;
        }
        assertEquals(new ComponentName(getContext(), BlockedNumbersSettingsActivity.class),
                FilteredNumberCompat.createManageBlockedNumbersIntent(getContext()).getComponent());
    }

    public void testCreateManageBlockedNumbersIntent_N_Disabled_NotMigrated() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        FilteredNumberCompat.setIsEnabledForTest(false);
        assertEquals(new ComponentName(getContext(), BlockedNumbersSettingsActivity.class),
                FilteredNumberCompat.createManageBlockedNumbersIntent(getContext()).getComponent());
    }

    public void testCreateManageBlockedNumbersIntent_N_Enabled_NotMigrated() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        assertEquals(new ComponentName(getContext(), BlockedNumbersSettingsActivity.class),
                FilteredNumberCompat.createManageBlockedNumbersIntent(getContext()).getComponent());
    }

    public void testCreateManageBlockedNumbersIntent_N_Enabled_Migrated() {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        when(mSharedPreferences
                .getBoolean(FilteredNumberCompat.HAS_MIGRATED_TO_NEW_BLOCKING_KEY, false))
                .thenReturn(true);
        assertFalse(new ComponentName(getContext(), BlockedNumbersSettingsActivity.class)
                .equals(FilteredNumberCompat.createManageBlockedNumbersIntent(getContext())
                        .getComponent()));
    }

    public void testCanCurrentUserOpenBlockSettings_M_SecondaryUser() {
        if (CompatUtils.isNCompatible()) {
            return;
        }
        UserManager userManager = mock(UserManager.class);
        when(mContext.getSystemService(Context.USER_SERVICE)).thenReturn(userManager);
        when(userManager.isSystemUser()).thenReturn(false);
        assertFalse(FilteredNumberCompat.canCurrentUserOpenBlockSettings(mContext));
        verify(mContext).getSystemService(Context.USER_SERVICE);
        verify(userManager).isSystemUser();
    }

    public void testCanCurrentUserOpenBlockSettings_M_PrimaryUser() {
        if (CompatUtils.isNCompatible()) {
            return;
        }
        UserManager userManager = mock(UserManager.class);
        when(mContext.getSystemService(Context.USER_SERVICE)).thenReturn(userManager);
        when(userManager.isSystemUser()).thenReturn(true);
        assertTrue(FilteredNumberCompat.canCurrentUserOpenBlockSettings(mContext));
        verify(mContext).getSystemService(Context.USER_SERVICE);
        verify(userManager).isSystemUser();
    }

    public void testCanAttemptBlockOperations_M_SecondaryUser() {
        if (CompatUtils.isNCompatible()) {
            return;
        }
        UserManager userManager = mock(UserManager.class);
        when(mContext.getSystemService(Context.USER_SERVICE)).thenReturn(userManager);
        when(userManager.isSystemUser()).thenReturn(false);
        assertFalse(FilteredNumberCompat.canAttemptBlockOperations(mContext));
        verify(mContext).getSystemService(Context.USER_SERVICE);
        verify(userManager).isSystemUser();
    }

    public void testCanAttemptBlockOperations_M_PrimaryUser() {
        if (CompatUtils.isNCompatible()) {
            return;
        }
        UserManager userManager = mock(UserManager.class);
        when(mContext.getSystemService(Context.USER_SERVICE)).thenReturn(userManager);
        when(userManager.isSystemUser()).thenReturn(true);
        assertTrue(FilteredNumberCompat.canAttemptBlockOperations(mContext));
        verify(mContext).getSystemService(Context.USER_SERVICE);
        verify(userManager).isSystemUser();
    }

    private ContentValues newExpectedContentValuesM(String number, String e164Number,
            String countryIso) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(FilteredNumberColumns.NUMBER, number);
        contentValues.put(FilteredNumberColumns.NORMALIZED_NUMBER, e164Number);
        contentValues.put(FilteredNumberColumns.COUNTRY_ISO, countryIso);
        contentValues.put(FilteredNumberColumns.TYPE, FilteredNumberTypes.BLOCKED_NUMBER);
        contentValues.put(FilteredNumberColumns.SOURCE, FilteredNumberSources.USER);
        return contentValues;
    }

    private void assertArrayEquals(String[] expected, String[] actual) {
        assertEquals(Arrays.toString(expected), Arrays.toString(actual));
    }
}
