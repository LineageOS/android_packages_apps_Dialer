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

package com.android.dialer.voicemail;

import static com.android.dialer.voicemail.VoicemailPlaybackPresenter.Tasks.CHECK_FOR_CONTENT;
import static com.android.dialer.voicemail.VoicemailPlaybackPresenter.Tasks.CHECK_CONTENT_AFTER_CHANGE;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.provider.VoicemailContract;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.View;
import android.widget.TextView;

import com.android.dialer.R;
import com.android.dialer.calllog.CallLogActivity;
import com.android.dialer.util.AsyncTaskExecutors;
import com.android.dialer.util.FakeAsyncTaskExecutor;
import com.android.dialer.util.LocaleTestUtils;
import com.android.dialer.voicemail.VoicemailPlaybackLayout;
import com.android.dialer.voicemail.VoicemailPlaybackPresenter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

/**
 * Unit tests for the {@link VoicemailPlaybackPresenter} and {@link VoicemailPlaybackLayout}.
 */
@LargeTest
public class VoicemailPlaybackTest extends ActivityInstrumentationTestCase2<CallLogActivity> {
    private static final String TEST_ASSET_NAME = "quick_test_recording.mp3";
    private static final String MIME_TYPE = "audio/mp3";
    private static final String CONTACT_NUMBER = "+1412555555";
    private static final String VOICEMAIL_FILE_LOCATION = "/sdcard/sadlfj893w4j23o9sfu.mp3";

    private Activity mActivity;
    private VoicemailPlaybackPresenter mPresenter;
    private VoicemailPlaybackLayout mLayout;

    private Uri mVoicemailUri;
    private LocaleTestUtils mLocaleTestUtils;
    private FakeAsyncTaskExecutor mFakeAsyncTaskExecutor;

    public VoicemailPlaybackTest() {
        super(CallLogActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mFakeAsyncTaskExecutor = new FakeAsyncTaskExecutor(getInstrumentation());
        AsyncTaskExecutors.setFactoryForTest(mFakeAsyncTaskExecutor.getFactory());

        // Some of the tests rely on the text - safest to force a specific locale.
        mLocaleTestUtils = new LocaleTestUtils(getInstrumentation().getTargetContext());
        mLocaleTestUtils.setLocale(Locale.US);

        mActivity = getActivity();
        mLayout = new VoicemailPlaybackLayout(mActivity);
        mLayout.onFinishInflate();

        mPresenter = VoicemailPlaybackPresenter.getInstance(mActivity, null);
    }

    @Override
    protected void tearDown() throws Exception {
        cleanUpVoicemailUri();

        mLocaleTestUtils.restoreLocale();
        mLocaleTestUtils = null;

        mPresenter.clearInstance();
        AsyncTaskExecutors.setFactoryForTest(null);

        mActivity = null;
        mPresenter = null;
        mLayout = null;

        super.tearDown();
    }

    public void testFetchingVoicemail() throws Throwable {
        setUriForUnfetchedVoicemailEntry();
        setPlaybackViewForPresenter();

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mPresenter.resumePlayback();
            }
        });
        mFakeAsyncTaskExecutor.runTask(CHECK_FOR_CONTENT);
        getInstrumentation().waitForIdleSync();

        assertStateTextContains("Loading voicemail");
    }

    public void testWhenCheckForContentCompletes() throws Throwable {
        setUriForRealFileVoicemailEntry();
        setPlaybackViewForPresenter();

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mPresenter.resumePlayback();
            }
        });
        mFakeAsyncTaskExecutor.runTask(CHECK_FOR_CONTENT);
        getInstrumentation().waitForIdleSync();

        // Since the content is already fetched, don't show the loading message.
        assertStateTextNotContains("Loading voicemail");
    }

    public void testInvalidVoicemailShowsErrorMessage() throws Throwable {
        setUriForInvalidVoicemailEntry();
        setPlaybackViewForPresenter();

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mPresenter.resumePlayback();
            }
        });
        mFakeAsyncTaskExecutor.runTask(CHECK_FOR_CONTENT);
        getInstrumentation().waitForIdleSync();

        // The media player will have thrown an IOException since the file doesn't exist.
        // This should have put a failed to play message on screen, buffering is gone.
        assertStateTextContains("Couldn't play voicemail");
        assertStateTextNotContains("Buffering");
    }

    public void testClickingSpeakerphoneButton() throws Throwable {
        setUriForRealFileVoicemailEntry();
        setPlaybackViewForPresenter();

        // Check that the speakerphone is false to start.
        assertFalse(mPresenter.isSpeakerphoneOn());

        View speakerphoneButton = mLayout.findViewById(R.id.playback_speakerphone);
        speakerphoneButton.performClick();
        assertTrue(mPresenter.isSpeakerphoneOn());
    }

    private void cleanUpVoicemailUri() {
        if (mVoicemailUri != null) {
            getContentResolver().delete(VoicemailContract.Voicemails.CONTENT_URI,
                    "_ID = ?", new String[] { String.valueOf(ContentUris.parseId(mVoicemailUri)) });
            mVoicemailUri = null;
        }
    }

    private void setUriForRealFileVoicemailEntry() throws IOException {
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
    }

    private void setUriForUnfetchedVoicemailEntry() {
        assertNull(mVoicemailUri);
        ContentValues values = new ContentValues();
        values.put(VoicemailContract.Voicemails.DATE, String.valueOf(System.currentTimeMillis()));
        values.put(VoicemailContract.Voicemails.NUMBER, CONTACT_NUMBER);
        values.put(VoicemailContract.Voicemails.MIME_TYPE, MIME_TYPE);
        values.put(VoicemailContract.Voicemails.HAS_CONTENT, 0);
        String packageName = getInstrumentation().getTargetContext().getPackageName();
        mVoicemailUri = getContentResolver().insert(
                VoicemailContract.Voicemails.buildSourceUri(packageName), values);
    }

    private void setUriForInvalidVoicemailEntry() {
        assertNull(mVoicemailUri);
        ContentResolver contentResolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(VoicemailContract.Voicemails.NUMBER, CONTACT_NUMBER);
        values.put(VoicemailContract.Voicemails.HAS_CONTENT, 1);
        // VoicemailContract.Voicemails._DATA
        values.put("_data", VOICEMAIL_FILE_LOCATION);
        mVoicemailUri = contentResolver.insert(VoicemailContract.Voicemails.CONTENT_URI, values);
    }

    private void setPlaybackViewForPresenter() {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mPresenter.setPlaybackView(mLayout, mVoicemailUri, false);
            }
        });
    }

    public void copyBetweenStreams(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) > 0) {
            out.write(buffer, 0, bytesRead);
        }
    }

    private void assertStateTextContains(String text) throws Throwable {
        assertNotNull(mLayout);
        assertTrue(mLayout.getStateText().contains(text));
    }

    private void assertStateTextNotContains(String text) throws Throwable {
        assertNotNull(mLayout);
        assertFalse(mLayout.getStateText().contains(text));
    }

    private ContentResolver getContentResolver() {
        return getInstrumentation().getTargetContext().getContentResolver();
    }

    private AssetManager getAssets() {
        return getInstrumentation().getContext().getAssets();
    }
}
