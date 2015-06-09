/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer;

import static com.android.dialer.calllog.CallLogAsyncTaskUtil.Tasks.GET_CALL_DETAILS;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.VoicemailContract;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.Suppress;
import android.view.Menu;
import android.widget.TextView;

import com.android.dialer.calllog.CallLogAsyncTaskUtil;
import com.android.dialer.util.AsyncTaskExecutors;
import com.android.dialer.util.FakeAsyncTaskExecutor;
import com.android.internal.view.menu.ContextMenuBuilder;

/**
 * Unit tests for the {@link CallDetailActivity}. NOTE: The screen needs to be on for the
 * UI-related tests to pass.
 */
@LargeTest
public class CallDetailActivityTest extends ActivityInstrumentationTestCase2<CallDetailActivity> {
    private static final String CONTACT_NUMBER = "+1412555555";
    private static final String VOICEMAIL_FILE_LOCATION = "/sdcard/sadlfj893w4j23o9sfu.mp3";

    private Uri mCallLogUri;
    private Uri mVoicemailUri;
    private FakeAsyncTaskExecutor mFakeAsyncTaskExecutor;
    private CallDetailActivity mActivityUnderTest;

    public CallDetailActivityTest() {
        super(CallDetailActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mFakeAsyncTaskExecutor = new FakeAsyncTaskExecutor(getInstrumentation());
        AsyncTaskExecutors.setFactoryForTest(mFakeAsyncTaskExecutor.getFactory());

        // I don't like the default of focus-mode for tests, the green focus border makes the
        // screenshots look weak.
        setActivityInitialTouchMode(true);
    }

    @Override
    protected void tearDown() throws Exception {
        cleanUpUri();

        AsyncTaskExecutors.setFactoryForTest(null);
        CallLogAsyncTaskUtil.resetForTest();

        super.tearDown();
    }

    /** Test for bug where missing Extras on intent used to start Activity causes NPE. */
    public void testCallLogUriWithMissingExtrasShouldNotCauseNPE() throws Throwable {
        setActivityIntentForTestCallEntry();
        startActivityUnderTest();
    }

    /**
     * Test for bug where voicemails should not have remove-from-call-log entry.
     * <p>
     * See http://b/5054103.
     */
    public void testVoicemailDoesNotHaveRemoveFromCallLog() throws Throwable {
        setActivityIntentForTestVoicemailEntry();
        startActivityUnderTest();
        mFakeAsyncTaskExecutor.runTask(GET_CALL_DETAILS);

        Menu menu = new ContextMenuBuilder(mActivityUnderTest);
        mActivityUnderTest.onCreateOptionsMenu(menu);
        mActivityUnderTest.onPrepareOptionsMenu(menu);
        assertFalse(menu.findItem(R.id.menu_remove_from_call_log).isVisible());
        assertTrue(menu.findItem(R.id.menu_trash).isVisible());
    }

    /**
     * Test to check that I haven't broken the remove-from-call-log entry from regular calls.
     */
    public void testRegularCallDoesHaveRemoveFromCallLog() throws Throwable {
        setActivityIntentForTestCallEntry();
        startActivityUnderTest();
        mFakeAsyncTaskExecutor.runTask(GET_CALL_DETAILS);

        Menu menu = new ContextMenuBuilder(mActivityUnderTest);
        mActivityUnderTest.onCreateOptionsMenu(menu);
        mActivityUnderTest.onPrepareOptionsMenu(menu);
        assertTrue(menu.findItem(R.id.menu_remove_from_call_log).isVisible());
        assertFalse(menu.findItem(R.id.menu_trash).isVisible());
    }

    private void setActivityIntentForTestCallEntry() {
        assertNull(mVoicemailUri);
        assertNull(mCallLogUri);
        ContentResolver contentResolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(CallLog.Calls.NUMBER, CONTACT_NUMBER);
        values.put(CallLog.Calls.NUMBER_PRESENTATION, CallLog.Calls.PRESENTATION_ALLOWED);
        values.put(CallLog.Calls.TYPE, CallLog.Calls.INCOMING_TYPE);
        mCallLogUri = contentResolver.insert(CallLog.Calls.CONTENT_URI, values);
        setActivityIntent(new Intent(Intent.ACTION_VIEW, mCallLogUri));
    }

    private void setActivityIntentForTestVoicemailEntry() {
        assertNull(mVoicemailUri);
        ContentResolver contentResolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(VoicemailContract.Voicemails.NUMBER, CONTACT_NUMBER);
        values.put(VoicemailContract.Voicemails.HAS_CONTENT, 1);
        values.put(VoicemailContract.Voicemails._DATA, VOICEMAIL_FILE_LOCATION);
        mVoicemailUri = contentResolver.insert(VoicemailContract.Voicemails.CONTENT_URI, values);

        Uri callLogUri = ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI_WITH_VOICEMAIL,
                ContentUris.parseId(mVoicemailUri));
        Intent intent = new Intent(Intent.ACTION_VIEW, callLogUri);
        intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_URI, mVoicemailUri);
        setActivityIntent(intent);
    }

    private void cleanUpUri() {
        if (mVoicemailUri != null) {
            getContentResolver().delete(VoicemailContract.Voicemails.CONTENT_URI,
                    "_ID = ?", new String[] { String.valueOf(ContentUris.parseId(mVoicemailUri)) });
            mVoicemailUri = null;
        }
        if (mCallLogUri != null) {
            getContentResolver().delete(CallLog.Calls.CONTENT_URI_WITH_VOICEMAIL,
                    "_ID = ?", new String[] { String.valueOf(ContentUris.parseId(mCallLogUri)) });
            mCallLogUri = null;
        }
    }

    private ContentResolver getContentResolver() {
        return getInstrumentation().getTargetContext().getContentResolver();
    }

    private void startActivityUnderTest() throws Throwable {
        assertNull(mActivityUnderTest);
        mActivityUnderTest = getActivity();
        assertNotNull("activity should not be null", mActivityUnderTest);
    }
}
