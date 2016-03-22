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

import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.provider.BlockedNumberContract.BlockedNumbers;
import android.test.ActivityInstrumentationTestCase2;

import com.android.contacts.common.compat.CompatUtils;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;

/**
 * UI tests for FilteredNumberCompat
 */
public class FilteredNumberCompatInstrumentationTest extends
        ActivityInstrumentationTestCase2<DialtactsActivity> {

    private static final String E164_NUMBER = "+16502530000";
    private static final String NUMBER = "6502530000";
    private static final String COUNTRY_ISO = "US";

    private ContentResolver mContentResolver;
    private FragmentManager mFragmentManager;

    public FilteredNumberCompatInstrumentationTest() {
        super(DialtactsActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mContentResolver = getActivity().getContentResolver();
        mFragmentManager = getActivity().getFragmentManager();
        mContentResolver.delete(BlockedNumbersSdkCompat.CONTENT_URI,
                BlockedNumbers.COLUMN_ORIGINAL_NUMBER + " = ?", new String[]{NUMBER});
    }

    public void testShowBlockNumberDialogFlow_AlreadyBlocked() throws InterruptedException {
        if (!CompatUtils.isNCompatible()) {
            return;
        }

        ContentValues values = new ContentValues();
        values.put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, NUMBER);
        mContentResolver.insert(BlockedNumbers.CONTENT_URI, values);

        FilteredNumberCompat.setHasMigratedToNewBlocking(false);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                FilteredNumberCompat
                        .showBlockNumberDialogFlow(mContentResolver, null, NUMBER, COUNTRY_ISO,
                                E164_NUMBER, R.id.floating_action_button_container,
                                mFragmentManager, null);
            }
        });
        getInstrumentation().waitForIdleSync();

        final DialogFragment migrateDialogFragment = (DialogFragment) mFragmentManager
                .findFragmentByTag("MigrateBlockedNumbers");
        assertTrue(migrateDialogFragment.getDialog().isShowing());
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                ((AlertDialog) migrateDialogFragment.getDialog())
                        .getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            }
        });
        getInstrumentation().waitForIdleSync();
        assertNull(mFragmentManager.findFragmentByTag("BlockNumberDialog"));
    }
}
