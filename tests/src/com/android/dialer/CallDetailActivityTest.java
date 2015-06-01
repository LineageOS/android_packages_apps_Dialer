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
import static com.android.dialer.voicemail.VoicemailPlaybackPresenter.Tasks.CHECK_FOR_CONTENT;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.AssetManager;
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
import com.android.contacts.common.test.IntegrationTestUtils;
import com.android.dialer.util.LocaleTestUtils;
import com.android.internal.view.menu.ContextMenuBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

/**
 * Unit tests for the {@link CallDetailActivity}. NOTE: The screen needs to be on for the
 * UI-related tests to pass.
 */
@LargeTest
public class CallDetailActivityTest extends ActivityInstrumentationTestCase2<CallDetailActivity> {
    private static final String TEST_ASSET_NAME = "quick_test_recording.mp3";
    private static final String MIME_TYPE = "audio/mp3";
    private static final String CONTACT_NUMBER = "+1412555555";
    private static final String VOICEMAIL_FILE_LOCATION = "/sdcard/sadlfj893w4j23o9sfu.mp3";

    private Uri mCallLogUri;
    private Uri mVoicemailUri;
    private IntegrationTestUtils mTestUtils;
    private LocaleTestUtils mLocaleTestUtils;
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
        mTestUtils = new IntegrationTestUtils(getInstrumentation());
        // Some of the tests rely on the text that appears on screen - safest to force a
        // specific locale.
        mLocaleTestUtils = new LocaleTestUtils(getInstrumentation().getTargetContext());
        mLocaleTestUtils.setLocale(Locale.US);
    }

    @Override
    protected void tearDown() throws Exception {
        mLocaleTestUtils.restoreLocale();
        mLocaleTestUtils = null;
        cleanUpUri();
        mTestUtils = null;
        AsyncTaskExecutors.setFactoryForTest(null);
        CallLogAsyncTaskUtil.resetForTest();
        super.tearDown();
    }

    public void testInitialActivityStartsWithFetchingVoicemail() throws Throwable {
        setActivityIntentForRealFileVoicemailEntry();
        startActivityUnderTest();
        // When the activity first starts, we will show "Loading voicemail" on the screen.
        // The duration should not be visible.
        assertHasOneTextViewContaining("Loading voicemail");
        assertZeroTextViewsContaining("00:00");
    }

    public void testWhenCheckForContentCompletes() throws Throwable {
        setActivityIntentForRealFileVoicemailEntry();
        startActivityUnderTest();
        // There is a background check that is testing to see if we have the content available.
        // Once that task completes, we shouldn't be showing the fetching message.
        mFakeAsyncTaskExecutor.runTask(CHECK_FOR_CONTENT);

        // The voicemail async call may or may not return before we check the asserts.
        assertHasOneTextViewContaining("Buffering", "00:00");
        assertZeroTextViewsContaining("Loading voicemail");
    }

    public void testInvalidVoicemailShowsErrorMessage() throws Throwable {
        setActivityIntentForTestVoicemailEntry();
        startActivityUnderTest();
        mFakeAsyncTaskExecutor.runTask(CHECK_FOR_CONTENT);
        // The media player will have thrown an IOException since the file doesn't exist.
        // This should have put a failed to play message on screen, buffering is gone.
        assertHasOneTextViewContaining("Couldn't play voicemail");
        assertZeroTextViewsContaining("Buffering");
    }

    public void testOnResumeDoesNotCreateManyFragments() throws Throwable {
        // There was a bug where every time the activity was resumed, a new fragment was created.
        // Before the fix, this was failing reproducibly with at least 3 "Buffering" views.
        setActivityIntentForRealFileVoicemailEntry();
        startActivityUnderTest();
        mFakeAsyncTaskExecutor.runTask(CHECK_FOR_CONTENT);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                getInstrumentation().callActivityOnPause(mActivityUnderTest);
                getInstrumentation().callActivityOnResume(mActivityUnderTest);
                getInstrumentation().callActivityOnPause(mActivityUnderTest);
                getInstrumentation().callActivityOnResume(mActivityUnderTest);
            }
        });
        assertHasOneTextViewContaining("Buffering", "00:00");
    }

    /**
     * Test for bug where increase rate button with invalid voicemail causes a crash.
     * <p>
     * The repro steps for this crash were to open a voicemail that does not have an attachment,
     * then click the play button (which just reported an error), then after that try to adjust the
     * rate.  See http://b/5047879.
     */
    public void testClickIncreaseRateButtonWithInvalidVoicemailDoesNotCrash() throws Throwable {
        setActivityIntentForTestVoicemailEntry();
        startActivityUnderTest();
        mTestUtils.clickButton(mActivityUnderTest, R.id.playback_start_stop);
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
        Menu menu = new ContextMenuBuilder(mActivityUnderTest);
        mActivityUnderTest.onCreateOptionsMenu(menu);
        mActivityUnderTest.onPrepareOptionsMenu(menu);
        assertFalse(menu.findItem(R.id.menu_remove_from_call_log).isVisible());
    }

    /** Test to check that I haven't broken the remove-from-call-log entry from regular calls. */
    public void testRegularCallDoesHaveRemoveFromCallLog() throws Throwable {
        setActivityIntentForTestCallEntry();
        startActivityUnderTest();
        Menu menu = new ContextMenuBuilder(mActivityUnderTest);
        mActivityUnderTest.onCreateOptionsMenu(menu);
        mActivityUnderTest.onPrepareOptionsMenu(menu);
        assertTrue(menu.findItem(R.id.menu_remove_from_call_log).isVisible());
    }

    @Suppress
    public void testClickingCallStopsPlayback() throws Throwable {
        setActivityIntentForRealFileVoicemailEntry();
        startActivityUnderTest();
        mFakeAsyncTaskExecutor.runTask(CHECK_FOR_CONTENT);
        mTestUtils.clickButton(mActivityUnderTest, R.id.playback_speakerphone);
        mTestUtils.clickButton(mActivityUnderTest, R.id.playback_start_stop);
        Thread.sleep(2000);
        // TODO: Suppressed the test for now, because I'm looking for an easy way to say "the audio
        // is not playing at this point", and I can't find it without doing dirty things.
    }

    private void setActivityIntentForTestCallEntry() {
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

    private void setActivityIntentForRealFileVoicemailEntry() throws IOException {
        assertNull(mVoicemailUri);
        ContentValues values = new ContentValues();
        values.put(VoicemailContract.Voicemails.DATE, String.valueOf(System.currentTimeMillis()));
        values.put(VoicemailContract.Voicemails.NUMBER, CONTACT_NUMBER);
        values.put(VoicemailContract.Voicemails.MIME_TYPE, MIME_TYPE);
        values.put(VoicemailContract.Voicemails.HAS_CONTENT, 1);
        String packageName = getInstrumentation().getTargetContext().getPackageName();
        mVoicemailUri = getContentResolver().insert(
                VoicemailContract.Voicemails.buildSourceUri(packageName), values);
        AssetManager assets = getAssets();
        try (InputStream inputStream = assets.open(TEST_ASSET_NAME);
             OutputStream outputStream = getContentResolver().openOutputStream(mVoicemailUri)) {
            copyBetweenStreams(inputStream, outputStream);
        }
        Uri callLogUri = ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI_WITH_VOICEMAIL,
                ContentUris.parseId(mVoicemailUri));
        Intent intent = new Intent(Intent.ACTION_VIEW, callLogUri);
        intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_URI, mVoicemailUri);
        setActivityIntent(intent);
    }

    public void copyBetweenStreams(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) > 0) {
            out.write(buffer, 0, bytesRead);
        }
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

    private TextView assertHasOneTextViewContaining(String text) throws Throwable {
        assertNotNull(mActivityUnderTest);
        List<TextView> views = mTestUtils.getTextViewsWithString(mActivityUnderTest, text);
        assertEquals("There should have been one TextView with text '" + text + "' but found "
                + views, 1, views.size());
        return views.get(0);
    }

    private void assertHasOneTextViewContaining(String text1, String text2) throws Throwable {
        assertNotNull(mActivityUnderTest);
        List<TextView> view1s = mTestUtils.getTextViewsWithString(mActivityUnderTest, text1);
        List<TextView> view2s = mTestUtils.getTextViewsWithString(mActivityUnderTest, text2);
        assertEquals("There should have been one TextView with text '" + text1 + "' or text '"
                + text2  + "' but found " + view1s + view2s, 1, view1s.size() + view2s.size());
    }

    private void assertZeroTextViewsContaining(String text) throws Throwable {
        assertNotNull(mActivityUnderTest);
        List<TextView> views = mTestUtils.getTextViewsWithString(mActivityUnderTest, text);
        assertEquals("There should have been no TextViews with text '" + text + "' but found "
                + views, 0,  views.size());
    }

    private void startActivityUnderTest() throws Throwable {
        assertNull(mActivityUnderTest);
        mActivityUnderTest = getActivity();
        assertNotNull("activity should not be null", mActivityUnderTest);
        // We have to run all tasks, not just one.
        // This is because it seems that we can have onResume, onPause, onResume during the course
        // of a single unit test.
        mFakeAsyncTaskExecutor.runAllTasks(GET_CALL_DETAILS);
        CallLogAsyncTaskUtil.resetForTest();
    }

    private AssetManager getAssets() {
        return getInstrumentation().getContext().getAssets();
    }
}
