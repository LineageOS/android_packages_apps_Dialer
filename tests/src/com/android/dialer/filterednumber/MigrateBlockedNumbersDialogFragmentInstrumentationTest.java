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

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlertDialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.test.ActivityInstrumentationTestCase2;

import com.android.dialer.DialtactsActivity;
import com.android.dialer.filterednumber.BlockedNumbersMigrator.Listener;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Instrumentation tests for {@link MigrateBlockedNumbersDialogFragment}. Note for these tests to
 * work properly, the device's screen must be on.
 */
public class MigrateBlockedNumbersDialogFragmentInstrumentationTest extends
        ActivityInstrumentationTestCase2<DialtactsActivity> {

    private static final String SHOW_TAG = "ShowTag";

    @Mock private BlockedNumbersMigrator mBlockedNumbersMigrator;
    @Mock private Listener mListener;
    private DialtactsActivity mActivity;
    private DialogFragment mMigrateDialogFragment;

    public MigrateBlockedNumbersDialogFragmentInstrumentationTest() {
        super(DialtactsActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        mActivity = getActivity();
        mMigrateDialogFragment = MigrateBlockedNumbersDialogFragment
                .newInstance(mBlockedNumbersMigrator, mListener);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mMigrateDialogFragment.show(mActivity.getFragmentManager(), SHOW_TAG);
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    public void testDialogAppears() {
        assertTrue(mMigrateDialogFragment.getDialog().isShowing());
    }

    public void testDialogPositiveButtonPress() {
        when(mBlockedNumbersMigrator.migrate(mListener)).thenReturn(true);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                ((AlertDialog) mMigrateDialogFragment.getDialog())
                        .getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            }
        });
        getInstrumentation().waitForIdleSync();
        // Dialog was dismissed
        assertNull(mMigrateDialogFragment.getDialog());
        verify(mBlockedNumbersMigrator).migrate(mListener);
    }
}
